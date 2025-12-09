package io.smithy.unison.codegen;

import io.smithy.unison.codegen.RuntimeModuleCopier.RuntimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.build.MockManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RuntimeModuleCopier}.
 */
class RuntimeModuleCopierTest {
    
    @TempDir
    Path tempDir;
    
    private MockManifest manifest;
    private RuntimeModuleCopier copier;
    
    @BeforeEach
    void setUp() {
        manifest = new MockManifest();
        copier = new RuntimeModuleCopier(manifest, null);
    }
    
    @Test
    void testSigV4ModuleIsAvailable() {
        assertTrue(copier.isModuleAvailable(RuntimeModule.AWS_SIGV4),
            "aws_sigv4.u module should be available as a resource");
    }
    
    @Test
    void testGetSigV4ModuleContent() {
        String content = copier.getModuleContent(RuntimeModule.AWS_SIGV4);
        
        assertNotNull(content, "Module content should not be null");
        assertFalse(content.isEmpty(), "Module content should not be empty");
        
        // Verify expected content
        assertTrue(content.contains("Aws.Credentials"),
            "Module should define Aws.Credentials type");
        assertTrue(content.contains("Aws.SigningConfig"),
            "Module should define Aws.SigningConfig type");
        assertTrue(content.contains("Aws.CredentialScope"),
            "Module should define Aws.CredentialScope type");
        assertTrue(content.contains("Aws.SigV4.signRequest"),
            "Module should define Aws.SigV4.signRequest function");
        assertTrue(content.contains("Aws.SigV4.deriveSigningKey"),
            "Module should define Aws.SigV4.deriveSigningKey function");
        assertTrue(content.contains("Aws.SigV4.canonicalRequest"),
            "Module should define Aws.SigV4.canonicalRequest function");
    }
    
    @Test
    void testSigV4ModuleHasDocumentation() {
        String content = copier.getModuleContent(RuntimeModule.AWS_SIGV4);
        
        // Check for doc comments
        assertTrue(content.contains("{{"),
            "Module should have doc comments");
        assertTrue(content.contains("AWS credentials for signing requests"),
            "Module should document Credentials type");
        assertTrue(content.contains("Sign an HTTP request using AWS SigV4"),
            "Module should document signRequest function");
    }
    
    @Test
    void testSigV4ModuleHasCryptoFunctions() {
        String content = copier.getModuleContent(RuntimeModule.AWS_SIGV4);
        
        // Check for crypto usage
        assertTrue(content.contains("hashBytes Sha2_256"),
            "Module should use SHA-256 hashing");
        assertTrue(content.contains("hmacBytes Sha2_256"),
            "Module should use HMAC-SHA256");
        assertTrue(content.contains("Bytes.toHex"),
            "Module should convert bytes to hex");
    }
    
    @Test
    void testSigV4ModuleHasSessionTokenHandling() {
        String content = copier.getModuleContent(RuntimeModule.AWS_SIGV4);
        
        assertTrue(content.contains("sessionToken"),
            "Module should handle session tokens");
        assertTrue(content.contains("X-Amz-Security-Token"),
            "Module should add security token header");
    }
    
    @Test
    void testCopyModule() {
        boolean result = copier.copyModule(RuntimeModule.AWS_SIGV4);
        
        assertTrue(result, "Copy should succeed");
        assertTrue(manifest.hasFile("src/aws_sigv4.u"),
            "Module should be written to manifest");
    }
    
    @Test
    void testCopyAwsModules() {
        List<String> copied = copier.copyAwsModules();
        
        assertFalse(copied.isEmpty(), "Should copy at least one module");
        assertTrue(copied.contains("aws_sigv4.u"),
            "Should copy aws_sigv4.u");
    }
    
    @Test
    void testCopyWithNullOutputDir() {
        // When outputDir is null, should write to manifest
        RuntimeModuleCopier customCopier = new RuntimeModuleCopier(manifest, null);
        
        boolean result = customCopier.copyModule(RuntimeModule.AWS_SIGV4);
        
        assertTrue(result, "Copy should succeed");
        assertTrue(manifest.hasFile("src/aws_sigv4.u"),
            "Should write to manifest when outputDir is null");
    }
    
    @Test
    void testRuntimeModuleEnum() {
        RuntimeModule sigv4 = RuntimeModule.AWS_SIGV4;
        
        assertEquals("aws_sigv4.u", sigv4.getFilename());
        assertEquals("AWS SigV4 request signing", sigv4.getDescription());
        assertEquals("runtime/aws_sigv4.u", sigv4.getResourcePath());
    }
    
