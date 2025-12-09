package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import software.amazon.smithy.model.shapes.UnionShape;

/**
 * Generates union type definitions.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public final class UnionGenerator {
    
    private final UnisonContext context;
    private final UnionShape unionShape;
    
    public UnionGenerator(UnisonContext context, UnionShape unionShape) {
        this.context = context;
        this.unionShape = unionShape;
    }
    
    /**
     * Generates union type definition.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    public void generate(UnisonWriter writer) {
        // TODO: Implement union generation
        // Unison unions are sum types: type Result a b = Ok a | Err b
        writer.writeComment("Union: " + unionShape.getId());
        writer.writeComment("NOT IMPLEMENTED: Union generation is stubbed");
    }
}
