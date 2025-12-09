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
 * <p>Key features:
 * <ul>
 *   <li>Types use PascalCase</li>
 *   <li>Functions use camelCase</li>
 *   <li>Maps Smithy types to Unison types (Text, Int, Float, etc.)</li>
 * </ul>
 * 
 * <p><b>NOT FULLY IMPLEMENTED</b>: This is a first draft with basic type mappings.
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
     * Converts a Smithy shape to a Unison symbol.
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
     */
    public static String toUnisonFunctionName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // Convert PascalCase to camelCase
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
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
        
        @Override
        public Void stringShape(StringShape shape) {
            if (shape.hasTrait(EnumTrait.class)) {
                builder.putProperty("unisonType", toUnisonTypeName(shape.getId().getName()));
            } else {
                builder.putProperty("unisonType", "Text");
            }
            return null;
        }
        
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
        public Void bigIntegerShape(BigIntegerShape shape) {
            builder.putProperty("unisonType", "Int");
            return null;
        }
        
        @Override
        public Void bigDecimalShape(BigDecimalShape shape) {
            builder.putProperty("unisonType", "Float");
            return null;
        }
        
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
            // Unison doesn't have a built-in DateTime, use Text
            builder.putProperty("unisonType", "Text");
            return null;
        }
        
        @Override
        public Void listShape(ListShape shape) {
            Shape memberShape = model.expectShape(shape.getMember().getTarget());
            String memberType = getUnisonTypeForShape(memberShape);
            builder.putProperty("unisonType", "[" + memberType + "]");
            builder.putProperty("memberType", memberType);
            return null;
        }
        
        @Override
        public Void setShape(SetShape shape) {
            Shape memberShape = model.expectShape(shape.getMember().getTarget());
            String memberType = getUnisonTypeForShape(memberShape);
            builder.putProperty("unisonType", "[" + memberType + "]");
            builder.putProperty("memberType", memberType);
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
            return null;
        }
        
        @Override
        public Void structureShape(StructureShape shape) {
            String typeName = toUnisonTypeName(shape.getId().getName());
            builder.putProperty("unisonType", typeName);
            return null;
        }
        
        @Override
        public Void unionShape(UnionShape shape) {
            String typeName = toUnisonTypeName(shape.getId().getName());
            builder.putProperty("unisonType", typeName);
            return null;
        }
        
        @Override
        public Void serviceShape(ServiceShape shape) {
            builder.putProperty("unisonType", "a");
            return null;
        }
        
        @Override
        public Void operationShape(OperationShape shape) {
            builder.putProperty("unisonType", "a");
            return null;
        }
        
        @Override
        public Void resourceShape(ResourceShape shape) {
            builder.putProperty("unisonType", "a");
            return null;
        }
        
        @Override
        public Void memberShape(MemberShape shape) {
            Shape targetShape = model.expectShape(shape.getTarget());
            targetShape.accept(this);
            return null;
        }
        
        /**
         * Gets the Unison type for a shape (simplified for nested types).
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
            } else if (shape instanceof ListShape || shape instanceof SetShape) {
                return "[a]";
            } else if (shape instanceof MapShape) {
                return "Map Text a";
            } else if (shape instanceof StructureShape) {
                return toUnisonTypeName(shape.getId().getName());
            } else if (shape instanceof UnionShape) {
                return toUnisonTypeName(shape.getId().getName());
            }
            return "a";
        }
    }
}
