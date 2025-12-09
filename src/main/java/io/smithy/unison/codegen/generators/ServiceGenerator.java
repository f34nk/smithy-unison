package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Generates service-level code.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public final class ServiceGenerator {
    
    private final UnisonContext context;
    private final ServiceShape service;
    
    public ServiceGenerator(UnisonContext context, ServiceShape service) {
        this.context = context;
        this.service = service;
    }
    
    /**
     * Generates service code.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    public void generate(UnisonWriter writer) {
        // TODO: Implement service generation
        writer.writeComment("Service: " + service.getId());
        writer.writeComment("NOT IMPLEMENTED: Service generation is stubbed");
    }
}
