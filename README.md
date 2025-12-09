# WORK IN PROGRESS

# Smithy Unison Code Generator

[![CI](https://github.com/f34nk/smithy-unison/actions/workflows/ci.yml/badge.svg)](https://github.com/f34nk/smithy-unison/actions/workflows/ci.yml)

Generates Unison client code from Smithy service models. Produces client modules, type definitions, and HTTP request/response handling for service operations.

Reference: https://smithy.io/2.0/index.html

## Features

### Core Code Generation ✅
- Smithy Build plugin integration
- Support for [Smithy Interface Definition Language (IDL)](https://smithy.io/2.0/spec/idl.html) and [JSON AST](https://smithy.io/2.0/spec/json-ast.html)
- Structure generation → Unison record types
- Enum generation → Unison sum types with `toText`/`fromText` functions
- Union generation → Unison sum types with payloads
- Error type generation with `toFailure` conversion functions
- Service-level error sum types with parsing functions

### Protocol Support (In Progress)
- **REST-XML protocol** ✅ (S3, CloudFront, Route 53)
  - Operation code generation
  - HTTP binding traits: `@http`, `@httpLabel`, `@httpQuery`, `@httpHeader`, `@httpPayload`, `@httpResponseCode`
  - Request serialization / Response deserialization
  - Error parsing
- REST-JSON protocol (planned)
- AWS JSON 1.0/1.1 protocols (planned)
- AWS Query / EC2 Query protocols (planned)

<!-- NOT YET IMPLEMENTED
### AWS SDK Support
- AWS SigV4 request signing
- Credential provider chain (environment variables, config files, credential providers)
- Retry logic with exponential backoff and jitter
- Pagination with automatic helper function generation
- Region configuration support
-->

Check out [AWS_SDK_SUPPORT.md](https://github.com/f34nk/smithy-unison/blob/main/AWS_SDK_SUPPORT.md) with a full list of AWS SDK features and their support status in smithy-unison.

## Prerequisites

- Java 11+
- Gradle 8.0+
- Unison (UCM)

- Smithy CLI

## Build

```bash
make build
```

## Testing

Run generator tests:

```bash
make test
```

Run all example builds:

```bash
make examples
```

Or build a specific example:

```bash
make examples/error-types
```

<!-- NOT YET IMPLEMENTED
Or generate and run the official [AWS SDK S3 model](https://github.com/aws/api-models-aws/tree/main/models/s3/service/2006-03-01):

```bash
make demo
```
-->

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

Build model:

```bash
smithy build
```

Generated files in `generated/`:
- `{namespace}_client.u` - Client module with types, records, and operation stubs

<!-- NOT YET IMPLEMENTED - Runtime Modules
- `aws_config.u` - AWS configuration management
- `aws_sigv4.u` - AWS Signature V4 request signing
- `aws_credentials.u` - Credential provider chain
- `aws_retry.u` - Retry logic with exponential backoff
- `aws_xml.u` - XML parsing for REST-XML protocol
- `aws_http.u` - HTTP request helpers
- `aws_s3.u` - S3-specific URL routing utilities
-->

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

### Built-in Integrations (Stubbed)

| Integration | Purpose |
|-------------|---------|
| `AwsSigV4Integration` | Copies AWS SigV4 signing modules |
| `AwsProtocolIntegration` | Copies protocol-specific runtime modules |
| `AwsRetryIntegration` | Copies retry logic module |

## Smithy Traits

[Smithy traits](https://smithy.io/2.0/spec/model.html#traits) are declarative metadata that tell code generators how to generate code, without embedding that logic in the model itself. They separate "what the API looks like" from "how to implement it".

Smithy-unison **reads and uses** built-in traits via Java's Smithy libraries. 

Check out [TRAITS.md](https://github.com/f34nk/smithy-unison/blob/main/TRAITS.md) with all Smithy traits and their support status in smithy-unison.

The generator maps Smithy types to Unison types:

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
getObject : Config -> GetObjectInput -> '{IO, Exception, Http} GetObjectOutput
getObject config input =
  -- Raises exception on error, returns output directly on success
```

## License

Apache License 2.0
