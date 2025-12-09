package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SigV4Generator}.
 * 
 * <p>Verifies that the generator produces correct Unison code for AWS SigV4
 * request signing, including types, helper functions, and the main signing
 * algorithm.
 */
class SigV4GeneratorTest {
    
    private SigV4Generator generator;
    private UnisonWriter writer;
    
    @BeforeEach
    void setUp() {
        generator = new SigV4Generator();
        writer = new UnisonWriter("test");
    }
    
    @Test
    void testGenerateCredentialsType() {
        generator.generateCredentialsType(writer);
        String output = writer.toString();
        
        // Verify type definition
        assertTrue(output.contains("type Aws.Credentials = {"), 
            "Should generate Aws.Credentials type");
        assertTrue(output.contains("accessKeyId : Text"), 
            "Should have accessKeyId field");
        assertTrue(output.contains("secretAccessKey : Text"), 
            "Should have secretAccessKey field");
        assertTrue(output.contains("sessionToken : Optional Text"), 
            "Should have optional sessionToken field");
        
        // Verify helper constructors
        assertTrue(output.contains("Aws.Credentials.basic"), 
            "Should generate basic constructor");
        assertTrue(output.contains("Aws.Credentials.withSessionToken"), 
            "Should generate withSessionToken constructor");
    }
    
    @Test
    void testGenerateSigningConfigType() {
        generator.generateSigningConfigType(writer);
        String output = writer.toString();
        
        assertTrue(output.contains("type Aws.SigningConfig = {"), 
            "Should generate Aws.SigningConfig type");
        assertTrue(output.contains("region : Text"), 
            "Should have region field");
        assertTrue(output.contains("service : Text"), 
            "Should have service field");
        assertTrue(output.contains("credentials : Aws.Credentials"), 
            "Should have credentials field");
    }
    
    @Test
    void testGenerateCredentialScopeType() {
        generator.generateCredentialScopeType(writer);
        String output = writer.toString();
        
        assertTrue(output.contains("type Aws.CredentialScope = {"), 
            "Should generate Aws.CredentialScope type");
        assertTrue(output.contains("date : Text"), 
            "Should have date field");
        assertTrue(output.contains("Aws.CredentialScope.toText"), 
            "Should generate toText function");
        assertTrue(output.contains("aws4_request"), 
            "toText should include aws4_request terminator");
    }
    
    @Test
    void testGenerateTimestampFunction() {
        generator.generateTimestampFunction(writer);
        String output = writer.toString();
        
        assertTrue(output.contains("Aws.SigV4.getTimestamp"), 
            "Should generate getTimestamp function");
        assertTrue(output.contains("'{IO}"), 
            "Should have IO ability");
        assertTrue(output.contains("Instant.toBasicISO8601"), 
            "Should format as ISO8601");
        
        assertTrue(output.contains("Aws.SigV4.getDateStamp"), 
            "Should generate getDateStamp function");
        assertTrue(output.contains("Text.take 8"), 
            "Should extract first 8 characters for date");
    }
    
    @Test
    void testGenerateHashPayloadFunction() {
        generator.generateHashPayloadFunction(writer);
        String output = writer.toString();
        
        assertTrue(output.contains("Aws.SigV4.hashPayload"), 
            "Should generate hashPayload function");
        assertTrue(output.contains("Bytes -> Text"), 
            "Should take Bytes and return Text");
        assertTrue(output.contains("hashBytes Sha2_256"), 
            "Should use SHA-256");
        assertTrue(output.contains("Bytes.toHex"), 
            "Should convert to hex");
    }
    
    @Test
    void testGenerateCanonicalHeadersFunction() {
        generator.generateCanonicalHeadersFunction(writer);
        String output = writer.toString();
        
        assertTrue(output.contains("Aws.SigV4.canonicalHeaders"), 
            "Should generate canonicalHeaders function");
        assertTrue(output.contains("[(Text, Text)] -> Text"), 
            "Should take header list and return Text");
        assertTrue(output.contains("Text.toLowercase"), 
            "Should lowercase header names");
        assertTrue(output.contains("List.sortBy"), 
            "Should sort headers");
    }
    
    @Test
    void testGenerateSignedHeadersFunction() {
        generator.generateSignedHeadersFunction(writer);
        String output = writer.toString();
        
        assertTrue(output.contains("Aws.SigV4.signedHeaders"), 
            "Should generate signedHeaders function");
        assertTrue(output.contains("List.sort"), 
            "Should sort header names");
        assertTrue(output.contains("Text.join \";\""), 
            "Should join with semicolons");
    }
    
    @Test
    void testGenerateCanonicalRequestFunction() {
        generator.generateCanonicalRequestFunction(writer);
        String output = writer.toString();
        
        assertTrue(output.contains("Aws.SigV4.canonicalRequest"), 
            "Should generate canonicalRequest function");
        assertTrue(output.contains("Text -> Text -> Text -> [(Text, Text)] -> Bytes -> Text"), 
            "Should have correct signature");
        assertTrue(output.contains("Text.join \"\\n\""), 
            "Should join components with newlines");
        assertTrue(output.contains("Aws.SigV4.canonicalHeaders"), 
            "Should call canonicalHeaders");
        assertTrue(output.contains("Aws.SigV4.hashPayload"), 
            "Should call hashPayload");
    }
    
    @Test
    void testGenerateStringToSignFunction() {
        generator.generateStringToSignFunction(writer);
        String output = writer.toString();
        
        assertTrue(output.contains("Aws.SigV4.stringToSign"), 
            "Should generate stringToSign function");
        assertTrue(output.contains("AWS4-HMAC-SHA256"), 
            "Should include algorithm identifier");
        assertTrue(output.contains("hashBytes Sha2_256"), 
            "Should hash canonical request");
    }
    
