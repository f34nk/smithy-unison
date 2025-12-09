package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.DocumentationTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Generates Unison sum types from Smithy union shapes.
 * 
 * <p>Union shapes in Unison are represented as sum types with tagged variants.
 * Each union member becomes a variant with its type as payload.
 * 
 * <h2>Example Smithy Union</h2>
 * <pre>
 * union StorageType {
 *     s3: S3Storage,
 *     glacier: GlacierStorage,
 *     efs: EfsStorage
 * }
 * </pre>
 * 
 * <h2>Generated Unison Code</h2>
 * <pre>
 * type StorageType
 *   = StorageType'S3 S3Storage
 *   | StorageType'Glacier GlacierStorage
 *   | StorageType'Efs EfsStorage
 * </pre>
 * 
 * <h2>S3 Union Examples</h2>
 * <pre>
 * type SelectObjectContentEventStream
 *   = SelectObjectContentEventStream'Records RecordsEvent
 *   | SelectObjectContentEventStream'Stats StatsEvent
 *   | SelectObjectContentEventStream'Progress ProgressEvent
 *   | SelectObjectContentEventStream'Cont ContinuationEvent
 *   | SelectObjectContentEventStream'End EndEvent
 * </pre>
 * 
 * @see UnisonContext
 * @see UnisonWriter
 */
public final class UnionGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(UnionGenerator.class.getName());
    
    private final String typeName;
    private final String documentation;
    private final List<UnionVariant> variants;
    
    /**
     * Represents a union variant with its name and payload type.
     */
    public static final class UnionVariant {
        private final String name;
        private final String payloadType;
        
        public UnionVariant(String name, String payloadType) {
            this.name = name;
            this.payloadType = payloadType;
        }
        
        /** The variant name (PascalCase, e.g., "S3"). */
        public String name() { return name; }
        
        /** The payload type (e.g., "S3Storage"), or null for unit variants. */
        public String payloadType() { return payloadType; }
    }
    
    /**
     * Creates a union generator from a Smithy UnionShape.
     *
     * @param union The union shape to generate
     * @param model The Smithy model
     */
    public UnionGenerator(UnionShape union, Model model) {
        Objects.requireNonNull(union, "union is required");
        Objects.requireNonNull(model, "model is required");
        
        this.typeName = UnisonSymbolProvider.toUnisonTypeName(union.getId().getName());
        this.documentation = union.getTrait(DocumentationTrait.class)
                .map(DocumentationTrait::getValue)
                .orElse(null);
        this.variants = extractVariants(union, model);
    }
    
    /**
     * Creates a union generator with explicit values (for testing or manual construction).
     *
     * @param typeName The type name
     * @param variants The union variants
     * @param documentation Optional documentation
     */
    public UnionGenerator(String typeName, List<UnionVariant> variants, String documentation) {
        this.typeName = Objects.requireNonNull(typeName, "typeName is required");
        this.variants = Objects.requireNonNull(variants, "variants is required");
        this.documentation = documentation;
    }
    
    /**
     * Gets the Unison type name for this union.
     *
     * @return The PascalCase type name
     */
    public String getTypeName() {
        return typeName;
    }
    
    /**
     * Gets the union variants.
     *
     * @return List of union variants
     */
    public List<UnionVariant> getVariants() {
        return variants;
    }
    
    /**
     * Gets the full variant name for a member.
     *
     * @param memberName The member name
     * @return The full variant name (TypeName'MemberName)
     */
    public String getVariantName(String memberName) {
        return UnisonSymbolProvider.toUnisonEnumVariant(typeName, memberName);
    }
    
    /**
     * Generates the complete union code: type definition.
     *
     * @param writer The writer to output code to
     */
    public void generate(UnisonWriter writer) {
        LOGGER.fine("Generating union: " + typeName);
        
        generateTypeDefinition(writer);
    }
    
    /**
     * Generates the union type definition.
     *
     * @param writer The writer to output code to
     */
    public void generateTypeDefinition(UnisonWriter writer) {
        // Write documentation if present
        if (documentation != null && !documentation.isEmpty()) {
            writer.writeDocComment(documentation);
        }
        
        // Build variants list for writeUnionType
        List<UnisonWriter.Variant> writerVariants = new ArrayList<>();
        for (UnionVariant variant : variants) {
            String variantName = getVariantName(variant.name());
            writerVariants.add(new UnisonWriter.Variant(variantName, variant.payloadType()));
        }
        
        // Write union type
        writer.writeUnionType(typeName, writerVariants);
    }
    
    /**
     * Extracts union variants from the UnionShape.
     */
    private List<UnionVariant> extractVariants(UnionShape union, Model model) {
        List<UnionVariant> result = new ArrayList<>();
        
        for (MemberShape member : union.getAllMembers().values()) {
            // Member names are typically lowercase in Smithy (e.g., "s3", "glacier")
            // Convert to PascalCase for Unison variant names
            String variantName = toPascalCase(member.getMemberName());
            Shape targetShape = model.expectShape(member.getTarget());
            String payloadType = getUnisonType(targetShape);
            
            result.add(new UnionVariant(variantName, payloadType));
        }
        
        return result;
    }
    
    /**
     * Converts a name to PascalCase.
     * 
     * <p>Examples:
     * <ul>
     *   <li>"s3" → "S3"</li>
     *   <li>"glacier" → "Glacier"</li>
     *   <li>"efsStorage" → "EfsStorage"</li>
     * </ul>
     */
    private String toPascalCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // Capitalize first character
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    
    /**
     * Gets the Unison type for a Smithy shape.
     */
    private String getUnisonType(Shape shape) {
        if (shape.isStructureShape()) {
            return UnisonSymbolProvider.toUnisonTypeName(shape.getId().getName());
        } else if (shape.isStringShape()) {
            return "Text";
        } else if (shape.isIntegerShape() || shape.isLongShape() ||
                   shape.isShortShape() || shape.isByteShape()) {
            return "Int";
        } else if (shape.isFloatShape() || shape.isDoubleShape()) {
            return "Float";
        } else if (shape.isBooleanShape()) {
            return "Boolean";
        } else if (shape.isBlobShape()) {
            return "Bytes";
        } else if (shape.isTimestampShape()) {
            return "Text";  // Timestamps as ISO strings for now
        } else if (shape.isListShape()) {
            return "[a]";  // Generic list for now
        } else if (shape.isMapShape()) {
            return "Map Text a";  // Generic map for now
        } else if (shape.isUnionShape()) {
            return UnisonSymbolProvider.toUnisonTypeName(shape.getId().getName());
        }
        // Default to the shape's type name
        return UnisonSymbolProvider.toUnisonTypeName(shape.getId().getName());
    }
}
