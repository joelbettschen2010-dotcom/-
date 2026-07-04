"""End-to-end demo: continuous online learning on two sequence tasks.

The model is scored the honest way: at every position it must predict the
next character *before* seeing it, then it learns from it -- a single pass,
no epochs, no train/test split leakage (the metric is prequential /
online accuracy, reported over trailing windows so you can watch learning
happen in real time).

Baselines:
  * an online bigram model (the classic "cheap sequence memory"),
    trained on exactly the same stream under exactly the same protocol.

Tasks:
  1. A stochastic template grammar -- tests structural learning beyond
     bigrams (the correct continuation depends on words several tokens
     back, which the liquid's slow cells must carry).
  2. Public-domain English prose (opening of Alice in Wonderland, 1865)
     streamed twice -- pass 2 shows the episodic concept graph kicking in:
     the second time through, contexts are *recognized* and recalled
     one-shot, which no gradient method can do in a single pass.

Run:  python -m mythos_core.train_demo
"""

from __future__ import annotations

import time

import numpy as np

from .model import MythosCore

# --------------------------------------------------------------------- #
# data                                                                   #
# --------------------------------------------------------------------- #

ALICE = (
    "Alice was beginning to get very tired of sitting by her sister on the "
    "bank, and of having nothing to do: once or twice she had peeped into "
    "the book her sister was reading, but it had no pictures or "
    "conversations in it, and what is the use of a book, thought Alice, "
    "without pictures or conversations? So she was considering in her own "
    "mind, as well as she could, for the hot day made her feel very sleepy "
    "and stupid, whether the pleasure of making a daisy-chain would be "
    "worth the trouble of getting up and picking the daisies, when suddenly "
    "a White Rabbit with pink eyes ran close by her. There was nothing so "
    "very remarkable in that; nor did Alice think it so very much out of "
    "the way to hear the Rabbit say to itself, Oh dear! Oh dear! I shall be "
    "late! But when the Rabbit actually took a watch out of its "
    "waistcoat-pocket, and looked at it, and then hurried on, Alice started "
    "to her feet, for it flashed across her mind that she had never before "
    "seen a rabbit with either a waistcoat-pocket, or a watch to take out "
    "of it, and burning with curiosity, she ran across the field after it, "
    "and fortunately was just in time to see it pop down a large "
    "rabbit-hole under the hedge. In another moment down went Alice after "
    "it, never once considering how in the world she was to get out again. "
)


def grammar_stream(n_chars: int, seed: int = 3) -> str:
    """Stochastic template grammar with long-range agreement.

    The object clause agrees with the subject chosen ~15 characters
    earlier ('cat' -> 'mice', 'dog' -> 'bones', 'bird' -> 'seeds'),
    so beating the bigram baseline requires carrying context across
    the whole sentence.
    """
    rng = np.random.default_rng(seed)
    pairs = [("cat", "mice"), ("dog", "bones"), ("bird", "seeds")]
    verbs = ["likes", "wants"]
    out = []
    while sum(len(s) for s in out) < n_chars:
        subj, obj = pairs[rng.integers(len(pairs))]
        verb = verbs[rng.integers(len(verbs))]
        out.append(f"the {subj} {verb} {obj} . ")
    return "".join(out)[:n_chars]


# --------------------------------------------------------------------- #
# baseline                                                               #
# --------------------------------------------------------------------- #

class OnlineBigram:
    def __init__(self, vocab_size: int):
        self.counts = np.ones((vocab_size, vocab_size), dtype=np.float64)
        self.prev = 0

    def step(self, token: int) -> int:
        pred = int(np.argmax(self.counts[self.prev]))
        self.counts[self.prev, token] += 1.0
        self.prev = token
        return pred


# --------------------------------------------------------------------- #
# harness                                                                #
# --------------------------------------------------------------------- #

def run_task(name: str, text: str, model_kwargs: dict, window: int = 500) -> None:
    chars = sorted(set(text))
    stoi = {c: i for i, c in enumerate(chars)}
    itos = {i: c for c, i in stoi.items()}
    stream = [stoi[c] for c in text]
    vocab = len(chars)

    model = MythosCore(vocab_size=vocab, **model_kwargs)
    bigram = OnlineBigram(vocab)

    hits_m = hits_b = 0
    win_m: list[int] = []
    t0 = time.perf_counter()
    print(f"\n### Task: {name}  ({len(stream)} chars, vocab {vocab}) ###")
    for i, tok in enumerate(stream):
        pred = model.step(tok, learn=True)
        ok = int(np.argmax(pred)) == tok
        hits_m += ok
        hits_b += bigram.step(tok) == tok
        win_m.append(ok)
        if len(win_m) > window:
            win_m.pop(0)
        if (i + 1) % 1000 == 0:
            print(f"  step {i + 1:6d} | trailing-{window} accuracy: "
                  f"{np.mean(win_m):5.1%} | graph nodes: {model.graph.n_nodes}")
    dt = time.perf_counter() - t0

    q = len(stream) // 4
    print(f"  overall     : model {hits_m / len(stream):5.1%} | "
          f"bigram {hits_b / len(stream):5.1%}")
    print(f"  final window: model {np.mean(win_m):5.1%}")
    print(f"  throughput  : {len(stream) / dt:,.0f} tokens/s (CPU) | "
          f"graph grew {model.graph.grown}, pruned {model.graph.pruned}")

    sample = model.generate([stoi[c] for c in text[:20]], 120, temperature=0.3)
    print(f"  free-run sample after training:\n    "
          f"{text[:20]!r} -> {''.join(itos[t] for t in sample)!r}")


def main() -> None:
    kwargs = dict(dim=2048, n_cells=16, max_nodes=4096, seed=0)
    run_task("template grammar (long-range agreement)",
             grammar_stream(8000), kwargs)
    run_task("Alice in Wonderland, streamed twice (episodic recall)",
             ALICE + ALICE, kwargs)


if __name__ == "__main__":
    main()
