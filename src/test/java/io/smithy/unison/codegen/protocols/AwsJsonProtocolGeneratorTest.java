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
import software.amazon.smithy.model.traits.RequiredTrait;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AwsJsonProtocolGenerator.
 */
public class AwsJsonProtocolGeneratorTest {
    
    private AwsJsonProtocolGenerator generator;
    private UnisonWriter writer;
    
    @BeforeEach
    void setUp() {
        generator = new AwsJsonProtocolGenerator();
        writer = new UnisonWriter("test.dynamodb");
    }
    
    @Test
    void testStructureSerializerHasNamespacePrefix() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape structure = StructureShape.builder()
                .id("test.namespace#TestStructure")
                .addMember(MemberShape.builder()
                        .id("test.namespace#TestStructure$Name")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.namespace#TestService")
                .version("2024-01-01")
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(structure)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateStructureSerializer(structure, writer, context);
        
        String output = writer.toString();
        
        assertTrue(output.contains("test.namespace.testStructureToJson"),
                "Structure serializer should have namespace prefix. Got: " + output);
    }
    
    @Test
    void testStructureDeserializerHasNamespacePrefix() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape structure = StructureShape.builder()
                .id("test.namespace#TestStructure")
                .addMember(MemberShape.builder()
                        .id("test.namespace#TestStructure$Name")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .build())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.namespace#TestService")
                .version("2024-01-01")
                .build();
        
        Model model = Model.builder()
                .addShape(stringShape)
                .addShape(structure)
                .addShape(service)
                .build();
        
        UnisonContext context = createTestContext(model, service);
        
        generator.generateStructureDeserializer(structure, writer, context);
        
        String output = writer.toString();
        
        assertTrue(output.contains("test.namespace.testStructureFromJson"),
                "Structure deserializer should have namespace prefix. Got: " + output);
    }
    
    @Test
    void testRequestBodyBuilderHasNamespacePrefix() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("test.namespace#TestOperationInput")
                .addMember(MemberShape.builder()
                        .id("test.namespace#TestOperationInput$Name")
                        .target("smithy.api#String")
                        .addTrait(new RequiredTrait())
                        .build())
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.namespace#TestOperationOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.namespace#TestOperation")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.namespace#TestService")
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
        
        assertTrue(output.contains("test.namespace.testOperationRequestBody"),
                "Request body builder should have namespace prefix. Got: " + output);
    }
    
    @Test
    void testResponseParserHasNamespacePrefix() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        IntegerShape intShape = IntegerShape.builder()
                .id("smithy.api#Integer")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("test.namespace#TestOperationInput")
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.namespace#TestOperationOutput")
                .addMember(MemberShape.builder()
                        .id("test.namespace#TestOperationOutput$Result")
                        .target("smithy.api#String")
                        .build())
                .addMember(MemberShape.builder()
                        .id("test.namespace#TestOperationOutput$Count")
                        .target("smithy.api#Integer")
                        .build())
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.namespace#TestOperation")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.namespace#TestService")
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
        
        assertTrue(output.contains("test.namespace.testOperationResponseParser"),
                "Response parser should have namespace prefix. Got: " + output);
    }
    
    @Test
    void testErrorParserHasNamespacePrefix() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape inputShape = StructureShape.builder()
                .id("test.namespace#TestOperationInput")
                .build();
        
        StructureShape outputShape = StructureShape.builder()
                .id("test.namespace#TestOperationOutput")
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("test.namespace#TestOperation")
                .input(inputShape.getId())
                .output(outputShape.getId())
                .build();
        
        ServiceShape service = ServiceShape.builder()
                .id("test.namespace#TestService")
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
        
        assertTrue(output.contains("test.namespace.parseError"),
                "Error parser should have namespace prefix. Got: " + output);
    }
    
    /**
     * Creates a test UnisonContext with minimal configuration.
     */
    private UnisonContext createTestContext(Model model, ServiceShape service) {
        UnisonSettings settings = UnisonSettings.builder()
                .service(service.getId())
                .namespace("test.namespace")
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

