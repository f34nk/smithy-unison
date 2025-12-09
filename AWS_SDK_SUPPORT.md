# AWS SDK Support

This document lists all AWS SDK features from the [Smithy AWS integrations](https://smithy.io/2.0/aws/index.html) specification and their support status in smithy-unison.

**Legend:**
- ✅ Supported - Feature is implemented and affects code generation
- ⚠️ Partial - Feature is partially implemented with limitations
- ❌ Not Supported - Feature is not implemented
- ➖ N/A - Feature is not applicable to client code generation

---

## Client SDK Features

General SDK features not specific to AWS traits.

| Feature | Status | Notes |
|---------|--------|-------|
| HTTP Protocol Bindings | ✅ | `@httpLabel`, `@httpHeader`, `@httpQuery`, `@httpPayload` implemented in RestXmlProtocolGenerator |
| Input Validation | ❌ | `@required` trait validation |
| Pagination Helpers | ✅ | Auto-generated pagination functions for `@paginated` operations |
| Retry with Exponential Backoff | ❌ | Configurable retry with jitter |
| Error Handling | ✅ | Error parsing implemented for REST-XML |
| HTTP Prefix Headers | ❌ | `@httpPrefixHeaders` trait not implemented |
| Idempotency Token | ❌ | `@idempotencyToken` trait not implemented |
| Host Label | ❌ | `@hostLabel` trait not implemented |
| Endpoint Override | ❌ | `@endpoint` trait not implemented |
| Request Compression | ❌ | `@requestCompression` trait not implemented |
| Streaming | ❌ | `@streaming` trait not implemented |
| Waiters | ❌ | `@waitable` trait not implemented |

---

## AWS Protocols

Protocol implementations for AWS services.

| Feature | Status | Notes |
|---------|--------|-------|
| [AWS EC2 Query protocol](https://smithy.io/2.0/aws/protocols/aws-ec2-query-protocol.html) | ❌ | Not implemented (stubbed) |
| [AWS JSON 1.0 protocol](https://smithy.io/2.0/aws/protocols/aws-json-1_0-protocol.html) | ❌ | Not implemented (stubbed) |
| [AWS JSON 1.1 protocol](https://smithy.io/2.0/aws/protocols/aws-json-1_1-protocol.html) | ❌ | Not implemented (stubbed) |
| [AWS Query protocol](https://smithy.io/2.0/aws/protocols/aws-query-protocol.html) | ❌ | Not implemented (stubbed) |
| [AWS restJson1 protocol](https://smithy.io/2.0/aws/protocols/aws-restjson1-protocol.html) | ❌ | Not implemented (stubbed) |
| [AWS restXml protocol](https://smithy.io/2.0/aws/protocols/aws-restxml-protocol.html) | ✅ | Implemented with operation generation, request/response serialization |

---

## XML Binding Traits

XML serialization traits for REST-XML and other XML-based protocols.

| Feature | Status | Notes |
|---------|--------|-------|
| [`@xmlName`](https://smithy.io/2.0/spec/protocol-traits.html#xmlname-trait) | ❌ | XML element name override not implemented |
| [`@xmlFlattened`](https://smithy.io/2.0/spec/protocol-traits.html#xmlflattened-trait) | ❌ | List/map flattening not implemented |
| [`@xmlNamespace`](https://smithy.io/2.0/spec/protocol-traits.html#xmlnamespace-trait) | ❌ | XML namespace declarations not implemented |
| [`@xmlAttribute`](https://smithy.io/2.0/spec/protocol-traits.html#xmlattribute-trait) | ❌ | XML attributes not implemented |

---

## AWS Authentication

Authentication mechanisms for AWS services.

| Feature | Status | Notes |
|---------|--------|-------|
| [AWS Signature Version 4 (SigV4)](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4-trait) | ✅ | SigV4Generator provides complete signing implementation |
| [Credential Provider Chain](https://smithy.io/2.0/aws/aws-auth.html) | ❌ | Not implemented |
| [AWS Signature Version 4A (SigV4A)](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4a-trait) | ❌ | Multi-region asymmetric signing not implemented |
| [Cognito User Pools Authentication](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-cognitouserpools-trait) | ❌ | Cognito authentication not implemented |
| [Unsigned Payload](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-unsignedpayload-trait) | ❌ | Skipping payload signing not implemented |

---

## AWS Core Specification

Core AWS service traits and metadata.

| Feature | Status | Notes |
|---------|--------|-------|
| [Service Trait (`aws.api#service`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-service-trait) | ❌ | Not implemented |
| [Endpoint Discovery](https://smithy.io/2.0/aws/aws-core.html#aws-api-clientendpointdiscovery-trait) | ❌ | Dynamic endpoint discovery not implemented |
| [HTTP Checksum (`aws.protocols#httpChecksum`)](https://smithy.io/2.0/aws/aws-core.html#aws-protocols-httpchecksum-trait) | ❌ | Request/response checksums not implemented |
| [ARN References (`aws.api#arnReference`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-arnreference-trait) | ➖ | Server-side resource modeling |
| [ARN Templates (`aws.api#arn`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-arn-trait) | ➖ | Server-side resource modeling |
| [Control Plane / Data Plane](https://smithy.io/2.0/aws/aws-core.html#aws-api-controlplane-trait) | ➖ | Service classification metadata |
| [Data Classification (`aws.api#data`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-data-trait) | ➖ | Compliance metadata |
| [Tagging (`aws.api#taggable`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-taggable-trait) | ➖ | Resource tagging metadata |

---

## AWS Endpoint Resolution

Endpoint resolution and regional configuration.

| Feature | Status | Notes |
|---------|--------|-------|
| [Partition Support](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Not implemented |
| [Region Configuration](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Not implemented |
| [Static Endpoint Resolution](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Not implemented |
| [Dual-Stack Endpoints](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-dualstackonlyendpoints-trait) | ❌ | Not implemented |
| [FIPS Endpoints](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Not implemented |
| [Declarative Endpoint Traits](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Not implemented |
| [Rules-Based Endpoint Resolution](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-rulesbasedendpoints-trait) | ❌ | Dynamic rules engine not implemented |

---

## AWS Service Customizations

Service-specific behaviors and customizations.

### Amazon S3 Customizations

| Feature | Status | Notes |
|---------|--------|-------|
| [S3 Path-Style Bucket Addressing](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-bucket-virtual-hosting) | ✅ | Implemented in S3UrlGenerator |
| [S3 Virtual-Hosted Bucket Addressing](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-bucket-virtual-hosting) | ✅ | Implemented in S3UrlGenerator (default) |
| [S3 Access Points](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | Access point ARN resolution not implemented |
| [S3 Dual-Stack Endpoints](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-dual-stack-endpoints) | ❌ | `.dualstack.` endpoint modifier not implemented |
| [S3 Multi-Region Access Points](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | MRAP not implemented |
| [S3 Multipart Upload](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | Multipart upload helpers not implemented |
| [S3 Presigned URLs](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | Presigned URL generation not implemented |
| [S3 Transfer Acceleration](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-transfer-acceleration-endpoints) | ❌ | `s3-accelerate.amazonaws.com` not implemented |

### Other Service Customizations

| Feature | Status | Notes |
|---------|--------|-------|
| [Amazon Glacier Customizations](https://smithy.io/2.0/aws/customizations/glacier-customizations.html) | ❌ | Tree hash checksums not implemented |
| [Amazon Machine Learning Customizations](https://smithy.io/2.0/aws/customizations/machinelearning-customizations.html) | ❌ | Predict endpoint resolution not implemented |
| [Amazon API Gateway Customizations](https://smithy.io/2.0/aws/customizations/apigateway-customizations.html) | ➖ | API Gateway deployment configuration |

---

## AWS Rules Engine

Endpoint rules engine for dynamic endpoint resolution.

| Feature | Status | Notes |
|---------|--------|-------|
| [Rules Engine Specification](https://smithy.io/2.0/aws/rules-engine/index.html) | ❌ | Not implemented |
| [`@endpointRuleSet`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ❌ | Endpoint rule set trait not processed |
| [`@contextParam`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ❌ | Context parameter bindings not implemented |
| [`@staticContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ❌ | Static context parameters not implemented |
| [`@clientContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ❌ | Client context parameters not implemented |
| [Authentication Scheme Validators](https://smithy.io/2.0/aws/rules-engine/auth-schemes.html) | ❌ | Auth scheme validation not implemented |
| [AWS Rules Engine Built-ins](https://smithy.io/2.0/aws/rules-engine/built-ins.html) | ❌ | Built-in functions not implemented |
| [AWS Rules Engine Library Functions](https://smithy.io/2.0/aws/rules-engine/library-functions.html) | ❌ | Library functions (`aws.partition`, `aws.parseArn`, etc.) not implemented |

---

## Amazon Event Stream

Event streaming for real-time data.

| Feature | Status | Notes |
|---------|--------|-------|
| [Event Headers](https://smithy.io/2.0/aws/amazon-eventstream.html) | ❌ | Event header encoding not implemented |
| [Event Payloads](https://smithy.io/2.0/aws/amazon-eventstream.html) | ❌ | Event payload handling not implemented |
| [Event Stream Specification](https://smithy.io/2.0/aws/amazon-eventstream.html) | ❌ | Binary event stream protocol not implemented |

---

## AWS IAM Traits

IAM policy generation traits. Not applicable to client code generation.

| Feature | Status | Notes |
|---------|--------|-------|
| [Condition Keys](https://smithy.io/2.0/aws/aws-iam.html) | ➖ | IAM policy generation |
| [IAM Action Traits](https://smithy.io/2.0/aws/aws-iam.html) | ➖ | IAM policy generation |
| [IAM Resource Traits](https://smithy.io/2.0/aws/aws-iam.html) | ➖ | IAM policy generation |

---

## Amazon API Gateway Traits

API Gateway integration traits. Not applicable to client code generation.

| Feature | Status | Notes |
|---------|--------|-------|
| [API Gateway Authorizers](https://smithy.io/2.0/aws/amazon-apigateway.html) | ➖ | API Gateway deployment |
| [API Gateway Integrations](https://smithy.io/2.0/aws/amazon-apigateway.html) | ➖ | API Gateway deployment |
| [Request Validators](https://smithy.io/2.0/aws/amazon-apigateway.html) | ➖ | API Gateway deployment |

---

## AWS CloudFormation Traits

CloudFormation resource generation traits. Not applicable to client code generation.

| Feature | Status | Notes |
|---------|--------|-------|
| [CloudFormation Resource Traits](https://smithy.io/2.0/aws/aws-cloudformation.html) | ➖ | CloudFormation resource schema generation |

---

*Generated from [Smithy 2.0 AWS integrations](https://smithy.io/2.0/aws/index.html)*

**Work in progress - See individual feature status above.**
