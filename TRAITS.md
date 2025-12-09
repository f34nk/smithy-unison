# Smithy Trait Support

This document lists all Smithy 2.0 traits and their support status in smithy-unison.

**Legend:**
- ✅ Supported - Trait is read and affects code generation
- ⚠️ Partial - Trait is recognized but not fully implemented
- ❌ Not Supported - Trait has no effect on generated code
- ➖ N/A - Trait is not applicable to client code generation

---

## HTTP Binding Traits

Traits for HTTP protocol bindings.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#http`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-http-trait) | ❌ | Defines HTTP method and URI pattern |
| [`smithy.api#httpHeader`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpheader-trait) | ❌ | Binds member to HTTP header |
| [`smithy.api#httpLabel`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httplabel-trait) | ❌ | Binds member to URI path segment |
| [`smithy.api#httpPayload`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httppayload-trait) | ❌ | Binds member to HTTP body |
| [`smithy.api#httpQuery`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpquery-trait) | ❌ | Binds member to query string parameter |
| [`smithy.api#cors`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-cors-trait) | ❌ | CORS configuration for service |
| [`smithy.api#httpChecksumRequired`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpchecksumrequired-trait) | ❌ | Requires checksum header |
| [`smithy.api#httpError`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httperror-trait) | ✅ | Included in error type documentation |
| [`smithy.api#httpPrefixHeaders`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpprefixheaders-trait) | ❌ | Binds map to prefixed headers |
| [`smithy.api#httpQueryParams`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpqueryparams-trait) | ❌ | Binds map to query parameters |
| [`smithy.api#httpResponseCode`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpresponsecode-trait) | ❌ | Binds member to HTTP response status |

---

## Protocol & Serialization Traits

Traits for serialization and protocol behavior.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#jsonName`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-jsonname-trait) | ❌ | Uses custom JSON key name in serialization |
| [`smithy.api#xmlAttribute`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-xmlattribute-trait) | ❌ | Serializes member as XML attribute |
| [`smithy.api#xmlFlattened`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-xmlflattened-trait) | ❌ | Flattens list/map in XML serialization |
| [`smithy.api#xmlName`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-xmlname-trait) | ❌ | Uses custom XML element name |
| [`smithy.api#xmlNamespace`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-xmlnamespace-trait) | ❌ | Defines XML namespace |
| [`smithy.api#mediaType`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-mediatype-trait) | ❌ | Defines MIME type for blob/string |
| [`smithy.api#timestampFormat`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-timestampformat-trait) | ❌ | Specifies timestamp wire format |
| [`smithy.api#protocolDefinition`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-protocoldefinition-trait) | ➖ | Defines new protocols (meta-trait) |

---

## AWS Protocol Traits (`aws.protocols#*`)

AWS-specific protocol traits.

