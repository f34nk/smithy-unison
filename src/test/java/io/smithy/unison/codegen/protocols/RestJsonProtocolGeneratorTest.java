package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonSettings;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.pattern.UriPattern;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RestJsonProtocolGenerator.
 * 
 * <p>Tests cover:
 * <ul>
 *   <li>HTTP binding detection</li>
 *   <li>Path parameter substitution</li>
 *   <li>Query parameter building</li>
 *   <li>Request body generation</li>
 *   <li>Error parsing</li>
 *   <li>Response deserialization</li>
 * </ul>
 */
public class RestJsonProtocolGeneratorTest {
    
    private RestJsonProtocolGenerator generator;
    private UnisonWriter writer;
    
    @BeforeEach
    void setUp() {
        generator = new RestJsonProtocolGenerator();
        writer = new UnisonWriter("test.api");
    }
    
    // =============================================================================
    // Basic Generator Tests
    // =============================================================================
    
    @Test
    void testGetProtocol() {
        assertEquals(ShapeId.from("aws.protocols#restJson1"), generator.getProtocol());
    }
    
    @Test
    void testGetName() {
        assertEquals("restJson1", generator.getName());
    }
    
    @Test
    void testGetContentType() {
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .build();
        
        assertEquals("application/json", generator.getContentType(service));
    }
    
    @Test
    void testGetDefaultMethod() {
        // REST protocols return null - method comes from @http trait
        assertNull(generator.getDefaultMethod());
    }
    
    @Test
    void testGetDefaultUri() {
        // REST protocols return null - URI comes from @http trait
        assertNull(generator.getDefaultUri());
    }
    
    // =============================================================================
    // Test HTTP Binding Detection
    // =============================================================================
    
