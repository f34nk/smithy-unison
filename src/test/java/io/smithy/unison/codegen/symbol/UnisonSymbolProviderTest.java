package io.smithy.unison.codegen.symbol;

import io.smithy.unison.codegen.UnisonSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.EnumDefinition;
import software.amazon.smithy.model.traits.EnumTrait;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UnisonSymbolProvider.
 */
class UnisonSymbolProviderTest {
    
    private static final ShapeId SERVICE_ID = ShapeId.from("test.example#TestService");
    
    private Model.Builder modelBuilder;
    private UnisonSettings settings;
    
    @BeforeEach
    void setUp() {
        modelBuilder = Model.builder();
        
        // Add required service shape
        ServiceShape service = ServiceShape.builder()
            .id(SERVICE_ID)
            .version("1.0")
            .build();
        modelBuilder.addShape(service);
        
        settings = UnisonSettings.builder()
            .service(SERVICE_ID)
            .namespace("test.example")
            .outputDir("generated")
            .build();
    }
    
    private UnisonSymbolProvider createProvider() {
        return new UnisonSymbolProvider(modelBuilder.build(), settings);
    }
    
    // ========== String Type Tests ==========
    
    @Test
    void stringShape_mapsToText() {
        StringShape shape = StringShape.builder()
            .id("test.example#MyString")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Text", symbol.getProperty("unisonType", String.class).orElse(""));
    }
    
    @Test
    void stringShape_withEnumTrait_mapsToTypeName() {
        StringShape shape = StringShape.builder()
            .id("test.example#Status")
            .addTrait(EnumTrait.builder()
                .addEnum(EnumDefinition.builder().value("ACTIVE").build())
                .addEnum(EnumDefinition.builder().value("INACTIVE").build())
                .build())
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Status", symbol.getProperty("unisonType", String.class).orElse(""));
        assertTrue(symbol.getProperty("isEnum", Boolean.class).orElse(false));
    }
    
    // ========== Integer Type Tests ==========
    
    @Test
    void integerShape_mapsToInt() {
        IntegerShape shape = IntegerShape.builder()
            .id("test.example#MyInt")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Int", symbol.getProperty("unisonType", String.class).orElse(""));
    }
    
    @Test
    void longShape_mapsToInt() {
        LongShape shape = LongShape.builder()
            .id("test.example#MyLong")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Int", symbol.getProperty("unisonType", String.class).orElse(""));
    }
    
    @Test
    void shortShape_mapsToInt() {
        ShortShape shape = ShortShape.builder()
            .id("test.example#MyShort")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Int", symbol.getProperty("unisonType", String.class).orElse(""));
    }
    
    @Test
    void byteShape_mapsToInt() {
        ByteShape shape = ByteShape.builder()
            .id("test.example#MyByte")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Int", symbol.getProperty("unisonType", String.class).orElse(""));
    }
    
    @Test
    void bigIntegerShape_mapsToInt() {
        BigIntegerShape shape = BigIntegerShape.builder()
            .id("test.example#MyBigInt")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Int", symbol.getProperty("unisonType", String.class).orElse(""));
    }
    
    // ========== Float Type Tests ==========
    
    @Test
    void floatShape_mapsToFloat() {
        FloatShape shape = FloatShape.builder()
            .id("test.example#MyFloat")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Float", symbol.getProperty("unisonType", String.class).orElse(""));
    }
    
    @Test
    void doubleShape_mapsToFloat() {
        DoubleShape shape = DoubleShape.builder()
            .id("test.example#MyDouble")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Float", symbol.getProperty("unisonType", String.class).orElse(""));
    }
    
    @Test
    void bigDecimalShape_mapsToFloat() {
        BigDecimalShape shape = BigDecimalShape.builder()
            .id("test.example#MyBigDecimal")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Float", symbol.getProperty("unisonType", String.class).orElse(""));
    }
    
    // ========== Boolean Type Test ==========
    