    @Test
    void testSigV4ModuleLineCount() {
        String content = copier.getModuleContent(RuntimeModule.AWS_SIGV4);
        long lineCount = content.lines().count();
        
        // Plan says ~300 lines
        assertTrue(lineCount >= 200 && lineCount <= 400,
            "Module should be approximately 300 lines (was " + lineCount + ")");
    }
    
    @Test
    void testSigV4ModuleContainsAllRequiredFunctions() {
        String content = copier.getModuleContent(RuntimeModule.AWS_SIGV4);
        
        // Types
        assertTrue(content.contains("type Aws.Credentials"));
        assertTrue(content.contains("type Aws.SigningConfig"));
        assertTrue(content.contains("type Aws.CredentialScope"));
        
        // Credential helpers
        assertTrue(content.contains("Aws.Credentials.basic"));
        assertTrue(content.contains("Aws.Credentials.withSessionToken"));
        
        // Timestamp functions
        assertTrue(content.contains("Aws.SigV4.getTimestamp"));
        assertTrue(content.contains("Aws.SigV4.getDateStamp"));
        
        // Hashing
        assertTrue(content.contains("Aws.SigV4.hashPayload"));
        
        // Canonical request building
        assertTrue(content.contains("Aws.SigV4.canonicalHeaders"));
        assertTrue(content.contains("Aws.SigV4.signedHeaders"));
        assertTrue(content.contains("Aws.SigV4.canonicalRequest"));
        
        // Signing
        assertTrue(content.contains("Aws.SigV4.stringToSign"));
        assertTrue(content.contains("Aws.SigV4.deriveSigningKey"));
        assertTrue(content.contains("Aws.SigV4.signature"));
        assertTrue(content.contains("Aws.SigV4.authorizationHeader"));
        
        // Main entry points
        assertTrue(content.contains("Aws.SigV4.signRequest"));
        assertTrue(content.contains("Aws.SigV4.addSigningHeaders"));
    }
    
    // ========== XML Module Tests ==========
    
    @Test
    void testXmlModuleIsAvailable() {
        assertTrue(copier.isModuleAvailable(RuntimeModule.AWS_XML),
            "aws_xml.u module should be available as a resource");
    }
    
    @Test
    void testGetXmlModuleContent() {
        String content = copier.getModuleContent(RuntimeModule.AWS_XML);
        
        assertNotNull(content, "Module content should not be null");
        assertFalse(content.isEmpty(), "Module content should not be empty");
        
        // Verify expected content
        assertTrue(content.contains("Aws.Xml.escape"),
            "Module should define Aws.Xml.escape function");
        assertTrue(content.contains("Aws.Xml.unescape"),
            "Module should define Aws.Xml.unescape function");
        assertTrue(content.contains("Aws.Xml.element"),
            "Module should define Aws.Xml.element function");
        assertTrue(content.contains("Aws.Xml.extractElement"),
            "Module should define Aws.Xml.extractElement function");
    }
    
    @Test
    void testXmlModuleHasEscapeFunctions() {
        String content = copier.getModuleContent(RuntimeModule.AWS_XML);
        
        // Check for escape handling
        assertTrue(content.contains("&amp;"),
            "Module should handle & escaping");
        assertTrue(content.contains("&lt;"),
            "Module should handle < escaping");
        assertTrue(content.contains("&gt;"),
            "Module should handle > escaping");
        assertTrue(content.contains("&quot;"),
            "Module should handle \" escaping");
        assertTrue(content.contains("&apos;"),
            "Module should handle ' escaping");
    }
    
    @Test
    void testXmlModuleHasElementCreation() {
        String content = copier.getModuleContent(RuntimeModule.AWS_XML);
        
        // Element creation functions
        assertTrue(content.contains("Aws.Xml.element"));
        assertTrue(content.contains("Aws.Xml.elementRaw"));
        assertTrue(content.contains("Aws.Xml.emptyElement"));
        assertTrue(content.contains("Aws.Xml.elementWithAttrs"));
        assertTrue(content.contains("Aws.Xml.optionalElement"));
    }
    
    @Test
    void testXmlModuleHasListHandling() {
        String content = copier.getModuleContent(RuntimeModule.AWS_XML);
        
        // List handling
        assertTrue(content.contains("Aws.Xml.listElements"),
            "Module should have listElements function");
        assertTrue(content.contains("Aws.Xml.wrappedList"),
            "Module should have wrappedList function");
        assertTrue(content.contains("Aws.Xml.extractAll"),
            "Module should have extractAll function");
    }
    
