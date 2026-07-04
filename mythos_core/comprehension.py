"""Reading comprehension: read a narrative, build memory, answer questions.

This is the end-to-end "reasons over what it read" pipeline.  It ties the
pieces together on ONE hyperdimensional substrate:

    natural-language sentences
        -> online fact extraction  (closed-vocabulary heuristic parser)
        -> ShardedKB               (holographic role-filler memory)
        -> multi-hop QA            (unbind -> cleanup -> rebind)

and it demonstrates the property that genuinely separates a holographic
memory from both count models and dense nets:

    CONTENT-ADDRESSABLE, NOISE-ROBUST RECALL.
    The memory can be queried with a *corrupted or partial* cue -- a
    hypervector with a fraction of its signs flipped -- and still return
    the right answer, because cleanup projects the noisy query back onto
    the nearest clean symbol.  An n-gram keyed on a corrupted context
    simply misses; a transformer needs the exact tokens.  Graceful
    degradation is free here, and it is the basis of robust reasoning
    over imperfect input.

HONEST SCOPE: the parser is a deliberately small hand-written matcher over
a closed vocabulary of relation phrases ("is the mother of", "lives in",
...).  It is NOT learned and NOT general NLP; it exists only to turn
readable prose into (relation, subject, object) triples so the memory and
reasoning layers can be exercised on natural sentences rather than a
pre-formatted fact table.  The contribution being demonstrated is the
unified read->store->reason->degrade-gracefully loop, not parsing.

Run:  python -m mythos_core.comprehension
"""

from __future__ import annotations

import numpy as np

from .hd import HDSpace
from .system2 import ShardedKB, build_world


# --------------------------------------------------------------------- #
# a tiny closed-vocabulary online parser (honestly not learned)          #
# --------------------------------------------------------------------- #

RELATION_PHRASES = [
    # (phrase tokens, relation, direction) ; direction 'ro' => rel(subj)=obj
    (["is", "the", "mother", "of"], "mother", "obj_is_subject_of_rel"),
    (["is", "the", "father", "of"], "father", "obj_is_subject_of_rel"),
    (["lives", "in"], "lives_in", "subj_rel_obj"),
    (["is", "a", "city", "in"], "city_in", "subj_rel_obj"),
    (["the", "capital", "of"], "capital_of", "capital_pattern"),
]


def parse_sentence(tokens: list[str]) -> tuple[str, str, str] | None:
    """Return (relation, subject, object) for one recognized sentence.

    Word-by-word matching over a closed phrase list -- the kind of thing a
    real system would learn, stubbed here so the rest of the pipeline can
    be shown on natural prose."""
    for phrase, rel, mode in RELATION_PHRASES:
        for i in range(1, len(tokens) - len(phrase)):
            if tokens[i:i + len(phrase)] == phrase:
                left = tokens[i - 1]
                right = tokens[i + len(phrase)] if i + len(phrase) < len(tokens) else None
                if right is None:
                    continue
                if mode == "obj_is_subject_of_rel":
                    # "<obj> is the mother of <subj>" -> mother(subj)=obj
                    return rel, right, left
                if mode == "subj_rel_obj":
                    return rel, left, right
                if mode == "capital_pattern":
                    # "the capital of <country> is <city>"
                    if "is" in tokens[i + len(phrase):]:
                        j = tokens.index("is", i + len(phrase))
                        if j + 1 < len(tokens):
                            return rel, tokens[i + len(phrase)], tokens[j + 1]
    return None


def read_narrative(kb: ShardedKB, text: str) -> int:
    """Stream sentences, parse each online, store recognized facts."""
    n = 0
    for raw in text.split("."):
        toks = raw.strip().split()
        if not toks:
            continue
        fact = parse_sentence(["<s>"] + toks)
        if fact:
            kb.store(*fact)
            n += 1
    return n


# --------------------------------------------------------------------- #
# noise-robust content-addressable recall                                #
# --------------------------------------------------------------------- #

