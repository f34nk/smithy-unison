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
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.pattern.UriPattern;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RestXmlProtocolGenerator.
 */
public class RestXmlProtocolGeneratorTest {
    
    private RestXmlProtocolGenerator generator;
    private UnisonWriter writer;
    
    @BeforeEach
    void setUp() {
        generator = new RestXmlProtocolGenerator();
        writer = new UnisonWriter("test.s3");
    }
    
    @Test
    void testGetProtocol() {
        assertEquals(ShapeId.from("aws.protocols#restXml"), generator.getProtocol());
    }
    
    @Test
    void testGetName() {
        assertEquals("restXml", generator.getName());
    }
    
    @Test
    void testGetContentType() {
        ServiceShape service = ServiceShape.builder()
                .id("com.example#TestService")
                .version("2024-01-01")
                .build();
        
        assertEquals("application/xml", generator.getContentType(service));
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
    
    @Test
    void testGenerateOperationWithSimpleGetOperation() {
        // Build model programmatically
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        BlobShape blobShape = BlobShape.builder()
                .id("smithy.api#Blob")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("com.example#GetObjectInput")
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$Bucket")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$Key")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$RequestPayer")
                        .target("smithy.api#String")
                        .addTrait(new HttpHeaderTrait("x-amz-request-payer"))
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("com.example#GetObjectOutput")
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectOutput$Body")
                        .target("smithy.api#Blob")
                        .addTrait(new HttpPayloadTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectOutput$ContentType")
                        .target("smithy.api#String")
                        .addTrait(new HttpHeaderTrait("Content-Type"))
                        .build())
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("com.example#GetObject")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/{Bucket}/{Key}"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("com.example#S3Service")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(blobShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateOperation(operation, writer, context);
        
        String output = writer.toString();
        
        // Check that the function signature is generated
        assertTrue(output.contains("getObject : Config -> GetObjectInput -> '{IO, Exception, Http} GetObjectOutput"),
                "Should generate correct function signature. Got: " + output);
        
        // Check that HTTP method is set
        assertTrue(output.contains("method = \"GET\""),
                "Should set HTTP method. Got: " + output);
        
        // Check that headers are generated
        assertTrue(output.contains("headers"),
                "Should generate headers code. Got: " + output);
        
        // Check that response handling is generated
        assertTrue(output.contains("handleHttpResponse response"),
                "Should generate error handling. Got: " + output);
    }
    
    @Test
    void testGenerateOperationWithQueryParameters() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        IntegerShape intShape = IntegerShape.builder()
                .id("smithy.api#Integer")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("com.example#ListObjectsInput")
                .addMember(MemberShape.builder()
                        .id("com.example#ListObjectsInput$Bucket")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#ListObjectsInput$Prefix")
                        .target("smithy.api#String")
                        .addTrait(new HttpQueryTrait("prefix"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#ListObjectsInput$MaxKeys")
                        .target("smithy.api#Integer")
                        .addTrait(new HttpQueryTrait("max-keys"))
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("com.example#ListObjectsOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("com.example#ListObjects")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/{Bucket}"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("com.example#S3Service")
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
        
        // Check that query parameters are built
        assertTrue(output.contains("queryParams") || output.contains("queryString"),
                "Should generate query parameter code. Got: " + output);
        
        // Check for query parameter names
        assertTrue(output.contains("prefix"),
                "Should include prefix query parameter. Got: " + output);
        assertTrue(output.contains("max-keys"),
                "Should include max-keys query parameter. Got: " + output);
    }
    
    @Test
    void testGenerateRequestSerializerWithEmptyBody() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        // Input with only @httpLabel members (no body)
        StructureShape inputShape = StructureShape.builder()
                .id("com.example#GetBucketInput")
                .addMember(MemberShape.builder()
                        .id("com.example#GetBucketInput$Bucket")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .addTrait(new HttpLabelTrait())
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("com.example#GetBucketOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("com.example#GetBucket")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/{Bucket}"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("com.example#S3Service")
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
        
        generator.generateRequestSerializer(operation, writer, context);
        
        String output = writer.toString();
        
        // Should generate empty body
        assertTrue(output.contains("empty") || output.contains("No body"),
                "Should generate empty body code. Got: " + output);
    }
    
    @Test
    void testGenerateResponseDeserializerWithPayload() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        BlobShape blobShape = BlobShape.builder()
                .id("smithy.api#Blob")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("com.example#GetObjectInput")
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$Bucket")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .addTrait(new HttpLabelTrait())
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("com.example#GetObjectOutput")
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectOutput$Body")
                        .target("smithy.api#Blob")
                        .addTrait(new HttpPayloadTrait())
                        .build())
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("com.example#GetObject")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/{Bucket}"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("com.example#S3Service")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(blobShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateResponseDeserializer(operation, writer, context);
        
        String output = writer.toString();
        
        // Should extract payload member
        assertTrue(output.contains("body") || output.contains("Body") || output.contains("Http.Response"),
                "Should extract payload from response. Got: " + output);
    }
    
    @Test
    void testGenerateErrorParser() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("com.example#GetObjectInput")
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$Bucket")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .addTrait(new HttpLabelTrait())
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("com.example#GetObjectOutput")
                .build();
        
        StructureShape errorShape = StructureShape.builder()
                .id("com.example#NoSuchKey")
                .addTrait(new ErrorTrait("client"))
                .addMember(MemberShape.builder()
                        .id("com.example#NoSuchKey$message")
                        .target("smithy.api#String")
                        .build())
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("com.example#GetObject")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .addError(errorShape.getId())
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/{Bucket}"))
                        .code(200)
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("com.example#S3Service")
                .version("2024-01-01")
                .addOperation(operation.getId())
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(inputShape)
                .addShape(outputShape)
                .addShape(errorShape)
                .addShape(operation)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateErrorParser(operation, writer, context);
        
        String output = writer.toString();
        
        // Should generate error parsing function
        assertTrue(output.contains("parseError"),
                "Should generate error parsing function. Got: " + output);
        
        // Should reference XML error elements
        assertTrue(output.contains("Code") || output.contains("Message"),
                "Should reference XML error structure. Got: " + output);
        
        // Should reference service error type
        assertTrue(output.contains("S3ServiceError"),
                "Should reference service error type. Got: " + output);
    }
    
    @Test
    void testAppliesToServiceWithRestXml() {
        // Service with REST-XML protocol trait
        ServiceShape service = ServiceShape.builder()
                .id("com.example#S3Service")
                .version("2024-01-01")
                .addTrait(software.amazon.smithy.aws.traits.protocols.RestXmlTrait.builder().build())
                .build();
        
        assertTrue(generator.appliesTo(service),
                "Should apply to REST-XML service");
    }
    
    @Test
    void testAppliesToServiceWithoutRestXml() {
        // Service without protocol trait
        ServiceShape service = ServiceShape.builder()
                .id("com.example#OtherService")
                .version("2024-01-01")
                .build();
        
        assertFalse(generator.appliesTo(service),
                "Should not apply to service without REST-XML protocol");
    }
    
    /**
     * Creates a test UnisonContext with minimal configuration.
     */
    private UnisonContext createTestContext(Model model, ServiceShape service) {
        UnisonSettings settings = UnisonSettings.builder()
                .service(service.getId())
                .namespace("com.example")
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
