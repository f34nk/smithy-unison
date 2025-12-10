package io.smithy.unison.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UnisonWriter.
 */
class UnisonWriterTest {
    
    private UnisonWriter writer;
    
    @BeforeEach
    void setUp() {
        writer = new UnisonWriter("test.namespace");
    }
    
    // ========== writeDocComment Tests ==========
    
    @Test
    void writeDocComment_singleLine() {
        writer.writeDocComment("This is a simple doc comment.");
        String output = writer.toString();
        assertEquals("{{ This is a simple doc comment. }}\n", output);
    }
    
    @Test
    void writeDocComment_multiLine() {
        writer.writeDocComment("First line.\nSecond line.\nThird line.");
        String output = writer.toString();
        assertTrue(output.contains("{{"));
        assertTrue(output.contains("First line."));
        assertTrue(output.contains("Second line."));
        assertTrue(output.contains("Third line."));
        assertTrue(output.contains("}}"));
    }
    
    @Test
    void writeDocComment_empty() {
        writer.writeDocComment("");
        String output = writer.toString();
        assertEquals("{{}}\n", output);
    }
    
    @Test
    void writeDocComment_null() {
        writer.writeDocComment(null);
        String output = writer.toString();
        assertEquals("{{}}\n", output);
    }
    
    // ========== writeRecordType Tests ==========
    
    @Test
    void writeRecordType_withFields() {
        writer.writeRecordType("GetObjectInput", List.of(
            new UnisonWriter.TypeField("bucket", "Text"),
            new UnisonWriter.TypeField("key", "Text"),
            new UnisonWriter.TypeField("versionId", "Optional Text")
        ));
        String output = writer.toString();
        
        assertTrue(output.contains("type GetObjectInput = {"));
        assertTrue(output.contains("bucket : Text,"));
        assertTrue(output.contains("key : Text,"));
        assertTrue(output.contains("versionId : Optional Text"));
        assertTrue(output.contains("}"));
        // Last field should NOT have comma
        assertFalse(output.contains("Optional Text,"));
    }
    
    @Test
    void writeRecordType_empty() {
        writer.writeRecordType("EmptyRecord", List.of());
        String output = writer.toString();
        // Empty records use simple constructor (Unison doesn't support empty braces)
        assertEquals("type EmptyRecord = EmptyRecord\n\n", output);
    }
    
    @Test
    void writeRecordType_null() {
        writer.writeRecordType("NullRecord", null);
        String output = writer.toString();
        // Empty records use simple constructor (Unison doesn't support empty braces)
        assertEquals("type NullRecord = NullRecord\n\n", output);
    }
    
    @Test
    void writeRecordType_singleField() {
        writer.writeRecordType("SingleField", List.of(
            new UnisonWriter.TypeField("name", "Text")
        ));
        String output = writer.toString();
        
        assertTrue(output.contains("type SingleField = {"));
        assertTrue(output.contains("name : Text"));
        // Single field should not have comma
        assertFalse(output.contains("Text,"));
    }
    
    // ========== writeUnionType Tests ==========
    
    @Test
    void writeUnionType_withTypeParams() {
        writer.writeUnionType("S3Response", "a", List.of(
            new UnisonWriter.Variant("S3Success", "a"),
            new UnisonWriter.Variant("S3Error", "{ code : Text, message : Text }")
        ));
        String output = writer.toString();
        
        assertTrue(output.contains("type S3Response a"));
        assertTrue(output.contains("= S3Success a"));
        assertTrue(output.contains("| S3Error { code : Text, message : Text }"));
    }
    
    @Test
    void writeUnionType_simpleEnum() {
        writer.writeUnionType("Status", List.of(
            new UnisonWriter.Variant("Active"),
            new UnisonWriter.Variant("Inactive"),
            new UnisonWriter.Variant("Pending")
        ));
        String output = writer.toString();
        
        assertTrue(output.contains("type Status"));
        assertTrue(output.contains("= Active"));
        assertTrue(output.contains("| Inactive"));
        assertTrue(output.contains("| Pending"));
    }
    
