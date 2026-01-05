package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpTrait;

import java.util.HashSet;
import java.util.List;
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
                
                // Extract value from input
                writer.write("$LValue = $L.$L input", memberName, inputType, memberName);
                
                // URL encode and substitute
                writer.write("$L = Text.replaceAll \"$L\" (aws.http.urlEncode $LValue) $L",
                        nextUri, placeholder, memberName, currentUri);
                
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
                String toTextFunc = getToTextFunction(targetShape, clientNamespace);
                
                // Check if member is required
                boolean isRequired = member.isRequired();
                
                if (isRequired) {
                    // Required field: convert value directly and wrap in Some
                    if (toTextFunc.isEmpty()) {
                        writer.write("Some (\"$L=\" ++ aws.http.urlEncode ($L.$L input))$L",
                                queryName, inputType, memberName, isLast ? "" : ",");
                    } else {
                        writer.write("Some (\"$L=\" ++ aws.http.urlEncode ($L ($L.$L input)))$L",
                                queryName, toTextFunc, inputType, memberName, isLast ? "" : ",");
                    }
                } else {
                    // Optional field: map over the Optional
                    if (toTextFunc.isEmpty()) {
                        writer.write("Optional.map (v -> \"$L=\" ++ aws.http.urlEncode v) ($L.$L input)$L",
                                queryName, inputType, memberName, isLast ? "" : ",");
                    } else {
                        writer.write("Optional.map (v -> \"$L=\" ++ aws.http.urlEncode ($L v)) ($L.$L input)$L",
                                queryName, toTextFunc, inputType, memberName, isLast ? "" : ",");
                    }
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
        
        if (targetShape.isBlobShape()) {
            // Blob payload - use as-is
            writer.write("bodyBytes = $L.$L input", inputType, memberName);
        } else if (targetShape.isStringShape()) {
            // String payload - convert to UTF8
            writer.write("bodyText = $L.$L input", inputType, memberName);
            writer.write("bodyBytes = Text.toUtf8 bodyText");
        } else {
            // Structure payload - serialize as JSON
            String clientNamespace = context.settings().getClientNamespace();
            String serializerName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(
                    targetShape.getId().getName() + "ToJson");
            writer.write("bodyJson = $L ($L.$L input)", serializerName, inputType, memberName);
            writer.write("bodyText = aws.json.bridge.jsonToRequestBody bodyJson");
            writer.write("bodyBytes = Text.toUtf8 bodyText");
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
            // @httpPayload present - may need structure serializer
            software.amazon.smithy.model.shapes.Shape targetShape = model.expectShape(payloadMember.get().getTarget());
            if (targetShape.isStructureShape()) {
                generateStructureSerializer((StructureShape) targetShape, writer, context);
            }
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
            return "aws.json.JsonValue.JsonString (Bytes.toBase64 " + varName + ")";
        } else if (shape.isTimestampShape()) {
            return "aws.json.JsonValue.JsonString " + varName;
        } else if (shape.isListShape()) {
            software.amazon.smithy.model.shapes.ListShape listShape = shape.asListShape().get();
            software.amazon.smithy.model.shapes.Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            String elemConversion = generateJsonValue(memberTarget, "elem", model, clientNamespace);
            return String.format("aws.json.JsonValue.JsonArray (List.map (elem -> %s) %s)", elemConversion, varName);
        } else if (shape.isMapShape()) {
            software.amazon.smithy.model.shapes.MapShape mapShape = shape.asMapShape().get();
            software.amazon.smithy.model.shapes.Shape valueTarget = model.expectShape(mapShape.getValue().getTarget());
            String valueConversion = generateJsonValue(valueTarget, "v", model, clientNamespace);
            return String.format("aws.json.jsonObject (List.map (cases (k, v) -> (k, %s)) (Map.toList %s))",
                    valueConversion, varName);
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
    private void generateStructureSerializer(StructureShape structure, UnisonWriter writer, UnisonContext context) {
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
    
    @Override
    public void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement response deserialization (Step 1.7)
    }
    
    @Override
    public void generateErrorParser(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement error parsing (Step 1.8)
    }
}
