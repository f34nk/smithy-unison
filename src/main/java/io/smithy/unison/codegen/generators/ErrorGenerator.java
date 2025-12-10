package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.ErrorTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Generates Unison error types from Smithy structures with @error trait.
 * 
 * <p>Error shapes in Smithy are structure shapes with the @error trait.
 * In Unison, errors are represented as record types with optional fields.
 * 
 * <h2>Example Smithy Error</h2>
 * <pre>
 * {@literal @}error("client")
 * structure NoSuchBucket {
 *     message: String
 * }
 * </pre>
 * 
 * <h2>Generated Unison Code</h2>
 * <pre>
 * {{ The specified bucket does not exist. }}
 * type NoSuchBucket = {
 *   message : Optional Text
 * }
 * </pre>
 * 
 * <p>For aggregated error types (sum types across all service errors),
 * use {@link ServiceErrorGenerator}.
 * 
 * @see UnisonContext
 * @see UnisonWriter
 */
public final class ErrorGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(ErrorGenerator.class.getName());
    
    private final String typeName;
    private final String errorCategory;
    private final Integer httpStatusCode;
    private final String documentation;
    private final List<ErrorField> fields;
    
    /**
     * Represents a field in an error structure.
     */
    public static final class ErrorField {
        private final String name;
        private final String type;
        private final boolean required;
        
        public ErrorField(String name, String type, boolean required) {
            this.name = name;
            this.type = type;
            this.required = required;
        }
        
        /** The field name (camelCase). */
        public String name() { return name; }
        
        /** The Unison type. */
        public String type() { return type; }
        
        /** Whether the field is required. */
        public boolean required() { return required; }
    }
    
    /**
     * Creates an error generator from a StructureShape with @error trait.
     *
     * @param errorShape The structure shape with @error trait
     * @param model The Smithy model
     * @throws IllegalArgumentException if the shape doesn't have ErrorTrait
     */
    public ErrorGenerator(StructureShape errorShape, Model model) {
        Objects.requireNonNull(errorShape, "errorShape is required");
        Objects.requireNonNull(model, "model is required");
        
        if (!errorShape.hasTrait(ErrorTrait.class)) {
            throw new IllegalArgumentException("Shape must have @error trait: " + errorShape.getId());
        }
        
        ErrorTrait errorTrait = errorShape.expectTrait(ErrorTrait.class);
        
        this.typeName = UnisonSymbolProvider.toUnisonTypeName(errorShape.getId().getName());
        this.errorCategory = errorTrait.getValue();
        this.httpStatusCode = errorShape.getTrait(software.amazon.smithy.model.traits.HttpErrorTrait.class)
                .map(t -> t.getCode())
                .orElse(null);
        this.documentation = errorShape.getTrait(DocumentationTrait.class)
                .map(DocumentationTrait::getValue)
                .orElse(null);
        this.fields = extractFields(errorShape, model);
    }
    
    /**
     * Creates an error generator with explicit values (for testing or manual construction).
     *
     * @param typeName The type name
     * @param errorCategory "client" or "server"
     * @param httpStatusCode HTTP status code (optional)
     * @param documentation Documentation string (optional)
     * @param fields The error fields
     */
    public ErrorGenerator(String typeName, String errorCategory, Integer httpStatusCode, 
                         String documentation, List<ErrorField> fields) {
        this.typeName = Objects.requireNonNull(typeName, "typeName is required");
        this.errorCategory = Objects.requireNonNull(errorCategory, "errorCategory is required");
        this.httpStatusCode = httpStatusCode;
        this.documentation = documentation;
        this.fields = fields != null ? fields : new ArrayList<>();
    }
    
    /**
     * Gets the Unison type name for this error.
     *
     * @return The PascalCase type name
     */
    public String getTypeName() {
        return typeName;
    }
    
    /**
     * Gets the error category ("client" or "server").
     *
     * @return The error category
     */
    public String getErrorCategory() {
        return errorCategory;
    }
    
    /**
     * Returns true if this is a client error.
     *
     * @return true for client errors, false for server errors
     */
    public boolean isClientError() {
        return "client".equals(errorCategory);
    }
    
    /**
     * Gets the HTTP status code for this error.
     *
     * @return The HTTP status code, or null if not specified
     */
    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }
    
    /**
     * Gets the error fields.
     *
     * @return List of error fields
     */
    public List<ErrorField> getFields() {
        return fields;
    }
    
    /**
     * Checks if this error has a message field.
     *
     * @return true if the error has a "message" field
     */
    public boolean hasMessageField() {
        return fields.stream().anyMatch(f -> "message".equals(f.name()));
    }
    
    /**
     * Gets the message field if present.
     *
     * @return The message field, or null if not present
     */
    public ErrorField getMessageField() {
        return fields.stream()
                .filter(f -> "message".equals(f.name()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Generates the complete error type definition and toFailure function.
     *
     * @param writer The writer to output code to
     */
    public void generate(UnisonWriter writer) {
        LOGGER.fine("Generating error: " + typeName);
        
        generateTypeDefinition(writer);
        generateToFailureFunction(writer);
    }
    
    /**
     * Generates the error type definition.
     *
     * @param writer The writer to output code to
     */
    public void generateTypeDefinition(UnisonWriter writer) {
        // Write documentation
        StringBuilder docBuilder = new StringBuilder();
        if (documentation != null && !documentation.isEmpty()) {
            // Clean up HTML tags from documentation
            String cleanDoc = documentation
                    .replaceAll("<[^>]+>", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            docBuilder.append(cleanDoc);
        }
        
        // Add error metadata to documentation
        if (docBuilder.length() > 0) {
            docBuilder.append("\n\n");
        }
        docBuilder.append("Error category: ").append(errorCategory);
        if (httpStatusCode != null) {
            docBuilder.append("\nHTTP status: ").append(httpStatusCode);
        }
        
        writer.writeDocComment(docBuilder.toString());
        
        if (fields.isEmpty()) {
            // Empty error - use unit type pattern
            writer.write("type $L = { }", typeName);
            writer.writeBlankLine();
        } else {
            // Build field list
            List<UnisonWriter.TypeField> typeFields = new ArrayList<>();
            for (ErrorField field : fields) {
                String fieldType = field.required() ? field.type() : "Optional " + field.type();
                typeFields.add(new UnisonWriter.TypeField(field.name(), fieldType));
            }
            
            writer.writeRecordType(typeName, typeFields);
        }
    }
    
    /**
     * Generates the toFailure conversion function.
     * 
     * <p>This function converts the error type to IO.Failure for use with
     * the Exception ability in Unison.
     *
     * <h2>Example Output</h2>
     * <pre>
     * NoSuchBucket.toFailure : NoSuchBucket -> IO.Failure
     * NoSuchBucket.toFailure err =
     *   IO.Failure.Failure (typeLink NoSuchBucket) (NoSuchBucket.message err) (Any err)
     * </pre>
     *
     * @param writer The writer to output code to
     */
    public void generateToFailureFunction(UnisonWriter writer) {
        String funcName = typeName + ".toFailure";
        String camelTypeName = UnisonSymbolProvider.toUnisonFunctionName(typeName);
        
        // Write signature
        writer.write("$L : $L -> IO.Failure", funcName, typeName);
        
        // Write function body
        // Determine the message expression
        String messageExpr;
        if (hasMessageField()) {
            ErrorField msgField = getMessageField();
            if (msgField.required()) {
                // Required message field - access directly
                messageExpr = typeName + ".message err";
            } else {
                // Optional message field - use getOrElse
                // Note: Optional.getOrElse takes default first, then optional
                messageExpr = "Optional.getOrElse \"\" (" + typeName + ".message err)";
            }
        } else {
            // No message field - use type name as message
            messageExpr = "\"" + typeName + "\"";
        }
        
        writer.write("$L err =", funcName);
        writer.indent();
        writer.write("IO.Failure.Failure (typeLink $L) ($L) (Any err)", typeName, messageExpr);
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Extracts error fields from the structure shape.
     */
    private List<ErrorField> extractFields(StructureShape shape, Model model) {
        List<ErrorField> result = new ArrayList<>();
        
        for (MemberShape member : shape.getAllMembers().values()) {
            String fieldName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            Shape targetShape = model.expectShape(member.getTarget());
            String fieldType = getUnisonType(targetShape);
            boolean required = member.hasTrait(software.amazon.smithy.model.traits.RequiredTrait.class);
            
            result.add(new ErrorField(fieldName, fieldType, required));
        }
        
        return result;
    }
    
    /**
     * Gets the Unison type for a Smithy shape.
     */
    private String getUnisonType(Shape shape) {
        if (shape.isStringShape()) {
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
        }
        // Default to the shape's type name
        return UnisonSymbolProvider.toUnisonTypeName(shape.getId().getName());
    }
}
