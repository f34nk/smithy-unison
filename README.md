# WORK IN PROGRESS

# Smithy Unison Code Generator

[![CI](https://github.com/f34nk/smithy-unison/actions/workflows/ci.yml/badge.svg)](https://github.com/f34nk/smithy-unison/actions/workflows/ci.yml)

Generates [Unison](https://www.unison-lang.org/) client code from Smithy service models. Produces client modules, type definitions, and HTTP request/response handling for service operations.

Reference: https://smithy.io/2.0/index.html

## Features

### Core Code Generation
- Smithy Build plugin integration
- Support for [Smithy Interface Definition Language (IDL)](https://smithy.io/2.0/spec/idl.html) and [JSON AST](https://smithy.io/2.0/spec/json-ast.html)
- Structure generation → Unison record types
- Enum generation → Unison sum types with `toText`/`fromText` functions
- Union generation → Unison sum types with payloads
- Error type generation with `toFailure` conversion functions
- Service-level error sum types with parsing functions
- Automatic type generation for all services (AWS and non-AWS)

### Protocol Support
- **REST-XML protocol** (S3, CloudFront, Route 53)
  - Full operation implementation (all 106 S3 operations)
  - HTTP binding traits: `@http`, `@httpLabel`, `@httpQuery`, `@httpHeader`, `@httpPayload`, `@httpResponseCode`
  - Request serialization / Response deserialization
  - Error parsing
- **AWS JSON 1.0/1.1 protocols** (DynamoDB, Lambda, Kinesis)
  - Full operation implementation with request serialization and response deserialization
  - Nested structure serializer/deserializer generation
  - Error parsing (JSON error responses with `__type` field)
  - Service error union types with exception handling
- **REST-JSON protocol** (EventBridge, Step Functions, API Gateway, Lambda)
  - Full operation implementation with HTTP bindings and JSON serialization/deserialization
  - Complete response deserializer generation (structures, enums, lists, maps, unions)
  - Path parameter substitution and query string building
  - HTTP header extraction and body serialization
  - Error parsing with multiple format support
  - Resource operation support
  - Reserved keyword escaping (backtick syntax)
- **AWS Query protocol** (SQS, SNS, RDS)
  - Full operation implementation with XML request serialization and response deserialization
  - Form-encoded parameter serialization with proper Query format
  - XML map and list extraction with @xmlFlattened trait support
  - Structure list serialization with required/optional field handling
  - Error parsing from XML error responses
- **EC2 Query protocol** (EC2)
  - Extends AWS Query with EC2-specific response and error format handling
  - Custom response wrapper navigation
  - EC2-specific error parsing structure

### AWS Authentication
- **AWS SigV4 request signing** - Complete implementation of Signature Version 4
  - Credential types (`aws.sigv4.Credentials`, `aws.sigv4.SigningConfig`)
  - Canonical request building
  - Signing key derivation (HMAC-SHA256 chain)
  - Authorization header generation

### AWS SDK Support
- Credential provider chain (environment variables, config files)
- Retry logic with exponential backoff and jitter
- Pagination with automatic helper function generation and token field inference

Check out [AWS_SDK_SUPPORT.md](https://github.com/f34nk/smithy-unison/blob/main/AWS_SDK_SUPPORT.md) with a full list of AWS SDK features and their support status in smithy-unison.

## Prerequisites

- Java 11+
- Gradle 8.0+
- Unison (UCM)
- Smithy CLI
- Docker and Terraform (for testing)

## Build

```bash
make build
```

## Testing

Run generator tests:

```bash
make test
```

Checkout [examples](https://github.com/f34nk/smithy-unison/tree/main/examples) for fully functional Unison demo.

Run any example with: 
```shell
make examples/<example_name> # <----- without trailing slash
```

The ***-demo** examples generate client code from the official [AWS SDK smithy models](https://github.com/aws/api-models-aws/tree/main/models/) and create AWS infrastructure using [LocalStack](https://github.com/localstack/localstack). The demo app then executes functions from the generated client against the mocked infrastructure.

|  |
|--|
| make [examples/s3-demo](https://github.com/f34nk/smithy-unison/blob/main/examples/s3-demo/src/main.u)  |
| make [examples/dynamodb-demo](https://github.com/f34nk/smithy-unison/blob/main/examples/dynamodb-demo/src/main.u)  |
| make [examples/sqs-demo](https://github.com/f34nk/smithy-unison/blob/main/examples/sqs-demo/src/main.u)  |
| make [examples/kinesis-demo](https://github.com/f34nk/smithy-unison/blob/main/examples/kinesis-demo/src/main.u)  |
| make [examples/sns-demo](https://github.com/f34nk/smithy-unison/blob/main/examples/sns-demo/src/main.u)  |
| make [examples/lambda-demo](https://github.com/f34nk/smithy-unison/blob/main/examples/lambda-demo/src/main.u)  |

Run integration-test:

```bash
make integration-test/s3
```

The **integration-test** [installs and compiles the Unison AWS library](https://github.com/f34nk/smithy-unison/blob/main/examples/s3-demo/compile-with-lib.md) (generated with `smithy-unison` and released to [Unison Share @f34nk/aws](https://share.unison-lang.org/@f34nk/aws)) and runs the [S3 demo](https://github.com/f34nk/smithy-unison/blob/main/examples/s3-demo/src/main.u) against a mocked infrastructure.

```bash
make integration-test/dynamodb
```

This **integration-test** does the above with the [DynamoDB demo](https://github.com/f34nk/smithy-unison/blob/main/examples/dynamodb-demo/src/main.u)


## Basic Usage

Create [smithy-build.json](https://smithy.io/2.0/guides/smithy-build-json.html):

```json
{
  "version": "1.0",
  "sources": ["model"],
  "maven": {
    "dependencies": ["io.smithy.unison:smithy-unison:0.1.0"],
    "repositories": [
      {
        "url": "https://repo1.maven.org/maven2"
      },
      {
        "url": "file://${user.home}/.m2/repository"
      }
    ]
  },
  "plugins": {
    "unison-codegen": {
      "service": "com.example#MyClient",
      "namespace": "my.client",
      "outputDir": "generated"
    }
  }
}
```

Generate only the operations you need, dramatically reducing code size and compilation time:

```json
{
  "plugins": {
    "unison-codegen": {
      "service": "com.amazonaws.dynamodb#DynamoDB_20120810",
      "namespace": "aws.dynamodb",
      "operations": [
        "CreateTable",
        "PutItem",
        "GetItem",
        "Query",
        "DeleteItem"
      ],
      "generateAllOperations": false
    }
  }
}
```

For example: DynamoDB 9 operations → 3,418 lines (vs ~40K for full model)

**Build model:**

```bash
smithy build
```

The generator automatically includes all transitive dependencies (nested types, errors, enums) ensuring type safety and completeness.

Generated files in `generated/`:
- `{namespace}_client.u` - Client module with types, records, and operations

For AWS services, additional runtime modules are copied:
- `aws_sigv4.u` - AWS Signature V4 request signing
- `aws_xml.u` - XML encoding/decoding (REST-XML protocol only)
- `aws_json.u` - JSON encoding/decoding with DynamoDB AttributeValue support
- `aws_json_bridge.u` - JSON-HTTP integration with parsing utilities (parseFloat, parseBlob, mapWithException)
- `aws_restjson.u` - REST-JSON URL building and parameter handling
- `aws_http.u` - HTTP request/response utilities
- `aws_http_bridge.u` - Bridge for @unison/http library (enables real HTTP)
- `aws_s3.u` - S3-specific URL routing (S3 only)
- `aws_config.u` - AWS configuration types
- `aws_credentials.u` - Credential provider chain

## Architecture

The generator follows Smithy's recommended `DirectedCodegen` pattern for extensibility and maintainability.

Reference: [Creating a Code Generator](https://smithy.io/2.0/guides/building-codegen/index.html)

See [ARCHITECTURE.md](https://github.com/f34nk/smithy-unison/blob/main/ARCHITECTURE.md) for detailed architecture documentation.

### Core Components

| Component | Description |
|-----------|-------------|
| `UnisonCodegenPlugin` | Main Smithy Build plugin entry point |
| `UnisonGenerator` | DirectedCodegen implementation for shape-by-shape generation |
| `UnisonContext` | Centralized access to model, settings, and dependencies |
| `UnisonSettings` | Immutable configuration from smithy-build.json |
| `UnisonWriter` | SymbolWriter extension for Unison code output |
| `UnisonSymbolProvider` | Smithy-to-Unison type mapping |

### Extension System

The generator supports custom integrations via Java SPI (Service Provider Interface):

```java
public class CustomIntegration implements UnisonIntegration {
    @Override
    public String name() { return "CustomIntegration"; }
    
    @Override
    public void preprocessModel(UnisonContext context) {
        // Copy runtime modules or perform setup before generation
    }
    
    @Override
    public void postprocessGeneration(UnisonContext context) {
        // Run after generation completes
    }
}
```

Register in `META-INF/services/io.smithy.unison.codegen.UnisonIntegration`:
```
com.example.CustomIntegration
```

### Built-in Integrations

| Integration | Status | Purpose |
|-------------|--------|---------|
| `SigV4Generator` | ✅ | Generates AWS SigV4 request signing code |
| `RuntimeModuleCopier` | ✅ | Copies protocol-specific runtime modules |
| Retry Logic | ✅ | Exponential backoff with jitter in aws_http.u |

## Smithy Traits

[Smithy traits](https://smithy.io/2.0/spec/model.html#traits) are declarative metadata that tell code generators how to generate code, without embedding that logic in the model itself. They separate "what the API looks like" from "how to implement it".

Smithy-unison **reads and uses** built-in traits via Java's Smithy libraries. 

Check out [TRAITS.md](https://github.com/f34nk/smithy-unison/blob/main/TRAITS.md) with all Smithy traits and their support status in smithy-unison.

The generator maps Smithy types to Unison types:

## Type Mapping

| Smithy Type | Unison Type |
|-------------|-------------|
| `string` | `Text` |
| `integer`, `long` | `Int` |
| `float`, `double` | `Float` |
| `boolean` | `Boolean` |
| `blob` | `Bytes` |
| `timestamp` | `Text` |
| `list<T>` | `[T]` |
| `map<K, V>` | `Map K V` |
| `structure` | Record type |
| `union` | Sum type |
| `enum` | Sum type |

Example generated code:

```unison
-- Record type from structure
type GetObjectInput = {
  bucket : Text,
  key : Text,
  versionId : Optional Text
}

-- Sum type from enum
type BucketLocationConstraint
  = BucketLocationConstraint'UsEast1
  | BucketLocationConstraint'UsWest2
  | BucketLocationConstraint'EuWest1

-- Error type with toFailure conversion
type NoSuchKey = {
  message : Text,
  key : Optional Text
}

NoSuchKey.toFailure : NoSuchKey -> IO.Failure
NoSuchKey.toFailure err =
  IO.Failure.Failure (typeLink NoSuchKey) err.message (Any err)

-- Operation with exception-based error handling
getObject : Config -> GetObjectInput -> '{IO, Exception, Threads} GetObjectOutput
getObject config input =
  -- Raises exception on error, returns output directly on success
```

## AWS SDK Generator (experimental)

Use the AWS SDK Generator to generate unison code for any available AWS SDK.

Check out [generate-aws-sdk](https://github.com/f34nk/smithy-unison/blob/main/generate-aws-sdk) for details.

## License

Apache License 2.0
