"""KAN-style readout trained by the delta rule (the semantic "cortex").

The generalizing pathway.  Two trainable pieces, both *single layers*:

  1. Edge functions (the Kolmogorov-Arnold element): every state dimension
     d gets its own learnable scalar function psi_d, represented on a hat
     (piecewise-linear spline) basis of B knots:

         z_d = sum_j  C[d, j] * hat_j(s_d)

     Initialized to the identity function (coefficients = knot centers),
     so training only *bends* dimensions that benefit from it.  This buys
     per-dimension nonlinearity at a cost of D*B parameters instead of a
     D x D hidden matrix (for D=4096, B=8: 32K params vs 16.7M).

  2. A linear vocabulary layer:  logits = W z + b.

Training is the pure delta rule -- the one-step local gradient of the
cross-entropy at this layer:

         e      = softmax(logits) - onehot(target)
         W     -= lr * outer(e, z)                (rank-1 update)
         C[d]  -= lr * (W^T e)_d * hat(s_d)

No backpropagation through time or depth ever happens: the liquid below is
frozen random wiring, so the error path is exactly one step long.  There is
no autograd graph, no stored activations, no Adam moments -- the optimizer
state is the weights themselves.  Per-token cost O(V*D + D*B); memory is
constant for the lifetime of the process.
"""

from __future__ import annotations

import numpy as np


def softmax(x: np.ndarray) -> np.ndarray:
    x = x - x.max()
    e = np.exp(x)
    return e / e.sum()


class KANReadout:
    def __init__(
        self,
        dim: int,
        vocab_size: int,
        n_basis: int = 8,
        lr: float = 0.05,
        span: float = 2.5,
    ):
        self.dim = dim
        self.vocab_size = vocab_size
        self.lr = lr
        self.centers = np.linspace(-span, span, n_basis).astype(np.float32)
        self.width = float(self.centers[1] - self.centers[0])
        # Hat-basis coefficients = centers  =>  psi_d(x) == x at init.
        self.C = np.tile(self.centers, (dim, 1)).astype(np.float32)
        self.W = np.zeros((vocab_size, dim), dtype=np.float32)
        self.b = np.zeros(vocab_size, dtype=np.float32)
        # Scratch buffers reused every step (no per-step allocation churn).
        self._phi = np.zeros((dim, n_basis), dtype=np.float32)
        self._z = np.zeros(dim, dtype=np.float32)

    # ------------------------------------------------------------------ #
    def _features(self, s: np.ndarray) -> np.ndarray:
        """Evaluate all hat basis functions and the edge functions psi."""
        np.subtract(s[:, None], self.centers[None, :], out=self._phi)
        np.abs(self._phi, out=self._phi)
        self._phi *= -1.0 / self.width
        self._phi += 1.0
        np.clip(self._phi, 0.0, None, out=self._phi)
        np.einsum("db,db->d", self.C, self._phi, out=self._z)
        return self._z

    def forward(self, s: np.ndarray) -> np.ndarray:
        """Next-token distribution from a (scaled) liquid state."""
        z = self._features(s)
        return softmax(self.W @ z + self.b)

    def update(self, s: np.ndarray, target: int) -> None:
        """Delta-rule step: forward, then two local rank-1 corrections."""
        z = self._features(s)
        p = softmax(self.W @ z + self.b)
        p[target] -= 1.0                       # e = p - onehot(target)
        grad_z = self.W.T @ p                  # one-step credit assignment
        self.W -= self.lr * np.outer(p, z)
        self.b -= self.lr * p
        self.C -= self.lr * grad_z[:, None] * self._phi

    def nbytes(self) -> int:
        return int(self.C.nbytes + self.W.nbytes + self.b.nbytes + self._phi.nbytes)
