package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpTrait;

/**
 * Protocol generator for REST-JSON protocol (aws.protocols#restJson1).
 * 
 * <p>Used by API Gateway, Step Functions, EventBridge, Lambda (streaming),
 * AppSync, IoT, Cognito, WAF, and other REST-JSON services.
 * 
 * <h2>Protocol Characteristics</h2>
 * <ul>
 *   <li>HTTP Method: From {@code @http} trait (GET, POST, PUT, DELETE)</li>
 *   <li>URI Path: From {@code @http} trait with {@code @httpLabel} substitution</li>
 *   <li>Query Parameters: From {@code @httpQuery} members</li>
 *   <li>Headers: From {@code @httpHeader} members</li>
 *   <li>Content-Type: {@code application/json}</li>
 *   <li>Request Body: JSON encoded (only unbound members)</li>
 *   <li>Response Body: JSON decoded</li>
 *   <li>Authentication: AWS SigV4</li>
 * </ul>
 * 
 * <h2>Key Difference from AWS JSON</h2>
 * <p>REST-JSON uses RESTful HTTP bindings (like REST-XML) but with JSON serialization
 * (like AWS JSON). HTTP-bound members ({@code @httpLabel}, {@code @httpQuery},
 * {@code @httpHeader}) are NOT included in the JSON body.
 * 
 * @see ProtocolGenerator
 * @see AwsJsonProtocolGenerator
 * @see RestXmlProtocolGenerator
 */
public class RestJsonProtocolGenerator implements ProtocolGenerator {
    
    /** Protocol trait ID for REST-JSON */
    public static final ShapeId REST_JSON = ShapeId.from("aws.protocols#restJson1");
    
    /**
     * Creates a REST-JSON protocol generator.
     */
    public RestJsonProtocolGenerator() {
    }
    
    @Override
    public ShapeId getProtocol() {
        return REST_JSON;
    }
    
    @Override
    public String getName() {
        return "restJson1";
    }
    
    @Override
    public String getDefaultMethod() {
        return null; // REST protocols use @http trait for method
    }
    
    @Override
    public String getDefaultUri() {
        return null; // REST protocols use @http trait for URI
    }
    
    @Override
    public String getContentType(ServiceShape service) {
        return "application/json";
    }
    
    // ========== HTTP Trait Helper Methods ==========
    
    /**
     * Gets the HTTP trait from an operation.
     * 
     * <p>REST-JSON operations are required to have an {@code @http} trait
     * that specifies the HTTP method and URI pattern.
     * 
     * @param operation The operation shape
     * @return The HTTP trait
     * @throws software.amazon.smithy.model.validation.ValidationException if the operation lacks an @http trait
     */
    private HttpTrait getHttpTrait(OperationShape operation) {
        return operation.expectTrait(HttpTrait.class);
    }
    
    /**
     * Gets the HTTP method from an operation's @http trait.
     * 
     * <p>REST-JSON supports all standard HTTP methods: GET, POST, PUT, DELETE, PATCH, HEAD.
     * 
     * @param operation The operation shape
     * @return The HTTP method (e.g., "GET", "POST", "PUT", "DELETE")
     */
    private String getHttpMethod(OperationShape operation) {
        return getHttpTrait(operation).getMethod();
    }
    
    /**
     * Gets the HTTP URI pattern from an operation's @http trait.
     * 
     * <p>The URI pattern may contain path parameters in curly braces (e.g., "/resources/{ResourceId}").
     * Path parameters correspond to input members with the {@code @httpLabel} trait.
     * 
     * @param operation The operation shape
     * @return The URI pattern (e.g., "/functions/{FunctionName}/invocations")
     */
    private String getHttpUri(OperationShape operation) {
        return getHttpTrait(operation).getUri().toString();
    }
    
    // ========== Operation Signature Generation ==========
    