    @Test
    void testHttpBoundMemberDetection() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        // Input with various HTTP-bound members
        StructureShape inputShape = StructureShape.builder()
                .id("test.api#TestInput")
                .addMember(MemberShape.builder()
                        .id("test.api#TestInput$ResourceId")
                        .target("smithy.api#String")
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#TestInput$Filter")
                        .target("smithy.api#String")
                        .addTrait(new HttpQueryTrait("filter"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#TestInput$Token")
                        .target("smithy.api#String")
                        .addTrait(new HttpHeaderTrait("X-Auth-Token"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#TestInput$Data")
                        .target("smithy.api#String")
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.api#TestOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#TestOperation")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("POST")
                        .uri(UriPattern.parse("/resources/{ResourceId}"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateOperation(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify path parameter substitution
        assertTrue(output.contains("ResourceId") || output.contains("resourceId"),
                "Should handle path parameter. Got: " + output);
        
        // Verify query parameters
        assertTrue(output.contains("filter"),
                "Should handle query parameter. Got: " + output);
        
        // Verify headers
        assertTrue(output.contains("X-Auth-Token") || output.contains("headers"),
                "Should handle header parameter. Got: " + output);
        
        // Verify body serialization is present (may be in separate function)
        assertTrue(output.contains("body") || output.contains("Body") || output.contains("RequestBody"),
                "Should have body serialization. Got: " + output);
    }
    
    @Test
    void testBodyMemberFiltering() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        // Input where all members are HTTP-bound (no body)
        StructureShape inputShape = StructureShape.builder()
                .id("test.api#GetInput")
                .addMember(MemberShape.builder()
                        .id("test.api#GetInput$Id")
                        .target("smithy.api#String")
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#GetInput$Filter")
                        .target("smithy.api#String")
                        .addTrait(new HttpQueryTrait("filter"))
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.api#GetOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#GetOperation")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/items/{Id}"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateOperation(operation, writer, context);
        
        String output = writer.toString();
        
        // Should have empty body or minimal JSON body
        assertTrue(output.contains("empty") || output.contains("{}") || output.contains("No body"),
                "Should generate empty body for all HTTP-bound members. Got: " + output);
    }
    
    // =============================================================================
    // Test Path Parameter Substitution
    // =============================================================================
    
    @Test
    void testPathParameterSubstitution() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("test.api#GetResourceInput")
                .addMember(MemberShape.builder()
                        .id("test.api#GetResourceInput$ResourceId")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .addTrait(new HttpLabelTrait())
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.api#GetResourceOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#GetResource")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/resources/{ResourceId}/actions"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateOperation(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify URI pattern is used
        assertTrue(output.contains("/resources/{ResourceId}/actions") || 
                   output.contains("ResourceId"),
                "Should reference path parameter. Got: " + output);
        
        // Verify URL building code
        assertTrue(output.contains("uri") || output.contains("url"),
                "Should generate URL building code. Got: " + output);
    }
    
    @Test
    void testMultiplePathParameters() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("test.api#GetObjectInput")
                .addMember(MemberShape.builder()
                        .id("test.api#GetObjectInput$Bucket")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#GetObjectInput$Key")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .addTrait(new HttpLabelTrait())
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.api#GetObjectOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#GetObject")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/buckets/{Bucket}/objects/{Key}"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateOperation(operation, writer, context);
        
        String output = writer.toString();
        
        // Both parameters should be referenced
        assertTrue(output.contains("Bucket") || output.contains("bucket"),
                "Should handle Bucket parameter. Got: " + output);
        assertTrue(output.contains("Key") || output.contains("key"),
                "Should handle Key parameter. Got: " + output);
    }
    
    // =============================================================================
    // Test Query Parameter Building
    // =============================================================================
    
    @Test
    void testQueryParameterSerialization() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        IntegerShape intShape = IntegerShape.builder()
                .id("smithy.api#Integer")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("test.api#ListInput")
                .addMember(MemberShape.builder()
                        .id("test.api#ListInput$MaxResults")
                        .target("smithy.api#Integer")
                        .addTrait(new HttpQueryTrait("maxResults"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#ListInput$NextToken")
                        .target("smithy.api#String")
                        .addTrait(new HttpQueryTrait("nextToken"))
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.api#ListOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#ListItems")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/items"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(intShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateOperation(operation, writer, context);
        
        String output = writer.toString();
        
        // Should reference query parameter names
        assertTrue(output.contains("maxResults"),
                "Should include maxResults query parameter. Got: " + output);
        assertTrue(output.contains("nextToken"),
                "Should include nextToken query parameter. Got: " + output);
        
        // Should have query string building logic
        assertTrue(output.contains("queryString") || output.contains("queryParams"),
                "Should generate query string building code. Got: " + output);
    }
    
    @Test
    void testEmptyQueryParameters() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("test.api#SimpleInput")
                .addMember(MemberShape.builder()
                        .id("test.api#SimpleInput$Data")
                        .target("smithy.api#String")
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.api#SimpleOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#SimpleOperation")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("POST")
                        .uri(UriPattern.parse("/items"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateOperation(operation, writer, context);
        
        String output = writer.toString();
        
        // Should handle case with no query parameters
        assertTrue(output.contains("queryString = \"\"") || output.contains("Map.empty"),
                "Should generate empty query string. Got: " + output);
    }
    
    // =============================================================================
    // Test Request Body Generation
    // =============================================================================
    
    @Test
    void testRequestBodyOnlyUnboundFields() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        IntegerShape intShape = IntegerShape.builder()
                .id("smithy.api#Integer")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("test.api#CreateInput")
                .addMember(MemberShape.builder()
                        .id("test.api#CreateInput$Id")
                        .target("smithy.api#String")
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#CreateInput$Filter")
                        .target("smithy.api#String")
                        .addTrait(new HttpQueryTrait("filter"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#CreateInput$Token")
                        .target("smithy.api#String")
                        .addTrait(new HttpHeaderTrait("X-Token"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#CreateInput$Name")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#CreateInput$Count")
                        .target("smithy.api#Integer")
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.api#CreateOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#CreateItem")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("POST")
                        .uri(UriPattern.parse("/items/{Id}"))
                        .code(201)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(intShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateRequestSerializer(operation, writer, context);
        
        String output = writer.toString();
        
        // HTTP-bound members should NOT be in body
        assertFalse(output.contains("\"Id\"") && output.contains("\"Filter\""),
                "Should not include HTTP-bound members in body. Got: " + output);
        
        // Unbound members SHOULD be in body
        assertTrue(output.contains("Name") || output.contains("name"),
                "Should include Name in body. Got: " + output);
        assertTrue(output.contains("Count") || output.contains("count"),
                "Should include Count in body. Got: " + output);
    }
    
    @Test
    void testOptionalFieldHandling() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        BooleanShape boolShape = BooleanShape.builder()
                .id("smithy.api#Boolean")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("test.api#UpdateInput")
                .addMember(MemberShape.builder()
                        .id("test.api#UpdateInput$Name")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#UpdateInput$Description")
                        .target("smithy.api#String")
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#UpdateInput$Enabled")
                        .target("smithy.api#Boolean")
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.api#UpdateOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#UpdateItem")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("PUT")
                        .uri(UriPattern.parse("/items"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(boolShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateRequestSerializer(operation, writer, context);
        
        String output = writer.toString();
        
        // Should handle both required and optional fields
        assertTrue(output.contains("Name") || output.contains("name"),
                "Should include required field. Got: " + output);
        assertTrue(output.contains("Description") || output.contains("description"),
                "Should include optional field. Got: " + output);
        
        // Should use Optional handling for optional fields
        assertTrue(output.contains("Optional") || output.contains("Some") || output.contains("None"),
                "Should use Optional handling for optional fields. Got: " + output);
    }
    
    // =============================================================================
    // Test Error Parsing
    // =============================================================================
    
    @Test
    void testErrorParserGeneration() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("test.api#TestInput")
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.api#TestOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#TestOperation")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("POST")
                        .uri(UriPattern.parse("/test"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateErrorParser(operation, writer, context);
        
        String output = writer.toString();
        
        // Should generate parseError function
        assertTrue(output.contains("parseError"),
                "Should generate parseError function. Got: " + output);
        
        // Should check multiple error code locations for REST-JSON
        assertTrue(output.contains("__type") || output.contains("code") || output.contains("Code"),
                "Should check multiple error code locations. Got: " + output);
        
        // Should extract error message
        assertTrue(output.contains("message") || output.contains("Message"),
                "Should extract error message. Got: " + output);
        
        // Should reference service error type
        assertTrue(output.contains("TestServiceError") || output.contains("errorFromCodeAndMessage"),
                "Should reference service error type. Got: " + output);
    }
    
    // =============================================================================
    // Test Response Deserialization
    // =============================================================================
    
    @Test
    void testResponseHeaderExtraction() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("test.api#GetInput")
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.api#GetOutput")
                .addMember(MemberShape.builder()
                        .id("test.api#GetOutput$RequestId")
                        .target("smithy.api#String")
                        .addTrait(new HttpHeaderTrait("x-request-id"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#GetOutput$Data")
                        .target("smithy.api#String")
                        .build())
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#GetItem")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/items"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateResponseDeserializer(operation, writer, context);
        
        String output = writer.toString();
        
        // Should extract header
        assertTrue(output.contains("x-request-id") || output.contains("getHeader") || output.contains("RequestId"),
                "Should extract response header. Got: " + output);
        
        // Should also parse body fields
        assertTrue(output.contains("Data") || output.contains("data"),
                "Should parse body fields. Got: " + output);
    }
    
    @Test
    void testResponseBodyParsing() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        IntegerShape intShape = IntegerShape.builder()
                .id("smithy.api#Integer")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("test.api#ListInput")
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.api#ListOutput")
                .addMember(MemberShape.builder()
                        .id("test.api#ListOutput$Items")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#ListOutput$Count")
                        .target("smithy.api#Integer")
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#ListOutput$CustomField")
                        .target("smithy.api#String")
                        .addTrait(new JsonNameTrait("custom_field"))
                        .build())
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#ListItems")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/items"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(intShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateResponseDeserializer(operation, writer, context);
        
        String output = writer.toString();
        
        // Should parse JSON response
        assertTrue(output.contains("json") || output.contains("parseJson"),
                "Should parse JSON response. Got: " + output);
        
        // Should extract all fields
        assertTrue(output.contains("Items") || output.contains("items"),
                "Should extract Items field. Got: " + output);
        assertTrue(output.contains("Count") || output.contains("count"),
                "Should extract Count field. Got: " + output);
        
        // Should respect @jsonName trait
        assertTrue(output.contains("custom_field"),
                "Should respect @jsonName trait. Got: " + output);
    }
    
    // =============================================================================
    // Test List-Valued Query Parameters
    // =============================================================================

    @Test
    void testListQueryParameterExpansion() {
        // A list<string> member bound to @httpQuery must expand into repeated key=value
        // pairs, not a single None placeholder.
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();

        IntegerShape intShape = IntegerShape.builder()
                .id("smithy.api#Integer")
                .build();

        // list<string> — member is embedded in the ListShape, NOT added separately
        ListShape statusList = ListShape.builder()
                .id("test.api#StatusList")
                .member(ShapeId.from("smithy.api#String"))
                .build();

        StructureShape inputShape = StructureShape.builder()
                .id("test.api#ListFilterInput")
                // list-valued @httpQuery param
                .addMember(MemberShape.builder()
                        .id("test.api#ListFilterInput$Status")
                        .target("test.api#StatusList")
                        .addTrait(new HttpQueryTrait("Status"))
                        .build())
                // scalar @httpQuery param alongside it
                .addMember(MemberShape.builder()
                        .id("test.api#ListFilterInput$MaxResults")
                        .target("smithy.api#Integer")
                        .addTrait(new HttpQueryTrait("MaxResults"))
                        .build())
                .build();

        StructureShape outputShape = StructureShape.builder()
                .id("test.api#ListFilterOutput")
                .build();

        OperationShape operation = OperationShape.builder()
                .id("test.api#ListFilter")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/items"))
                        .code(200)
                        .build())
                .build();

        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();

        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(intShape)
                .addShape(statusList)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();

        UnisonContext context = createTestContext(model, service);
        generator.generateOperation(operation, writer, context);
        String output = writer.toString();

        // scalarParts should be emitted (not the old queryParts)
        assertTrue(output.contains("scalarParts"),
                "Should use scalarParts for scalar query params. Got: " + output);

        // List expansion: should contain List.map and urlEncode for the list param
        assertTrue(output.contains("statusQueryParts") || output.contains("StatusQueryParts"),
                "Should emit a listPartsVar for the list param. Got: " + output);
        assertTrue(output.contains("List.map"),
                "Should use List.map to expand list query param. Got: " + output);

        // filteredParts must concatenate both
        assertTrue(output.contains("List.++"),
                "filteredParts should concat scalarParts and list parts with List.++. Got: " + output);

        // No None stub
        assertFalse(output.contains("-- TODO: list-valued query parameter not supported"),
                "Should not emit TODO stub for list query param. Got: " + output);
        assertFalse(output.contains("queryParts"),
                "Should not use old queryParts variable name. Got: " + output);
    }

    // =============================================================================
    // Test Union Deserialization — Key-dispatch strategy
    // =============================================================================

    @Test
    void testUnionDeserializerUsesKeyDispatch() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();

        IntegerShape intShape = IntegerShape.builder()
                .id("smithy.api#Integer")
                .build();

        UnionShape myUnion = UnionShape.builder()
                .id("test.api#MyUnion")
                .addMember(MemberShape.builder()
                        .id("test.api#MyUnion$StringMember")
                        .target("smithy.api#String")
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.api#MyUnion$IntMember")
                        .target("smithy.api#Integer")
                        .build())
                .build();

        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .build();

        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(intShape)
                .addShape(myUnion)
                .addShape(service)
                .build();

        UnisonContext context = createTestContext(model, service);
        generator.generateUnionDeserializer(myUnion, writer, context);
        String output = writer.toString();

        // Key-dispatch: must extract discriminant key from the JSON object
        assertTrue(output.contains("coreJsonObjectKey"),
                "Should use coreJsonObjectKey for key-dispatch. Got: " + output);

        // Must dispatch on the extracted key
        assertTrue(output.contains("match key with"),
                "Should emit 'match key with' dispatch. Got: " + output);

        // Must include both variant names as string match arms
        assertTrue(output.contains("\"StringMember\""),
                "Should match on StringMember key. Got: " + output);
        assertTrue(output.contains("\"IntMember\""),
                "Should match on IntMember key. Got: " + output);

        // Must not use the old try-each catch/Left-_ approach
        assertFalse(output.contains("Left _ ->"),
                "Should not use try-each Left _ -> approach. Got: " + output);
        assertFalse(output.contains("match catch do"),
                "Should not use match catch do. Got: " + output);
    }

    @Test
    void testUnionDeserializerRespectsJsonNameTrait() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();

        UnionShape myUnion = UnionShape.builder()
                .id("test.api#TaggedUnion")
                .addMember(MemberShape.builder()
                        .id("test.api#TaggedUnion$StringValue")
                        .target("smithy.api#String")
                        .addTrait(new JsonNameTrait("string_value"))
                        .build())
                .build();

        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2024-01-01")
                .build();

        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(myUnion)
                .addShape(service)
                .build();

        UnisonContext context = createTestContext(model, service);
        generator.generateUnionDeserializer(myUnion, writer, context);
        String output = writer.toString();

        // The @jsonName value should be used as the match key, not the member name
        assertTrue(output.contains("\"string_value\""),
                "Should use @jsonName value as match key. Got: " + output);
        assertFalse(output.contains("\"StringValue\""),
                "Should not use raw member name when @jsonName is present. Got: " + output);
    }

    /**
     * Creates a test UnisonContext with minimal configuration.
     */
    private UnisonContext createTestContext(Model model, ServiceShape service) {
        UnisonSettings settings = UnisonSettings.builder()
                .service(service.getId())
                .namespace("test.api")
                .build();
        
        SymbolProvider symbolProvider = new UnisonSymbolProvider(model, settings);
        FileManifest fileManifest = FileManifest.create(Paths.get(System.getProperty("java.io.tmpdir")));
        
        // Create a WriterDelegator for the context
        WriterDelegator<UnisonWriter> writerDelegator = new WriterDelegator<>(
                fileManifest,
                symbolProvider,
                UnisonWriter.factory());
        
        return UnisonContext.builder()
                .model(model)
                .settings(settings)
                .symbolProvider(symbolProvider)
                .fileManifest(fileManifest)
                .writerDelegator(writerDelegator)
                .service(service)
                .build();
    }
}
