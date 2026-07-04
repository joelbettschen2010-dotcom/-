"""MythosCore: the assembled Holographic Attractor Machine.

Dataflow per token (all costs constant in sequence length):

    token ---> Codebook -----------------------------.
                                                      v
    LiquidNCA ring  (frozen random liquid, K x D) --> state s (unit vector)
        |                                                |
        |                     .--------------------------+-----------.
        |                     v                                      v
        |         ConceptGraph.predict(s)                 KANReadout.forward(s*)
        |         episodic: grow/prune nodes,             semantic: delta-rule
        |         one-shot Hebbian counts                 spline readout
        |                     \                                      /
        |                      '---> confidence-gated blend  <------'
        |                                    |
        '---- step(token) after learning <---'   ==> P(next token)

The two pathways implement complementary learning systems: the graph
memorizes specifics instantly (high confidence when a situation repeats),
the readout distills slow statistical structure.  The blend gate trusts
the graph exactly in proportion to how strongly it recognizes the present.

Training == inference + two local updates.  There is no separate training
mode, no batch dimension, no backprop: the model learns continuously from
the stream it is predicting, inside a fixed memory envelope.
"""

from __future__ import annotations

import numpy as np

from .encoder import LiquidNCA
from .hd import Codebook, HDSpace
from .memory import ConceptGraph
from .readout import KANReadout


class MythosCore:
    def __init__(
        self,
        vocab_size: int,
        dim: int = 4096,
        n_cells: int = 16,
        max_nodes: int = 4096,
        novelty_threshold: float = 0.985,
        readout_lr: float = 0.05,
        graph_weight: float = 0.85,
        confidence_floor: float = 0.60,
        smoothing: float = 0.05,
        seed: int = 0,
    ):
        self.vocab_size = vocab_size
        self.space = HDSpace(dim=dim, seed=seed)
        self.codebook = Codebook(self.space)
        self.liquid = LiquidNCA(self.space, n_cells=n_cells, seed=seed + 1)
        self.graph = ConceptGraph(
            dim, vocab_size, max_nodes=max_nodes, novelty_threshold=novelty_threshold
        )
        # Short-horizon episodic memory over the fast cells only.  The
        # prediction backs off long -> short -> readout, so novel long
        # contexts still benefit from familiar recent history (the HD
        # analogue of n-gram backoff, but in constant memory).
        self.graph_short = ConceptGraph(
            dim, vocab_size, max_nodes=max_nodes,
            novelty_threshold=novelty_threshold,
        )
        self.readout = KANReadout(dim, vocab_size, lr=readout_lr)
        self.graph_weight = graph_weight
        self.confidence_floor = confidence_floor
        self.smoothing = smoothing
        # Unit states have entries ~ 1/sqrt(D); rescale so the KAN splines
        # (whose knots span roughly [-2.5, 2.5]) see O(1) inputs.
        self._state_scale = float(np.sqrt(dim))
        self._uniform = np.full(vocab_size, 1.0 / vocab_size, dtype=np.float32)

    def reset(self) -> None:
        self.liquid.reset()
        self.graph.prev_node = None
        self.graph_short.prev_node = None

    # ------------------------------------------------------------------ #
    def predict(self) -> np.ndarray:
        """P(next token | everything seen so far)."""
        return self._predict(self.liquid.state(), self.liquid.state_short())

    def _gate(self, confidence: float) -> float:
        g = (confidence - self.confidence_floor) / (1.0 - self.confidence_floor)
        return self.graph_weight * min(max(g, 0.0), 1.0)

    def _predict(self, s: np.ndarray, s_short: np.ndarray) -> np.ndarray:
        """Backoff blend: full-context recall -> short-context recall ->
        semantic readout, each trusted in proportion to how strongly it
        recognizes the present."""
        p_long, conf_long = self.graph.predict(s)
        p_short, conf_short = self.graph_short.predict(s_short)
        p = self.readout.forward(s * self._state_scale)
        if p_short is not None:
            g = self._gate(conf_short)
            p = g * p_short + (1.0 - g) * p
        if p_long is not None:
            g = self._gate(conf_long)
            p = g * p_long + (1.0 - g) * p
        # Calibration floor: never assign ~0 to any token.  Episodic recall
        # emits near-one-hot distributions that are catastrophic under a
        # log-loss when wrong; mixing in a little uniform mass is the same
        # smoothing every serious n-gram model uses, and it is what makes
        # bits-per-character a fair fight.
        if self.smoothing > 0.0:
            p = (1.0 - self.smoothing) * p + self.smoothing / self.vocab_size
        return p

    def step(self, token: int, learn: bool = True) -> np.ndarray:
        """Predict the incoming token, learn from it, then consume it.

        Returns the distribution that was predicted *before* seeing the
        token, so callers can score genuine next-token prediction.
        """
        s = self.liquid.state()
        s_short = self.liquid.state_short()
        prediction = self._predict(s, s_short)
        if learn:
            # The similarity matvecs from predict() are reused here, so the
            # two dominant operations run exactly once per token.
            self.graph.learn(s, token, sims=self.graph._last_sims)
            self.graph_short.learn(s_short, token,
                                   sims=self.graph_short._last_sims)
            self.readout.update(s * self._state_scale, token)
        self.liquid.step(self.codebook[token])
        return prediction

    def generate(self, prompt: list[int], n: int, temperature: float = 0.5,
                 rng: np.random.Generator | None = None) -> list[int]:
        """Free-run the machine: sample, feed back, repeat (no learning)."""
        rng = rng or np.random.default_rng(0)
        self.reset()
        for t in prompt:
            self.liquid.step(self.codebook[t])
        out = []
        for _ in range(n):
            p = self.predict()
            if temperature <= 0:
                t = int(np.argmax(p))
            else:
                logp = np.log(np.maximum(p, 1e-12)) / temperature
                logp -= logp.max()
                q = np.exp(logp)
                t = int(rng.choice(self.vocab_size, p=q / q.sum()))
            out.append(t)
            self.liquid.step(self.codebook[t])
        return out

    # ------------------------------------------------------------------ #
    def memory_report(self) -> dict[str, int]:
        """Exact bytes held by every component (the whole model)."""
        return {
            "codebook": self.codebook.nbytes(),
            "liquid": self.liquid.nbytes(),
            "concept_graph": self.graph.nbytes(),
            "concept_graph_short": self.graph_short.nbytes(),
            "kan_readout": self.readout.nbytes(),
        }
