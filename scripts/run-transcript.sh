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

cleanup() {
    rm -rf "$CODEBASE"
}
trap cleanup EXIT

# Disable pager
export PAGER=cat
export TERM=dumb
export NO_COLOR=1
export LESS="-F -X"

echo "Run transcript: $FILE_PATH"

yes "" 2>/dev/null | ucm -C "$CODEBASE" transcript $FILE_PATH
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ]; then
    echo "ERROR: failed to run transcript: $FILE_PATH"
    exit 1
fi

exit 0
