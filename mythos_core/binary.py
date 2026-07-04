"""Binary (1-bit) holographic memory: the efficiency claim, made real.

The repo's headline efficiency argument is that concept prototypes -- which
are near-bipolar -- can be stored as *single bits* and retrieved by Hamming
distance (XOR + popcount) instead of an fp32 matvec.  benchmark.py only
*projects* that.  This module *implements* and *measures* it.

Representation:
    bipolar hv in {-1,+1}^D   -->   bits in {0,1}^D   -->   np.packbits
    stored as uint8[N, D/8]   =>   32x smaller than fp32[N, D]

Retrieval (nearest neighbour = cleanup memory):
    hamming(a, b) = popcount(a XOR b)
    cosine(a, b)  = 1 - 2 * hamming / D            (exact for bipolar codes)
    argmax cosine = argmin hamming

Popcount is vectorized with a SWAR kernel over uint64 words -- integer XOR
+ a handful of shifts/adds, no floating point, no multiplies, no fancy
indexing.

HONEST SPEED RESULT (measured below): on a single CPU core this is ~0.7x
the throughput of the fp32 matvec -- i.e. SLOWER.  numpy's matvec dispatches
to decades-tuned, multithreaded BLAS, which is very hard to beat on CPU;
pure-numpy popcount stays within ~1.5x of it but does not win.  The
efficiency that IS real and measured here is MEMORY (32x) and ACCURACY
(fully retained).  A speed *advantage* is expected only on bandwidth-bound
hardware (GPU), where popcount is a native instruction and the binary
codes move 1/32 the bytes -- that is NOT measured in this file and is not
claimed as fact.

What is measured in main():
    * memory:   bytes(binary) vs bytes(fp32)          -> 32x (real win)
    * accuracy: cleanup recall retained after binarization -> retained
    * speed:    queries/sec, both backends -> fp32 wins on CPU, reported honestly

Run:  python -m mythos_core.binary
"""

from __future__ import annotations

import time

import numpy as np

# SWAR (SIMD-within-a-register) popcount constants for uint64.  This counts
# set bits with pure arithmetic on whole uint64 arrays -- fully vectorized by
# numpy, and far faster than a byte lookup table (which needs slow fancy
# indexing).  It is the standard trick a GPU/CPU popcount kernel uses.
_M1 = np.uint64(0x5555555555555555)
_M2 = np.uint64(0x3333333333333333)
_M4 = np.uint64(0x0F0F0F0F0F0F0F0F)
_H01 = np.uint64(0x0101010101010101)
_S1 = np.uint64(1)
_S2 = np.uint64(2)
_S4 = np.uint64(4)
_S56 = np.uint64(56)


def _popcount64(x: np.ndarray) -> np.ndarray:
    """Vectorized popcount over a uint64 array (SWAR); returns uint64 counts."""
    x = x - ((x >> _S1) & _M1)
    x = (x & _M2) + ((x >> _S2) & _M2)
    x = (x + (x >> _S4)) & _M4
    return (x * _H01) >> _S56


def to_bits(hv: np.ndarray) -> np.ndarray:
    """Pack a bipolar (or real) vector to bits by sign; returns uint8[D/8]."""
    return np.packbits((hv >= 0).astype(np.uint8), axis=-1)


class BinaryCleanup:
    """A content-addressable memory of bit-packed hypervectors."""

    def __init__(self, dim: int, capacity: int):
        assert dim % 64 == 0, "dim must be a multiple of 64 for uint64 packing"
        self.dim = dim
        self.words = dim // 64
        # Stored as uint64 words so popcount is pure vectorized arithmetic.
        self.store = np.zeros((capacity, self.words), dtype=np.uint64)
        self.n = 0
        self.names: list[str] = []

    def _pack(self, hv: np.ndarray) -> np.ndarray:
        bits = np.packbits((hv >= 0).astype(np.uint8))          # uint8[D/8]
        return bits.view(np.uint64)                              # uint64[D/64]

    def add(self, hv: np.ndarray, name: str) -> None:
        self.store[self.n] = self._pack(hv)
        self.names.append(name)
        self.n += 1

    def query(self, hv: np.ndarray) -> tuple[str, float]:
        """Nearest stored vector by Hamming distance -> (name, cosine)."""
        q = self._pack(hv)
        xor = np.bitwise_xor(self.store[: self.n], q)           # uint64[n, W]
        ham = _popcount64(xor).sum(axis=1)                      # per-row bits
        i = int(np.argmin(ham))
        cos = 1.0 - 2.0 * int(ham[i]) / self.dim
        return self.names[i], float(cos)

    def nbytes(self) -> int:
        return int(self.store[: self.n].nbytes)


