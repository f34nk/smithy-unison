# Changelog

## [Unreleased]

### Fixed

#### Union Deserialization in REST-JSON (key-dispatch)
- `generateUnionDeserializer` in `RestJsonProtocolGenerator` now uses the correct AWS REST-JSON wire format: extract the single discriminant key from the JSON object and `match key with` to construct the right union variant
- Replaces the fragile try-each-variant `match catch do` / `Left _ ->` approach, which failed for variants whose value types share a shape
- `coreJsonObjectKey` and `coreJsonObjectValue` helpers added to `aws_json_bridge.u` for extracting the discriminant key and its value from a `core.Json` object
- `@jsonName` trait is respected when matching variant keys

### Added

#### Runtime Tests for XML Map, Float, and Timestamp Extraction
- `aws_xml_bridge_test.u`: tests for `aws.xml.extractMapSoup` (default tags, custom tags, empty container)
- `aws_xml_bridge_test.u`: tests for the `findText |> Optional.flatMap Float.fromText` float extraction pattern
- `aws_xml_bridge_test.u`: tests for the `findText` timestamp extraction pattern

### Fixed

#### REST-XML Response Fields of Previously Unsupported Types
- Timestamp fields now extract as `Optional Text` (or `Text`) via `aws.xml.findText` instead of a `None` stub
- Float and Double fields now extract via `aws.xml.findText |> Optional.flatMap Float.fromText`
- Map fields now extract via `aws.xml.findOpt` + `aws.xml.extractMapSoup` + `Map.fromList`; key/value tag names are resolved from `@xmlName` traits
- Union and Document response fields emit a type-specific `None -- TODO` stub instead of the generic `None -- TODO: parse <type>` placeholder
- The generic catch-all in `generateXmlFieldExtraction` is replaced with an `IllegalArgumentException` to surface unrecognised types at code-generation time

#### Optional Scalar Fields in Flattened Nested Structs (AWS Query)
- `generateFlattenedStructure` now emits `opt_* = match ... with None -> [] | Some v -> [...]` let-bindings for optional scalar members
- Optional lists are concatenated to the required field list via `List.++`
- Removes the `-- TODO: Handle optional nested field` stub

#### List-Valued Query Parameters (REST-JSON)
- `generateQueryString` separates scalar and list-valued `@httpQuery` members
- Each list element becomes a separate `key=value` repetition via `List.map` and `aws.http.urlEncode`
- Removes the `None -- TODO: list-valued query parameter not supported` stub

### Added

#### Shared AWS Configuration Types
- Shared `aws.config.Config` and `aws.config.Credentials` types replacing per-service duplicates
- Type-safe newtype wrappers: `Region`, `Service`, `HostName`, `Port`
- Region constants for common AWS regions (usEast1, usWest2, euWest1, etc.)
- `Config.default` for standard AWS endpoints and `Config.withEndpoint` for custom endpoints
- Credential constructors: `anonymous`, `basicCredentials`, `temporaryCredentials`
- Service-specific `defaultConfig` convenience functions generated for each AWS client
- Unit tests for aws_config runtime module

### Changed

#### Protocol Generators Use Shared Config
- All protocol generators now reference `aws.config.Config` instead of per-service Config types
- PaginationGenerator updated to use shared config type in helper function signatures
- Field accessors use newtype extractors (e.g., `Region.name`, `HostName.toText`)
- Demo examples updated to use shared configuration types

### Removed

#### Per-Service Config Types
- Removed redundant Config and Credentials type generation from ClientModuleWriter
- All services now use the shared aws_config.u runtime module

### Added

#### EC2 Demo Example
- Integration test against LocalStack with EC2 Query protocol
- DescribeSecurityGroups, DescribeSubnets, DescribeVpcs, DescribeInstances, and RunInstances operations
- Terraform configuration for LocalStack EC2 setup

#### EC2 XML Parsing Isolation Tests
- Runtime validation tests for extractElement and extractAllBlocks functions
- Sample LocalStack XML responses for VPC and SecurityGroup parsing

### Fixed

#### XML Element Name Resolution
- XML parsers now respect @xmlName trait when extracting fields from EC2 Query responses
- Fallback to capitalized member name for non-EC2 protocols

#### HTTP Bridge URI Parsing
- Fixed host:port separation in parseUri to correctly construct Authority type
- Resolves connection failures to LocalStack endpoints with custom ports

#### Runtime Module Operator Ambiguity
- Replaced Universal.== with Text.eq, Char.eq, and Nat.eq in aws_xml.u, aws_http.u, aws_sigv4.u, and aws_json.u
- Fixed hardcoded library references in aws_json_bridge.u and aws_s3.u

