package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.EnumDefinition;
import software.amazon.smithy.model.traits.EnumTrait;
import software.amazon.smithy.model.traits.EnumValueTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Generates Unison sum types and conversion functions for enum shapes.
 * 
 * <p>This generator supports both:
 * <ul>
 *   <li>Smithy 1.0 StringShape with {@code @enum} trait</li>
 *   <li>Smithy 2.0 EnumShape</li>
 * </ul>
 * 
 * <h2>Generated Code</h2>
 * <p>For each enum, generates:
 * <ul>
 *   <li>Sum type definition with variants using TypeName'VariantName format</li>
 *   <li>{@code enumNameToText} function for serialization</li>
 *   <li>{@code enumNameFromText} function for deserialization</li>
 * </ul>
 * 
 * <h2>Example Output</h2>
 * <pre>
 * type BucketLocationConstraint
 *   = BucketLocationConstraint'AfSouth1
 *   | BucketLocationConstraint'ApEast1
 *   | BucketLocationConstraint'UsEast1
 * 
 * bucketLocationConstraintToText : BucketLocationConstraint -> Text
 * bucketLocationConstraintToText val = match val with
 *   BucketLocationConstraint'AfSouth1 -> "af-south-1"
 *   BucketLocationConstraint'ApEast1 -> "ap-east-1"
 *   BucketLocationConstraint'UsEast1 -> "us-east-1"
 * 
 * bucketLocationConstraintFromText : Text -> Optional BucketLocationConstraint
 * bucketLocationConstraintFromText t = match t with
 *   "af-south-1" -> Some BucketLocationConstraint'AfSouth1
 *   "ap-east-1" -> Some BucketLocationConstraint'ApEast1
 *   "us-east-1" -> Some BucketLocationConstraint'UsEast1
 *   _ -> None
 * </pre>
 * 
 * @see UnisonContext
 * @see UnisonWriter
 */
