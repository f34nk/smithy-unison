package io.smithy.unison.codegen.protocols;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;

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
            String serializerName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName() + "RequestBody");
            writer.write("bodyJson = $L input", serializerName);
            writer.write("bodyText = aws.json.bridge.jsonToRequestBody bodyJson");
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
        writer.write("-- Convert to aws.sigv4.Credentials for signing");
        String credsType = UnisonSymbolProvider.toNamespacedTypeName("Credentials", clientNamespace);
        writer.write("awsCreds = aws.sigv4.Credentials.Credentials ($L.accessKeyId creds) ($L.secretAccessKey creds) ($L.sessionToken creds)", 
            credsType, credsType, credsType);
        // Extract service name for signing (lowercase, without version suffix)
        String signingServiceName = extractSigningServiceName(serviceName);
        writer.write("signingConfig = aws.sigv4.SigningConfig.SigningConfig region \"$L\" awsCreds", signingServiceName);
        writer.write("allHeaders = !(aws.sigv4.addSigningHeaders signingConfig method uri \"\" headers bodyBytes)");
        
        // Make HTTP request
        writer.write("");
        writer.write("-- Make HTTP request");
        writer.write("request = Http.Request.post url allHeaders bodyBytes");
        writer.write("response = !(executeRequest request)");
        
        // Handle response - check status and parse
        writer.write("");
        writer.write("-- Handle response based on status code");
        writer.write("statusCode = Http.Response.statusCode response");
        writer.write("if Nat.lt statusCode 300 then");
        writer.indent();
        
        // Success - parse response (force the delayed computation with !)
        if (operation.getOutput().isPresent()) {
            String parserName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName() + "ResponseParser");
            writer.write("!($L response)", parserName);
        } else {
            writer.write("()");
        }
        
        writer.dedent();
        writer.write("else");
        writer.indent();
        
        // Error - parse error and raise exception
        writer.write("-- Parse error response");
        writer.write("serviceError = $L.parseError response", clientNamespace);
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
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName() + "RequestBody");
        
        generateStructureSerializerWithName(input, functionName, inputType, model, clientNamespace, writer);
    }
    
    /**
     * Generates a JSON serializer for a structure with the naming pattern {StructureName}ToJson.
     * Used for nested structures that are referenced by operation inputs.
     */
    public void generateStructureSerializer(StructureShape structure, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        if (structure.getAllMembers().isEmpty()) {
            return; // No fields to serialize
        }
        
        String structType = UnisonSymbolProvider.toNamespacedTypeName(structure.getId().getName(), clientNamespace);
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(structure.getId().getName() + "ToJson");
        
        generateStructureSerializerWithName(structure, functionName, structType, model, clientNamespace, writer);
    }
    
    /**
     * Generates a JSON deserializer for a structure with the naming pattern {StructureName}FromJson.
     * Used for nested structures that are referenced by operation outputs.
     */
    public void generateStructureDeserializer(StructureShape structure, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        String structType = UnisonSymbolProvider.toNamespacedTypeName(structure.getId().getName(), clientNamespace);
        String baseTypeName = UnisonSymbolProvider.toUnisonTypeName(structure.getId().getName());
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(structure.getId().getName() + "FromJson");
        
        writer.writeComment("Parse " + structure.getId().getName() + " from JSON");
        writer.writeSignature(functionName, "aws.json.JsonValue -> Optional " + structType);
        writer.write("$L json =", functionName);
        writer.indent();
        writer.write("use aws.json JsonNull JsonString JsonNumber JsonBoolean JsonObject JsonArray");
        
        // Extract each field from JSON - all extractions return Optional
        List<MemberShape> members = structure.getAllMembers().values().stream().toList();
        
        // Collect required and optional field info
        List<String> requiredVars = new ArrayList<>();
        List<String> optionalVars = new ArrayList<>();
        
        if (!members.isEmpty()) {
            for (MemberShape member : members) {
                String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
                String varName = io.smithy.unison.codegen.symbol.UnisonReservedWords.appendSuffix(memberName, "Opt");
                String jsonName = getJsonName(member);
                
                Shape target = model.expectShape(member.getTarget());
                String extraction = generateJsonExtraction(target, "json", jsonName, model, clientNamespace);
                
                // All extractions are Optional - we unwrap required fields at the end
                writer.write("$L = $L", varName, extraction);
                
                boolean isNonOptional = member.isRequired() || member.hasTrait(DefaultTrait.class);
                if (isNonOptional) {
                    requiredVars.add(varName);
                } else {
                    optionalVars.add(varName);
                }
            }
        }
        
        // Construct output - use Optional.flatMap chain for required fields
        if (requiredVars.isEmpty()) {
            // No required fields - just construct with optional values
            List<String> args = new ArrayList<>();
            for (MemberShape member : members) {
                String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
                args.add(io.smithy.unison.codegen.symbol.UnisonReservedWords.appendSuffix(memberName, "Opt"));
            }
            
            if (args.isEmpty()) {
                writer.write("Some $L", baseTypeName);
            } else {
                writer.write("Some ($L $L)", baseTypeName, String.join(" ", args));
            }
        } else {
            // Has required fields - use match to unwrap them all at once
            // Build constructor arguments, using unwrapped names for required, Opt suffix for optional
            List<String> constructorArgs = new ArrayList<>();
            List<String> matchPatterns = new ArrayList<>();
            List<String> unwrappedNames = new ArrayList<>();
            
            for (MemberShape member : members) {
                String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
                boolean isNonOptional = member.isRequired() || member.hasTrait(DefaultTrait.class);
                
                if (isNonOptional) {
                    String unwrappedName = io.smithy.unison.codegen.symbol.UnisonReservedWords.appendSuffix(memberName, "Unwrapped");
                    String optName = io.smithy.unison.codegen.symbol.UnisonReservedWords.appendSuffix(memberName, "Opt");
                    matchPatterns.add("Some " + unwrappedName);
                    unwrappedNames.add(optName);
                    constructorArgs.add(unwrappedName);
                } else {
                    constructorArgs.add(io.smithy.unison.codegen.symbol.UnisonReservedWords.appendSuffix(memberName, "Opt"));
                }
            }
            
            // Generate nested Optional.flatMap for required fields
            // Pattern: reqField1Opt |> Optional.flatMap (f1 -> reqField2Opt |> Optional.flatMap (f2 -> Some (Struct f1 f2 optFields...)))
            StringBuilder nested = new StringBuilder();
            int depth = 0;
            
            for (int i = 0; i < requiredVars.size(); i++) {
                String varName = requiredVars.get(i);
                String unwrappedName = io.smithy.unison.codegen.symbol.UnisonReservedWords.escape(
                        varName.replace("Opt", ""));
                
                if (i == 0) {
                    nested.append(varName).append(" |> Optional.flatMap (").append(unwrappedName).append(" ->");
                } else {
                    nested.append(" ").append(varName).append(" |> Optional.flatMap (").append(unwrappedName).append(" ->");
                }
                depth++;
            }
            
            // Build the constructor call with proper argument names
            List<String> finalArgs = new ArrayList<>();
            for (MemberShape member : members) {
                String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
                boolean isNonOptional = member.isRequired() || member.hasTrait(DefaultTrait.class);
                
                if (isNonOptional) {
                    finalArgs.add(memberName);  // already escaped by toUnisonFunctionName
                } else {
                    finalArgs.add(io.smithy.unison.codegen.symbol.UnisonReservedWords.appendSuffix(memberName, "Opt"));  // keep Optional
                }
            }
            
            nested.append(" Some ($L $L)");
            for (int i = 0; i < depth; i++) {
                nested.append(")");
            }
            
            writer.write(nested.toString(), baseTypeName, String.join(" ", finalArgs));
        }
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Core implementation for generating a structure serializer with a given function name.
     */
    private void generateStructureSerializerWithName(StructureShape structure, String functionName, 
                                                      String inputType, Model model, String clientNamespace,
                                                      UnisonWriter writer) {
        writer.writeComment("Serialize " + structure.getId().getName() + " to JSON");
        writer.writeSignature(functionName, inputType + " -> aws.json.JsonValue");
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
     * type aws.json.AttributeValue with its special serialization/deserialization.
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
        
        // Check if field is non-optional (required or has default) - matches StructureGenerator logic
        boolean isNonOptional = member.isRequired() || member.hasTrait(DefaultTrait.class);
        
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
     */
    private String generateJsonValue(Shape shape, String varName, Model model, String clientNamespace) {
        // Check enum FIRST (before string check, since enums may be string-based)
        if (shape.isEnumShape() || (shape.isStringShape() && shape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            // Enum - convert to text
            String toTextFn = UnisonSymbolProvider.toNamespacedFunctionName(shape.getId().getName() + "ToText", clientNamespace);
            return "aws.json.JsonValue.JsonString (" + toTextFn + " " + varName + ")";
        } else if (shape.isStringShape()) {
            return "aws.json.JsonValue.JsonString " + varName;
        } else if (shape.isBooleanShape()) {
            return "aws.json.JsonValue.JsonBoolean " + varName;
        } else if (shape.isIntegerShape() || shape.isLongShape() || shape.isShortShape() || shape.isByteShape()) {
            return "aws.json.JsonValue.JsonNumber (Float.fromInt " + varName + ")";
        } else if (shape.isFloatShape() || shape.isDoubleShape()) {
            return "aws.json.JsonValue.JsonNumber " + varName;
        } else if (shape.isBlobShape()) {
            // Base64 encode bytes - toBase64 returns Bytes, need to convert to Text
            return "aws.json.JsonValue.JsonString (Text.fromUtf8 (Bytes.toBase64 " + varName + "))";
        } else if (shape.isTimestampShape()) {
            // Timestamp is generated as Text in structures, just wrap in JSON string
            return "aws.json.JsonValue.JsonString " + varName;
        } else if (shape.isListShape()) {
            ListShape listShape = shape.asListShape().get();
            Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            String elemConversion = generateJsonValue(memberTarget, "elem", model, clientNamespace);
            return String.format("aws.json.JsonValue.JsonArray (List.map (elem -> %s) %s)", elemConversion, varName);
        } else if (shape.isMapShape()) {
            MapShape mapShape = shape.asMapShape().get();
            Shape keyTarget = model.expectShape(mapShape.getKey().getTarget());
            Shape valueTarget = model.expectShape(mapShape.getValue().getTarget());
            
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
            String serializerName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(shape.getId().getName() + "ToJson");
            return serializerName + " " + varName;
        } else if (shape.isUnionShape()) {
            // Union - check if it's DynamoDB AttributeValue
            UnionShape unionShape = shape.asUnionShape().get();
            if (isDynamoDBAttributeValue(unionShape)) {
                // Use runtime AttributeValue converter
                return "aws.json.attributeValueToJson " + varName;
            }
            // Generic union - need serializer
            String serializerName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(shape.getId().getName() + "ToJson");
            return serializerName + " " + varName;
        } else if (shape.isDocumentShape()) {
            // Document type - pass through as-is (already JsonValue)
            return varName;
        } else {
            // Fallback: convert to string
            return "aws.json.JsonValue.JsonString (Any.toText " + varName + ")";
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
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName() + "ResponseParser");
        
        writer.writeComment("Parse " + output.getId().getName() + " from AWS JSON response");
        writer.writeSignature(functionName, "Http.Response -> '{Exception} " + outputType);
        writer.write("$L response = do", functionName);
        writer.indent();
        writer.write("use aws.json JsonNull JsonString JsonNumber JsonBoolean JsonObject JsonArray");
        
        // Parse JSON from response body
        writer.write("-- Parse JSON response body");
        writer.write("bodyText = aws.http.bytesToText (Response.body response)");
        writer.write("json = !(aws.json.parseJson bodyText)");
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
        
        // Check if field is non-optional (required or has default) - matches StructureGenerator logic
        boolean isNonOptional = member.isRequired() || member.hasTrait(DefaultTrait.class);
        
        if (isNonOptional) {
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
            return String.format("aws.json.getFieldAsString \"%s\" %s |> Optional.flatMap %s",
                    fieldName, jsonVar, fromTextFn);
        } else if (target.isStringShape()) {
            return String.format("aws.json.getFieldAsString \"%s\" %s",
                    fieldName, jsonVar);
        } else if (target.isBooleanShape()) {
            return String.format("aws.json.getFieldAsBoolean \"%s\" %s",
                    fieldName, jsonVar);
        } else if (target.isIntegerShape() || target.isLongShape() || target.isShortShape() || target.isByteShape()) {
            return String.format("aws.json.getFieldAsNumber \"%s\" %s |> Optional.map Float.truncate",
                    fieldName, jsonVar);
        } else if (target.isFloatShape() || target.isDoubleShape()) {
            return String.format("aws.json.getFieldAsNumber \"%s\" %s",
                    fieldName, jsonVar);
        } else if (target.isBlobShape()) {
            // Base64 decode: Text -> Bytes (via toUtf8) -> Either Text Bytes -> Optional Bytes
            return String.format("aws.json.getFieldAsString \"%s\" %s |> Optional.flatMap (t -> aws.json.eitherToOptional (builtin.Bytes.fromBase64 (toUtf8 t)))",
                    fieldName, jsonVar);
        } else if (target.isTimestampShape()) {
            // Timestamp is generated as Text in structures, just extract string
            return String.format("aws.json.getFieldAsString \"%s\" %s",
                    fieldName, jsonVar);
        } else if (target.isListShape()) {
            ListShape listShape = target.asListShape().get();
            Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            String elemConversion = generateJsonValueConversion(memberTarget, "elem", model, clientNamespace);
            return String.format("aws.json.getFieldAsArray \"%s\" %s |> Optional.map (arr -> aws.json.filterMap (elem -> %s) arr)",
                    fieldName, jsonVar, elemConversion);
        } else if (target.isMapShape()) {
            MapShape mapShape = target.asMapShape().get();
            Shape keyTarget = model.expectShape(mapShape.getKey().getTarget());
            Shape valueTarget = model.expectShape(mapShape.getValue().getTarget());
            
            // Check if key is an enum that needs conversion from Text
            String keyConversion;
            if (keyTarget.isEnumShape() || (keyTarget.isStringShape() && keyTarget.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
                String keyFromTextFn = UnisonSymbolProvider.toNamespacedFunctionName(keyTarget.getId().getName() + "FromText", clientNamespace);
                keyConversion = "Optional.flatMap " + keyFromTextFn + " (Some k)";
            } else {
                keyConversion = "Some k";  // Key is already Text
            }
            
            String valueConversion = generateJsonValueConversion(valueTarget, "v", model, clientNamespace);
            // For enum keys, use Optional.flatMap to convert key text to enum
            String mapConversion;
            if (keyTarget.isEnumShape() || (keyTarget.isStringShape() && keyTarget.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
                String keyFromTextFn = UnisonSymbolProvider.toNamespacedFunctionName(keyTarget.getId().getName() + "FromText", clientNamespace);
                mapConversion = String.format("aws.json.filterMap (kv -> match kv with (k, v) -> Optional.flatMap (key -> Optional.map (val -> (key, val)) (%s)) (%s k)) fields", 
                    valueConversion, keyFromTextFn);
            } else {
                mapConversion = String.format("aws.json.filterMap (kv -> match kv with (k, v) -> Optional.map (val -> (k, val)) (%s)) fields", valueConversion);
            }
            return String.format("aws.json.getFieldAsObjectList \"%s\" %s |> Optional.map (fields -> lib.unison_base_3_18_0.data.Map.fromList (%s))",
                    fieldName, jsonVar, mapConversion);
        } else if (target.isStructureShape()) {
            // Nested structure - need recursive parser
            String parserName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(target.getId().getName() + "FromJson");
            return String.format("aws.json.getField \"%s\" %s |> Optional.flatMap %s",
                    fieldName, jsonVar, parserName);
        } else if (target.isUnionShape()) {
            // Union - check if it's DynamoDB AttributeValue
            UnionShape unionShape = target.asUnionShape().get();
            if (isDynamoDBAttributeValue(unionShape)) {
                // Use runtime AttributeValue converter
                return String.format("aws.json.getField \"%s\" %s |> Optional.flatMap aws.json.jsonToAttributeValue",
                        fieldName, jsonVar);
            }
            // Generic union - need parser
            String parserName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(target.getId().getName() + "FromJson");
            return String.format("aws.json.getField \"%s\" %s |> Optional.flatMap %s",
                    fieldName, jsonVar, parserName);
        } else if (target.isDocumentShape()) {
            // Document type - pass through as JsonValue
            return String.format("aws.json.getField \"%s\" %s", fieldName, jsonVar);
        } else {
            // Fallback: try to parse as string
            return String.format("aws.json.getFieldAsString \"%s\" %s",
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
            return String.format("aws.json.jsonValueToString %s |> Optional.flatMap %s", varName, fromTextFn);
        } else if (target.isStringShape()) {
            return String.format("aws.json.jsonValueToString %s", varName);
        } else if (target.isBooleanShape()) {
            return String.format("aws.json.jsonValueToBoolean %s", varName);
        } else if (target.isIntegerShape() || target.isLongShape() || target.isShortShape() || target.isByteShape()) {
            return String.format("aws.json.jsonValueToInt %s", varName);
        } else if (target.isFloatShape() || target.isDoubleShape()) {
            return String.format("aws.json.jsonValueToNumber %s", varName);
        } else if (target.isBlobShape()) {
            // Base64 decode: Text -> Bytes (via toUtf8) -> Either Text Bytes -> Optional Bytes
            return String.format("aws.json.jsonValueToString %s |> Optional.flatMap (t -> aws.json.eitherToOptional (builtin.Bytes.fromBase64 (toUtf8 t)))", varName);
        } else if (target.isTimestampShape()) {
            // Timestamp is generated as Text in structures, just extract string
            return String.format("aws.json.jsonValueToString %s", varName);
        } else if (target.isListShape()) {
            // List - convert array elements recursively
            ListShape listShape = target.asListShape().get();
            Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            String elemConversion = generateJsonValueConversion(memberTarget, "elem", model, clientNamespace);
            return String.format("aws.json.jsonValueToArray %s |> Optional.map (arr -> aws.json.filterMap (elem -> %s) arr)", varName, elemConversion);
        } else if (target.isMapShape()) {
            // Map - convert object fields recursively
            MapShape mapShape = target.asMapShape().get();
            Shape keyTarget = model.expectShape(mapShape.getKey().getTarget());
            Shape valueTarget = model.expectShape(mapShape.getValue().getTarget());
            
            // Check if key is an enum that needs conversion from Text
            String keyConversion;
            if (keyTarget.isEnumShape() || (keyTarget.isStringShape() && keyTarget.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
                String keyFromTextFn = UnisonSymbolProvider.toNamespacedFunctionName(keyTarget.getId().getName() + "FromText", clientNamespace);
                keyConversion = "Optional.flatMap " + keyFromTextFn + " (Some k)";
            } else {
                keyConversion = "Some k";  // Key is already Text
            }
            
            String valueConversion = generateJsonValueConversion(valueTarget, "v", model, clientNamespace);
            // For enum keys, use Optional.flatMap to convert key text to enum
            String mapConversion;
            if (keyTarget.isEnumShape() || (keyTarget.isStringShape() && keyTarget.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
                String keyFromTextFn = UnisonSymbolProvider.toNamespacedFunctionName(keyTarget.getId().getName() + "FromText", clientNamespace);
                mapConversion = String.format("aws.json.filterMap (kv -> match kv with (k, v) -> Optional.flatMap (key -> Optional.map (val -> (key, val)) (%s)) (%s k)) fields", 
                    valueConversion, keyFromTextFn);
            } else {
                mapConversion = String.format("aws.json.filterMap (kv -> match kv with (k, v) -> Optional.map (val -> (k, val)) (%s)) fields", valueConversion);
            }
            return String.format("aws.json.jsonValueToObjectList %s |> Optional.map (fields -> lib.unison_base_3_18_0.data.Map.fromList (%s))", varName, mapConversion);
        } else if (target.isStructureShape()) {
            String parserName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(target.getId().getName() + "FromJson");
            return String.format("%s %s", parserName, varName);
        } else if (target.isUnionShape()) {
            // Union - check if it's DynamoDB AttributeValue
            UnionShape unionShape = target.asUnionShape().get();
            if (isDynamoDBAttributeValue(unionShape)) {
                // Use runtime AttributeValue converter
                return String.format("aws.json.jsonToAttributeValue %s", varName);
            }
            // Generic union - need parser
            String parserName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(target.getId().getName() + "FromJson");
            return String.format("%s %s", parserName, varName);
        } else if (target.isDocumentShape()) {
            // Document type - pass through as JsonValue
            return String.format("Some %s", varName);
        } else {
            // Fallback
            return String.format("aws.json.jsonValueToString %s", varName);
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
        writer.writeSignature(clientNamespace + ".parseError", "Http.Response -> " + errorTypeName);
        writer.write("$L.parseError response =", clientNamespace);
        writer.indent();
        
        // Parse error body
        writer.write("errorBody = aws.http.bytesToText (Response.body response)");
        writer.write("json = match catch do !(aws.json.parseJson errorBody) with");
        writer.indent();
        writer.write("Right j -> j");
        writer.write("Left _ -> aws.json.jsonObject []");
        writer.dedent();
        writer.write("");
        
        // Extract error type and message using runtime helpers
        writer.write("-- Extract error type (handles both full and short formats)");
        writer.write("errorType = aws.json.bridge.extractErrorType json");
        writer.write("errorMessage = aws.json.bridge.extractErrorMessage json");
        writer.write("");
        
        // Convert to service error type using fromCodeAndMessage
        writer.write("-- Map to service error type");
        writer.write("code = Optional.getOrElse \"UnknownError\" errorType");
        writer.write("message = Optional.getOrElse \"\" errorMessage");
        writer.write("$L.fromCodeAndMessage code message", errorTypeName);
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates a JSON serializer for a union type with the naming pattern {UnionName}ToJson.
     * Used for union types that are referenced in structures.
     */
    public void generateUnionSerializer(UnionShape union, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        String unionType = UnisonSymbolProvider.toNamespacedTypeName(union.getId().getName(), clientNamespace);
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(union.getId().getName() + "ToJson");
        
        writer.writeComment("Serialize " + union.getId().getName() + " to JSON");
        writer.writeSignature(functionName, unionType + " -> aws.json.JsonValue");
        writer.write("$L union =", functionName);
        writer.indent();
        
        // Add use statements for union constructors
        writer.write("use " + clientNamespace + " " + 
            String.join(" ", union.getAllMembers().keySet().stream()
                .map(memberName -> union.getId().getName() + "'" + memberName)
                .toList()));
        
        writer.write("match union with");
        writer.indent();
        
        // Generate pattern match case for each union member
        for (MemberShape member : union.getAllMembers().values()) {
            String variantName = member.getMemberName();
            String constructorName = union.getId().getName() + "'" + variantName;
            Shape memberTarget = model.expectShape(member.getTarget());
            
            // Get the JSON serialization for the member's value
            String serializedValue = generateJsonValueForShape(memberTarget, "value", model, clientNamespace);
            
            writer.write("$L value -> $L", constructorName, serializedValue);
        }
        
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates a JSON deserializer for a union type with the naming pattern {UnionName}FromJson.
     * Used for union types that are referenced in structures.
     */
    public void generateUnionDeserializer(UnionShape union, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        String unionType = UnisonSymbolProvider.toNamespacedTypeName(union.getId().getName(), clientNamespace);
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(union.getId().getName() + "FromJson");
        
        writer.writeComment("Parse " + union.getId().getName() + " from JSON");
        writer.writeSignature(functionName, "aws.json.JsonValue -> Optional " + unionType);
        writer.write("$L json =", functionName);
        writer.indent();
        
        // Add use statement for union constructors
        writer.write("use " + clientNamespace + " " + 
            String.join(" ", union.getAllMembers().keySet().stream()
                .map(memberName -> union.getId().getName() + "'" + memberName)
                .toList()));
        
        // Try to parse each variant
        // We try the first variant that successfully parses
        List<MemberShape> members = union.getAllMembers().values().stream().toList();
        
        if (members.isEmpty()) {
            writer.write("None");
        } else if (members.size() == 1) {
            // Single variant - just try to parse it
            MemberShape member = members.get(0);
            String variantName = member.getMemberName();
            String constructorName = union.getId().getName() + "'" + variantName;
            Shape memberTarget = model.expectShape(member.getTarget());
            
            String parseExpr = generateJsonValueConversion(memberTarget, "json", model, clientNamespace);
            writer.write("$L |> Optional.map $L", parseExpr, constructorName);
        } else {
            // Multiple variants - try each in sequence
            for (int i = 0; i < members.size(); i++) {
                MemberShape member = members.get(i);
                String variantName = member.getMemberName();
                String constructorName = union.getId().getName() + "'" + variantName;
                Shape memberTarget = model.expectShape(member.getTarget());
                
                String parseExpr = generateJsonValueConversion(memberTarget, "json", model, clientNamespace);
                
                if (i == 0) {
                    writer.write("match $L with", parseExpr);
                    writer.indent();
                    writer.write("Some val -> Some ($L val)", constructorName);
                    writer.write("None ->");
                    writer.indent();
                } else if (i < members.size() - 1) {
                    writer.write("match $L with", parseExpr);
                    writer.indent();
                    writer.write("Some val -> Some ($L val)", constructorName);
                    writer.write("None ->");
                    writer.indent();
                } else {
                    // Last one
                    writer.write("$L |> Optional.map $L", parseExpr, constructorName);
                }
            }
            
            // Close all the match blocks
            for (int i = 0; i < members.size() - 1; i++) {
                writer.dedent();
                writer.dedent();
            }
        }
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Helper to generate JSON value expression for a shape (used in union serialization).
     */
    private String generateJsonValueForShape(Shape shape, String varName, Model model, String clientNamespace) {
        if (shape.isStringShape()) {
            return "aws.json.JsonValue.JsonString " + varName;
        } else if (shape.isBooleanShape()) {
            return "aws.json.JsonValue.JsonBoolean " + varName;
        } else if (shape.isIntegerShape() || shape.isLongShape() || shape.isShortShape() || shape.isByteShape()) {
            return "aws.json.JsonValue.JsonNumber (Float.fromInt " + varName + ")";
        } else if (shape.isFloatShape() || shape.isDoubleShape()) {
            return "aws.json.JsonValue.JsonNumber " + varName;
        } else if (shape.isBlobShape()) {
            return "aws.json.JsonValue.JsonString (builtin.Bytes.toBase64 " + varName + " |> fromUtf8)";
        } else if (shape.isTimestampShape()) {
            return "aws.json.JsonValue.JsonString " + varName;
        } else if (shape.isStructureShape()) {
            String serializerName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(shape.getId().getName() + "ToJson");
            return serializerName + " " + varName;
        } else if (shape.isUnionShape()) {
            String serializerName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(shape.getId().getName() + "ToJson");
            return serializerName + " " + varName;
        } else {
            return "aws.json.JsonValue.JsonString (Any.toText " + varName + ")";
        }
    }
}
