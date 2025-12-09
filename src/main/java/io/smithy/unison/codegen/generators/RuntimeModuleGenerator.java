package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonContext;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Generates runtime support modules.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public final class RuntimeModuleGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(RuntimeModuleGenerator.class.getName());
    
    private final UnisonContext context;
    
    public RuntimeModuleGenerator(UnisonContext context) {
        this.context = context;
    }
    
    /**
     * Copies all runtime modules to the output directory.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    public void copyRuntimeModules() throws IOException {
        LOGGER.info("Runtime module generation not yet implemented");
        
        // TODO: Implement runtime module generation
        // Would generate or copy:
        // - aws_sigv4.u (SigV4 signing)
        // - aws_http.u (HTTP request/response helpers)
        // - aws_xml.u (XML encoding/decoding)
        // - aws_json.u (JSON encoding/decoding)
        // - aws_config.u (Configuration types)
        // - aws_retry.u (Retry logic)
    }
    
    /**
     * Generates the SigV4 signing module.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    public void generateSigV4Module() throws IOException {
        // TODO: Implement SigV4 module generation
        LOGGER.fine("SigV4 module generation not yet implemented");
    }
    
    /**
     * Generates the HTTP helper module.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    public void generateHttpModule() throws IOException {
        // TODO: Implement HTTP module generation
        LOGGER.fine("HTTP module generation not yet implemented");
    }
    
    /**
     * Generates the XML helper module.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    public void generateXmlModule() throws IOException {
        // TODO: Implement XML module generation
        LOGGER.fine("XML module generation not yet implemented");
    }
}
