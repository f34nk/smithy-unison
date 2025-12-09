package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonWriter;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.HttpErrorTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ErrorGenerator.
 */
class ErrorGeneratorTest {
    
    // ========== Type Definition Tests ==========
    
    @Test
    void generate_emptyError() {
        ErrorGenerator generator = new ErrorGenerator(
            "NoSuchBucket",
            "client",
            404,
            "The specified bucket does not exist.",
            List.of()
        );
        
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("The specified bucket does not exist."));
        assertTrue(output.contains("Error category: client"));
        assertTrue(output.contains("HTTP status: 404"));
        assertTrue(output.contains("type NoSuchBucket = { }"));
    }
    
    @Test
    void generate_errorWithFields() {
        List<ErrorGenerator.ErrorField> fields = List.of(
            new ErrorGenerator.ErrorField("message", "Text", false),
            new ErrorGenerator.ErrorField("code", "Text", false)
        );
        
        ErrorGenerator generator = new ErrorGenerator(
            "ValidationError",
            "client",
            400,
            "A validation error occurred.",
            fields
        );
        
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("A validation error occurred."));
        assertTrue(output.contains("Error category: client"));
        assertTrue(output.contains("HTTP status: 400"));
        assertTrue(output.contains("type ValidationError = {"));
        assertTrue(output.contains("message : Optional Text"));
        assertTrue(output.contains("code : Optional Text"));
    }
    
    @Test
    void generate_errorWithRequiredFields() {
        List<ErrorGenerator.ErrorField> fields = List.of(
            new ErrorGenerator.ErrorField("message", "Text", true),
            new ErrorGenerator.ErrorField("requestId", "Text", false)
        );
        
        ErrorGenerator generator = new ErrorGenerator(
            "InternalError",
            "server",
            500,
            "An internal server error occurred.",
            fields
        );
        
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("Error category: server"));
        assertTrue(output.contains("HTTP status: 500"));
        assertTrue(output.contains("message : Text"));
        assertTrue(output.contains("requestId : Optional Text"));
    }
    
    @Test
    void generate_errorWithoutHttpStatus() {
        ErrorGenerator generator = new ErrorGenerator(
            "NotFound",
            "client",
            null,
            "The specified content does not exist.",
            List.of()
        );
        
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("Error category: client"));
        assertFalse(output.contains("HTTP status:"));
        assertTrue(output.contains("type NotFound = { }"));
    }
    
    // ========== S3 Error Tests ==========
    
    @Test
    void generate_s3NoSuchBucket() {
        ErrorGenerator generator = new ErrorGenerator(
            "NoSuchBucket",
            "client",
            404,
            "The specified bucket does not exist.",
            List.of()
        );
        
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type NoSuchBucket = { }"));
        assertTrue(output.contains("Error category: client"));
        assertTrue(output.contains("HTTP status: 404"));
    }
    
    @Test
    void generate_s3NoSuchKey() {
        ErrorGenerator generator = new ErrorGenerator(
            "NoSuchKey",
            "client",
            404,
            "The specified key does not exist.",
            List.of()
        );
        
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type NoSuchKey = { }"));
    }
    
    @Test
    void generate_s3BucketAlreadyExists() {
        ErrorGenerator generator = new ErrorGenerator(
            "BucketAlreadyExists",
            "client",
            409,
            "The requested bucket name is not available.",
            List.of()
        );
        
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type BucketAlreadyExists = { }"));
        assertTrue(output.contains("HTTP status: 409"));
    }
    
    @Test
    void generate_s3InvalidObjectState() {
        // InvalidObjectState has members
        List<ErrorGenerator.ErrorField> fields = List.of(
            new ErrorGenerator.ErrorField("storageClass", "Text", false),
            new ErrorGenerator.ErrorField("accessTier", "Text", false)
        );
        
        ErrorGenerator generator = new ErrorGenerator(
            "InvalidObjectState",
            "client",
            403,
            "Object is archived and inaccessible until restored.",
            fields
        );
        
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type InvalidObjectState = {"));
        assertTrue(output.contains("storageClass : Optional Text"));
        assertTrue(output.contains("accessTier : Optional Text"));
        assertTrue(output.contains("HTTP status: 403"));
    }
    
    // ========== From Smithy Model Tests ==========
    
    @Test
    void generate_fromSmithyModel_emptyError() {
        Model.Builder modelBuilder = Model.builder();
        
        StructureShape errorShape = StructureShape.builder()
            .id("test.example#NoSuchBucket")
            .addTrait(new ErrorTrait("client"))
            .addTrait(new HttpErrorTrait(404))
            .addTrait(new DocumentationTrait("The specified bucket does not exist."))
            .build();
        modelBuilder.addShape(errorShape);
        
        Model model = modelBuilder.build();
        
        ErrorGenerator generator = new ErrorGenerator(errorShape, model);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type NoSuchBucket = { }"));
        assertTrue(output.contains("Error category: client"));
        assertTrue(output.contains("HTTP status: 404"));
        assertTrue(output.contains("The specified bucket does not exist."));
    }
    
    @Test
    void generate_fromSmithyModel_withFields() {
        Model.Builder modelBuilder = Model.builder();
        
        // Add target types
        StringShape stringShape = StringShape.builder().id("smithy.api#String").build();
        modelBuilder.addShape(stringShape);
        
        // Build error with members
        StructureShape errorShape = StructureShape.builder()
            .id("test.example#ValidationError")
            .addMember("message", stringShape.getId())
            .addTrait(new ErrorTrait("client"))
            .addTrait(new HttpErrorTrait(400))
            .addTrait(new DocumentationTrait("A validation error occurred."))
            .build();
        modelBuilder.addShape(errorShape);
        
        Model model = modelBuilder.build();
        
        ErrorGenerator generator = new ErrorGenerator(errorShape, model);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type ValidationError = {"));
        assertTrue(output.contains("message : Optional Text"));
    }
    
    @Test
    void constructor_throwsForNonErrorShape() {
        Model.Builder modelBuilder = Model.builder();
        
        StructureShape nonErrorShape = StructureShape.builder()
            .id("test.example#RegularStructure")
            .build();
        modelBuilder.addShape(nonErrorShape);
        
        Model model = modelBuilder.build();
        
        assertThrows(IllegalArgumentException.class, () -> {
            new ErrorGenerator(nonErrorShape, model);
        });
    }
    
    // ========== Helper Method Tests ==========
    
    @Test
    void getTypeName_returnsPascalCase() {
        ErrorGenerator generator = new ErrorGenerator(
            "NoSuchBucket", "client", 404, null, List.of()
        );
        assertEquals("NoSuchBucket", generator.getTypeName());
    }
    
    @Test
    void getErrorCategory_returnsCorrectCategory() {
        ErrorGenerator clientError = new ErrorGenerator(
            "ClientError", "client", 400, null, List.of()
        );
        ErrorGenerator serverError = new ErrorGenerator(
            "ServerError", "server", 500, null, List.of()
        );
        
        assertEquals("client", clientError.getErrorCategory());
        assertEquals("server", serverError.getErrorCategory());
    }
    
    @Test
    void isClientError_returnsCorrectValue() {
        ErrorGenerator clientError = new ErrorGenerator(
            "ClientError", "client", 400, null, List.of()
        );
        ErrorGenerator serverError = new ErrorGenerator(
            "ServerError", "server", 500, null, List.of()
        );
        
        assertTrue(clientError.isClientError());
        assertFalse(serverError.isClientError());
    }
    
    @Test
    void getHttpStatusCode_returnsCorrectValue() {
        ErrorGenerator with404 = new ErrorGenerator(
            "NotFound", "client", 404, null, List.of()
        );
        ErrorGenerator withNull = new ErrorGenerator(
            "Unknown", "client", null, null, List.of()
        );
        
        assertEquals(404, with404.getHttpStatusCode());
        assertNull(withNull.getHttpStatusCode());
    }
    
    @Test
    void getFields_returnsAllFields() {
        List<ErrorGenerator.ErrorField> fields = List.of(
            new ErrorGenerator.ErrorField("message", "Text", true),
            new ErrorGenerator.ErrorField("code", "Text", false)
        );
        
        ErrorGenerator generator = new ErrorGenerator(
            "TestError", "client", 400, null, fields
        );
        
        assertEquals(2, generator.getFields().size());
        assertEquals("message", generator.getFields().get(0).name());
        assertEquals("Text", generator.getFields().get(0).type());
        assertTrue(generator.getFields().get(0).required());
    }
    
    // ========== ErrorField Tests ==========
    
    @Test
    void errorField_holdCorrectValues() {
        ErrorGenerator.ErrorField field = new ErrorGenerator.ErrorField(
            "errorCode", "Int", true
        );
        
        assertEquals("errorCode", field.name());
        assertEquals("Int", field.type());
        assertTrue(field.required());
    }
}
