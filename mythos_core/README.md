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

# 2. Online sequence learning: template grammar + Alice-in-Wonderland
#    episodic recall, scored against an online bigram baseline
python -m mythos_core.train_demo

# 3. Validation: 30k-step training loop, flat-memory leak check (exits
#    nonzero on failure), measured footprint, 12GB VRAM projection
python -m mythos_core.benchmark
```

## Expected results

- `reasoning.py` — "dollar of Mexico → peso" and a two-hop analogy chain,
  each answered by one elementwise multiply + nearest-neighbor lookup.
- `train_demo.py` — grammar task ~93% online accuracy vs ~52% bigram;
  Alice pass-2 accuracy roughly doubles pass-1 as episodic recognition
  kicks in; free-run generation produces grammatical sentences with
  correct long-range agreement.
- `benchmark.py` — `LEAK CHECK: PASS` with 0.0 MB RSS drift over 30,000
  online learning steps; scaled "Mythos" config (D=16384, 2M concept
  nodes) projected at ~4.9 GB — 40% of an RTX 3060's 12 GB.

## Layout

| File | Role |
|---|---|
| `hd.py` | hypervector space: bind, bundle, permute, codebook |
| `encoder.py` | LiquidNCA — frozen multi-timescale cellular ring |
| `memory.py` | ConceptGraph — grow/prune episodic memory with edges |
| `readout.py` | KANReadout — spline edge functions + delta rule |
| `model.py` | MythosCore — the assembled machine |
| `reasoning.py` | VSA analogy/reasoning demo |
| `train_demo.py` | online learning demo vs bigram baseline |
| `benchmark.py` | leak check + VRAM budget validation |
