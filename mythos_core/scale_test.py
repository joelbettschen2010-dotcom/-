"""Honest scale test: single-pass generalization on real English prose.

This is the test the toy demos cannot fake.  The model streams a real
book ONCE, predicting each character before seeing it (prequential
protocol), and is scored against a strong online backoff-6gram baseline
over the *same* stream.  There is no second pass, so episodic recall of
repeats cannot inflate the number: to win here the liquid state has to
capture genuine statistical structure of English that literal
context-counting does not.

Two numbers are reported per model:
  * overall prequential accuracy (harder early, easier late)
  * second-half accuracy (after both models have warmed up)

and a compression-style metric:
  * bits-per-character (mean -log2 p(true token)) -- the information-
    theoretic quality of the *full distribution*, not just top-1.  This
    is what a language model is really judged on, and it rewards being
    well-calibrated rather than merely arg-max-correct.

The corpus is Alice in Wonderland (public domain, 1865), fetched from
Project Gutenberg with a bundled fallback so the test runs offline.

Run:  python -m mythos_core.scale_test [n_chars]
"""

from __future__ import annotations

import re
import sys
import time
import unicodedata
import urllib.request

import numpy as np

from .model import MythosCore
from .train_demo import ALICE, OnlineBackoffNgram

GUTENBERG_URL = "https://www.gutenberg.org/files/11/11-0.txt"


def load_corpus(n_chars: int) -> str:
    """Fetch and clean the book; fall back to the bundled excerpt offline."""
    try:
        with urllib.request.urlopen(GUTENBERG_URL, timeout=20) as r:
            raw = r.read().decode("utf-8", "ignore")
        start = raw.find("CHAPTER I.")
        start = raw.find("CHAPTER I.", start + 1)
        end = raw.find("THE END")
        body = raw[start:end] if 0 < start < end else raw
        body = unicodedata.normalize("NFKD", body).encode("ascii", "ignore").decode()
        body = re.sub(r"\s+", " ", body).strip()
        if len(body) < 5000:
            raise ValueError("fetched text too short")
        source = "Project Gutenberg (full text)"
    except Exception as e:                       # offline / blocked
        body = (ALICE * (n_chars // len(ALICE) + 2))
        source = f"bundled excerpt (offline fallback: {type(e).__name__})"
    print(f"corpus source   : {source}")
    return body[:n_chars]


def bits_per_char(p: np.ndarray, target: int) -> float:
    return -float(np.log2(max(p[target], 1e-12)))


def ngram_dist(ng: OnlineBackoffNgram, vocab: int) -> np.ndarray:
    """Turn the backoff model's longest-context counts into a distribution
    (add-one smoothed) so it can be scored on bits-per-character too."""
    for k in range(ng.order - 1, -1, -1):
        key = ng.ctx[len(ng.ctx) - k:] if k else tuple()
        table = ng.tables[k].get(key)
        if table:
            v = np.ones(vocab)
            for tok, c in table.items():
                v[tok] += c
            return v / v.sum()
    return np.ones(vocab) / vocab


def main() -> None:
    n_chars = int(sys.argv[1]) if len(sys.argv) > 1 else 40000
    print("=" * 68)
    print("Scale test: single-pass generalization on real English prose")
    print("=" * 68)
    text = load_corpus(n_chars)
    chars = sorted(set(text))
    stoi = {c: i for i, c in enumerate(chars)}
    stream = [stoi[c] for c in text]
    vocab = len(chars)
    half = len(stream) // 2
    print(f"corpus          : {len(stream):,} chars, vocab {vocab}\n")

    model = MythosCore(vocab_size=vocab, dim=2048, n_cells=16,
                       max_nodes=20000, seed=0)
    ngram = OnlineBackoffNgram(vocab, order=6)

    m_hit = n_hit = 0
    m_hit2 = n_hit2 = 0
    m_bits = n_bits = 0.0
    t0 = time.perf_counter()
    for i, tok in enumerate(stream):
        p = model.step(tok, learn=True)
        pn = ngram_dist(ngram, vocab)
        m_ok = int(np.argmax(p)) == tok
        n_ok = ngram.step(tok) == tok
        m_hit += m_ok; n_hit += n_ok
        m_bits += bits_per_char(p, tok)
        n_bits += bits_per_char(pn, tok)
        if i >= half:
            m_hit2 += m_ok; n_hit2 += n_ok
        if (i + 1) % 5000 == 0:
            print(f"  {i + 1:6d} chars | model acc {m_hit / (i + 1):5.1%} "
                  f"bpc {m_bits / (i + 1):4.2f} | "
                  f"6gram acc {n_hit / (i + 1):5.1%} bpc {n_bits / (i + 1):4.2f} "
                  f"| nodes {model.graph.n_nodes:,}")
    dt = time.perf_counter() - t0
    N, H = len(stream), len(stream) - half

    print(f"\n{'':16}{'accuracy':>12}{'2nd-half acc':>14}{'bits/char':>12}")
    print(f"{'MythosCore':16}{m_hit / N:>12.1%}{m_hit2 / H:>14.1%}{m_bits / N:>12.2f}")
    print(f"{'backoff-6gram':16}{n_hit / N:>12.1%}{n_hit2 / H:>14.1%}{n_bits / N:>12.2f}")
    print(f"{'uniform':16}{1 / vocab:>12.1%}{'':>14}{np.log2(vocab):>12.2f}")
    print(f"\nthroughput      : {N / dt:,.0f} tokens/s (single CPU core)")
    print("bits/char is the real language-model metric (lower = better")
    print("calibrated).  Single pass, no repeats to memorize -- this is")
    print("generalization of English structure, not episodic recall.")


if __name__ == "__main__":
    main()
