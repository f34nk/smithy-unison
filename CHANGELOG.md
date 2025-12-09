# Changelog

## [Unreleased]

### Added

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
