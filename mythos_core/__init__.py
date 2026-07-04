"""mythos_core: a Holographic Attractor Machine.

A cognitive architecture prototype that replaces attention + backprop with
hyperdimensional algebra, a frozen liquid cellular automaton, a dynamic
sparse concept graph, and delta-rule KAN readouts.  See ARCHITECTURE.md.
"""

from .hd import Codebook, HDSpace
from .encoder import LiquidNCA
from .memory import ConceptGraph
from .readout import KANReadout
from .model import MythosCore

__all__ = [
    "HDSpace",
    "Codebook",
    "LiquidNCA",
    "ConceptGraph",
    "KANReadout",
    "MythosCore",
]
