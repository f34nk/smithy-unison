package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Generates enum type definitions.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public final class EnumGenerator {
    
    private final UnisonContext context;
    private final Shape enumShape;
    
    public EnumGenerator(UnisonContext context, Shape enumShape) {
        this.context = context;
        this.enumShape = enumShape;
    }
    
    /**
     * Generates enum type definition.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    public void generate(UnisonWriter writer) {
        // TODO: Implement enum generation
        // Unison enums are sum types: type Color = Red | Green | Blue
        writer.writeComment("Enum: " + enumShape.getId());
        writer.writeComment("NOT IMPLEMENTED: Enum generation is stubbed");
    }
}
