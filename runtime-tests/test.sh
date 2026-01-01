#!/bin/bash
set -e

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

# Run UCM transcript
yes "" 2>/dev/null | ucm -C "$CODEBASE" transcript test.md 2>&1 | cat
