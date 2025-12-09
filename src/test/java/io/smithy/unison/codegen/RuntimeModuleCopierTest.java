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
}
