package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for S3UrlGenerator.
 */
public class S3UrlGeneratorTest {
    
    private S3UrlGenerator generator;
    private UnisonWriter writer;
    
    @BeforeEach
    void setUp() {
        generator = new S3UrlGenerator();
        writer = new UnisonWriter("aws.s3");
    }
    
    @Test
    void testGenerateUrlEncode() {
        generator.generateUrlEncode(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.urlEncode : Text -> Text"),
                "Should generate correct signature. Got: " + output);
        
        // Check function definition
        assertTrue(output.contains("Aws.urlEncode text ="),
                "Should generate function definition. Got: " + output);
        
        // Check URL encoding for common special characters
        assertTrue(output.contains("%20"), "Should encode spaces");
        assertTrue(output.contains("%26"), "Should encode ampersands");
        assertTrue(output.contains("%3D"), "Should encode equals signs");
        assertTrue(output.contains("%3F"), "Should encode question marks");
    }
    
    @Test
    void testGenerateUrlEncodePathSegment() {
        generator.generateUrlEncodePathSegment(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.urlEncodePathSegment : Text -> Text"),
                "Should generate correct signature. Got: " + output);
        
        // Check that it encodes slashes
        assertTrue(output.contains("%2F"),
                "Should encode slashes. Got: " + output);
    }
    
    @Test
    void testGenerateBuildQueryString() {
        generator.generateBuildQueryString(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.buildQueryString : [(Text, Optional Text)] -> Text"),
                "Should generate correct signature. Got: " + output);
        
        // Check function definition
        assertTrue(output.contains("Aws.buildQueryString params ="),
                "Should generate function definition. Got: " + output);
        
        // Check that it handles None values
        assertTrue(output.contains("None") && output.contains("Some"),
                "Should handle Optional values. Got: " + output);
        
        // Check that it joins with & and prefixes with ?
        assertTrue(output.contains("\"?\"") && output.contains("\"&\""),
                "Should use ? prefix and & separator. Got: " + output);
    }
    
    @Test
    void testGenerateBuildS3Url() {
        generator.generateBuildS3Url(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.S3.buildUrl : Config -> Text -> Text -> Text"),
                "Should generate correct signature. Got: " + output);
        
        // Check function definition
        assertTrue(output.contains("Aws.S3.buildUrl config bucket key ="),
                "Should generate function definition. Got: " + output);
        
        // Check for path style handling
        assertTrue(output.contains("usePathStyle"),
                "Should check usePathStyle config. Got: " + output);
        
        // Check for virtual-hosted style URL pattern
        assertTrue(output.contains("bucket ++ \".\" ++ endpoint"),
                "Should build virtual-hosted style URL. Got: " + output);
        
        // Check for path style URL pattern
        assertTrue(output.contains("endpoint ++ \"/\" ++ bucket"),
                "Should build path style URL. Got: " + output);
    }
    
    @Test
    void testGenerateBuildS3UrlWithQuery() {
        generator.generateBuildS3UrlWithQuery(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.S3.buildUrlWithQuery : Config -> Text -> Text -> [(Text, Optional Text)] -> Text"),
                "Should generate correct signature. Got: " + output);
        
        // Check that it uses buildUrl and buildQueryString
        assertTrue(output.contains("Aws.S3.buildUrl"),
                "Should call Aws.S3.buildUrl. Got: " + output);
        assertTrue(output.contains("Aws.buildQueryString"),
                "Should call Aws.buildQueryString. Got: " + output);
    }
    
    @Test
    void testGenerate_producesAllCoreFunctions() {
        generator.generate(writer);
        
        String output = writer.toString();
        
        // Check all core functions are generated
        assertTrue(output.contains("Aws.urlEncode :"),
                "Should generate urlEncode. Got: " + output);
        assertTrue(output.contains("Aws.buildQueryString :"),
                "Should generate buildQueryString. Got: " + output);
        assertTrue(output.contains("Aws.S3.buildUrl :"),
                "Should generate S3.buildUrl. Got: " + output);
    }
    
    @Test
    void testGenerateAll_producesAllFunctions() {
        generator.generateAll(writer);
        
        String output = writer.toString();
        
        // Check all functions including helpers
        assertTrue(output.contains("Aws.urlEncode :"),
                "Should generate urlEncode");
        assertTrue(output.contains("Aws.urlEncodePathSegment :"),
                "Should generate urlEncodePathSegment");
        assertTrue(output.contains("Aws.buildQueryString :"),
                "Should generate buildQueryString");
        assertTrue(output.contains("Aws.S3.buildUrl :"),
                "Should generate S3.buildUrl");
        assertTrue(output.contains("Aws.S3.buildUrlWithQuery :"),
                "Should generate S3.buildUrlWithQuery");
        
        // Check section comments
        assertTrue(output.contains("AWS URL Utilities"),
                "Should have URL utilities section");
        assertTrue(output.contains("S3 URL Building"),
                "Should have S3 section");
    }
    
    @Test
    void testGenerateBuildS3Url_documentation() {
        generator.generateBuildS3Url(writer);
        
        String output = writer.toString();
        
        // Check documentation is generated
        assertTrue(output.contains("{{"),
                "Should have documentation. Got: " + output);
        assertTrue(output.contains("Virtual-hosted") || output.contains("virtual-hosted"),
                "Should document virtual-hosted style. Got: " + output);
        assertTrue(output.contains("Path style") || output.contains("path style"),
                "Should document path style. Got: " + output);
    }
    
    @Test
    void testUrlEncode_documentationMentionsRFC() {
        generator.generateUrlEncode(writer);
        
        String output = writer.toString();
        
        // Check RFC reference in documentation
        assertTrue(output.contains("RFC 3986") || output.contains("URL encode"),
                "Should mention RFC or URL encoding. Got: " + output);
    }
}
