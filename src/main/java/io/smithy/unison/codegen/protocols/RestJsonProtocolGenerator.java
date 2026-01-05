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
    
    @Override
    public void generateOperation(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement main operation generation (Step 1.1.4)
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
