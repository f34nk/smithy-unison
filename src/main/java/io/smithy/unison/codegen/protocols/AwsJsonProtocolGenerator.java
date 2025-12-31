package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.JsonNameTrait;

import java.util.List;
import java.util.Optional;

/**
 * Protocol generator for AWS JSON 1.0/1.1 protocols.
 * 
 * <p>Used by DynamoDB, Lambda, Kinesis, and other JSON-based services.
 * 
 * <h2>Protocol Characteristics</h2>
 * <ul>
 *   <li>HTTP Method: POST (always)</li>
 *   <li>URI: "/" (always)</li>
 *   <li>Content-Type: application/x-amz-json-1.0 or 1.1</li>
 *   <li>X-Amz-Target header: {ServiceName}.{OperationName}</li>
 *   <li>Request Body: JSON encoded</li>
 *   <li>Response Body: JSON decoded</li>
 *   <li>Authentication: AWS SigV4</li>
 * </ul>
 * 
 * <h2>AWS JSON Error Format</h2>
 * <p>Errors are returned with a __type field:
 * <pre>
 * {
 *   "__type": "com.amazon.dynamodb.v20120810#ResourceNotFoundException",
 *   "message": "Requested resource not found"
 * }
 * </pre>
 */
public class AwsJsonProtocolGenerator implements ProtocolGenerator {
    
    public static final ShapeId AWS_JSON_1_0 = ShapeId.from("aws.protocols#awsJson1_0");
    public static final ShapeId AWS_JSON_1_1 = ShapeId.from("aws.protocols#awsJson1_1");
    
    private final ShapeId protocol;
    private final String version;
    
    public AwsJsonProtocolGenerator() {
        this(AWS_JSON_1_1, "1.1");
    }
    
    public AwsJsonProtocolGenerator(ShapeId protocol, String version) {
        this.protocol = protocol;
        this.version = version;
    }
    
    @Override
    public ShapeId getProtocol() {
        return protocol;
    }
    
    @Override
    public String getName() {
        return "awsJson" + version.replace(".", "_");
    }
    
    @Override
    public String getContentType(ServiceShape service) {
        return "application/x-amz-json-" + version;
    }
    
    @Override
    public void generateOperation(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        ServiceShape service = context.serviceShape();
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
        
        // AWS JSON protocols always use POST to /
        String method = "POST";
        String uri = "/";
        
        // Get target header value: {ServiceName}.{OperationName}
        String serviceName = service.getId().getName();
        String operationName = operation.getId().getName();
        String target = serviceName + "." + operationName;
        
        // Write documentation
        writer.writeDocComment(operation.getId().getName() + " operation\n\n" +
                "AWS JSON " + version + " protocol\n" +
                "HTTP POST /\n" +
                "X-Amz-Target: " + target + "\n" +
                "Raises exception on error, returns output directly on success.");
        
        // Write signature
        String signature = String.format("%s -> %s -> '{IO, Exception, Threads} %s", configType, inputType, outputType);
        writer.writeSignature(opName, signature);
        
        // Write function definition with do block
        writer.write("$L config input = do", opName);
        writer.indent();
        
        // HTTP method and URI
        writer.write("method = \"$L\"", method);
        writer.write("uri = \"$L\"", uri);
        writer.write("url = ($L.endpoint config) ++ uri", configType);
        
        // Build headers with X-Amz-Target
        writer.write("");
        writer.write("-- AWS JSON protocol headers");
        writer.write("headers = [");
        writer.indent();
        writer.write("(\"Content-Type\", \"$L\"),", getContentType(service));
        writer.write("(\"X-Amz-Target\", \"$L\")", target);
        writer.dedent();
        writer.write("]");
        
        // Serialize request body
        writer.write("");
        writer.write("-- Serialize request to JSON");
        Optional<StructureShape> inputShape = ProtocolUtils.getInputShape(operation, model);
        if (inputShape.isPresent() && !inputShape.get().getAllMembers().isEmpty()) {
            String serializerName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName() + "RequestBody");
            writer.write("bodyJson = $L input", serializerName);
            writer.write("body = Aws.Json.Bridge.jsonToRequestBody bodyJson");
        } else {
            writer.write("body = \"{}\"");
        }
        
        // Sign request (placeholder)
        writer.write("signedHeaders = headers");
        
        // Make HTTP request
        writer.write("");
        writer.write("-- Make HTTP request");
        writer.write("response = !(executeRequest (Http.Request.$L url signedHeaders body))", method.toLowerCase());
        
        // Handle response
        writer.write("");
        writer.write("-- Check for errors and parse response");
        writer.write("_ = handleHttpResponse response");
        
