#!/usr/bin/env python3
"""Поднимает версию в gradle.properties на единицу в последнем разряде с переносом: 0.0.9 -> 0.1.0."""
import re, pathlib
f = pathlib.Path(__file__).resolve().parent.parent / "gradle.properties"
t = f.read_text(encoding="utf-8")
m = re.search(r"^VERSION_NAME=(\d)\.(\d)\.(\d)$", t, re.M)
a, b, c = map(int, m.groups())
c += 1
if c > 9: c, b = 0, b + 1
if b > 9: b, a = 0, a + 1
if a > 9: raise SystemExit("Версия переполнена")
new = f"{a}.{b}.{c}"
f.write_text(t.replace(m.group(0), f"VERSION_NAME={new}"), encoding="utf-8")
print(f"{m.group(0).split('=')[1]} -> {new} (versionCode {a*100+b*10+c})")
