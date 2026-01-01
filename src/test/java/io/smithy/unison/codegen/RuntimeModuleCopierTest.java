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
        assertTrue(content.contains("aws.credentials"),
            "Module should define aws.credentials type");
        assertTrue(content.contains("aws.signingconfig"),
            "Module should define aws.signingconfig type");
        assertTrue(content.contains("aws.credentialscope"),
            "Module should define aws.credentialscope type");
        assertTrue(content.contains("aws.sigv4.signRequest"),
            "Module should define aws.sigv4.signRequest function");
        assertTrue(content.contains("aws.sigv4.deriveSigningKey"),
            "Module should define aws.sigv4.deriveSigningKey function");
        assertTrue(content.contains("aws.sigv4.canonicalRequest"),
            "Module should define aws.sigv4.canonicalRequest function");
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
        assertTrue(content.contains("type aws.credentials"));
        assertTrue(content.contains("type aws.signingconfig"));
        assertTrue(content.contains("type aws.credentialscope"));
        
        // Credential helpers
        assertTrue(content.contains("aws.credentials.basic"));
        assertTrue(content.contains("aws.credentials.withSessionToken"));
        
        // Timestamp functions
        assertTrue(content.contains("aws.sigv4.getTimestamp"));
        assertTrue(content.contains("aws.sigv4.getDateStamp"));
        
        // Hashing
        assertTrue(content.contains("aws.sigv4.hashPayload"));
        
        // Canonical request building
        assertTrue(content.contains("aws.sigv4.canonicalHeaders"));
        assertTrue(content.contains("aws.sigv4.signedHeaders"));
        assertTrue(content.contains("aws.sigv4.canonicalRequest"));
        
        // Signing
        assertTrue(content.contains("aws.sigv4.stringToSign"));
        assertTrue(content.contains("aws.sigv4.deriveSigningKey"));
        assertTrue(content.contains("aws.sigv4.signature"));
        assertTrue(content.contains("aws.sigv4.authorizationHeader"));
        
        // Main entry points
        assertTrue(content.contains("aws.sigv4.signRequest"));
        assertTrue(content.contains("aws.sigv4.addSigningHeaders"));
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
        assertTrue(content.contains("aws.xml.escape"),
            "Module should define aws.xml.escape function");
        assertTrue(content.contains("aws.xml.unescape"),
            "Module should define aws.xml.unescape function");
        assertTrue(content.contains("aws.xml.element"),
            "Module should define aws.xml.element function");
        assertTrue(content.contains("aws.xml.extractElement"),
            "Module should define aws.xml.extractElement function");
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
        assertTrue(content.contains("aws.xml.element"));
        assertTrue(content.contains("aws.xml.elementRaw"));
        assertTrue(content.contains("aws.xml.emptyElement"));
        assertTrue(content.contains("aws.xml.elementWithAttrs"));
        assertTrue(content.contains("aws.xml.optionalElement"));
    }
    
    @Test
    void testXmlModuleHasListHandling() {
        String content = copier.getModuleContent(RuntimeModule.AWS_XML);
        
        // List handling
        assertTrue(content.contains("aws.xml.listElements"),
            "Module should have listElements function");
        assertTrue(content.contains("aws.xml.wrappedList"),
            "Module should have wrappedList function");
        assertTrue(content.contains("aws.xml.extractAll"),
            "Module should have extractAll function");
    }
    
    @Test
    void testXmlModuleHasExtraction() {
        String content = copier.getModuleContent(RuntimeModule.AWS_XML);
        
        // Extraction functions
        assertTrue(content.contains("aws.xml.extractElement"));
        assertTrue(content.contains("aws.xml.extractElementOpt"));
        assertTrue(content.contains("aws.xml.extractAttribute"));
        assertTrue(content.contains("aws.xml.extractInt"));
        assertTrue(content.contains("aws.xml.extractBool"));
        assertTrue(content.contains("aws.xml.extractBlock"));
        assertTrue(content.contains("aws.xml.extractAllBlocks"));
    }
    
    @Test
    void testXmlModuleHasErrorParsing() {
        String content = copier.getModuleContent(RuntimeModule.AWS_XML);
        
        // Error parsing
        assertTrue(content.contains("aws.xml.ErrorResponse"),
            "Module should have ErrorResponse type");
        assertTrue(content.contains("aws.xml.parseError"),
            "Module should have parseError function");
        assertTrue(content.contains("aws.xml.isError"),
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
        
        assertTrue(content.contains("aws.xml.s3Namespace"),
            "Module should have S3 namespace constant");
        assertTrue(content.contains("aws.xml.s3Element"),
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
        assertTrue(content.contains("aws.http.isSuccess"),
            "Module should define aws.http.isSuccess function");
        assertTrue(content.contains("aws.http.getHeader"),
            "Module should define aws.http.getHeader function");
        assertTrue(content.contains("aws.http.buildQueryString"),
            "Module should define aws.http.buildQueryString function");
    }
    
    @Test
    void testHttpModuleHasStatusCodeHelpers() {
        String content = copier.getModuleContent(RuntimeModule.AWS_HTTP);
        
        assertTrue(content.contains("aws.http.isSuccess"),
            "Module should have isSuccess function");
        assertTrue(content.contains("aws.http.isClientError"),
            "Module should have isClientError function");
        assertTrue(content.contains("aws.http.isServerError"),
            "Module should have isServerError function");
        assertTrue(content.contains("aws.http.isError"),
            "Module should have isError function");
        assertTrue(content.contains("aws.http.isRetryable"),
            "Module should have isRetryable function");
    }
    
    @Test
    void testHttpModuleHasHeaderHelpers() {
        String content = copier.getModuleContent(RuntimeModule.AWS_HTTP);
        
        assertTrue(content.contains("aws.http.getHeader"));
        assertTrue(content.contains("aws.http.getHeaderOrDefault"));
        assertTrue(content.contains("aws.http.hasHeader"));
        assertTrue(content.contains("aws.http.addHeader"));
        assertTrue(content.contains("aws.http.setHeader"));
        assertTrue(content.contains("aws.http.removeHeader"));
        assertTrue(content.contains("aws.http.mergeHeaders"));
    }
    
    @Test
    void testHttpModuleHasQueryStringHelpers() {
        String content = copier.getModuleContent(RuntimeModule.AWS_HTTP);
        
        assertTrue(content.contains("aws.http.buildQueryString"),
            "Module should have buildQueryString function");
        assertTrue(content.contains("aws.http.appendQueryString"),
            "Module should have appendQueryString function");
        assertTrue(content.contains("aws.http.urlEncode"),
            "Module should have urlEncode function");
        assertTrue(content.contains("aws.http.urlDecode"),
            "Module should have urlDecode function");
    }
    
    @Test
    void testHttpModuleHasUrlHelpers() {
        String content = copier.getModuleContent(RuntimeModule.AWS_HTTP);
        
        assertTrue(content.contains("aws.http.buildUrl"),
            "Module should have buildUrl function");
        assertTrue(content.contains("aws.http.extractHost"),
            "Module should have extractHost function");
        assertTrue(content.contains("aws.http.extractPath"),
            "Module should have extractPath function");
    }
    
    @Test
    void testHttpModuleHasContentTypeHelpers() {
        String content = copier.getModuleContent(RuntimeModule.AWS_HTTP);
        
        assertTrue(content.contains("aws.http.contentTypeXml"),
            "Module should have contentTypeXml constant");
        assertTrue(content.contains("aws.http.contentTypeJson"),
            "Module should have contentTypeJson constant");
        assertTrue(content.contains("aws.http.isXmlContentType"),
            "Module should have isXmlContentType function");
        assertTrue(content.contains("aws.http.isJsonContentType"),
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
        assertTrue(content.contains("aws.http.getRequestId"),
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
    
    // ========== S3 Module Tests ==========
    
    @Test
    void testS3ModuleIsAvailable() {
        assertTrue(copier.isModuleAvailable(RuntimeModule.AWS_S3),
            "aws_s3.u module should be available as a resource");
    }
    
    @Test
    void testGetS3ModuleContent() {
        String content = copier.getModuleContent(RuntimeModule.AWS_S3);
        
        assertNotNull(content, "Module content should not be null");
        assertFalse(content.isEmpty(), "Module content should not be empty");
        
        // Verify expected content
        assertTrue(content.contains("aws.s3.buildUrl"),
            "Module should define aws.s3.buildUrl function");
        assertTrue(content.contains("aws.s3.isValidBucketName"),
            "Module should define aws.s3.isValidBucketName function");
        assertTrue(content.contains("aws.s3.urlEncodeKey"),
            "Module should define aws.s3.urlEncodeKey function");
    }
    
    @Test
    void testS3ModuleHasUrlBuilding() {
        String content = copier.getModuleContent(RuntimeModule.AWS_S3);
        
        assertTrue(content.contains("aws.s3.buildUrl"),
            "Module should have buildUrl function");
        assertTrue(content.contains("aws.s3.buildUrlWithQuery"),
            "Module should have buildUrlWithQuery function");
        assertTrue(content.contains("aws.s3.buildBucketUrl"),
            "Module should have buildBucketUrl function");
        assertTrue(content.contains("usePathStyle"),
            "Module should support path-style addressing");
        assertTrue(content.contains("Virtual-hosted"),
            "Module should document virtual-hosted style");
    }
    
    @Test
    void testS3ModuleHasBucketValidation() {
        String content = copier.getModuleContent(RuntimeModule.AWS_S3);
        
        assertTrue(content.contains("aws.s3.isValidBucketName"),
            "Module should have isValidBucketName function");
        assertTrue(content.contains("aws.s3.isValidBucketChar"),
            "Module should have isValidBucketChar function");
        assertTrue(content.contains("xn--"),
            "Module should check for xn-- prefix");
        assertTrue(content.contains("-s3alias"),
            "Module should check for -s3alias suffix");
    }
    
    @Test
    void testS3ModuleHasEndpointHelpers() {
        String content = copier.getModuleContent(RuntimeModule.AWS_S3);
        
        assertTrue(content.contains("aws.s3.defaultEndpoint"),
            "Module should have defaultEndpoint function");
        assertTrue(content.contains("aws.s3.accelerateEndpoint"),
            "Module should have accelerateEndpoint constant");
        assertTrue(content.contains("aws.s3.dualStackEndpoint"),
            "Module should have dualStackEndpoint function");
        assertTrue(content.contains("aws.s3.localStackEndpoint"),
            "Module should have localStackEndpoint function");
    }
    
    @Test
    void testS3ModuleHasKeyHelpers() {
        String content = copier.getModuleContent(RuntimeModule.AWS_S3);
        
        assertTrue(content.contains("aws.s3.getFileName"),
            "Module should have getFileName function");
        assertTrue(content.contains("aws.s3.getDirectory"),
            "Module should have getDirectory function");
        assertTrue(content.contains("aws.s3.getExtension"),
            "Module should have getExtension function");
        assertTrue(content.contains("aws.s3.joinKey"),
            "Module should have joinKey function");
    }
    
    @Test
    void testS3ModuleHasDocumentation() {
        String content = copier.getModuleContent(RuntimeModule.AWS_S3);
        
        assertTrue(content.contains("{{"),
            "Module should have doc comments");
        assertTrue(content.contains("Build an S3 URL with bucket routing"),
            "Module should document buildUrl function");
        assertTrue(content.contains("S3 bucket naming rules"),
            "Module should document bucket naming rules");
    }
    
    @Test
    void testCopyS3Module() {
        boolean result = copier.copyModule(RuntimeModule.AWS_S3);
        
        assertTrue(result, "Copy should succeed");
        assertTrue(manifest.hasFile("src/aws_s3.u"),
            "Module should be written to manifest");
    }
    
    @Test
    void testCopyAwsModulesIncludesS3() {
        List<String> copied = copier.copyAwsModules();
        
        assertTrue(copied.contains("aws_s3.u"),
            "Should copy aws_s3.u");
        assertTrue(copied.contains("aws_http.u"),
            "Should also copy aws_http.u");
        assertTrue(copied.contains("aws_sigv4.u"),
            "Should also copy aws_sigv4.u");
        assertTrue(copied.contains("aws_xml.u"),
            "Should also copy aws_xml.u");
    }
    
    @Test
    void testS3ModuleEnum() {
        RuntimeModule s3 = RuntimeModule.AWS_S3;
        
        assertEquals("aws_s3.u", s3.getFilename());
        assertEquals("S3-specific utilities", s3.getDescription());
        assertEquals("runtime/aws_s3.u", s3.getResourcePath());
    }
    
    // ========== Config Module Tests ==========
    
    @Test
    void testConfigModuleIsAvailable() {
        assertTrue(copier.isModuleAvailable(RuntimeModule.AWS_CONFIG),
            "aws_config.u module should be available as a resource");
    }
    
    @Test
    void testGetConfigModuleContent() {
        String content = copier.getModuleContent(RuntimeModule.AWS_CONFIG);
        
        assertNotNull(content, "Module content should not be null");
        assertFalse(content.isEmpty(), "Module content should not be empty");
        
        // Verify expected content
        assertTrue(content.contains("aws.config.Credentials"),
            "Module should define aws.config.Credentials type");
        assertTrue(content.contains("aws.config.S3Config"),
            "Module should define aws.config.S3Config type");
        assertTrue(content.contains("aws.config.ServiceConfig"),
            "Module should define aws.config.ServiceConfig type");
    }
    
    @Test
    void testConfigModuleHasCredentialsTypes() {
        String content = copier.getModuleContent(RuntimeModule.AWS_CONFIG);
        
        assertTrue(content.contains("aws.config.Credentials"),
            "Module should have Credentials type");
        assertTrue(content.contains("aws.config.basicCredentials"),
            "Module should have basicCredentials function");
        assertTrue(content.contains("aws.config.temporaryCredentials"),
            "Module should have temporaryCredentials function");
        assertTrue(content.contains("aws.config.hasSessionToken"),
            "Module should have hasSessionToken function");
    }
    
    @Test
    void testConfigModuleHasS3Config() {
        String content = copier.getModuleContent(RuntimeModule.AWS_CONFIG);
        
        assertTrue(content.contains("aws.config.S3Config"),
            "Module should have S3Config type");
        assertTrue(content.contains("aws.config.s3Config"),
            "Module should have s3Config function");
        assertTrue(content.contains("aws.config.s3ConfigPathStyle"),
            "Module should have s3ConfigPathStyle function");
        assertTrue(content.contains("aws.config.s3ConfigCustom"),
            "Module should have s3ConfigCustom function");
        assertTrue(content.contains("aws.config.s3ConfigLocalStack"),
            "Module should have s3ConfigLocalStack function");
    }
    
    @Test
    void testConfigModuleHasRegionHelpers() {
        String content = copier.getModuleContent(RuntimeModule.AWS_CONFIG);
        
        assertTrue(content.contains("aws.config.usEast1"),
            "Module should have usEast1 constant");
        assertTrue(content.contains("aws.config.euWest1"),
            "Module should have euWest1 constant");
        assertTrue(content.contains("aws.config.isValidRegion"),
            "Module should have isValidRegion function");
        assertTrue(content.contains("aws.config.defaultRegion"),
            "Module should have defaultRegion constant");
    }
    
    @Test
    void testConfigModuleHasDefaults() {
        String content = copier.getModuleContent(RuntimeModule.AWS_CONFIG);
        
        assertTrue(content.contains("aws.config.defaultTimeout"),
            "Module should have defaultTimeout constant");
        assertTrue(content.contains("aws.config.defaultMaxRetries"),
            "Module should have defaultMaxRetries constant");
    }
    
    @Test
    void testConfigModuleHasDocumentation() {
        String content = copier.getModuleContent(RuntimeModule.AWS_CONFIG);
        
        assertTrue(content.contains("{{"),
            "Module should have doc comments");
        assertTrue(content.contains("AWS credentials for authenticating requests"),
            "Module should document Credentials type");
        assertTrue(content.contains("Configuration for Amazon S3"),
            "Module should document S3Config type");
    }
    
    @Test
    void testCopyConfigModule() {
        boolean result = copier.copyModule(RuntimeModule.AWS_CONFIG);
        
        assertTrue(result, "Copy should succeed");
        assertTrue(manifest.hasFile("src/aws_config.u"),
            "Module should be written to manifest");
    }
    
    @Test
    void testCopyAwsModulesIncludesConfig() {
        List<String> copied = copier.copyAwsModules();
        
        assertTrue(copied.contains("aws_config.u"),
            "Should copy aws_config.u");
        assertTrue(copied.contains("aws_s3.u"),
            "Should also copy aws_s3.u");
        assertTrue(copied.contains("aws_http.u"),
            "Should also copy aws_http.u");
        assertTrue(copied.contains("aws_sigv4.u"),
            "Should also copy aws_sigv4.u");
        assertTrue(copied.contains("aws_xml.u"),
            "Should also copy aws_xml.u");
    }
    
    @Test
    void testConfigModuleEnum() {
        RuntimeModule config = RuntimeModule.AWS_CONFIG;
        
        assertEquals("aws_config.u", config.getFilename());
        assertEquals("Configuration types", config.getDescription());
        assertEquals("runtime/aws_config.u", config.getResourcePath());
    }
    
    // ========== Credentials Module Tests ==========
    
    @Test
    void testCredentialsModuleIsAvailable() {
        assertTrue(copier.isModuleAvailable(RuntimeModule.AWS_CREDENTIALS),
            "aws_credentials.u module should be available as a resource");
    }
    
    @Test
    void testGetCredentialsModuleContent() {
        String content = copier.getModuleContent(RuntimeModule.AWS_CREDENTIALS);
        
        assertNotNull(content, "Module content should not be null");
        assertFalse(content.isEmpty(), "Module content should not be empty");
        
        // Verify expected content
        assertTrue(content.contains("aws.credentials.fromEnvironment"),
            "Module should define fromEnvironment function");
        assertTrue(content.contains("aws.credentials.defaultCredentials"),
            "Module should define defaultCredentials function");
        assertTrue(content.contains("aws.credentials.defaultRegion"),
            "Module should define defaultRegion function");
    }
    
    @Test
    void testCredentialsModuleHasEnvironmentLoading() {
        String content = copier.getModuleContent(RuntimeModule.AWS_CREDENTIALS);
        
        assertTrue(content.contains("AWS_ACCESS_KEY_ID"),
            "Module should reference AWS_ACCESS_KEY_ID");
        assertTrue(content.contains("AWS_SECRET_ACCESS_KEY"),
            "Module should reference AWS_SECRET_ACCESS_KEY");
        assertTrue(content.contains("AWS_SESSION_TOKEN"),
            "Module should reference AWS_SESSION_TOKEN");
        assertTrue(content.contains("aws.credentials.fromEnvironment"),
            "Module should have fromEnvironment function");
    }
    
    @Test
    void testCredentialsModuleHasFileLoading() {
        String content = copier.getModuleContent(RuntimeModule.AWS_CREDENTIALS);
        
        assertTrue(content.contains("aws.credentials.defaultCredentialsFilePath"),
            "Module should have defaultCredentialsFilePath function");
        assertTrue(content.contains("aws.credentials.defaultConfigFilePath"),
            "Module should have defaultConfigFilePath function");
        assertTrue(content.contains("/.aws/credentials"),
            "Module should reference credentials file path");
        assertTrue(content.contains("/.aws/config"),
            "Module should reference config file path");
    }
    
    @Test
    void testCredentialsModuleHasProviderChain() {
        String content = copier.getModuleContent(RuntimeModule.AWS_CREDENTIALS);
        
        assertTrue(content.contains("aws.credentials.defaultCredentials"),
            "Module should have defaultCredentials function");
        assertTrue(content.contains("aws.credentials.lookupEntry"),
            "Module should have lookupEntry function");
        assertTrue(content.contains("aws.credentials.profileFromEnvironment"),
            "Module should have profileFromEnvironment function");
    }
    
    @Test
    void testCredentialsModuleHasRegionResolution() {
        String content = copier.getModuleContent(RuntimeModule.AWS_CREDENTIALS);
        
        assertTrue(content.contains("AWS_REGION"),
            "Module should reference AWS_REGION");
        assertTrue(content.contains("AWS_DEFAULT_REGION"),
            "Module should reference AWS_DEFAULT_REGION");
        assertTrue(content.contains("aws.credentials.regionFromEnvironment"),
            "Module should have regionFromEnvironment function");
        assertTrue(content.contains("aws.credentials.defaultRegion"),
            "Module should have defaultRegion function");
    }
    
    @Test
    void testCredentialsModuleHasConvenienceFunctions() {
        String content = copier.getModuleContent(RuntimeModule.AWS_CREDENTIALS);
        
        assertTrue(content.contains("aws.credentials.envAccessKeyId"),
            "Module should have envAccessKeyId constant");
        assertTrue(content.contains("aws.credentials.envSecretAccessKey"),
            "Module should have envSecretAccessKey constant");
        assertTrue(content.contains("aws.credentials.envSessionToken"),
            "Module should have envSessionToken constant");
    }
    
    @Test
    void testCredentialsModuleHasDocumentation() {
        String content = copier.getModuleContent(RuntimeModule.AWS_CREDENTIALS);
        
        assertTrue(content.contains("{{"),
            "Module should have doc comments");
        assertTrue(content.contains("Load credentials from environment variables"),
            "Module should document fromEnvironment function");
        assertTrue(content.contains("credential provider chain"),
            "Module should mention provider chain");
    }
    
    @Test
    void testCopyCredentialsModule() {
        boolean result = copier.copyModule(RuntimeModule.AWS_CREDENTIALS);
        
        assertTrue(result, "Copy should succeed");
        assertTrue(manifest.hasFile("src/aws_credentials.u"),
            "Module should be written to manifest");
    }
    
    @Test
    void testCopyAwsModulesIncludesCredentials() {
        List<String> copied = copier.copyAwsModules();
        
        assertTrue(copied.contains("aws_credentials.u"),
            "Should copy aws_credentials.u");
        assertTrue(copied.contains("aws_config.u"),
            "Should also copy aws_config.u");
        assertTrue(copied.contains("aws_s3.u"),
            "Should also copy aws_s3.u");
        assertTrue(copied.contains("aws_http.u"),
            "Should also copy aws_http.u");
        assertTrue(copied.contains("aws_sigv4.u"),
            "Should also copy aws_sigv4.u");
        assertTrue(copied.contains("aws_xml.u"),
            "Should also copy aws_xml.u");
    }
    
    @Test
    void testCredentialsModuleEnum() {
        RuntimeModule creds = RuntimeModule.AWS_CREDENTIALS;
        
        assertEquals("aws_credentials.u", creds.getFilename());
        assertEquals("Credential loading", creds.getDescription());
        assertEquals("runtime/aws_credentials.u", creds.getResourcePath());
    }
}