    /**
     * Gets the input type name for an operation.
     * 
     * <p>Returns the namespaced type name for the operation's input shape,
     * or "()" if the operation has no input.
     * 
     * @param operation The operation shape
     * @param context The code generation context
     * @return The fully qualified input type name (e.g., "aws.eventbridge.PutEventsRequest")
     */
    private String getInputTypeName(OperationShape operation, UnisonContext context) {
        String clientNamespace = context.settings().getClientNamespace();
        return operation.getInput()
                .map(id -> UnisonSymbolProvider.toNamespacedTypeName(id.getName(), clientNamespace))
                .orElse("()");
    }
    
    /**
     * Gets the output type name for an operation.
     * 
     * <p>Returns the namespaced type name for the operation's output shape,
     * or "()" if the operation has no output.
     * 
     * @param operation The operation shape
     * @param context The code generation context
     * @return The fully qualified output type name (e.g., "aws.eventbridge.PutEventsResponse")
     */
    private String getOutputTypeName(OperationShape operation, UnisonContext context) {
        String clientNamespace = context.settings().getClientNamespace();
        return operation.getOutput()
                .map(id -> UnisonSymbolProvider.toNamespacedTypeName(id.getName(), clientNamespace))
                .orElse("()");
    }
    
    /**
     * Gets the operation function name.
     * 
     * <p>Converts the operation name to a namespaced Unison function name
     * (camelCase with namespace prefix).
     * 
     * @param operation The operation shape
     * @param context The code generation context
     * @return The fully qualified function name (e.g., "aws.eventbridge.putEvents")
     */
    private String getOperationName(OperationShape operation, UnisonContext context) {
        String clientNamespace = context.settings().getClientNamespace();
        return UnisonSymbolProvider.toNamespacedFunctionName(
                operation.getId().getName(), clientNamespace);
    }
    
    /**
     * Generates the operation function signature.
     * 
     * <p>Generates a Unison function signature in the format:
     * <pre>
     * operationName : Config -> InputType -> '{IO, Exception, Threads} OutputType
     * </pre>
     * 
     * <p>Example:
     * <pre>
     * aws.eventbridge.putEvents : aws.eventbridge.Config -> aws.eventbridge.PutEventsRequest -> '{IO, Exception, Threads} aws.eventbridge.PutEventsResponse
     * </pre>
     * 
     * @param operation The operation to generate a signature for
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    private void generateOperationSignature(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        String clientNamespace = context.settings().getClientNamespace();
        
        String opName = getOperationName(operation, context);
        String configType = UnisonSymbolProvider.toNamespacedTypeName("Config", clientNamespace);
        String inputType = getInputTypeName(operation, context);
        String outputType = getOutputTypeName(operation, context);
        
        // Generate signature: opName : Config -> Input -> '{IO, Exception, Threads} Output
        String signature = String.format("%s -> %s -> '{IO, Exception, Threads} %s", 
                configType, inputType, outputType);
        writer.writeSignature(opName, signature);
    }
    
    // ========== Operation Generation ==========
    
    /**
     * Generates the complete operation function.
     * 
     * <p>This method orchestrates the generation of a REST-JSON operation by:
     * <ol>
     *   <li>Writing documentation</li>
     *   <li>Writing the function signature</li>
     *   <li>Writing the function definition with do block</li>
     *   <li>Generating URL building with path parameter substitution</li>
     *   <li>Generating query string from @httpQuery members</li>
     *   <li>Generating request headers from @httpHeader members</li>
     *   <li>Generating request body serialization (JSON)</li>
     *   <li>Generating HTTP call with SigV4 signing</li>
     *   <li>Generating response parsing</li>
     * </ol>
     * 
     * @param operation The operation to generate code for
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    @Override
    public void generateOperation(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        String clientNamespace = context.settings().getClientNamespace();
        String method = getHttpMethod(operation);
        String uri = getHttpUri(operation);
        String opName = getOperationName(operation, context);
        String configType = UnisonSymbolProvider.toNamespacedTypeName("Config", clientNamespace);
        
        // Write documentation
        writer.writeDocComment(operation.getId().getName() + " operation\n\n" +
                "REST-JSON protocol\n" +
                "HTTP " + method + " " + uri + "\n" +
                "Raises exception on error, returns output directly on success.");
        
        // Write function signature
        generateOperationSignature(operation, writer, context);
        
        // Write function definition with do block
        writer.write("$L config input = do", opName);
        writer.indent();
        
        // HTTP method and URI
        writer.write("method = \"$L\"", method);
        writer.write("uri = \"$L\"", uri);
        
        // Build URL (Step 1.3 will implement path parameter substitution)
        generateUrlBuilding(operation, writer, context);
        
        // Build query string (Step 1.3 will implement query parameter serialization)
        generateQueryString(operation, writer, context);
        
        // Build full URL
        writer.write("fullUrl = url ++ queryString");
        
        // Build headers (Step 1.5 will implement header serialization)
        generateRequestHeaders(operation, writer, context);
        
        // Build request body (Step 1.4 will implement JSON body serialization)
        generateRequestBody(operation, writer, context);
        
        // Make HTTP call (Step 1.6 will implement full HTTP call with signing)
        generateHttpCall(operation, writer, context);
        
        // Handle response (Step 1.7 will implement full response parsing)
        generateResponseHandling(operation, writer, context);
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    // ========== URL Building (Step 1.3) ==========
    
    /**
     * Generates URL building code with path parameter substitution.
     * 
     * <p>TODO: Step 1.3 will implement:
     * <ul>
     *   <li>Path parameter extraction from @httpLabel members</li>
     *   <li>URL encoding of path segments</li>
     *   <li>Substitution of {param} placeholders</li>
     * </ul>
     */
    private void generateUrlBuilding(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        String clientNamespace = context.settings().getClientNamespace();
        String configType = UnisonSymbolProvider.toNamespacedTypeName("Config", clientNamespace);
        
        // Basic URL building - Step 1.3 will add path parameter substitution
        writer.write("url = ($L.endpoint config) ++ uri", configType);
    }
    
