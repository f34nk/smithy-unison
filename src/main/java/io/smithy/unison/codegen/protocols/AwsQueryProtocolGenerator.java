package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Protocol generator for AWS Query protocol (aws.protocols#awsQuery).
 * 
 * <p>Used by SQS, SNS, CloudWatch, RDS, ElastiCache, IAM, STS, and other Query-based services.
 * 
 * <h2>Protocol Characteristics</h2>
 * <ul>
 *   <li>HTTP Method: POST (always)</li>
 *   <li>URI Path: "/" (always)</li>
 *   <li>Content-Type: application/x-www-form-urlencoded</li>
 *   <li>Request Body: Form-encoded parameters with Action and Version</li>
 *   <li>Response Body: XML decoded</li>
 *   <li>Authentication: AWS SigV4</li>
 * </ul>
 * 
 * <h2>Request Format</h2>
 * <p>Example AWS Query request:
 * <pre>
 * POST / HTTP/1.1
 * Host: sqs.us-east-1.amazonaws.com
 * Content-Type: application/x-www-form-urlencoded
 * 
 * Action=SendMessage&Version=2012-11-05&QueueUrl=...&MessageBody=...
 * </pre>
 * 
 * <h2>Response Format</h2>
 * <p>Example AWS Query response:
 * <pre>
 * &lt;SendMessageResponse xmlns="..."&gt;
 *   &lt;SendMessageResult&gt;
 *     &lt;MessageId&gt;abc123&lt;/MessageId&gt;
 *   &lt;/SendMessageResult&gt;
 *   &lt;ResponseMetadata&gt;
 *     &lt;RequestId&gt;xyz789&lt;/RequestId&gt;
 *   &lt;/ResponseMetadata&gt;
 * &lt;/SendMessageResponse&gt;
 * </pre>
 * 
 * @see ProtocolGenerator
 * @see AwsJsonProtocolGenerator
 * @see RestXmlProtocolGenerator
 */
public class AwsQueryProtocolGenerator implements ProtocolGenerator {
    
    /** Protocol trait ID for AWS Query */
    public static final ShapeId AWS_QUERY = ShapeId.from("aws.protocols#awsQuery");
    
    /** Default service version if not specified in service metadata */
    private static final String DEFAULT_VERSION = "2010-05-15";
    
    /**
     * Creates an AWS Query protocol generator.
     */
    public AwsQueryProtocolGenerator() {
    }
    
    @Override
    public ShapeId getProtocol() {
        return AWS_QUERY;
    }
    
    @Override
    public String getName() {
        return "awsQuery";
    }
    
    @Override
    public String getDefaultMethod() {
        return "POST"; // AWS Query always uses POST
    }
    
    @Override
    public String getDefaultUri() {
        return "/"; // AWS Query always uses /
    }
    
    @Override
    public String getContentType(ServiceShape service) {
        return "application/x-www-form-urlencoded";
    }
    
    // ========== Service Version Helper ==========
    
    /**
     * Gets the service version from the service shape.
     * 
     * <p>AWS Query protocol requires a Version parameter in all requests.
     * This is typically the API version date (e.g., "2012-11-05" for SQS).
     * 
     * <p>The version is extracted from the service's version metadata.
     * If not specified, defaults to "2010-05-15".
     * 
     * @param service The service shape
     * @return The service version string (e.g., "2012-11-05")
     */
    private String getServiceVersion(ServiceShape service) {
        String version = service.getVersion();
        return (version != null && !version.isEmpty()) ? version : DEFAULT_VERSION;
    }
    
    // ========== Operation Signature Generation ==========
    
    /**
     * Generates the type signature for an AWS Query operation.
     * 
     * <p>AWS Query operations follow the pattern:
     * <pre>
     * operationName : Config -> InputType -> '{IO, Exception, Threads} OutputType
     * </pre>
     * 
     * @param operation The operation shape
     * @param writer The Unison writer
     * @param context The codegen context
     */
    private void generateOperationSignature(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        String clientNamespace = context.settings().getClientNamespace();
        
        String opName = UnisonSymbolProvider.toNamespacedFunctionName(
                operation.getId().getName(), clientNamespace);
        
        // Determine input and output types (namespaced)
        String inputType = operation.getInput()
                .map(id -> UnisonSymbolProvider.toNamespacedTypeName(id.getName(), clientNamespace))
                .orElse("()");
        String outputType = operation.getOutput()
                .map(id -> UnisonSymbolProvider.toNamespacedTypeName(id.getName(), clientNamespace))
                .orElse("()");
        String configType = UnisonSymbolProvider.toNamespacedTypeName("Config", clientNamespace);
        
        // Write signature
        String signature = String.format("%s -> %s -> '{IO, Exception, Threads} %s", configType, inputType, outputType);
        writer.writeSignature(opName, signature);
    }
    
    // ========== Main Operation Generation Method ==========
    
    @Override
    public void generateOperation(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        ServiceShape service = context.serviceShape();
        String clientNamespace = context.settings().getClientNamespace();
        
        String opName = UnisonSymbolProvider.toNamespacedFunctionName(
                operation.getId().getName(), clientNamespace);
        String operationName = operation.getId().getName();
        String serviceVersion = getServiceVersion(service);
        String configType = UnisonSymbolProvider.toNamespacedTypeName("Config", clientNamespace);
        
        // Write documentation
        writer.writeDocComment(operationName + " operation\n\n" +
                "AWS Query protocol\n" +
                "HTTP POST /\n" +
                "Action: " + operationName + "\n" +
                "Version: " + serviceVersion + "\n" +
                "Raises exception on error, returns output directly on success.");
        
        // Generate signature
        generateOperationSignature(operation, writer, context);
        
        // Write function definition with do block
        writer.write("$L config input = do", opName);
        writer.indent();
        
        // HTTP method and URI (always POST /)
        writer.write("method = \"POST\"");
        writer.write("uri = \"/\"");
        writer.write("url = ($L.endpoint config) ++ uri", configType);
        
        // Placeholder for request serialization (Step 2.2)
        writer.write("");
        writer.write("-- TODO: Serialize request to form-encoded parameters");
        writer.write("-- params = serializeRequest input");
        
        // Placeholder for Action and Version parameters (Step 2.2)
        writer.write("");
        writer.write("-- TODO: Add Action and Version parameters");
        writer.write("-- allParams = params ++ [(\"Action\", \"$L\"), (\"Version\", \"$L\")]", operationName, serviceVersion);
        
        // Placeholder for form encoding (Step 2.3)
        writer.write("");
        writer.write("-- TODO: Form-encode parameters");
        writer.write("-- bodyText = formEncode allParams");
        
        // Placeholder for HTTP POST (Step 2.4)
        writer.write("");
        writer.write("-- TODO: Execute HTTP POST");
        writer.write("-- response = executeHttpRequest method url headers bodyText");
        
        // Placeholder for response parsing (Step 2.5)
        writer.write("");
        writer.write("-- TODO: Parse XML response");
        writer.write("-- parseResponse response");
        
        // Temporary stub return
        writer.write("");
        writer.write("bug \"AWS Query operation not yet implemented\"");
        
        writer.dedent();
    }
    
    @Override
    public void generateRequestSerializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS Query request serialization (Step 2.2)
    }
    
    @Override
    public void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS Query response deserialization (Step 2.5)
    }
    
    @Override
    public void generateErrorParser(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS Query error parsing (Step 2.6)
    }
}
