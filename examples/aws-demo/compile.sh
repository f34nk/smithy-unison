#!/bin/bash
set -e

# Compile Unison code locally
# This script:
# 1. Compiles all Unison code to bytecode
# 2. Outputs compiled/main.uc

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

Create namespace aliases so main.u can use lib.f34nk_aws_0_1_1 imports:

\`\`\`ucm
scratch/main> alias.type Config lib.f34nk_aws_0_1_1.Config
scratch/main> alias.type Credentials lib.f34nk_aws_0_1_1.Credentials
scratch/main> alias.type ListBucketsRequest lib.f34nk_aws_0_1_1.ListBucketsRequest
scratch/main> alias.type ListBucketsOutput lib.f34nk_aws_0_1_1.ListBucketsOutput
scratch/main> alias.type Bucket lib.f34nk_aws_0_1_1.Bucket
scratch/main> alias.type PutObjectRequest lib.f34nk_aws_0_1_1.PutObjectRequest
scratch/main> alias.type PutObjectOutput lib.f34nk_aws_0_1_1.PutObjectOutput
scratch/main> alias.type ListObjectsV2Request lib.f34nk_aws_0_1_1.ListObjectsV2Request
scratch/main> alias.type ListObjectsV2Output lib.f34nk_aws_0_1_1.ListObjectsV2Output
scratch/main> alias.type Object lib.f34nk_aws_0_1_1.Object
scratch/main> alias.type GetObjectRequest lib.f34nk_aws_0_1_1.GetObjectRequest
scratch/main> alias.type GetObjectOutput lib.f34nk_aws_0_1_1.GetObjectOutput
scratch/main> alias.type DeleteObjectRequest lib.f34nk_aws_0_1_1.DeleteObjectRequest
scratch/main> alias.type DeleteObjectOutput lib.f34nk_aws_0_1_1.DeleteObjectOutput
\`\`\`

\`\`\`ucm
scratch/main> alias.term objectCannedACLFromText lib.f34nk_aws_0_1_1.objectCannedACLFromText
scratch/main> alias.term listBuckets lib.f34nk_aws_0_1_1.listBuckets
scratch/main> alias.term putObject lib.f34nk_aws_0_1_1.putObject
scratch/main> alias.term listObjectsV2 lib.f34nk_aws_0_1_1.listObjectsV2
scratch/main> alias.term getObject lib.f34nk_aws_0_1_1.getObject
scratch/main> alias.term deleteObject lib.f34nk_aws_0_1_1.deleteObject
\`\`\`

Alias constructors:

\`\`\`ucm
scratch/main> alias.term Credentials.Credentials lib.f34nk_aws_0_1_1.Credentials.Credentials
scratch/main> alias.term Config.Config lib.f34nk_aws_0_1_1.Config.Config
scratch/main> alias.term ListBucketsRequest.ListBucketsRequest lib.f34nk_aws_0_1_1.ListBucketsRequest.ListBucketsRequest
scratch/main> alias.term PutObjectRequest.PutObjectRequest lib.f34nk_aws_0_1_1.PutObjectRequest.PutObjectRequest
scratch/main> alias.term ListObjectsV2Request.ListObjectsV2Request lib.f34nk_aws_0_1_1.ListObjectsV2Request.ListObjectsV2Request
scratch/main> alias.term GetObjectRequest.GetObjectRequest lib.f34nk_aws_0_1_1.GetObjectRequest.GetObjectRequest
scratch/main> alias.term DeleteObjectRequest.DeleteObjectRequest lib.f34nk_aws_0_1_1.DeleteObjectRequest.DeleteObjectRequest
\`\`\`

Alias record accessors:

\`\`\`ucm
scratch/main> alias.term Bucket.name lib.f34nk_aws_0_1_1.Bucket.name
scratch/main> alias.term Config.endpoint lib.f34nk_aws_0_1_1.Config.endpoint
scratch/main> alias.term Config.region lib.f34nk_aws_0_1_1.Config.region
scratch/main> alias.term Object.key lib.f34nk_aws_0_1_1.Object.key
scratch/main> alias.term Object.size lib.f34nk_aws_0_1_1.Object.size
scratch/main> alias.term ListBucketsOutput.buckets lib.f34nk_aws_0_1_1.ListBucketsOutput.buckets
scratch/main> alias.term ListObjectsV2Output.contents lib.f34nk_aws_0_1_1.ListObjectsV2Output.contents
scratch/main> alias.term GetObjectOutput.body lib.f34nk_aws_0_1_1.GetObjectOutput.body
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
