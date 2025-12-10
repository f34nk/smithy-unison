package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonSettings;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StructureGenerator.
 */
class StructureGeneratorTest {
    
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
    
    private StructureGenerator createGenerator(StructureShape structure) {
        Model model = modelBuilder.build();
        UnisonSymbolProvider symbolProvider = new UnisonSymbolProvider(model, settings);
        return new StructureGenerator(structure, model, symbolProvider);
    }
    
    // ========== Basic Structure Tests ==========
    
    @Test
    void generate_emptyStructure() {
        StructureShape structure = StructureShape.builder()
            .id("test.example#EmptyStruct")
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        // Empty records use simple constructor (Unison doesn't support empty braces)
        assertTrue(output.contains("type EmptyStruct = EmptyStruct"));
    }
    
    @Test
    void generate_simpleStructure() {
        StringShape stringShape = StringShape.builder()
            .id("test.example#StringType")
            .build();
        modelBuilder.addShape(stringShape);
        
        StructureShape structure = StructureShape.builder()
            .id("test.example#SimpleStruct")
            .addMember("name", stringShape.getId())
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type SimpleStruct = {"));
        assertTrue(output.contains("name : Optional Text"));
    }
    
    @Test
    void generate_multipleFields() {
        StringShape stringShape = StringShape.builder()
            .id("test.example#StringType")
            .build();
        IntegerShape intShape = IntegerShape.builder()
            .id("test.example#IntType")
            .build();
        BooleanShape boolShape = BooleanShape.builder()
            .id("test.example#BoolType")
            .build();
        modelBuilder.addShape(stringShape);
        modelBuilder.addShape(intShape);
        modelBuilder.addShape(boolShape);
        
        StructureShape structure = StructureShape.builder()
            .id("test.example#MultiFieldStruct")
            .addMember("name", stringShape.getId())
            .addMember("count", intShape.getId())
            .addMember("active", boolShape.getId())
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type MultiFieldStruct = {"));
        assertTrue(output.contains("name : Optional Text"));
        assertTrue(output.contains("count : Optional Int"));
        assertTrue(output.contains("active : Optional Boolean"));
    }
    
    // ========== @required Trait Tests ==========
    
    @Test
    void generate_requiredField() {
        StringShape stringShape = StringShape.builder()
            .id("test.example#StringType")
            .build();
        modelBuilder.addShape(stringShape);
        
        MemberShape requiredMember = MemberShape.builder()
            .id("test.example#RequiredStruct$bucket")
            .target(stringShape.getId())
            .addTrait(new RequiredTrait())
            .build();
        
        StructureShape structure = StructureShape.builder()
            .id("test.example#RequiredStruct")
            .addMember(requiredMember)
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type RequiredStruct = {"));
        // Required field should NOT be wrapped in Optional
        assertTrue(output.contains("bucket : Text"));
        assertFalse(output.contains("bucket : Optional Text"));
    }
    
    @Test
    void generate_mixedRequiredOptional() {
        StringShape stringShape = StringShape.builder()
            .id("test.example#StringType")
            .build();
        modelBuilder.addShape(stringShape);
        
        MemberShape requiredMember = MemberShape.builder()
            .id("test.example#MixedStruct$bucket")
            .target(stringShape.getId())
            .addTrait(new RequiredTrait())
            .build();
        
        MemberShape optionalMember = MemberShape.builder()
            .id("test.example#MixedStruct$prefix")
            .target(stringShape.getId())
            .build();
        
        StructureShape structure = StructureShape.builder()
            .id("test.example#MixedStruct")
            .addMember(requiredMember)
            .addMember(optionalMember)
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("bucket : Text"));  // Required - no Optional
        assertTrue(output.contains("prefix : Optional Text"));  // Optional
    }
    
    // ========== @default Trait Tests ==========
    
    @Test
    void generate_fieldWithDefault() {
        IntegerShape intShape = IntegerShape.builder()
            .id("test.example#IntType")
            .build();
        modelBuilder.addShape(intShape);
        
        MemberShape defaultMember = MemberShape.builder()
            .id("test.example#DefaultStruct$maxKeys")
            .target(intShape.getId())
            .addTrait(new DefaultTrait(Node.from(1000)))
            .build();
        
        StructureShape structure = StructureShape.builder()
            .id("test.example#DefaultStruct")
            .addMember(defaultMember)
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        // Field with default should NOT be wrapped in Optional
        assertTrue(output.contains("maxKeys : Int"));
        assertFalse(output.contains("maxKeys : Optional Int"));
    }
    
    // ========== @documentation Trait Tests ==========
    
    @Test
    void generate_withDocumentation() {
        StringShape stringShape = StringShape.builder()
            .id("test.example#StringType")
            .build();
        modelBuilder.addShape(stringShape);
        
        StructureShape structure = StructureShape.builder()
            .id("test.example#DocumentedStruct")
            .addMember("name", stringShape.getId())
            .addTrait(new DocumentationTrait("Represents a documented structure."))
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("{{ Represents a documented structure. }}"));
        assertTrue(output.contains("type DocumentedStruct = {"));
    }
    
    // ========== Complex Type Tests ==========
    
    @Test
    void generate_nestedStructure() {
        StringShape stringShape = StringShape.builder()
            .id("test.example#StringType")
            .build();
        modelBuilder.addShape(stringShape);
        
        StructureShape innerStruct = StructureShape.builder()
            .id("test.example#Owner")
            .addMember("name", stringShape.getId())
            .build();
        modelBuilder.addShape(innerStruct);
        
        StructureShape outerStruct = StructureShape.builder()
            .id("test.example#Bucket")
            .addMember("owner", innerStruct.getId())
            .build();
        modelBuilder.addShape(outerStruct);
        
        StructureGenerator generator = createGenerator(outerStruct);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type Bucket = {"));
        assertTrue(output.contains("owner : Optional Owner"));
    }
    
    @Test
    void generate_listField() {
        StringShape stringShape = StringShape.builder()
            .id("test.example#StringType")
            .build();
        modelBuilder.addShape(stringShape);
        
        ListShape listShape = ListShape.builder()
            .id("test.example#StringList")
            .member(stringShape.getId())
            .build();
        modelBuilder.addShape(listShape);
        
        StructureShape structure = StructureShape.builder()
            .id("test.example#ListStruct")
            .addMember("items", listShape.getId())
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("items : Optional [Text]"));
    }
    
    @Test
    void generate_mapField() {
        StringShape stringShape = StringShape.builder()
            .id("test.example#StringType")
            .build();
        modelBuilder.addShape(stringShape);
        
        MapShape mapShape = MapShape.builder()
            .id("test.example#StringMap")
            .key(stringShape.getId())
            .value(stringShape.getId())
            .build();
        modelBuilder.addShape(mapShape);
        
        StructureShape structure = StructureShape.builder()
            .id("test.example#MapStruct")
            .addMember("metadata", mapShape.getId())
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        // Map types are complex, should be wrapped in parens inside Optional
        assertTrue(output.contains("metadata : Optional (Map Text Text)"));
    }
    
    // ========== Helper Method Tests ==========
    
    @Test
    void getTypeName_returnsPascalCase() {
        StructureShape structure = StructureShape.builder()
            .id("test.example#GetObjectInput")
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        assertEquals("GetObjectInput", generator.getTypeName());
    }
    
    @Test
    void isInputStructure_detectsInputSuffix() {
        StructureShape inputStruct = StructureShape.builder()
            .id("test.example#GetObjectInput")
            .build();
        modelBuilder.addShape(inputStruct);
        
        StructureGenerator generator = createGenerator(inputStruct);
        assertTrue(generator.isInputStructure());
        assertFalse(generator.isOutputStructure());
    }
    
    @Test
    void isOutputStructure_detectsOutputSuffix() {
        StructureShape outputStruct = StructureShape.builder()
            .id("test.example#GetObjectOutput")
            .build();
        modelBuilder.addShape(outputStruct);
        
        StructureGenerator generator = createGenerator(outputStruct);
        assertFalse(generator.isInputStructure());
        assertTrue(generator.isOutputStructure());
    }
    
    @Test
    void getRequiredFields_returnsRequiredFieldNames() {
        StringShape stringShape = StringShape.builder()
            .id("test.example#StringType")
            .build();
        modelBuilder.addShape(stringShape);
        
        MemberShape requiredMember = MemberShape.builder()
            .id("test.example#TestStruct$bucket")
            .target(stringShape.getId())
            .addTrait(new RequiredTrait())
            .build();
        
        MemberShape optionalMember = MemberShape.builder()
            .id("test.example#TestStruct$prefix")
            .target(stringShape.getId())
            .build();
        
        StructureShape structure = StructureShape.builder()
            .id("test.example#TestStruct")
            .addMember(requiredMember)
            .addMember(optionalMember)
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        List<String> required = generator.getRequiredFields();
        
        assertEquals(1, required.size());
        assertTrue(required.contains("bucket"));
        assertFalse(required.contains("prefix"));
    }
    
    @Test
    void getDocumentation_returnsDocIfPresent() {
        StructureShape structure = StructureShape.builder()
            .id("test.example#DocStruct")
            .addTrait(new DocumentationTrait("Test documentation"))
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        assertTrue(generator.getDocumentation().isPresent());
        assertEquals("Test documentation", generator.getDocumentation().get());
    }
    
    @Test
    void getDocumentation_returnsEmptyIfNotPresent() {
        StructureShape structure = StructureShape.builder()
            .id("test.example#NoDocStruct")
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        assertFalse(generator.getDocumentation().isPresent());
    }
    
    // ========== S3-Style Structure Test ==========
    
    @Test
    void generate_s3StyleBucket() {
        StringShape stringShape = StringShape.builder()
            .id("test.example#StringType")
            .build();
        TimestampShape timestampShape = TimestampShape.builder()
            .id("test.example#TimestampType")
            .build();
        modelBuilder.addShape(stringShape);
        modelBuilder.addShape(timestampShape);
        
        StructureShape structure = StructureShape.builder()
            .id("test.example#Bucket")
            .addMember("name", stringShape.getId())
            .addMember("creationDate", timestampShape.getId())
            .addTrait(new DocumentationTrait("Represents an S3 bucket."))
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("{{ Represents an S3 bucket. }}"));
        assertTrue(output.contains("type Bucket = {"));
        assertTrue(output.contains("name : Optional Text"));
        assertTrue(output.contains("creationDate : Optional Text"));  // Timestamp → Text
    }
    
