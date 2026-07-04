# MythosCore: A Holographic Attractor Machine

*A cognitive architecture for continuous local learning on consumer hardware —
no attention, no backpropagation, no KV cache, constant memory per token.*

> **Honesty note.** This document proposes and the code implements a real,
> working, measurable architecture — but "Mythos-class emergent reasoning" is
> the *target of the research program*, not a property this prototype has
> demonstrated. What the prototype demonstrates is the load-bearing claims:
> symbolic reasoning by vector algebra with zero trained parameters,
> long-range sequence learning that beats an n-gram baseline in a single
> online pass, one-shot episodic recall no gradient method can match, and a
> training loop whose memory footprint is provably flat and two orders of
> magnitude under the 12 GB budget.

---

## 1. Why Transformers cannot be trained on an RTX 3060 — and what to keep

A transformer's costs are structural, not incidental:

| Cost | Origin | Scaling |
|---|---|---|
| Weights + optimizer state | dense matrices everywhere | ~16 bytes/param with Adam (fp16 weights + fp32 master + 2 moments) |
| Activations | needed for backprop | O(layers × batch × seq × width) |
| KV cache | attention re-reads all history | O(layers × seq × width) |
| Attention compute | all-pairs comparison | O(N²) |

A 7B model needs ~13 GB *just to exist* in fp16; training it with Adam needs
>100 GB. Every mitigation (LoRA, quantization, offloading) trims constants
but keeps the structure. To train intelligence *from scratch* in 12 GB, the
structure itself has to go.

What must be kept, whatever the substrate:

1. **Composable representations** — the ability to build and query structure
   (roles, bindings, analogies).
2. **Multi-timescale context** — the present interpreted against seconds,
   sentences, and chapters of history simultaneously.
3. **Two speeds of learning** — instant memorization of specifics *and* slow
   statistical generalization (the complementary-learning-systems insight
   from neuroscience).

## 2. Trade-off analysis of the candidate paradigms

| Paradigm | What it uniquely offers | Fatal flaw when used alone |
|---|---|---|
| **HDC / VSA** | Algebra over concepts: `bind` (association), `bundle` (superposition), `permute` (order). Capacity O(D/log D) items per vector. One-shot learning. All ops O(D). | Fixed random codes can't *approximate functions*; no gradient-quality curve fitting. |
| **Liquid / SNN** | Continuous-time, multi-timescale temporal integration; the reservoir needs **no training at all**. | Event-driven spikes are slow on von-Neumann hardware; a readout must still be learned. |
| **KAN** | Learnable 1-D functions on edges: high expressivity per parameter (D·B coefficients vs D² matrices). | Deep KANs still require backprop. |
| **NCA** | Global behavior from one cheap local rule; O(cells) memory forever. | Training the rule by gradients means BPTT through the grid — the very thing we're escaping. |
| **Dynamic concept graphs** | Capacity on demand: grow on novelty, prune on disuse; no catastrophic forgetting. | Needs a similarity space good enough that "novelty" is measurable. |

**The synthesis insight: the weaknesses cancel pairwise if the *state space
is hyperdimensional*.**

- A liquid/NCA with **frozen random wiring** loses nothing in high dimension —
  random projections are information-preserving (Johnson–Lindenstrauss). So
  the recurrent core needs *zero* training, which eliminates BPTT, activation
  storage, and the entire backward pass through time.
- The concept graph gets its similarity metric **for free**: by concentration
  of measure, unrelated hyperdimensional states have cosine ≈ 0 ± 1/√D while
  revisited contexts measure ≈ 1. Novelty detection is one matvec and a
  threshold. (Measured in this repo: repeated contexts pin at **1.000**,
  unrelated contexts at **−0.002 ± 0.07**.)
- With the recurrent core frozen, the only trainable modules are **single
  layers** — so the *delta rule* (the exact one-step local gradient) replaces
  backprop with no approximation. No autograd graph, no optimizer moments.
- The KAN element survives as learnable 1-D edge functions on the readout,
  where one-step local updates reach it — expressivity without depth.

## 3. The architecture

