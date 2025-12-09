package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.aws.AwsProtocol;
import io.smithy.unison.codegen.aws.AwsProtocolDetector;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Factory for creating protocol-specific code generators.
 * 
 * <p>Maps AWS protocols detected from service traits to their corresponding
 * generator implementations.
 * 
 * <h2>Supported Protocols</h2>
 * <ul>
 *   <li>REST-XML (aws.protocols#restXml) - S3, CloudFront, Route 53</li>
 *   <li>REST-JSON (aws.protocols#restJson1) - API Gateway, Lambda (planned)</li>
 *   <li>AWS JSON 1.0/1.1 (aws.protocols#awsJson*) - DynamoDB, Kinesis (planned)</li>
 *   <li>AWS Query (aws.protocols#awsQuery) - SQS, SNS (planned)</li>
 *   <li>EC2 Query (aws.protocols#ec2Query) - EC2 (planned)</li>
 * </ul>
 */
public final class ProtocolGeneratorFactory {
    
    private static final Logger LOGGER = Logger.getLogger(ProtocolGeneratorFactory.class.getName());
    
    /**
     * Gets a protocol generator for the given service.
     * 
     * <p>Detects the protocol from service traits and returns the appropriate
     * generator implementation.
     * 
     * @param service The service shape to generate code for
     * @return The protocol generator, or empty if no supported protocol is found
     */
    public static Optional<ProtocolGenerator> getGenerator(ServiceShape service) {
        AwsProtocol protocol = AwsProtocolDetector.detectProtocol(service);
        
        return getGenerator(protocol);
    }
    
    /**
     * Gets a protocol generator for the given protocol type.
     * 
     * @param protocol The AWS protocol type
     * @return The protocol generator, or empty if not supported
     */
    public static Optional<ProtocolGenerator> getGenerator(AwsProtocol protocol) {
        if (protocol == AwsProtocol.REST_XML) {
            LOGGER.fine("Using RestXmlProtocolGenerator");
            return Optional.of(new RestXmlProtocolGenerator());
        } else if (protocol == AwsProtocol.REST_JSON_1) {
            LOGGER.fine("RestJsonProtocolGenerator not yet fully implemented");
            return Optional.of(new RestJsonProtocolGenerator());
        } else if (protocol == AwsProtocol.AWS_JSON_1_0) {
            LOGGER.fine("AwsJsonProtocolGenerator (1.0) not yet fully implemented");
            return Optional.of(new AwsJsonProtocolGenerator(
                    AwsJsonProtocolGenerator.AWS_JSON_1_0, "1.0"));
        } else if (protocol == AwsProtocol.AWS_JSON_1_1) {
            LOGGER.fine("AwsJsonProtocolGenerator (1.1) not yet fully implemented");
            return Optional.of(new AwsJsonProtocolGenerator());
        } else if (protocol == AwsProtocol.AWS_QUERY) {
            LOGGER.fine("AwsQueryProtocolGenerator not yet fully implemented");
            return Optional.of(new AwsQueryProtocolGenerator());
        } else if (protocol == AwsProtocol.EC2_QUERY) {
            LOGGER.fine("Ec2QueryProtocolGenerator not yet fully implemented");
            return Optional.of(new Ec2QueryProtocolGenerator());
        } else {
            LOGGER.warning("No protocol generator available for: " + protocol);
            return Optional.empty();
        }
    }
    
    /**
     * Checks if a protocol is fully implemented (not just stubbed).
     * 
     * @param protocol The protocol to check
     * @return true if the protocol has a full implementation
     */
    public static boolean isFullyImplemented(AwsProtocol protocol) {
        return protocol == AwsProtocol.REST_XML;
    }
    
    private ProtocolGeneratorFactory() {
        // Utility class
    }
}
