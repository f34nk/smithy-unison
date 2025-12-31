# Changelog

## [Unreleased]

### Added

#### AWS JSON Protocol Request Serialization
- Implemented request serialization for AWS JSON 1.0/1.1 protocols
- Generate JSON request body serializers for all operation inputs
- Support for primitive types (string, boolean, integer, float)
- Support for complex types (lists, maps, nested structures)
- Support for optional fields with null filtering
- Support for blob types with Base64 encoding
- Support for timestamp types with ISO-8601 formatting
- Support for enum types with text conversion
- Respect `@jsonName` trait for custom field names

#### Runtime Module Copier Updates
- Added `AWS_JSON` and `AWS_JSON_BRIDGE` to available runtime modules
- Automatic copying of JSON modules for JSON-based AWS protocols (AWS JSON 1.0/1.1, REST-JSON)
- Protocol-aware runtime module selection in `copyAwsModulesForProtocol`

#### JSON Bridge Module
- `aws_json_bridge.u` runtime module for JSON-HTTP integration
- Request serialization functions (`serializeJsonRequest`, `jsonToRequestBody`)
- Response deserialization functions (`deserializeJsonResponse`, `responseBodyToJson`)
- AWS JSON error parsing (`parseJsonError`, `extractErrorType`, `extractErrorMessage`)
- DynamoDB-specific helpers (`parseItemFromJson`, `serializeItemToJson`, `parseKeyFromJson`, `parseItemsFromJson`)
- Comprehensive unit tests for bridge functionality

#### JSON Runtime Module
- `aws_json.u` runtime module with JSON serialization/deserialization
- `JsonValue` type for representing JSON data structures
- DynamoDB `AttributeValue` type with tagged union format support
- JSON object builders and accessors for field extraction
- Comprehensive unit tests for JSON parsing and AttributeValue conversion

#### Library Publishing Support
- `compile-with-lib.sh` script for compiling using published `@f34nk/aws` library
- Namespace aliases in `compile.sh` for portable demo code
- `use lib.f34nk_aws_0_1_0` imports in demo for library compatibility
- `README.u` package documentation for Unison Share

### Fixed
- Ambiguity errors in runtime modules by using fully qualified `lib.unison_base_3_18_0` paths
- `Text.split` and `URI.parse` resolution when multiple base libraries in scope

#### Retry Logic with Exponential Backoff
- `Aws.Http.RetryConfig` type for configurable retry behavior
- `Aws.Http.RetryResult` tracking attempts and delay times
- `Aws.Http.calculateBackoff` for exponential backoff calculation
- `Aws.Http.parseRetryAfter` to parse Retry-After headers
- `Aws.Http.withRetry` and convenience retry functions

#### AWS Error Classification and Parsing
- Error classification helpers: `isNotFound`, `isAccessDenied`, `isThrottled`, `isRetryable`
- `Aws.Http.Error.toText` for detailed error logging
- `Aws.Http.parseErrorXml` and `Aws.Http.parseError` for structured error parsing
- Try/catch helpers: `Aws.Http.try`, `Aws.Http.tryOptional`
- Extended `Aws.Http.Error` with `hostId` field for S3 debugging

#### Improved HTTP Error Handling
- `Aws.Http.Error` type for structured AWS error information
- `handleHttpResponse` now parses AWS XML error responses and raises proper exceptions
- Helper functions: `Aws.Http.isSuccessResponse`, `Aws.Http.isEmptyResponse`
- Proper handling of 204 No Content responses

#### XML Response Parser Generation
- Code generator now produces `parseXxxFromXml` functions for all structure types
- Handles nested structures, lists of structures, enums, and primitive types
- Runtime helpers in `aws_xml.u`: `parseListFromXml`, `parseNestedFromXml`, `parseWrappedListFromXml`
- Replaced ~57 TODO comments with actual parsing code

#### XML to Record Mapping
- Structure list parsing: `parseList`, `parseChildList`, `parseWrappedList`
- Nested structure parsing: `parseNested`, `parseOptionalWrappedList`
- Required field extraction with exceptions: `requireText`, `requireInt`, `requireNat`, `requireBool`
- Text-based parsing for aws_xml.u compatibility: `parseListFromText`, `parseWrappedListFromText`, `parseNestedFromText`

#### XML Bridge Module
- `aws_xml_bridge.u` providing integration with `@unison/xml` library
- Soup-based XML parsing and navigation functions
- Convenience extraction: `findText`, `findInt`, `findBool`, `findNat`
- Error bridging between `XMLError` and `Exception`

#### HTTP Method Support
- Complete HTTP method support in `aws_http.u`: GET, POST, PUT, DELETE, HEAD, PATCH, OPTIONS
- Convenience execute functions in `aws_http_bridge.u` for all HTTP methods
- Proper header conversion between AWS client types and `@unison/http` library

#### Conditional Code Generation
- AWS service detection using `aws.api#service` and `aws.auth#sigv4` traits
- Conditional runtime module copying (only for AWS services)
- Protocol-aware module selection (`aws_xml.u` for XML protocols, `aws_s3.u` for S3)
- Conditional Config types (AWS-style vs generic for non-AWS services)
- Model type generation for non-AWS services (structures, errors with `toFailure`)

#### Runtime Modules
- `aws_credentials.u` runtime module with credential provider chain
- `aws_config.u` runtime module with configuration types and helpers
- `aws_s3.u` runtime module with S3 URL building and bucket validation
- `aws_http.u` runtime module with HTTP request/response utilities
- `aws_xml.u` runtime module with XML encoding/decoding utilities
- `aws_sigv4.u` runtime module with SigV4 signing implementation
- RuntimeModuleCopier for bundling runtime modules with generated code

#### Pagination Support
- PaginationGenerator for `@paginated` operations
- Auto-paginating helper functions (e.g., `listObjectsV2All`)

#### Type Generation
- StructureGenerator for Unison record types
- EnumGenerator for Unison sum types with `toText`/`fromText` functions
- UnionGenerator for Unison sum types with payloads
- ErrorGenerator for error record types with `toFailure` conversion
- ServiceErrorGenerator for unified service error sum types

#### Protocol Support
- RestXmlProtocolGenerator with operation code generation
- HTTP binding trait support: `@http`, `@httpLabel`, `@httpQuery`, `@httpHeader`, `@httpPayload`, `@httpResponseCode`
- Error parsing functions for XML and JSON protocols
- HTTP error handler generation
- S3UrlGenerator for S3-specific URL building (virtual-hosted and path-style)
- XmlGenerator for XML encoding/decoding utilities
- ProtocolGeneratorFactory for protocol-based code generation routing

#### Operation Generation
- Full operation implementations for REST-XML services (S3)
- Automatic URL building with path parameter substitution
- Query string generation from @httpQuery members
- Request header generation from @httpHeader members
- Response header extraction and body parsing

#### Authentication
- SigV4Generator for AWS Signature Version 4 request signing

#### Core Infrastructure
- UnisonWriter with record type, union type, function, and match expression methods
- UnisonSymbolProvider with complete Smithy-to-Unison type mappings
- Exception-based error handling pattern for operations

#### Project Setup
- Smithy Build plugin integration
- GitHub CI workflow
- Error-types example

### Changed
- Operations now use `'{IO, Exception, Http}` abilities instead of Response sum type