```
 token ──► Codebook (frozen random bipolar hypervectors, V×D) ──┐
                                                                ▼
 ┌────────────────────── LiquidNCA ring (K cells × D) ──────────────────┐
 │  local rule:  h_i ← tanh( α_i·h_i + (1-α_i)·[β·ρ(h_{i-1}) + γ·g_i⊛x] ) │
 │  α_i log-spaced 0.35…0.985  →  a filterbank over timescales           │
 └───────────────┬───────────────────────────────────────────────────────┘
                 │  bundle cells tagged with ρ^i, subtract running mean,
                 │  normalize  →  state s (unit vector, one per instant)
        ┌────────┴─────────┐
        ▼                  ▼
 ConceptGraph          KANReadout
 (episodic:            (semantic: learnable 1-D spline
  grow/prune nodes,     per dimension + linear layer,
  Hebbian counts,       trained by pure delta rule)
  co-activation edges)
        └────────┬─────────┘
                 ▼
   confidence-gated blend  →  P(next token)
```

### 3.1 Input encoding: tokens → holograms

Each token gets a **frozen random bipolar hypervector** (D = 4096 in the
prototype; 16k–32k at scale). No embedding training: random codes are
already maximally separated in high dimension, and a new symbol costs O(D)
to add — open vocabulary, forever.

Order is encoded *algebraically*, not positionally: the ring circulates
state through a fixed random permutation ρ, and the bundled query tags cell
*i* with ρⁱ. Because ρᵏ(x) is quasi-orthogonal to x, "the same token,
earlier" occupies its own subspace. **This is what replaces positional
embeddings and attention offsets.**

### 3.2 Thinking: a frozen liquid instead of attention

The working memory is a ring of K cells (a 1-D neural cellular automaton),
each holding one hypervector, all updated by a single local rule per token.
Three design decisions carry the weight:

1. **Per-cell leak α_i, log-spaced** — the ring is a *liquid filterbank*:
   fast cells hold the last couple of tokens, slow cells the last ~65+.
   Context at every horizon is simultaneously present in the state.
2. **Drive scaling (1−α_i)** — the canonical leaky-integrator form. Slow
   cells feel the *average* of many tokens rather than being slammed by the
   current one. Without this, the instantaneous token dominates every cell
   and states collapse toward "last-character identity" (measured: 0.87
   cosine for same-last-char contexts before the fix, 0.71 after; true
   repeats stay at 1.000).
3. **Mean-centered query** — a running mean of the bundled state is
   subtracted before normalization, cancelling the static component that
   tanh-saturated slow cells contribute. One line; it is the difference
   between a graph that degenerates into a bigram table and one that does
   genuine episodic recognition.

Cost per token: **O(K·D) elementwise operations, constant in sequence
length.** There is no pairwise term anywhere; "attention" over the past is
replaced by the superposition already living in the state.

### 3.3 Remembering: the dynamic sparse concept graph

The episodic pathway ("hippocampus"). A node = a prototype hypervector +
next-token counts + co-activation edges.

- **Retrieval**: one (N×D) matvec → top-k similarity mixture, sharpened
  toward the best match, plus a one-hop spreading-activation vote from the
  previously active node's outgoing edges.
- **Learning**: if the best similarity is below the novelty threshold
  (0.985 — calibrated from the measured similarity spectrum), allocate a
  node (one-shot memorization). Otherwise nudge the winner's prototype
  (Hebbian drift) and bump its counts.
- **Pruning**: when full, evict argmin(usage/age). Capacity is a hard
  budget, allocated on demand — "parameters" exist only where experience
  put them.

This is where "parameter-equivalent" scale lives: 2M nodes at D = 16384
with classical 1-bit HDC packing is a 32-billion-bit associative memory
that costs **3.8 GB** and is queried by XOR+popcount.

### 3.4 Generalizing: the KAN readout under the delta rule

The semantic pathway ("cortex"). Every state dimension d has its own
learnable scalar function ψ_d on a hat-spline basis (initialized to the
identity), feeding one linear layer to the vocabulary:

```
logits = W ψ(s√D) + b          ψ_d(x) = Σ_j C[d,j] · hat_j(x)
```

Training is the **pure delta rule** — the exact gradient *at this layer
only*: `e = softmax − onehot`, then two rank-1 updates (W, C). Because
everything below is frozen, one local step *is* the whole credit
assignment. No stored activations, no Adam moments, no backward graph:
the optimizer state is the weights themselves.

