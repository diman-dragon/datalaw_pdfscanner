#!/usr/bin/env python3
"""Checks the layers of the app: no package depends on another one in a circle, and the low layers know nothing of the screens.

    data, pdf, imaging, settings   (the low layers: files, PDF, pictures, preferences)
      <- core                           (what everybody shares: the graph, dialogs, texts, navigation contract, camera contract)
      <- ui                             (theme, widgets, dialogs on screen)
      <- camera, scan, library, docedit, prefs   (the features)
      <- app                            (the shell: tabs, overlays, the view model)

A feature builds its buttons only of the kit (PrimaryButton, SecondaryButton, GlyphButton).

Run from the repository root: python3 tools/check_layers.py. Exit code 1 = a violation.
"""
import collections
import glob
import re
import sys

BASE = "app/src/main/java/com/datalaw/pdfscanner/"
PKG = "com.datalaw.pdfscanner"
LOW = {"data", "pdf", "imaging", "settings"}
HIGH = {"core", "ui", "app"}
FEATURES = {"camera", "scan", "library", "docedit", "prefs"}
# a screen has no buttons of its own: PrimaryButton / SecondaryButton / GlyphButton of the kit (ui) only
RAW_BUTTON = re.compile(r"(?<![A-Za-z])(Outlined|Text|Filled|Elevated)?Button\(")


def top(path: str) -> str:
    rest = path[len(BASE):]
    return rest.split("/")[0] if "/" in rest else "root"


graph = collections.defaultdict(set)
errors = []
for f in glob.glob(BASE + "**/*.kt", recursive=True):
    a = top(f)
    if a in FEATURES:
        for n, line in enumerate(open(f).read().splitlines(), 1):
            if RAW_BUTTON.search(line.split("//")[0]):
                errors.append(f"{f}:{n}: a raw Material button in a feature; use PrimaryButton / SecondaryButton from ui")
    for m in re.finditer(r"^import " + re.escape(PKG) + r"\.(\w+)\.", open(f).read(), re.M):
        b = m.group(1)
        if b == a or not b[0].islower():
            continue
        graph[a].add(b)
        if a in LOW and b in HIGH:
            errors.append(f"{f}: the low layer '{a}' imports '{b}'")

for a in sorted(graph):
    for b in sorted(graph[a]):
        if a in graph.get(b, ()) and a < b:
            errors.append(f"'{a}' and '{b}' import each other")

state, path = {}, []


def visit(n):
    if state.get(n) == 2:
        return
    if state.get(n) == 1:
        errors.append("a circle: " + " -> ".join(path[path.index(n):] + [n]))
        return
    state[n] = 1
    path.append(n)
    for m in sorted(graph.get(n, ())):
        visit(m)
    path.pop()
    state[n] = 2


for n in sorted(graph):
    visit(n)

if errors:
    print("\n".join(sorted(set(errors))))
    sys.exit(1)
print("layers ok:", ", ".join(f"{a} -> {len(graph[a])}" for a in sorted(graph)))