    @Test
    void writeUnionType_singleVariant() {
        writer.writeUnionType("Wrapper", "a", List.of(
            new UnisonWriter.Variant("Wrap", "a")
        ));
        String output = writer.toString();
        
        assertTrue(output.contains("type Wrapper a"));
        assertTrue(output.contains("= Wrap a"));
        assertFalse(output.contains("|"));
    }
    
    @Test
    void writeUnionType_empty() {
        writer.writeUnionType("Empty", "a", List.of());
        String output = writer.toString();
        assertTrue(output.contains("type Empty a"));
    }
    
    // ========== writeSignature Tests ==========
    
    @Test
    void writeSignature_simple() {
        writer.writeSignature("getObject", "S3Config -> GetObjectInput -> '{IO, Exception} GetObjectOutput");
        String output = writer.toString();
        assertEquals("getObject : S3Config -> GetObjectInput -> '{IO, Exception} GetObjectOutput\n", output);
    }
    
    @Test
    void writeSignature_noParams() {
        writer.writeSignature("listBuckets", "S3Config -> '{IO, Exception} [Bucket]");
        String output = writer.toString();
        assertEquals("listBuckets : S3Config -> '{IO, Exception} [Bucket]\n", output);
    }
    
    // ========== writeFunction Tests ==========
    
    @Test
    void writeFunction_withParams() {
        writer.writeFunction("getObject", "config input", () -> {
            writer.write("Http.get (buildUrl config input)");
        });
        String output = writer.toString();
        
        assertTrue(output.contains("getObject config input ="));
        assertTrue(output.contains("Http.get (buildUrl config input)"));
    }
    
    @Test
    void writeFunction_noParams() {
        writer.writeFunction("getTimestamp", "", () -> {
            writer.write("Clock.now");
        });
        String output = writer.toString();
        
        assertTrue(output.contains("getTimestamp ="));
        assertTrue(output.contains("Clock.now"));
    }
    
    @Test
    void writeFunction_nullParams() {
        writer.writeFunction("constant", null, () -> {
            writer.write("42");
        });
        String output = writer.toString();
        
        assertTrue(output.contains("constant ="));
        assertTrue(output.contains("42"));
    }
    
    @Test
    void writeFunctionWithThunk() {
        writer.writeFunctionWithThunk("performIO", "config", () -> {
            writer.write("doSomething config");
        });
        String output = writer.toString();
        
        assertTrue(output.contains("performIO config _ ="));
    }
    
    // ========== writeMatch Tests ==========
    
    @Test
    void writeMatch_simpleCases() {
        writer.writeMatch("response", List.of(
            new UnisonWriter.MatchCase("Some x", "x"),
            new UnisonWriter.MatchCase("None", "defaultValue")
        ));
        String output = writer.toString();
        
        assertTrue(output.contains("match response with"));
        assertTrue(output.contains("Some x -> x"));
        assertTrue(output.contains("None -> defaultValue"));
    }
    
    @Test
    void writeMatch_empty() {
        writer.writeMatch("value", List.of());
        String output = writer.toString();
        
        assertTrue(output.contains("match value with"));
        assertTrue(output.contains("_ -> ()"));
    }
    
    @Test
    void writeMatch_null() {
        writer.writeMatch("value", null);
        String output = writer.toString();
        
        assertTrue(output.contains("match value with"));
        assertTrue(output.contains("_ -> ()"));
    }
    
    @Test
    void writeMatchWithBodies() {
        writer.writeMatchWithBodies("status", List.of(
            new UnisonWriter.MatchCaseWithBody("200", () -> {
                writer.write("parseSuccess body");
            }),
            new UnisonWriter.MatchCaseWithBody("code", () -> {
                writer.write("parseError code body");
            })
        ));
        String output = writer.toString();
        
        assertTrue(output.contains("match status with"));
        assertTrue(output.contains("200 ->"));
        assertTrue(output.contains("parseSuccess body"));
        assertTrue(output.contains("code ->"));
        assertTrue(output.contains("parseError code body"));
    }
    