| Trait | Status | Notes |
|-------|--------|-------|
| [`aws.protocols#awsJson1_0`](https://smithy.io/2.0/aws/protocols/aws-json-1_0-protocol.html#aws-protocols-awsjson1_0-trait) | ❌ | AWS JSON 1.0 protocol (DynamoDB, Kinesis) |
| [`aws.protocols#awsJson1_1`](https://smithy.io/2.0/aws/protocols/aws-json-1_1-protocol.html#aws-protocols-awsjson1_1-trait) | ❌ | AWS JSON 1.1 protocol (Lambda, ECS) |
| [`aws.protocols#awsQuery`](https://smithy.io/2.0/aws/protocols/aws-query-protocol.html#aws-protocols-awsquery-trait) | ❌ | AWS Query protocol (SQS, SNS, RDS) |
| [`aws.protocols#ec2Query`](https://smithy.io/2.0/aws/protocols/aws-ec2-query-protocol.html#aws-protocols-ec2query-trait) | ❌ | EC2 Query protocol |
| [`aws.protocols#restJson1`](https://smithy.io/2.0/aws/protocols/aws-restjson1-protocol.html#aws-protocols-restjson1-trait) | ❌ | REST-JSON protocol (API Gateway, Step Functions) |
| [`aws.protocols#restXml`](https://smithy.io/2.0/aws/protocols/aws-restxml-protocol.html#aws-protocols-restxml-trait) | ❌ | REST-XML protocol (S3, CloudFront, Route 53) |
| [`aws.protocols#awsQueryCompatible`](https://smithy.io/2.0/aws/protocols/aws-query-protocol.html#aws-protocols-awsquerycompatible-trait) | ❌ | Query protocol compatibility mode |
| [`aws.protocols#httpChecksum`](https://smithy.io/2.0/aws/aws-core.html#aws-protocols-httpchecksum-trait) | ❌ | HTTP checksum configuration |

---

## AWS Authentication Traits (`aws.auth#*`)

AWS-specific authentication traits.

| Trait | Status | Notes |
|-------|--------|-------|
| [`aws.auth#sigv4`](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4-trait) | ❌ | Enables AWS SigV4 request signing |
| [`aws.auth#cognitoUserPools`](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-cognitouserpools-trait) | ❌ | Cognito User Pools authentication |
| [`aws.auth#sigv4a`](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4a-trait) | ❌ | Multi-region SigV4a signing |
| [`aws.auth#unsignedPayload`](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-unsignedpayload-trait) | ❌ | Skips payload signing |

---

## Type Refinement Traits

Traits that refine or modify type semantics.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#enumValue`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-enumvalue-trait) | ✅ | Defines wire value for enum conversion functions |
| [`smithy.api#error`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-error-trait) | ✅ | Generates error record type with metadata |
| [`smithy.api#input`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-input-trait) | ❌ | Marks structure as operation input |
| [`smithy.api#output`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-output-trait) | ❌ | Marks structure as operation output |
| [`smithy.api#required`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-required-trait) | ✅ | Marks member as required (non-optional in Unison) |
| [`smithy.api#default`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-default-trait) | ⚠️ | Sets default value - field is non-optional but default not generated |
| [`smithy.api#sparse`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-sparse-trait) | ❌ | Allows null values in lists/maps |

---

## Constraint Traits

Traits that constrain or validate values.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#enum`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-enum-trait) | ✅ | Generates sum type with toText/fromText functions |
| [`smithy.api#length`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-length-trait) | ❌ | Constrains length of strings, lists, or blobs |
| [`smithy.api#pattern`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-pattern-trait) | ❌ | Requires string values to match a regular expression |
| [`smithy.api#range`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-range-trait) | ❌ | Constrains numeric values to a minimum and/or maximum |
| [`smithy.api#uniqueItems`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-uniqueitems-trait) | ➖ | List constraint |

---

## Behavior Traits

Traits that define operation behavior.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#paginated`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-paginated-trait) | ❌ | Pagination support |
| [`smithy.api#idempotencyToken`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-idempotencytoken-trait) | ❌ | Auto-generates unique token for idempotent operations |
| [`smithy.api#idempotent`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-idempotent-trait) | ❌ | Marks operation as idempotent |
| [`smithy.api#readonly`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-readonly-trait) | ❌ | Marks operation as read-only |
| [`smithy.api#retryable`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-retryable-trait) | ❌ | Marks error as retryable |

---

## Documentation Traits

Traits that provide documentation metadata.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#documentation`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-documentation-trait) | ✅ | Documentation for shapes (generates {{ }} doc comments) |
| [`smithy.api#deprecated`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-deprecated-trait) | ❌ | Marks shape as deprecated |
| [`smithy.api#examples`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-examples-trait) | ❌ | Provides example input/output for operations |

---

## Streaming Traits

Traits for streaming data.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#streaming`](https://smithy.io/2.0/spec/streaming.html#smithy-api-streaming-trait) | ❌ | Enables streaming of large payloads |
| [`smithy.api#eventHeader`](https://smithy.io/2.0/spec/streaming.html#smithy-api-eventheader-trait) | ❌ | Event stream header binding |
| [`smithy.api#eventPayload`](https://smithy.io/2.0/spec/streaming.html#smithy-api-eventpayload-trait) | ❌ | Event stream payload binding |

---

## Waiter Traits

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.waiters#waitable`](https://smithy.io/2.0/additional-specs/waiters.html#smithy-waiters-waitable-trait) | ❌ | Defines waiter for polling long-running operations |

---

## Implementation Roadmap

When implementing trait support, prioritize in this order:

### Phase 1: Core Types
1. `@required` - Input validation
2. `@enum` / `@enumValue` - Enum type generation
3. `@error` - Error type generation

### Phase 2: HTTP Bindings
4. `@http` - HTTP method and URI
5. `@httpLabel` - Path parameters
6. `@httpQuery` - Query parameters
7. `@httpHeader` - HTTP headers
8. `@httpPayload` - Request/response body

### Phase 3: Protocols
9. AWS Protocol traits - Protocol detection
10. `@aws.auth#sigv4` - Request signing

### Phase 4: Advanced Features
11. `@paginated` - Pagination helpers
12. `@streaming` - Large payload support
13. `@waitable` - Waiter functions

---

*Generated from [Smithy 2.0 Trait Index](https://smithy.io/2.0/trait-index.html)*

**FIRST DRAFT - No traits are currently implemented.**
