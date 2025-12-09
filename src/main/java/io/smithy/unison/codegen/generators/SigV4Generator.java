package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonWriter;

/**
 * Generates Unison functions for AWS SigV4 request signing.
 * 
 * <p>AWS Signature Version 4 (SigV4) is the signing process required to
 * authenticate requests to AWS services. This generator creates Unison
 * functions that implement the full signing algorithm.
 * 
 * <h2>SigV4 Signing Process</h2>
 * <ol>
 *   <li>Create a canonical request</li>
 *   <li>Create a string to sign</li>
 *   <li>Calculate the signature</li>
 *   <li>Add the signature to the request</li>
 * </ol>
 * 
 * <h2>Generated Types</h2>
 * <ul>
 *   <li>{@code Aws.Credentials} - AWS access credentials</li>
 *   <li>{@code Aws.SigningConfig} - Signing configuration</li>
 *   <li>{@code Aws.CredentialScope} - Credential scope for signing</li>
 * </ul>
 * 
 * <h2>Generated Functions</h2>
 * <ul>
 *   <li>{@code Aws.SigV4.signRequest} - Sign an HTTP request</li>
 *   <li>{@code Aws.SigV4.canonicalRequest} - Build canonical request string</li>
 *   <li>{@code Aws.SigV4.stringToSign} - Build string to sign</li>
 *   <li>{@code Aws.SigV4.deriveSigningKey} - Derive HMAC signing key</li>
 *   <li>{@code Aws.SigV4.signature} - Calculate signature</li>
 *   <li>{@code Aws.SigV4.authorizationHeader} - Build Authorization header</li>
 * </ul>
 * 
 * <h2>References</h2>
 * <ul>
 *   <li><a href="https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html">
 *       AWS SigV4 Documentation</a></li>
 *   <li><a href="https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_sigv-create-signed-request.html">
 *       Create a Signed AWS Request</a></li>
 * </ul>
 */
public class SigV4Generator {
    
    /**
     * Creates a new SigV4Generator.
     */
    public SigV4Generator() {
    }
    
    /**
     * Generates all SigV4 signing types and functions.
     * 
     * @param writer The Unison code writer
     */
    public void generate(UnisonWriter writer) {
        writer.writeComment("=== AWS SigV4 Authentication ===");
        writer.writeBlankLine();
        
        generateCredentialsType(writer);
        generateSigningConfigType(writer);
        generateCredentialScopeType(writer);
        generateTimestampFunction(writer);
        generateHashPayloadFunction(writer);
        generateCanonicalHeadersFunction(writer);
        generateSignedHeadersFunction(writer);
        generateCanonicalRequestFunction(writer);
        generateStringToSignFunction(writer);
        generateDeriveSigningKeyFunction(writer);
        generateSignatureFunction(writer);
        generateAuthorizationHeaderFunction(writer);
        generateSignRequestFunction(writer);
    }
    
