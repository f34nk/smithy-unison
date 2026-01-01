#!/usr/bin/env bash
set -e

FILE_PATH=$@

if [ -z "$FILE_PATH" ]; then
    echo "Usage: $0 <file-path>"
    exit 1
fi

if [[ "$FILE_PATH" != *.md ]]; then
    echo "ERROR: must be a .md file: $FILE_PATH"
    exit 1
fi

if [ ! -f "$FILE_PATH" ]; then
    echo "ERROR: file does not exist: $FILE_PATH"
    exit 1
fi

# Create temporary codebase
CODEBASE=$(mktemp -d)
echo "Using temporary codebase: $CODEBASE"

cleanup() {
    rm -rf "$CODEBASE"
}
trap cleanup EXIT

# Disable pager
export PAGER=cat
export TERM=dumb
export NO_COLOR=1
export LESS="-F -X"

FILE=$(basename "$FILE_PATH")
echo "Running transcript: $FILE"

# Run UCM transcript
yes "" 2>/dev/null | ucm -C "$CODEBASE" transcript $FILE 2>&1 | cat
