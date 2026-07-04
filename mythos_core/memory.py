"""Dynamic Sparse Concept Graph (the episodic memory / "hippocampus").

Instead of storing knowledge in dense weight matrices sized up front, this
memory *grows nodes on novelty and prunes them by disuse*:

  * A node is a prototype hypervector (a Hebbian running average of the
    liquid states that activated it) plus a count vector over observed
    next-tokens, plus co-activation edges to other nodes.
  * Novelty detection is free: cosine similarity in hyperdimensional space
    is sharply calibrated (unrelated states measure ~0, revisited contexts
    measure near 1), so "have I been in a situation like this before?" is
    a single matvec + threshold.
  * Learning is one-shot and local: bump a count, nudge a prototype.
    No gradients, no replay buffer, no catastrophic forgetting of the
    kind dense nets suffer -- new knowledge gets new nodes.

This is the component that gives the machine "parameters on demand":
capacity is bounded by max_nodes * D floats, allocated only as experience
requires, and reclaimable at any time.
"""

from __future__ import annotations

import numpy as np


class ConceptGraph:
    def __init__(
        self,
        dim: int,
        vocab_size: int,
        max_nodes: int = 4096,
        novelty_threshold: float = 0.985,
        proto_lr: float = 0.05,
        top_k: int = 4,
    ):
        self.dim = dim
        self.vocab_size = vocab_size
        self.max_nodes = max_nodes
        self.novelty_threshold = novelty_threshold
        self.proto_lr = proto_lr
        self.top_k = top_k

        # Pre-allocated node storage (never reallocated -> no fragmentation,
        # no memory growth after warm-up; central to the no-leak guarantee).
        self.prototypes = np.zeros((max_nodes, dim), dtype=np.float32)
        self.outcome_counts = np.zeros((max_nodes, vocab_size), dtype=np.float32)
        self.usage = np.zeros(max_nodes, dtype=np.float32)
        self.last_used = np.zeros(max_nodes, dtype=np.int64)

        # Sparse co-activation edges: node -> {successor: count}.
        self.edges: dict[int, dict[int, float]] = {}

        self.n_nodes = 0
        self.step_counter = 0
        self.prev_node: int | None = None
        self.grown = 0
        self.pruned = 0
        self._last_sims: np.ndarray | None = None

    # ------------------------------------------------------------------ #
    # retrieval                                                          #
    # ------------------------------------------------------------------ #
    def predict(self, state: np.ndarray) -> tuple[np.ndarray | None, float]:
        """Return (next-token distribution, retrieval confidence).

        The single "big" operation of the whole architecture: one
        (n_nodes x D) @ (D,) matvec.  With 1M nodes at D=16384 in fp16
        this is ~32 GFLOP-equivalent -- a few ms on an RTX 3060 -- and
        it replaces the entire attention stack.
        """
        if self.n_nodes == 0:
            self._last_sims = None
            return None, 0.0
        sims = self.prototypes[: self.n_nodes] @ state
        self._last_sims = sims          # reused by learn() on the same state
        k = min(self.top_k, self.n_nodes)
        top = np.argpartition(sims, -k)[-k:]
        w = np.clip(sims[top], 0.0, None) ** 8          # sharpen toward best match
        if w.sum() < 1e-8:
            return None, float(sims.max())
        dist = w @ self.outcome_counts[top]

        # Graph structure at work: the previously active node's outgoing
        # edges cast a weak vote for their targets' outcomes (a one-hop
        # spreading-activation prior -- sequential association).
        if self.prev_node is not None:
            for succ, cnt in self.edges.get(self.prev_node, {}).items():
                dist = dist + 0.1 * cnt * self.outcome_counts[succ]

        total = dist.sum()
        if total < 1e-8:
            return None, float(sims.max())
        return dist / total, float(sims.max())

    # ------------------------------------------------------------------ #
    # learning (Hebbian, one-shot, local)                                #
    # ------------------------------------------------------------------ #
    def learn(self, state: np.ndarray, target: int,
              sims: np.ndarray | None = None) -> None:
        """One-shot Hebbian update.  Pass sims from a predict() call on the
        same state to skip recomputing the similarity matvec (the single
        dominant cost of a step)."""
        self.step_counter += 1
        best, best_sim = -1, -1.0
        if self.n_nodes > 0:
            if sims is None:
                sims = self.prototypes[: self.n_nodes] @ state
            best = int(np.argmax(sims))
            best_sim = float(sims[best])

        if best_sim < self.novelty_threshold:
            node = self._allocate()
            self.prototypes[node] = state
            self.outcome_counts[node] = 0.0
        else:
            node = best
            # Hebbian prototype drift toward the current state.
            p = self.prototypes[node]
            p += self.proto_lr * (state - p)
            p /= max(np.linalg.norm(p), 1e-8)

        self.outcome_counts[node, target] += 1.0
        self.usage[node] += 1.0
        self.last_used[node] = self.step_counter

        if self.prev_node is not None and self.prev_node != node:
            succ = self.edges.setdefault(self.prev_node, {})
            succ[node] = succ.get(node, 0.0) + 1.0
        self.prev_node = node

    # ------------------------------------------------------------------ #
    # growth & pruning                                                   #
    # ------------------------------------------------------------------ #
    def _allocate(self) -> int:
        if self.n_nodes < self.max_nodes:
            node = self.n_nodes
            self.n_nodes += 1
            self.grown += 1
            return node
        # Full: evict the least valuable node (low usage, long unused).
        age = self.step_counter - self.last_used[: self.n_nodes] + 1
        score = self.usage[: self.n_nodes] / age
        victim = int(np.argmin(score))
        self._forget(victim)
        self.pruned += 1
        return victim

    def _forget(self, node: int) -> None:
        self.usage[node] = 0.0
        self.outcome_counts[node] = 0.0
        self.edges.pop(node, None)
        for succ in self.edges.values():
            succ.pop(node, None)
        if self.prev_node == node:
            self.prev_node = None

    def nbytes(self) -> int:
        edge_bytes = sum(len(s) for s in self.edges.values()) * 24
        return int(
            self.prototypes.nbytes
            + self.outcome_counts.nbytes
            + self.usage.nbytes
            + self.last_used.nbytes
            + edge_bytes
        )
