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
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.RequiredTrait;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AwsQueryProtocolGenerator.
 * 
 * <p>Tests the code generation for AWS Query protocol operations,
 * including request serialization, response parsing, and error handling.
 */
public class AwsQueryProtocolGeneratorTest {

    private AwsQueryProtocolGenerator generator;
    private Model model;
    private ServiceShape service;
    private UnisonContext context;

    @BeforeEach
    public void setUp() {
        generator = new AwsQueryProtocolGenerator();
        
        // Build a minimal test model with SQS-like structure
        model = Model.assembler()
                .addImport(getClass().getResource("/test-models/aws-query-test.smithy"))
                .assemble()
                .unwrap();
        
        service = model.expectShape(ShapeId.from("example.sqs#SQS"), ServiceShape.class);
        context = createTestContext(model, service);
    }
    
    private UnisonContext createTestContext(Model model, ServiceShape service) {
        UnisonSettings settings = UnisonSettings.builder()
                .service(service.getId())
                .namespace("aws.sqs")
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

    @Test
    public void testProtocolId() {
        assertEquals(AwsQueryProtocolGenerator.AWS_QUERY, generator.getProtocol());
    }

    @Test
    public void testProtocolName() {
        assertEquals("awsQuery", generator.getName());
    }

    @Test
    public void testDefaultMethod() {
        assertEquals("POST", generator.getDefaultMethod());
    }

    @Test
    public void testDefaultUri() {
        assertEquals("/", generator.getDefaultUri());
    }

    @Test
    public void testContentType() {
        assertEquals("application/x-www-form-urlencoded", generator.getContentType(service));
    }

    @Test
    public void testOperationGeneration() {
        OperationShape operation = model.expectShape(
                ShapeId.from("example.sqs#SendMessage"), OperationShape.class);
        
        UnisonWriter writer = new UnisonWriter("aws.sqs");
        generator.generateOperation(operation, writer, context);
        
        String code = writer.toString();
        
        // Verify basic structure
        assertTrue(code.contains("aws.sqs.sendMessage"), "Should generate operation function");
        assertTrue(code.contains("uri = \"/\""), "Should use root URI");
        
        // Verify AWS Query specific elements
        assertTrue(code.contains("Action"), "Should include Action parameter");
        assertTrue(code.contains("Version"), "Should include Version parameter");
        assertTrue(code.contains("buildFormEncodedBody"), "Should build form-encoded body");
        assertTrue(code.contains("Content-Type"), "Should set Content-Type header");
        
        // Verify AWSEnv-based signing (replaced direct aws.sigv4 calls)
        assertTrue(code.contains("AWSEnv.sign"), "Should use AWSEnv.sign for signing");
        assertTrue(code.contains("AWSEnv.region"), "Should use AWSEnv.region for region");
        
        // Verify HTTP execution
        assertTrue(code.contains("Http.Request.post"), "Should make HTTP POST request");
        assertTrue(code.contains("executeRequest"), "Should execute request");
    }

    @Test
    public void testRequestSerialization() {
        OperationShape operation = model.expectShape(
                ShapeId.from("example.sqs#SendMessage"), OperationShape.class);
        
        UnisonWriter writer = new UnisonWriter("aws.sqs");
        generator.generateRequestSerializer(operation, writer, context);
        
        String code = writer.toString();
        
        // Verify parameter serialization
        assertTrue(code.contains("sendMessageRequestParams"), "Should generate request params function");
        assertTrue(code.contains("[(Text, Text)]"), "Should return list of parameters");
        
        // Verify field handling
        assertTrue(code.contains("QueueUrl") || code.contains("queueUrl"), 
                "Should serialize QueueUrl field");
        assertTrue(code.contains("MessageBody") || code.contains("messageBody"), 
                "Should serialize MessageBody field");
    }

    @Test
    public void testScalarFieldSerialization() {
        OperationShape operation = model.expectShape(
                ShapeId.from("example.sqs#SendMessage"), OperationShape.class);
        
        UnisonWriter writer = new UnisonWriter("aws.sqs");
        generator.generateRequestSerializer(operation, writer, context);
        
        String code = writer.toString();
        
        // Verify scalar field handling with Optional
        assertTrue(code.contains("match") || code.contains("Some") || code.contains("None"), 
                "Should handle optional fields");
    }

    @Test
    public void testListSerialization() {
        OperationShape operation = model.expectShape(
                ShapeId.from("example.sqs#SendMessageBatch"), OperationShape.class);
        
        UnisonWriter writer = new UnisonWriter("aws.sqs");
        generator.generateRequestSerializer(operation, writer, context);
        
        String code = writer.toString();
        
        // Verify list serialization with indexing
        assertTrue(code.contains("List.indexed"), "Should use indexed list mapping");
        assertTrue(code.contains("idx + 1"), "Should use 1-based indexing for AWS Query");
    }

    @Test
    public void testResponseParsing() {
        OperationShape operation = model.expectShape(
                ShapeId.from("example.sqs#SendMessage"), OperationShape.class);
        
        UnisonWriter writer = new UnisonWriter("aws.sqs");
        generator.generateResponseDeserializer(operation, writer, context);
        
        String code = writer.toString();
        
        // Verify AWS Query response structure navigation (Soup-based)
        assertTrue(code.contains("SendMessageResponse"), "Should look for operation response wrapper");
        assertTrue(code.contains("SendMessageResult"), "Should extract result element");
        assertTrue(code.contains("aws.xml.parseResponse"), "Should use bridge entry point for response parsing");
        assertTrue(code.contains("aws.xml.findAndDrill"), "Should use bridge to navigate response wrapper");
        assertTrue(code.contains("resultSoup"), "Should use resultSoup for field extraction");
        
        // Verify field extraction
        assertTrue(code.contains("MessageId"), "Should extract MessageId field");
        
        // No Soup.toXML / extractAllBlocks round-trips in generated response parsers
        assertFalse(code.contains("Soup.toXML"), "Should not emit Soup.toXML in response deserializer");
        assertFalse(code.contains("extractAllBlocks"), "Should not emit extractAllBlocks in response deserializer");
        
        assertFalse(code.contains("aws.xml.parseNested \""),
                "Response parser should use parseNestedSoup, not text-based parseNested");
        assertFalse(code.contains("aws.xml.parseList \""),
                "Response parser should use parseListSoup, not text-based parseList");
    }

    @Test
    public void testBatchResponseUsesSoupNativeListParsing() {
        OperationShape operation = model.expectShape(
                ShapeId.from("example.sqs#SendMessageBatch"), OperationShape.class);
        
        UnisonWriter writer = new UnisonWriter("aws.sqs");
        generator.generateResponseDeserializer(operation, writer, context);
        
        String code = writer.toString();
        
        assertTrue(code.contains("aws.xml.parseListSoup"),
                "Batch response should use parseListSoup for list-of-structure fields. Got: " + code);
        // Inline *_parseElement helpers use Soup ->{Exception} and do not always reference *FromSoup names
        assertTrue(code.contains("Soup ->{Exception}") || code.contains("FromSoup"),
                "Should use Soup-native element parsing (inline helper or *FromSoup). Got: " + code);
        assertFalse(code.contains("aws.xml.parseList \""),
                "Should not use text-based parseList in response deserializer");
    }

    @Test
    public void testXmlFieldExtraction() {
        OperationShape operation = model.expectShape(
                ShapeId.from("example.sqs#ReceiveMessage"), OperationShape.class);
        
        UnisonWriter writer = new UnisonWriter("aws.sqs");
        generator.generateResponseDeserializer(operation, writer, context);
        
        String code = writer.toString();
        
        // Verify different extraction methods for different types (Soup-based)
        assertTrue(code.contains("aws.xml.findText") || code.contains("aws.xml.findInt") || code.contains("aws.xml.findBool"), 
                "Should use appropriate Soup-based extractors for field types");
    }

    @Test
    public void testErrorParsing() {
        OperationShape operation = model.expectShape(
                ShapeId.from("example.sqs#SendMessage"), OperationShape.class);
        
        UnisonWriter writer = new UnisonWriter("aws.sqs");
        generator.generateErrorParser(operation, writer, context);
        
        String code = writer.toString();
        
        // Verify AWS Query error structure (Soup-based)
        assertTrue(code.contains("ErrorResponse"), "Should parse ErrorResponse wrapper");
        assertTrue(code.contains("Error"), "Should extract Error element");
        assertTrue(code.contains("aws.xml.parseResponse"), "Should use bridge entry point for error parsing");
        assertTrue(code.contains("aws.xml.findAndDrill"), "Should use bridge to navigate error wrapper");
        assertTrue(code.contains("aws.xml.findText"), "Should use Soup-based text extraction");
        assertTrue(code.contains("Code"), "Should extract error code");
        assertTrue(code.contains("Message"), "Should extract error message");
        
        // Verify error mapping
        assertTrue(code.contains("fromCodeAndMessage"), "Should map to service error type");
    }

    @Test
    public void testServiceVersionExtraction() {
        // Service in test model should have version 2012-11-05
        OperationShape operation = model.expectShape(
                ShapeId.from("example.sqs#SendMessage"), OperationShape.class);
        
        UnisonWriter writer = new UnisonWriter("aws.sqs");
        generator.generateOperation(operation, writer, context);
        
        String code = writer.toString();
        
        // Verify version is included
        assertTrue(code.contains("2012-11-05") || code.contains("2010-05-15"), 
                "Should include API version");
    }

    @Test
    public void testOptionalFieldHandling() {
        OperationShape operation = model.expectShape(
                ShapeId.from("example.sqs#SendMessage"), OperationShape.class);
        
        UnisonWriter writer = new UnisonWriter("aws.sqs");
        generator.generateRequestSerializer(operation, writer, context);
        
        String code = writer.toString();
        
        // Verify optional field handling
        assertTrue(code.contains("Optional") || code.contains("None") || code.contains("Some"), 
                "Should handle optional fields");
        assertTrue(code.contains("match"), "Should pattern match on Optional");
    }

    @Test
    public void testFormEncodedBodyConstruction() {
        OperationShape operation = model.expectShape(
                ShapeId.from("example.sqs#SendMessage"), OperationShape.class);
        
        UnisonWriter writer = new UnisonWriter("aws.sqs");
        generator.generateOperation(operation, writer, context);
        
        String code = writer.toString();
        
        // Verify form encoding
        assertTrue(code.contains("buildFormEncodedBody"), "Should build form-encoded body");
        assertTrue(code.contains("Text.toUtf8"), "Should convert to UTF-8 bytes");
        assertTrue(code.contains("allParams"), "Should combine all parameters");
    }

    @Test
    public void testSigningServiceName() {
        OperationShape operation = model.expectShape(
                ShapeId.from("example.sqs#SendMessage"), OperationShape.class);
        
        UnisonWriter writer = new UnisonWriter("aws.sqs");
        generator.generateOperation(operation, writer, context);
        
        String code = writer.toString();
        
        // Verify signing service name is lowercase (used in AWSEnv.sign call)
        assertTrue(code.contains("AWSEnv.sign \"sqs\""), 
                "Should use lowercase service name for signing");
    }

    @Test
    public void testOptionalNestedFieldInFlattenedStruct() {
        // SpotOptions has one required (spotInstanceType: Integer) and one optional (maxPrice: String).
        // When serialized as a flattened struct, the optional field must produce an opt_* let-binding
        // with a match expression instead of a -- TODO comment.
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        IntegerShape intShape = IntegerShape.builder()
                .id("smithy.api#Integer")
                .build();

        StructureShape spotOptions = StructureShape.builder()
                .id("test.ec2#SpotOptions")
                .addMember(MemberShape.builder()
                        .id("test.ec2#SpotOptions$spotInstanceType")
                        .target("smithy.api#Integer")
                        .addTrait(new RequiredTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.ec2#SpotOptions$maxPrice")
                        .target("smithy.api#String")
                        .build())
                .build();

        StructureShape inputShape = StructureShape.builder()
                .id("test.ec2#RunInput")
                .addMember(MemberShape.builder()
                        .id("test.ec2#RunInput$spotOptions")
                        .target("test.ec2#SpotOptions")
                        .build())
                .build();

        StructureShape outputShape = StructureShape.builder()
                .id("test.ec2#RunOutput")
                .build();

        OperationShape operation = OperationShape.builder()
                .id("test.ec2#RunInstances")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .build();

        ServiceShape testService = ServiceShape.builder()
                .id("test.ec2#EC2")
                .version("2016-11-15")
                .addOperation(operation.getId())
                .build();

        Model testModel = Model.builder()
                .addShape(stringShape)
                .addShape(intShape)
                .addShape(spotOptions)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(testService)
                .build();

        UnisonContext testContext = createTestContext(testModel, testService);
        UnisonWriter writer = new UnisonWriter("aws.sqs");
        generator.generateRequestSerializer(operation, writer, testContext);

        String code = writer.toString();

        // Optional field should produce an opt_ let-binding with a match expression
        assertTrue(code.contains("opt_maxPrice"), "Should emit opt_maxPrice binding. Got:\n" + code);
        assertTrue(code.contains("None -> []"), "opt_ binding should have None -> [] arm. Got:\n" + code);
        assertTrue(code.contains("Some v ->"), "opt_ binding should have Some v -> arm. Got:\n" + code);
        // The result expression must concatenate required list and optional list
        assertTrue(code.contains("List.++"), "Should concatenate required and optional lists with List.++. Got:\n" + code);
        // No TODO stubs
        assertFalse(code.contains("-- TODO: Handle optional nested field"),
                "Should not emit TODO stub for optional nested field. Got:\n" + code);
    }
}
