#!/bin/bash
set -ex

echo "=== Starting Unison S3 Demo ==="

# Wait for moto to be available
echo "Waiting for Moto server..."
until curl --silent --fail http://moto:5050 > /dev/null 2>&1; do
    echo "Waiting for moto..."
    sleep 2
done
echo "Moto is ready!"

echo ""
echo "UCM version: $(ucm version 2>&1)"

# Codebase location
CODEBASE="/app/.unison/codebase"
mkdir -p "$CODEBASE"

# Create a transcript - pull base library for IO functions
cat > /tmp/init-and-run.md << 'TRANSCRIPT'
# Initialize and Run

First, merge builtins and pull base library:

```ucm
scratch/main> builtins.merge
scratch/main> lib.install @unison/base/releases/3.18.0
```

Now define a simple test:

```unison
helloWorld : '{IO, Exception} ()
helloWorld = do
  printLine "Hello from Unison!"
  printLine "UCM is working correctly!"
```

```ucm
scratch/main> add
scratch/main> run helloWorld
```
TRANSCRIPT

echo ""
echo "Transcript file:"
cat /tmp/init-and-run.md

echo ""
echo "=== Running UCM transcript ==="

# Disable pager and colors
export PAGER=cat
export TERM=dumb
export NO_COLOR=1
export LESS="-F -X"

# Run UCM with output piped through cat
yes "" 2>/dev/null | ucm -C "$CODEBASE" transcript /tmp/init-and-run.md 2>&1 | cat

echo ""
echo "=== Demo Complete ==="
