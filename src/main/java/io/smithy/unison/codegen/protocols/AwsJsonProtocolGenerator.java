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
        String bodyVar;
        if (inputShape.isPresent() && !inputShape.get().getAllMembers().isEmpty()) {
            String serializerName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName() + "RequestBody");
            writer.write("bodyJson = $L input", serializerName);
            writer.write("bodyText = Aws.Json.Bridge.jsonToRequestBody bodyJson");
            bodyVar = "bodyText";
        } else {
            writer.write("bodyText = \"{}\"");
            bodyVar = "bodyText";
        }
        writer.write("bodyBytes = Text.toUtf8 $L", bodyVar);
        
        // Sign request with SigV4
        writer.write("");
        writer.write("-- Sign request with AWS Signature Version 4");
        writer.write("region = $L.region config", configType);
        writer.write("creds = $L.credentials config", configType);
        writer.write("-- Convert to Aws.Credentials for signing");
        String credsType = UnisonSymbolProvider.toNamespacedTypeName("Credentials", clientNamespace);
        writer.write("awsCreds = Aws.Credentials.Credentials ($L.accessKeyId creds) ($L.secretAccessKey creds) ($L.sessionToken creds)", 
            credsType, credsType, credsType);
        // Extract service name for signing (lowercase, without version suffix)
        String signingServiceName = extractSigningServiceName(serviceName);
        writer.write("signingConfig = Aws.SigningConfig.SigningConfig region \"$L\" awsCreds", signingServiceName);
        writer.write("signedHeaders = !(Aws.SigV4.signRequest signingConfig method uri \"\" headers bodyBytes)");
        
        // Make HTTP request
        writer.write("");
        writer.write("-- Make HTTP request");
        writer.write("request = Http.Request.post url signedHeaders bodyBytes");
        writer.write("response = !(executeRequest request)");
        
        // Handle response - check status and parse
        writer.write("");
        writer.write("-- Handle response based on status code");
        writer.write("statusCode = Http.Response.statusCode response");
        writer.write("if Nat.lt statusCode 300 then");
        writer.indent();
        
        // Success - parse response
        if (operation.getOutput().isPresent()) {
            String parserName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName() + "ResponseParser");
            writer.write("$L response", parserName);
        } else {
            writer.write("()");
        }
        
        writer.dedent();
        writer.write("else");
        writer.indent();
        
        // Error - parse error and raise exception
        writer.write("-- Parse error response");
        writer.write("serviceError = parseError response");
        // Remove "Service" suffix from service name to avoid "DynamoDBServiceServiceError"
        String errorServiceName = serviceName.endsWith("Service") 
                ? serviceName.substring(0, serviceName.length() - 7)
                : serviceName;
        String errorTypeName = UnisonSymbolProvider.toNamespacedTypeName(
                errorServiceName + "ServiceError", clientNamespace);
        writer.write("failure = $L.toFailure serviceError", errorTypeName);
        writer.write("Exception.raise failure");
        
        writer.dedent();
        
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
        writer.write("let");
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
        // Define predicate as a local function to avoid parsing issues with inline cases
        writer.write("isNotNull = cases");
        writer.indent();
        writer.write("(_, Aws.Json.JsonValue.JsonNull) -> false");
        writer.write("_ -> true");
        writer.dedent();
        writer.write("filteredFields = List.filter isNotNull fields");
        
        // Create JSON object
        writer.write("Aws.Json.jsonObject filteredFields");
        
        writer.dedent();
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
     * Extracts the service name for SigV4 signing from the full service name.
     * 
     * <p>AWS service names in models often include version suffixes (e.g., DynamoDB_20120810),
     * but SigV4 signing uses the lowercase base service name (e.g., "dynamodb").
     * 
     * @param serviceName The full service name (e.g., "DynamoDB_20120810")
     * @return The signing service name (e.g., "dynamodb")
     */
    private String extractSigningServiceName(String serviceName) {
        // Remove version suffix (e.g., "_20120810")
        String baseName = serviceName.replaceAll("_\\d+$", "");
        // Convert to lowercase for signing
        return baseName.toLowerCase();
    }
    
    /**
     * Checks if a union shape is the DynamoDB AttributeValue type.
     * 
     * <p>DynamoDB's AttributeValue is a special union that should use the runtime
     * type Aws.Json.AttributeValue with its special serialization/deserialization.
     * 
     * @param union The union shape to check
     * @return true if this is DynamoDB's AttributeValue union
     */
    private boolean isDynamoDBAttributeValue(UnionShape union) {
        String shapeId = union.getId().toString();
        // Check if this is the DynamoDB AttributeValue union
        // Pattern: com.amazonaws.dynamodb#AttributeValue
        return shapeId.contains("dynamodb") && shapeId.endsWith("#AttributeValue");
    }
    
    /**
     * Generates Unison code to convert a member value to JsonValue.
     */
    private String generateJsonValueForMember(MemberShape member, Model model, String clientNamespace, String inputVar) {
        String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
        String inputType = UnisonSymbolProvider.toNamespacedTypeName(
                model.expectShape(member.getContainer()).asStructureShape().get().getId().getName(),
                clientNamespace);
        String accessor = "(" + inputType + "." + memberName + " " + inputVar + ")";
        
        Shape target = model.expectShape(member.getTarget());
        
        if (member.isRequired()) {
            return generateJsonValue(target, accessor, model, clientNamespace);
        } else {
            // Optional field - map to JsonValue, defaulting to JsonNull
            String conversion = generateJsonValue(target, "x", model, clientNamespace);
            return String.format("Optional.map (x -> %s) %s |> Optional.getOrElse Aws.Json.JsonValue.JsonNull",
                    conversion, accessor);
        }
    }
    
    /**
     * Generates Unison expression to convert a shape value to JsonValue.
     */
    private String generateJsonValue(Shape shape, String varName, Model model, String clientNamespace) {
        // Check enum FIRST (before string check, since enums may be string-based)
        if (shape.isEnumShape() || (shape.isStringShape() && shape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            // Enum - convert to text
            String toTextFn = UnisonSymbolProvider.toNamespacedFunctionName(shape.getId().getName() + "ToText", clientNamespace);
            return "Aws.Json.JsonValue.JsonString (" + toTextFn + " " + varName + ")";
        } else if (shape.isStringShape()) {
            return "Aws.Json.JsonValue.JsonString " + varName;
        } else if (shape.isBooleanShape()) {
            return "Aws.Json.JsonValue.JsonBoolean " + varName;
        } else if (shape.isIntegerShape() || shape.isLongShape() || shape.isShortShape() || shape.isByteShape()) {
            return "Aws.Json.JsonValue.JsonNumber (Float.fromInt " + varName + ")";
        } else if (shape.isFloatShape() || shape.isDoubleShape()) {
            return "Aws.Json.JsonValue.JsonNumber " + varName;
        } else if (shape.isBlobShape()) {
            // Base64 encode bytes
            return "Aws.Json.JsonValue.JsonString (Bytes.toBase64 " + varName + ")";
        } else if (shape.isTimestampShape()) {
            // Timestamp as ISO-8601 string (AWS JSON default)
            return "Aws.Json.JsonValue.JsonString (Instant.toText " + varName + ")";
        } else if (shape.isListShape()) {
            ListShape listShape = shape.asListShape().get();
            Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            String elemConversion = generateJsonValue(memberTarget, "elem", model, clientNamespace);
            return String.format("Aws.Json.JsonValue.JsonArray (List.map (elem -> %s) %s)", elemConversion, varName);
        } else if (shape.isMapShape()) {
            MapShape mapShape = shape.asMapShape().get();
            Shape valueTarget = model.expectShape(mapShape.getValue().getTarget());
            String valueConversion = generateJsonValue(valueTarget, "v", model, clientNamespace);
            return String.format("Aws.Json.jsonObject (List.map (cases (k, v) -> (k, %s)) (Map.toList %s))",
                    valueConversion, varName);
        } else if (shape.isStructureShape()) {
            // Nested structure - need recursive serialization
            String serializerName = UnisonSymbolProvider.toUnisonFunctionName(shape.getId().getName() + "ToJson");
            return serializerName + " " + varName;
        } else if (shape.isUnionShape()) {
            // Union - check if it's DynamoDB AttributeValue
            UnionShape unionShape = shape.asUnionShape().get();
            if (isDynamoDBAttributeValue(unionShape)) {
                // Use runtime AttributeValue converter
                return "Aws.Json.attributeValueToJson " + varName;
            }
            // Generic union - need serializer
            String serializerName = UnisonSymbolProvider.toUnisonFunctionName(shape.getId().getName() + "ToJson");
            return serializerName + " " + varName;
        } else if (shape.isDocumentShape()) {
            // Document type - pass through as-is (already JsonValue)
            return varName;
        } else {
            // Fallback: convert to string
            return "Aws.Json.JsonValue.JsonString (Any.toText " + varName + ")";
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
        writer.write("$L response = do", functionName);
        writer.indent();
        writer.write("use Aws.Json JsonNull JsonString JsonNumber JsonBoolean JsonObject JsonArray");
        
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
        
        writer.dedent(); // Close function
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
        // Check enum FIRST (before string check, since enums may be string-based)
        if (target.isEnumShape() || (target.isStringShape() && target.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            // Enum - parse from text
            String fromTextFn = UnisonSymbolProvider.toNamespacedFunctionName(target.getId().getName() + "FromText", clientNamespace);
            return String.format("Aws.Json.getFieldAsString \"%s\" %s |> Optional.flatMap %s",
                    fieldName, jsonVar, fromTextFn);
        } else if (target.isStringShape()) {
            return String.format("Aws.Json.getFieldAsString \"%s\" %s",
                    fieldName, jsonVar);
        } else if (target.isBooleanShape()) {
            return String.format("Aws.Json.getFieldAsBoolean \"%s\" %s",
                    fieldName, jsonVar);
        } else if (target.isIntegerShape() || target.isLongShape() || target.isShortShape() || target.isByteShape()) {
            return String.format("Aws.Json.getFieldAsNumber \"%s\" %s |> Optional.map Float.truncate",
                    fieldName, jsonVar);
        } else if (target.isFloatShape() || target.isDoubleShape()) {
            return String.format("Aws.Json.getFieldAsNumber \"%s\" %s",
                    fieldName, jsonVar);
        } else if (target.isBlobShape()) {
            // Base64 decode
            return String.format("Aws.Json.getFieldAsString \"%s\" %s |> Optional.flatMap Bytes.fromBase64",
                    fieldName, jsonVar);
        } else if (target.isTimestampShape()) {
            // Timestamp from ISO-8601 string
            return String.format("Aws.Json.getFieldAsString \"%s\" %s |> Optional.flatMap Instant.fromText",
                    fieldName, jsonVar);
        } else if (target.isListShape()) {
            ListShape listShape = target.asListShape().get();
            Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            String elemConversion = generateJsonValueConversion(memberTarget, "elem", model, clientNamespace);
            return String.format("Aws.Json.getFieldAsArray \"%s\" %s |> Optional.map (arr -> List.filterMap (elem -> %s) arr)",
                    fieldName, jsonVar, elemConversion);
        } else if (target.isMapShape()) {
            MapShape mapShape = target.asMapShape().get();
            Shape valueTarget = model.expectShape(mapShape.getValue().getTarget());
            String valueConversion = generateJsonValueConversion(valueTarget, "v", model, clientNamespace);
            return String.format("Aws.Json.getFieldAsObjectList \"%s\" %s |> Optional.map (fields -> Map.fromList (List.filterMap (kv -> match kv with (k, v) -> Optional.map (val -> (k, val)) (%s)) fields))",
                    fieldName, jsonVar, valueConversion);
        } else if (target.isStructureShape()) {
            // Nested structure - need recursive parser
            String parserName = UnisonSymbolProvider.toUnisonFunctionName(target.getId().getName() + "FromJson");
            return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap %s",
                    fieldName, jsonVar, parserName);
        } else if (target.isUnionShape()) {
            // Union - check if it's DynamoDB AttributeValue
            UnionShape unionShape = target.asUnionShape().get();
            if (isDynamoDBAttributeValue(unionShape)) {
                // Use runtime AttributeValue converter
                return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap Aws.Json.jsonToAttributeValue",
                        fieldName, jsonVar);
            }
            // Generic union - need parser
            String parserName = UnisonSymbolProvider.toUnisonFunctionName(target.getId().getName() + "FromJson");
            return String.format("Aws.Json.getField \"%s\" %s |> Optional.flatMap %s",
                    fieldName, jsonVar, parserName);
        } else if (target.isDocumentShape()) {
            // Document type - pass through as JsonValue
            return String.format("Aws.Json.getField \"%s\" %s", fieldName, jsonVar);
        } else {
            // Fallback: try to parse as string
            return String.format("Aws.Json.getFieldAsString \"%s\" %s",
                    fieldName, jsonVar);
        }
    }
    
    /**
     * Generates Unison expression to convert a JsonValue to a target type.
     * Used for list elements and map values. Returns Optional value.
     */
    private String generateJsonValueConversion(Shape target, String varName, Model model, String clientNamespace) {
        // Check enum FIRST (before string check, since enums may be string-based)
        if (target.isEnumShape() || (target.isStringShape() && target.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            String fromTextFn = UnisonSymbolProvider.toNamespacedFunctionName(target.getId().getName() + "FromText", clientNamespace);
            return String.format("Aws.Json.jsonValueToString %s |> Optional.flatMap %s", varName, fromTextFn);
        } else if (target.isStringShape()) {
            return String.format("Aws.Json.jsonValueToString %s", varName);
        } else if (target.isBooleanShape()) {
            return String.format("Aws.Json.jsonValueToBoolean %s", varName);
        } else if (target.isIntegerShape() || target.isLongShape() || target.isShortShape() || target.isByteShape()) {
            return String.format("Aws.Json.jsonValueToInt %s", varName);
        } else if (target.isFloatShape() || target.isDoubleShape()) {
            return String.format("Aws.Json.jsonValueToNumber %s", varName);
        } else if (target.isBlobShape()) {
            return String.format("Aws.Json.jsonValueToString %s |> Optional.flatMap Bytes.fromBase64", varName);
        } else if (target.isTimestampShape()) {
            return String.format("Aws.Json.jsonValueToString %s |> Optional.flatMap Instant.fromText", varName);
        } else if (target.isListShape()) {
            // List - convert array elements recursively
            ListShape listShape = target.asListShape().get();
            Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            String elemConversion = generateJsonValueConversion(memberTarget, "elem", model, clientNamespace);
            return String.format("Aws.Json.jsonValueToArray %s |> Optional.map (arr -> List.filterMap (elem -> %s) arr)", varName, elemConversion);
        } else if (target.isMapShape()) {
            // Map - convert object fields recursively
            MapShape mapShape = target.asMapShape().get();
            Shape valueTarget = model.expectShape(mapShape.getValue().getTarget());
            String valueConversion = generateJsonValueConversion(valueTarget, "v", model, clientNamespace);
            return String.format("Aws.Json.jsonValueToObjectList %s |> Optional.map (fields -> Map.fromList (List.filterMap (kv -> match kv with (k, v) -> Optional.map (val -> (k, val)) (%s)) fields))", varName, valueConversion);
        } else if (target.isStructureShape()) {
            String parserName = UnisonSymbolProvider.toUnisonFunctionName(target.getId().getName() + "FromJson");
            return String.format("%s %s", parserName, varName);
        } else if (target.isUnionShape()) {
            // Union - check if it's DynamoDB AttributeValue
            UnionShape unionShape = target.asUnionShape().get();
            if (isDynamoDBAttributeValue(unionShape)) {
                // Use runtime AttributeValue converter
                return String.format("Aws.Json.jsonToAttributeValue %s", varName);
            }
            // Generic union - need parser
            String parserName = UnisonSymbolProvider.toUnisonFunctionName(target.getId().getName() + "FromJson");
            return String.format("%s %s", parserName, varName);
        } else if (target.isDocumentShape()) {
            // Document type - pass through as JsonValue
            return String.format("Some %s", varName);
        } else {
            // Fallback
            return String.format("Aws.Json.jsonValueToString %s", varName);
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
        
        writer.writeDocComment("Parse AWS JSON error response\n\n" +
                "Extracts `__type` and `message` fields from JSON error response.\n" +
                "Handles both full format (com.amazon.coral#ErrorName) and short format (ErrorName).");
        writer.writeSignature("parseError", "Http.Response -> " + errorTypeName);
        writer.write("parseError response =");
        writer.indent();
        
        // Parse error body
        writer.write("errorBody = Aws.Http.bytesToText (Response.body response)");
        writer.write("json = match catch do !(Aws.Json.parseJson errorBody) with");
        writer.indent();
        writer.write("Right j -> j");
        writer.write("Left _ -> Aws.Json.jsonObject []");
        writer.dedent();
        writer.write("");
        
        // Extract error type and message using runtime helpers
        writer.write("-- Extract error type (handles both full and short formats)");
        writer.write("errorType = Aws.Json.Bridge.extractErrorType json");
        writer.write("errorMessage = Aws.Json.Bridge.extractErrorMessage json");
        writer.write("");
        
        // Convert to service error type using fromCodeAndMessage
        writer.write("-- Map to service error type");
        writer.write("code = Optional.getOrElse \"UnknownError\" errorType");
        writer.write("message = Optional.getOrElse \"\" errorMessage");
        writer.write("$L.fromCodeAndMessage code message", errorTypeName);
        
        writer.dedent();
        writer.writeBlankLine();
    }
}
