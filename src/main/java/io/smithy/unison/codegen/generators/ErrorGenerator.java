package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Generates error type definitions.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public final class ErrorGenerator {
    
    private final UnisonContext context;
    private final StructureShape errorShape;
    
    public ErrorGenerator(UnisonContext context, StructureShape errorShape) {
        this.context = context;
        this.errorShape = errorShape;
    }
    
    /**
     * Generates error type definition.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    public void generate(UnisonWriter writer) {
        // TODO: Implement error generation
        writer.writeComment("Error: " + errorShape.getId());
        writer.writeComment("NOT IMPLEMENTED: Error generation is stubbed");
    }
}
