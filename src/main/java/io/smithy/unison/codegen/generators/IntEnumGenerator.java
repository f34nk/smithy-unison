package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Generates integer enum type definitions.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public final class IntEnumGenerator {
    
    private final UnisonContext context;
    private final Shape intEnumShape;
    
    public IntEnumGenerator(UnisonContext context, Shape intEnumShape) {
        this.context = context;
        this.intEnumShape = intEnumShape;
    }
    
    /**
     * Generates int enum type definition.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    public void generate(UnisonWriter writer) {
        // TODO: Implement int enum generation
        writer.writeComment("IntEnum: " + intEnumShape.getId());
        writer.writeComment("NOT IMPLEMENTED: IntEnum generation is stubbed");
    }
}