    @Test
    void testGenerateDeriveSigningKeyFunction() {
        generator.generateDeriveSigningKeyFunction(writer);
        String output = writer.toString();
        
        assertTrue(output.contains("Aws.SigV4.deriveSigningKey"), 
            "Should generate deriveSigningKey function");
        assertTrue(output.contains("Text -> Text -> Text -> Text -> Bytes"), 
            "Should have correct signature");
        assertTrue(output.contains("AWS4"), 
            "Should prepend AWS4 to secret key");
        assertTrue(output.contains("hmacBytes Sha2_256"), 
            "Should use HMAC-SHA256");
        assertTrue(output.contains("kDate"), 
            "Should derive kDate");
        assertTrue(output.contains("kRegion"), 
            "Should derive kRegion");
        assertTrue(output.contains("kService"), 
            "Should derive kService");
        assertTrue(output.contains("kSigning"), 
            "Should derive kSigning");
    }
    
    @Test
    void testGenerateSignatureFunction() {
        generator.generateSignatureFunction(writer);
        String output = writer.toString();
        
        assertTrue(output.contains("Aws.SigV4.signature"), 
            "Should generate signature function");
        assertTrue(output.contains("Bytes -> Text -> Text"), 
            "Should take signing key and string to sign");
        assertTrue(output.contains("hmacBytes Sha2_256"), 
            "Should use HMAC-SHA256");
        assertTrue(output.contains("Bytes.toHex"), 
            "Should return hex-encoded signature");
    }
    
    @Test
    void testGenerateAuthorizationHeaderFunction() {
        generator.generateAuthorizationHeaderFunction(writer);
        String output = writer.toString();
        
        assertTrue(output.contains("Aws.SigV4.authorizationHeader"), 
            "Should generate authorizationHeader function");
        assertTrue(output.contains("AWS4-HMAC-SHA256 Credential="), 
            "Should include algorithm prefix");
        assertTrue(output.contains("SignedHeaders="), 
            "Should include SignedHeaders");
        assertTrue(output.contains("Signature="), 
            "Should include Signature");
    }
    
    @Test
    void testGenerateSignRequestFunction() {
        generator.generateSignRequestFunction(writer);
        String output = writer.toString();
        
        assertTrue(output.contains("Aws.SigV4.signRequest"), 
            "Should generate signRequest function");
        assertTrue(output.contains("Aws.SigningConfig ->"), 
            "Should take SigningConfig");
        assertTrue(output.contains("'{IO}"), 
            "Should have IO ability");
        assertTrue(output.contains("[(Text, Text)]"), 
            "Should return header list");
        
        // Verify complete signing flow
        assertTrue(output.contains("Aws.SigV4.getTimestamp"), 
            "Should get timestamp");
        assertTrue(output.contains("Aws.SigV4.hashPayload"), 
            "Should hash payload");
        assertTrue(output.contains("Aws.SigV4.canonicalRequest"), 
            "Should build canonical request");
        assertTrue(output.contains("Aws.SigV4.stringToSign"), 
            "Should build string to sign");
        assertTrue(output.contains("Aws.SigV4.deriveSigningKey"), 
            "Should derive signing key");
        assertTrue(output.contains("Aws.SigV4.signature"), 
            "Should calculate signature");
        assertTrue(output.contains("Aws.SigV4.authorizationHeader"), 
            "Should build authorization header");
        
        // Verify headers
        assertTrue(output.contains("Authorization"), 
            "Should include Authorization header");
        assertTrue(output.contains("X-Amz-Date"), 
            "Should include X-Amz-Date header");
        assertTrue(output.contains("X-Amz-Content-Sha256"), 
            "Should include X-Amz-Content-Sha256 header");
        assertTrue(output.contains("X-Amz-Security-Token"), 
            "Should handle session token");
    }
    
    @Test
    void testGenerate() {
        generator.generate(writer);
        String output = writer.toString();
        
        // Verify all major components are present
        assertTrue(output.contains("type Aws.Credentials"), 
            "Should generate Credentials type");
        assertTrue(output.contains("type Aws.SigningConfig"), 
            "Should generate SigningConfig type");
        assertTrue(output.contains("type Aws.CredentialScope"), 
            "Should generate CredentialScope type");
        assertTrue(output.contains("Aws.SigV4.signRequest"), 
            "Should generate signRequest function");
    }
    
    @Test
    void testGenerateAll() {
        generator.generateAll(writer);
        String output = writer.toString();
        
        // Verify section comments
        assertTrue(output.contains("AWS SigV4 Authentication"), 
            "Should have authentication section");
        assertTrue(output.contains("--- Types ---"), 
            "Should have types section");
        assertTrue(output.contains("--- Utility Functions ---"), 
            "Should have utility section");
        assertTrue(output.contains("--- Canonical Request Building ---"), 
            "Should have canonical request section");
        assertTrue(output.contains("--- Signing ---"), 
            "Should have signing section");
        assertTrue(output.contains("--- Main Entry Points ---"), 
            "Should have main entry points section");
        
        // Verify helper function
        assertTrue(output.contains("Aws.SigV4.addSigningHeaders"), 
            "Should generate addSigningHeaders helper");
    }
    
    @Test
    void testDocumentation() {
        generator.generate(writer);
        String output = writer.toString();
        
        // Verify doc comments are present
        assertTrue(output.contains("{{"), 
            "Should have doc comments");
        assertTrue(output.contains("AWS credentials for signing requests"), 
            "Should document Credentials type");
        assertTrue(output.contains("AWS4-HMAC-SHA256"), 
            "Should document algorithm");
    }
}
