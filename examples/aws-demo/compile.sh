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
UCM_VERSION="${UCM_VERSION:-1.0.0}"
UCM_INSTALL_DIR="$SCRIPT_DIR/.ucm"
UCM_BIN="$UCM_INSTALL_DIR/ucm"

echo "=== Unison S3 Demo - Compile ==="
echo ""

# =============================================================================
# Install/Update UCM
# =============================================================================

install_ucm() {
    echo "Installing UCM ${UCM_VERSION}..."
    
    # Detect architecture
    ARCH=$(uname -m)
    case "$ARCH" in
        arm64|aarch64) UCM_ARCH="arm64" ;;
        x86_64|amd64) UCM_ARCH="x64" ;;
        *) echo "ERROR: Unsupported architecture: $ARCH"; exit 1 ;;
    esac
    
    # Detect OS
    OS=$(uname -s)
    case "$OS" in
        Darwin) UCM_OS="macos" ;;
        Linux) UCM_OS="linux" ;;
        *) echo "ERROR: Unsupported OS: $OS"; exit 1 ;;
    esac
    
    # Download URL
    UCM_URL="https://github.com/unisonweb/unison/releases/download/release/${UCM_VERSION}/ucm-${UCM_OS}-${UCM_ARCH}.tar.gz"
    
    echo "Downloading from: $UCM_URL"
    
    # Create install directory
    rm -rf "$UCM_INSTALL_DIR"
    mkdir -p "$UCM_INSTALL_DIR"
    
    # Download and extract
    curl -fsSL "$UCM_URL" | tar -xz -C "$UCM_INSTALL_DIR"
    
    # Make executable
    chmod +x "$UCM_BIN"
    
    echo "UCM installed to: $UCM_INSTALL_DIR"
}

# Check if UCM needs to be installed or updated
if [ ! -f "$UCM_BIN" ]; then
    echo "UCM not found"
    install_ucm
else
    CURRENT_VERSION=$("$UCM_BIN" version 2>&1 || echo "unknown")
    echo "Current UCM: $CURRENT_VERSION"
    
    # Check if version matches
    if echo "$CURRENT_VERSION" | grep -q "$UCM_VERSION"; then
        echo "UCM version OK"
    else
        echo "UCM version mismatch. Reinstalling..."
        install_ucm
    fi
fi

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
