# AWS S3 Demo

End-to-end test of the generated S3 client against a mock S3 server (LocalStack).

## Prerequisites

- Docker (runs pre-compiled code)
- `curl` for downloading UCM

## Quick Start

```bash
make test
```

This will:
1. Generate Unison code from Smithy model
2. Install UCM locally (from GitHub releases, same version as Docker)
3. Compile all Unison code locally
4. Start LocalStack in docker
5. Create the stack with terraform against LocalStack endpoint
6. Run the compiled demo

The demo will execute functions from the generated `s3_client` against a mocked S3 bucket 

```shell
╔══════════════════════════════════════════╗
║      S3 Client Demo (LocalStack Backend) ║
╚══════════════════════════════════════════╝

Endpoint: http://localhost:4566
Region: us-east-1
Bucket: us-east-1-nonprod-configs

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Step 1: List Buckets
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  → Creating ListBucketsRequest...
  → Calling listBuckets...
  ✓ SUCCESS: ListBuckets returned
  → Found 1 bucket(s):
      • us-east-1-nonprod-configs

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Step 2: Put Object
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  → Creating PutObjectRequest...
    Bucket: us-east-1-nonprod-configs
    Key: configs/unison-test.txt
    Content: "Hello from Unison S3 Client!"
  → Calling putObject...
  ✓ SUCCESS: PutObject completed

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Step 3: List Objects
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  → Creating ListObjectsV2Request...
    Bucket: us-east-1-nonprod-configs
    Prefix: configs/
  → Calling listObjectsV2...
  ✓ SUCCESS: ListObjectsV2 returned
  → Found 3 object(s):
      • configs/config1.toml (12 bytes)
      • configs/config2.toml (12 bytes)
      • configs/unison-test.txt (28 bytes)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Step 4: Get Object
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  → Creating GetObjectRequest...
    Bucket: us-east-1-nonprod-configs
    Key: configs/unison-test.txt
  → Calling getObject...
  ✓ SUCCESS: GetObject returned
    Body: "Hello from Unison S3 Client!"
    ✓ Content matches uploaded data!

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Step 5: Delete Object (cleanup)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  → Creating DeleteObjectRequest...
    Bucket: us-east-1-nonprod-configs
    Key: configs/unison-test.txt
  → Calling deleteObject...
  ✓ SUCCESS: DeleteObject completed

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Step 6: Verify Deletion
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  → Listing objects after deletion...
    Bucket: us-east-1-nonprod-configs
    Prefix: configs/
  ✓ SUCCESS: ListObjectsV2 returned
  → Found 2 object(s):
      • configs/config1.toml
      • configs/config2.toml
    ✓ Object configs/unison-test.txt was successfully deleted!

```