public final class EnumGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(EnumGenerator.class.getName());
    
    private final String typeName;
    private final List<EnumValue> enumValues;
    private final String documentation;
    
    /**
     * Represents a single enum value with its name and wire value.
     */
    public static final class EnumValue {
        private final String name;
        private final String wireValue;
        
        public EnumValue(String name, String wireValue) {
            this.name = name;
            this.wireValue = wireValue;
        }
        
        /** The variant name (PascalCase). */
        public String name() { return name; }
        
        /** The wire value (string to serialize/deserialize). */
        public String wireValue() { return wireValue; }
    }
    
    /**
     * Creates an enum generator from a StringShape with @enum trait (Smithy 1.0).
     *
     * @param enumShape The string shape with @enum trait
     * @param context The code generation context (unused but kept for API compatibility)
     * @throws IllegalArgumentException if the shape doesn't have EnumTrait
     */
    public EnumGenerator(StringShape enumShape, UnisonContext context) {
        Objects.requireNonNull(enumShape, "enumShape is required");
        
        if (!enumShape.hasTrait(EnumTrait.class)) {
            throw new IllegalArgumentException("Shape must have @enum trait: " + enumShape.getId());
        }
        
        this.typeName = UnisonSymbolProvider.toUnisonTypeName(enumShape.getId().getName());
        this.enumValues = extractEnumTraitValues(enumShape);
        this.documentation = enumShape.getTrait(DocumentationTrait.class)
            .map(DocumentationTrait::getValue)
            .orElse(null);
    }
    
    /**
     * Creates an enum generator from a Smithy 2.0 EnumShape.
     *
     * @param enumShape The Smithy 2.0 enum shape
     * @param model The Smithy model
     */
    public EnumGenerator(EnumShape enumShape, Model model) {
        Objects.requireNonNull(enumShape, "enumShape is required");
        Objects.requireNonNull(model, "model is required");
        
        this.typeName = UnisonSymbolProvider.toUnisonTypeName(enumShape.getId().getName());
        this.enumValues = extractEnumShapeValues(enumShape);
        this.documentation = enumShape.getTrait(DocumentationTrait.class)
            .map(DocumentationTrait::getValue)
            .orElse(null);
    }
    
    /**
     * Creates an enum generator with explicit values (for testing or manual construction).
     *
     * @param typeName The type name
     * @param values The enum values
     * @param documentation Optional documentation
     */
    public EnumGenerator(String typeName, List<EnumValue> values, String documentation) {
        this.typeName = Objects.requireNonNull(typeName, "typeName is required");
        this.enumValues = Objects.requireNonNull(values, "values is required");
        this.documentation = documentation;
    }
    
    /**
     * Gets the Unison type name for this enum.
     *
     * @return The PascalCase type name
     */
    public String getTypeName() {
        return typeName;
    }
    
    /**
     * Gets the enum values.
     *
     * @return List of enum values
     */
    public List<EnumValue> getValues() {
        return enumValues;
    }
    
    /**
     * Gets the full variant name for an enum value.
     *
     * @param valueName The value name
     * @return The full variant name (TypeName'ValueName)
     */
    public String getVariantName(String valueName) {
        return UnisonSymbolProvider.toUnisonEnumVariant(typeName, valueName);
    }
    
    /**
     * Generates the complete enum code: type definition and conversion functions.
     *
     * @param writer The writer to output code to
     */
    public void generate(UnisonWriter writer) {
        LOGGER.fine("Generating enum: " + typeName);
        
        generateTypeDefinition(writer);
        generateToTextFunction(writer);
        generateFromTextFunction(writer);
    }
    
    /**
     * Generates only the type definition (without conversion functions).
     *
     * @param writer The writer to output code to
     */
    public void generateTypeDefinition(UnisonWriter writer) {
        // Write documentation if present
        if (documentation != null && !documentation.isEmpty()) {
            writer.writeDocComment(documentation);
        }
        
        // Build variants list
        List<UnisonWriter.Variant> variants = new ArrayList<>();
        for (EnumValue value : enumValues) {
            String variantName = getVariantName(value.name());
            variants.add(new UnisonWriter.Variant(variantName, null));  // No payload for enum variants
        }
        
        // Write union type
        writer.writeUnionType(typeName, variants);
    }
    
    /**
     * Generates the toText conversion function.
     *
     * @param writer The writer to output code to
     */
    public void generateToTextFunction(UnisonWriter writer) {
        String funcName = UnisonSymbolProvider.toUnisonFunctionName(typeName) + "ToText";
        
        // Build match cases
        List<UnisonWriter.EnumMapping> mappings = new ArrayList<>();
        for (EnumValue value : enumValues) {
            mappings.add(new UnisonWriter.EnumMapping(value.name(), value.wireValue()));
        }
        
        writer.writeEnumToTextFunction(typeName, mappings);
    }
    
    /**
     * Generates the fromText conversion function.
     *
     * @param writer The writer to output code to
     */
    public void generateFromTextFunction(UnisonWriter writer) {
        String funcName = UnisonSymbolProvider.toUnisonFunctionName(typeName) + "FromText";
        
        // Build match cases
        List<UnisonWriter.EnumMapping> mappings = new ArrayList<>();
        for (EnumValue value : enumValues) {
            mappings.add(new UnisonWriter.EnumMapping(value.name(), value.wireValue()));
        }
        
        writer.writeEnumFromTextFunction(typeName, mappings);
    }
    
    /**
     * Extracts enum values from a StringShape with @enum trait (Smithy 1.0).
     */
    private List<EnumValue> extractEnumTraitValues(StringShape shape) {
        EnumTrait trait = shape.expectTrait(EnumTrait.class);
        List<EnumValue> values = new ArrayList<>();
        
        for (EnumDefinition def : trait.getValues()) {
            String name = def.getName().orElse(toVariantName(def.getValue()));
            String wireValue = def.getValue();
            values.add(new EnumValue(name, wireValue));
        }
        
        return values;
    }
    
    /**
     * Extracts enum values from a Smithy 2.0 EnumShape.
     */
    private List<EnumValue> extractEnumShapeValues(EnumShape shape) {
        List<EnumValue> values = new ArrayList<>();
        Map<String, String> enumValues = shape.getEnumValues();
        
        for (Map.Entry<String, String> entry : enumValues.entrySet()) {
            String name = entry.getKey();
            String wireValue = entry.getValue();
            values.add(new EnumValue(name, wireValue));
        }
        
        return values;
    }
    
    /**
     * Converts a wire value to a suitable variant name.
     * 
     * <p>Examples:
     * <ul>
     *   <li>"af-south-1" → "AfSouth1"</li>
     *   <li>"STANDARD" → "Standard"</li>
     *   <li>"us-east-1" → "UsEast1"</li>
     * </ul>
     */
    private String toVariantName(String wireValue) {
        if (wireValue == null || wireValue.isEmpty()) {
            return wireValue;
        }
        
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : wireValue.toCharArray()) {
            if (c == '-' || c == '_' || c == ' ') {
                capitalizeNext = true;
            } else if (Character.isDigit(c)) {
                sb.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        
        return sb.toString();
    }
}
