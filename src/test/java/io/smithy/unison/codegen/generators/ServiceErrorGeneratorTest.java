package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonWriter;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.HttpErrorTrait;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServiceErrorGenerator.
 */
class ServiceErrorGeneratorTest {
    
    // ========== Type Definition Tests ==========
    
    @Test
    void generateTypeDefinition_simpleService() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("NotFound", "NotFound"),
            new ServiceErrorGenerator.ErrorVariant("AccessDenied", "AccessDenied")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Example", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateTypeDefinition(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type ExampleServiceError"));
        assertTrue(output.contains("= ExampleServiceError'NotFound NotFound"));
        assertTrue(output.contains("| ExampleServiceError'AccessDenied AccessDenied"));
        assertTrue(output.contains("| ExampleServiceError'UnknownError Text"));
    }
    
    @Test
    void generateTypeDefinition_withDocumentation() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("Error1", "Error1")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Test", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateTypeDefinition(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("Aggregated error type for Test service"));
        assertTrue(output.contains("Use pattern matching to handle specific error types"));
    }
    
    @Test
    void generateTypeDefinition_s3Style() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("NoSuchBucket", "NoSuchBucket"),
            new ServiceErrorGenerator.ErrorVariant("NoSuchKey", "NoSuchKey"),
            new ServiceErrorGenerator.ErrorVariant("BucketAlreadyExists", "BucketAlreadyExists"),
            new ServiceErrorGenerator.ErrorVariant("AccessDenied", "AccessDenied")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("S3", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateTypeDefinition(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type S3ServiceError"));
        assertTrue(output.contains("= S3ServiceError'NoSuchBucket NoSuchBucket"));
        assertTrue(output.contains("| S3ServiceError'NoSuchKey NoSuchKey"));
        assertTrue(output.contains("| S3ServiceError'BucketAlreadyExists BucketAlreadyExists"));
        assertTrue(output.contains("| S3ServiceError'AccessDenied AccessDenied"));
        assertTrue(output.contains("| S3ServiceError'UnknownError Text"));
    }
    
    // ========== toFailure Function Tests ==========
    
    @Test
    void generateToFailure_simpleService() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("NotFound", "NotFound"),
            new ServiceErrorGenerator.ErrorVariant("AccessDenied", "AccessDenied")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Example", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateToFailureFunction(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("ExampleServiceError.toFailure : ExampleServiceError -> IO.Failure"));
        assertTrue(output.contains("ExampleServiceError.toFailure = cases"));
        assertTrue(output.contains("ExampleServiceError'NotFound e -> NotFound.toFailure e"));
        assertTrue(output.contains("ExampleServiceError'AccessDenied e -> AccessDenied.toFailure e"));
        assertTrue(output.contains("ExampleServiceError'UnknownError msg -> IO.Failure.Failure (typeLink Text) msg (Any msg)"));
    }
    
    @Test
    void generateToFailure_s3Style() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("NoSuchBucket", "NoSuchBucket"),
            new ServiceErrorGenerator.ErrorVariant("NoSuchKey", "NoSuchKey")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("S3", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateToFailureFunction(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("S3ServiceError.toFailure : S3ServiceError -> IO.Failure"));
        assertTrue(output.contains("S3ServiceError'NoSuchBucket e -> NoSuchBucket.toFailure e"));
        assertTrue(output.contains("S3ServiceError'NoSuchKey e -> NoSuchKey.toFailure e"));
    }
    
    // ========== Complete Generation Tests ==========
    
    @Test
    void generate_complete() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("ResourceNotFound", "ResourceNotFound"),
            new ServiceErrorGenerator.ErrorVariant("ValidationError", "ValidationError"),
            new ServiceErrorGenerator.ErrorVariant("InternalError", "InternalError")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("MyApi", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        
        // Type definition
        assertTrue(output.contains("type MyApiServiceError"));
        assertTrue(output.contains("MyApiServiceError'ResourceNotFound ResourceNotFound"));
        assertTrue(output.contains("MyApiServiceError'ValidationError ValidationError"));
        assertTrue(output.contains("MyApiServiceError'InternalError InternalError"));
        assertTrue(output.contains("MyApiServiceError'UnknownError Text"));
        
        // toFailure function
        assertTrue(output.contains("MyApiServiceError.toFailure : MyApiServiceError -> IO.Failure"));
        assertTrue(output.contains("MyApiServiceError'ResourceNotFound e -> ResourceNotFound.toFailure e"));
        assertTrue(output.contains("MyApiServiceError'UnknownError msg -> IO.Failure.Failure (typeLink Text) msg (Any msg)"));
    }
    
    // ========== From Smithy Model Tests ==========
    
    @Test
    void generate_fromSmithyModel() {
        Model.Builder modelBuilder = Model.builder();
        
        // Add error shapes
        StructureShape notFoundError = StructureShape.builder()
            .id("test.example#NotFoundError")
            .addTrait(new ErrorTrait("client"))
            .addTrait(new HttpErrorTrait(404))
            .build();
        
        StructureShape accessDeniedError = StructureShape.builder()
            .id("test.example#AccessDeniedError")
            .addTrait(new ErrorTrait("client"))
            .addTrait(new HttpErrorTrait(403))
            .build();
        
        modelBuilder.addShapes(notFoundError, accessDeniedError);
        
        // Add operation with errors
        OperationShape operation = OperationShape.builder()
            .id("test.example#GetResource")
            .addError(notFoundError.getId())
            .addError(accessDeniedError.getId())
            .build();
        modelBuilder.addShape(operation);
        
        // Add service
        ServiceShape service = ServiceShape.builder()
            .id("test.example#TestService")
            .version("1.0")
            .addOperation(operation.getId())
            .build();
        modelBuilder.addShape(service);
        
        Model model = modelBuilder.build();
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator(service, model);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("type TestServiceServiceError"));
        assertTrue(output.contains("NotFoundError"));
        assertTrue(output.contains("AccessDeniedError"));
        assertTrue(output.contains("UnknownError Text"));
    }
    
    @Test
    void generate_fromSmithyModel_multipleOperations() {
        Model.Builder modelBuilder = Model.builder();
        
        // Add error shapes
        StructureShape error1 = StructureShape.builder()
            .id("test.example#Error1")
            .addTrait(new ErrorTrait("client"))
            .build();
        
        StructureShape error2 = StructureShape.builder()
            .id("test.example#Error2")
            .addTrait(new ErrorTrait("client"))
            .build();
        
        StructureShape error3 = StructureShape.builder()
            .id("test.example#Error3")
            .addTrait(new ErrorTrait("server"))
            .build();
        
        modelBuilder.addShapes(error1, error2, error3);
        
        // Add operations - some share errors
        OperationShape op1 = OperationShape.builder()
            .id("test.example#Op1")
            .addError(error1.getId())
            .addError(error2.getId())
            .build();
        
        OperationShape op2 = OperationShape.builder()
            .id("test.example#Op2")
            .addError(error2.getId())  // Duplicate - should only appear once
            .addError(error3.getId())
            .build();
        
        modelBuilder.addShapes(op1, op2);
        
        // Add service
        ServiceShape service = ServiceShape.builder()
            .id("test.example#MultiOpService")
            .version("1.0")
            .addOperation(op1.getId())
            .addOperation(op2.getId())
            .build();
        modelBuilder.addShape(service);
        
        Model model = modelBuilder.build();
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator(service, model);
        
        // Should have 3 unique error variants (not 4)
        assertEquals(3, generator.getErrorVariants().size());
    }
    
    // ========== Helper Method Tests ==========
    
    @Test
    void getServiceName_returnsCorrectly() {
        ServiceErrorGenerator generator = new ServiceErrorGenerator("S3", List.of());
        assertEquals("S3", generator.getServiceName());
    }
    
    @Test
    void getTypeName_returnsCorrectly() {
        ServiceErrorGenerator generator = new ServiceErrorGenerator("S3", List.of());
        assertEquals("S3ServiceError", generator.getTypeName());
    }
    
    @Test
    void getVariantName_formatsCorrectly() {
        ServiceErrorGenerator generator = new ServiceErrorGenerator("S3", List.of());
        assertEquals("S3ServiceError'NoSuchBucket", generator.getVariantName("NoSuchBucket"));
        assertEquals("S3ServiceError'AccessDenied", generator.getVariantName("AccessDenied"));
    }
    
    @Test
    void getErrorVariants_returnsAllVariants() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("A", "A"),
            new ServiceErrorGenerator.ErrorVariant("B", "B"),
            new ServiceErrorGenerator.ErrorVariant("C", "C")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Test", variants);
        assertEquals(3, generator.getErrorVariants().size());
        assertEquals("A", generator.getErrorVariants().get(0).variantName());
        assertEquals("A", generator.getErrorVariants().get(0).errorTypeName());
    }
    
    // ========== ErrorVariant Tests ==========
    
    @Test
    void errorVariant_holdsCorrectValues() {
        ServiceErrorGenerator.ErrorVariant variant = new ServiceErrorGenerator.ErrorVariant(
            "NoSuchBucket", "NoSuchBucket"
        );
        
        assertEquals("NoSuchBucket", variant.variantName());
        assertEquals("NoSuchBucket", variant.errorTypeName());
    }
    
    // ========== fromCodeAndMessage Tests ==========
    
    @Test
    void generateFromCodeAndMessage_simpleService() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("NotFound", "NotFound"),
            new ServiceErrorGenerator.ErrorVariant("AccessDenied", "AccessDenied")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Example", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateFromCodeAndMessageFunction(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("ExampleServiceError.fromCodeAndMessage : Text -> Text -> ExampleServiceError"));
        assertTrue(output.contains("ExampleServiceError.fromCodeAndMessage code message = match code with"));
        assertTrue(output.contains("\"NotFound\" -> ExampleServiceError'NotFound { message }"));
        assertTrue(output.contains("\"AccessDenied\" -> ExampleServiceError'AccessDenied { message }"));
        assertTrue(output.contains("_ -> ExampleServiceError'UnknownError (code ++ \": \" ++ message)"));
    }
    
    @Test
    void generateFromCodeAndMessage_s3Style() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("NoSuchBucket", "NoSuchBucket"),
            new ServiceErrorGenerator.ErrorVariant("NoSuchKey", "NoSuchKey")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("S3", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateFromCodeAndMessageFunction(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("S3ServiceError.fromCodeAndMessage : Text -> Text -> S3ServiceError"));
        assertTrue(output.contains("\"NoSuchBucket\" -> S3ServiceError'NoSuchBucket { message }"));
        assertTrue(output.contains("\"NoSuchKey\" -> S3ServiceError'NoSuchKey { message }"));
    }
    
    // ========== fromXml Tests ==========
    
    @Test
    void generateFromXml_s3Style() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("NoSuchBucket", "NoSuchBucket")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("S3", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateFromXmlFunction(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("S3ServiceError.fromXml : Text -> S3ServiceError"));
        assertTrue(output.contains("S3ServiceError.fromXml xmlText ="));
        assertTrue(output.contains("code = extractXmlTag \"Code\" xmlText"));
        assertTrue(output.contains("message = extractXmlTag \"Message\" xmlText"));
        assertTrue(output.contains("S3ServiceError.fromCodeAndMessage code message"));
    }
    
    @Test
    void generateFromXml_documentation() {
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Test", List.of());
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateFromXmlFunction(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("Parse XML error response"));
        assertTrue(output.contains("<Error><Code>"));
    }
    
    // ========== fromJson Tests ==========
    
    @Test
    void generateFromJson_simpleService() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("ValidationError", "ValidationError")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Api", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateFromJsonFunction(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("ApiServiceError.fromJson : Text -> ApiServiceError"));
        assertTrue(output.contains("ApiServiceError.fromJson jsonText ="));
        assertTrue(output.contains("code = extractJsonField \"__type\" jsonText"));
        assertTrue(output.contains("message = extractJsonField \"message\" jsonText"));
        assertTrue(output.contains("ApiServiceError.fromCodeAndMessage code message"));
    }
    
    @Test
    void generateFromJson_documentation() {
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Test", List.of());
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateFromJsonFunction(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("Parse JSON error response"));
        assertTrue(output.contains("__type"));
    }
    
    // ========== Protocol-specific Generation Tests ==========
    
    @Test
    void generate_withXmlProtocol() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("NoSuchBucket", "NoSuchBucket")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("S3", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer, "xml");
        
        String output = writer.toString();
        // Should have type, toFailure, fromCodeAndMessage, fromXml, and handleHttpResponse
        assertTrue(output.contains("type S3ServiceError"));
        assertTrue(output.contains("S3ServiceError.toFailure"));
        assertTrue(output.contains("S3ServiceError.fromCodeAndMessage"));
        assertTrue(output.contains("S3ServiceError.fromXml"));
        assertTrue(output.contains("handleS3HttpResponse"));
        // Should NOT have fromJson
        assertFalse(output.contains("fromJson"));
    }
    
    @Test
    void generate_withJsonProtocol() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("ValidationError", "ValidationError")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Api", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer, "json");
        
        String output = writer.toString();
        // Should have type, toFailure, fromCodeAndMessage, fromJson, and handleHttpResponse
        assertTrue(output.contains("type ApiServiceError"));
        assertTrue(output.contains("ApiServiceError.toFailure"));
        assertTrue(output.contains("ApiServiceError.fromCodeAndMessage"));
        assertTrue(output.contains("ApiServiceError.fromJson"));
        assertTrue(output.contains("handleApiHttpResponse"));
        // Should NOT have fromXml
        assertFalse(output.contains("fromXml"));
    }
    
    // ========== handleHttpResponse Tests ==========
    
    @Test
    void generateHandleHttpResponse_xml() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("NoSuchBucket", "NoSuchBucket")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("S3", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateHandleHttpResponseFunction(writer, "xml");
        
        String output = writer.toString();
        assertTrue(output.contains("handleS3HttpResponse : HttpResponse ->{Exception} ()"));
        assertTrue(output.contains("handleS3HttpResponse response ="));
        assertTrue(output.contains("statusCode = HttpResponse.status response |> Status.code"));
        assertTrue(output.contains("when (statusCode < 200 || statusCode >= 300) do"));
        assertTrue(output.contains("errorBody = HttpResponse.body response |> bodyText"));
        assertTrue(output.contains("serviceError = S3ServiceError.fromXml errorBody"));
        assertTrue(output.contains("Exception.raise (S3ServiceError.toFailure serviceError)"));
    }
    
    @Test
    void generateHandleHttpResponse_json() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("ValidationError", "ValidationError")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Api", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateHandleHttpResponseFunction(writer, "json");
        
        String output = writer.toString();
        assertTrue(output.contains("handleApiHttpResponse : HttpResponse ->{Exception} ()"));
        assertTrue(output.contains("serviceError = ApiServiceError.fromJson errorBody"));
        assertTrue(output.contains("Exception.raise (ApiServiceError.toFailure serviceError)"));
    }
    
    @Test
    void generateHandleHttpResponse_documentation() {
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Test", List.of());
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateHandleHttpResponseFunction(writer, "xml");
        
        String output = writer.toString();
        assertTrue(output.contains("Handle HTTP response for Test service"));
        assertTrue(output.contains("Checks status code and raises exception on error"));
        assertTrue(output.contains("On success (2xx), does nothing"));
    }
    
    @Test
    void generateHandleHttpResponseXml_convenience() {
        ServiceErrorGenerator generator = new ServiceErrorGenerator("S3", List.of());
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateHandleHttpResponseXml(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("handleS3HttpResponse"));
        assertTrue(output.contains("fromXml"));
    }
    
    @Test
    void generateHandleHttpResponseJson_convenience() {
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Api", List.of());
        UnisonWriter writer = new UnisonWriter("test");
        generator.generateHandleHttpResponseJson(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("handleApiHttpResponse"));
        assertTrue(output.contains("fromJson"));
    }
    
    @Test
    void generate_defaultIncludesFromCodeAndMessage() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("Error1", "Error1")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Test", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);  // No protocol specified
        
        String output = writer.toString();
        assertTrue(output.contains("TestServiceError.fromCodeAndMessage"));
        // Should NOT have protocol-specific functions
        assertFalse(output.contains("fromXml"));
        assertFalse(output.contains("fromJson"));
    }
    
    // ========== Edge Cases ==========
    
    @Test
    void generate_emptyErrorList() {
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Empty", List.of());
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        // Should still have UnknownError variant
        assertTrue(output.contains("type EmptyServiceError"));
        assertTrue(output.contains("EmptyServiceError'UnknownError Text"));
        // fromCodeAndMessage should handle unknown codes
        assertTrue(output.contains("_ -> EmptyServiceError'UnknownError"));
    }
    
    @Test
    void generate_singleError() {
        List<ServiceErrorGenerator.ErrorVariant> variants = List.of(
            new ServiceErrorGenerator.ErrorVariant("OnlyError", "OnlyError")
        );
        
        ServiceErrorGenerator generator = new ServiceErrorGenerator("Single", variants);
        UnisonWriter writer = new UnisonWriter("test");
        generator.generate(writer);
        
        String output = writer.toString();
        assertTrue(output.contains("SingleServiceError'OnlyError OnlyError"));
        assertTrue(output.contains("SingleServiceError'UnknownError Text"));
    }
}