    @Test
    void booleanShape_mapsToBoolean() {
        BooleanShape shape = BooleanShape.builder()
            .id("test.example#MyBool")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Boolean", symbol.getProperty("unisonType", String.class).orElse(""));
    }
    
    // ========== Blob Type Test ==========
    
    @Test
    void blobShape_mapsToBytes() {
        BlobShape shape = BlobShape.builder()
            .id("test.example#MyBlob")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Bytes", symbol.getProperty("unisonType", String.class).orElse(""));
    }
    
    // ========== Timestamp Type Test ==========
    
    @Test
    void timestampShape_mapsToText() {
        TimestampShape shape = TimestampShape.builder()
            .id("test.example#MyTimestamp")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Text", symbol.getProperty("unisonType", String.class).orElse(""));
        assertTrue(symbol.getProperty("isTimestamp", Boolean.class).orElse(false));
    }
    
    // ========== List Type Tests ==========
    
    @Test
    void listShape_mapsToListType() {
        StringShape memberShape = StringShape.builder()
            .id("test.example#StringMember")
            .build();
        modelBuilder.addShape(memberShape);
        
        ListShape shape = ListShape.builder()
            .id("test.example#MyList")
            .member(memberShape.getId())
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("[Text]", symbol.getProperty("unisonType", String.class).orElse(""));
        assertEquals("Text", symbol.getProperty("memberType", String.class).orElse(""));
        assertTrue(symbol.getProperty("isList", Boolean.class).orElse(false));
    }
    
    @Test
    void listShape_withStructureMember() {
        StructureShape memberStruct = StructureShape.builder()
            .id("test.example#Item")
            .build();
        modelBuilder.addShape(memberStruct);
        
        ListShape shape = ListShape.builder()
            .id("test.example#ItemList")
            .member(memberStruct.getId())
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("[Item]", symbol.getProperty("unisonType", String.class).orElse(""));
        assertEquals("Item", symbol.getProperty("memberType", String.class).orElse(""));
    }
    
    // ========== Map Type Tests ==========
    
    @Test
    void mapShape_mapsToMapType() {
        StringShape keyShape = StringShape.builder()
            .id("test.example#KeyString")
            .build();
        IntegerShape valueShape = IntegerShape.builder()
            .id("test.example#ValueInt")
            .build();
        modelBuilder.addShape(keyShape);
        modelBuilder.addShape(valueShape);
        
        MapShape shape = MapShape.builder()
            .id("test.example#MyMap")
            .key(keyShape.getId())
            .value(valueShape.getId())
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Map Text Int", symbol.getProperty("unisonType", String.class).orElse(""));
        assertEquals("Text", symbol.getProperty("keyType", String.class).orElse(""));
        assertEquals("Int", symbol.getProperty("valueType", String.class).orElse(""));
        assertTrue(symbol.getProperty("isMap", Boolean.class).orElse(false));
    }
    
    // ========== Structure Type Test ==========
    
    @Test
    void structureShape_mapsToTypeName() {
        StructureShape shape = StructureShape.builder()
            .id("test.example#GetObjectInput")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("GetObjectInput", symbol.getProperty("unisonType", String.class).orElse(""));
        assertTrue(symbol.getProperty("isStructure", Boolean.class).orElse(false));
    }
    
    // ========== Union Type Test ==========
    
    @Test
    void unionShape_mapsToTypeName() {
        StringShape variant1 = StringShape.builder()
            .id("test.example#VariantA")
            .build();
        IntegerShape variant2 = IntegerShape.builder()
            .id("test.example#VariantB")
            .build();
        modelBuilder.addShape(variant1);
        modelBuilder.addShape(variant2);
        
        UnionShape shape = UnionShape.builder()
            .id("test.example#S3Response")
            .addMember("success", variant1.getId())
            .addMember("error", variant2.getId())
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("S3Response", symbol.getProperty("unisonType", String.class).orElse(""));
        assertTrue(symbol.getProperty("isUnion", Boolean.class).orElse(false));
    }
    
