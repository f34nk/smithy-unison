package io.smithy.unison.codegen.aws;

import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.logging.Logger;

/**
 * Detects the AWS protocol used by a service.
 */
public final class AwsProtocolDetector {
    
    private static final Logger LOGGER = Logger.getLogger(AwsProtocolDetector.class.getName());
    
    /**
     * Protocol trait ShapeIds in priority order.
     */
    private static final ShapeId[] PROTOCOL_TRAITS = {
        ShapeId.from("aws.protocols#restJson1"),
        ShapeId.from("aws.protocols#awsJson1_1"),
        ShapeId.from("aws.protocols#awsJson1_0"),
        ShapeId.from("aws.protocols#restXml"),
        ShapeId.from("aws.protocols#awsQuery"),
        ShapeId.from("aws.protocols#ec2Query")
    };
    
    /**
     * Detects the AWS protocol from a service shape.
     */
    public static AwsProtocol detectProtocol(ServiceShape service) {
        for (ShapeId traitId : PROTOCOL_TRAITS) {
            if (service.findTrait(traitId).isPresent()) {
                AwsProtocol protocol = AwsProtocol.fromTraitId(traitId);
                LOGGER.fine("Detected protocol " + protocol + " for service " + service.getId());
                return protocol;
            }
        }
        
        LOGGER.warning("No AWS protocol trait found on service " + service.getId() + ", defaulting to UNKNOWN");
        return AwsProtocol.UNKNOWN;
    }
    
    private AwsProtocolDetector() {
        // Utility class
    }
}