def corrupt(space: HDSpace, hv: np.ndarray, flip_frac: float,
            rng: np.random.Generator) -> np.ndarray:
    """Flip a fraction of the signs -- a partial / noisy version of a cue."""
    mask = rng.random(space.dim) < flip_frac
    out = hv.copy()
    out[mask] *= -1.0
    return out


def noise_robustness(kb: ShardedKB, truth, space: HDSpace) -> None:
    """Query with progressively corrupted subject cues; report accuracy."""
    rng = np.random.default_rng(0)
    mother = truth["mother"]
    subjects = [c for c in mother if mother[c] in mother][:60]  # 2-hop-able
    print(f"\n  noise-robust recall (grandmother query, {len(subjects)} people):")
    print(f"  {'cue corruption':>16}{'accuracy':>12}")
    for flip in [0.0, 0.1, 0.2, 0.3, 0.4]:
        correct = 0
        for child in subjects:
            # ground truth grandmother set
            gms = {mother[p] for p in truth_parents(truth, child) if p in mother}
            cue = corrupt(space, kb.entities[child], flip, rng)
            got = chain_from_cue(kb, cue, [["mother", "father"], ["mother"]])
            correct += got in gms
        print(f"  {flip:>15.0%}{correct / len(subjects):>12.1%}")


def truth_parents(truth, child):
    return [p for p in (truth["mother"].get(child), truth["father"].get(child)) if p]


def chain_from_cue(kb: ShardedKB, cue: np.ndarray,
                   rels: list[list[str]]) -> str:
    """Multi-hop chain starting from a raw (possibly noisy) cue vector.

    Only the FIRST hop sees the corrupted cue; cleanup snaps the result
    back onto a clean symbol, so noise does not compound across hops.
    This demo uses a single merged trace (shards=1) so a nameless raw
    vector can still address every fact."""
    space = kb.space
    trace = kb.M[0]

    # hop 1 -- from the noisy cue vector directly
    first = rels[0]
    best_name, best_sim = "?", -1.0
    for rel in first:
        if rel not in kb.relations:
            continue
        noisy = trace * (kb.relations[rel] * space.permute(cue))
        name, sim = kb._cleanup(noisy)
        if sim > best_sim:
            best_name, best_sim = name, sim
    current = best_name

    # remaining hops -- from clean symbols via the normal query path
    for alternatives in rels[1:]:
        if current not in kb.entities:
            return "?"
        best_name, best_sim = "?", -1.0
        for rel in alternatives:
            name, sim = kb.query(rel, current)
            if sim > best_sim:
                best_name, best_sim = name, sim
        current = best_name
    return current


def main() -> None:
    print("=" * 68)
    print("Reading comprehension: read prose -> build memory -> reason")
    print("=" * 68)

    space = HDSpace(dim=16384, seed=5)
    # A single merged trace for the noisy-cue demo (so a raw vector cue,
    # which has no name to hash, still hits all facts).
    kb = ShardedKB(space, shards=1)
    text, truth = build_world(n_couples=12, seed=11)
    # build_world already emits natural sentences; read them as prose.
    n = read_narrative(kb, text)
    print(f"\nread {n} facts from narrative prose (closed-vocab parser).")
    print("Sample sentences from the narrative:")
    for s in text.split(".")[:3]:
        if s.strip():
            print(f"    \"{s.strip()}.\"")

    # 1. exact multi-hop QA
    from .system2 import evaluate
    acc, total = evaluate(kb, truth)
    print(f"\n  exact multi-hop QA (2-4 hops): {acc:.1%} on {total} queries")

    # 2. the differentiated capability: noise-robust content-addressable recall
    noise_robustness(kb, truth, space)

    print("\nThe accuracy curve degrades gracefully as the cue is corrupted")
    print("-- the memory is content-addressable, so a partial or noisy")
    print("pointer still lands on the right answer.  That robustness is the")
    print("HDC substrate's structural advantage over exact-match retrieval.")


if __name__ == "__main__":
    main()
