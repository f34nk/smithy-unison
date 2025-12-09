# WORK IN PROGRESS

# Smithy Unison Code Generator

> **⚠️ FIRST DRAFT - NOT YET FUNCTIONAL**
>
> This is an initial bootstrap of smithy-unison. All code generation is stubbed.
> The project structure is in place but no Unison code is generated yet.

[![CI](https://github.com/f34nk/smithy-unison/actions/workflows/ci.yml/badge.svg)](https://github.com/f34nk/smithy-unison/actions/workflows/ci.yml)

Generates Unison client code from Smithy service models. Produces client modules, type definitions, and HTTP request/response handling for service operations.

Reference: https://smithy.io/2.0/index.html

## Features

### Core Code Generation
<!-- NOT YET IMPLEMENTED
- Single-module code generation with types, records, and functions
- Type aliases in function specs for documentation
- Supports core Smithy shapes (structures, lists, maps, primitives, unions, enums)
- Union types as sum types with encoding/decoding
- Enum types as sum types with validation
- Topological sorting for dependency-ordered type definitions
-->
- Smithy Build plugin integration ✅
- Support for [Smithy Interface Definition Language (IDL)](https://smithy.io/2.0/spec/idl.html) and [JSON AST](https://smithy.io/2.0/spec/json-ast.html)

<!-- NOT YET IMPLEMENTED
### Protocol Support (Planned)
- All 5 AWS protocols supported:
  - awsJson1.0 and awsJson1.1 (DynamoDB, Lambda, Kinesis)
  - awsQuery (SQS, SNS, RDS)
  - ec2Query (EC2)
  - restXml (S3, CloudFront, Route 53)
  - restJson1 (API Gateway, Step Functions)
- HTTP protocol bindings: @httpLabel, @httpHeader, @httpQuery, @httpPayload
- URI template parsing and parameter substitution
- Field validation for @required trait
-->

<!-- NOT YET IMPLEMENTED
### AWS SDK Support (Planned)
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

<!-- NOT YET IMPLEMENTED
Run all example builds and tests:

```bash
make examples
```

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

<!-- NOT YET IMPLEMENTED
Generated files in `src/generated/`:
- `my_client.u` - A single module with types, records, and operation functions (generated from the smithy model)
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
type GetObjectInput = {
  bucket : Text,
  key : Text,
  versionId : Optional Text
}

getObject : S3Config -> GetObjectInput -> '{IO, Exception} S3Response Bytes
getObject config input _ =
  -- implementation
```

## License

Apache License 2.0
