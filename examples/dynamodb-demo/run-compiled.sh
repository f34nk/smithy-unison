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

export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:4566

# Wait for mocked infrastructure to be available
echo "Waiting for LocalStack ..."
until curl --silent --fail http://localhost:4566 > /dev/null 2>&1; do
    echo "Waiting for LocalStack..."
    sleep 2
done
echo "LocalStack is ready!"

ucm run.compiled $FILE_PATH