    // ========== Enum Type Tests (Smithy 2.0) ==========
    
    @Test
    void enumShape_mapsToTypeName() {
        EnumShape shape = EnumShape.builder()
            .id("test.example#BucketLocation")
            .addMember("US_EAST_1", "us-east-1")
            .addMember("US_WEST_2", "us-west-2")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("BucketLocation", symbol.getProperty("unisonType", String.class).orElse(""));
        assertTrue(symbol.getProperty("isEnum", Boolean.class).orElse(false));
    }
    
    // ========== IntEnum Type Tests ==========
    
    @Test
    void intEnumShape_mapsToTypeName() {
        IntEnumShape shape = IntEnumShape.builder()
            .id("test.example#Priority")
            .addMember("LOW", 1)
            .addMember("MEDIUM", 2)
            .addMember("HIGH", 3)
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("Priority", symbol.getProperty("unisonType", String.class).orElse(""));
        assertTrue(symbol.getProperty("isIntEnum", Boolean.class).orElse(false));
    }
    
    // ========== Naming Convention Tests ==========
    
    @Test
    void toUnisonTypeName_preservesPascalCase() {
        assertEquals("GetObjectInput", UnisonSymbolProvider.toUnisonTypeName("GetObjectInput"));
        assertEquals("S3Bucket", UnisonSymbolProvider.toUnisonTypeName("S3Bucket"));
        assertEquals("XMLParser", UnisonSymbolProvider.toUnisonTypeName("XMLParser"));
    }
    
    @Test
    void toUnisonTypeName_handlesEdgeCases() {
        assertNull(UnisonSymbolProvider.toUnisonTypeName(null));
        assertEquals("", UnisonSymbolProvider.toUnisonTypeName(""));
    }
    
    @Test
    void toUnisonFunctionName_convertsToCamelCase() {
        assertEquals("getObject", UnisonSymbolProvider.toUnisonFunctionName("GetObject"));
        assertEquals("listBuckets", UnisonSymbolProvider.toUnisonFunctionName("ListBuckets"));
        assertEquals("s3Bucket", UnisonSymbolProvider.toUnisonFunctionName("S3Bucket"));
    }
    
    @Test
    void toUnisonFunctionName_handlesEdgeCases() {
        assertNull(UnisonSymbolProvider.toUnisonFunctionName(null));
        assertEquals("", UnisonSymbolProvider.toUnisonFunctionName(""));
    }
    
    @Test
    void toUnisonEnumVariant_formatsCorrectly() {
        assertEquals("BucketLocation'UsEast1", UnisonSymbolProvider.toUnisonEnumVariant("BucketLocation", "UsEast1"));
        assertEquals("Status'Active", UnisonSymbolProvider.toUnisonEnumVariant("Status", "Active"));
    }
    
    // ========== Symbol Properties Tests ==========
    
    @Test
    void symbol_hasCorrectNamespace() {
        StringShape shape = StringShape.builder()
            .id("test.example#MyString")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("test.example", symbol.getNamespace());
    }
    
    @Test
    void symbol_hasCorrectDefinitionFile() {
        StructureShape shape = StructureShape.builder()
            .id("test.example#MyStruct")
            .build();
        modelBuilder.addShape(shape);
        
        UnisonSymbolProvider provider = createProvider();
        Symbol symbol = provider.toSymbol(shape);
        
        assertEquals("test_example_types.u", symbol.getDefinitionFile());
    }
    
    @Test
    void serviceShape_hasClientDefinitionFile() {
        UnisonSymbolProvider provider = createProvider();
        ServiceShape service = modelBuilder.build().expectShape(SERVICE_ID, ServiceShape.class);
        Symbol symbol = provider.toSymbol(service);
        
        assertEquals("test_example_client.u", symbol.getDefinitionFile());
    }
}
