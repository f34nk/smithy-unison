# AWS SDK Generator

Generates Unison AWS SDKs from the [official AWS Smithy models](https://github.com/aws/api-models-aws) using smithy-unison.

## Prerequisites

- Python 3.12+
- [uv](https://github.com/astral-sh/uv) (Python package manager)
- [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/cli_installation.html)
- smithy-unison installed to local Maven repository (`~/.m2/repository`)

## Setup

Build smithy-unison to local Maven:

```bash
cd .. && make build
```

## Usage

Generate SDKs:

```bash
make build
```

Output is written to `output/<service>/src/`.

## Configuration

Edit `generate.py` to customize which services to generate:

```python
sdks = ["s3", "dynamodb", "ec2"]  # Add or remove services
```

Processing all 415 SDKs, using 40 workers (10 cores available, max 64 workers), 
takes ~1.7 minutes.
