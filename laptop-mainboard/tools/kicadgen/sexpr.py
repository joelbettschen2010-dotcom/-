"""Kleine Helfer fuer deterministische KiCad-S-Expression-Erzeugung."""
import uuid

_NS = uuid.UUID("7c9a1f00-6b1e-4b6a-9e6c-2f3d4e5a6b7c")
_counter = [0]


def uid(tag=""):
    """Deterministische UUID (reproduzierbarer Output bei jedem Lauf)."""
    _counter[0] += 1
    return str(uuid.uuid5(_NS, f"{tag}:{_counter[0]}"))


def reset_uids():
    _counter[0] = 0


def f(v):
    """Zahl kompakt formatieren (KiCad-Stil)."""
    if isinstance(v, float):
        s = f"{v:.4f}".rstrip("0").rstrip(".")
        return s if s not in ("-0", "") else "0"
    return str(v)


def check_balanced(text, name):
    """Pruefe Klammerbalance und Quote-Paarung der erzeugten Datei."""
    depth = 0
    in_str = False
    prev = ""
    for ch in text:
        if in_str:
            if ch == '"' and prev != "\\":
                in_str = False
        else:
            if ch == '"':
                in_str = True
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth < 0:
                    raise ValueError(f"{name}: Klammer zu viel geschlossen")
        prev = ch
    if in_str:
        raise ValueError(f"{name}: offener String")
    if depth != 0:
        raise ValueError(f"{name}: Klammerbilanz {depth}")
