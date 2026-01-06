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
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.XmlNameTrait;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AwsQueryProtocolGenerator.
 * 
 * <p>Tests cover:
 * <ul>
 *   <li>Parameter serialization (Step 2.7.2)</li>
 *   <li>Action and Version addition (Step 2.7.3)</li>
 *   <li>Form encoding (Step 2.7.4)</li>
 *   <li>XML response parsing (Step 2.7.5)</li>
 *   <li>Error parsing (Step 2.7.6)</li>
 * </ul>
 */
public class AwsQueryProtocolGeneratorTest {
    
    private AwsQueryProtocolGenerator generator;
    private UnisonWriter writer;
    
    @BeforeEach
    void setUp() {
        generator = new AwsQueryProtocolGenerator();
        writer = new UnisonWriter("test.api");
    }
    
    // =============================================================================
    // Basic Generator Tests
    // =============================================================================
    
    @Test
    void testGetProtocol() {
        assertEquals(ShapeId.from("aws.protocols#awsQuery"), generator.getProtocol());
    }
    
    @Test
    void testGetName() {
        assertEquals("awsQuery", generator.getName());
    }
    
    @Test
    void testGetContentType() {
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2012-11-05")
                .build();
        
        assertEquals("application/x-www-form-urlencoded", generator.getContentType(service));
    }
    
    @Test
    void testGetDefaultMethod() {
        assertEquals("POST", generator.getDefaultMethod());
    }
    
    @Test
    void testGetDefaultUri() {
        assertEquals("/", generator.getDefaultUri());
    }
    
    // =============================================================================
    // Step 2.7.2: Test Parameter Serialization
    // =============================================================================
    
