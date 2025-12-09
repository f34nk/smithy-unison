package io.smithy.unison.codegen.protocols;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.*;
import software.amazon.smithy.model.pattern.UriPattern;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HTTP binding trait handling in ProtocolUtils.
 * 
 * Tests the following traits:
 * - @http (method, URI, status code)
 * - @httpLabel (path parameters)
 * - @httpQuery (query parameters)
 * - @httpHeader (request/response headers)
 * - @httpPayload (raw body)
 * - @httpResponseCode (status code binding)
 */
public class HttpBindingTraitsTest {
    
    // ========== @http Trait Tests ==========
    
    @Test
    void testGetHttpMethod_withHttpTrait() {
        OperationShape operation = OperationShape.builder()
                .id("com.example#GetObject")
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/{Bucket}/{Key}"))
                        .code(200)
                        .build())
                .build();
        
        assertEquals("GET", ProtocolUtils.getHttpMethod(operation, "POST"));
    }
    
    @Test
    void testGetHttpMethod_withoutHttpTrait() {
        OperationShape operation = OperationShape.builder()
                .id("com.example#GetObject")
                .build();
        
        assertEquals("POST", ProtocolUtils.getHttpMethod(operation, "POST"));
    }
    
    @Test
    void testGetHttpUri_withHttpTrait() {
        OperationShape operation = OperationShape.builder()
                .id("com.example#GetObject")
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/{Bucket}/{Key+}"))
                        .code(200)
                        .build())
                .build();
        
        assertEquals("/{Bucket}/{Key+}", ProtocolUtils.getHttpUri(operation, "/"));
    }
    
    @Test
    void testGetHttpStatusCode_withHttpTrait() {
        OperationShape operation = OperationShape.builder()
                .id("com.example#CreateBucket")
                .addTrait(HttpTrait.builder()
                        .method("PUT")
                        .uri(UriPattern.parse("/{Bucket}"))
                        .code(201)
                        .build())
                .build();
        
        assertEquals(201, ProtocolUtils.getHttpStatusCode(operation, 200));
    }
    
    @Test
    void testHasHttpTrait() {
        OperationShape opWithTrait = OperationShape.builder()
                .id("com.example#GetObject")
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/"))
                        .code(200)
                        .build())
                .build();
        
        OperationShape opWithoutTrait = OperationShape.builder()
                .id("com.example#OtherOp")
                .build();
        
        assertTrue(ProtocolUtils.hasHttpTrait(opWithTrait));
        assertFalse(ProtocolUtils.hasHttpTrait(opWithoutTrait));
    }
    
    // ========== @httpLabel Trait Tests ==========
    
    @Test
    void testGetLabelMembers() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape input = StructureShape.builder()
                .id("com.example#GetObjectInput")
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$Bucket")
                        .target("smithy.api#String")
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$Key")
                        .target("smithy.api#String")
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$VersionId")
                        .target("smithy.api#String")
                        .build())
                .build();
        
        List<MemberShape> labelMembers = ProtocolUtils.getLabelMembers(input);
        
        assertEquals(2, labelMembers.size());
        assertTrue(labelMembers.stream().anyMatch(m -> m.getMemberName().equals("Bucket")));
        assertTrue(labelMembers.stream().anyMatch(m -> m.getMemberName().equals("Key")));
    }
    
    @Test
    void testIsGreedyLabel() {
        String uri = "/{Bucket}/{Key+}";
        
        assertTrue(ProtocolUtils.isGreedyLabel(uri, "Key"));
        assertFalse(ProtocolUtils.isGreedyLabel(uri, "Bucket"));
    }
    
    // ========== @httpQuery Trait Tests ==========
    
    @Test
    void testGetQueryMembers() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape input = StructureShape.builder()
                .id("com.example#ListObjectsInput")
                .addMember(MemberShape.builder()
                        .id("com.example#ListObjectsInput$Bucket")
                        .target("smithy.api#String")
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#ListObjectsInput$Prefix")
                        .target("smithy.api#String")
                        .addTrait(new HttpQueryTrait("prefix"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#ListObjectsInput$MaxKeys")
                        .target("smithy.api#String")
                        .addTrait(new HttpQueryTrait("max-keys"))
                        .build())
                .build();
        
        List<MemberShape> queryMembers = ProtocolUtils.getQueryMembers(input);
        
        assertEquals(2, queryMembers.size());
        assertTrue(queryMembers.stream().anyMatch(m -> m.getMemberName().equals("Prefix")));
        assertTrue(queryMembers.stream().anyMatch(m -> m.getMemberName().equals("MaxKeys")));
    }
    
    @Test
    void testGetQueryParamName() {
        MemberShape memberWithQueryName = MemberShape.builder()
                .id("com.example#Input$MaxKeys")
                .target("smithy.api#Integer")
                .addTrait(new HttpQueryTrait("max-keys"))
                .build();
        
        MemberShape memberWithDefaultQueryName = MemberShape.builder()
                .id("com.example#Input$Prefix")
                .target("smithy.api#String")
                .addTrait(new HttpQueryTrait("prefix"))
                .build();
        
        assertEquals("max-keys", ProtocolUtils.getQueryParamName(memberWithQueryName));
        assertEquals("prefix", ProtocolUtils.getQueryParamName(memberWithDefaultQueryName));
    }
    
    // ========== @httpHeader Trait Tests ==========
    
    @Test
    void testGetHeaderMembers() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape input = StructureShape.builder()
                .id("com.example#GetObjectInput")
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$Bucket")
                        .target("smithy.api#String")
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$RequestPayer")
                        .target("smithy.api#String")
                        .addTrait(new HttpHeaderTrait("x-amz-request-payer"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$IfMatch")
                        .target("smithy.api#String")
                        .addTrait(new HttpHeaderTrait("If-Match"))
                        .build())
                .build();
        
        List<MemberShape> headerMembers = ProtocolUtils.getHeaderMembers(input);
        
        assertEquals(2, headerMembers.size());
        assertTrue(headerMembers.stream().anyMatch(m -> m.getMemberName().equals("RequestPayer")));
        assertTrue(headerMembers.stream().anyMatch(m -> m.getMemberName().equals("IfMatch")));
    }
    
    @Test
    void testGetHeaderName() {
        MemberShape memberWithHeaderName = MemberShape.builder()
                .id("com.example#Input$RequestPayer")
                .target("smithy.api#String")
                .addTrait(new HttpHeaderTrait("x-amz-request-payer"))
                .build();
        
        MemberShape memberWithDifferentHeaderName = MemberShape.builder()
                .id("com.example#Input$ContentType")
                .target("smithy.api#String")
                .addTrait(new HttpHeaderTrait("Content-Type"))
                .build();
        
        assertEquals("x-amz-request-payer", ProtocolUtils.getHeaderName(memberWithHeaderName));
        assertEquals("Content-Type", ProtocolUtils.getHeaderName(memberWithDifferentHeaderName));
    }
    
    // ========== @httpPayload Trait Tests ==========
    
    @Test
    void testGetPayloadMember() {
        BlobShape blobShape = BlobShape.builder()
                .id("smithy.api#Blob")
                .build();
        
        StructureShape output = StructureShape.builder()
                .id("com.example#GetObjectOutput")
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectOutput$Body")
                        .target("smithy.api#Blob")
                        .addTrait(new HttpPayloadTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectOutput$ContentType")
                        .target("smithy.api#String")
                        .addTrait(new HttpHeaderTrait("Content-Type"))
                        .build())
                .build();
        
        Optional<MemberShape> payloadMember = ProtocolUtils.getPayloadMember(output);
        
        assertTrue(payloadMember.isPresent());
        assertEquals("Body", payloadMember.get().getMemberName());
    }
    
    @Test
    void testGetPayloadMember_notPresent() {
        StructureShape output = StructureShape.builder()
                .id("com.example#ListBucketsOutput")
                .addMember(MemberShape.builder()
                        .id("com.example#ListBucketsOutput$Buckets")
                        .target("smithy.api#String")
                        .build())
                .build();
        
        Optional<MemberShape> payloadMember = ProtocolUtils.getPayloadMember(output);
        
        assertFalse(payloadMember.isPresent());
    }
    
    // ========== @httpResponseCode Trait Tests ==========
    
    @Test
    void testGetResponseCodeMember() {
        IntegerShape intShape = IntegerShape.builder()
                .id("smithy.api#Integer")
                .build();
        
        StructureShape output = StructureShape.builder()
                .id("com.example#CustomOutput")
                .addMember(MemberShape.builder()
                        .id("com.example#CustomOutput$StatusCode")
                        .target("smithy.api#Integer")
                        .addTrait(new HttpResponseCodeTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#CustomOutput$Body")
                        .target("smithy.api#String")
                        .build())
                .build();
        
        Optional<MemberShape> responseCodeMember = ProtocolUtils.getResponseCodeMember(output);
        
        assertTrue(responseCodeMember.isPresent());
        assertEquals("StatusCode", responseCodeMember.get().getMemberName());
    }
    
    // ========== Body Members Tests ==========
    
    @Test
    void testGetBodyMembers() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        StructureShape input = StructureShape.builder()
                .id("com.example#CreateBucketInput")
                .addMember(MemberShape.builder()
                        .id("com.example#CreateBucketInput$Bucket")
                        .target("smithy.api#String")
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#CreateBucketInput$ACL")
                        .target("smithy.api#String")
                        .addTrait(new HttpHeaderTrait("x-amz-acl"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#CreateBucketInput$LocationConstraint")
                        .target("smithy.api#String")
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#CreateBucketInput$GrantRead")
                        .target("smithy.api#String")
                        .build())
                .build();
        
        List<MemberShape> bodyMembers = ProtocolUtils.getBodyMembers(input);
        
        // Only LocationConstraint and GrantRead should be body members
        assertEquals(2, bodyMembers.size());
        assertTrue(bodyMembers.stream().anyMatch(m -> m.getMemberName().equals("LocationConstraint")));
        assertTrue(bodyMembers.stream().anyMatch(m -> m.getMemberName().equals("GrantRead")));
        
        // Verify label and header members are excluded
        assertFalse(bodyMembers.stream().anyMatch(m -> m.getMemberName().equals("Bucket")));
        assertFalse(bodyMembers.stream().anyMatch(m -> m.getMemberName().equals("ACL")));
    }
    
    @Test
    void testGetBodyMembers_excludesAllBindings() {
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        IntegerShape intShape = IntegerShape.builder()
                .id("smithy.api#Integer")
                .build();
        
        StructureShape shape = StructureShape.builder()
                .id("com.example#FullBindingExample")
                .addMember(MemberShape.builder()
                        .id("com.example#FullBindingExample$PathParam")
                        .target("smithy.api#String")
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#FullBindingExample$QueryParam")
                        .target("smithy.api#String")
                        .addTrait(new HttpQueryTrait("query"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#FullBindingExample$HeaderParam")
                        .target("smithy.api#String")
                        .addTrait(new HttpHeaderTrait("X-Custom-Header"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#FullBindingExample$StatusCode")
                        .target("smithy.api#Integer")
                        .addTrait(new HttpResponseCodeTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#FullBindingExample$BodyField")
                        .target("smithy.api#String")
                        .build())
                .build();
        
        List<MemberShape> bodyMembers = ProtocolUtils.getBodyMembers(shape);
        
        // Only BodyField should be a body member
        assertEquals(1, bodyMembers.size());
        assertEquals("BodyField", bodyMembers.get(0).getMemberName());
    }
    
    // ========== Combined Scenarios Tests ==========
    
    @Test
    void testS3GetObjectScenario() {
        // Model a simplified S3 GetObject operation
        StringShape stringShape = StringShape.builder()
                .id("smithy.api#String")
                .build();
        
        BlobShape blobShape = BlobShape.builder()
                .id("smithy.api#Blob")
                .build();
        
        StructureShape input = StructureShape.builder()
                .id("com.example#GetObjectInput")
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$Bucket")
                        .target("smithy.api#String")
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$Key")
                        .target("smithy.api#String")
                        .addTrait(new HttpLabelTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$VersionId")
                        .target("smithy.api#String")
                        .addTrait(new HttpQueryTrait("versionId"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectInput$IfMatch")
                        .target("smithy.api#String")
                        .addTrait(new HttpHeaderTrait("If-Match"))
                        .build())
                .build();
        
        StructureShape output = StructureShape.builder()
                .id("com.example#GetObjectOutput")
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectOutput$Body")
                        .target("smithy.api#Blob")
                        .addTrait(new HttpPayloadTrait())
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectOutput$ContentType")
                        .target("smithy.api#String")
                        .addTrait(new HttpHeaderTrait("Content-Type"))
                        .build())
                .addMember(MemberShape.builder()
                        .id("com.example#GetObjectOutput$ETag")
                        .target("smithy.api#String")
                        .addTrait(new HttpHeaderTrait("ETag"))
                        .build())
                .build();
        
        OperationShape operation = OperationShape.builder()
                .id("com.example#GetObject")
                .input(input.getId())
                .output(output.getId())
                .addTrait(HttpTrait.builder()
                        .method("GET")
                        .uri(UriPattern.parse("/{Bucket}/{Key+}"))
                        .code(200)
                        .build())
                .build();
        
        // Verify operation bindings
        assertEquals("GET", ProtocolUtils.getHttpMethod(operation, "POST"));
        assertEquals("/{Bucket}/{Key+}", ProtocolUtils.getHttpUri(operation, "/"));
        assertEquals(200, ProtocolUtils.getHttpStatusCode(operation, 500));
        
        // Verify input bindings
        List<MemberShape> labelMembers = ProtocolUtils.getLabelMembers(input);
        List<MemberShape> queryMembers = ProtocolUtils.getQueryMembers(input);
        List<MemberShape> headerInputMembers = ProtocolUtils.getHeaderMembers(input);
        List<MemberShape> bodyInputMembers = ProtocolUtils.getBodyMembers(input);
        
        assertEquals(2, labelMembers.size());
        assertEquals(1, queryMembers.size());
        assertEquals(1, headerInputMembers.size());
        assertEquals(0, bodyInputMembers.size());
        
        // Verify output bindings
        Optional<MemberShape> payloadMember = ProtocolUtils.getPayloadMember(output);
        List<MemberShape> headerOutputMembers = ProtocolUtils.getHeaderMembers(output);
        List<MemberShape> bodyOutputMembers = ProtocolUtils.getBodyMembers(output);
        
        assertTrue(payloadMember.isPresent());
        assertEquals("Body", payloadMember.get().getMemberName());
        assertEquals(2, headerOutputMembers.size());
        assertEquals(0, bodyOutputMembers.size());
    }
}
