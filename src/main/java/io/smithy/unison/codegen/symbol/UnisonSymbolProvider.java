package io.smithy.unison.codegen.symbol;

import io.smithy.unison.codegen.UnisonSettings;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.EnumTrait;

import java.util.Objects;

/**
 * Symbol provider that maps Smithy shapes to Unison symbols.
 * 
 * <p>This class implements the standard Smithy {@link SymbolProvider} interface,
 * providing a mapping from Smithy shapes to Unison symbols with appropriate
 * names, types, and file locations.
 * 
 * <h2>Type Mappings</h2>
 * <table>
 *   <tr><th>Smithy Type</th><th>Unison Type</th></tr>
 *   <tr><td>string</td><td>Text</td></tr>
 *   <tr><td>integer, long, short, byte, bigInteger</td><td>Int</td></tr>
 *   <tr><td>float, double, bigDecimal</td><td>Float</td></tr>
 *   <tr><td>boolean</td><td>Boolean</td></tr>
 *   <tr><td>blob</td><td>Bytes</td></tr>
 *   <tr><td>timestamp</td><td>Text (ISO 8601)</td></tr>
 *   <tr><td>list&lt;T&gt;</td><td>[T]</td></tr>
 *   <tr><td>map&lt;K, V&gt;</td><td>Map K V</td></tr>
 *   <tr><td>structure</td><td>Record type</td></tr>
 *   <tr><td>union</td><td>Sum type</td></tr>
 *   <tr><td>enum</td><td>Sum type</td></tr>
 *   <tr><td>intEnum</td><td>Sum type with Int payload</td></tr>
 * </table>
 * 
 * <h2>Naming Conventions</h2>
 * <ul>
 *   <li>Types use PascalCase</li>
 *   <li>Functions use camelCase</li>
 *   <li>Enum variants use TypeName'VariantName format</li>
 * </ul>
 * 
 * @see SymbolProvider
 */
public final class UnisonSymbolProvider implements SymbolProvider {
    
    private final Model model;
    private final UnisonSettings settings;
    
    /**
     * Creates a new symbol provider.
     *
     * @param model The Smithy model
     * @param settings The code generation settings
     */
    public UnisonSymbolProvider(Model model, UnisonSettings settings) {
        this.model = Objects.requireNonNull(model, "model is required");
        this.settings = Objects.requireNonNull(settings, "settings is required");
    }
    
    /**
     * Gets the model.
     *
     * @return The Smithy model
     */
    public Model getModel() {
        return model;
    }
    
    /**
     * Gets the settings.
     *
     * @return The code generation settings
     */
    public UnisonSettings getSettings() {
        return settings;
    }
    
    /**
     * Converts a Smithy shape to a Unison symbol.
     * 
     * <p>The resulting symbol includes:
     * <ul>
     *   <li>name - The Unison-friendly name</li>
     *   <li>namespace - The Unison namespace</li>
     *   <li>definitionFile - The file where the shape is defined</li>
     *   <li>unisonType property - The Unison type specification</li>
     * </ul>
     */
    @Override
    public Symbol toSymbol(Shape shape) {
        String name = toUnisonTypeName(shape.getId().getName());
        
        Symbol.Builder builder = Symbol.builder()
            .name(name)
            .namespace(getNamespace(), ".")
            .definitionFile(getDefinitionFile(shape));
        
        // Add shape-specific type configuration
        shape.accept(new UnisonTypeVisitor(builder));
        
        return builder.build();
    }
    