    @Test
    void testXmlModuleHasExtraction() {
        String content = copier.getModuleContent(RuntimeModule.AWS_XML);
        
        // Extraction functions
        assertTrue(content.contains("Aws.Xml.extractElement"));
        assertTrue(content.contains("Aws.Xml.extractElementOpt"));
        assertTrue(content.contains("Aws.Xml.extractAttribute"));
        assertTrue(content.contains("Aws.Xml.extractInt"));
        assertTrue(content.contains("Aws.Xml.extractBool"));
        assertTrue(content.contains("Aws.Xml.extractBlock"));
        assertTrue(content.contains("Aws.Xml.extractAllBlocks"));
    }
    
    @Test
    void testXmlModuleHasErrorParsing() {
        String content = copier.getModuleContent(RuntimeModule.AWS_XML);
        
        // Error parsing
        assertTrue(content.contains("Aws.Xml.ErrorResponse"),
            "Module should have ErrorResponse type");
        assertTrue(content.contains("Aws.Xml.parseError"),
            "Module should have parseError function");
        assertTrue(content.contains("Aws.Xml.isError"),
            "Module should have isError function");
    }
    
    @Test
    void testXmlModuleHasDocumentation() {
        String content = copier.getModuleContent(RuntimeModule.AWS_XML);
        
        assertTrue(content.contains("{{"),
            "Module should have doc comments");
        assertTrue(content.contains("Escape special XML characters"),
            "Module should document escape function");
        assertTrue(content.contains("Extract text content from an XML element"),
            "Module should document extractElement function");
    }
    
    @Test
    void testXmlModuleHasNamespaceSupport() {
        String content = copier.getModuleContent(RuntimeModule.AWS_XML);
        
        assertTrue(content.contains("Aws.Xml.s3Namespace"),
            "Module should have S3 namespace constant");
        assertTrue(content.contains("Aws.Xml.s3Element"),
            "Module should have s3Element helper");
        assertTrue(content.contains("http://s3.amazonaws.com/doc/2006-03-01/"),
            "Module should have correct S3 namespace URL");
    }
    
    @Test
    void testCopyXmlModule() {
        boolean result = copier.copyModule(RuntimeModule.AWS_XML);
        
        assertTrue(result, "Copy should succeed");
        assertTrue(manifest.hasFile("src/aws_xml.u"),
            "Module should be written to manifest");
    }
    
    @Test
    void testCopyAwsModulesIncludesXml() {
        List<String> copied = copier.copyAwsModules();
        
        assertTrue(copied.contains("aws_xml.u"),
            "Should copy aws_xml.u");
        assertTrue(copied.contains("aws_sigv4.u"),
            "Should also copy aws_sigv4.u");
    }
    
    @Test
    void testXmlModuleEnum() {
        RuntimeModule xml = RuntimeModule.AWS_XML;
        
        assertEquals("aws_xml.u", xml.getFilename());
        assertEquals("XML encoding/decoding", xml.getDescription());
        assertEquals("runtime/aws_xml.u", xml.getResourcePath());
    }
    
    // ========== HTTP Module Tests ==========
    
    @Test
    void testHttpModuleIsAvailable() {
        assertTrue(copier.isModuleAvailable(RuntimeModule.AWS_HTTP),
            "aws_http.u module should be available as a resource");
    }
    
    @Test
    void testGetHttpModuleContent() {
        String content = copier.getModuleContent(RuntimeModule.AWS_HTTP);
        
        assertNotNull(content, "Module content should not be null");
        assertFalse(content.isEmpty(), "Module content should not be empty");
        
        // Verify expected content
        assertTrue(content.contains("Aws.Http.isSuccess"),
            "Module should define Aws.Http.isSuccess function");
        assertTrue(content.contains("Aws.Http.getHeader"),
            "Module should define Aws.Http.getHeader function");
        assertTrue(content.contains("Aws.Http.buildQueryString"),
            "Module should define Aws.Http.buildQueryString function");
    }
    
    @Test
    void testHttpModuleHasStatusCodeHelpers() {
        String content = copier.getModuleContent(RuntimeModule.AWS_HTTP);
        
        assertTrue(content.contains("Aws.Http.isSuccess"),
            "Module should have isSuccess function");
        assertTrue(content.contains("Aws.Http.isClientError"),
            "Module should have isClientError function");
        assertTrue(content.contains("Aws.Http.isServerError"),
            "Module should have isServerError function");
        assertTrue(content.contains("Aws.Http.isError"),
            "Module should have isError function");
        assertTrue(content.contains("Aws.Http.isRetryable"),
            "Module should have isRetryable function");
    }
    
