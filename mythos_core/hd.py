"""Hyperdimensional vector-space primitives (VSA, MAP-B family).

The entire architecture lives inside one vector space: R^D with D in the
thousands.  Concepts are (near-)bipolar hypervectors.  Three algebraic
operations replace the matrix machinery of deep networks:

  bind(a, b)   = a * b        elementwise product; invertible (self-inverse
                              for bipolar codes), creates a vector that is
                              quasi-orthogonal to both inputs -> represents
                              an *association* (role: filler).
  bundle(...)  = sign(sum)    majority vote; the result stays similar to
                              every input -> represents a *set / superposition*.
  permute(v)   = v[perm]      a fixed random permutation rho; rho^k encodes
                              order / position k, replacing positional
                              embeddings and attention offsets.

Why this works: in high dimensions, independently drawn random vectors are
almost orthogonal (|cos| ~ 1/sqrt(D)), so a single D-dim vector can hold a
superposition of O(D / log D) items that remain individually recoverable.
That superposition capacity is the "parameter-equivalent" trick: capacity
lives in the *space*, not in trained weight matrices.
"""

from __future__ import annotations

import numpy as np


class HDSpace:
    """A D-dimensional holographic vector space with a fixed permutation."""

    def __init__(self, dim: int = 4096, seed: int = 0):
        self.dim = dim
        self.rng = np.random.default_rng(seed)
        # One global random permutation rho; its powers encode sequence order.
        self.perm = self.rng.permutation(dim)
        self.inv_perm = np.argsort(self.perm)
        self._perm_powers = {0: np.arange(dim), 1: self.perm}

    # ------------------------------------------------------------------ #
    # vector creation                                                    #
    # ------------------------------------------------------------------ #
    def random_hv(self, n: int | None = None) -> np.ndarray:
        """Fresh random bipolar hypervector(s) in {-1, +1}^D."""
        shape = (self.dim,) if n is None else (n, self.dim)
        return self.rng.choice(np.array([-1.0, 1.0], dtype=np.float32), size=shape)

    # ------------------------------------------------------------------ #
    # the three VSA operations                                           #
    # ------------------------------------------------------------------ #
    @staticmethod
    def bind(a: np.ndarray, b: np.ndarray) -> np.ndarray:
        return a * b

    @staticmethod
    def bundle(*vectors: np.ndarray) -> np.ndarray:
        s = np.sum(vectors, axis=0) if len(vectors) > 1 else vectors[0]
        # sign() with ties broken toward +1 keeps the result bipolar.
        return np.where(s >= 0, 1.0, -1.0).astype(np.float32)

    def permute(self, v: np.ndarray, k: int = 1) -> np.ndarray:
        """Apply rho^k along the last axis (cached index composition)."""
        if k not in self._perm_powers:
            self._perm_powers[k] = self.perm[self.perm_power(k - 1)]
        return v[..., self._perm_powers[k]]

    def perm_power(self, k: int) -> np.ndarray:
        if k not in self._perm_powers:
            self._perm_powers[k] = self.perm[self.perm_power(k - 1)]
        return self._perm_powers[k]

    # ------------------------------------------------------------------ #
    # geometry                                                           #
    # ------------------------------------------------------------------ #
    @staticmethod
    def normalize(v: np.ndarray) -> np.ndarray:
        n = np.linalg.norm(v, axis=-1, keepdims=True)
        return v / np.maximum(n, 1e-8)

    @staticmethod
    def cosine(a: np.ndarray, b: np.ndarray) -> float:
        na, nb = np.linalg.norm(a), np.linalg.norm(b)
        if na < 1e-8 or nb < 1e-8:
            return 0.0
        return float(np.dot(a, b) / (na * nb))


class Codebook:
    """Token id -> frozen random hypervector.

    The codebook is *not trained*: random codes are already maximally
    separated in high dimension.  Growing it costs O(D) per new symbol,
    so the vocabulary is open-ended (continual learning of new symbols
    is trivial -- draw a fresh vector).
    """

    def __init__(self, space: HDSpace):
        self.space = space
        self._table: dict[int, np.ndarray] = {}

    def __getitem__(self, token_id: int) -> np.ndarray:
        hv = self._table.get(token_id)
        if hv is None:
            hv = self.space.random_hv()
            self._table[token_id] = hv
        return hv

    def __len__(self) -> int:
        return len(self._table)

    def nbytes(self) -> int:
        return len(self._table) * self.space.dim * 4