        // Parse response
        if (operation.getOutput().isPresent()) {
            String parserName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName() + "ResponseParser");
            writer.write("$L response", parserName);
        } else {
            writer.write("()");
        }
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    @Override
    public void generateRequestSerializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        Optional<StructureShape> inputShape = ProtocolUtils.getInputShape(operation, model);
        if (inputShape.isEmpty() || inputShape.get().getAllMembers().isEmpty()) {
            return; // No request body needed
        }
        
        StructureShape input = inputShape.get();
        String inputType = UnisonSymbolProvider.toNamespacedTypeName(input.getId().getName(), clientNamespace);
        String functionName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName() + "RequestBody");
        
        writer.writeComment("Serialize " + input.getId().getName() + " to JSON for AWS JSON protocol");
        writer.writeSignature(functionName, inputType + " -> Aws.Json.JsonValue");
        writer.write("$L input =", functionName);
        writer.indent();
        
        // Generate field list
        writer.write("fields = [");
        writer.indent();
        
        List<MemberShape> members = input.getAllMembers().values().stream().toList();
        for (int i = 0; i < members.size(); i++) {
            MemberShape member = members.get(i);
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            String jsonName = getJsonName(member);
            boolean isLast = (i == members.size() - 1);
            
            // Generate serialization for this field
            writer.write("(\"$L\", $L)$L",
                    jsonName,
                    generateJsonValueForMember(member, model, clientNamespace, "input"),
                    isLast ? "" : ",");
        }
        
        writer.dedent();
        writer.write("]");
        
        // Filter out null values for optional fields
        writer.write("|> List.filter (cases (_, Aws.Json.JsonNull) -> false; _ -> true)");
        
        // Create JSON object
        writer.write("Aws.Json.jsonObject fields");
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Gets the JSON field name for a member, respecting @jsonName trait.
     */
    private String getJsonName(MemberShape member) {
        return member.getTrait(JsonNameTrait.class)
                .map(JsonNameTrait::getValue)
                .orElse(member.getMemberName());
    }
    
    /**
     * Generates Unison code to convert a member value to JsonValue.
     */
    private String generateJsonValueForMember(MemberShape member, Model model, String clientNamespace, String inputVar) {
        String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
        String inputType = UnisonSymbolProvider.toNamespacedTypeName(
                model.expectShape(member.getContainer()).asStructureShape().get().getId().getName(),
                clientNamespace);
        String accessor = inputType + "." + memberName + " " + inputVar;
        
        Shape target = model.expectShape(member.getTarget());
        
        if (member.isRequired()) {
            return generateJsonValue(target, accessor, model, clientNamespace);
        } else {
            // Optional field - map to JsonValue, defaulting to JsonNull
            String conversion = generateJsonValue(target, "x", model, clientNamespace);
            return String.format("Optional.map (x -> %s) (%s) |> Optional.getOrElse Aws.Json.JsonNull",
                    conversion, accessor);
        }
    }
    
    /**
     * Generates Unison expression to convert a shape value to JsonValue.
     */
    private String generateJsonValue(Shape shape, String varName, Model model, String clientNamespace) {
        if (shape.isStringShape()) {
            return "Aws.Json.JsonString " + varName;
        } else if (shape.isBooleanShape()) {
            return "Aws.Json.JsonBoolean " + varName;
        } else if (shape.isIntegerShape() || shape.isLongShape() || shape.isShortShape() || shape.isByteShape()) {
            return "Aws.Json.JsonNumber (Float.fromInt " + varName + ")";
        } else if (shape.isFloatShape() || shape.isDoubleShape()) {
            return "Aws.Json.JsonNumber " + varName;
        } else if (shape.isBlobShape()) {
            // Base64 encode bytes
            return "Aws.Json.JsonString (Bytes.toBase64 " + varName + ")";
        } else if (shape.isListShape()) {
            ListShape listShape = shape.asListShape().get();
            Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            String elemConversion = generateJsonValue(memberTarget, "elem", model, clientNamespace);
            return String.format("Aws.Json.JsonArray (List.map (elem -> %s) %s)", elemConversion, varName);
        } else if (shape.isMapShape()) {
            MapShape mapShape = shape.asMapShape().get();
            Shape valueTarget = model.expectShape(mapShape.getValue().getTarget());
            String valueConversion = generateJsonValue(valueTarget, "v", model, clientNamespace);
            return String.format("Aws.Json.jsonObject (List.map (cases (k, v) -> (k, %s)) (Map.toList %s))",
                    valueConversion, varName);
        } else if (shape.isStructureShape()) {
            // Nested structure - need recursive serialization
            String structType = UnisonSymbolProvider.toNamespacedTypeName(shape.getId().getName(), clientNamespace);
            String serializerName = UnisonSymbolProvider.toUnisonFunctionName(shape.getId().getName() + "ToJson");
            return serializerName + " " + varName;
        } else if (shape.isEnumShape()) {
            // Enum - convert to text
            String enumType = UnisonSymbolProvider.toNamespacedTypeName(shape.getId().getName(), clientNamespace);
            String toTextFn = UnisonSymbolProvider.toNamespacedFunctionName(shape.getId().getName() + "ToText", clientNamespace);
            return "Aws.Json.JsonString (" + toTextFn + " " + varName + ")";
        } else if (shape.isTimestampShape()) {
            // Timestamp as ISO-8601 string (AWS JSON default)
            return "Aws.Json.JsonString (Instant.toText " + varName + ")";
        } else {
            // Fallback: convert to string
            return "Aws.Json.JsonString (Any.toText " + varName + ")";
        }
    }
    
    @Override
    public void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS JSON response deserialization
    }
    
    @Override
    public void generateErrorParser(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS JSON error parsing
        writer.writeComment("AWS JSON error parsing (NOT IMPLEMENTED)");
    }
}
