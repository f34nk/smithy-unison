#!/bin/bash
set -e

# Compile Unison code locally
# This script:
# 1. Installs/updates UCM from GitHub releases (same as Docker)
# 2. Compiles all Unison code to bytecode
# 3. Outputs compiled/main.uc

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# UCM version (must match docker-compose.yaml)
UCM_INSTALL_DIR="$SCRIPT_DIR/.ucm"
UCM_BIN="$UCM_INSTALL_DIR/ucm"

echo "=== Unison S3 Demo - Compile ==="
echo ""

# =============================================================================
# Check prerequisites
# =============================================================================

if [ ! -d "generated" ]; then
    echo "ERROR: generated/ directory not found. Run 'make generate' first."
    exit 1
fi

# =============================================================================
# Compile
# =============================================================================

# Create output directory
mkdir -p compiled

# Create temporary codebase
CODEBASE=$(mktemp -d)
echo "Using temporary codebase: $CODEBASE"

cleanup() {
    rm -rf "$CODEBASE"
    rm -f /tmp/compile.md
}
trap cleanup EXIT

# Get absolute path for output (UCM adds .uc extension automatically)
OUTPUT_FILE="$SCRIPT_DIR/compiled/main"

# Create UCM transcript
cat > /tmp/compile.md << TRANSCRIPT
# Compile Unison S3 Demo

Initialize with builtins and base library:

\`\`\`ucm
scratch/main> builtins.merge
scratch/main> lib.install @unison/base/releases/3.18.0
\`\`\`

Install HTTP library for real network requests:

\`\`\`ucm
scratch/main> lib.install @unison/http/releases/8.0.0
\`\`\`

Load AWS runtime modules:

\`\`\`ucm
scratch/main> load generated/aws_http.u
scratch/main> add
\`\`\`

\`\`\`ucm
scratch/main> load generated/aws_xml.u
scratch/main> add
\`\`\`

\`\`\`ucm
scratch/main> load generated/aws_sigv4.u
scratch/main> add
\`\`\`

\`\`\`ucm
scratch/main> load generated/aws_config.u
scratch/main> add
\`\`\`

\`\`\`ucm
scratch/main> load generated/aws_credentials.u
scratch/main> add
\`\`\`

\`\`\`ucm
scratch/main> load generated/aws_s3.u
scratch/main> add
\`\`\`

Load HTTP bridge module (enables real HTTP requests):

\`\`\`ucm
scratch/main> load generated/aws_http_bridge.u
scratch/main> add
\`\`\`

Load the generated S3 client:

\`\`\`ucm
scratch/main> load generated/aws_s3_client.u
scratch/main> add
\`\`\`

Load the main application:

\`\`\`ucm
scratch/main> load src/main.u
scratch/main> add
\`\`\`

Compile main to bytecode:

\`\`\`ucm
scratch/main> compile main $OUTPUT_FILE
\`\`\`
TRANSCRIPT

echo "=== Compiling Unison Code ==="
echo ""

# Disable pager
export PAGER=cat
export TERM=dumb
export NO_COLOR=1
export LESS="-F -X"

# Run UCM transcript
yes "" 2>/dev/null | "$UCM_BIN" -C "$CODEBASE" transcript /tmp/compile.md 2>&1 | cat

# Verify compiled output
if [ -f "compiled/main.uc" ]; then
    echo ""
    echo "=== Compilation Successful ==="
    echo "Output: compiled/main.uc"
    ls -la compiled/main.uc
else
    echo ""
    echo "=== Compilation Failed ==="
    echo "compiled/main.uc was not created"
    exit 1
fi