    @Test
    void testHttpModuleHasHeaderHelpers() {
        String content = copier.getModuleContent(RuntimeModule.AWS_HTTP);
        
        assertTrue(content.contains("Aws.Http.getHeader"));
        assertTrue(content.contains("Aws.Http.getHeaderOrDefault"));
        assertTrue(content.contains("Aws.Http.hasHeader"));
        assertTrue(content.contains("Aws.Http.addHeader"));
        assertTrue(content.contains("Aws.Http.setHeader"));
        assertTrue(content.contains("Aws.Http.removeHeader"));
        assertTrue(content.contains("Aws.Http.mergeHeaders"));
    }
    
    @Test
    void testHttpModuleHasQueryStringHelpers() {
        String content = copier.getModuleContent(RuntimeModule.AWS_HTTP);
        
        assertTrue(content.contains("Aws.Http.buildQueryString"),
            "Module should have buildQueryString function");
        assertTrue(content.contains("Aws.Http.appendQueryString"),
            "Module should have appendQueryString function");
        assertTrue(content.contains("Aws.Http.urlEncode"),
            "Module should have urlEncode function");
        assertTrue(content.contains("Aws.Http.urlDecode"),
            "Module should have urlDecode function");
    }
    
    @Test
    void testHttpModuleHasUrlHelpers() {
        String content = copier.getModuleContent(RuntimeModule.AWS_HTTP);
        
        assertTrue(content.contains("Aws.Http.buildUrl"),
            "Module should have buildUrl function");
        assertTrue(content.contains("Aws.Http.extractHost"),
            "Module should have extractHost function");
        assertTrue(content.contains("Aws.Http.extractPath"),
            "Module should have extractPath function");
    }
    
    @Test
    void testHttpModuleHasContentTypeHelpers() {
        String content = copier.getModuleContent(RuntimeModule.AWS_HTTP);
        
        assertTrue(content.contains("Aws.Http.contentTypeXml"),
            "Module should have contentTypeXml constant");
        assertTrue(content.contains("Aws.Http.contentTypeJson"),
            "Module should have contentTypeJson constant");
        assertTrue(content.contains("Aws.Http.isXmlContentType"),
            "Module should have isXmlContentType function");
        assertTrue(content.contains("Aws.Http.isJsonContentType"),
            "Module should have isJsonContentType function");
    }
    
    @Test
    void testHttpModuleHasAwsHeaders() {
        String content = copier.getModuleContent(RuntimeModule.AWS_HTTP);
        
        assertTrue(content.contains("x-amz-request-id"),
            "Module should have AWS request ID header");
        assertTrue(content.contains("x-amz-date"),
            "Module should have AWS date header");
        assertTrue(content.contains("x-amz-security-token"),
            "Module should have AWS security token header");
        assertTrue(content.contains("Aws.Http.getRequestId"),
            "Module should have getRequestId function");
    }
    
    @Test
    void testHttpModuleHasDocumentation() {
        String content = copier.getModuleContent(RuntimeModule.AWS_HTTP);
        
        assertTrue(content.contains("{{"),
            "Module should have doc comments");
        assertTrue(content.contains("Check if an HTTP status code indicates success"),
            "Module should document isSuccess function");
    }
    
    @Test
    void testCopyHttpModule() {
        boolean result = copier.copyModule(RuntimeModule.AWS_HTTP);
        
        assertTrue(result, "Copy should succeed");
        assertTrue(manifest.hasFile("src/aws_http.u"),
            "Module should be written to manifest");
    }
    
    @Test
    void testCopyAwsModulesIncludesHttp() {
        List<String> copied = copier.copyAwsModules();
        
        assertTrue(copied.contains("aws_http.u"),
            "Should copy aws_http.u");
        assertTrue(copied.contains("aws_sigv4.u"),
            "Should also copy aws_sigv4.u");
        assertTrue(copied.contains("aws_xml.u"),
            "Should also copy aws_xml.u");
    }
    
    @Test
    void testHttpModuleEnum() {
        RuntimeModule http = RuntimeModule.AWS_HTTP;
        
        assertEquals("aws_http.u", http.getFilename());
        assertEquals("HTTP request helpers", http.getDescription());
        assertEquals("runtime/aws_http.u", http.getResourcePath());
    }
}