    @Test
    void generate_s3StyleGetObjectInput() {
        StringShape stringShape = StringShape.builder()
            .id("test.example#StringType")
            .build();
        modelBuilder.addShape(stringShape);
        
        MemberShape bucketMember = MemberShape.builder()
            .id("test.example#GetObjectInput$bucket")
            .target(stringShape.getId())
            .addTrait(new RequiredTrait())
            .build();
        
        MemberShape keyMember = MemberShape.builder()
            .id("test.example#GetObjectInput$key")
            .target(stringShape.getId())
            .addTrait(new RequiredTrait())
            .build();
        
        MemberShape versionIdMember = MemberShape.builder()
            .id("test.example#GetObjectInput$versionId")
            .target(stringShape.getId())
            .build();
        
        StructureShape structure = StructureShape.builder()
            .id("test.example#GetObjectInput")
            .addMember(bucketMember)
            .addMember(keyMember)
            .addMember(versionIdMember)
            .addTrait(new DocumentationTrait("Input for GetObject operation."))
            .build();
        modelBuilder.addShape(structure);
        
        StructureGenerator generator = createGenerator(structure);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("{{ Input for GetObject operation. }}"));
        assertTrue(output.contains("type GetObjectInput = {"));
        assertTrue(output.contains("bucket : Text"));  // Required
        assertTrue(output.contains("key : Text"));  // Required
        assertTrue(output.contains("versionId : Optional Text"));  // Optional
        
        // Verify it's detected as input structure
        assertTrue(generator.isInputStructure());
    }
}
