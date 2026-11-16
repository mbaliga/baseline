#!/usr/bin/env python3
"""Fail if any tracked path cannot be checked out on Windows (R3), or a tracked .kt file has non-ASCII bytes."""
import re
import subprocess
import sys

BAD_CHARS = re.compile(r'[:<>|?*"]')
RESERVED = {"CON", "PRN", "AUX", "NUL"} | {f"COM{i}" for i in range(1, 10)} | {f"LPT{i}" for i in range(1, 10)}


def tracked():
    out = subprocess.run(["git", "ls-files", "-z"], check=True, capture_output=True).stdout
    return [p.decode("utf-8", "surrogateescape") for p in out.split(b"\0") if p]


def main():
    problems = []
    for path in tracked():
        if BAD_CHARS.search(path):
            problems.append(f"{path}: contains a character Windows forbids")
        for part in path.split("/"):
            if part.split(".")[0].upper() in RESERVED:
                problems.append(f"{path}: reserved device name '{part}'")
            if part != part.rstrip(" ."):
                problems.append(f"{path}: component '{part}' ends in space or dot")
        if path.endswith(".kt"):
            try:
                with open(path, "rb") as f:
                    f.read().decode("ascii")
            except FileNotFoundError:
                pass  # deleted in the working tree but still indexed
            except UnicodeDecodeError:
                problems.append(f"{path}: non-ASCII bytes in Kotlin source")
    for p in problems:
        print(p)
    print(f"{len(problems)} offending path(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
