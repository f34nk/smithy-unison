package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonWriter;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.DocumentationTrait;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UnionGenerator.
 */
class UnionGeneratorTest {
    
    // ========== Type Definition Tests ==========
    
    @Test
    void generate_simpleUnion() {
        List<UnionGenerator.UnionVariant> variants = List.of(
            new UnionGenerator.UnionVariant("Success", "Text"),
            new UnionGenerator.UnionVariant("Failure", "Text")
        );
        
        UnionGenerator generator = new UnionGenerator("Result", variants, null);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type Result"));
        assertTrue(output.contains("= Result'Success Text"));
        assertTrue(output.contains("| Result'Failure Text"));
    }
    
    @Test
    void generate_withDocumentation() {
        List<UnionGenerator.UnionVariant> variants = List.of(
            new UnionGenerator.UnionVariant("Ok", "a"),
            new UnionGenerator.UnionVariant("Err", "e")
        );
        
        UnionGenerator generator = new UnionGenerator("Either", variants, "Represents a value that can be one of two types.");
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("{{ Represents a value that can be one of two types. }}"));
        assertTrue(output.contains("type Either"));
    }
    
    // ========== S3 Union Tests ==========
    
    @Test
    void generate_s3StorageType() {
        List<UnionGenerator.UnionVariant> variants = List.of(
            new UnionGenerator.UnionVariant("S3", "S3Storage"),
            new UnionGenerator.UnionVariant("Glacier", "GlacierStorage"),
            new UnionGenerator.UnionVariant("Efs", "EfsStorage")
        );
        
        UnionGenerator generator = new UnionGenerator("StorageType", variants, "Storage type can be S3, Glacier, or EFS.");
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type StorageType"));
        assertTrue(output.contains("= StorageType'S3 S3Storage"));
        assertTrue(output.contains("| StorageType'Glacier GlacierStorage"));
        assertTrue(output.contains("| StorageType'Efs EfsStorage"));
    }
    
    @Test
    void generate_s3SelectObjectContentEventStream() {
        List<UnionGenerator.UnionVariant> variants = List.of(
            new UnionGenerator.UnionVariant("Records", "RecordsEvent"),
            new UnionGenerator.UnionVariant("Stats", "StatsEvent"),
            new UnionGenerator.UnionVariant("Progress", "ProgressEvent"),
            new UnionGenerator.UnionVariant("Cont", "ContinuationEvent"),
            new UnionGenerator.UnionVariant("End", "EndEvent")
        );
        
        UnionGenerator generator = new UnionGenerator("SelectObjectContentEventStream", variants, null);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type SelectObjectContentEventStream"));
        assertTrue(output.contains("= SelectObjectContentEventStream'Records RecordsEvent"));
        assertTrue(output.contains("| SelectObjectContentEventStream'Stats StatsEvent"));
        assertTrue(output.contains("| SelectObjectContentEventStream'Progress ProgressEvent"));
        assertTrue(output.contains("| SelectObjectContentEventStream'Cont ContinuationEvent"));
        assertTrue(output.contains("| SelectObjectContentEventStream'End EndEvent"));
    }
    
    // ========== Primitive Payload Tests ==========
    
    @Test
    void generate_withPrimitivePayloads() {
        List<UnionGenerator.UnionVariant> variants = List.of(
            new UnionGenerator.UnionVariant("StringVal", "Text"),
            new UnionGenerator.UnionVariant("IntVal", "Int"),
            new UnionGenerator.UnionVariant("FloatVal", "Float"),
            new UnionGenerator.UnionVariant("BoolVal", "Boolean"),
            new UnionGenerator.UnionVariant("BytesVal", "Bytes")
        );
        
        UnionGenerator generator = new UnionGenerator("DynamicValue", variants, null);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type DynamicValue"));
        assertTrue(output.contains("= DynamicValue'StringVal Text"));
        assertTrue(output.contains("| DynamicValue'IntVal Int"));
        assertTrue(output.contains("| DynamicValue'FloatVal Float"));
        assertTrue(output.contains("| DynamicValue'BoolVal Boolean"));
        assertTrue(output.contains("| DynamicValue'BytesVal Bytes"));
    }
    
    // ========== From Smithy Model Tests ==========
    
    @Test
    void generate_fromSmithyModel() {
        Model.Builder modelBuilder = Model.builder();
        
        // Add target types
        StringShape stringShape = StringShape.builder().id("smithy.api#String").build();
        StructureShape s3Storage = StructureShape.builder().id("test.example#S3Storage").build();
        StructureShape glacierStorage = StructureShape.builder().id("test.example#GlacierStorage").build();
        modelBuilder.addShapes(stringShape, s3Storage, glacierStorage);
        
        // Build union - member names are lowercase in Smithy convention
        UnionShape unionShape = UnionShape.builder()
            .id("test.example#StorageType")
            .addMember("s3", s3Storage.getId())
            .addMember("glacier", glacierStorage.getId())
            .addTrait(new DocumentationTrait("Storage backend type."))
            .build();
        modelBuilder.addShape(unionShape);
        
        Model model = modelBuilder.build();
        
        UnionGenerator generator = new UnionGenerator(unionShape, model);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("{{ Storage backend type. }}"));
        assertTrue(output.contains("type StorageType"));
        // Member names get PascalCased: s3 -> S3, glacier -> Glacier
        assertTrue(output.contains("StorageType'S3 S3Storage"), "Expected StorageType'S3, got: " + output);
        assertTrue(output.contains("StorageType'Glacier GlacierStorage"), "Expected StorageType'Glacier, got: " + output);
    }
    
    @Test
    void generate_fromSmithyModel_withPrimitives() {
        Model.Builder modelBuilder = Model.builder();
        
        // Add primitive targets
        StringShape stringShape = StringShape.builder().id("smithy.api#String").build();
        IntegerShape intShape = IntegerShape.builder().id("smithy.api#Integer").build();
        BooleanShape boolShape = BooleanShape.builder().id("smithy.api#Boolean").build();
        modelBuilder.addShapes(stringShape, intShape, boolShape);
        
        // Build union with primitive members
        UnionShape unionShape = UnionShape.builder()
            .id("test.example#MixedValue")
            .addMember("stringValue", stringShape.getId())
            .addMember("intValue", intShape.getId())
            .addMember("boolValue", boolShape.getId())
            .build();
        modelBuilder.addShape(unionShape);
        
        Model model = modelBuilder.build();
        
        UnionGenerator generator = new UnionGenerator(unionShape, model);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type MixedValue"));
        assertTrue(output.contains("MixedValue'StringValue Text"));
        assertTrue(output.contains("MixedValue'IntValue Int"));
        assertTrue(output.contains("MixedValue'BoolValue Boolean"));
    }
    
    // ========== Helper Method Tests ==========
    
    @Test
    void getTypeName_returnsPascalCase() {
        UnionGenerator generator = new UnionGenerator("StorageType", List.of(), null);
        assertEquals("StorageType", generator.getTypeName());
    }
    
    @Test
    void getVariantName_formatsCorrectly() {
        UnionGenerator generator = new UnionGenerator("StorageType", List.of(), null);
        assertEquals("StorageType'S3", generator.getVariantName("S3"));
        assertEquals("StorageType'Glacier", generator.getVariantName("Glacier"));
    }
    
    @Test
    void getVariants_returnsAllVariants() {
        List<UnionGenerator.UnionVariant> variants = List.of(
            new UnionGenerator.UnionVariant("A", "TypeA"),
            new UnionGenerator.UnionVariant("B", "TypeB"),
            new UnionGenerator.UnionVariant("C", "TypeC")
        );
        
        UnionGenerator generator = new UnionGenerator("TestUnion", variants, null);
        assertEquals(3, generator.getVariants().size());
        assertEquals("A", generator.getVariants().get(0).name());
        assertEquals("TypeA", generator.getVariants().get(0).payloadType());
    }
    
    // ========== UnionVariant Tests ==========
    
    @Test
    void unionVariant_holdsCorrectValues() {
        UnionGenerator.UnionVariant variant = new UnionGenerator.UnionVariant("S3", "S3Storage");
        
        assertEquals("S3", variant.name());
        assertEquals("S3Storage", variant.payloadType());
    }
    
    @Test
    void unionVariant_nullPayload() {
        UnionGenerator.UnionVariant variant = new UnionGenerator.UnionVariant("Empty", null);
        
        assertEquals("Empty", variant.name());
        assertNull(variant.payloadType());
    }
    
    // ========== Edge Cases ==========
    
    @Test
    void generate_singleVariant() {
        List<UnionGenerator.UnionVariant> variants = List.of(
            new UnionGenerator.UnionVariant("Only", "OnlyType")
        );
        
        UnionGenerator generator = new UnionGenerator("SingleVariant", variants, null);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type SingleVariant"));
        assertTrue(output.contains("= SingleVariant'Only OnlyType"));
        // Should not have pipe for single variant
        assertFalse(output.contains("|"));
    }
    
    @Test
    void generate_emptyUnion() {
        UnionGenerator generator = new UnionGenerator("EmptyUnion", List.of(), null);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type EmptyUnion"));
    }
}
