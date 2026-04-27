"""Crude .ppt text extractor via olefile.

Reads the PowerPointDocument stream and pulls ASCII/UTF-16LE text
runs. Good enough to recover slide titles / bullet text for the
AuthenticSelf report template where we just need the section names.
"""
from __future__ import annotations
import re, sys
import olefile

PATH = r"C:/AuthenticSelf_Project/AuthenticSelf_v3/중간결과보고양식_v1.ppt"
ole = olefile.OleFileIO(PATH)

# Dump all streams for diagnostic value.
print("streams:", ole.listdir())
try:
    stream = ole.openstream("PowerPoint Document")
    data = stream.read()
    print(f"PowerPoint Document stream: {len(data)} bytes")
except Exception as e:
    print("no PowerPoint Document stream:", e)
    sys.exit(0)

# Extract UTF-16LE runs (printable Korean + ASCII).
i = 0
runs = []
current = bytearray()
while i < len(data) - 1:
    lo, hi = data[i], data[i + 1]
    # Korean range (U+AC00..U+D7A3), ASCII printable, newline, space, tab
    cp = lo | (hi << 8)
    printable = (
        (0x20 <= cp < 0x7F)
        or cp in (0x09, 0x0A, 0x0D)
        or 0xAC00 <= cp <= 0xD7A3
        or 0x3131 <= cp <= 0x318E
        or 0xFF00 <= cp <= 0xFFEF
    )
    if printable:
        current += bytes([lo, hi])
        i += 2
    else:
        if len(current) >= 6:
            try:
                s = current.decode("utf-16-le", errors="ignore")
                if any(c.isalnum() for c in s):
                    runs.append(s.strip())
            except Exception:
                pass
        current = bytearray()
        i += 1

# De-dupe preserving order
seen = set()
out = []
for r in runs:
    r = re.sub(r"\s+", " ", r).strip()
    if r and r not in seen:
        seen.add(r)
        out.append(r)

with open(r"C:/AuthenticSelf_Project/AuthenticSelf_v3/var/ppt_text.txt", "w", encoding="utf-8") as f:
    for r in out:
        f.write(r + "\n")
print(f"wrote {len(out)} runs")