    /**
     * Generates the AWS Credentials type.
     * 
     * <p>Holds AWS access credentials for signing requests.
     */
    public void generateCredentialsType(UnisonWriter writer) {
        writer.writeDocComment(
            "AWS credentials for signing requests.\n\n" +
            "Contains the access key ID, secret access key, and optional session token\n" +
            "for temporary credentials (e.g., from STS AssumeRole).");
        
        writer.write("type Aws.Credentials = {");
        writer.indent();
        writer.write("accessKeyId : Text,");
        writer.write("secretAccessKey : Text,");
        writer.write("sessionToken : Optional Text");
        writer.dedent();
        writer.write("}");
        writer.writeBlankLine();
        
        // Constructor helper
        writer.writeDocComment("Create credentials without a session token.");
        writer.writeSignature("Aws.Credentials.basic", "Text -> Text -> Aws.Credentials");
        writer.write("Aws.Credentials.basic accessKey secretKey =");
        writer.indent();
        writer.write("Aws.Credentials accessKey secretKey None");
        writer.dedent();
        writer.writeBlankLine();
        
        // Constructor with session token
        writer.writeDocComment("Create credentials with a session token (for temporary credentials).");
        writer.writeSignature("Aws.Credentials.withSessionToken", "Text -> Text -> Text -> Aws.Credentials");
        writer.write("Aws.Credentials.withSessionToken accessKey secretKey token =");
        writer.indent();
        writer.write("Aws.Credentials accessKey secretKey (Some token)");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the SigningConfig type.
     */
    public void generateSigningConfigType(UnisonWriter writer) {
        writer.writeDocComment(
            "Configuration for signing AWS requests.\n\n" +
            "Contains the region, service name, and credentials needed for signing.");
        
        writer.write("type Aws.SigningConfig = {");
        writer.indent();
        writer.write("region : Text,");
        writer.write("service : Text,");
        writer.write("credentials : Aws.Credentials");
        writer.dedent();
        writer.write("}");
        writer.writeBlankLine();
    }
    
    /**
     * Generates the CredentialScope type.
     */
    public void generateCredentialScopeType(UnisonWriter writer) {
        writer.writeDocComment(
            "Credential scope for AWS SigV4 signing.\n\n" +
            "Format: YYYYMMDD/region/service/aws4_request");
        
        writer.write("type Aws.CredentialScope = {");
        writer.indent();
        writer.write("date : Text,");
        writer.write("region : Text,");
        writer.write("service : Text");
        writer.dedent();
        writer.write("}");
        writer.writeBlankLine();
        
        // toText function
        writer.writeDocComment("Convert credential scope to string format: YYYYMMDD/region/service/aws4_request");
        writer.writeSignature("Aws.CredentialScope.toText", "Aws.CredentialScope -> Text");
        writer.write("Aws.CredentialScope.toText scope =");
        writer.indent();
        writer.write("scope.date ++ \"/\" ++ scope.region ++ \"/\" ++ scope.service ++ \"/aws4_request\"");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the timestamp function.
     */
    public void generateTimestampFunction(UnisonWriter writer) {
        writer.writeDocComment(
            "Get the current AWS-formatted timestamp.\n\n" +
            "Returns timestamp in ISO 8601 basic format: YYYYMMDD'T'HHMMSS'Z'\n" +
            "Example: \"20231215T143052Z\"");
        
        writer.writeSignature("Aws.SigV4.getTimestamp", "'{IO} Text");
        writer.write("Aws.SigV4.getTimestamp _ =");
        writer.indent();
        writer.write("-- Get current UTC time and format as AWS timestamp");
        writer.write("-- Format: YYYYMMDD'T'HHMMSS'Z' (ISO 8601 basic format)");
        writer.write("now = !systemTime");
        writer.write("Instant.toBasicISO8601 now");
        writer.dedent();
        writer.writeBlankLine();
        
        // Date stamp extraction
        writer.writeDocComment("Extract the date stamp (YYYYMMDD) from an AWS timestamp.");
        writer.writeSignature("Aws.SigV4.getDateStamp", "Text -> Text");
        writer.write("Aws.SigV4.getDateStamp timestamp =");
        writer.indent();
        writer.write("Text.take 8 timestamp");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the payload hash function.
     */
    public void generateHashPayloadFunction(UnisonWriter writer) {
        writer.writeDocComment(
            "Hash the request payload using SHA-256.\n\n" +
            "Returns the lowercase hex-encoded hash of the body.\n" +
            "For GET requests with no body, use Bytes.empty.");
        
        writer.writeSignature("Aws.SigV4.hashPayload", "Bytes -> Text");
        writer.write("Aws.SigV4.hashPayload body =");
        writer.indent();
        writer.write("hashBytes Sha2_256 body |> Bytes.toHex");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the canonical headers function.
     */
    public void generateCanonicalHeadersFunction(UnisonWriter writer) {
        writer.writeDocComment(
            "Create canonical headers string for signing.\n\n" +
            "Headers are lowercased, sorted alphabetically, and formatted as:\n" +
            "header-name:header-value\\n\n" +
            "Multiple values for the same header are joined with commas.\n" +
            "Leading/trailing whitespace is trimmed from values.");
        
        writer.writeSignature("Aws.SigV4.canonicalHeaders", "[(Text, Text)] -> Text");
        writer.write("Aws.SigV4.canonicalHeaders headers =");
        writer.indent();
        writer.write("headers");
        writer.indent();
        writer.write("|> List.map (cases (name, value) -> (Text.toLowercase name, Text.trim value))");
        writer.write("|> List.sortBy Tuple.at1");
        writer.write("|> List.map (cases (name, value) -> name ++ \":\" ++ value)");
        writer.writeWithNoFormatting("|> Text.join \"\\n\"");
        writer.writeWithNoFormatting("|> (t -> t ++ \"\\n\")");
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the signed headers function.
     */
    public void generateSignedHeadersFunction(UnisonWriter writer) {
        writer.writeDocComment(
            "Create the signed headers list.\n\n" +
            "Returns a semicolon-separated list of lowercase header names.\n" +
            "Example: \"content-type;host;x-amz-date\"");
        
        writer.writeSignature("Aws.SigV4.signedHeaders", "[(Text, Text)] -> Text");
        writer.write("Aws.SigV4.signedHeaders headers =");
        writer.indent();
        writer.write("headers");
        writer.indent();
        writer.write("|> List.map (cases (name, _) -> Text.toLowercase name)");
        writer.write("|> List.sort");
        writer.writeWithNoFormatting("|> Text.join \";\"");
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the canonical request function.
     */
    public void generateCanonicalRequestFunction(UnisonWriter writer) {
        writer.writeDocComment(
            "Build the canonical request string.\n\n" +
            "The canonical request is a standardized format of the HTTP request used\n" +
            "in the signing process. Format:\n" +
            "  METHOD\\n\n" +
            "  CanonicalURI\\n\n" +
            "  CanonicalQueryString\\n\n" +
            "  CanonicalHeaders\\n\n" +
            "  SignedHeaders\\n\n" +
            "  HashedPayload");
        
        writer.writeSignature("Aws.SigV4.canonicalRequest", "Text -> Text -> Text -> [(Text, Text)] -> Bytes -> Text");
        writer.write("Aws.SigV4.canonicalRequest method path queryString headers body =");
        writer.indent();
        writer.write("let");
        writer.indent();
        writer.write("canonicalHeaders = Aws.SigV4.canonicalHeaders headers");
        writer.write("signedHeaders = Aws.SigV4.signedHeaders headers");
        writer.write("hashedPayload = Aws.SigV4.hashPayload body");
        writer.dedent();
        writer.writeWithNoFormatting("Text.join \"\\n\" [");
        writer.indent();
        writer.write("method,");
        writer.write("path,");
        writer.write("queryString,");
        writer.write("canonicalHeaders,");
        writer.write("signedHeaders,");
        writer.write("hashedPayload");
        writer.dedent();
        writer.write("]");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the string to sign function.
     */
    public void generateStringToSignFunction(UnisonWriter writer) {
        writer.writeDocComment(
            "Build the string to sign.\n\n" +
            "The string to sign includes the algorithm, timestamp, credential scope,\n" +
            "and hashed canonical request. Format:\n" +
            "  AWS4-HMAC-SHA256\\n\n" +
            "  Timestamp\\n\n" +
            "  CredentialScope\\n\n" +
            "  HashedCanonicalRequest");
        
        writer.writeSignature("Aws.SigV4.stringToSign", "Text -> Aws.CredentialScope -> Text -> Text");
        writer.write("Aws.SigV4.stringToSign timestamp scope canonicalRequest =");
        writer.indent();
        writer.write("let");
        writer.indent();
        writer.write("hashedCanonicalRequest = hashBytes Sha2_256 (Text.toUtf8 canonicalRequest) |> Bytes.toHex");
        writer.write("credentialScope = Aws.CredentialScope.toText scope");
        writer.dedent();
        writer.writeWithNoFormatting("Text.join \"\\n\" [");
        writer.indent();
        writer.write("\"AWS4-HMAC-SHA256\",");
        writer.write("timestamp,");
        writer.write("credentialScope,");
        writer.write("hashedCanonicalRequest");
        writer.dedent();
        writer.write("]");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the signing key derivation function.
     */
    public void generateDeriveSigningKeyFunction(UnisonWriter writer) {
        writer.writeDocComment(
            "Derive the signing key using HMAC-SHA256.\n\n" +
            "The signing key is derived by successively applying HMAC-SHA256:\n" +
            "  kDate = HMAC(\"AWS4\" + secretKey, dateStamp)\n" +
            "  kRegion = HMAC(kDate, region)\n" +
            "  kService = HMAC(kRegion, service)\n" +
            "  kSigning = HMAC(kService, \"aws4_request\")");
        
        writer.writeSignature("Aws.SigV4.deriveSigningKey", "Text -> Text -> Text -> Text -> Bytes");
        writer.write("Aws.SigV4.deriveSigningKey secretKey dateStamp region service =");
        writer.indent();
        writer.write("let");
        writer.indent();
        writer.writeWithNoFormatting("kSecret = Text.toUtf8 (\"AWS4\" ++ secretKey)");
        writer.write("kDate = hmacBytes Sha2_256 kSecret (Text.toUtf8 dateStamp)");
        writer.write("kRegion = hmacBytes Sha2_256 kDate (Text.toUtf8 region)");
        writer.write("kService = hmacBytes Sha2_256 kRegion (Text.toUtf8 service)");
        writer.write("kSigning = hmacBytes Sha2_256 kService (Text.toUtf8 \"aws4_request\")");
        writer.dedent();
        writer.write("kSigning");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the signature calculation function.
     */
    public void generateSignatureFunction(UnisonWriter writer) {
        writer.writeDocComment(
            "Calculate the request signature.\n\n" +
            "Uses HMAC-SHA256 with the signing key to sign the string to sign.\n" +
            "Returns the lowercase hex-encoded signature.");
        
        writer.writeSignature("Aws.SigV4.signature", "Bytes -> Text -> Text");
        writer.write("Aws.SigV4.signature signingKey stringToSign =");
        writer.indent();
        writer.write("hmacBytes Sha2_256 signingKey (Text.toUtf8 stringToSign) |> Bytes.toHex");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the authorization header function.
     */
    public void generateAuthorizationHeaderFunction(UnisonWriter writer) {
        writer.writeDocComment(
            "Build the Authorization header value.\n\n" +
            "Format:\n" +
            "  AWS4-HMAC-SHA256 Credential=accessKey/scope,SignedHeaders=headers,Signature=sig");
        
        writer.writeSignature("Aws.SigV4.authorizationHeader", "Text -> Aws.CredentialScope -> Text -> Text -> Text");
        writer.write("Aws.SigV4.authorizationHeader accessKeyId scope signedHeaders signature =");
        writer.indent();
        writer.write("let");
        writer.indent();
        writer.write("credential = accessKeyId ++ \"/\" ++ Aws.CredentialScope.toText scope");
        writer.dedent();
        writer.writeWithNoFormatting("\"AWS4-HMAC-SHA256 Credential=\" ++ credential ++ \",SignedHeaders=\" ++ signedHeaders ++ \",Signature=\" ++ signature");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the main sign request function.
     */
    public void generateSignRequestFunction(UnisonWriter writer) {
        writer.writeDocComment(
            "Sign an HTTP request using AWS SigV4.\n\n" +
            "This is the main entry point for request signing. It performs the full\n" +
            "SigV4 signing process and returns the headers to add to the request:\n" +
            "- Authorization header with the signature\n" +
            "- X-Amz-Date header with the timestamp\n" +
            "- X-Amz-Security-Token header (if using session credentials)\n" +
            "- X-Amz-Content-Sha256 header with the payload hash\n\n" +
            "Parameters:\n" +
            "- config: Signing configuration with region, service, and credentials\n" +
            "- method: HTTP method (GET, PUT, POST, DELETE, etc.)\n" +
            "- path: Request path (e.g., \"/bucket/key\")\n" +
            "- queryString: Query string without leading ? (e.g., \"version-id=123\")\n" +
            "- headers: Request headers as name-value pairs\n" +
            "- body: Request body bytes");
        
        writer.writeSignature("Aws.SigV4.signRequest", "Aws.SigningConfig -> Text -> Text -> Text -> [(Text, Text)] -> Bytes -> '{IO} [(Text, Text)]");
        writer.write("Aws.SigV4.signRequest config method path queryString headers body _ =");
        writer.indent();
        writer.write("let");
        writer.indent();
        writer.write("-- Get timestamp");
        writer.write("timestamp = !Aws.SigV4.getTimestamp");
        writer.write("dateStamp = Aws.SigV4.getDateStamp timestamp");
        writer.writeBlankLine();
        writer.write("-- Build credential scope");
        writer.write("scope = Aws.CredentialScope dateStamp config.region config.service");
        writer.writeBlankLine();
        writer.write("-- Hash payload");
        writer.write("payloadHash = Aws.SigV4.hashPayload body");
        writer.writeBlankLine();
        writer.write("-- Add required headers for signing");
        writer.write("headersWithAmz = headers");
        writer.indent();
        writer.writeWithNoFormatting("|> List.cons (\"x-amz-date\", timestamp)");
        writer.writeWithNoFormatting("|> List.cons (\"x-amz-content-sha256\", payloadHash)");
        writer.dedent();
        writer.writeBlankLine();
        writer.write("-- Build canonical request");
        writer.write("canonicalReq = Aws.SigV4.canonicalRequest method path queryString headersWithAmz body");
        writer.writeBlankLine();
        writer.write("-- Build string to sign");
        writer.write("strToSign = Aws.SigV4.stringToSign timestamp scope canonicalReq");
        writer.writeBlankLine();
        writer.write("-- Derive signing key and calculate signature");
        writer.write("signingKey = Aws.SigV4.deriveSigningKey config.credentials.secretAccessKey dateStamp config.region config.service");
        writer.write("sig = Aws.SigV4.signature signingKey strToSign");
        writer.writeBlankLine();
        writer.write("-- Build authorization header");
        writer.write("signedHdrs = Aws.SigV4.signedHeaders headersWithAmz");
        writer.write("authHeader = Aws.SigV4.authorizationHeader config.credentials.accessKeyId scope signedHdrs sig");
        writer.writeBlankLine();
        writer.write("-- Build final headers list");
        writer.write("baseHeaders = [");
        writer.indent();
        writer.write("(\"Authorization\", authHeader),");
        writer.write("(\"X-Amz-Date\", timestamp),");
        writer.write("(\"X-Amz-Content-Sha256\", payloadHash)");
        writer.dedent();
        writer.write("]");
        writer.writeBlankLine();
        writer.write("-- Add session token header if present");
        writer.dedent();
        writer.write("match config.credentials.sessionToken with");
        writer.indent();
        writer.write("Some token -> List.cons (\"X-Amz-Security-Token\", token) baseHeaders");
        writer.write("None -> baseHeaders");
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates a helper function to add signing headers to an existing request.
     */
    public void generateAddSigningHeaders(UnisonWriter writer) {
        writer.writeDocComment(
            "Add SigV4 signing headers to an existing header list.\n\n" +
            "Convenience function that signs the request and merges the signing\n" +
            "headers with the original headers.");
        
        writer.writeSignature("Aws.SigV4.addSigningHeaders", "Aws.SigningConfig -> Text -> Text -> Text -> [(Text, Text)] -> Bytes -> '{IO} [(Text, Text)]");
        writer.write("Aws.SigV4.addSigningHeaders config method path queryString headers body _ =");
        writer.indent();
        writer.write("let");
        writer.indent();
        writer.write("signingHeaders = Aws.SigV4.signRequest config method path queryString headers body !");
        writer.dedent();
        writer.write("headers ++ signingHeaders");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates all SigV4 functions including helpers.
     * 
     * @param writer The Unison code writer
     */
    public void generateAll(UnisonWriter writer) {
        writer.writeComment("=== AWS SigV4 Authentication ===");
        writer.writeComment("Implementation of AWS Signature Version 4 for request signing");
        writer.writeBlankLine();
        
        writer.writeComment("--- Types ---");
        writer.writeBlankLine();
        generateCredentialsType(writer);
        generateSigningConfigType(writer);
        generateCredentialScopeType(writer);
        
        writer.writeComment("--- Utility Functions ---");
        writer.writeBlankLine();
        generateTimestampFunction(writer);
        generateHashPayloadFunction(writer);
        
        writer.writeComment("--- Canonical Request Building ---");
        writer.writeBlankLine();
        generateCanonicalHeadersFunction(writer);
        generateSignedHeadersFunction(writer);
        generateCanonicalRequestFunction(writer);
        
        writer.writeComment("--- Signing ---");
        writer.writeBlankLine();
        generateStringToSignFunction(writer);
        generateDeriveSigningKeyFunction(writer);
        generateSignatureFunction(writer);
        generateAuthorizationHeaderFunction(writer);
        
        writer.writeComment("--- Main Entry Points ---");
        writer.writeBlankLine();
        generateSignRequestFunction(writer);
        generateAddSigningHeaders(writer);
    }
}
