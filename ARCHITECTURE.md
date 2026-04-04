# Smithy Unison Architecture

This document describes the architecture of the Smithy Unison code generator, which follows Smithy's recommended `DirectedCodegen` pattern for extensibility and maintainability.

Reference: [Creating a Code Generator](https://smithy.io/2.0/guides/building-codegen/index.html)

## Overview

The code generator transforms Smithy service models into Unison client modules (`.u` files). It uses a plugin-based architecture that allows customization at multiple points in the generation process.

```
┌────────────────────────────────────────────────────────────────────┐
│                         Smithy Build                               │
│  ┌───────────────┐    ┌──────────────────┐    ┌─────────────────┐  │
│  │ smithy-build  │───▶│ UnisonCodegen    │───▶│ Generated       │  │
│  │ .json         │    │ Plugin           │    │ .u files        │  │
│  └───────────────┘    └──────────────────┘    └─────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. UnisonCodegenPlugin

The main entry point that implements `SmithyBuildPlugin`. It orchestrates code generation using `CodegenDirector`.

```java
public final class UnisonCodegenPlugin implements SmithyBuildPlugin {
    @Override
    public void execute(PluginContext context) {
        CodegenDirector<UnisonWriter, UnisonIntegration, UnisonContext, UnisonSettings> director =
            new CodegenDirector<>();
        
        director.directedCodegen(new UnisonGenerator());
        director.integrationClass(UnisonIntegration.class);
        // ... configuration ...
        director.run();
    }
}
```

### 2. UnisonSettings

Immutable configuration object parsed from `smithy-build.json`:

```java
public final class UnisonSettings {
    private final ShapeId service;    // Target service shape
    private final String namespace;   // Output namespace (e.g., "aws.s3")
    private final String outputDir;   // Output directory
    private final String protocol;    // Optional protocol override
}
```

### 3. UnisonContext

Implements `CodegenContext` to provide access to all generation dependencies:

```java
public final class UnisonContext implements CodegenContext<UnisonSettings, UnisonWriter, UnisonIntegration> {
    private final Model model;
    private final UnisonSettings settings;
    private final SymbolProvider symbolProvider;
    private final FileManifest fileManifest;
    private final List<UnisonIntegration> integrations;
    private final WriterDelegator<UnisonWriter> writerDelegator;
}
```

### 4. UnisonGenerator

Implements `DirectedCodegen` to handle shape-by-shape code generation:

```java
public class UnisonGenerator implements DirectedCodegen<UnisonContext, UnisonSettings, UnisonIntegration> {
    
    @Override
    public SymbolProvider createSymbolProvider(CreateSymbolProviderDirective<UnisonSettings> directive) {
        return new UnisonSymbolProvider(directive.model(), directive.settings());
    }
    
    @Override
    public void generateService(GenerateServiceDirective<UnisonContext, UnisonSettings> directive) {
        ClientModuleWriter writer = ClientModuleWriter.fromContext(directive.context());
        writer.generate();  // Handles type generation and runtime module copying
    }
}
```

### 5. UnisonWriter

Extends `SymbolWriter` for Unison-specific code generation:

```java
public class UnisonWriter extends SymbolWriter<UnisonWriter, UnisonImportContainer> {
    
    public UnisonWriter(String namespace) {
        super(new UnisonImportContainer());
        putFormatter('T', this::formatUnisonType);  // $T for types
        putFormatter('N', this::formatUnisonName);  // $N for names
    }
    
    public UnisonWriter writeRecordType(String typeName, List<TypeField> fields) { ... }
    public UnisonWriter writeUnionType(String typeName, String typeParams, List<Variant> variants) { ... }
    public UnisonWriter writeSignature(String name, String signature) { ... }
    public UnisonWriter writeFunction(String name, String params, Runnable body) { ... }
}
```

### 6. ClientModuleWriter

Orchestrates client module generation with conditional logic based on service type:

```java
public final class ClientModuleWriter {
    
    public void generate() throws IOException {
        // Detect protocol and AWS service
        AwsProtocol protocol = AwsProtocolDetector.detectProtocol(service);
        boolean isAws = copier.isAwsService(service, protocol);
        
        // Generate Config type (AWS-style or generic)
        if (isAws) {
            generateAwsConfigTypes(writer);
        } else {
            generateGenericConfigType(writer);
        }
        
        // Generate model types for non-AWS services
        if (!useProtocolGenerator) {
            generateModelTypes(writer);
        }
        
        // Generate operations (including resource operations)
        // For REST-JSON, generate nested structure serializers upfront
        // Copy runtime modules (only for AWS services)
        copyRuntimeModules(protocol);
    }
}
```

### 7. RuntimeModuleCopier

Handles conditional copying of runtime modules based on service and protocol:

```java
public final class RuntimeModuleCopier {
    
    // AWS service detection using traits
    public boolean isAwsService(ServiceShape service, AwsProtocol protocol) {
        return service.findTrait(AWS_SERVICE_TRAIT).isPresent()
            || service.findTrait(AWS_SIGV4_TRAIT).isPresent()
            || protocol != AwsProtocol.UNKNOWN;
    }
    
    // Protocol-aware module copying
    public List<String> copyAwsModulesForProtocol(AwsProtocol protocol, ServiceShape service) {
        // Core modules: aws_sigv4.u, aws_config.u, aws_credentials.u, aws_http.u, aws_http_bridge.u
        // XML protocol: aws_xml.u
        // S3 service: aws_s3.u
    }
}
```

### 8. UnisonSymbolProvider

Implements `SymbolProvider` for Smithy-to-Unison type mapping:

```java
public final class UnisonSymbolProvider implements SymbolProvider {
    
    @Override
    public Symbol toSymbol(Shape shape) {
        return shape.accept(new UnisonTypeVisitor());
    }
    
    public static String toUnisonTypeName(String name) {
        // Keep PascalCase for types
        return name;
    }
    
    public static String toUnisonFunctionName(String name) {
        // Convert PascalCase to camelCase for functions
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
```

## Integration System

### UnisonIntegration Interface

Extends `SmithyIntegration` for pluggable extensions:

```java
public interface UnisonIntegration 
    extends SmithyIntegration<UnisonSettings, UnisonWriter, UnisonContext> {
    
    default String name() { return getClass().getCanonicalName(); }
    
    default byte priority() { return 0; }
    
    default void preprocessModel(UnisonContext context) {
        // Default: no-op
    }
    
    default void postprocessGeneration(UnisonContext context) {
        // Default: no-op
    }
}
```

### Built-in Integrations

| Integration | Status | Purpose |
|-------------|--------|---------|
| `SigV4Generator` | ✅ | Generates AWS SigV4 request signing code |
| `RuntimeModuleCopier` | ✅ | Copies protocol-specific runtime modules |
| `AwsRetryIntegration` | Planned | Copies retry logic module |

### SPI Discovery

Integrations are discovered via Java's `ServiceLoader` mechanism:

```
src/main/resources/META-INF/services/io.smithy.unison.codegen.UnisonIntegration
```

## Protocol Generators

### ProtocolGenerator Interface

```java
public interface ProtocolGenerator {
    ShapeId getProtocol();
    
    void generateOperation(OperationShape operation, UnisonWriter writer, UnisonContext context);
    void generateRequestSerializer(OperationShape operation, UnisonWriter writer, UnisonContext context);
    void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context);
}
```

### Available Protocol Generators

| Protocol | Generator Class | Status | Services |
|----------|----------------|--------|----------|
| REST-XML | `RestXmlProtocolGenerator` | ✅ | S3, CloudFront, Route 53 |
| AWS JSON 1.0/1.1 | `AwsJsonProtocolGenerator` | ✅ | DynamoDB, Lambda, Kinesis |
| REST-JSON | `RestJsonProtocolGenerator` | ✅ | EventBridge, Step Functions, API Gateway, Lambda |
| AWS Query | `AwsQueryProtocolGenerator` | ✅ | SQS, SNS, RDS, CloudWatch |
| EC2 Query | `Ec2QueryProtocolGenerator` | ✅ | EC2, Auto Scaling, ELB Classic |

### Protocol Details

#### AWS Query Protocol

**Request Format:** Form-encoded (URL-encoded) parameters sent via POST
```
Action=CreateTopic&Name=MyTopic&Version=2010-03-31
```

**Response Format:** XML with nested result wrapper
```xml
<CreateTopicResponse>
  <CreateTopicResult>
    <TopicArn>arn:aws:sns:us-east-1:123456789012:MyTopic</TopicArn>
  </CreateTopicResult>
</CreateTopicResponse>
```

**Key Features:**
- Parameters serialized using dot notation: `Tags.member.1.Key=env`
- Lists numbered starting from 1: `.member.1`, `.member.2`
- Maps serialized as entries: `.entry.1.key`, `.entry.1.value`
- Optional scalar fields in flattened nested structs emit `opt_* = match ... with None -> [] | Some v -> [...]` bindings concatenated with `List.++`
- Response wrapper: `<OperationNameResponse><OperationNameResult>`
- Error format: `<ErrorResponse><Error><Code>...</Code></Error></ErrorResponse>`

**Implementation:** `AwsQueryProtocolGenerator.java`

#### EC2 Query Protocol

EC2 Query extends AWS Query with these differences:

**Response Format:** Simpler XML structure (no nested Result element)
```xml
<RunInstancesResponse>
  <requestId>...</requestId>
  <reservationId>...</reservationId>
  ...
</RunInstancesResponse>
```

**Error Format:** Different wrapper structure
```xml
<Response>
  <Errors>
    <Error>
      <Code>InvalidParameterValue</Code>
      <Message>...</Message>
    </Error>
  </Errors>
</Response>
```

**Implementation:** `Ec2QueryProtocolGenerator.java` (extends `AwsQueryProtocolGenerator`)

## Code Generation Flow

```
┌───────────────────────────────────────────────────────────────────┐
│                           Code Generation Flow                    │
├───────────────────────────────────────────────────────────────────┤
│                                                                   │
│  1. Plugin Initialization                                         │
│     ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│     │ Load Model   │───▶│ Parse        │───▶│ Create       │      │
│     │              │    │ Settings     │    │ Context      │      │
│     └──────────────┘    └──────────────┘    └──────────────┘      │
│                                                                   │
│  2. Integration Discovery (SPI)                                   │
│     ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│     │ ServiceLoader│───▶│ Sort by      │───▶│ Apply        │      │
│     │ .load()      │    │ Priority     │    │ preprocessors│      │
│     └──────────────┘    └──────────────┘    └──────────────┘      │
│                                                                   │
│  3. Shape Generation (DirectedCodegen)                            │
│     ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│     │ Service      │───▶│ Structures   │───▶│ Unions/Enums │      │
│     │ generateSvc  │    │ generateStruct│   │ generateEnum │      │
│     └──────────────┘    └──────────────┘    └──────────────┘      │
│                                                                   │
│  4. Protocol-Specific Generation                                  │
│     ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│     │ Detect       │───▶│ Select       │───▶│ Generate     │      │
│     │ Protocol     │    │ Generator    │    │ Operations   │      │
│     └──────────────┘    └──────────────┘    └──────────────┘      │
│                                                                   │
│  5. File Output                                                   │
│     ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│     │ Flush        │───▶│ Copy Runtime │───▶│ Write .u     │      │
│     │ Writers      │    │ Modules      │    │ Files        │      │
│     └──────────────┘    └──────────────┘    └──────────────┘      │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

## AWS Service Detection

The generator conditionally generates AWS-specific code based on service traits:

### Detection Priority

1. **`aws.api#service` trait** - Definitive AWS service marker
2. **`aws.auth#sigv4` trait** - AWS authentication requirement
3. **Protocol detection** - AWS protocol traits (`aws.protocols#*`)

### Conditional Generation

| Feature | AWS Service | Non-AWS Service |
|---------|-------------|-----------------|
| Config type | `endpoint`, `region`, `credentials`, `usePathStyle` | `endpoint`, `headers` |
| Credentials type | Generated | Not generated |
| Runtime modules | Copied (protocol-specific) | Not copied |
| Model types | Via protocol generator | Via `generateModelTypes()` |
| Operations | Full implementation (if protocol supported) | Stub implementation |

---

## Protocol Implementation Details

### REST-JSON Protocol

**Status:** ✅ Fully Implemented  
**Generator:** `RestJsonProtocolGenerator.java`  
**Trait:** `aws.protocols#restJson1`  
**Services:** EventBridge, Step Functions, API Gateway, Lambda, AppSync, IoT, Cognito, WAF

#### Overview

The REST-JSON protocol combines RESTful HTTP bindings with JSON request/response serialization. It uses HTTP verbs, URI paths, query strings, and headers for operation routing while serializing structured data as JSON.

#### HTTP Binding Extraction

The generator extracts HTTP bindings from Smithy traits to determine where each input member should be placed:

| Trait | Location | Example |
|-------|----------|---------|
| `@httpLabel` | URI path | `/functions/{FunctionName}/invocations` |
| `@httpQuery` | Query string | `?maxResults=100&nextToken=abc` |
| `@httpHeader` | HTTP header | `X-Amz-Target: EventBridge.PutEvents` |
| `@httpPayload` | HTTP body (raw) | Binary or text payload |
| *(none)* | HTTP body (JSON) | `{"Detail": "...", "DetailType": "..."}` |

**Binding Priority:**
1. Members with explicit HTTP traits (`@httpLabel`, `@httpQuery`, `@httpHeader`, `@httpPayload`)
2. Remaining members serialize to JSON body

**List-valued `@httpQuery` members** are expanded so each element becomes a separate `key=value` repetition (e.g. `Status=A&Status=B`). Scalar params are collected in `scalarParts`; list params generate `<memberName>QueryParts` via `List.map`. Both are concatenated into `filteredParts`.

#### Request Generation Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    REST-JSON Request Generation              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. HTTP Binding Detection                                  │
│     ┌──────────────┐    ┌──────────────┐    ┌────────────┐ │
│     │ Scan Input   │───▶│ Group by     │───▶│ Validate   │ │
│     │ Members      │    │ Trait Type   │    │ Placement  │ │
│     └──────────────┘    └──────────────┘    └────────────┘ │
│                                                              │
│  2. URL Building                                             │
│     ┌──────────────┐    ┌──────────────┐                    │
│     │ Substitute   │───▶│ Append Query │                    │
│     │ Path Params  │    │ Parameters   │                    │
│     └──────────────┘    └──────────────┘                    │
│                                                              │
│  3. Header Construction                                      │
│     ┌──────────────┐    ┌──────────────┐                    │
│     │ Extract      │───▶│ Add Content- │                    │
│     │ @httpHeader  │    │ Type         │                    │
│     └──────────────┘    └──────────────┘                    │
│                                                              │
│  4. Body Serialization                                       │
│     ┌──────────────┐    ┌──────────────┐    ┌────────────┐ │
│     │ Collect Body │───▶│ Serialize to │───▶│ UTF-8      │ │
│     │ Members      │    │ JSON         │    │ Encode     │ │
│     └──────────────┘    └──────────────┘    └────────────┘ │
│                                                              │
│  5. SigV4 Signing                                            │
│     ┌──────────────┐    ┌──────────────┐                    │
│     │ Sign Request │───▶│ Add Auth     │                    │
│     │ (aws.sigv4)  │    │ Headers      │                    │
│     └──────────────┘    └──────────────┘                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### Response Deserialization Flow

```
┌─────────────────────────────────────────────────────────────┐
│                 REST-JSON Response Deserialization           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. Status Check                                             │
│     ┌──────────────┐    ┌──────────────┐    ┌────────────┐ │
│     │ Check Status │───▶│ < 300:       │───▶│ Parse      │ │
│     │ Code         │    │ Success      │    │ Response   │ │
│     └──────────────┘    │ >= 300:      │    └────────────┘ │
│                         │ Error        │                    │
│                         └──────────────┘                    │
│                                ↓                             │
│  2. Error Parsing (if >= 300)                                │
│     ┌──────────────┐    ┌──────────────┐                    │
│     │ Parse JSON   │───▶│ Extract      │                    │
│     │ Body         │    │ __type/code  │                    │
│     └──────────────┘    └──────────────┘                    │
│                                ↓                             │
│     ┌──────────────┐    ┌──────────────┐                    │
│     │ Extract      │───▶│ Map to       │                    │
│     │ message      │    │ ServiceError │                    │
│     └──────────────┘    └──────────────┘                    │
│                                                              │
│  3. Success Parsing                                          │
│     ┌──────────────┐    ┌──────────────┐    ┌────────────┐ │
│     │ Parse JSON   │───▶│ Extract HTTP │───▶│ Construct  │ │
│     │ Body         │    │ Headers      │    │ Output     │ │
│     └──────────────┘    └──────────────┘    └────────────┘ │
│                                                              │
│  4. Deserializer Generation                                  │
│     ┌──────────────┐    ┌──────────────┐    ┌────────────┐ │
│     │ Generate     │───▶│ Generate     │───▶│ Generate   │ │
│     │ List/Map     │    │ Enum/Union   │    │ Structure  │ │
│     │ Deserializers│    │ Deserializers│    │ Deserial.  │ │
│     └──────────────┘    └──────────────┘    └────────────┘ │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

The generator produces complete JSON deserializer functions for:
- **Structures**: Field-by-field parsing with exception handling
- **Enums**: String-to-enum conversion with validation
- **Unions**: Tagged union deserialization
- **Lists**: Element-wise deserialization with `mapWithException`
- **Maps**: Key-value pair deserialization with `mapPairsWithException`

#### Error Handling

REST-JSON errors are returned as JSON with HTTP status >= 300. The error code can appear in multiple locations:

```json
{
  "__type": "ResourceNotFoundException",
  "message": "The resource was not found"
}
```

Or:

```json
{
  "code": "ResourceNotFoundException",
  "message": "The resource was not found"
}
```

The generator handles all variations:
- `__type` field (primary)
- `code` field (secondary)
- `Code` field (alternate casing)
- `Type` field (alternate location)

#### Reserved Keyword Handling

Unison has reserved keywords like `type` that may appear as field names in AWS models. The generator escapes these using backticks:

```unison
-- Field accessor with escaped keyword
Condition.`type` condition

-- Lambda parameter with escaped keyword
Optional.flatMap (`type` -> ...) typeOpt
```

#### Runtime Dependencies

The REST-JSON protocol requires these runtime modules:
- `aws_restjson.u` - URL building, path/query parameter handling
- `aws_json.u` - JSON serialization/deserialization
- `aws_json_bridge.u` - JSON parsing utilities (`parseFloat`, `parseBlob`, `mapWithException`, `mapPairsWithException`)
- `aws_http.u` - HTTP client with retry
- `aws_sigv4.u` - SigV4 request signing

---

## Unison-Specific Considerations

### Output Format

Unison generates `.u` scratch files that users will `add` to their codebase via UCM (Unison Codebase Manager).

Reference: https://www.unison-lang.org/docs/tooling/project-workflows/

### Type Mappings

| Smithy Type | Unison Type |
|-------------|-------------|
| `string` | `Text` |
| `integer`, `long`, `short`, `byte` | `Int` |
| `float`, `double` | `Float` |
| `boolean` | `Boolean` |
| `blob` | `Bytes` |
| `timestamp` | `Text` |
| `list<T>` | `[T]` |
| `map<K, V>` | `Map K V` |
| `structure` | Record type |
| `union` | Sum type |

### Naming Conventions

- **Types**: PascalCase (e.g., `GetObjectInput`)
- **Functions**: camelCase (e.g., `getObject`)
- **Fields**: camelCase (e.g., `bucketName`)