#### Protocol Generator XML Parsing and Enum Handling
- Fixed aws.xml.runXml invocation in EC2 and AWS Query protocols
- Added enum type support to request serialization with proper toText function generation
- Fixed reserved word handling in generated variable names using UnisonReservedWords.appendSuffix
- Prevented orphaned code generation in REST-XML by handling serialization inline

### Added

#### Selective Operation Generation
- Configuration support for specifying operations to generate via smithy-build.json
- OperationSelector for filtering operations by name or shape ID
- TransitiveDependencyCollector for recursive dependency collection from selected operations
- Automatic inclusion of all transitive dependencies (nested structures, errors, enums, unions)
- Integration across all code generation phases (types, serializers, deserializers, pagination helpers)
- Operation filtering for REST-JSON list and map deserializers
- Operation filtering for pagination helper generation
- Support for all AWS protocols (AWS JSON, REST-JSON, REST-XML, AWS Query, EC2 Query)
- Validation across DynamoDB, Lambda, S3, and SNS demos

#### REST-JSON Response Deserializer Generation
- Complete JSON deserializer generation for REST-JSON protocol responses
- Structure deserializers with field-by-field parsing and exception handling
- Enum deserializers with string-to-enum conversion and validation
- Union deserializers with tagged union deserialization
- List deserializers with element-wise deserialization using `mapWithException`
- Map deserializers with key-value pair deserialization using `mapPairsWithException`
- Recursive collection of shapes needing deserializers from service and resource operations
- Upfront nested structure serializer generation to avoid duplication

#### Resource Operation Support
- Resource operation collection throughout code generator components
- ClientModuleWriter collects operations from both service and resources
- RestJsonProtocolGenerator supports resource operations
- PaginationGenerator handles resource operations

#### Pagination Token Field Inference
- Automatic inference of input token field names when not explicitly specified in `@paginated` trait
- Automatic inference of output token field names with fallback defaults
- Case-insensitive search for common token field patterns (marker, continuationToken, nextToken, pageToken)
- Fallback to sensible defaults (continuationToken, nextContinuationToken)

#### JSON Bridge Enhancements
- `parseFloat` function for REST-JSON float field parsing
- `parseBlob` function for base64-encoded blob deserialization
- `mapWithException` helper for sequencing exception-raising list deserializations
- `mapPairsWithException` helper for sequencing exception-raising map value deserializations
- Refactored existing parse functions to use explicit match expressions

### Fixed

#### REST-JSON Optional Payload Handling
- Corrected jsonObject reference from `aws.json.JsonObject` to `jsonObject` in optional payload serialization
- Proper empty JSON object default for optional structure payloads

### Added

#### AWS Query Protocol Full Implementation
- Complete AWS Query protocol support (SQS, SNS, RDS) with request serialization and response deserialization
- XML-based request parameter serialization with proper Query format encoding
- XML response parsing with map and list extraction support
- Structure list serialization with handling of required and optional fields
- Support for @xmlName and @xmlFlattened traits in serialization and deserialization
- Service error parsing from XML error responses
- AWS SigV4 request signing integration with form-encoded body

#### EC2 Query Protocol Implementation
- EC2 Query protocol support extending AWS Query with protocol-specific differences
- Custom response wrapper navigation (OperationNameResponse without nested Result element)
- EC2-specific error format parsing (Response/Errors/Error structure)
- Complete test coverage with ProtocolGeneratorFactoryTest

#### AWS Query XML Map and List Deserialization
- Implemented map extraction from XML responses using entry/key/value structure
- Support for flattened and wrapped maps with @xmlFlattened trait
- List extraction for scalar types and structures
- Enum list deserialization with fromText conversion
- Required vs optional field handling in nested structures
- Proper namespace handling for generated type references

#### SNS Demo Example
- Integration test against LocalStack with complete SNS workflow
- Topic creation, subscription management, and message publishing
- Terraform configuration for LocalStack SNS setup
- Demonstrates AWS Query protocol usage with real AWS service

### Fixed

#### Error Constructor Generation
- Use fully qualified type names in error fromMessage functions to avoid ambiguity
- Handle required vs optional message fields correctly
- Provide appropriate default values (empty string, false, +0) for required non-message fields

#### AWS Query Request Serialization
- Support for @default trait - fields with defaults are treated as non-optional
- Structure list serialization with proper field-by-field parameter generation
- Skip complex nested types (maps/lists/structures within structures) with TODO markers
- Optional field handling in structure lists using conditional parameter inclusion

#### AWS Query Response Parsing
- Empty structure response parsing using fully qualified type names
- Enum field deserialization with proper type conversion and optionality handling
- Required scalar field extraction without Optional wrapping in nested structures

#### Trait Support Updates
- Updated TRAITS.md to reflect @xmlFlattened and @xmlName implementation status
- Updated TRAITS.md to reflect @default trait full support
- Updated TRAITS.md to reflect AWS Query and EC2 Query protocol implementation

