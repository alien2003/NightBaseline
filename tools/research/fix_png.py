#!/usr/bin/env python3
"""Strips text that adb screencap prints before the PNG signature (e.g. a multi-display warning)."""
import sys

SIG = b"\x89PNG\r\n\x1a\n"
for path in sys.argv[1:]:
    data = open(path, "rb").read()
    i = data.find(SIG)
    if i < 0:
        print(f"{path}: no PNG data")
    elif i > 0:
        open(path, "wb").write(data[i:])
        print(f"{path}: stripped {i} bytes")
    else:
        print(f"{path}: ok")