    @Test
    void testScalarSerialization() {
        // Create a simple structure with scalar fields
        StringShape stringShape = StringShape.builder()
                .id("test.api#String")
                .build();
        
        IntegerShape intShape = IntegerShape.builder()
                .id("test.api#Integer")
                .build();
        
        BooleanShape boolShape = BooleanShape.builder()
                .id("test.api#Boolean")
                .build();
        
        MemberShape stringMember = MemberShape.builder()
                .id("test.api#TestInput$stringField")
                .target(stringShape.getId())
                .addTrait(new RequiredTrait())
                .build();
        
        MemberShape intMember = MemberShape.builder()
                .id("test.api#TestInput$intField")
                .target(intShape.getId())
                .build();
        
        MemberShape boolMember = MemberShape.builder()
                .id("test.api#TestInput$boolField")
                .target(boolShape.getId())
                .build();
        
        StructureShape input = StructureShape.builder()
                .id("test.api#TestInput")
                .addMember(stringMember)
                .addMember(intMember)
                .addMember(boolMember)
                .build();
        
        Model model = Model.builder()
                .addShapes(stringShape, intShape, boolShape, stringMember, intMember, boolMember, input)
                .build();
        
        // Create context
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2012-11-05")
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#TestOperation")
                .input(input.getId())
                .build();
        
        // Generate serializer
        generator.generateRequestSerializer(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify scalar serialization patterns
        assertTrue(output.contains("stringField"), "Should generate stringField parameter");
        assertTrue(output.contains("intField"), "Should generate intField parameter");
        assertTrue(output.contains("boolField"), "Should generate boolField parameter");
        assertTrue(output.contains("Int.toText"), "Should convert int to text");
        assertTrue(output.contains("Boolean.toText"), "Should convert boolean to text");
    }
    
    @Test
    void testListSerialization() {
        // Create a list field
        StringShape stringShape = StringShape.builder()
                .id("test.api#String")
                .build();
        
        ListShape listShape = ListShape.builder()
                .id("test.api#StringList")
                .member(stringShape.getId())
                .build();
        
        MemberShape listMember = MemberShape.builder()
                .id("test.api#TestInput$items")
                .target(listShape.getId())
                .addTrait(new RequiredTrait())
                .build();
        
        StructureShape input = StructureShape.builder()
                .id("test.api#TestInput")
                .addMember(listMember)
                .build();
        
        Model model = Model.builder()
                .addShapes(stringShape, listShape, listMember, input)
                .build();
        
        // Create minimal context
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2012-11-05")
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#TestOperation")
                .input(input.getId())
                .build();
        
        // Generate serializer
        generator.generateRequestSerializer(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify list serialization with indexed notation
        assertTrue(output.contains("List.indexed"), "Should use List.indexed for list serialization");
        assertTrue(output.contains("Int.toText (idx + 1)"), "Should use 1-based indexing");
        assertTrue(output.contains("items."), "Should prefix list items with field name");
    }
    
    @Test
    void testMapSerialization() {
        // Create a map field
        StringShape keyShape = StringShape.builder()
                .id("test.api#String")
                .build();
        
        StringShape valueShape = StringShape.builder()
                .id("test.api#String")
                .build();
        
        MapShape mapShape = MapShape.builder()
                .id("test.api#StringMap")
                .key(keyShape.getId())
                .value(valueShape.getId())
                .build();
        
        MemberShape mapMember = MemberShape.builder()
                .id("test.api#TestInput$attributes")
                .target(mapShape.getId())
                .addTrait(new RequiredTrait())
                .build();
        
        StructureShape input = StructureShape.builder()
                .id("test.api#TestInput")
                .addMember(mapMember)
                .build();
        
        Model model = Model.builder()
                .addShapes(keyShape, valueShape, mapShape, mapMember, input)
                .build();
        
        // Create minimal context
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2012-11-05")
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#TestOperation")
                .input(input.getId())
                .build();
        
        // Generate serializer
        generator.generateRequestSerializer(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify map serialization with Key/Value notation
        assertTrue(output.contains("Map.toList"), "Should convert map to list");
        assertTrue(output.contains("List.indexed"), "Should use indexed for map entries");
        assertTrue(output.contains(".Key"), "Should generate .Key parameters");
        assertTrue(output.contains(".Value"), "Should generate .Value parameters");
        assertTrue(output.contains("List.flatMap"), "Should use flatMap for map entries");
    }
    
    @Test
    void testNestedStructureSerialization() {
        // Create nested structure
        StringShape stringShape = StringShape.builder()
                .id("test.api#String")
                .build();
        
        MemberShape nestedField = MemberShape.builder()
                .id("test.api#NestedStructure$field")
                .target(stringShape.getId())
                .addTrait(new RequiredTrait())
                .build();
        
        StructureShape nestedStruct = StructureShape.builder()
                .id("test.api#NestedStructure")
                .addMember(nestedField)
                .build();
        
        MemberShape structMember = MemberShape.builder()
                .id("test.api#TestInput$nested")
                .target(nestedStruct.getId())
                .addTrait(new RequiredTrait())
                .build();
        
        StructureShape input = StructureShape.builder()
                .id("test.api#TestInput")
                .addMember(structMember)
                .build();
        
        Model model = Model.builder()
                .addShapes(stringShape, nestedField, nestedStruct, structMember, input)
                .build();
        
        // Create minimal context
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2012-11-05")
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#TestOperation")
                .input(input.getId())
                .build();
        
        // Generate serializer
        generator.generateRequestSerializer(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify nested structure serialization with dot notation
        assertTrue(output.contains("nested."), "Should use dot notation for nested fields");
    }
    
    // =============================================================================
    // Step 2.7.3: Test Action and Version
    // =============================================================================
    
    @Test
    void testActionParameterAdded() {
        // Create minimal operation
        StructureShape input = StructureShape.builder()
                .id("test.api#TestInput")
                .build();
        
        StructureShape outputStructure = StructureShape.builder()
                .id("test.api#TestOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#SendMessage")
                .input(input.getId())
                .output(outputStructure.getId())
                .build();
        
        Model model = Model.builder()
                .addShapes(input, outputStructure, operation)
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2012-11-05")
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        // Generate operation
        generator.generateOperation(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify Action parameter is added
        assertTrue(output.contains("\"Action\""), "Should include Action parameter");
        assertTrue(output.contains("\"SendMessage\""), "Action should be operation name");
    }
    
    @Test
    void testVersionParameterAdded() {
        // Create minimal operation
        StructureShape input = StructureShape.builder()
                .id("test.api#TestInput")
                .build();
        
        StructureShape outputStructure = StructureShape.builder()
                .id("test.api#TestOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#SendMessage")
                .input(input.getId())
                .output(outputStructure.getId())
                .build();
        
        Model model = Model.builder()
                .addShapes(input, outputStructure, operation)
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2012-11-05")
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        // Generate operation
        generator.generateOperation(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify Version parameter is added
        assertTrue(output.contains("\"Version\""), "Should include Version parameter");
        assertTrue(output.contains("\"2012-11-05\""), "Version should be from service");
    }
    
    // =============================================================================
    // Step 2.7.4: Test Form Encoding
    // =============================================================================
    
    @Test
    void testFormEncodingCallGenerated() {
        // Create minimal operation
        StructureShape input = StructureShape.builder()
                .id("test.api#TestInput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#TestOperation")
                .input(input.getId())
                .build();
        
        Model model = Model.builder()
                .addShapes(input, operation)
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2012-11-05")
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        // Generate operation
        generator.generateOperation(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify form encoding function is called
        assertTrue(output.contains("aws.query.buildFormEncodedBody"), "Should call form encoding function");
        assertTrue(output.contains("bodyText ="), "Should assign to bodyText variable");
        assertTrue(output.contains("Text.toUtf8"), "Should convert to bytes");
    }
    
    @Test
    void testContentTypeHeader() {
        // Create minimal operation
        StructureShape input = StructureShape.builder()
                .id("test.api#TestInput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#TestOperation")
                .input(input.getId())
                .build();
        
        Model model = Model.builder()
                .addShapes(input, operation)
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2012-11-05")
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        // Generate operation
        generator.generateOperation(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify Content-Type header is set
        assertTrue(output.contains("Content-Type"), "Should set Content-Type header");
        assertTrue(output.contains("application/x-www-form-urlencoded"), "Should use form-urlencoded content type");
    }
    
    // =============================================================================
    // Step 2.7.5: Test XML Response Parsing
    // =============================================================================
    
    @Test
    void testResponseWrapperNavigation() {
        // Create output structure
        StringShape stringShape = StringShape.builder()
                .id("test.api#String")
                .build();
        
        MemberShape resultField = MemberShape.builder()
                .id("test.api#TestOutput$result")
                .target(stringShape.getId())
                .addTrait(new RequiredTrait())
                .build();
        
        StructureShape outputStructure = StructureShape.builder()
                .id("test.api#TestOutput")
                .addMember(resultField)
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#TestOperation")
                .output(outputStructure.getId())
                .build();
        
        Model model = Model.builder()
                .addShapes(stringShape, resultField, outputStructure, operation)
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2012-11-05")
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        // Generate response deserializer
        generator.generateResponseDeserializer(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify AWS Query response wrapper navigation
        assertTrue(output.contains("TestOperationResponse"), "Should extract OperationNameResponse");
        assertTrue(output.contains("TestOperationResult"), "Should extract OperationNameResult");
        assertTrue(output.contains("aws.xml.extractElement"), "Should use XML extraction");
    }
    
    @Test
    void testFieldExtraction() {
        // Create output with multiple fields
        StringShape stringShape = StringShape.builder()
                .id("test.api#String")
                .build();
        
        IntegerShape intShape = IntegerShape.builder()
                .id("test.api#Integer")
                .build();
        
        MemberShape stringField = MemberShape.builder()
                .id("test.api#TestOutput$stringField")
                .target(stringShape.getId())
                .addTrait(new RequiredTrait())
                .build();
        
        MemberShape intField = MemberShape.builder()
                .id("test.api#TestOutput$intField")
                .target(intShape.getId())
                .build();
        
        StructureShape outputStructure = StructureShape.builder()
                .id("test.api#TestOutput")
                .addMember(stringField)
                .addMember(intField)
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.api#TestOperation")
                .output(outputStructure.getId())
                .build();
        
        Model model = Model.builder()
                .addShapes(stringShape, intShape, stringField, intField, outputStructure, operation)
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2012-11-05")
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        // Generate response deserializer
        generator.generateResponseDeserializer(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify field extraction
        assertTrue(output.contains("stringFieldVal"), "Should extract string field");
        assertTrue(output.contains("intFieldVal"), "Should extract int field");
        assertTrue(output.contains("aws.xml.extractElement"), "Should use element extractor for required string");
        assertTrue(output.contains("aws.xml.extractIntOpt"), "Should use int extractor for optional int");
    }
    
    // =============================================================================
    // Step 2.7.6: Test Error Parsing
    // =============================================================================
    
    @Test
    void testErrorCodeExtraction() {
        // Create minimal operation
        OperationShape operation = OperationShape.builder()
                .id("test.api#TestOperation")
                .build();
        
        Model model = Model.builder()
                .addShapes(operation)
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2012-11-05")
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        // Generate error parser
        generator.generateErrorParser(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify error code extraction
        assertTrue(output.contains("ErrorResponse"), "Should navigate to ErrorResponse element");
        assertTrue(output.contains("\"Error\""), "Should navigate to Error element");
        assertTrue(output.contains("\"Code\""), "Should extract Code element");
        assertTrue(output.contains("code ="), "Should assign code variable");
    }
    
    @Test
    void testErrorMessageExtraction() {
        // Create minimal operation
        OperationShape operation = OperationShape.builder()
                .id("test.api#TestOperation")
                .build();
        
        Model model = Model.builder()
                .addShapes(operation)
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.api#TestService")
                .version("2012-11-05")
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        // Generate error parser
        generator.generateErrorParser(operation, writer, context);
        
        String output = writer.toString();
        
        // Verify error message extraction
        assertTrue(output.contains("\"Message\""), "Should extract Message element");
        assertTrue(output.contains("message ="), "Should assign message variable");
        assertTrue(output.contains("fromCodeAndMessage"), "Should map to service error type");
    }
    
    // =============================================================================
    // Helper Methods
    // =============================================================================
    
    private UnisonContext createTestContext(Model model, ServiceShape service) {
        UnisonSettings settings = UnisonSettings.builder()
                .service(service.getId())
                .namespace("test.api")
                .build();
        
        SymbolProvider symbolProvider = new UnisonSymbolProvider(model, settings);
        FileManifest fileManifest = FileManifest.create(Paths.get(System.getProperty("java.io.tmpdir")));
        
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