### Changed

#### Namespace Naming Convention
- Migrated all Unison namespaces from PascalCase to lowercase per Unison conventions
- Namespace format: `aws.json`, `aws.xml`, `aws.http`, `aws.sigv4`, `aws.config`, `aws.credentials`, `aws.s3`
- Type names remain PascalCase within lowercase namespaces (e.g., `aws.sigv4.Credentials`, `aws.sigv4.SigningConfig`)
- Updated all runtime modules, test files, and code generators to use new naming convention

### Added

#### AWS JSON Protocol Full Implementation
- Complete AWS JSON 1.0/1.1 protocol support with request serialization, response deserialization, and error parsing
- Generate JSON serializers and deserializers for nested structures
- Service error union types with `toFailure` conversion for exception handling
- Proper handling of required vs optional fields using `@required` and `@default` traits
- `Optional.flatMap` chain pattern for deserializing structures with required fields

#### DynamoDB Demo
- Integration test against LocalStack with ListTables, PutItem, GetItem, and DeleteItem operations
- Terraform configuration for LocalStack table provisioning
- Compiled demo using generated DynamoDB client

### Fixed

#### AWS JSON Request Signing
- Use `addSigningHeaders` instead of `signRequest` to preserve Content-Type and X-Amz-Target headers
- Include original headers in signed requests (previously only signing headers were sent)

#### Smithy Document Type Support
- Map Smithy `document` shapes to `aws.json.JsonValue` runtime type
- Pass-through serialization for document types in JSON protocols
- Pass-through deserialization for document types in JSON protocols
- Support for document types in nested structures, lists, and maps
- Enable schema-less JSON data in AWS services

#### DynamoDB AttributeValue Type Support
- Special handling for DynamoDB AttributeValue union type
- Skip code generation for AttributeValue, use runtime type `aws.json.AttributeValue`
- Automatic detection of `com.amazonaws.dynamodb#AttributeValue` shape
- Map AttributeValue fields to runtime type in structure generation
- Use `aws.json.attributeValueToJson` for request serialization
- Use `aws.json.jsonToAttributeValue` for response deserialization
- Support for AttributeValue in lists and maps (nested collections)

#### AWS JSON Protocol Operation Generation
- Enhanced operation generation for AWS JSON 1.0/1.1 protocols with complete implementation
- Proper X-Amz-Target header formation using service name and operation name
- AWS Signature Version 4 (SigV4) request signing integration
- Explicit status code checking (2xx for success, 4xx/5xx for errors)
- JSON request body serialization with proper Content-Type headers
- Service-specific error handling using generated parseError and toFailure functions
- Dynamic service name extraction for signing (removes version suffixes)
- Support for operations with and without input/output structures

#### AWS JSON Protocol Error Parsing
- Implemented error parsing for AWS JSON 1.0/1.1 protocols
- Generate `parseError` function for each service
- Parse `__type` field from JSON error responses
- Extract error message from `message` or `Message` fields
- Handle both full format (`com.amazon.coral#ErrorName`) and short format (`ErrorName`)
- Map error types to service error variants using `fromCodeAndMessage`
- Fallback to `UnknownError` for unrecognized error types
- Leverage runtime helpers: `extractErrorType` and `extractErrorMessage`

#### AWS JSON Protocol Response Deserialization
- Implemented response deserialization for AWS JSON 1.0/1.1 protocols
- Generate JSON response parsers for all operation outputs
- Support for primitive types (string, boolean, integer, float)
- Support for complex types (lists, maps, nested structures)
- Support for optional fields with missing value handling
- Support for blob types with Base64 decoding
- Support for timestamp types with ISO-8601 parsing
- Support for enum types with fromText conversion
- Required field validation with exception raising
- Respect `@jsonName` trait for custom field names

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
- `aws.http.RetryConfig` type for configurable retry behavior
- `aws.http.RetryResult` tracking attempts and delay times
- `aws.http.calculateBackoff` for exponential backoff calculation
- `aws.http.parseRetryAfter` to parse Retry-After headers
- `aws.http.withRetry` and convenience retry functions

#### AWS Error Classification and Parsing
- Error classification helpers: `isNotFound`, `isAccessDenied`, `isThrottled`, `isRetryable`
- `aws.http.Error.toText` for detailed error logging
- `aws.http.parseErrorXml` and `aws.http.parseError` for structured error parsing
- Try/catch helpers: `aws.http.try`, `aws.http.tryOptional`
- Extended `aws.http.Error` with `hostId` field for S3 debugging

#### Improved HTTP Error Handling
- `aws.http.Error` type for structured AWS error information
- `handleHttpResponse` now parses AWS XML error responses and raises proper exceptions
- Helper functions: `aws.http.isSuccessResponse`, `aws.http.isEmptyResponse`
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
