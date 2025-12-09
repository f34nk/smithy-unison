package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.aws.AwsProtocol;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProtocolGeneratorFactory}.
 */
class ProtocolGeneratorFactoryTest {
    
    @Test
    void testGetGeneratorForRestXml() {
        Optional<ProtocolGenerator> generator = ProtocolGeneratorFactory.getGenerator(AwsProtocol.REST_XML);
        
        assertTrue(generator.isPresent(), "Should return a generator for REST-XML");
        assertTrue(generator.get() instanceof RestXmlProtocolGenerator, 
            "Should return RestXmlProtocolGenerator for REST-XML");
    }
    
    @Test
    void testGetGeneratorForRestJson() {
        Optional<ProtocolGenerator> generator = ProtocolGeneratorFactory.getGenerator(AwsProtocol.REST_JSON_1);
        
        assertTrue(generator.isPresent(), "Should return a generator for REST-JSON");
        assertTrue(generator.get() instanceof RestJsonProtocolGenerator,
            "Should return RestJsonProtocolGenerator for REST-JSON");
    }
    
    @Test
    void testGetGeneratorForAwsJson10() {
        Optional<ProtocolGenerator> generator = ProtocolGeneratorFactory.getGenerator(AwsProtocol.AWS_JSON_1_0);
        
        assertTrue(generator.isPresent(), "Should return a generator for AWS JSON 1.0");
        assertTrue(generator.get() instanceof AwsJsonProtocolGenerator,
            "Should return AwsJsonProtocolGenerator for AWS JSON 1.0");
    }
    
    @Test
    void testGetGeneratorForAwsJson11() {
        Optional<ProtocolGenerator> generator = ProtocolGeneratorFactory.getGenerator(AwsProtocol.AWS_JSON_1_1);
        
        assertTrue(generator.isPresent(), "Should return a generator for AWS JSON 1.1");
        assertTrue(generator.get() instanceof AwsJsonProtocolGenerator,
            "Should return AwsJsonProtocolGenerator for AWS JSON 1.1");
    }
    
    @Test
    void testGetGeneratorForAwsQuery() {
        Optional<ProtocolGenerator> generator = ProtocolGeneratorFactory.getGenerator(AwsProtocol.AWS_QUERY);
        
        assertTrue(generator.isPresent(), "Should return a generator for AWS Query");
        assertTrue(generator.get() instanceof AwsQueryProtocolGenerator,
            "Should return AwsQueryProtocolGenerator for AWS Query");
    }
    
    @Test
    void testGetGeneratorForEc2Query() {
        Optional<ProtocolGenerator> generator = ProtocolGeneratorFactory.getGenerator(AwsProtocol.EC2_QUERY);
        
        assertTrue(generator.isPresent(), "Should return a generator for EC2 Query");
        assertTrue(generator.get() instanceof Ec2QueryProtocolGenerator,
            "Should return Ec2QueryProtocolGenerator for EC2 Query");
    }
    
    @Test
    void testGetGeneratorForUnknown() {
        Optional<ProtocolGenerator> generator = ProtocolGeneratorFactory.getGenerator(AwsProtocol.UNKNOWN);
        
        assertFalse(generator.isPresent(), "Should return empty for UNKNOWN protocol");
    }
    
    @Test
    void testIsFullyImplementedRestXml() {
        assertTrue(ProtocolGeneratorFactory.isFullyImplemented(AwsProtocol.REST_XML),
            "REST-XML should be fully implemented");
    }
    
    @Test
    void testIsFullyImplementedOtherProtocols() {
        assertFalse(ProtocolGeneratorFactory.isFullyImplemented(AwsProtocol.REST_JSON_1),
            "REST-JSON should not be fully implemented yet");
        assertFalse(ProtocolGeneratorFactory.isFullyImplemented(AwsProtocol.AWS_JSON_1_0),
            "AWS JSON 1.0 should not be fully implemented yet");
        assertFalse(ProtocolGeneratorFactory.isFullyImplemented(AwsProtocol.AWS_JSON_1_1),
            "AWS JSON 1.1 should not be fully implemented yet");
        assertFalse(ProtocolGeneratorFactory.isFullyImplemented(AwsProtocol.AWS_QUERY),
            "AWS Query should not be fully implemented yet");
        assertFalse(ProtocolGeneratorFactory.isFullyImplemented(AwsProtocol.EC2_QUERY),
            "EC2 Query should not be fully implemented yet");
        assertFalse(ProtocolGeneratorFactory.isFullyImplemented(AwsProtocol.UNKNOWN),
            "UNKNOWN should not be fully implemented");
    }
    
    @Test
    void testGetGeneratorFromServiceWithRestXml() {
        // Build a minimal service with restXml trait
        Model model = Model.assembler()
            .addUnparsedModel("test.smithy", 
                "$version: \"2.0\"\n" +
                "namespace test\n" +
                "use aws.protocols#restXml\n" +
                "@restXml\n" +
                "service TestService {\n" +
                "  version: \"1.0\"\n" +
                "}\n")
            .discoverModels()
            .assemble()
            .unwrap();
        
        ServiceShape service = model.expectShape(
            ShapeId.from("test#TestService"), ServiceShape.class);
        
        Optional<ProtocolGenerator> generator = ProtocolGeneratorFactory.getGenerator(service);
        
        assertTrue(generator.isPresent(), "Should find generator for service with restXml trait");
        assertTrue(generator.get() instanceof RestXmlProtocolGenerator,
            "Should return RestXmlProtocolGenerator");
    }
    
    @Test
    void testGetGeneratorFromServiceWithoutProtocol() {
        // Build a minimal service without any protocol trait
        Model model = Model.assembler()
            .addUnparsedModel("test.smithy",
                "$version: \"2.0\"\n" +
                "namespace test\n" +
                "service TestService {\n" +
                "  version: \"1.0\"\n" +
                "}\n")
            .assemble()
            .unwrap();
        
        ServiceShape service = model.expectShape(
            ShapeId.from("test#TestService"), ServiceShape.class);
        
        Optional<ProtocolGenerator> generator = ProtocolGeneratorFactory.getGenerator(service);
        
        assertFalse(generator.isPresent(), 
            "Should return empty for service without protocol trait");
    }
}
