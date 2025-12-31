package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.JsonNameTrait;

import java.util.ArrayList;
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
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        Optional<StructureShape> outputShape = ProtocolUtils.getOutputShape(operation, model);
        if (outputShape.isEmpty()) {
            return; // No response body to parse
        }
        
        StructureShape output = outputShape.get();
        String outputType = UnisonSymbolProvider.toNamespacedTypeName(output.getId().getName(), clientNamespace);
        String functionName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName() + "ResponseParser");
        
        writer.writeComment("Parse " + output.getId().getName() + " from AWS JSON response");
        writer.writeSignature(functionName, "Http.Response -> '{Exception} " + outputType);
        writer.write("$L response =", functionName);
        writer.indent();
        
        // Parse JSON from response body
        writer.write("-- Parse JSON response body");
        writer.write("bodyText = Aws.Http.bytesToText (Response.body response)");
        writer.write("json = !(Aws.Json.parseJson bodyText)");
        writer.write("");
        
        // Extract each field from JSON
        List<MemberShape> members = output.getAllMembers().values().stream().toList();
        if (!members.isEmpty()) {
            writer.write("-- Extract fields from JSON");
            for (MemberShape member : members) {
                String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
                String varName = memberName + "Val";
                String jsonName = getJsonName(member);
                
                generateFieldExtraction(member, model, clientNamespace, jsonName, varName, writer);
            }
            writer.write("");
        }
        
        // Construct output using base type name (Unison constructor resolution)
        String baseTypeName = UnisonSymbolProvider.toUnisonTypeName(output.getId().getName());
        writer.write("-- Construct output structure");
        
        // Build positional arguments in member order
        List<String> args = new ArrayList<>();
        for (MemberShape member : members) {
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            args.add(memberName + "Val");
        }
        
        if (args.isEmpty()) {
            writer.write("$L", baseTypeName);
        } else {
            writer.write("$L $L", baseTypeName, String.join(" ", args));
        }
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates code to extract a field from JSON and convert to Unison type.
     */
    private void generateFieldExtraction(MemberShape member, Model model, String clientNamespace,
                                          String jsonName, String varName, UnisonWriter writer) {
        Shape target = model.expectShape(member.getTarget());
        
        if (member.isRequired()) {
            // Required field - extract and convert, raising exception if missing
            String extraction = generateJsonExtraction(target, "json", jsonName, model, clientNamespace);
            writer.write("$L = match $L with", varName, extraction);
            writer.indent();
            writer.write("Some value -> value");
            writer.write("None -> Exception.raise (Failure (typeLink Generic) \"Missing required field: $L\" (Any ()))", jsonName);
            writer.dedent();
        } else {
            // Optional field - return Optional value
            String extraction = generateJsonExtraction(target, "json", jsonName, model, clientNamespace);
            writer.write("$L = $L", varName, extraction);
        }
    }
    
    /**
     * Generates Unison expression to extract and convert a JSON field to a target type.
     * Returns Optional value for all types.
     */
    private String generateJsonExtraction(Shape target, String jsonVar, String fieldName,
                                           Model model, String clientNamespace) {
        if (target.isStringShape()) {
            return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap (cases Aws.Json.JsonString s -> Some s; _ -> None)",
                    fieldName, jsonVar);
        } else if (target.isBooleanShape()) {
            return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap (cases Aws.Json.JsonBoolean b -> Some b; _ -> None)",
                    fieldName, jsonVar);
        } else if (target.isIntegerShape() || target.isLongShape() || target.isShortShape() || target.isByteShape()) {
            return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap (cases Aws.Json.JsonNumber n -> Some (Float.truncate n); _ -> None)",
                    fieldName, jsonVar);
        } else if (target.isFloatShape() || target.isDoubleShape()) {
            return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap (cases Aws.Json.JsonNumber n -> Some n; _ -> None)",
                    fieldName, jsonVar);
        } else if (target.isBlobShape()) {
            // Base64 decode
            return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap (cases Aws.Json.JsonString s -> Bytes.fromBase64 s; _ -> None)",
                    fieldName, jsonVar);
        } else if (target.isListShape()) {
            ListShape listShape = target.asListShape().get();
            Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            String elemConversion = generateJsonValueConversion(memberTarget, "elem", model, clientNamespace);
            return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap (cases Aws.Json.JsonArray arr -> Some (List.filterMap (elem -> %s) arr); _ -> None)",
                    fieldName, jsonVar, elemConversion);
        } else if (target.isMapShape()) {
            MapShape mapShape = target.asMapShape().get();
            Shape valueTarget = model.expectShape(mapShape.getValue().getTarget());
            String valueConversion = generateJsonValueConversion(valueTarget, "v", model, clientNamespace);
            return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap (cases Aws.Json.JsonObject obj -> Some (Map.fromList (List.filterMap (cases (k, v) -> Optional.map (val -> (k, val)) (%s)) obj)); _ -> None)",
                    fieldName, jsonVar, valueConversion);
        } else if (target.isStructureShape()) {
            // Nested structure - need recursive parser
            String parserName = UnisonSymbolProvider.toUnisonFunctionName(target.getId().getName() + "FromJson");
            return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap %s",
                    fieldName, jsonVar, parserName);
        } else if (target.isEnumShape()) {
            // Enum - parse from text
            String fromTextFn = UnisonSymbolProvider.toNamespacedFunctionName(target.getId().getName() + "FromText", clientNamespace);
            return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap (cases Aws.Json.JsonString s -> %s s; _ -> None)",
                    fieldName, jsonVar, fromTextFn);
        } else if (target.isTimestampShape()) {
            // Timestamp from ISO-8601 string
            return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap (cases Aws.Json.JsonString s -> Instant.fromText s; _ -> None)",
                    fieldName, jsonVar);
        } else {
            // Fallback: try to parse as string
            return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap (cases Aws.Json.JsonString s -> Some s; _ -> None)",
                    fieldName, jsonVar);
        }
    }
    
    /**
     * Generates Unison expression to convert a JsonValue to a target type.
     * Used for list elements and map values. Returns Optional value.
     */
    private String generateJsonValueConversion(Shape target, String varName, Model model, String clientNamespace) {
        if (target.isStringShape()) {
            return String.format("(cases Aws.Json.JsonString s -> Some s; _ -> None) %s", varName);
        } else if (target.isBooleanShape()) {
            return String.format("(cases Aws.Json.JsonBoolean b -> Some b; _ -> None) %s", varName);
        } else if (target.isIntegerShape() || target.isLongShape() || target.isShortShape() || target.isByteShape()) {
            return String.format("(cases Aws.Json.JsonNumber n -> Some (Float.truncate n); _ -> None) %s", varName);
        } else if (target.isFloatShape() || target.isDoubleShape()) {
            return String.format("(cases Aws.Json.JsonNumber n -> Some n; _ -> None) %s", varName);
        } else if (target.isBlobShape()) {
            return String.format("(cases Aws.Json.JsonString s -> Bytes.fromBase64 s; _ -> None) %s", varName);
        } else if (target.isStructureShape()) {
            String parserName = UnisonSymbolProvider.toUnisonFunctionName(target.getId().getName() + "FromJson");
            return String.format("%s %s", parserName, varName);
        } else if (target.isEnumShape()) {
            String fromTextFn = UnisonSymbolProvider.toNamespacedFunctionName(target.getId().getName() + "FromText", clientNamespace);
            return String.format("(cases Aws.Json.JsonString s -> %s s; _ -> None) %s", fromTextFn, varName);
        } else if (target.isTimestampShape()) {
            return String.format("(cases Aws.Json.JsonString s -> Instant.fromText s; _ -> None) %s", varName);
        } else {
            // Fallback
            return String.format("(cases Aws.Json.JsonString s -> Some s; _ -> None) %s", varName);
        }
    }
    
    @Override
    public void generateErrorParser(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS JSON error parsing
        writer.writeComment("AWS JSON error parsing (NOT IMPLEMENTED)");
    }
}
