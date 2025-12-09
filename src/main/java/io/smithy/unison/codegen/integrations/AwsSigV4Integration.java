package io.smithy.unison.codegen.integrations;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonIntegration;

import java.util.logging.Logger;

/**
 * Integration that copies AWS SigV4 signing modules.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public class AwsSigV4Integration implements UnisonIntegration {
    
    private static final Logger LOGGER = Logger.getLogger(AwsSigV4Integration.class.getName());
    
    @Override
    public String name() {
        return "AwsSigV4";
    }
    
    @Override
    public byte priority() {
        return 64; // High priority - runs early
    }
    
    @Override
    public void preprocessModel(UnisonContext context) {
        LOGGER.fine("AwsSigV4Integration.preprocessModel called");
        // TODO: Implement SigV4 module copying
        // This would copy aws_sigv4.u runtime module to output directory
    }
    
    @Override
    public void postprocessGeneration(UnisonContext context) {
        // No post-processing needed
    }
}