    // ========== writeLet Tests ==========
    
    @Test
    void writeLet_simple() {
        writer.writeLet(
            () -> {
                writer.write("x = 1");
                writer.write("y = 2");
            },
            () -> writer.write("x + y")
        );
        String output = writer.toString();
        
        assertTrue(output.contains("let"));
        assertTrue(output.contains("x = 1"));
        assertTrue(output.contains("y = 2"));
        assertTrue(output.contains("x + y"));
    }
    
    // ========== writeComment Tests ==========
    
    @Test
    void writeComment_simple() {
        writer.writeComment("This is a comment");
        String output = writer.toString();
        assertEquals("-- This is a comment\n", output);
    }
    
    // ========== Integration Tests ==========
    
    @Test
    void fullFunctionDefinition() {
        writer.writeDocComment("Gets an object from S3.");
        writer.writeSignature("getObject", "S3Config -> GetObjectInput -> '{IO, Exception} GetObjectOutput");
        writer.writeFunction("getObject", "config input", () -> {
            writer.writeLet(
                () -> {
                    writer.write("url = buildUrl config input");
                    writer.write("headers = buildHeaders config");
                },
                () -> {
                    writer.writeMatch("Http.get url headers", List.of(
                        new UnisonWriter.MatchCase("Right response", "parseResponse response"),
                        new UnisonWriter.MatchCase("Left err", "abort (Exception.raise err)")
                    ));
                }
            );
        });
        
        String output = writer.toString();
        
        // Verify structure
        assertTrue(output.contains("{{ Gets an object from S3. }}"));
        assertTrue(output.contains("getObject : S3Config"));
        assertTrue(output.contains("getObject config input ="));
        assertTrue(output.contains("let"));
        assertTrue(output.contains("url = buildUrl config input"));
        assertTrue(output.contains("match Http.get url headers with"));
    }
    
    // ========== Factory Tests ==========
    
    @Test
    void factory_createsWriter() {
        UnisonWriter.Factory factory = UnisonWriter.factory();
        UnisonWriter w = factory.apply("test.u", "test.namespace");
        
        assertNotNull(w);
        assertEquals("test", w.getNamespace());
    }
    
    @Test
    void factory_handlesFilenameWithoutExtension() {
        UnisonWriter.Factory factory = UnisonWriter.factory();
        UnisonWriter w = factory.apply("mymodule", "test");
        
        assertEquals("mymodule", w.getNamespace());
    }
    
    // ========== Enum Helper Tests ==========
    
    @Test
    void writeEnumToTextFunction() {
        writer.writeEnumToTextFunction("BucketLocation", List.of(
            new UnisonWriter.EnumMapping("UsEast1", "us-east-1"),
            new UnisonWriter.EnumMapping("UsWest2", "us-west-2")
        ));
        String output = writer.toString();
        
        assertTrue(output.contains("bucketLocationToText : BucketLocation -> Text"));
        assertTrue(output.contains("bucketLocationToText val ="));
        assertTrue(output.contains("match val with"));
        assertTrue(output.contains("BucketLocation'UsEast1 -> \"us-east-1\""));
        assertTrue(output.contains("BucketLocation'UsWest2 -> \"us-west-2\""));
    }
    
    @Test
    void writeEnumFromTextFunction() {
        writer.writeEnumFromTextFunction("BucketLocation", List.of(
            new UnisonWriter.EnumMapping("UsEast1", "us-east-1"),
            new UnisonWriter.EnumMapping("UsWest2", "us-west-2")
        ));
        String output = writer.toString();
        
        assertTrue(output.contains("bucketLocationFromText : Text -> Optional BucketLocation"));
        assertTrue(output.contains("bucketLocationFromText t ="));
        assertTrue(output.contains("\"us-east-1\" -> Some BucketLocation'UsEast1"));
        assertTrue(output.contains("\"us-west-2\" -> Some BucketLocation'UsWest2"));
        assertTrue(output.contains("_ -> None"));
    }
}
