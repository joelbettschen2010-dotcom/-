"""Liquid Hyperdimensional Cellular Automaton (the "thinking substrate").

This module replaces both the attention mechanism and the recurrent weight
matrices of conventional sequence models.  The working memory is a small
ring of K cells; each cell holds one D-dimensional hypervector.  Every
timestep, one *local* update rule runs (the Neural-Cellular-Automaton
element):

    h_i  <-  tanh( alpha_i * h_i                 # leaky self-memory
                 + beta    * rho(h_{i-1})        # permuted neighbor coupling
                 + gamma   * (g_i (*) x_t) )     # gated input injection

  * alpha_i is a per-cell decay constant, log-spaced from ~0.35 to ~0.985.
    The ring is therefore a *liquid filterbank over timescales* (the Liquid
    Neural Network element): fast cells hold the last few tokens, slow cells
    hold paragraph-scale context -- continuous-time dynamics discretized.
  * rho() is the space's random permutation.  Permuting the neighbor's state
    before mixing keeps information from different cells quasi-orthogonal,
    so the ring circulates history without overwriting it.
  * g_i is a fixed random bipolar gate vector per cell; binding the input
    with g_i gives every cell its own "view" of the token (a random role),
    exactly like multi-head projections -- but with zero trained weights.

Nothing in here is learned.  Random wiring is sufficient because random
projections in high dimension preserve information (Johnson-Lindenstrauss);
all learning is deferred to the one-layer readouts downstream.

Cost per token: O(K * D) elementwise ops.  Memory: O(K * D) -- constant in
sequence length.  There is no KV cache and no O(N^2) term anywhere.
"""

from __future__ import annotations

import numpy as np

from .hd import HDSpace


class LiquidNCA:
    def __init__(
        self,
        space: HDSpace,
        n_cells: int = 16,
        beta: float = 0.25,
        gamma: float = 1.0,
        seed: int = 1,
    ):
        self.space = space
        self.K = n_cells
        self.beta = beta
        self.gamma = gamma
        # Per-cell leak: geometric spacing => timescales from ~1.5 to ~65 tokens.
        self.alpha = np.geomspace(0.35, 0.985, n_cells).astype(np.float32)[:, None]
        # Canonical leaky-integrator drive scaling: a cell integrating over
        # timescale 1/(1-alpha) receives input weighted by (1-alpha), so
        # slow cells reflect the *average* of many tokens rather than being
        # slammed by the current one.  This is what makes the bundled state
        # genuinely multi-timescale instead of last-token-dominated.
        self.drive = (1.0 - self.alpha).astype(np.float32)
        rng = np.random.default_rng(seed)
        # Fixed random gates: each cell binds the input with its own role vector.
        self.gates = rng.choice(
            np.array([-1.0, 1.0], dtype=np.float32), size=(n_cells, space.dim)
        )
        self.h = np.zeros((n_cells, space.dim), dtype=np.float32)
        # Running mean of the bundled state.  Slow cells saturate under
        # tanh, which gives every state a large *static* component; the
        # mean is subtracted before similarity queries so that cosine
        # measures what is *different* about the present, not what is
        # always true.  (A one-line analogue of layer normalization /
        # homeostatic adaptation -- and still training-free.)
        self.mu = np.zeros(space.dim, dtype=np.float32)
        self.mu_rate = 0.02
        self._acc = np.zeros(space.dim, dtype=np.float32)

    def reset(self) -> None:
        self.h[:] = 0.0
        self._acc[:] = 0.0
        # mu is a long-run statistic; it survives episode resets on purpose.

    def step(self, x: np.ndarray) -> None:
        """Advance the automaton by one token hypervector x."""
        neighbor = np.roll(self.h, 1, axis=0)          # ring topology
        neighbor = self.space.permute(neighbor)         # rho along last axis
        pre = self.alpha * self.h + self.drive * (
            self.beta * neighbor + self.gamma * (self.gates * x)
        )
        # tanh keeps every cell bounded (no exploding state, ever) and acts
        # as the soft spike-rate nonlinearity of the liquid.
        np.tanh(pre, out=self.h)
        self._bundle()
        self.mu += self.mu_rate * (self._acc - self.mu)

    def _bundle(self) -> None:
        """Collapse the ring into one vector: each cell is tagged with a
        distinct permutation power rho^i before summing, so cells remain
        individually addressable inside the superposition (a holographic
        reduced representation of the whole working memory).  Cells are
        RMS-equalized first so every timescale gets an equal vote in the
        query, regardless of how saturated its tanh dynamics run."""
        norms = np.linalg.norm(self.h, axis=1) / np.sqrt(self.space.dim)
        self._acc[:] = 0.0
        for i in range(self.K):
            self._acc += self.h[i][self.space.perm_power(i)] / (norms[i] + 1e-6)

    def state(self) -> np.ndarray:
        """Centered, unit-norm query vector for the current instant."""
        return self.space.normalize(self._acc - self.mu)

    def nbytes(self) -> int:
        return int(self.h.nbytes + self.gates.nbytes + self.mu.nbytes + self._acc.nbytes)