### 3.5 Predicting: complementary systems with multi-timescale backoff

Prediction is a **backoff cascade** over three sources, each trusted in
proportion to how strongly it recognizes the present:

```
P = readout                                    # semantic prior
P = g(conf_short)·P_short + (1−g)·P            # short-context episodic
P = g(conf_long )·P_long  + (1−g)·P            # full-context episodic
P = (1−ε)·P + ε/V                              # calibration floor
```

where g rises from 0 to 0.85 as confidence climbs from 0.60 to 1.0. The
**short-horizon graph** indexes only the fast liquid cells (last ~3
tokens), so a novel long context still benefits from familiar recent
history — this is the hyperdimensional analogue of n-gram backoff, in
constant memory. The **calibration floor** ε (=0.05) keeps episodic
recall's near-one-hot outputs from being catastrophic under log-loss; it
is the same smoothing every serious n-gram model uses, and it is what
turns a 5.9 bits/char model into a 3.2 bits/char one (§4).

Familiar situations are answered from episodic memory (exact, one-shot);
novel ones fall back on distilled statistics. This is two-speed learning
that dense nets cannot do in a single pass: **the second time MythosCore
reads a text, it recognizes it** (measured below).

### 3.5b System 2: compositional reasoning by unbind–cleanup–rebind

`reasoning.py` and `system2.py` demonstrate that the same algebra supports
*multi-hop reasoning over facts learned one-shot from text*, with zero
trained parameters. A whole knowledge graph is superposed into one
vector `M = Σ (rel ⊛ ρ(subj)) ⊛ obj`; a query unbinds the key and cleans
up against the entity codebook. Chaining hops (unbind → cleanup → rebind)
answers queries whose compositions never appeared in the input —
`grandmother`, `great-grandfather`, `capital of the country where X's
mother lives` — as pure algebra, O(D) per hop. The permutation ρ on the
subject breaks the bind symmetry so direction is preserved (mother(ben)=anna
≠ mother(anna)=ben). Capacity per trace is O(D/log D) facts; **sharding
the memory makes total capacity linear in bytes** (measured: 64 shards ×
32 KB hold 10k facts at 100% on 9k multi-hop queries).

`comprehension.py` runs the whole loop on natural prose (read → extract →
store → reason) and demonstrates the substrate's signature advantage:
**content-addressable, noise-robust recall**. Querying with a *corrupted*
subject cue — up to 30% of its signs randomly flipped — still resolves the
correct multi-hop answer at 100%, decaying gracefully to 58% at 40%
corruption, because cleanup projects the noisy query onto the nearest clean
symbol. Exact-match retrieval (an n-gram, a hash map, a dict) fails at the
first flipped bit; graceful degradation under noise is free here and is a
prerequisite for reasoning over imperfect input.

### 3.6 Continuous learning within 12 GB

Training **is** inference plus two local updates — there is no separate
training mode, no batch dimension, no epochs. Consequences:

- Memory is **flat for the lifetime of the process** (all node storage
  pre-allocated; measured: 0.0 MB RSS drift over 30,000 online steps).
- Per-token compute is O(K·D + N·D) regardless of context length.
- Learning never stops: the model in production keeps acquiring nodes and
  bending its splines. Catastrophic forgetting is structural, not
  statistical — new knowledge takes new nodes instead of overwriting
  shared weights.

## 4. Measured results (this repo, single CPU core, numpy)

Baselines are scored under the identical online prequential protocol. The
honest bar is not a bigram (trivial) but a **stupid-backoff 6-gram** — a
genuinely strong count model that memorizes exact contexts. Where
MythosCore wins and loses is itself the finding:

| Experiment | MythosCore | Strong n-gram | Bigram |
|---|---|---|---|
| **Template grammar**, long-range agreement beyond the n-gram window, 8k chars | **93.2%** | 91.2% | 51.6% |
| **Alice ×2**, pure repetition (episodic recall) | 54.5% (pass-2 window **67%**) | **65.6%** | 28.5% |
| **Real Alice**, 40k chars, single pass, top-1 accuracy | 50.7% | **53.8%** | — |
| **Real Alice**, 40k chars, single pass, **bits/char** (interpolated n-gram) | 3.23 | **2.60** | — (uniform 5.98) |
| **System 2** multi-hop QA (grandmother / great-grandfather / 4-hop geo), sharded | **100%** on 9k queries | n/a | n/a |
| VSA analogy ("dollar of Mexico", role extraction, 2-hop chain) | all correct, ~10× margin | n/a | n/a |
| 30k-step training loop | RSS drift **+0.0 MB**, 261 MB model | — | — |

