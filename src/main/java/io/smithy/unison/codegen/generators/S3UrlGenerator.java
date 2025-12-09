package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonWriter;

/**
 * Generates Unison functions for S3-specific URL building.
 * 
 * <p>S3 uses special URL patterns based on bucket addressing style:
 * <ul>
 *   <li><b>Virtual-Hosted Style</b> (default): {@code bucket.s3.region.amazonaws.com/key}</li>
 *   <li><b>Path Style</b>: {@code s3.region.amazonaws.com/bucket/key}</li>
 * </ul>
 * 
 * <p>This generator creates Unison functions that handle:
 * <ul>
 *   <li>Virtual-hosted vs path-style URL routing</li>
 *   <li>URL encoding for object keys (including special characters and slashes)</li>
 *   <li>Query string building from parameters</li>
 * </ul>
 * 
 * <h2>Generated Functions</h2>
 * <ul>
 *   <li>{@code Aws.S3.buildUrl} - Build S3 URL with bucket routing</li>
 *   <li>{@code Aws.urlEncode} - URL encode a string</li>
 *   <li>{@code Aws.buildQueryString} - Build query string from parameters</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>
 * S3UrlGenerator generator = new S3UrlGenerator();
 * generator.generate(writer);
 * </pre>
 */
public class S3UrlGenerator {
    
    /**
     * Creates a new S3UrlGenerator.
     */
    public S3UrlGenerator() {
    }
    
    /**
     * Generates all S3 URL building functions.
     * 
     * @param writer The Unison code writer
     */
    public void generate(UnisonWriter writer) {
        generateUrlEncode(writer);
        generateBuildQueryString(writer);
        generateBuildS3Url(writer);
    }
    
