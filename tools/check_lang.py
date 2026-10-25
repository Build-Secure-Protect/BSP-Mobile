#!/usr/bin/env python3
"""Checks the screen text: every key the client code looks up exists in the language file, and no value breaks
Minecraft's formatting. A missing key would show in game as the raw key, so it is caught here instead.

  python3 tools/gen_lang.py && python3 tools/check_lang.py
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CLIENT = ROOT / "src/main/java/com/mrgregles/bsp_mobile/client"
LANG = ROOT / "src/main/resources/assets/bsp_mobile/lang/en_us.json"
P = "gui.bsp_mobile."


def main():
    lang = json.loads(LANG.read_text())
    sources = list(CLIENT.glob("*.java")) if CLIENT.is_dir() else []
    every = "\n".join(p.read_text() for p in sources)
    needed = set(re.findall(r'Tr\.t\("([a-z0-9_.]+)"', every))
    # keys chosen by a condition: Tr.t(flag ? "a" : "b")
    for a, b in re.findall(r'Tr\.t\([^"]*?\?\s*"([a-z0-9_.]+)"\s*:\s*"([a-z0-9_.]+)"', every):
        needed |= {a, b}
    # keys built from a prefix at run time: Tr.t("note." + x) and Tr.t("reason." + x)
    prefixes = set(re.findall(r'Tr\.(?:t|has)\("([a-z0-9_.]+\.)"\s*\+', every))
    needed = {k for k in needed if not k.endswith('.')}  # a prefix, not a key
    missing = sorted(k for k in needed if P + k not in lang)
    unused = sorted(k[len(P):] for k in lang if k.startswith(P) and k[len(P):] not in needed
                    and not any(k[len(P):].startswith(pre) for pre in prefixes))
    bad = 0
    for k, v in lang.items():
        if re.search(r"(?<!%)%(?![s%])", v):
            print("BAD %   ", k, repr(v), "(write %% for a percent sign)")
            bad += 1
    for k in missing:
        print("MISSING", P + k)
    for k in unused:
        print("unused ", P + k)
    print(f"{len(needed)} keys used, {len(missing)} missing, {len(unused)} unused")
    return 1 if missing or bad else 0


if __name__ == "__main__":
    sys.exit(main())
