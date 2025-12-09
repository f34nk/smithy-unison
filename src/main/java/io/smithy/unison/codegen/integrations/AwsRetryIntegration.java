package io.smithy.unison.codegen.integrations;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonIntegration;

import java.util.logging.Logger;

/**
 * Integration that copies retry logic module.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public class AwsRetryIntegration implements UnisonIntegration {
    
    private static final Logger LOGGER = Logger.getLogger(AwsRetryIntegration.class.getName());
    
    @Override
    public String name() {
        return "AwsRetry";
    }
    
    @Override
    public byte priority() {
        return 16;
    }
    
    @Override
    public void preprocessModel(UnisonContext context) {
        LOGGER.fine("AwsRetryIntegration.preprocessModel called");
        // TODO: Implement retry module copying
        // This would copy aws_retry.u runtime module to output directory
    }
    
    @Override
    public void postprocessGeneration(UnisonContext context) {
        // No post-processing needed
    }
}