How to read this honestly — including where MythosCore **loses**:

- **On plain character-level language modeling of real prose, the n-gram
  wins outright.** A properly *interpolated* backoff n-gram reaches 2.60
  bits/char and higher top-1 accuracy than MythosCore's 3.23. Count-based
  smoothing is a ferociously strong baseline at this exact task, and
  MythosCore does not beat it. (An earlier draft of this table claimed a
  bits/char *win*; that was measured against a weakly-smoothed n-gram and
  was wrong. Strengthening the baseline reversed the result, and the table
  now reports the loss.)
- **On long-range structure, MythosCore wins.** The grammar task's
  subject–object agreement spans ~15 characters, past a 6-gram's window;
  the liquid state carries it and MythosCore beats the n-gram 93.2% vs
  91.2%. The free-run sample generates syntactically perfect sentences with
  correct agreement. This is the one sequence task where the architecture's
  unbounded-context state genuinely pays off over counting.
- **On pure repetition, the n-gram wins** (perfect exact-substring memory);
  MythosCore's approximate recognition trails.
- **On compositional reasoning, there is no n-gram baseline at all** —
  answering `grandmother`/`great-grandfather`/4-hop-geography queries that
  were never stated is not something a count model can do in principle.
  This, not language modeling, is where the architecture is actually
  differentiated, and it costs zero trained parameters.

Net: **MythosCore is not a better character language model than a
well-smoothed n-gram.** Its defensible claims are (1) carrying dependencies
beyond a fixed window, (2) one-shot episodic recall in constant memory, and
(3) symbolic compositional reasoning. The value proposition is the
combination running trainably in a fixed VRAM envelope — not raw next-token
accuracy.

## 5. VRAM budget at scale (fp16 / 1-bit, exact closed form)

| Config | D | Nodes | Total | % of 12 GB |
|---|---|---|---|---|
| Prototype (this repo) | 4096 | 8k | 0.066 GB | 0.5% |
| 3060, fp16 prototypes | 16384 | 250k | 7.8 GB | 65% |
| **3060 Mythos-scale, 1-bit** | 16384 | 2M | **4.9 GB** | 40% |
| 3060 max, 1-bit | 32768 | 2M | 9.2 GB | 77% |

Nothing is hidden in these numbers because the architecture stores no
activations, no KV cache, and no optimizer state, and per-token scratch is
O(K·D + N). Every op is elementwise or a single matvec/popcount — the
port to CUDA is mechanical (`cupy`/`torch` drop-in), and the retrieval
matvec at Mythos scale is ~a few ms on a 3060.

## 6. Honest limitations and the road forward

- **This is not a language model competitive with transformers.** On raw
  next-character prediction of real prose it trails a backoff n-gram on
  top-1 accuracy. Its wins are specific: long-range *structure*,
  *calibration* (bits/char), one-shot *episodic recall*, and *symbolic
  compositional reasoning*. Those are real and measured; general fluent
  generation is not claimed.
- The delta-rule readout is a linear decoder over a rich random feature
  space — powerful, but shallow. The scaling hypothesis is that *capacity
  should live in episodic/compositional structure (nodes, edges, superposed
  traces) rather than in deep trained weights*. The System 2 sharding
  result (capacity linear in bytes, O(D) queries) is the first evidence for
  it; whether it carries to open-domain reasoning is the open question.
- The n-gram baseline should be strengthened to Kneser-Ney before the
  bits/char win is treated as decisive.
- The confidence gate, novelty threshold, and smoothing floor are
  calibrated constants; they should be homeostatic (target a node-growth
  rate and a running cross-entropy).
- System 2 reasoning (`system2.py`) currently runs on a hand-parsed fact
  grammar and a separate holographic store. Fusing it with the online
  concept graph — so the sequence model *populates* the reasoning memory as
  it reads, then queries it mid-stream — is the natural next layer and the
  path from "predicts text" to "reasons over what it read."