    /**
     * Generates query string building code.
     * 
     * <p>TODO: Step 1.3 will implement:
     * <ul>
     *   <li>Query parameter extraction from @httpQuery members</li>
     *   <li>URL encoding of query values</li>
     *   <li>Building query string with proper separators</li>
     * </ul>
     */
    private void generateQueryString(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // Basic empty query string - Step 1.3 will add query parameter serialization
        writer.write("queryString = \"\"");
    }
    
    // ========== Request Headers (Step 1.5) ==========
    
    /**
     * Generates request header building code.
     * 
     * <p>TODO: Step 1.5 will implement:
     * <ul>
     *   <li>Header extraction from @httpHeader members</li>
     *   <li>Content-Type header</li>
     *   <li>Optional header handling</li>
     * </ul>
     */
    private void generateRequestHeaders(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // Basic headers - Step 1.5 will add @httpHeader members
        writer.write("");
        writer.write("-- Request headers");
        writer.write("headers = [");
        writer.indent();
        writer.write("(\"Content-Type\", \"application/json\")");
        writer.dedent();
        writer.write("]");
    }
    
    // ========== Request Body (Step 1.4) ==========
    
    /**
     * Generates request body serialization code.
     * 
     * <p>TODO: Step 1.4 will implement:
     * <ul>
     *   <li>Identification of body members (not HTTP-bound)</li>
     *   <li>JSON serialization of body members</li>
     *   <li>Optional field handling</li>
     *   <li>@httpPayload support</li>
     * </ul>
     */
    private void generateRequestBody(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // Basic empty body - Step 1.4 will add JSON body serialization
        writer.write("");
        writer.write("-- Serialize request body to JSON");
        writer.write("bodyText = \"{}\"");
        writer.write("bodyBytes = Text.toUtf8 bodyText");
    }
    
    // ========== HTTP Call (Step 1.6) ==========
    
