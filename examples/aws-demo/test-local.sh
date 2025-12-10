#!/bin/bash
set -ex

# Local test script for UCM compilation
# This script tests if UCM can compile the generated runtime files
# without needing Docker or Moto.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Local UCM Compilation Test ==="
echo ""

# Check UCM is available - use local .ucm if system ucm not found
if command -v ucm &> /dev/null; then
    UCM="ucm"
elif [ -x "$SCRIPT_DIR/.ucm/ucm" ]; then
    UCM="$SCRIPT_DIR/.ucm/ucm"
else
    echo "ERROR: ucm is not installed or not in PATH"
    echo "Run ./install.sh first to install UCM locally"
    exit 1
fi

echo "UCM: $UCM"
echo "UCM version: $($UCM version 2>&1)"
echo ""

# Check generated files exist
if [ ! -d "generated" ]; then
    echo "ERROR: generated/ directory not found"
    echo "Run 'smithy build' first to generate the files"
    exit 1
fi

# Use a temporary codebase
CODEBASE=$(mktemp -d)
echo "Using temporary codebase: $CODEBASE"

# Cleanup on exit
cleanup() {
    rm -rf "$CODEBASE"
    rm -f /tmp/test-compile.md
}
trap cleanup EXIT

# Create transcript to test compilation
cat > /tmp/test-compile.md << 'TRANSCRIPT'
# Test UCM Compilation of Runtime Modules

Initialize with builtins and base library:

```ucm
scratch/main> builtins.merge
scratch/main> lib.install @unison/base/releases/3.18.0
```

Install HTTP library (required for Threads ability):

```ucm
scratch/main> lib.install @unison/http/releases/8.0.0
```

Load AWS runtime modules (order matters for dependencies):

```ucm
scratch/main> load generated/aws_http.u
scratch/main> add
```

```ucm
scratch/main> load generated/aws_xml.u
scratch/main> add
```

```ucm
scratch/main> load generated/aws_sigv4.u
scratch/main> add
```

```ucm
scratch/main> load generated/aws_config.u
scratch/main> add
```

```ucm
scratch/main> load generated/aws_credentials.u
scratch/main> add
```

```ucm
scratch/main> load generated/aws_s3.u
scratch/main> add
```

Load HTTP bridge (enables real HTTP via @unison/http):

```ucm
scratch/main> load generated/aws_http_bridge.u
scratch/main> add
```

Load the generated S3 client:

```ucm
scratch/main> load generated/aws_s3_client.u
scratch/main> add
```

Load the main application:

```ucm
scratch/main> load src/main.u
scratch/main> add
```

Test with a simple config (no Moto needed):

```unison
testCompile : '{IO, Exception} ()
testCompile = do
  printLine "=== S3 Client Loaded Successfully ==="
  printLine "All runtime modules and S3 client compiled without errors!"
  printLine "=== Compilation Test Complete ==="
```

```ucm
scratch/main> add
scratch/main> run testCompile
```
TRANSCRIPT

echo ""
echo "=== Running UCM transcript ==="
echo ""

# Disable pager and colors
export PAGER=cat
export TERM=dumb
export NO_COLOR=1
export LESS="-F -X"

# Run UCM
yes "" 2>/dev/null | $UCM -C "$CODEBASE" transcript /tmp/test-compile.md 2>&1 | cat

echo ""
echo "=== Test Complete ==="
