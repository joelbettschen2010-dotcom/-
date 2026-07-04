# mythos_core

A working prototype of a **Holographic Attractor Machine** — a cognitive
architecture that replaces attention and backpropagation with:

- **HDC/VSA** hypervector algebra (bind / bundle / permute) for representation
  and zero-parameter symbolic reasoning,
- a frozen **liquid neural cellular automaton** (multi-timescale ring of
  hypervector cells) as the thinking substrate,
- a **dynamic sparse concept graph** that grows on novelty and prunes on
  disuse (one-shot episodic memory),
- a **KAN-style spline readout** trained by the pure delta rule (no autograd,
  no optimizer state, no backward pass).

Training is online and continuous, memory is flat for the lifetime of the
process, and per-token cost is constant in context length. Full design
rationale, math, and measured results: **[ARCHITECTURE.md](ARCHITECTURE.md)**.

## Requirements

```
pip install numpy psutil
```

Pure numpy — deliberately. The architecture needs no autograd, and the
prototype config fits in ~130 MB of RAM. Every operation is elementwise or
a single matvec, so a CUDA port is a mechanical `torch`/`cupy` swap.

## Run it

```bash
# 1. Symbolic reasoning with zero trained parameters (Kanerva analogies)
python -m mythos_core.reasoning

# 2. System 2: one-shot fact learning + compositional multi-hop QA
#    (grandmother / great-grandfather / 4-hop geography never stated in text)
python -m mythos_core.system2

# 2b. End-to-end reading comprehension: read prose -> build memory ->
#     answer multi-hop questions, with noise-robust content-addressable recall
python -m mythos_core.comprehension

# 3. Online sequence learning: grammar + Alice recall, scored against a
#    strong stupid-backoff 6-gram baseline (not just a bigram)
python -m mythos_core.train_demo

# 4. Honest scale test: single-pass generalization on real English prose,
#    top-1 accuracy AND bits/char vs the backoff n-gram
python -m mythos_core.scale_test 40000

# 5. Efficiency, measured not projected: 1-bit binary memory vs fp32
#    (32x smaller, accuracy retained, honest speed comparison)
python -m mythos_core.binary

# 5b. Learning efficiency: one-shot HDC vs SGD on identical features
#     (tied accuracy; the win is tuning-free single-pass add-only updates)
python -m mythos_core.sample_efficiency

# 6. Validation: 30k-step training loop, flat-memory leak check (exits
#    nonzero on failure), measured footprint, 12GB VRAM projection
python -m mythos_core.benchmark
```

## Expected results (honest, with a strong baseline)

- `reasoning.py` — "dollar of Mexico → peso" and a two-hop analogy chain,
  each one elementwise multiply + nearest-neighbor lookup.
- `system2.py` — **100%** on ~9k multi-hop queries whose compositions never
  appear in the fact stream; capacity scales linearly with sharded memory.
- `comprehension.py` — reads 128 facts from natural prose, 100% multi-hop
  QA, and **100% recall with 30% of the query cue corrupted** (graceful
  decay to 58% at 40%) — content-addressable retrieval n-grams can't do.
- `train_demo.py` — grammar task **93.2%** vs **91.2%** backoff-6gram (win:
  agreement spans past the n-gram window); Alice ×2 the n-gram wins on pure
  repetition (it memorizes exact substrings) — stated plainly, not hidden.
- `scale_test.py` — real single-pass prose: a **properly interpolated
  n-gram beats MythosCore** on both top-1 (53.8% vs 50.7%) and bits/char
  (2.60 vs 3.23). On plain char-level language modeling MythosCore is *not*
  the better model — this test marks that boundary honestly.
- `binary.py` — 1-bit concept memory: **32× smaller than fp32 with recall
  fully retained** (measured). Speed: fp32 BLAS wins on CPU (~1.5×); the
  popcount speed win needs a GPU and is *not* claimed here. Honest.
- `sample_efficiency.py` — one-shot HDC vs SGD on identical features:
  accuracy **ties** SGD-1-epoch at every shot count (SGD edges ahead with
  50 epochs). The win is the *update*: add-only, tuning-free, single-pass,
  online — reported at its true modest size, not inflated.
- `benchmark.py` — `LEAK CHECK: PASS`, 0.0 MB RSS drift over 30,000 steps;
  scaled config (D=16384, 2M nodes, 1-bit) at ~4.9 GB, 40% of a 3060.

**What this is and isn't:** MythosCore is **not** a better language model
than a well-smoothed n-gram, and not a transformer-grade generator. Its
three defensible wins are (1) carrying dependencies *beyond a fixed context
window* (grammar task, 93% vs 91%), (2) one-shot *episodic recall* in
constant memory, and (3) zero-parameter *compositional multi-hop reasoning*
that n-grams cannot do at all. The value is that combination running
*trainably* in a fixed 12 GB envelope. See ARCHITECTURE.md §4 and §6.

## Layout

| File | Role |
|---|---|
| `hd.py` | hypervector space: bind, bundle, permute, codebook |
| `encoder.py` | LiquidNCA — frozen multi-timescale cellular ring |
| `memory.py` | ConceptGraph — grow/prune episodic memory with edges |
| `readout.py` | KANReadout — spline edge functions + delta rule |
| `model.py` | MythosCore — the assembled machine (backoff + calibration) |
| `reasoning.py` | VSA analogy/reasoning demo |
| `system2.py` | one-shot fact learning + compositional multi-hop QA |
| `comprehension.py` | read prose → memory → QA + noise-robust recall |
| `binary.py` | 1-bit binary memory backend — measured memory/speed/accuracy |
| `sample_efficiency.py` | one-shot HDC vs SGD learning efficiency (measured) |
| `train_demo.py` | online learning demo vs backoff-6gram baseline |
| `scale_test.py` | single-pass generalization on real prose (accuracy + bits/char) |
| `benchmark.py` | leak check + VRAM budget validation |
