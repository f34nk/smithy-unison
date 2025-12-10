package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.EnumTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Generates Unison record types from Smithy structure shapes.
 * 
 * <p>This generator converts Smithy structure shapes to Unison record types,
 * handling traits like {@code @required}, {@code @default}, and {@code @documentation}.
 * 
 * <h2>Type Mapping</h2>
 * <ul>
 *   <li>Required fields ({@code @required}) → non-optional type (e.g., {@code Text})</li>
 *   <li>Optional fields → wrapped in {@code Optional} (e.g., {@code Optional Text})</li>
 *   <li>Fields with {@code @default} → non-optional with default value noted</li>
 * </ul>
 * 
 * <h2>Example Output</h2>
 * <pre>
 * {{ Represents an S3 bucket. }}
 * type Bucket = {
 *   name : Optional Text,
 *   creationDate : Optional Text
 * }
 * </pre>
 * 
 * @see UnisonContext
 * @see UnisonWriter
 */
public final class StructureGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(StructureGenerator.class.getName());
    
    private final Model model;
    private final StructureShape structure;
    private final SymbolProvider symbolProvider;
    
    /**
     * Creates a new structure generator.
     *
     * @param structure The structure shape to generate
     * @param context The code generation context
     */
    public StructureGenerator(StructureShape structure, UnisonContext context) {
        this.structure = Objects.requireNonNull(structure, "structure is required");
        Objects.requireNonNull(context, "context is required");
        this.model = context.model();
        this.symbolProvider = context.symbolProvider();
    }
    
    /**
     * Creates a new structure generator with explicit model and symbol provider.
     *
     * @param structure The structure shape to generate
     * @param model The Smithy model
     * @param symbolProvider The symbol provider
     */
    public StructureGenerator(StructureShape structure, Model model, SymbolProvider symbolProvider) {
        this.structure = Objects.requireNonNull(structure, "structure is required");
        this.model = Objects.requireNonNull(model, "model is required");
        this.symbolProvider = Objects.requireNonNull(symbolProvider, "symbolProvider is required");
    }
    
    /**
     * Gets the structure shape being generated.
     *
     * @return The structure shape
     */
    public StructureShape getStructure() {
        return structure;
    }
    
    /**
     * Gets the Unison type name for this structure.
     *
     * @return The type name (PascalCase)
     */
    public String getTypeName() {
        return UnisonSymbolProvider.toUnisonTypeName(structure.getId().getName());
    }
    
    /**
     * Generates the Unison record type definition.
     *
     * @param writer The writer to output to
     */
    public void generate(UnisonWriter writer) {
        LOGGER.fine("Generating structure: " + structure.getId());
        
        // Write documentation comment if present
        writeDocumentation(writer);
        
        // Collect fields
        List<UnisonWriter.TypeField> fields = collectFields();
        
        // Write record type
        String typeName = getTypeName();
        writer.writeRecordType(typeName, fields);
    }
    
    /**
     * Generates just the type definition without documentation.
     * 
     * <p>Useful when generating multiple types in a batch.
     *
     * @param writer The writer to output to
     */
    public void generateTypeOnly(UnisonWriter writer) {
        List<UnisonWriter.TypeField> fields = collectFields();
        String typeName = getTypeName();
        writer.writeRecordType(typeName, fields);
    }
    
    /**
     * Collects the fields for this structure.
     *
     * @return List of type fields
     */
    public List<UnisonWriter.TypeField> collectFields() {
        List<UnisonWriter.TypeField> fields = new ArrayList<>();
        
        for (MemberShape member : structure.getAllMembers().values()) {
            String fieldName = toFieldName(member.getMemberName());
            String fieldType = getFieldType(member);
            fields.add(new UnisonWriter.TypeField(fieldName, fieldType));
        }
        
        return fields;
    }
    
    /**
     * Writes documentation comment if the structure has a documentation trait.
     */
    private void writeDocumentation(UnisonWriter writer) {
        Optional<DocumentationTrait> doc = structure.getTrait(DocumentationTrait.class);
        if (doc.isPresent()) {
            writer.writeDocComment(doc.get().getValue());
        }
    }
    
    /**
     * Converts a member name to a Unison field name (camelCase).
     * 
     * <p>Also escapes reserved words by appending an underscore.
     */
    private String toFieldName(String memberName) {
        if (memberName == null || memberName.isEmpty()) {
            return memberName;
        }
        // Convert to camelCase (first letter lowercase)
        String fieldName = Character.toLowerCase(memberName.charAt(0)) + memberName.substring(1);
        // Escape reserved words
        return io.smithy.unison.codegen.symbol.UnisonReservedWords.escape(fieldName);
    }
    
    /**
     * Gets the Unison type for a member, considering required/optional status.
     * 
     * <p>Members are wrapped in Optional unless:
     * <ul>
     *   <li>They have the {@code @required} trait</li>
     *   <li>They have the {@code @default} trait</li>
     * </ul>
     */
    private String getFieldType(MemberShape member) {
        Shape targetShape = model.expectShape(member.getTarget());
        String baseType = getUnisonType(targetShape);
        
        // Check if required or has default
        boolean isRequired = member.hasTrait(RequiredTrait.class);
        boolean hasDefault = member.hasTrait(DefaultTrait.class);
        
        if (isRequired || hasDefault) {
            return baseType;
        }
        
        // Wrap in Optional for optional fields
        return "Optional " + wrapIfComplex(baseType);
    }
    
    /**
     * Wraps a type in parentheses if it's complex (contains spaces).
     * 
     * <p>For example:
     * <ul>
     *   <li>{@code Text} → {@code Text}</li>
     *   <li>{@code Map Text Int} → {@code (Map Text Int)}</li>
     * </ul>
     */
    private String wrapIfComplex(String type) {
        if (type.contains(" ") && !type.startsWith("(") && !type.startsWith("[")) {
            return "(" + type + ")";
        }
        return type;
    }
    
    /**
     * Gets the Unison type for a shape.
     */
    private String getUnisonType(Shape shape) {
        if (shape instanceof StringShape) {
            if (shape.hasTrait(EnumTrait.class)) {
                return UnisonSymbolProvider.toUnisonTypeName(shape.getId().getName());
            }
            return "Text";
        } else if (shape instanceof IntegerShape || shape instanceof LongShape ||
                   shape instanceof ShortShape || shape instanceof ByteShape ||
                   shape instanceof BigIntegerShape) {
            return "Int";
        } else if (shape instanceof FloatShape || shape instanceof DoubleShape ||
                   shape instanceof BigDecimalShape) {
            return "Float";
        } else if (shape instanceof BooleanShape) {
            return "Boolean";
        } else if (shape instanceof BlobShape) {
            return "Bytes";
        } else if (shape instanceof TimestampShape) {
            return "Text";
        } else if (shape instanceof ListShape) {
            ListShape list = (ListShape) shape;
            Shape memberShape = model.expectShape(list.getMember().getTarget());
            String memberType = getUnisonType(memberShape);
            return "[" + memberType + "]";
        } else if (shape instanceof SetShape) {
            SetShape set = (SetShape) shape;
            Shape memberShape = model.expectShape(set.getMember().getTarget());
            String memberType = getUnisonType(memberShape);
            return "[" + memberType + "]";
        } else if (shape instanceof MapShape) {
            MapShape map = (MapShape) shape;
            Shape keyShape = model.expectShape(map.getKey().getTarget());
            Shape valueShape = model.expectShape(map.getValue().getTarget());
            String keyType = getUnisonType(keyShape);
            String valueType = getUnisonType(valueShape);
            return "Map " + keyType + " " + valueType;
        } else if (shape instanceof StructureShape) {
            return UnisonSymbolProvider.toUnisonTypeName(shape.getId().getName());
        } else if (shape instanceof UnionShape) {
            return UnisonSymbolProvider.toUnisonTypeName(shape.getId().getName());
        } else if (shape instanceof EnumShape) {
            return UnisonSymbolProvider.toUnisonTypeName(shape.getId().getName());
        } else if (shape instanceof IntEnumShape) {
            return UnisonSymbolProvider.toUnisonTypeName(shape.getId().getName());
        }
        return "a";  // Generic type parameter as fallback
    }
    
    /**
     * Checks if this is an input structure (name ends with "Input" or "Request").
     *
     * @return true if this is an input structure
     */
    public boolean isInputStructure() {
        String name = structure.getId().getName();
        return name.endsWith("Input") || name.endsWith("Request");
    }
    
    /**
     * Checks if this is an output structure (name ends with "Output" or "Response").
     *
     * @return true if this is an output structure
     */
    public boolean isOutputStructure() {
        String name = structure.getId().getName();
        return name.endsWith("Output") || name.endsWith("Response");
    }
    
    /**
     * Gets the documentation for this structure, if present.
     *
     * @return The documentation string, or empty if not present
     */
    public Optional<String> getDocumentation() {
        return structure.getTrait(DocumentationTrait.class)
            .map(DocumentationTrait::getValue);
    }
    
    /**
     * Gets the list of required field names.
     *
     * @return List of required field names
     */
    public List<String> getRequiredFields() {
        List<String> required = new ArrayList<>();
        for (MemberShape member : structure.getAllMembers().values()) {
            if (member.hasTrait(RequiredTrait.class)) {
                required.add(toFieldName(member.getMemberName()));
            }
        }
        return required;
    }
    
    /**
     * Gets the list of fields with default values.
     *
     * @return List of field names with defaults
     */
    public List<String> getFieldsWithDefaults() {
        List<String> withDefaults = new ArrayList<>();
        for (MemberShape member : structure.getAllMembers().values()) {
            if (member.hasTrait(DefaultTrait.class)) {
                withDefaults.add(toFieldName(member.getMemberName()));
            }
        }
        return withDefaults;
    }
}
