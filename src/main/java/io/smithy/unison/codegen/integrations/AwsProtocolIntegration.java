package io.smithy.unison.codegen.integrations;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonIntegration;

import java.util.logging.Logger;

/**
 * Integration that copies protocol-specific modules.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public class AwsProtocolIntegration implements UnisonIntegration {
    
    private static final Logger LOGGER = Logger.getLogger(AwsProtocolIntegration.class.getName());
    
    @Override
    public String name() {
        return "AwsProtocol";
    }
    
    @Override
    public byte priority() {
        return 32;
    }
    
    @Override
    public void preprocessModel(UnisonContext context) {
        LOGGER.fine("AwsProtocolIntegration.preprocessModel called");
        // TODO: Implement protocol module copying
        // This would copy aws_xml.u, aws_json.u, etc. based on detected protocol
    }
    
    @Override
    public void postprocessGeneration(UnisonContext context) {
        // No post-processing needed
    }
}
