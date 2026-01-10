package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpResponseCodeTrait;
import software.amazon.smithy.model.traits.HttpTrait;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    
    // ========== HTTP Binding Detection ==========
    
    /**
     * Gets the set of member names that are bound to HTTP (not in the body).
     * 
     * <p>HTTP-bound members are those with:
     * <ul>
     *   <li>{@code @httpLabel} - Path parameters</li>
     *   <li>{@code @httpQuery} - Query string parameters</li>
     *   <li>{@code @httpHeader} - HTTP headers</li>
     *   <li>{@code @httpPayload} - Raw payload (entire body)</li>
     * </ul>
     * 
     * <p>These members are NOT serialized into the JSON body. Only unbound
     * members are included in the JSON request/response body.
     * 
     * @param input The input structure shape
     * @return Set of member names that are HTTP-bound
     */
    private Set<String> getHttpBoundMembers(StructureShape input) {
        Set<String> bound = new HashSet<>();
        
        for (MemberShape member : input.getAllMembers().values()) {
            if (isHttpBound(member)) {
                bound.add(member.getMemberName());
            }
        }
        
        return bound;
    }
    
    /**
     * Checks if a member is bound to HTTP (not in the JSON body).
     * 
     * @param member The member shape to check
     * @return true if the member has an HTTP binding trait
     */
    private boolean isHttpBound(MemberShape member) {
        return member.hasTrait(HttpLabelTrait.class) ||
               member.hasTrait(HttpQueryTrait.class) ||
               member.hasTrait(HttpHeaderTrait.class) ||
               member.hasTrait(HttpPayloadTrait.class);
    }
    
    /**
     * Gets members with {@code @httpLabel} trait (path parameters).
     * 
     * <p>These members are substituted into the URI path template.
     * Example: For URI "/resources/{ResourceId}", the input member
     * with {@code @httpLabel} named "ResourceId" provides the value.
     * 
     * @param input The input structure shape
     * @return List of members with {@code @httpLabel} trait
     */
    private List<MemberShape> getPathParameterMembers(StructureShape input) {
        return input.getAllMembers().values().stream()
            .filter(m -> m.hasTrait(HttpLabelTrait.class))
            .collect(Collectors.toList());
    }
    
    /**
     * Gets members with {@code @httpQuery} trait (query string parameters).
     * 
     * <p>These members are serialized as query string parameters.
     * Example: Member with {@code @httpQuery("maxResults")} becomes "?maxResults=10"
     * 
     * @param input The input structure shape
     * @return List of members with {@code @httpQuery} trait
     */
    private List<MemberShape> getQueryParameterMembers(StructureShape input) {
        return input.getAllMembers().values().stream()
            .filter(m -> m.hasTrait(HttpQueryTrait.class))
            .collect(Collectors.toList());
    }
    
    /**
     * Gets members with {@code @httpHeader} trait (HTTP headers).
     * 
     * <p>These members are serialized as HTTP request headers.
     * Example: Member with {@code @httpHeader("X-Custom-Header")} becomes
     * an HTTP header "X-Custom-Header: value"
     * 
     * @param input The input structure shape
     * @return List of members with {@code @httpHeader} trait
     */
    private List<MemberShape> getHeaderMembers(StructureShape input) {
        return input.getAllMembers().values().stream()
            .filter(m -> m.hasTrait(HttpHeaderTrait.class))
            .collect(Collectors.toList());
    }
    
    /**
     * Gets body members (not bound to HTTP).
     * 
     * <p>Body members are those WITHOUT HTTP binding traits. These members
     * are serialized into the JSON request/response body.
     * 
     * <p>This is a key difference from AWS JSON protocol, which includes
     * ALL members in the JSON body.
     * 
     * @param input The input structure shape
     * @return List of members to be serialized in the JSON body
     */
    private List<MemberShape> getBodyMembers(StructureShape input) {
        Set<String> httpBound = getHttpBoundMembers(input);
        return input.getAllMembers().values().stream()
            .filter(m -> !httpBound.contains(m.getMemberName()))
            .collect(Collectors.toList());
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
     * <p>Extracts {@code @httpLabel} members and substitutes them into the URI template.
     * Example: URI "/resources/{ResourceId}" with ResourceId="abc123" becomes "/resources/abc123"
     * 
     * @param operation The operation shape
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    private void generateUrlBuilding(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        String configType = UnisonSymbolProvider.toNamespacedTypeName("Config", clientNamespace);
        String inputType = getInputTypeName(operation, context);
        
        Optional<StructureShape> inputShape = ProtocolUtils.getInputShape(operation, model);
        if (inputShape.isEmpty()) {
            // No input - just use URI as-is
            writer.write("url = ($L.endpoint config) ++ uri", configType);
            return;
        }
        
        List<MemberShape> pathParams = getPathParameterMembers(inputShape.get());
        
        if (pathParams.isEmpty()) {
            // No path parameters - just use URI as-is
            writer.write("url = ($L.endpoint config) ++ uri", configType);
        } else {
            // Has path parameters - need substitution
            writer.write("");
            writer.write("-- Substitute path parameters");
            
            String currentUri = "uri";
            for (int i = 0; i < pathParams.size(); i++) {
                MemberShape member = pathParams.get(i);
                String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
                String placeholder = "{" + member.getMemberName() + "}";
                String nextUri = "uri" + (i + 1);
                
                // Get the target shape to determine if conversion needed
                software.amazon.smithy.model.shapes.Shape targetShape = model.expectShape(member.getTarget());
                String toTextFunc = getToTextFunction(targetShape, clientNamespace);
                
                // Extract value from input
                writer.write("$LValue = $L.$L input", memberName, inputType, memberName);
                
                // Convert to text if needed, URL encode, and substitute
                if (toTextFunc.isEmpty()) {
                    // Already text - just URL encode
                    writer.write("$L = Text.replaceAll \"$L\" (aws.http.urlEncode $LValue) $L",
                            nextUri, placeholder, memberName, currentUri);
                } else {
                    // Convert to text first, then URL encode
                    writer.write("$L = Text.replaceAll \"$L\" (aws.http.urlEncode ($L $LValue)) $L",
                            nextUri, placeholder, toTextFunc, memberName, currentUri);
                }
                
                currentUri = nextUri;
            }
            
            writer.write("url = ($L.endpoint config) ++ $L", configType, currentUri);
        }
    }
    
    /**
     * Generates query string building code.
     * 
     * <p>Extracts {@code @httpQuery} members and builds a query string.
     * Example: maxResults=10&filter=active becomes "?maxResults=10&filter=active"
     * 
     * @param operation The operation shape
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    private void generateQueryString(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        String inputType = getInputTypeName(operation, context);
        
        Optional<StructureShape> inputShape = ProtocolUtils.getInputShape(operation, model);
        if (inputShape.isEmpty()) {
            writer.write("queryString = \"\"");
            return;
        }
        
        List<MemberShape> queryParams = getQueryParameterMembers(inputShape.get());
        
        if (queryParams.isEmpty()) {
            writer.write("queryString = \"\"");
        } else {
            writer.write("");
            writer.write("-- Build query string from @httpQuery members");
            writer.write("queryParts : [Optional Text]");
            writer.write("queryParts = [");
            writer.indent();
            
            for (int i = 0; i < queryParams.size(); i++) {
                MemberShape member = queryParams.get(i);
                String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
                String queryName = getQueryParamName(member);
                boolean isLast = (i == queryParams.size() - 1);
                
                // Get the target shape to determine serialization
                software.amazon.smithy.model.shapes.Shape targetShape = model.expectShape(member.getTarget());
                
                // Skip list-valued query parameters for now (TODO: implement proper list serialization)
                if (targetShape instanceof ListShape) {
                    writer.write("None$L  -- TODO: list-valued query parameter not supported: $L",
                               isLast ? "" : ",", queryName);
                    continue;
                }
                
                String toTextFunc = getToTextFunction(targetShape, clientNamespace);
                
                // HTTP query parameters are always optional in the generated types
                // (even if marked @required in Smithy) because they can be omitted from the HTTP request
                // So we always use Optional.map here
                if (toTextFunc.isEmpty()) {
                    writer.write("Optional.map (v -> \"$L=\" ++ aws.http.urlEncode v) ($L.$L input)$L",
                            queryName, inputType, memberName, isLast ? "" : ",");
                } else {
                    writer.write("Optional.map (v -> \"$L=\" ++ aws.http.urlEncode ($L v)) ($L.$L input)$L",
                            queryName, toTextFunc, inputType, memberName, isLast ? "" : ",");
                }
            }
            
            writer.dedent();
            writer.write("]");
            writer.write("filteredParts = List.filterMap (x -> x) queryParts");
            writer.write("queryString = if List.isEmpty filteredParts then \"\" else \"?\" ++ Text.join \"&\" filteredParts");
        }
    }
    
    /**
     * Gets the query parameter name for a member with {@code @httpQuery} trait.
     * 
     * @param member The member shape
     * @return The query parameter name
     */
    private String getQueryParamName(MemberShape member) {
        return member.getTrait(HttpQueryTrait.class)
                .map(HttpQueryTrait::getValue)
                .filter(v -> !v.isEmpty())
                .orElse(member.getMemberName());
    }
    
    /**
     * Gets the appropriate toText function for a given shape type.
     * 
     * @param shape The shape to get toText function for
     * @param clientNamespace The client namespace for namespacing enum functions
     * @return The toText function name, or empty string if no conversion needed
     */
    private String getToTextFunction(software.amazon.smithy.model.shapes.Shape shape, String clientNamespace) {
        // Check for enums first
        if (shape.isEnumShape() || 
            (shape.isStringShape() && shape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            return UnisonSymbolProvider.toNamespacedFunctionName(
                    shape.getId().getName() + "ToText", clientNamespace);
        }
        
        if (shape.isStringShape()) {
            return "";  // No conversion needed for Text
        } else if (shape.isIntegerShape() || shape.isLongShape()) {
            return "Int.toText";
        } else if (shape.isBooleanShape()) {
            return "Boolean.toText";
        } else if (shape.isFloatShape() || shape.isDoubleShape()) {
            return "Float.toText";
        } else if (shape.isTimestampShape()) {
            return "";  // Timestamps are Text in Unison
        } else {
            return "";  // Default fallback
        }
    }
    
    // ========== Request Headers (Step 1.5) ==========
    
    /**
     * Generates request header building code.
     * 
     * <p>Extracts {@code @httpHeader} members and builds HTTP request headers.
     * Handles both required and optional headers, with type-specific serialization.
     * 
     * @param operation The operation shape
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    private void generateRequestHeaders(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        String inputType = getInputTypeName(operation, context);
        
        Optional<StructureShape> inputShape = ProtocolUtils.getInputShape(operation, model);
        
        writer.write("");
        writer.write("-- Build headers from @httpHeader members");
        
        if (inputShape.isEmpty()) {
            // No input - just Content-Type
            writer.write("headers = [(\"Content-Type\", \"application/json\")]");
            return;
        }
        
        List<MemberShape> headerMembers = getHeaderMembers(inputShape.get());
        
        if (headerMembers.isEmpty()) {
            // No custom headers - just Content-Type
            writer.write("headers = [(\"Content-Type\", \"application/json\")]");
        } else {
            // Has custom headers - build header list
            writer.write("baseHeaders = [(\"Content-Type\", \"application/json\")]");
            writer.write("-- Each header is converted to (Text, Optional Text) for homogeneous list");
            writer.write("customHeaderParts : [(Text, Optional Text)]");
            writer.write("customHeaderParts = [");
            writer.indent();
            
            for (int i = 0; i < headerMembers.size(); i++) {
                MemberShape member = headerMembers.get(i);
                String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
                String headerName = getHeaderName(member);
                boolean isLast = (i == headerMembers.size() - 1);
                
                // Get target shape to determine serialization
                software.amazon.smithy.model.shapes.Shape targetShape = model.expectShape(member.getTarget());
                String toTextFunc = getToTextFunction(targetShape, clientNamespace);
                
                // Check if member is required
                boolean isRequired = member.isRequired();
                
                if (isRequired) {
                    // Required field: convert value directly and wrap in Some
                    if (toTextFunc.isEmpty()) {
                        writer.write("(\"$L\", Some ($L.$L input))$L", 
                                headerName, inputType, memberName, isLast ? "" : ",");
                    } else {
                        writer.write("(\"$L\", Some ($L ($L.$L input)))$L", 
                                headerName, toTextFunc, inputType, memberName, isLast ? "" : ",");
                    }
                } else {
                    // Optional field: map over the Optional
                    if (toTextFunc.isEmpty()) {
                        writer.write("(\"$L\", $L.$L input)$L", 
                                headerName, inputType, memberName, isLast ? "" : ",");
                    } else {
                        writer.write("(\"$L\", Optional.map $L ($L.$L input))$L", 
                                headerName, toTextFunc, inputType, memberName, isLast ? "" : ",");
                    }
                }
            }
            
            writer.dedent();
            writer.write("]");
            
            // Build headers by extracting Some values
            writer.write("toHeader : (Text, Optional Text) -> Optional (Text, Text)");
            writer.write("toHeader pair = match pair with");
            writer.indent();
            writer.write("(name, Some v) -> if Text.isEmpty v then None else Some (name, v)");
            writer.write("(_, None) -> None");
            writer.dedent();
            writer.write("filteredHeaders = List.filterMap toHeader customHeaderParts");
            writer.write("headers = baseHeaders ++ filteredHeaders");
        }
    }
    
    /**
     * Gets the header name for a member with {@code @httpHeader} trait.
     * 
     * @param member The member shape
     * @return The header name
     */
    private String getHeaderName(MemberShape member) {
        return member.getTrait(HttpHeaderTrait.class)
                .map(HttpHeaderTrait::getValue)
                .filter(v -> !v.isEmpty())
                .orElse(member.getMemberName());
    }
    
    // ========== Request Body (Step 1.4) ==========
    
    /**
     * Generates request body serialization code.
     * 
     * <p>Serializes body members (not HTTP-bound) to JSON.
     * Key difference from AWS JSON: Only unbound members are serialized.
     * 
     * @param operation The operation shape
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    private void generateRequestBody(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        String inputType = getInputTypeName(operation, context);
        
        Optional<StructureShape> inputShape = ProtocolUtils.getInputShape(operation, model);
        if (inputShape.isEmpty()) {
            // No input - empty body
            writer.write("");
            writer.write("-- No input structure - empty body");
            writer.write("bodyText = \"{}\"");
            writer.write("bodyBytes = Text.toUtf8 bodyText");
            return;
        }
        
        // Check for @httpPayload member
        Optional<MemberShape> payloadMember = ProtocolUtils.getPayloadMember(inputShape.get());
        if (payloadMember.isPresent()) {
            // @httpPayload present - use the payload member directly
            generatePayloadSerialization(payloadMember.get(), inputType, model, writer, context);
            return;
        }
        
        // Get body members (not HTTP-bound)
        List<MemberShape> bodyMembers = getBodyMembers(inputShape.get());
        
        if (bodyMembers.isEmpty()) {
            // No body members - empty JSON body
            writer.write("");
            writer.write("-- No body members - empty JSON body");
            writer.write("bodyText = \"{}\"");
            writer.write("bodyBytes = Text.toUtf8 bodyText");
        } else {
            // Has body members - serialize to JSON
            writer.write("");
            writer.write("-- Serialize request body to JSON (only unbound members)");
            String serializerName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    operation.getId().getName() + "RequestBody");
            writer.write("bodyJson = $L input", serializerName);
            writer.write("bodyText = aws.json.bridge.jsonToRequestBody bodyJson");
            writer.write("bodyBytes = Text.toUtf8 bodyText");
        }
    }
    
    /**
     * Generates serialization for @httpPayload member.
     * 
     * @param payloadMember The payload member
     * @param inputType The input type name
     * @param model The Smithy model
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    private void generatePayloadSerialization(MemberShape payloadMember, String inputType,
                                               Model model, UnisonWriter writer, UnisonContext context) {
        String memberName = UnisonSymbolProvider.toUnisonFunctionName(payloadMember.getMemberName());
        software.amazon.smithy.model.shapes.Shape targetShape = model.expectShape(payloadMember.getTarget());
        
        writer.write("");
        writer.write("-- @httpPayload: use payload member directly");
        
        // Check if the payload is required or optional
        boolean isRequired = payloadMember.isRequired();
        
        if (targetShape.isBlobShape()) {
            // Blob payload - use as-is (unwrap Optional if needed)
            if (isRequired) {
                writer.write("bodyBytes = $L.$L input", inputType, memberName);
            } else {
                writer.write("bodyBytes = Optional.getOrElse Bytes.empty ($L.$L input)", inputType, memberName);
            }
        } else if (targetShape.isStringShape()) {
            // String payload - convert to UTF8 (unwrap Optional if needed)
            if (isRequired) {
                writer.write("bodyText = $L.$L input", inputType, memberName);
                writer.write("bodyBytes = Text.toUtf8 bodyText");
            } else {
                writer.write("bodyText = Optional.getOrElse \"\" ($L.$L input)", inputType, memberName);
                writer.write("bodyBytes = Text.toUtf8 bodyText");
            }
        } else {
            // Structure payload - serialize as JSON (unwrap Optional if needed)
            String clientNamespace = context.settings().getClientNamespace();
            String serializerName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    targetShape.getId().getName() + "ToJson");
            if (isRequired) {
                writer.write("bodyJson = $L ($L.$L input)", serializerName, inputType, memberName);
                writer.write("bodyText = aws.json.bridge.jsonToRequestBody bodyJson");
                writer.write("bodyBytes = Text.toUtf8 bodyText");
            } else {
                // For optional structure payloads, use empty JSON object as default
                writer.write("bodyJson = Optional.map $L ($L.$L input)", serializerName, inputType, memberName);
                writer.write("bodyText = aws.json.bridge.jsonToRequestBody (Optional.getOrElse (jsonObject []) bodyJson)");
                writer.write("bodyBytes = Text.toUtf8 bodyText");
            }
        }
    }
    
    // ========== HTTP Call (Step 1.6) ==========
    
    /**
     * Generates HTTP call code with SigV4 signing.
     * 
     * <p>Implements:
     * <ul>
     *   <li>SigV4 request signing</li>
     *   <li>HTTP method-specific request construction (GET/DELETE/HEAD vs POST/PUT/PATCH)</li>
     *   <li>HTTP request execution</li>
     * </ul>
     * 
     * <p>Error status checking is handled in {@link #generateResponseHandling}.
     * 
     * @param operation The operation shape
     * @param writer The Unison code writer
     * @param context The code generation context
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
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        Optional<StructureShape> inputShape = ProtocolUtils.getInputShape(operation, model);
        if (inputShape.isEmpty()) {
            return; // No request body needed
        }
        
        // Check for @httpPayload member
        Optional<MemberShape> payloadMember = ProtocolUtils.getPayloadMember(inputShape.get());
        if (payloadMember.isPresent()) {
            // @httpPayload present - structure serializer is generated upfront by ClientModuleWriter
            // No need to generate it here
            return;
        }
        
        // Get body members (not HTTP-bound)
        List<MemberShape> bodyMembers = getBodyMembers(inputShape.get());
        if (bodyMembers.isEmpty()) {
            return; // No request body needed
        }
        
        StructureShape input = inputShape.get();
        String inputType = UnisonSymbolProvider.toNamespacedTypeName(input.getId().getName(), clientNamespace);
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                operation.getId().getName() + "RequestBody");
        
        // Generate serializer for body members only
        generateBodyMembersSerializer(input, bodyMembers, functionName, inputType, model, clientNamespace, writer);
    }
    
    /**
     * Generates a JSON serializer for body members only (not HTTP-bound members).
     * 
     * <p>This is the key difference from AWS JSON: Only unbound members are serialized.
     * 
     * @param input The input structure
     * @param bodyMembers The list of body members (not HTTP-bound)
     * @param functionName The function name
     * @param inputType The input type name
     * @param model The Smithy model
     * @param clientNamespace The client namespace
     * @param writer The Unison code writer
     */
    private void generateBodyMembersSerializer(StructureShape input, List<MemberShape> bodyMembers,
                                                String functionName, String inputType, 
                                                Model model, String clientNamespace, UnisonWriter writer) {
        writer.writeComment("Serialize " + input.getId().getName() + " body members to JSON");
        writer.writeSignature(functionName, inputType + " -> aws.json.JsonValue");
        writer.write("$L input =", functionName);
        writer.indent();
        writer.write("let");
        writer.indent();
        
        // Generate field list for body members only
        writer.write("fields = [");
        writer.indent();
        
        for (int i = 0; i < bodyMembers.size(); i++) {
            MemberShape member = bodyMembers.get(i);
            String jsonName = getJsonName(member);
            boolean isLast = (i == bodyMembers.size() - 1);
            
            // Generate serialization for this field
            writer.write("(\"$L\", $L)$L",
                    jsonName,
                    generateJsonValueForMember(member, model, clientNamespace, "input"),
                    isLast ? "" : ",");
        }
        
        writer.dedent();
        writer.write("]");
        
        // Filter out null values for optional fields
        writer.write("isNotNull = cases");
        writer.indent();
        writer.write("(_, aws.json.JsonValue.JsonNull) -> false");
        writer.write("_ -> true");
        writer.dedent();
        writer.write("filteredFields = List.filter isNotNull fields");
        
        // Create JSON object
        writer.write("aws.json.jsonObject filteredFields");
        
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Gets the JSON field name for a member, respecting @jsonName trait.
     * 
     * @param member The member shape
     * @return The JSON field name
     */
    private String getJsonName(MemberShape member) {
        return member.getTrait(software.amazon.smithy.model.traits.JsonNameTrait.class)
                .map(software.amazon.smithy.model.traits.JsonNameTrait::getValue)
                .orElse(member.getMemberName());
    }
    
    /**
     * Generates Unison code to convert a member value to JsonValue.
     * 
     * <p>Adapted from AwsJsonProtocolGenerator.
     * 
     * @param member The member shape
     * @param model The Smithy model
     * @param clientNamespace The client namespace
     * @param inputVar The input variable name
     * @return Unison expression to convert member to JsonValue
     */
    private String generateJsonValueForMember(MemberShape member, Model model, String clientNamespace, String inputVar) {
        String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
        String inputType = UnisonSymbolProvider.toNamespacedTypeName(
                model.expectShape(member.getContainer()).asStructureShape().get().getId().getName(),
                clientNamespace);
        String accessor = "(" + inputType + "." + memberName + " " + inputVar + ")";
        
        software.amazon.smithy.model.shapes.Shape target = model.expectShape(member.getTarget());
        
        // Check if field is non-optional (required or has default)
        boolean isNonOptional = member.isRequired() || 
                member.hasTrait(software.amazon.smithy.model.traits.DefaultTrait.class);
        
        if (isNonOptional) {
            return generateJsonValue(target, accessor, model, clientNamespace);
        } else {
            // Optional field - map to JsonValue, defaulting to JsonNull
            String conversion = generateJsonValue(target, "x", model, clientNamespace);
            return String.format("Optional.map (x -> %s) %s |> Optional.getOrElse aws.json.JsonValue.JsonNull",
                    conversion, accessor);
        }
    }
    
    /**
     * Generates Unison expression to convert a shape value to JsonValue.
     * 
     * <p>Adapted from AwsJsonProtocolGenerator.
     * 
     * @param shape The shape
     * @param varName The variable name
     * @param model The Smithy model
     * @param clientNamespace The client namespace
     * @return Unison expression to convert to JsonValue
     */
    private String generateJsonValue(software.amazon.smithy.model.shapes.Shape shape, String varName, 
                                       Model model, String clientNamespace) {
        // Check enum FIRST (before string check)
        if (shape.isEnumShape() || 
            (shape.isStringShape() && shape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            String toTextFn = UnisonSymbolProvider.toNamespacedFunctionName(
                    shape.getId().getName() + "ToText", clientNamespace);
            return "aws.json.JsonValue.JsonString (" + toTextFn + " " + varName + ")";
        } else if (shape.isStringShape()) {
            return "aws.json.JsonValue.JsonString " + varName;
        } else if (shape.isBooleanShape()) {
            return "aws.json.JsonValue.JsonBoolean " + varName;
        } else if (shape.isIntegerShape() || shape.isLongShape() || 
                   shape.isShortShape() || shape.isByteShape()) {
            return "aws.json.JsonValue.JsonNumber (Float.fromInt " + varName + ")";
        } else if (shape.isFloatShape() || shape.isDoubleShape()) {
            return "aws.json.JsonValue.JsonNumber " + varName;
        } else if (shape.isBlobShape()) {
            // Base64 encode bytes - toBase64 returns Bytes, need to convert to Text
            return "aws.json.JsonValue.JsonString (Text.fromUtf8 (Bytes.toBase64 " + varName + "))";
        } else if (shape.isTimestampShape()) {
            return "aws.json.JsonValue.JsonString " + varName;
        } else if (shape.isListShape()) {
            software.amazon.smithy.model.shapes.ListShape listShape = shape.asListShape().get();
            software.amazon.smithy.model.shapes.Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            String elemConversion = generateJsonValue(memberTarget, "elem", model, clientNamespace);
            return String.format("aws.json.JsonValue.JsonArray (List.map (elem -> %s) %s)", elemConversion, varName);
        } else if (shape.isMapShape()) {
            software.amazon.smithy.model.shapes.MapShape mapShape = shape.asMapShape().get();
            software.amazon.smithy.model.shapes.Shape keyTarget = model.expectShape(mapShape.getKey().getTarget());
            software.amazon.smithy.model.shapes.Shape valueTarget = model.expectShape(mapShape.getValue().getTarget());
            
            // Check if key is an enum that needs conversion to Text
            String keyConversion;
            if (keyTarget.isEnumShape() || (keyTarget.isStringShape() && keyTarget.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
                String keyToTextFn = UnisonSymbolProvider.toNamespacedFunctionName(keyTarget.getId().getName() + "ToText", clientNamespace);
                keyConversion = keyToTextFn + " k";
            } else {
                keyConversion = "k";  // Key is already Text
            }
            
            String valueConversion = generateJsonValue(valueTarget, "v", model, clientNamespace);
            return String.format("aws.json.jsonObject (List.map (cases (k, v) -> (%s, %s)) (Map.toList %s))",
                    keyConversion, valueConversion, varName);
        } else if (shape.isStructureShape()) {
            // Nested structure - need recursive serialization
            String serializerName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    shape.getId().getName() + "ToJson");
            return serializerName + " " + varName;
        } else if (shape.isUnionShape()) {
            // Union - need serializer
            String serializerName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    shape.getId().getName() + "ToJson");
            return serializerName + " " + varName;
        } else {
            // Fallback
            return "aws.json.JsonValue.JsonString (Any.toText " + varName + ")";
        }
    }
    
    /**
     * Generates a JSON serializer for a structure (for nested structures).
     * 
     * <p>Adapted from AwsJsonProtocolGenerator.
     * 
     * @param structure The structure shape
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    public void generateStructureSerializer(StructureShape structure, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        if (structure.getAllMembers().isEmpty()) {
            return; // No fields to serialize
        }
        
        String structType = UnisonSymbolProvider.toNamespacedTypeName(structure.getId().getName(), clientNamespace);
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                structure.getId().getName() + "ToJson");
        
        writer.writeComment("Serialize " + structure.getId().getName() + " to JSON");
        writer.writeSignature(functionName, structType + " -> aws.json.JsonValue");
        writer.write("$L input =", functionName);
        writer.indent();
        writer.write("let");
        writer.indent();
        
        // Generate field list
        writer.write("fields = [");
        writer.indent();
        
        List<MemberShape> members = structure.getAllMembers().values().stream().toList();
        for (int i = 0; i < members.size(); i++) {
            MemberShape member = members.get(i);
            String jsonName = getJsonName(member);
            boolean isLast = (i == members.size() - 1);
            
            writer.write("(\"$L\", $L)$L",
                    jsonName,
                    generateJsonValueForMember(member, model, clientNamespace, "input"),
                    isLast ? "" : ",");
        }
        
        writer.dedent();
        writer.write("]");
        
        // Filter out null values
        writer.write("isNotNull = cases");
        writer.indent();
        writer.write("(_, aws.json.JsonValue.JsonNull) -> false");
        writer.write("_ -> true");
        writer.dedent();
        writer.write("filteredFields = List.filter isNotNull fields");
        
        // Create JSON object
        writer.write("aws.json.jsonObject filteredFields");
        
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    // ========== Deserializer Collection and Generation (Phase 2) ==========
    
    /**
     * Collects all structures that need FromJson deserializers.
     * 
     * <p>Walks through all operation outputs and their nested structures recursively.
     * 
     * @param service The service shape
     * @param model The Smithy model
     * @return Set of structure shapes that need deserializers
     */
    private Set<StructureShape> collectStructuresNeedingDeserializers(ServiceShape service, Model model) {
        Set<StructureShape> structures = new HashSet<>();
        
        // Collect from all operation outputs
        for (ShapeId opId : service.getOperations()) {
            OperationShape operation = model.expectShape(opId, OperationShape.class);
            operation.getOutput().ifPresent(outputId -> {
                StructureShape output = model.expectShape(outputId, StructureShape.class);
                collectStructuresRecursively(output, model, structures);
            });
        }
        
        return structures;
    }
    
    /**
     * Recursively collects all structure shapes that need deserializers.
     * 
     * @param structure The structure to process
     * @param model The Smithy model
     * @param collected Set to accumulate found structures
     */
    private void collectStructuresRecursively(StructureShape structure, Model model, Set<StructureShape> collected) {
        // Add this structure if not already collected
        if (collected.contains(structure)) {
            return; // Already processed
        }
        
        // Don't add yet - process members first to get dependency order (nested first)
        
        // Process all member target shapes
        for (MemberShape member : structure.getAllMembers().values()) {
            Shape targetShape = model.expectShape(member.getTarget());
            collectShapeRecursively(targetShape, model, collected);
        }
        
        // Now add this structure after its dependencies
        collected.add(structure);
    }
    
    /**
     * Recursively collects structures from any shape type.
     * 
     * @param shape The shape to process
     * @param model The Smithy model
     * @param collected Set to accumulate found structures
     */
    private void collectShapeRecursively(Shape shape, Model model, Set<StructureShape> collected) {
        if (shape.isStructureShape()) {
            collectStructuresRecursively(shape.asStructureShape().get(), model, collected);
        } else if (shape.isListShape()) {
            ListShape listShape = shape.asListShape().get();
            Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            collectShapeRecursively(memberTarget, model, collected);
        } else if (shape.isMapShape()) {
            MapShape mapShape = shape.asMapShape().get();
            Shape valueTarget = model.expectShape(mapShape.getValue().getTarget());
            collectShapeRecursively(valueTarget, model, collected);
        } else if (shape.isUnionShape()) {
            // TODO: Handle union shapes if needed
        }
    }
    
    /**
     * Collects all enums that need FromJson deserializers.
     * 
     * @param service The service shape
     * @param model The Smithy model
     * @return Set of enum shape IDs
     */
    private Set<ShapeId> collectEnumsNeedingDeserializers(ServiceShape service, Model model) {
        Set<ShapeId> enums = new HashSet<>();
        
        // Collect from all operation outputs
        for (ShapeId opId : service.getOperations()) {
            OperationShape operation = model.expectShape(opId, OperationShape.class);
            operation.getOutput().ifPresent(outputId -> {
                StructureShape output = model.expectShape(outputId, StructureShape.class);
                collectEnumsRecursively(output, model, enums);
            });
        }
        
        return enums;
    }
    
    /**
     * Collects all map shapes from a set of structures.
     * 
     * <p>This is used for selective operation generation where we only want
     * to generate deserializers for maps referenced by selected operations.
     * 
     * @param structures The structures to collect from
     * @param model The Smithy model
     * @return Set of map shapes (one per unique key+value type combination)
     */
    public Set<MapShape> collectMapsFromStructures(Set<StructureShape> structures, Model model) {
        Map<String, MapShape> mapsByKeyAndValueType = new HashMap<>();
        
        // Collect maps from all structures
        for (StructureShape structure : structures) {
            collectMapsRecursively(structure, model, mapsByKeyAndValueType);
        }
        
        return new HashSet<>(mapsByKeyAndValueType.values());
    }
    
    /**
     * Collects all map shapes that need FromJson deserializers.
     * 
     * <p>Returns one representative map shape for each unique key+value type combination.
     * We deduplicate by key and value type names since maps with different key types
     * need different deserializer functions (e.g., for enum key conversion).
     * 
     * @param service The service shape
     * @param model The Smithy model
     * @return Set of map shapes (one per unique key+value type combination)
     */
    public Set<MapShape> collectMapsNeedingDeserializers(ServiceShape service, Model model) {
        Map<String, MapShape> mapsByKeyAndValueType = new HashMap<>();
        
        // Collect from direct service operations
        for (ShapeId opId : service.getOperations()) {
            OperationShape operation = model.expectShape(opId, OperationShape.class);
            operation.getOutput().ifPresent(outputId -> {
                StructureShape output = model.expectShape(outputId, StructureShape.class);
                collectMapsRecursively(output, model, mapsByKeyAndValueType);
            });
        }
        
        // Also collect from resource operations
        for (ShapeId resourceId : service.getResources()) {
            software.amazon.smithy.model.shapes.ResourceShape resource = model.expectShape(resourceId, software.amazon.smithy.model.shapes.ResourceShape.class);
            collectMapsFromResource(resource, model, mapsByKeyAndValueType);
        }
        
        return new HashSet<>(mapsByKeyAndValueType.values());
    }
    
    /**
     * Recursively collects maps from a resource and its operations.
     */
    private void collectMapsFromResource(software.amazon.smithy.model.shapes.ResourceShape resource, Model model, Map<String, MapShape> collected) {
        // Collect from all resource operations
        for (ShapeId opId : resource.getAllOperations()) {
            OperationShape operation = model.expectShape(opId, OperationShape.class);
            operation.getOutput().ifPresent(outputId -> {
                StructureShape output = model.expectShape(outputId, StructureShape.class);
                collectMapsRecursively(output, model, collected);
            });
        }
        
        // Recursively collect from child resources
        for (ShapeId childResourceId : resource.getResources()) {
            software.amazon.smithy.model.shapes.ResourceShape childResource = model.expectShape(childResourceId, software.amazon.smithy.model.shapes.ResourceShape.class);
            collectMapsFromResource(childResource, model, collected);
        }
    }
    
    /**
     * Collects all list shapes from a set of structures.
     * 
     * <p>This is used for selective operation generation where we only want
     * to generate deserializers for lists referenced by selected operations.
     * 
     * @param structures The structures to collect from
     * @param model The Smithy model
     * @return Set of list shapes (one per unique element type)
     */
    public Set<ListShape> collectListsFromStructures(Set<StructureShape> structures, Model model) {
        Map<String, ListShape> listsByElementType = new HashMap<>();
        
        // Collect lists from all structures
        for (StructureShape structure : structures) {
            collectListsRecursively(structure, model, listsByElementType);
        }
        
        return new HashSet<>(listsByElementType.values());
    }
    
    /**
     * Collects all list shapes that need FromJson deserializers.
     * 
     * <p>Returns one representative list shape for each unique element type.
     * We deduplicate by element type name since multiple list shapes with the same
     * element type should generate the same deserializer function.
     * 
     * @param service The service shape
     * @param model The Smithy model
     * @return Set of list shapes (one per unique element type)
     */
    public Set<ListShape> collectListsNeedingDeserializers(ServiceShape service, Model model) {
        Map<String, ListShape> listsByElementType = new HashMap<>();
        
        // Collect from direct service operations
        for (ShapeId opId : service.getOperations()) {
            OperationShape operation = model.expectShape(opId, OperationShape.class);
            operation.getOutput().ifPresent(outputId -> {
                StructureShape output = model.expectShape(outputId, StructureShape.class);
                collectListsRecursively(output, model, listsByElementType);
            });
        }
        
        // Also collect from resource operations
        for (ShapeId resourceId : service.getResources()) {
            software.amazon.smithy.model.shapes.ResourceShape resource = model.expectShape(resourceId, software.amazon.smithy.model.shapes.ResourceShape.class);
            collectListsFromResource(resource, model, listsByElementType);
        }
        
        return new HashSet<>(listsByElementType.values());
    }
    
    /**
     * Recursively collects lists from a resource and its operations.
     */
    private void collectListsFromResource(software.amazon.smithy.model.shapes.ResourceShape resource, Model model, Map<String, ListShape> collected) {
        // Collect from all resource operations
        for (ShapeId opId : resource.getAllOperations()) {
            OperationShape operation = model.expectShape(opId, OperationShape.class);
            operation.getOutput().ifPresent(outputId -> {
                StructureShape output = model.expectShape(outputId, StructureShape.class);
                collectListsRecursively(output, model, collected);
            });
        }
        
        // Recursively collect from child resources
        for (ShapeId childResourceId : resource.getResources()) {
            software.amazon.smithy.model.shapes.ResourceShape childResource = model.expectShape(childResourceId, software.amazon.smithy.model.shapes.ResourceShape.class);
            collectListsFromResource(childResource, model, collected);
        }
    }
    
    /**
     * Recursively collects list shapes from a structure.
     * 
     * @param structure The structure to process
     * @param model The Smithy model
     * @param collected Map from element type name to list shape
     */
    private void collectListsRecursively(StructureShape structure, Model model, Map<String, ListShape> collected) {
        for (MemberShape member : structure.getAllMembers().values()) {
            Shape targetShape = model.expectShape(member.getTarget());
            collectListsFromShape(targetShape, model, collected);
        }
    }
    
    /**
     * Collects lists from any shape type.
     * 
     * @param shape The shape to process
     * @param model The Smithy model
     * @param collected Map from element type name to list shape
     */
    private void collectListsFromShape(Shape shape, Model model, Map<String, ListShape> collected) {
        if (shape.isListShape()) {
            ListShape listShape = shape.asListShape().get();
            Shape elementShape = model.expectShape(listShape.getMember().getTarget());
            String elementTypeName = getShapeTypeName(elementShape, model);
            // Only store if not already present (deduplicate by element type)
            collected.putIfAbsent(elementTypeName, listShape);
            // Also collect from element type (in case it contains nested lists)
            collectListsFromShape(elementShape, model, collected);
        } else if (shape.isMapShape()) {
            MapShape mapShape = shape.asMapShape().get();
            Shape valueShape = model.expectShape(mapShape.getValue().getTarget());
            collectListsFromShape(valueShape, model, collected);
        } else if (shape.isStructureShape()) {
            collectListsRecursively(shape.asStructureShape().get(), model, collected);
        }
    }
    
    /**
     * Recursively collects map shapes from a structure.
     * 
     * @param structure The structure to process
     * @param model The Smithy model
     * @param collected Map from value type name to map shape
     */
    private void collectMapsRecursively(StructureShape structure, Model model, Map<String, MapShape> collected) {
        for (MemberShape member : structure.getAllMembers().values()) {
            Shape targetShape = model.expectShape(member.getTarget());
            collectMapsFromShape(targetShape, model, collected);
        }
    }
    
    /**
     * Collects maps from any shape type.
     * 
     * @param shape The shape to process
     * @param model The Smithy model
     * @param collected Map from key+value type name to map shape
     */
    private void collectMapsFromShape(Shape shape, Model model, Map<String, MapShape> collected) {
        if (shape.isMapShape()) {
            MapShape mapShape = shape.asMapShape().get();
            Shape keyShape = model.expectShape(mapShape.getKey().getTarget());
            Shape valueShape = model.expectShape(mapShape.getValue().getTarget());
            String keyTypeName = getShapeTypeName(keyShape, model);
            String valueTypeName = getShapeTypeName(valueShape, model);
            // Deduplicate by both key and value types
            String mapKey = keyTypeName + "_" + valueTypeName;
            collected.putIfAbsent(mapKey, mapShape);
            // Also collect from key and value types (in case they contain nested maps)
            collectMapsFromShape(keyShape, model, collected);
            collectMapsFromShape(valueShape, model, collected);
        } else if (shape.isListShape()) {
            ListShape listShape = shape.asListShape().get();
            Shape elementShape = model.expectShape(listShape.getMember().getTarget());
            collectMapsFromShape(elementShape, model, collected);
        } else if (shape.isStructureShape()) {
            collectMapsRecursively(shape.asStructureShape().get(), model, collected);
        }
    }
    
    /**
     * Recursively collects enum shapes.
     * 
     * @param structure The structure to process
     * @param model The Smithy model
     * @param collected Set to accumulate found enum IDs
     */
    private void collectEnumsRecursively(StructureShape structure, Model model, Set<ShapeId> collected) {
        for (MemberShape member : structure.getAllMembers().values()) {
            Shape targetShape = model.expectShape(member.getTarget());
            collectEnumsFromShape(targetShape, model, collected);
        }
    }
    
    /**
     * Collects enums from any shape type.
     * 
     * @param shape The shape to process
     * @param model The Smithy model
     * @param collected Set to accumulate found enum IDs
     */
    private void collectEnumsFromShape(Shape shape, Model model, Set<ShapeId> collected) {
        if (shape.isEnumShape() || (shape.isStringShape() && shape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            collected.add(shape.getId());
        } else if (shape.isListShape()) {
            ListShape listShape = shape.asListShape().get();
            Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            collectEnumsFromShape(memberTarget, model, collected);
        } else if (shape.isMapShape()) {
            MapShape mapShape = shape.asMapShape().get();
            Shape keyTarget = model.expectShape(mapShape.getKey().getTarget());
            Shape valueTarget = model.expectShape(mapShape.getValue().getTarget());
            collectEnumsFromShape(keyTarget, model, collected);
            collectEnumsFromShape(valueTarget, model, collected);
        } else if (shape.isStructureShape()) {
            collectEnumsRecursively(shape.asStructureShape().get(), model, collected);
        }
    }
    
    /**
     * Generates a FromJson deserializer for a structure.
     * 
     * <p>Generates a function that takes JsonValue and returns the structure with Exception ability.
     * 
     * @param structure The structure shape
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    public void generateStructureDeserializer(StructureShape structure, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        String structType = UnisonSymbolProvider.toNamespacedTypeName(structure.getId().getName(), clientNamespace);
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                structure.getId().getName() + "FromJson");
        
        writer.writeComment("Deserialize " + structure.getId().getName() + " from JSON");
        writer.writeSignature(functionName, "core.Json -> '{Exception} " + structType);
        
        List<MemberShape> members = structure.getAllMembers().values().stream().toList();
        String baseTypeName = UnisonSymbolProvider.toUnisonTypeName(structure.getId().getName());
        
        if (members.isEmpty()) {
            // Empty structure - just return the constructor
            writer.write("$L json = do", functionName);
            writer.indent();
            writer.write(baseTypeName);
            writer.dedent();
        } else {
            writer.write("$L json = do", functionName);
            writer.indent();
            // Extract fields
            for (MemberShape member : members) {
                String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
                String jsonName = getJsonName(member);
                Shape targetShape = model.expectShape(member.getTarget());
                String deserializer = getDeserializerForType(targetShape, model, clientNamespace);
                
                // Check if field is non-optional - must match StructureGenerator and AwsJsonProtocolGenerator logic
                // HTTP-bound parameters are always optional in generated types
                // Fields with @required OR @default are non-optional in the generated type
                boolean isHttpBound = member.hasTrait(software.amazon.smithy.model.traits.HttpQueryTrait.class) ||
                                    member.hasTrait(software.amazon.smithy.model.traits.HttpHeaderTrait.class) ||
                                    member.hasTrait(software.amazon.smithy.model.traits.HttpPrefixHeadersTrait.class);
                boolean hasDefault = member.hasTrait(software.amazon.smithy.model.traits.DefaultTrait.class);
                boolean isNonOptional = (member.isRequired() || hasDefault) && !isHttpBound;
                
                // Generate field extraction using core.Json helpers
                generateCoreJsonFieldExtraction(memberName, jsonName, targetShape, member, model, clientNamespace, writer, isNonOptional);
            }
            
            // Build structure using positional constructor
            writer.write("");
            writer.write("-- Build structure");
            List<String> fieldNames = new ArrayList<>();
            for (MemberShape member : members) {
                String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
                fieldNames.add(memberName);
            }
            
            // Use base type name for constructor (Unison constructor resolution)
            writer.write("$L $L", baseTypeName, String.join(" ", fieldNames));
            
            writer.dedent();
        }
        
        writer.writeBlankLine();
    }
    
    /**
     * Generates a FromJson deserializer for an enum.
     * 
     * <p>Generates a function that parses a JSON string to an enum value.
     * 
     * @param enumId The enum shape ID
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    public void generateEnumDeserializer(ShapeId enumId, UnisonWriter writer, UnisonContext context) {
        String clientNamespace = context.settings().getClientNamespace();
        
        String enumType = UnisonSymbolProvider.toNamespacedTypeName(enumId.getName(), clientNamespace);
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                enumId.getName() + "FromJson");
        String fromTextFunc = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                enumId.getName() + "FromText");
        
        writer.writeComment("Deserialize " + enumId.getName() + " from JSON");
        writer.writeSignature(functionName, "core.Json -> '{Exception} " + enumType);
        writer.write("$L json = do", functionName);
        writer.indent();
        
        writer.write("match json with");
        writer.indent();
        writer.write("core.Json.Text value ->");
        writer.indent();
        writer.write("match $L value with", fromTextFunc);
        writer.indent();
        writer.write("Some e -> e");
        writer.write("None -> Exception.raise (Generic.failure \"Invalid enum value\" value)");
        writer.dedent();
        writer.dedent();
        writer.write("_ -> Exception.raise (Generic.failure \"Expected string for enum\" json)");
        writer.dedent();
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates a FromJson deserializer for a union type.
     * 
     * <p>For REST-JSON, unions are untagged and we try to parse each variant.
     * 
     * @param unionShape The union shape
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    public void generateUnionDeserializer(software.amazon.smithy.model.shapes.UnionShape unionShape, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        String unionType = UnisonSymbolProvider.toNamespacedTypeName(unionShape.getId().getName(), clientNamespace);
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                unionShape.getId().getName() + "FromJson");
        
        writer.writeComment("Deserialize " + unionShape.getId().getName() + " from JSON (union type)");
        writer.writeSignature(functionName, "core.Json -> '{Exception} " + unionType);
        writer.write("$L json = do", functionName);
        writer.indent();
        
        // For REST-JSON unions, try to parse as each variant
        // We attempt each variant and use the first that succeeds
        List<MemberShape> members = new ArrayList<>(unionShape.getAllMembers().values());
        
        if (members.isEmpty()) {
            writer.write("Exception.raise (Generic.failure \"Empty union\" \"No members\")");
        } else {
            // Generate try-parse logic for each member
            // Use nested match on Either to try each variant
            for (int i = 0; i < members.size(); i++) {
                MemberShape member = members.get(i);
                Shape memberTarget = model.expectShape(member.getTarget());
                String constructorName = UnisonSymbolProvider.toUnisonTypeName(unionShape.getId().getName()) + "'" +
                        UnisonSymbolProvider.toUnisonTypeName(member.getMemberName());
                String memberDeserializer = getDeserializerForType(memberTarget, model, clientNamespace);
                
                writer.write("match catch do");
                writer.indent();
                writer.write("value = !($L json)", memberDeserializer);
                writer.write("$L value", constructorName);
                writer.dedent();
                writer.write("with");
                writer.indent();
                writer.write("Right result -> result");
                
                if (i < members.size() - 1) {
                    writer.write("Left _ ->");
                    writer.indent();
                } else {
                    // Last variant - re-raise the exception
                    writer.write("Left err -> Exception.raise err");
                    writer.dedent(); // Close match
                }
            }
            
            // Close all the nested "Left _ ->" blocks
            for (int i = 0; i < members.size() - 1; i++) {
                writer.dedent(); // Close the "Left _ ->" indent
                writer.dedent(); // Close match indent
            }
        }
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates a FromJson deserializer for a map type.
     * 
     * <p>Generates a function that parses a JSON object to a Map.
     * 
     * @param mapShape The map shape
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    public void generateMapDeserializer(MapShape mapShape, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        // Get key and value shapes
        Shape keyShape = model.expectShape(mapShape.getKey().getTarget());
        Shape valueShape = model.expectShape(mapShape.getValue().getTarget());
        
        String keyTypeName = getShapeTypeName(keyShape, model);
        String valueTypeName = getShapeTypeName(valueShape, model);
        
        // Generate a unique function name based on both key and value types
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                "mapOf" + keyTypeName + "To" + valueTypeName + "FromJson");
        
        String valueDeserializer = getDeserializerForType(valueShape, model, clientNamespace);
        String keyType = getUnisonTypeForShape(keyShape, model, clientNamespace);
        String valueType = getUnisonTypeForShape(valueShape, model, clientNamespace);
        
        writer.writeComment("Deserialize map from " + keyTypeName + " to " + valueTypeName + " from JSON");
        writer.writeSignature(functionName, "core.Json -> '{Exception} Map " + keyType + " " + valueType);
        writer.write("$L json =", functionName);
        writer.indent();
        writer.write("do");
        writer.indent();
        writer.write("fields = match json with");
        writer.indent();
        writer.write("core.Json.Object f -> f");
        writer.write("_ -> Exception.raise (Generic.failure \"Expected JSON object for map\" json)");
        writer.dedent();
        writer.write("valuePairs = List.map (cases (k, v) -> (k, !($L v))) fields", valueDeserializer);
        
        // If key is an enum, convert Text keys to enum keys
        if (keyShape.isEnumShape() || (keyShape.isStringShape() && keyShape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            String keyDeserializer = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    keyTypeName + "FromText");
            writer.write("-- Convert Text keys to enum keys");
            writer.write("convertKey = cases k -> match ($L k) with", keyDeserializer);
            writer.indent();
            writer.write("Some e -> e");
            writer.write("None -> Exception.raise (Generic.failure \"Invalid enum key\" k)");
            writer.dedent();
            writer.write("keyPairs = List.map (cases (k, v) -> (convertKey k, v)) valuePairs");
            writer.write("Map.fromList keyPairs");
        } else {
            writer.write("Map.fromList valuePairs");
        }
        
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates a FromJson deserializer for a list type.
     * 
     * <p>Generates a function that parses a JSON array to a list.
     * 
     * @param listShape The list shape
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    public void generateListDeserializer(ListShape listShape, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        // Generate a unique function name based on the element type
        Shape elementShape = model.expectShape(listShape.getMember().getTarget());
        String elementTypeName = getShapeTypeName(elementShape, model);
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                "listOf" + elementTypeName + "FromJson");
        
        String elementDeserializer = getDeserializerForType(elementShape, model, clientNamespace);
        
        writer.writeComment("Deserialize list of " + elementTypeName + " from JSON");
        writer.writeSignature(functionName, "core.Json -> '{Exception} [" + 
                             getUnisonTypeForShape(elementShape, model, clientNamespace) + "]");
        writer.write("$L json =", functionName);
        writer.indent();
        writer.write("do");
        writer.indent();
        writer.write("items = match json with");
        writer.indent();
        writer.write("core.Json.Array arr -> arr");
        writer.write("_ -> Exception.raise (Generic.failure \"Expected JSON array for list\" json)");
        writer.dedent();
        writer.write("List.map (elem -> !($L elem)) items", elementDeserializer);
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Gets a simple type name for a shape (used for naming map/list deserializers).
     */
    private String getShapeTypeName(Shape shape, Model model) {
        // Check enum FIRST (before string check, since enums may be string-based)
        if (shape.isEnumShape() || (shape.isStringShape() && shape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            return shape.getId().getName();
        } else if (shape.isStringShape()) {
            return "Text";
        } else if (shape.isIntegerShape() || shape.isLongShape()) {
            return "Int";
        } else if (shape.isFloatShape() || shape.isDoubleShape()) {
            return "Float";
        } else if (shape.isBooleanShape()) {
            return "Boolean";
        } else if (shape.isBlobShape()) {
            return "Bytes";
        } else if (shape.isStructureShape()) {
            return shape.getId().getName();
        } else if (shape.isListShape()) {
            // For lists, generate name based on element type
            ListShape listShape = shape.asListShape().get();
            Shape elementShape = model.expectShape(listShape.getMember().getTarget());
            return "ListOf" + getShapeTypeName(elementShape, model);
        } else if (shape.isMapShape()) {
            // For maps, generate name based on value type
            MapShape mapShape = shape.asMapShape().get();
            Shape valueShape = model.expectShape(mapShape.getValue().getTarget());
            return "MapOf" + getShapeTypeName(valueShape, model);
        } else {
            return "Value";
        }
    }
    
    /**
     * Gets the Unison type string for a shape.
     */
    private String getUnisonTypeForShape(Shape shape, Model model, String clientNamespace) {
        // Check enum FIRST (before string check, since enums may be string-based)
        if (shape.isEnumShape() || (shape.isStringShape() && shape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            return UnisonSymbolProvider.toNamespacedTypeName(shape.getId().getName(), clientNamespace);
        } else if (shape.isStringShape()) {
            return "Text";
        } else if (shape.isIntegerShape() || shape.isLongShape() || shape.isShortShape() || shape.isByteShape()) {
            return "Int";
        } else if (shape.isFloatShape() || shape.isDoubleShape()) {
            return "Float";
        } else if (shape.isBooleanShape()) {
            return "Boolean";
        } else if (shape.isBlobShape()) {
            return "Bytes";
        } else if (shape.isStructureShape()) {
            return UnisonSymbolProvider.toNamespacedTypeName(shape.getId().getName(), clientNamespace);
        } else if (shape.isListShape()) {
            ListShape listShape = shape.asListShape().get();
            Shape elementShape = model.expectShape(listShape.getMember().getTarget());
            return "[" + getUnisonTypeForShape(elementShape, model, clientNamespace) + "]";
        } else {
            return "Text"; // fallback
        }
    }
    
    @Override
    public void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        Optional<StructureShape> outputShape = ProtocolUtils.getOutputShape(operation, model);
        if (outputShape.isEmpty()) {
            // No output - no parser needed
            return;
        }
        
        StructureShape output = outputShape.get();
        String outputType = UnisonSymbolProvider.toNamespacedTypeName(output.getId().getName(), clientNamespace);
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                operation.getId().getName() + "ResponseParser");
        
        writer.writeComment("Parse " + output.getId().getName() + " from REST-JSON response");
        writer.writeSignature(functionName, "Http.Response -> '{Exception} " + outputType);
        writer.write("$L response = do", functionName);
        writer.indent();
        
        // Check if there are body members to parse
        List<MemberShape> bodyMembers = getResponseBodyMembers(output);
        boolean hasBodyMembers = !bodyMembers.isEmpty();
        
        if (hasBodyMembers) {
            // Parse JSON from response body using core.Json
            writer.write("-- Parse JSON response body");
            writer.write("json = !(aws.json.bridge.deserializeCoreJsonResponse (Http.Response.body response))");
            writer.write("");
        }
        
        // Extract field values
        List<MemberShape> members = output.getAllMembers().values().stream().toList();
        for (MemberShape member : members) {
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            
            // Check if member is HTTP-bound
            if (member.hasTrait(HttpHeaderTrait.class)) {
                // Extract from response header
                generateResponseHeaderExtraction(member, model, writer, context);
            } else if (member.hasTrait(HttpResponseCodeTrait.class)) {
                // Extract status code (convert Nat to Int)
                writer.write("$L = Nat.toInt (Http.Response.statusCode response)", memberName);
            } else if (member.hasTrait(HttpPayloadTrait.class)) {
                // Extract raw payload
                generateResponsePayloadExtraction(member, model, writer, context);
            } else {
                // Extract from JSON body using core.Json helpers
                String jsonName = getJsonName(member);
                Shape targetShape = model.expectShape(member.getTarget());
                
                // Check if field is non-optional (required or has default value)
                boolean hasDefault = member.hasTrait(software.amazon.smithy.model.traits.DefaultTrait.class);
                boolean isNonOptional = member.isRequired() || hasDefault;
                
                // Generate field extraction using core.Json helpers
                generateCoreJsonFieldExtraction(memberName, jsonName, targetShape, member, model, clientNamespace, writer, isNonOptional);
            }
        }
        
        // Build output structure using positional constructor
        writer.write("");
        writer.write("-- Build output structure");
        List<String> fieldNames = new ArrayList<>();
        for (MemberShape member : members) {
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            fieldNames.add(memberName);
        }
        
        // Use base type name for constructor (Unison constructor resolution)
        String baseTypeName = UnisonSymbolProvider.toUnisonTypeName(output.getId().getName());
        if (fieldNames.isEmpty()) {
            writer.write(baseTypeName);
        } else {
            writer.write("$L $L", baseTypeName, String.join(" ", fieldNames));
        }
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Gets body members from output (not HTTP-bound).
     * 
     * @param output The output structure
     * @return List of body members
     */
    private List<MemberShape> getResponseBodyMembers(StructureShape output) {
        return output.getAllMembers().values().stream()
                .filter(m -> !m.hasTrait(HttpHeaderTrait.class) 
                        && !m.hasTrait(HttpResponseCodeTrait.class)
                        && !m.hasTrait(HttpPayloadTrait.class))
                .toList();
    }
    
    /**
     * Generates response header extraction code.
     * 
     * @param member The member with @httpHeader
     * @param model The Smithy model
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    private void generateResponseHeaderExtraction(MemberShape member, Model model, 
                                                   UnisonWriter writer, UnisonContext context) {
        String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
        String headerName = getHeaderName(member);
        Shape targetShape = model.expectShape(member.getTarget());
        
        // Get the header value as Text
        writer.write("-- Extract @httpHeader: $L", headerName);
        writer.write("$L = aws.http.getHeader \"$L\" (Http.Response.headers response)", memberName, headerName);
    }
    
    /**
     * Generates response payload extraction code.
     * 
     * @param member The member with @httpPayload
     * @param model The Smithy model
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    private void generateResponsePayloadExtraction(MemberShape member, Model model,
                                                     UnisonWriter writer, UnisonContext context) {
        String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
        Shape targetShape = model.expectShape(member.getTarget());
        String clientNamespace = context.settings().getClientNamespace();
        boolean isOptional = !member.isRequired() && !member.hasTrait(software.amazon.smithy.model.traits.DefaultTrait.class);
        
        if (targetShape.isBlobShape()) {
            // Blob payload - use response body directly
            if (isOptional) {
                writer.write("body = Http.Response.body response");
                writer.write("$L = if Bytes.size body Nat.== 0 then None else Some body", memberName);
            } else {
                writer.write("$L = Http.Response.body response", memberName);
            }
        } else if (targetShape.isStringShape()) {
            // String payload - convert from bytes
            if (isOptional) {
                writer.write("body = Http.Response.body response");
                writer.write("$L = if Bytes.size body Nat.== 0 then None else Some (aws.http.bytesToText body)", memberName);
            } else {
                writer.write("$L = aws.http.bytesToText (Http.Response.body response)", memberName);
            }
        } else if (targetShape.isStructureShape() || targetShape.isUnionShape()) {
            // Structure/Union payload - parse as JSON
            String deserializer = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    targetShape.getId().getName() + "FromJson");
            writer.write("payloadText = aws.http.bytesToText (Http.Response.body response)");
            if (isOptional) {
                writer.write("$L = if Text.size payloadText Nat.== 0 then None else", memberName);
                writer.indent();
                writer.write("payloadJson = !(aws.json.parseJson payloadText)");
                writer.write("Some (!($L payloadJson))", deserializer);
                writer.dedent();
            } else {
                writer.write("payloadJson = !(aws.json.parseJson payloadText)");
                writer.write("$L = !($L payloadJson)", memberName, deserializer);
            }
        } else {
            // Fallback - treat as text
            if (isOptional) {
                writer.write("body = Http.Response.body response");
                writer.write("$L = if Bytes.size body Nat.== 0 then None else Some (aws.http.bytesToText body)", memberName);
            } else {
                writer.write("$L = aws.http.bytesToText (Http.Response.body response)", memberName);
            }
        }
    }
    
    /**
     * Generates field extraction code using core.Json helper functions.
     * 
     * <p>This method generates the appropriate extraction code based on the target shape type,
     * using the new core.Json helper functions in aws_json_bridge.u.
     * 
     * @param varName The variable name for the extracted field
     * @param jsonName The JSON field name
     * @param targetShape The target shape of the member
     * @param member The member shape
     * @param model The Smithy model
     * @param clientNamespace The client namespace
     * @param writer The Unison code writer
     * @param isNonOptional Whether the field is non-optional (required or has default)
     */
    private void generateCoreJsonFieldExtraction(String varName, String jsonName, Shape targetShape,
            MemberShape member, Model model, String clientNamespace, UnisonWriter writer, boolean isNonOptional) {
        
        if (targetShape.isEnumShape() || (targetShape.isStringShape() && targetShape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            // Enum - find text then convert via FromText function
            String fromTextFunc = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    targetShape.getId().getName() + "FromText");
            if (isNonOptional) {
                String tempVar = varName + "Opt";
                writer.write("$L = aws.json.bridge.coreJsonFindText \"$L\" json |> Optional.flatMap $L", 
                        tempVar, jsonName, fromTextFunc);
                writer.write("$L = match $L with", varName, tempVar);
                writer.indent();
                writer.write("Some v -> v");
                writer.write("None -> Exception.raise (Generic.failure \"Missing required field: $L\" \"$L\")", jsonName, jsonName);
                writer.dedent();
            } else {
                writer.write("$L = aws.json.bridge.coreJsonFindText \"$L\" json |> Optional.flatMap $L", 
                        varName, jsonName, fromTextFunc);
            }
        } else if (targetShape.isStringShape()) {
            if (isNonOptional) {
                String tempVar = varName + "Opt";
                writer.write("$L = aws.json.bridge.coreJsonFindText \"$L\" json", tempVar, jsonName);
                writer.write("$L = Optional.getOrElse \"\" $L", varName, tempVar);
            } else {
                writer.write("$L = aws.json.bridge.coreJsonFindText \"$L\" json", varName, jsonName);
            }
        } else if (targetShape.isIntegerShape() || targetShape.isLongShape() || targetShape.isShortShape() || targetShape.isByteShape()) {
            if (isNonOptional) {
                String tempVar = varName + "Opt";
                writer.write("$L = aws.json.bridge.coreJsonFindInt \"$L\" json", tempVar, jsonName);
                writer.write("$L = Optional.getOrElse +0 $L", varName, tempVar);
            } else {
                writer.write("$L = aws.json.bridge.coreJsonFindInt \"$L\" json", varName, jsonName);
            }
        } else if (targetShape.isFloatShape() || targetShape.isDoubleShape()) {
            if (isNonOptional) {
                String tempVar = varName + "Opt";
                writer.write("$L = aws.json.bridge.coreJsonFindFloat \"$L\" json", tempVar, jsonName);
                writer.write("$L = Optional.getOrElse 0.0 $L", varName, tempVar);
            } else {
                writer.write("$L = aws.json.bridge.coreJsonFindFloat \"$L\" json", varName, jsonName);
            }
        } else if (targetShape.isBooleanShape()) {
            if (isNonOptional) {
                String tempVar = varName + "Opt";
                writer.write("$L = aws.json.bridge.coreJsonFindBool \"$L\" json", tempVar, jsonName);
                writer.write("$L = Optional.getOrElse false $L", varName, tempVar);
            } else {
                writer.write("$L = aws.json.bridge.coreJsonFindBool \"$L\" json", varName, jsonName);
            }
        } else if (targetShape.isBlobShape()) {
            // Blob - find text then base64 decode
            if (isNonOptional) {
                String tempVar = varName + "Opt";
                writer.write("$L = aws.json.bridge.coreJsonFindText \"$L\" json |> Optional.flatMap (t -> match builtin.Bytes.fromBase64 (toUtf8 t) with", tempVar, jsonName);
                writer.indent();
                writer.write("Right bytes -> Some bytes");
                writer.write("Left _ -> None)");
                writer.dedent();
                writer.write("$L = Optional.getOrElse (Bytes.empty) $L", varName, tempVar);
            } else {
                writer.write("$L = aws.json.bridge.coreJsonFindText \"$L\" json |> Optional.flatMap (t -> match builtin.Bytes.fromBase64 (toUtf8 t) with", varName, jsonName);
                writer.indent();
                writer.write("Right bytes -> Some bytes");
                writer.write("Left _ -> None)");
                writer.dedent();
            }
        } else if (targetShape.isTimestampShape()) {
            // Timestamp - stored as Text
            if (isNonOptional) {
                String tempVar = varName + "Opt";
                writer.write("$L = aws.json.bridge.coreJsonFindText \"$L\" json", tempVar, jsonName);
                writer.write("$L = Optional.getOrElse \"\" $L", varName, tempVar);
            } else {
                writer.write("$L = aws.json.bridge.coreJsonFindText \"$L\" json", varName, jsonName);
            }
        } else if (targetShape.isListShape()) {
            ListShape listShape = targetShape.asListShape().get();
            Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            
            if (memberTarget.isEnumShape() || (memberTarget.isStringShape() && memberTarget.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
                // List of enums - use effectful bridge to parse each text value through enum FromJson
                String parserName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                        memberTarget.getId().getName() + "FromJson");
                if (isNonOptional) {
                    writer.write("$L = !(aws.json.bridge.coreJsonParseListEffectful \"$L\" $L json)", varName, jsonName, parserName);
                } else {
                    writer.write("$L = !(aws.json.bridge.coreJsonParseOptionalListEffectful \"$L\" $L json)", varName, jsonName, parserName);
                }
            } else if (memberTarget.isStringShape()) {
                // List of strings - use findAllText which returns []
                if (!isNonOptional) {
                    // Make it Optional for optional fields
                    String tempVar = varName + "List";
                    writer.write("$L = aws.json.bridge.coreJsonFindAllText \"$L\" json", tempVar, jsonName);
                    writer.write("$L = if List.isEmpty $L then None else Some $L", varName, tempVar, tempVar);
                } else {
                    writer.write("$L = aws.json.bridge.coreJsonFindAllText \"$L\" json", varName, jsonName);
                }
            } else if (memberTarget.isIntegerShape() || memberTarget.isLongShape()) {
                // List of integers
                if (!isNonOptional) {
                    String tempVar = varName + "List";
                    writer.write("$L = aws.json.bridge.coreJsonFindAllInt \"$L\" json", tempVar, jsonName);
                    writer.write("$L = if List.isEmpty $L then None else Some $L", varName, tempVar, tempVar);
                } else {
                    writer.write("$L = aws.json.bridge.coreJsonFindAllInt \"$L\" json", varName, jsonName);
                }
            } else if (memberTarget.isStructureShape()) {
                // List of structures - use effectful bridge to call existing *FromJson parser
                String parserName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                        memberTarget.getId().getName() + "FromJson");
                if (isNonOptional) {
                    writer.write("$L = !(aws.json.bridge.coreJsonParseListEffectful \"$L\" $L json)", varName, jsonName, parserName);
                } else {
                    writer.write("$L = !(aws.json.bridge.coreJsonParseOptionalListEffectful \"$L\" $L json)", varName, jsonName, parserName);
                }
            } else {
                // Fallback for other list types
                if (isNonOptional) {
                    String tempVar = varName + "Opt";
                    writer.write("$L = aws.json.bridge.coreJsonFindArray \"$L\" json", tempVar, jsonName);
                    writer.write("$L = Optional.getOrElse [] $L", varName, tempVar);
                } else {
                    writer.write("$L = aws.json.bridge.coreJsonFindArray \"$L\" json", varName, jsonName);
                }
            }
        } else if (targetShape.isMapShape()) {
            MapShape mapShape = targetShape.asMapShape().get();
            Shape valueTarget = model.expectShape(mapShape.getValue().getTarget());
            
            if (valueTarget.isStringShape()) {
                // Map with string values - use multi-line format with arguments on separate lines
                // Convert list of tuples to Map
                if (!isNonOptional) {
                    String tempVar = varName + "List";
                    writer.write("$L =", tempVar);
                    writer.indent();
                    writer.write("aws.json.bridge.coreJsonParseMap \"$L\"", jsonName);
                    writer.indent();
                    writer.write("(j -> (match j with");
                    writer.indent();
                    writer.write("core.Json.Text t -> t");
                    writer.write("_ -> \"\"))");
                    writer.dedent();
                    writer.write("json");
                    writer.dedent();
                    writer.dedent();
                    writer.write("$L = if List.isEmpty $L then None else Some (lib.unison_base_3_18_0.data.Map.fromList $L)", varName, tempVar, tempVar);
                } else {
                    String tempVar = varName + "List";
                    writer.write("$L =", tempVar);
                    writer.indent();
                    writer.write("aws.json.bridge.coreJsonParseMap \"$L\"", jsonName);
                    writer.indent();
                    writer.write("(j -> (match j with");
                    writer.indent();
                    writer.write("core.Json.Text t -> t");
                    writer.write("_ -> \"\"))");
                    writer.dedent();
                    writer.write("json");
                    writer.dedent();
                    writer.dedent();
                    writer.write("$L = lib.unison_base_3_18_0.data.Map.fromList $L", varName, tempVar);
                }
            } else if (valueTarget.isStructureShape()) {
                // Map with structure values - use ViaJsonValue bridge to call existing *FromJson parser
                String parserName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                        valueTarget.getId().getName() + "FromJson");
                if (!isNonOptional) {
                    String tempVar = varName + "Map";
                    writer.write("$L = aws.json.bridge.coreJsonParseMapViaJsonValue \"$L\" $L json", tempVar, jsonName, parserName);
                    writer.write("$L = if List.isEmpty $L then None else Some $L", varName, tempVar, tempVar);
                } else {
                    writer.write("$L = aws.json.bridge.coreJsonParseMapViaJsonValue \"$L\" $L json", varName, jsonName, parserName);
                }
            } else {
                // Fallback
                if (isNonOptional) {
                    String tempVar = varName + "Opt";
                    writer.write("$L = aws.json.bridge.coreJsonFindObject \"$L\" json", tempVar, jsonName);
                    writer.write("$L = Optional.getOrElse (core.Json.Object []) $L", varName, tempVar);
                } else {
                    writer.write("$L = aws.json.bridge.coreJsonFindObject \"$L\" json", varName, jsonName);
                }
            }
        } else if (targetShape.isStructureShape()) {
            // Nested structure - use ViaJsonValue bridge to call existing *FromJson parser
            String parserName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    targetShape.getId().getName() + "FromJson");
            if (isNonOptional) {
                writer.write("$L = !(aws.json.bridge.coreJsonRequireNestedEffectful \"$L\" $L json)", varName, jsonName, parserName);
            } else {
                writer.write("$L = !(aws.json.bridge.coreJsonParseNestedEffectful \"$L\" $L json)", varName, jsonName, parserName);
            }
        } else if (targetShape.isUnionShape()) {
            // Union - use effectful bridge to call existing *FromJson deserializer
            String deserializer = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    targetShape.getId().getName() + "FromJson");
            if (isNonOptional) {
                writer.write("$L = !(aws.json.bridge.coreJsonRequireNestedEffectful \"$L\" $L json)", varName, jsonName, deserializer);
            } else {
                writer.write("$L = !(aws.json.bridge.coreJsonParseNestedEffectful \"$L\" $L json)", varName, jsonName, deserializer);
            }
        } else if (targetShape.isDocumentShape()) {
            // Document - keep as Json
            if (isNonOptional) {
                String tempVar = varName + "Opt";
                writer.write("$L = aws.json.bridge.coreJsonFindObject \"$L\" json", tempVar, jsonName);
                writer.write("$L = Optional.getOrElse (core.Json.Object []) $L", varName, tempVar);
            } else {
                writer.write("$L = aws.json.bridge.coreJsonFindObject \"$L\" json", varName, jsonName);
            }
        } else {
            // Fallback - treat as text
            if (isNonOptional) {
                String tempVar = varName + "Opt";
                writer.write("$L = aws.json.bridge.coreJsonFindText \"$L\" json", tempVar, jsonName);
                writer.write("$L = Optional.getOrElse \"\" $L", varName, tempVar);
            } else {
                writer.write("$L = aws.json.bridge.coreJsonFindText \"$L\" json", varName, jsonName);
            }
        }
    }
    
    /**
     * Gets the JSON deserializer for a member.
     * 
     * <p>Just returns the base deserializer - the caller will choose
     * whether to use extractField (required) or extractOptionalField (optional).
     * 
     * @param member The member shape
     * @param model The Smithy model
     * @param clientNamespace The client namespace
     * @return The deserializer function
     */
    private String getJsonDeserializer(MemberShape member, Model model, String clientNamespace) {
        Shape targetShape = model.expectShape(member.getTarget());
        return getDeserializerForType(targetShape, model, clientNamespace);
    }
    
    /**
     * Gets the deserializer function for a type.
     * 
     * <p>Returns the appropriate parser function for the given shape type.
     * These parsers work with the Exception-based extraction functions.
     * 
     * @param shape The target shape
     * @param model The Smithy model
     * @param clientNamespace The client namespace
     * @return The deserializer function name
     */
    private String getDeserializerForType(Shape shape, Model model, String clientNamespace) {
        if (shape.isEnumShape() || (shape.isStringShape() && shape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            // Enum - use generated FromJson deserializer
            return clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    shape.getId().getName() + "FromJson");
        } else if (shape.isStringShape()) {
            // String - use coreJsonParseString for core.Json input
            return "aws.json.bridge.coreJsonParseString";
        } else if (shape.isIntegerShape() || shape.isLongShape() || shape.isShortShape() || shape.isByteShape()) {
            // Integer types - use coreJsonParseInt for core.Json input
            return "aws.json.bridge.coreJsonParseInt";
        } else if (shape.isFloatShape() || shape.isDoubleShape()) {
            // Float types - use coreJsonParseFloat for core.Json input
            return "aws.json.bridge.coreJsonParseFloat";
        } else if (shape.isBooleanShape()) {
            // Boolean - use coreJsonParseBoolean for core.Json input
            return "aws.json.bridge.coreJsonParseBoolean";
        } else if (shape.isBlobShape()) {
            // Blob - parse as base64-encoded string and decode to Bytes
            return "aws.json.bridge.coreJsonParseBlob";
        } else if (shape.isTimestampShape()) {
            // Timestamp - parse as string (ISO 8601 in REST-JSON)
            return "aws.json.bridge.coreJsonParseString";
        } else if (shape.isListShape()) {
            // List - use generated list deserializer function
            ListShape listShape = shape.asListShape().get();
            Shape elementShape = model.expectShape(listShape.getMember().getTarget());
            String elementTypeName = getShapeTypeName(elementShape, model);
            return clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    "listOf" + elementTypeName + "FromJson");
        } else if (shape.isMapShape()) {
            // Map - use generated map deserializer function
            MapShape mapShape = shape.asMapShape().get();
            Shape keyShape = model.expectShape(mapShape.getKey().getTarget());
            Shape valueShape = model.expectShape(mapShape.getValue().getTarget());
            String keyTypeName = getShapeTypeName(keyShape, model);
            String valueTypeName = getShapeTypeName(valueShape, model);
            return clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    "mapOf" + keyTypeName + "To" + valueTypeName + "FromJson");
        } else if (shape.isStructureShape()) {
            // Structure - use generated FromJson deserializer
            return clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    shape.getId().getName() + "FromJson");
        } else if (shape.isUnionShape()) {
            // Union - use generated FromJson deserializer (TODO: implement union deserializers)
            return clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    shape.getId().getName() + "FromJson");
        } else if (shape.isDocumentShape()) {
            // Document - convert core.Json to JsonValue
            return "(j -> 'aws.json.coreJsonToJsonValue j)";
        } else {
            // Fallback - parse as string
            return "aws.json.bridge.coreJsonParseString";
        }
    }
    
    @Override
    public void generateErrorParser(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        ServiceShape service = context.serviceShape();
        String clientNamespace = context.settings().getClientNamespace();
        String serviceName = service.getId().getName();
        
        // Remove "Service" suffix if present to avoid "DynamoDBServiceServiceError"
        if (serviceName.endsWith("Service")) {
            serviceName = serviceName.substring(0, serviceName.length() - 7);
        }
        String errorTypeName = UnisonSymbolProvider.toNamespacedTypeName(
                serviceName + "ServiceError", clientNamespace);
        
        // Generate error constructor helper first
        writer.writeDocComment("Create a service error from error code and message");
        writer.writeSignature(clientNamespace + ".errorFromCodeAndMessage", "Text -> Text -> " + errorTypeName);
        writer.write("$L.errorFromCodeAndMessage code message =", clientNamespace);
        writer.indent();
        writer.write("$L.UnknownError code message", errorTypeName);
        writer.dedent();
        writer.writeBlankLine();
        
        writer.writeDocComment("Parse REST-JSON error response\n\n" +
                "REST-JSON has multiple error formats:\n" +
                "- Format 1: {\"__type\": \"ErrorName\", \"message\": \"...\"}\n" +
                "- Format 2: {\"Type\": \"Sender\", \"message\": \"...\"}\n" +
                "- Format 3: {\"code\": \"ErrorName\", \"message\": \"...\"}\n\n" +
                "This parser checks all possible error code locations.");
        writer.writeSignature(clientNamespace + ".parseError", "Http.Response -> " + errorTypeName);
        writer.write("$L.parseError response =", clientNamespace);
        writer.indent();
        
        // Parse error body using core.Json
        writer.write("errorBody = aws.http.bytesToText (Http.Response.body response)");
        writer.write("json = match core.Json.tryFromText errorBody with");
        writer.indent();
        writer.write("Right j -> j");
        writer.write("Left _ -> core.Json.Object []");
        writer.dedent();
        writer.write("");
        
        // Extract error code from multiple possible locations using core.Json helpers
        writer.write("-- Extract error code (try multiple locations)");
        writer.write("-- Format 1: __type field");
        writer.write("errorType1 = aws.json.bridge.coreJsonFindText \"__type\" json");
        writer.write("-- Format 2: Type field");
        writer.write("errorType2 = aws.json.bridge.coreJsonFindText \"Type\" json");
        writer.write("-- Format 3: code/Code field");
        writer.write("errorType3 = aws.json.bridge.coreJsonFindText \"code\" json");
        writer.write("errorType4 = aws.json.bridge.coreJsonFindText \"Code\" json");
        writer.write("");
        writer.write("-- Use first non-None error type");
        writer.write("errorType = match errorType1 with");
        writer.write("  Some t -> t");
        writer.write("  None -> match errorType2 with");
        writer.write("    Some t -> t");
        writer.write("    None -> match errorType3 with");
        writer.write("      Some t -> t");
        writer.write("      None -> Optional.getOrElse \"UnknownError\" errorType4");
        writer.write("");
        
        // Extract error message using core.Json helpers
        writer.write("-- Extract error message (try both cases)");
        writer.write("errorMsg1 = aws.json.bridge.coreJsonFindText \"message\" json");
        writer.write("errorMsg2 = aws.json.bridge.coreJsonFindText \"Message\" json");
        writer.write("errorMessage = match errorMsg1 with");
        writer.write("  Some m -> m");
        writer.write("  None -> Optional.getOrElse \"\" errorMsg2");
        writer.write("");
        
        // Map to service error
        writer.write("-- Map error type to service error");
        writer.write("$L.errorFromCodeAndMessage errorType errorMessage", clientNamespace);
        
        writer.dedent();
        writer.writeBlankLine();
    }
}