    /**
     * Converts a Smithy name to Unison type convention (PascalCase).
     * 
     * <p>Smithy types are already PascalCase, so this mostly preserves them.
     *
     * @param name The Smithy name
     * @return The Unison type name
     */
    public static String toUnisonTypeName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // Smithy types are already PascalCase, keep as-is
        return name;
    }
    
    /**
     * Converts a Smithy name to Unison function convention (camelCase).
     * 
     * <p>Converts PascalCase to camelCase for function names.
     *
     * @param name The Smithy name
     * @return The Unison function name
     */
    public static String toUnisonFunctionName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // Convert PascalCase to camelCase
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    
    /**
     * Converts an enum value to a Unison variant name.
     * 
     * <p>Unison uses TypeName'VariantName format for enum variants.
     * This method takes the enum type name and variant name and combines them.
     *
     * @param typeName The enum type name
     * @param variantName The variant name
     * @return The Unison variant name (e.g., "BucketLocation'UsEast1")
     */
    public static String toUnisonEnumVariant(String typeName, String variantName) {
        return typeName + "'" + variantName;
    }
    
    /**
     * Gets the namespace for symbols.
     */
    private String getNamespace() {
        String ns = settings.namespace();
        if (ns != null && !ns.isEmpty()) {
            return ns;
        }
        return settings.outputDir();
    }
    
    /**
     * Gets the definition file for a shape.
     */
    private String getDefinitionFile(Shape shape) {
        String namespace = settings.namespace();
        if (namespace == null) {
            namespace = toUnisonFunctionName(settings.service().getName());
        }
        
        // Replace dots with underscores for filename
        String baseName = namespace.replace(".", "_");
        
        if (shape instanceof ServiceShape) {
            return baseName + "_client.u";
        }
        return baseName + "_types.u";
    }
    
    /**
     * Visitor for shape-specific symbol configuration.
     * 
     * <p>Sets the "unisonType" property on the symbol builder
     * based on the shape type.
     */
    private class UnisonTypeVisitor extends ShapeVisitor.Default<Void> {
        private final Symbol.Builder builder;
        
        UnisonTypeVisitor(Symbol.Builder builder) {
            this.builder = builder;
        }
        
        @Override
        protected Void getDefault(Shape shape) {
            builder.putProperty("unisonType", "a");  // Generic type parameter
            return null;
        }
        
        // ========== String Types ==========
        
        @Override
        public Void stringShape(StringShape shape) {
            if (shape.hasTrait(EnumTrait.class)) {
                // StringShape with @enum trait (Smithy 1.0 style)
                builder.putProperty("unisonType", toUnisonTypeName(shape.getId().getName()));
                builder.putProperty("isEnum", true);
            } else {
                builder.putProperty("unisonType", "Text");
            }
            return null;
        }
        
        // ========== Integer Types ==========
        
        @Override
        public Void integerShape(IntegerShape shape) {
            builder.putProperty("unisonType", "Int");
            return null;
        }
        
        @Override
        public Void longShape(LongShape shape) {
            builder.putProperty("unisonType", "Int");
            return null;
        }
        
        @Override
        public Void shortShape(ShortShape shape) {
            builder.putProperty("unisonType", "Int");
            return null;
        }
        
        @Override
        public Void byteShape(ByteShape shape) {
            builder.putProperty("unisonType", "Int");
            return null;
        }
        
        @Override
        public Void bigIntegerShape(BigIntegerShape shape) {
            builder.putProperty("unisonType", "Int");
            return null;
        }
        
        // ========== Float Types ==========
        
        @Override
        public Void floatShape(FloatShape shape) {
            builder.putProperty("unisonType", "Float");
            return null;
        }
        
        @Override
        public Void doubleShape(DoubleShape shape) {
            builder.putProperty("unisonType", "Float");
            return null;
        }
        
        @Override
        public Void bigDecimalShape(BigDecimalShape shape) {
            builder.putProperty("unisonType", "Float");
            return null;
        }
        
        // ========== Other Primitive Types ==========
        
        @Override
        public Void booleanShape(BooleanShape shape) {
            builder.putProperty("unisonType", "Boolean");
            return null;
        }
        
        @Override
        public Void blobShape(BlobShape shape) {
            builder.putProperty("unisonType", "Bytes");
            return null;
        }
        
        @Override
        public Void timestampShape(TimestampShape shape) {
            // Unison doesn't have a built-in DateTime type
            // Use Text for ISO 8601 string representation
            builder.putProperty("unisonType", "Text");
            builder.putProperty("isTimestamp", true);
            return null;
        }
        
        // ========== Collection Types ==========
        
        @Override
        public Void listShape(ListShape shape) {
            Shape memberShape = model.expectShape(shape.getMember().getTarget());
            String memberType = getUnisonTypeForShape(memberShape);
            builder.putProperty("unisonType", "[" + memberType + "]");
            builder.putProperty("memberType", memberType);
            builder.putProperty("isList", true);
            return null;
        }
        
        @Override
        public Void setShape(SetShape shape) {
            // Unison doesn't have a built-in Set type, use List
            Shape memberShape = model.expectShape(shape.getMember().getTarget());
            String memberType = getUnisonTypeForShape(memberShape);
            builder.putProperty("unisonType", "[" + memberType + "]");
            builder.putProperty("memberType", memberType);
            builder.putProperty("isList", true);
            builder.putProperty("isSet", true);
            return null;
        }
        
        @Override
        public Void mapShape(MapShape shape) {
            Shape keyShape = model.expectShape(shape.getKey().getTarget());
            Shape valueShape = model.expectShape(shape.getValue().getTarget());
            String keyType = getUnisonTypeForShape(keyShape);
            String valueType = getUnisonTypeForShape(valueShape);
            builder.putProperty("unisonType", "Map " + keyType + " " + valueType);
            builder.putProperty("keyType", keyType);
            builder.putProperty("valueType", valueType);
            builder.putProperty("isMap", true);
            return null;
        }
        
        // ========== Aggregate Types ==========
        
        @Override
        public Void structureShape(StructureShape shape) {
            String typeName = toUnisonTypeName(shape.getId().getName());
            builder.putProperty("unisonType", typeName);
            builder.putProperty("isStructure", true);
            return null;
        }
        
        @Override
        public Void unionShape(UnionShape shape) {
            String typeName = toUnisonTypeName(shape.getId().getName());
            builder.putProperty("unisonType", typeName);
            builder.putProperty("isUnion", true);
            return null;
        }
        
        // ========== Enum Types (Smithy 2.0) ==========
        
        @Override
        public Void enumShape(EnumShape shape) {
            // Smithy 2.0 EnumShape (not StringShape with @enum trait)
            String typeName = toUnisonTypeName(shape.getId().getName());
            builder.putProperty("unisonType", typeName);
            builder.putProperty("isEnum", true);
            return null;
        }
        
        @Override
        public Void intEnumShape(IntEnumShape shape) {
            // Integer enum - maps to sum type in Unison
            String typeName = toUnisonTypeName(shape.getId().getName());
            builder.putProperty("unisonType", typeName);
            builder.putProperty("isIntEnum", true);
            return null;
        }
        
        // ========== Service Types ==========
        
        @Override
        public Void serviceShape(ServiceShape shape) {
            builder.putProperty("unisonType", "a");
            builder.putProperty("isService", true);
            return null;
        }
        
        @Override
        public Void operationShape(OperationShape shape) {
            builder.putProperty("unisonType", "a");
            builder.putProperty("isOperation", true);
            return null;
        }
        
        @Override
        public Void resourceShape(ResourceShape shape) {
            builder.putProperty("unisonType", "a");
            builder.putProperty("isResource", true);
            return null;
        }
        
        // ========== Member Types ==========
        
        @Override
        public Void memberShape(MemberShape shape) {
            Shape targetShape = model.expectShape(shape.getTarget());
            targetShape.accept(this);
            return null;
        }
        
        /**
         * Gets the Unison type for a shape (used for nested types in collections).
         * 
         * <p>This provides a simplified type string for use in container types
         * like List and Map. For complex nested types, returns the type name
         * rather than recursively expanding.
         *
         * @param shape The shape to get the type for
         * @return The Unison type string
         */
        private String getUnisonTypeForShape(Shape shape) {
            if (shape instanceof StringShape) {
                return shape.hasTrait(EnumTrait.class) 
                    ? toUnisonTypeName(shape.getId().getName())
                    : "Text";
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
                // For nested lists, use generic type parameter
                return "[a]";
            } else if (shape instanceof SetShape) {
                return "[a]";
            } else if (shape instanceof MapShape) {
                return "Map Text a";
            } else if (shape instanceof StructureShape) {
                return toUnisonTypeName(shape.getId().getName());
            } else if (shape instanceof UnionShape) {
                return toUnisonTypeName(shape.getId().getName());
            } else if (shape instanceof EnumShape) {
                return toUnisonTypeName(shape.getId().getName());
            } else if (shape instanceof IntEnumShape) {
                return toUnisonTypeName(shape.getId().getName());
            }
            return "a";
        }
    }
}
