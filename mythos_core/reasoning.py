"""Symbolic reasoning by pure vector algebra (no network, no training).

Demonstrates the property that makes the HDC substrate more than a fancy
embedding: *structured records and analogies are algebraic operations*.

A record is a bundle of role-filler bindings:

    USA    = NAME (*) usa  +  CAPITAL (*) washington  +  CURRENCY (*) dollar
    MEXICO = NAME (*) mex  +  CAPITAL (*) mexico_city +  CURRENCY (*) peso

Because binding is self-inverse and distributes over bundling, the single
product  F = USA (*) MEXICO  is itself a mapping that pairs analogous
fillers across the two records.  Kanerva's classic query
"What is the dollar of Mexico?" becomes one multiply:

    F (*) dollar  ~  peso        (all cross terms vanish as noise)

This is O(D) reasoning: role extraction, analogy, and multi-hop chaining
without a single trained parameter or attention head.

Run:  python -m mythos_core.reasoning
"""

from __future__ import annotations

from .hd import HDSpace


def main() -> None:
    space = HDSpace(dim=8192, seed=42)

    # Atomic symbols: roles and fillers are just random hypervectors.
    names = ["NAME", "CAPITAL", "CURRENCY",
             "usa", "washington", "dollar",
             "mexico", "mexico_city", "peso",
             "japan", "tokyo", "yen"]
    sym = {n: space.random_hv() for n in names}
    bind, bundle, cos = space.bind, space.bundle, space.cosine

    def record(name: str, capital: str, currency: str):
        return bundle(
            bind(sym["NAME"], sym[name]),
            bind(sym["CAPITAL"], sym[capital]),
            bind(sym["CURRENCY"], sym[currency]),
        )

    usa = record("usa", "washington", "dollar")
    mex = record("mexico", "mexico_city", "peso")
    jpn = record("japan", "tokyo", "yen")

    fillers = ["usa", "washington", "dollar", "mexico", "mexico_city", "peso",
               "japan", "tokyo", "yen"]

    def cleanup(v):
        """Nearest stored symbol = holographic 'cleanup memory' lookup."""
        scored = sorted(((cos(v, sym[f]), f) for f in fillers), reverse=True)
        return scored[0], scored[1]

    print("=" * 64)
    print("VSA reasoning demo  (D = 8192, zero trained parameters)")
    print("=" * 64)

    # 1. Field extraction: unbind a role from a record.
    (s1, a1), (s2, a2) = cleanup(bind(usa, sym["CAPITAL"]))
    print(f"\ncapital of USA        -> {a1:12s} (cos={s1:+.3f}; runner-up {a2} {s2:+.3f})")

    # 2. Kanerva analogy: 'What is the dollar of Mexico?'
    f_map = bind(usa, mex)                      # holistic USA<->Mexico mapping
    (s1, a1), (s2, a2) = cleanup(bind(f_map, sym["dollar"]))
    print(f"dollar of Mexico      -> {a1:12s} (cos={s1:+.3f}; runner-up {a2} {s2:+.3f})")

    # 3. The same mapping reused for a different slot -- it generalizes
    #    across roles because it was never built for a specific one.
    (s1, a1), (s2, a2) = cleanup(bind(f_map, sym["washington"]))
    print(f"'washington' of Mexico-> {a1:12s} (cos={s1:+.3f}; runner-up {a2} {s2:+.3f})")

    # 4. Two-hop chain: map USA->Mexico, then Mexico->Japan, composed
    #    into a single vector before querying.
    chain = bind(bind(usa, mex), bind(mex, jpn))   # ~ USA<->Japan mapping
    (s1, a1), (s2, a2) = cleanup(bind(chain, sym["dollar"]))
    print(f"dollar, USA=>MX=>JP   -> {a1:12s} (cos={s1:+.3f}; runner-up {a2} {s2:+.3f})")

    print("\nEvery answer above is one elementwise multiply + one nearest-")
    print("neighbor lookup: reasoning as algebra, not as forward passes.")


if __name__ == "__main__":
    main()