    /**
     * Generates the URL encoding function.
     * 
     * <p>Generates:
     * <pre>
     * Aws.urlEncode : Text -> Text
     * Aws.urlEncode text =
     *   -- URL encode special characters
     *   ...
     * </pre>
     */
    public void generateUrlEncode(UnisonWriter writer) {
        writer.writeDocComment(
            "URL encode a string for use in URLs.\n\n" +
            "Encodes special characters according to RFC 3986.\n" +
            "Spaces become %20, slashes are preserved for S3 keys.");
        
        writer.writeSignature("Aws.urlEncode", "Text -> Text");
        writer.write("Aws.urlEncode text =");
        writer.indent();
        writer.write("-- Replace special characters with percent-encoded values");
        writer.write("text");
        writer.indent();
        writer.writeWithNoFormatting("|> Text.replace \" \" \"%20\"");
        writer.writeWithNoFormatting("|> Text.replace \"!\" \"%21\"");
        writer.writeWithNoFormatting("|> Text.replace \"#\" \"%23\"");
        writer.writeWithNoFormatting("|> Text.replace \"$\" \"%24\"");
        writer.writeWithNoFormatting("|> Text.replace \"&\" \"%26\"");
        writer.writeWithNoFormatting("|> Text.replace \"'\" \"%27\"");
        writer.writeWithNoFormatting("|> Text.replace \"(\" \"%28\"");
        writer.writeWithNoFormatting("|> Text.replace \")\" \"%29\"");
        writer.writeWithNoFormatting("|> Text.replace \"*\" \"%2A\"");
        writer.writeWithNoFormatting("|> Text.replace \"+\" \"%2B\"");
        writer.writeWithNoFormatting("|> Text.replace \",\" \"%2C\"");
        writer.writeWithNoFormatting("|> Text.replace \":\" \"%3A\"");
        writer.writeWithNoFormatting("|> Text.replace \";\" \"%3B\"");
        writer.writeWithNoFormatting("|> Text.replace \"=\" \"%3D\"");
        writer.writeWithNoFormatting("|> Text.replace \"?\" \"%3F\"");
        writer.writeWithNoFormatting("|> Text.replace \"@\" \"%40\"");
        writer.writeWithNoFormatting("|> Text.replace \"[\" \"%5B\"");
        writer.writeWithNoFormatting("|> Text.replace \"]\" \"%5D\"");
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the URL encoding function that also encodes slashes.
     * 
     * <p>Used for encoding path segments where slashes should be encoded.
     */
    public void generateUrlEncodePathSegment(UnisonWriter writer) {
        writer.writeDocComment(
            "URL encode a path segment, including slashes.\n\n" +
            "Use this for individual path segments, not full paths.");
        
        writer.writeSignature("Aws.urlEncodePathSegment", "Text -> Text");
        writer.write("Aws.urlEncodePathSegment text =");
        writer.indent();
        writer.write("Aws.urlEncode text |> Text.replace \"/\" \"%2F\"");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the query string building function.
     * 
     * <p>Generates:
     * <pre>
     * Aws.buildQueryString : [(Text, Optional Text)] -> Text
     * Aws.buildQueryString params =
     *   ...
     * </pre>
     */
    public void generateBuildQueryString(UnisonWriter writer) {
        writer.writeDocComment(
            "Build a query string from a list of parameter name-value pairs.\n\n" +
            "Filters out None values and URL encodes both names and values.\n" +
            "Returns empty string if no parameters, otherwise returns \"?param1=value1&param2=value2\".");
        
        writer.writeSignature("Aws.buildQueryString", "[(Text, Optional Text)] -> Text");
        writer.write("Aws.buildQueryString params =");
        writer.indent();
        writer.write("let");
        writer.indent();
        writer.write("-- Filter out None values and encode");
        writer.write("encoded = params");
        writer.indent();
        writer.write("|> List.filterMap (cases");
        writer.indent();
        writer.write("(name, Some value) -> Some (Aws.urlEncode name ++ \"=\" ++ Aws.urlEncode value)");
        writer.write("(_, None) -> None)");
        writer.dedent();
        writer.dedent();
        writer.dedent();
        writer.write("match encoded with");
        writer.indent();
        writer.write("[] -> \"\"");
        writer.write("parts -> \"?\" ++ Text.join \"&\" parts");
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the S3 URL building function.
     * 
     * <p>Supports both virtual-hosted style and path-style URLs:
     * <ul>
     *   <li>Virtual-hosted: {@code https://bucket.s3.region.amazonaws.com/key}</li>
     *   <li>Path-style: {@code https://s3.region.amazonaws.com/bucket/key}</li>
     * </ul>
     */
    public void generateBuildS3Url(UnisonWriter writer) {
        writer.writeDocComment(
            "Build an S3 URL with bucket routing.\n\n" +
            "Supports two addressing styles:\n" +
            "- Virtual-hosted style (default): https://bucket.endpoint/key\n" +
            "- Path style: https://endpoint/bucket/key\n\n" +
            "The style is determined by config.usePathStyle.");
        
        writer.writeSignature("Aws.S3.buildUrl", "Config -> Text -> Text -> Text");
        writer.write("Aws.S3.buildUrl config bucket key =");
        writer.indent();
        writer.write("let");
        writer.indent();
        writer.write("encodedKey = Aws.urlEncode key");
        writer.write("endpoint = config.endpoint");
        writer.dedent();
        writer.write("if config.usePathStyle then");
        writer.indent();
        writer.write("-- Path style: https://endpoint/bucket/key");
        writer.write("endpoint ++ \"/\" ++ bucket ++ \"/\" ++ encodedKey");
        writer.dedent();
        writer.write("else");
        writer.indent();
        writer.write("-- Virtual-hosted style: https://bucket.endpoint/key");
        writer.write("\"https://\" ++ bucket ++ \".\" ++ endpoint ++ \"/\" ++ encodedKey");
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the full S3 URL building function with query string support.
     */
    public void generateBuildS3UrlWithQuery(UnisonWriter writer) {
        writer.writeDocComment(
            "Build an S3 URL with bucket routing and query string.\n\n" +
            "Combines bucket routing with query parameters.");
        
        writer.writeSignature("Aws.S3.buildUrlWithQuery", "Config -> Text -> Text -> [(Text, Optional Text)] -> Text");
        writer.write("Aws.S3.buildUrlWithQuery config bucket key queryParams =");
        writer.indent();
        writer.write("let");
        writer.indent();
        writer.write("baseUrl = Aws.S3.buildUrl config bucket key");
        writer.write("queryString = Aws.buildQueryString queryParams");
        writer.dedent();
        writer.write("baseUrl ++ queryString");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates all S3 URL functions including helpers.
     * 
     * @param writer The Unison code writer
     */
    public void generateAll(UnisonWriter writer) {
        writer.writeComment("=== AWS URL Utilities ===");
        writer.writeBlankLine();
        
        generateUrlEncode(writer);
        generateUrlEncodePathSegment(writer);
        generateBuildQueryString(writer);
        
        writer.writeComment("=== S3 URL Building ===");
        writer.writeBlankLine();
        
        generateBuildS3Url(writer);
        generateBuildS3UrlWithQuery(writer);
    }
}
