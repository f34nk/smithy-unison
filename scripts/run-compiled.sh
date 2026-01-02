#!/bin/bash
set -e

FILE_PATH=$@

if [ -z "$FILE_PATH" ]; then
    echo "Usage: $0 <file-path>"
    exit 1
fi

if [[ "$FILE_PATH" != *.uc ]]; then
    echo "ERROR: must be a compiled file: $FILE_PATH"
    exit 1
fi

if [ ! -f "$FILE_PATH" ]; then
    echo "ERROR: file does not exist: $FILE_PATH"
    exit 1
fi

echo "Run compiled: $FILE_PATH"

ucm run.compiled $FILE_PATH
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ]; then
    echo "ERROR: failed to run compiled: $FILE_PATH"
    exit 1
fi

exit 0