    /**
     * Generates HTTP call code with SigV4 signing.
     * 
     * <p>TODO: Step 1.6 will implement:
     * <ul>
     *   <li>SigV4 request signing</li>
     *   <li>Error status checking</li>
     *   <li>HTTP method-specific request construction</li>
     * </ul>
     */
    private void generateHttpCall(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        String clientNamespace = context.settings().getClientNamespace();
        String configType = UnisonSymbolProvider.toNamespacedTypeName("Config", clientNamespace);
        String credsType = UnisonSymbolProvider.toNamespacedTypeName("Credentials", clientNamespace);
        String method = getHttpMethod(operation);
        String serviceName = extractSigningServiceName(context.serviceShape().getId().getName());
        
        writer.write("");
        writer.write("-- Sign request with AWS Signature Version 4");
        writer.write("region = $L.region config", configType);
        writer.write("creds = $L.credentials config", configType);
        writer.write("awsCreds = aws.sigv4.Credentials.Credentials ($L.accessKeyId creds) ($L.secretAccessKey creds) ($L.sessionToken creds)", 
                credsType, credsType, credsType);
        writer.write("signingConfig = aws.sigv4.SigningConfig.SigningConfig region \"$L\" awsCreds", serviceName);
        writer.write("allHeaders = !(aws.sigv4.addSigningHeaders signingConfig method uri \"\" headers bodyBytes)");
        
        writer.write("");
        writer.write("-- Make HTTP request");
        String methodLower = method.toLowerCase();
        if (methodLower.equals("get") || methodLower.equals("delete") || methodLower.equals("head")) {
            writer.write("request = Http.Request.$L fullUrl allHeaders", methodLower);
        } else {
            writer.write("request = Http.Request.$L fullUrl allHeaders bodyBytes", methodLower);
        }
        writer.write("response = !(executeRequest request)");
    }
    
    /**
     * Extracts the service name for SigV4 signing from the full service name.
     * 
     * <p>AWS service names in models often include version suffixes (e.g., EventBridge_20150702),
     * but SigV4 signing uses the lowercase base service name (e.g., "events").
     * 
     * @param serviceName The full service name
     * @return The signing service name
     */
    private String extractSigningServiceName(String serviceName) {
        // Remove version suffix (e.g., "_20150702")
        String baseName = serviceName.replaceAll("_\\d+$", "");
        // Convert to lowercase for signing
        return baseName.toLowerCase();
    }
    
    // ========== Response Handling (Step 1.7) ==========
    
    /**
     * Generates response handling code.
     * 
     * <p>TODO: Step 1.7 will implement:
     * <ul>
     *   <li>Error response parsing</li>
     *   <li>Success response JSON parsing</li>
     *   <li>@httpHeader response extraction</li>
     *   <li>Output type construction</li>
     * </ul>
     */
    private void generateResponseHandling(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        String clientNamespace = context.settings().getClientNamespace();
        String outputType = getOutputTypeName(operation, context);
        String serviceName = context.serviceShape().getId().getName();
        
        // Remove "Service" suffix if present
        if (serviceName.endsWith("Service")) {
            serviceName = serviceName.substring(0, serviceName.length() - 7);
        }
        String errorTypeName = UnisonSymbolProvider.toNamespacedTypeName(
                serviceName + "ServiceError", clientNamespace);
        
        writer.write("");
        writer.write("-- Handle response based on status code");
        writer.write("statusCode = Http.Response.statusCode response");
        writer.write("if Nat.lt statusCode 300 then");
        writer.indent();
        
        // Success case - basic output (Step 1.7 will add proper response parsing)
        if (operation.getOutput().isPresent()) {
            String parserName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    operation.getId().getName() + "ResponseParser");
            writer.write("!($L response)", parserName);
        } else {
            writer.write("()");
        }
        
        writer.dedent();
        writer.write("else");
        writer.indent();
        
        // Error case - parse error and raise exception
        writer.write("-- Parse error response");
        writer.write("serviceError = $L.parseError response", clientNamespace);
        writer.write("failure = $L.toFailure serviceError", errorTypeName);
        writer.write("Exception.raise failure");
        
        writer.dedent();
    }
    
    @Override
    public void generateRequestSerializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement request serialization (Step 1.4)
    }
    
    @Override
    public void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement response deserialization (Step 1.7)
    }
    
    @Override
    public void generateErrorParser(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement error parsing (Step 1.8)
    }
}