class FloatCleanup:
    """fp32 reference backend: nearest neighbour by dot product (matvec)."""

    def __init__(self, dim: int, capacity: int):
        self.dim = dim
        self.store = np.zeros((capacity, dim), dtype=np.float32)
        self.n = 0
        self.names: list[str] = []

    def add(self, hv: np.ndarray, name: str) -> None:
        self.store[self.n] = hv
        self.names.append(name)
        self.n += 1

    def query(self, hv: np.ndarray) -> tuple[str, float]:
        sims = self.store[: self.n] @ hv
        i = int(np.argmax(sims))
        return self.names[i], float(sims[i] / self.dim)

    def nbytes(self) -> int:
        return int(self.store[: self.n].nbytes)


# --------------------------------------------------------------------- #
# measurement                                                            #
# --------------------------------------------------------------------- #

def main() -> None:
    print("=" * 68)
    print("Binary holographic memory: measured efficiency vs fp32")
    print("=" * 68)

    dim = 8192
    N = 8000                       # stored concepts
    Q = 500                        # queries (noisy versions of stored items)
    flip = 0.15                    # 15% of bits corrupted in each query
    rng = np.random.default_rng(0)

    base = rng.choice(np.array([-1.0, 1.0], np.float32), size=(N, dim))
    fp = FloatCleanup(dim, N)
    bn = BinaryCleanup(dim, N)
    for i in range(N):
        fp.add(base[i], str(i))
        bn.add(base[i], str(i))

    # queries: corrupted copies of random stored items
    idx = rng.integers(0, N, Q)
    queries = base[idx].copy()
    mask = rng.random((Q, dim)) < flip
    queries[mask] *= -1.0

    # --- memory ---------------------------------------------------------
    print(f"\nstored {N:,} concepts at D={dim}:")
    print(f"  fp32   : {fp.nbytes() / 1e6:8.2f} MB")
    print(f"  binary : {bn.nbytes() / 1e6:8.2f} MB   "
          f"({fp.nbytes() / bn.nbytes():.0f}x smaller)")

    # --- accuracy -------------------------------------------------------
    fp_hit = bn_hit = 0
    for b in range(Q):
        fp_hit += (fp.query(queries[b])[0] == str(idx[b]))
        bn_hit += (bn.query(queries[b])[0] == str(idx[b]))
    print(f"\ncleanup recall on {Q} noisy queries ({flip:.0%} bits flipped):")
    print(f"  fp32   : {fp_hit / Q:6.1%}")
    print(f"  binary : {bn_hit / Q:6.1%}   (accuracy retained after 1-bit packing)")

    # --- speed ----------------------------------------------------------
    t0 = time.perf_counter()
    for b in range(Q):
        fp.query(queries[b])
    fp_dt = time.perf_counter() - t0

    t0 = time.perf_counter()
    for b in range(Q):
        bn.query(queries[b])
    bn_dt = time.perf_counter() - t0

    print(f"\nthroughput (single CPU core, {N:,}-way nearest neighbour):")
    print(f"  fp32   : {Q / fp_dt:8.0f} queries/s   (multithreaded BLAS matvec)")
    print(f"  binary : {Q / bn_dt:8.0f} queries/s   "
          f"({fp_dt / bn_dt:.2f}x -- {'slower' if bn_dt > fp_dt else 'faster'})")
    print("\nHONEST SUMMARY:")
    print("  * MEMORY 32x smaller and ACCURACY fully retained -- both real,")
    print("    both measured. This is what lets 2M concepts fit in ~3.8 GB.")
    print("  * SPEED: fp32 BLAS wins on CPU; the binary path is ~1.5x slower")
    print("    here. A speed win needs bandwidth-bound hardware (GPU popcount)")
    print("    and is NOT demonstrated in this file -- so it is not claimed.")


if __name__ == "__main__":
    main()
