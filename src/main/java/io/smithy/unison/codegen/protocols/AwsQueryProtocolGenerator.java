package io.smithy.unison.codegen.protocols;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonReservedWords;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.EnumTrait;
import software.amazon.smithy.model.traits.XmlFlattenedTrait;
import software.amazon.smithy.model.traits.XmlNameTrait;

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
    
    /**
     * Extracts the service name for SigV4 signing from the full service name.
     * 
     * <p>AWS service names in models often include version suffixes (e.g., SQS_20121105),
     * but SigV4 signing uses the lowercase base service name (e.g., "sqs").
     * 
     * @param serviceName The full service name (e.g., "SQS_20121105")
     * @return The signing service name (e.g., "sqs")
     */
    private String extractSigningServiceName(String serviceName) {
        // Remove version suffix (e.g., "_20121105")
        String baseName = serviceName.replaceAll("_\\d+$", "");
        // Convert to lowercase for signing
        return baseName.toLowerCase();
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
        
        // Serialize request to form parameters
        Model model = context.model();
        Optional<StructureShape> inputShape = ProtocolUtils.getInputShape(operation, model);
        
        writer.write("");
        writer.write("-- Serialize request to form-encoded parameters");
        if (inputShape.isPresent() && !inputShape.get().getAllMembers().isEmpty()) {
            String serializerName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName() + "RequestParams");
            writer.write("params = $L input", serializerName);
        } else {
            writer.write("params = []");
        }
        
        // Add Action and Version parameters
        writer.write("");
        writer.write("-- Add Action and Version parameters");
        writer.write("allParams = (List.++) params [(\"Action\", \"$L\"), (\"Version\", \"$L\")]", operationName, serviceVersion);
        
        // Form-encode parameters
        writer.write("");
        writer.write("-- Form-encode parameters for request body");
        writer.write("bodyText = aws.query.buildFormEncodedBody allParams");
        writer.write("bodyBytes = Text.toUtf8 bodyText");
        
        // Build headers
        writer.write("");
        writer.write("-- Build request headers");
        writer.write("headers = [(\"Content-Type\", \"$L\")]", getContentType(service));
        
        // Sign request with SigV4
        writer.write("");
        writer.write("-- Sign request with AWS Signature Version 4");
        writer.write("region = $L.region config", configType);
        writer.write("creds = $L.credentials config", configType);
        String credsType = UnisonSymbolProvider.toNamespacedTypeName("Credentials", clientNamespace);
        writer.write("awsCreds = aws.sigv4.Credentials.Credentials ($L.accessKeyId creds) ($L.secretAccessKey creds) ($L.sessionToken creds)", 
                credsType, credsType, credsType);
        
        // Extract signing service name (lowercase, without version suffix)
        String signingServiceName = extractSigningServiceName(service.getId().getName());
        writer.write("signingConfig = aws.sigv4.SigningConfig.SigningConfig region \"$L\" awsCreds", signingServiceName);
        writer.write("allHeaders = !(aws.sigv4.addSigningHeaders signingConfig method uri \"\" headers bodyBytes)");
        
        // Execute HTTP POST
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
        
        // Success - parse response
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
        String errorServiceName = service.getId().getName();
        if (errorServiceName.endsWith("Service")) {
            errorServiceName = errorServiceName.substring(0, errorServiceName.length() - 7);
        }
        String errorTypeName = UnisonSymbolProvider.toNamespacedTypeName(
                errorServiceName + "ServiceError", clientNamespace);
        writer.write("failure = $L.toFailure serviceError", errorTypeName);
        writer.write("Exception.raise failure");
        
        writer.dedent();
        
        writer.dedent();
    }
    
    // ========== Request Serialization ==========
    
    @Override
    public void generateRequestSerializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        Optional<StructureShape> inputShape = ProtocolUtils.getInputShape(operation, model);
        if (inputShape.isEmpty() || inputShape.get().getAllMembers().isEmpty()) {
            return; // No request parameters needed
        }
        
        StructureShape input = inputShape.get();
        String inputType = UnisonSymbolProvider.toNamespacedTypeName(input.getId().getName(), clientNamespace);
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName() + "RequestParams");
        
        writer.writeComment("Serialize " + operation.getId().getName() + " request to Query parameters");
        writer.writeSignature(functionName, inputType + " -> [(Text, Text)]");
        writer.write("$L input =", functionName);
        writer.indent();
        
        // Generate parameter serialization for each member
        List<String> paramLists = new ArrayList<>();
        for (MemberShape member : input.getAllMembers().values()) {
            String paramName = getQueryParamName(member);
            String varName = "params_" + UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            
            Shape target = model.expectShape(member.getTarget());
            generateMemberSerialization(member, target, paramName, varName, model, clientNamespace, writer);
            
            paramLists.add(varName);
        }
        
        // Concatenate all parameter lists
        if (paramLists.isEmpty()) {
            writer.write("[]");
        } else if (paramLists.size() == 1) {
            writer.write(paramLists.get(0));
        } else {
            // Build concatenation chain: list1 List.++ list2 List.++ list3
            String result = paramLists.get(0);
            for (int i = 1; i < paramLists.size(); i++) {
                result = result + " List.++ " + paramLists.get(i);
            }
            writer.write(result);
        }
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Gets the query parameter name for a member.
     * Uses @xmlName trait if present, otherwise uses the member name.
     */
    private String getQueryParamName(MemberShape member) {
        return member.getTrait(XmlNameTrait.class)
                .map(XmlNameTrait::getValue)
                .orElse(member.getMemberName());
    }
    
    /**
     * Generates serialization code for a single member.
     */
    private void generateMemberSerialization(MemberShape member, Shape target, String paramName, 
                                             String varName, Model model, String clientNamespace, 
                                             UnisonWriter writer) {
        String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
        String inputTypeName = getInputTypeNameForAccessor(member, clientNamespace);
        
        switch (target.getType()) {
            case STRING:
            case ENUM:
            case INT_ENUM:
            case BOOLEAN:
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BIG_INTEGER:
            case BIG_DECIMAL:
            case TIMESTAMP:
                generateScalarSerialization(member, paramName, varName, memberName, inputTypeName, target, clientNamespace, writer);
                break;
            case LIST:
                generateListSerialization(member, (ListShape) target, paramName, varName, memberName, 
                        inputTypeName, model, clientNamespace, writer);
                break;
            case MAP:
                generateMapSerialization(member, (MapShape) target, paramName, varName, memberName, 
                        inputTypeName, model, clientNamespace, writer);
                break;
            case STRUCTURE:
                generateNestedStructureSerialization(member, (StructureShape) target, paramName, varName, 
                        memberName, inputTypeName, model, clientNamespace, writer);
                break;
            default:
                writer.write("$L = [] -- TODO: Unsupported type $L", varName, target.getType());
        }
    }
    
    /**
     * Gets the input type name for accessing a member (handles namespacing).
     */
    private String getInputTypeNameForAccessor(MemberShape member, String clientNamespace) {
        // For members in the input structure, we need to get the parent structure's name
        // The member's ID format is: namespace#StructureName$memberName
        String structureName = member.getId().getName().split("\\$")[0];
        return UnisonSymbolProvider.toNamespacedTypeName(structureName, clientNamespace);
    }
    
    /**
     * Generates serialization for a scalar field.
     * Pattern: [(paramName, textValue)] if present, [] if Optional None
     */
    private void generateScalarSerialization(MemberShape member, String paramName, String varName, 
                                             String memberName, String inputTypeName, Shape target,
                                             String clientNamespace, UnisonWriter writer) {
        String accessor = inputTypeName + "." + memberName + " input";
        String toTextFunc = getToTextFunction(target, clientNamespace);
        boolean isTextType = isTextType(target);
        
        // Check if field is non-optional (required or has default)
        boolean isNonOptional = member.isRequired() || member.hasTrait(DefaultTrait.class);
        
        if (isNonOptional) {
            if (isTextType) {
                writer.write("$L = [(\"$L\", $L)]", varName, paramName, accessor);
            } else {
                writer.write("$L = [(\"$L\", $L ($L))]", varName, paramName, toTextFunc, accessor);
            }
        } else {
            writer.write("$L = match $L with", varName, accessor);
            writer.indent();
            writer.write("None -> []");
            if (isTextType) {
                writer.write("Some val -> [(\"$L\", val)]", paramName);
            } else {
                writer.write("Some val -> [(\"$L\", $L val)]", paramName, toTextFunc);
            }
            writer.dedent();
        }
    }
    
    /**
     * Generates serialization for a list field.
     * AWS Query format: ParamName.1=val1&ParamName.2=val2
     */
    private void generateListSerialization(MemberShape member, ListShape listShape, String paramName, 
                                           String varName, String memberName, String inputTypeName, 
                                           Model model, String clientNamespace, UnisonWriter writer) {
        String accessor = inputTypeName + "." + memberName + " input";
        Shape elementShape = model.expectShape(listShape.getMember().getTarget());
        
        // Check if element is a structure - needs special handling
        if (elementShape.isStructureShape()) {
            generateStructureListSerialization(member, (StructureShape) elementShape, paramName, varName, 
                    accessor, model, clientNamespace, writer);
            return;
        }
        
        String toTextFunc = getToTextFunction(elementShape, clientNamespace);
        boolean isTextType = isTextType(elementShape);
        
        // Get the Unison type name for the element
        String elementTypeName = getUnisonTypeName(elementShape, clientNamespace);
        
        // Check if field is non-optional (required or has default)
        boolean isNonOptional = member.isRequired() || member.hasTrait(DefaultTrait.class);
        
        if (isNonOptional) {
            String helperName = varName + "_helper";
            writer.write(helperName + " : ($L, Nat) -> (Text, Text)", elementTypeName);
            writer.write(helperName + " = cases");
            writer.indent();
            if (isTextType) {
                // For text types, use value directly (val is element, idx is index)
                writer.write("(val, idx) -> (\"$L.\" ++ Nat.toText (idx + 1), val)", paramName);
            } else {
                writer.write("(val, idx) -> (\"$L.\" ++ Nat.toText (idx + 1), $L val)", paramName, toTextFunc);
            }
            writer.dedent();
            writer.write("$L = ($L) |> List.indexed |> List.map $L", varName, accessor, helperName);
        } else {
            String helperName = varName + "_helper";
            writer.write(helperName + " : ($L, Nat) -> (Text, Text)", elementTypeName);
            writer.write(helperName + " = cases");
            writer.indent();
            if (isTextType) {
                // For text types, use value directly (val is element, idx is index)
                writer.write("(val, idx) -> (\"$L.\" ++ Nat.toText (idx + 1), val)", paramName);
            } else {
                writer.write("(val, idx) -> (\"$L.\" ++ Nat.toText (idx + 1), $L val)", paramName, toTextFunc);
            }
            writer.dedent();
            writer.write("$L = match $L with", varName, accessor);
            writer.indent();
            writer.write("None -> []");
            writer.write("Some list -> list |> List.indexed |> List.map $L", helperName);
            writer.dedent();
        }
    }
    
    /**
     * Generates serialization for a list of structures.
     * AWS Query format: ParamName.1.Field1=val1&ParamName.1.Field2=val2&ParamName.2.Field1=val3...
     */
    private void generateStructureListSerialization(MemberShape member, StructureShape structureShape, 
                                                    String paramName, String varName, String accessor,
                                                    Model model, String clientNamespace, UnisonWriter writer) {
        String structTypeName = getUnisonTypeName(structureShape, clientNamespace);
        
        // Check if field is non-optional (required or has default)
        boolean isNonOptional = member.isRequired() || member.hasTrait(DefaultTrait.class);
        
        String helperName = varName + "_helper";
        writer.write(helperName + " : ($L, Nat) -> [(Text, Text)]", structTypeName);
        writer.write(helperName + " = cases");
        writer.indent();
        writer.write("(val, idx) -> let");
        writer.indent();
        writer.write("idxText = Nat.toText (idx + 1)");
        
        // Generate field serialization for each member of the structure
        // Separate required and optional fields
        List<String> requiredFields = new ArrayList<>();
        List<String> optionalFieldLists = new ArrayList<>();
        
        for (MemberShape structMember : structureShape.getAllMembers().values()) {
            String fieldName = getQueryParamName(structMember);
            String fieldAccessor = structTypeName + "." + UnisonSymbolProvider.toUnisonFunctionName(structMember.getMemberName()) + " val";
            Shape fieldShape = model.expectShape(structMember.getTarget());
            
            // Skip complex types (maps, lists, structures) within structures for now
            // These would require recursive/nested serialization which is complex
            if (fieldShape.isMapShape() || fieldShape.isListShape() || fieldShape.isStructureShape()) {
                // TODO: Implement nested complex type serialization
                continue;
            }
            
            String fieldParamName = paramName + ".\" ++ idxText ++ \"." + fieldName;
            
            boolean isRequired = structMember.isRequired();
            boolean isFieldText = isTextType(fieldShape);
            String toTextFunc = getToTextFunction(fieldShape, clientNamespace);
            
            if (isRequired) {
                // Required fields go directly in the list
                if (isFieldText) {
                    requiredFields.add("(\"" + fieldParamName + "\", " + fieldAccessor + ")");
                } else {
                    requiredFields.add("(\"" + fieldParamName + "\", " + toTextFunc + " (" + fieldAccessor + "))");
                }
            } else {
                // Optional fields need conditional inclusion
                String optFieldName = "opt_" + structMember.getMemberName();
                if (isFieldText) {
                    writer.write("$L = match $L with", optFieldName, fieldAccessor);
                    writer.indent();
                    writer.write("None -> []");
                    writer.write("Some v -> [(\"$L\", v)]", fieldParamName);
                    writer.dedent();
                } else {
                    writer.write("$L = match $L with", optFieldName, fieldAccessor);
                    writer.indent();
                    writer.write("None -> []");
                    writer.write("Some v -> [(\"$L\", $L v)]", fieldParamName, toTextFunc);
                    writer.dedent();
                }
                optionalFieldLists.add(optFieldName);
            }
        }
        
        // Build the result by concatenating required fields with optional field lists
        if (requiredFields.isEmpty()) {
            // Only optional fields
            if (optionalFieldLists.isEmpty()) {
                writer.write("[]");
            } else {
                String result = optionalFieldLists.get(0);
                for (int i = 1; i < optionalFieldLists.size(); i++) {
                    result = result + " List.++ " + optionalFieldLists.get(i);
                }
                writer.write(result);
            }
        } else {
            // Start with required fields
            String requiredList = "[ " + String.join(",\n  ", requiredFields) + " ]";
            if (optionalFieldLists.isEmpty()) {
                writer.write(requiredList);
            } else {
                // Concatenate required with optional
                String result = requiredList;
                for (String optList : optionalFieldLists) {
                    result = result + " List.++ " + optList;
                }
                writer.write(result);
            }
        }
        
        writer.dedent();
        writer.dedent();
        
        if (isNonOptional) {
            writer.write("$L = ($L) |> List.indexed |> List.flatMap $L", varName, accessor, helperName);
        } else {
            writer.write("$L = match $L with", varName, accessor);
            writer.indent();
            writer.write("None -> []");
            writer.write("Some list -> list |> List.indexed |> List.flatMap $L", helperName);
            writer.dedent();
        }
    }
    
    /**
     * Generates serialization for a map field.
     * AWS Query format: MapName.1.Key=k1&MapName.1.Value=v1
     */
    private void generateMapSerialization(MemberShape member, MapShape mapShape, String paramName, 
                                          String varName, String memberName, String inputTypeName,
                                          Model model, String clientNamespace, UnisonWriter writer) {
        String accessor = inputTypeName + "." + memberName + " input";
        Shape keyShape = model.expectShape(mapShape.getKey().getTarget());
        Shape valueShape = model.expectShape(mapShape.getValue().getTarget());
        
        // Skip maps with complex value types (structures, lists, or nested maps)
        // These would require recursive/nested serialization which is complex
        if (valueShape.isStructureShape() || valueShape.isListShape() || valueShape.isMapShape()) {
            // TODO: Implement nested complex type serialization for map values
            writer.write("$L = [] -- TODO: Map with complex value type not yet supported", varName);
            return;
        }
        
        boolean isKeyText = isTextType(keyShape);
        boolean isValueText = isTextType(valueShape);
        String keyToText = getToTextFunction(keyShape, clientNamespace);
        String valueToText = getToTextFunction(valueShape, clientNamespace);
        
        // Check if field is non-optional (required or has default)
        boolean isNonOptional = member.isRequired() || member.hasTrait(DefaultTrait.class);
        
        if (isNonOptional) {
            String keyExpr = isKeyText ? "k" : keyToText + " k";
            String valueExpr = isValueText ? "v" : valueToText + " v";
            writer.write("$L = (Map.toList ($L)) |> List.indexed |> List.flatMap (cases ((k, v), idx) -> let", varName, accessor);
            writer.indent();
            writer.write("idxText = Nat.toText (idx + 1)");
            writer.write("[ (\"$L.\" ++ idxText ++ \".Key\", $L),", paramName, keyExpr);
            writer.write("  (\"$L.\" ++ idxText ++ \".Value\", $L) ])", paramName, valueExpr);
            writer.dedent();
        } else {
            String keyExpr = isKeyText ? "k" : keyToText + " k";
            String valueExpr = isValueText ? "v" : valueToText + " v";
            writer.write("$L = match $L with", varName, accessor);
            writer.indent();
            writer.write("None -> []");
            writer.write("Some map -> (Map.toList map) |> List.indexed |> List.flatMap (cases ((k, v), idx) -> let");
            writer.indent();
            writer.write("idxText = Nat.toText (idx + 1)");
            writer.write("[ (\"$L.\" ++ idxText ++ \".Key\", $L),", paramName, keyExpr);
            writer.write("  (\"$L.\" ++ idxText ++ \".Value\", $L) ])", paramName, valueExpr);
            writer.dedent();
            writer.dedent();
        }
    }
    
    /**
     * Generates serialization for a nested structure.
     * AWS Query format: StructField.NestedField=value
     */
    private void generateNestedStructureSerialization(MemberShape member, StructureShape structShape, 
                                                      String paramName, String varName, String memberName, 
                                                      String inputTypeName, Model model, 
                                                      String clientNamespace, UnisonWriter writer) {
        String accessor = inputTypeName + "." + memberName + " input";
        
        if (member.isRequired()) {
            writer.write("$L =", varName);
            writer.indent();
            writer.write("nested = $L", accessor);
            generateFlattenedStructure(structShape, paramName, "nested", model, clientNamespace, writer);
            writer.dedent();
        } else {
            writer.write("$L = match $L with", varName, accessor);
            writer.indent();
            writer.write("None -> []");
            writer.write("Some nested ->");
            writer.indent();
            generateFlattenedStructure(structShape, paramName, "nested", model, clientNamespace, writer);
            writer.dedent();
            writer.dedent();
        }
    }
    
    /**
     * Generates flattened structure serialization with dot notation.
     */
    private void generateFlattenedStructure(StructureShape structure, String prefix, String varName, 
                                            Model model, String clientNamespace, UnisonWriter writer) {
        List<String> fieldParams = new ArrayList<>();
        
        for (MemberShape nestedMember : structure.getAllMembers().values()) {
            String nestedParamName = prefix + "." + getQueryParamName(nestedMember);
            String nestedMemberName = UnisonSymbolProvider.toUnisonFunctionName(nestedMember.getMemberName());
            String nestedTypeName = UnisonSymbolProvider.toNamespacedTypeName(structure.getId().getName(), clientNamespace);
            String nestedAccessor = nestedTypeName + "." + nestedMemberName + " " + varName;
            
            Shape nestedTarget = model.expectShape(nestedMember.getTarget());
            String toTextFunc = getToTextFunction(nestedTarget, clientNamespace);
            
            // For simplicity, only handle scalar fields in nested structures
            if (isScalarShape(nestedTarget)) {
                if (nestedMember.isRequired()) {
                    fieldParams.add("(\"" + nestedParamName + "\", " + toTextFunc + " (" + nestedAccessor + "))");
                } else {
                    writer.write("-- TODO: Handle optional nested field: $L", nestedMemberName);
                }
            }
        }
        
        if (fieldParams.isEmpty()) {
            writer.write("[]");
        } else {
            writer.write("[ " + String.join(", ", fieldParams) + " ]");
        }
    }
    
    /**
     * Checks if a shape is a scalar type.
     */
    private boolean isScalarShape(Shape shape) {
        switch (shape.getType()) {
            case STRING:
            case ENUM:
            case INT_ENUM:
            case BOOLEAN:
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BIG_INTEGER:
            case BIG_DECIMAL:
            case TIMESTAMP:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Gets the appropriate Unison function to convert a shape to Text.
     * Returns a function reference that can be applied to a value.
     */
    private String getToTextFunction(Shape shape, String clientNamespace) {
        // Check for enum types first (enums may be string-based)
        if (shape.isEnumShape() || (shape.isStringShape() && shape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            return UnisonSymbolProvider.toNamespacedFunctionName(shape.getId().getName() + "ToText", clientNamespace);
        }
        
        switch (shape.getType()) {
            case STRING:
            case TIMESTAMP:
                // For strings and timestamps, use Function.id - they're already text
                return "Function.id";
            case BOOLEAN:
                return "Boolean.toText";
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
                return "Int.toText";
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
                return "Float.toText";
            default:
                // For unknown types, generate a descriptive error
                return "(x -> bug (\"Cannot convert \" ++ Any.typeLinkToText (Any.typeLink x) ++ \" to Text\"))";
        }
    }
    
    // Overload for backward compatibility - defaults to no namespace
    private String getToTextFunction(Shape shape) {
        return getToTextFunction(shape, "");
    }
    
    /**
     * Checks if a shape type is already text (doesn't need conversion).
     */
    private boolean isTextType(Shape shape) {
        return shape.getType() == ShapeType.STRING || shape.getType() == ShapeType.TIMESTAMP;
    }
    
    /**
     * Gets the Unison type name for a shape (with namespace prefix for complex types).
     */
    private String getUnisonTypeName(Shape shape, String clientNamespace) {
        switch (shape.getType()) {
            case STRING:
                // Check if this is an enum string
                if (shape.hasTrait(EnumTrait.class)) {
                    return UnisonSymbolProvider.toNamespacedTypeName(shape.getId().getName(), clientNamespace);
                }
                return "Text";
            case TIMESTAMP:
                return "Text";
            case BOOLEAN:
                return "Boolean";
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
                return "Int";
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
                return "Float";
            case STRUCTURE:
                // For structures, use the namespaced type name
                return UnisonSymbolProvider.toNamespacedTypeName(shape.getId().getName(), clientNamespace);
            default:
                return "Text"; // Fallback
        }
    }
    
    /**
     * Generates Map extraction from XML response.
     * 
     * <p>AWS Query maps in XML follow the structure:
     * <pre>
     * &lt;MapField&gt;
     *   &lt;entry&gt;
     *     &lt;key&gt;KeyName&lt;/key&gt;
     *     &lt;value&gt;ValueText&lt;/value&gt;
     *   &lt;/entry&gt;
     *   &lt;entry&gt;
     *     &lt;key&gt;AnotherKey&lt;/key&gt;
     *     &lt;value&gt;AnotherValue&lt;/value&gt;
     *   &lt;/entry&gt;
     * &lt;/MapField&gt;
     * </pre>
     * 
     * <p>The generated code uses aws.xml.extractMap to parse the entries and
     * converts the result to a Unison Map using Map.fromList.
     */
    private void generateMapExtraction(MemberShape member, MapShape mapShape, String xmlName, 
                                       String varName, boolean isNonOptional, Model model, 
                                       UnisonWriter writer) {
        // Get the entry tag name - can be customized with @xmlFlattened or @xmlName
        String entryTag = getMapEntryTag(member);
        String keyTag = getMapKeyTag(mapShape);
        String valueTag = getMapValueTag(mapShape);
        
        if (isNonOptional) {
            // Required map - extract or use empty map
            writer.write("$LMapSoup = aws.xml.findOpt \"$L\" resultSoup", varName, xmlName);
            writer.write("$LList = match $LMapSoup with", varName, varName);
            writer.indent();
            writer.write("None -> []");
            writer.write("Some mapSoup ->");
            writer.indent();
            writer.write("xmlText = handle Soup.toXML mapSoup with cases");
            writer.indent();
            writer.write("{ x } -> x");
            writer.write("{ Throw.throw err -> _ } -> Exception.raise (aws.xml.xmlErrorToFailure err)");
            writer.dedent();
            writer.write("aws.xml.extractMap \"$L\" \"$L\" \"$L\" xmlText", entryTag, keyTag, valueTag);
            writer.dedent();
            writer.dedent();
            writer.write("$L = Map.fromList $LList", varName, varName);
        } else {
            // Optional map - wrap in Optional
            writer.write("$LMapSoup = aws.xml.findOpt \"$L\" resultSoup", varName, xmlName);
            writer.write("$L = match $LMapSoup with", varName, varName);
            writer.indent();
            writer.write("None -> None");
            writer.write("Some mapSoup ->");
            writer.indent();
            writer.write("xmlText = handle Soup.toXML mapSoup with cases");
            writer.indent();
            writer.write("{ x } -> x");
            writer.write("{ Throw.throw err -> _ } -> Exception.raise (aws.xml.xmlErrorToFailure err)");
            writer.dedent();
            writer.write("mapList = aws.xml.extractMap \"$L\" \"$L\" \"$L\" xmlText", entryTag, keyTag, valueTag);
            writer.write("Some (Map.fromList mapList)");
            writer.dedent();
            writer.dedent();
        }
    }
    
    /**
     * Gets the XML tag name for map entries.
     * Defaults to "entry" for AWS Query protocol.
     */
    private String getMapEntryTag(MemberShape member) {
        // Check for @xmlFlattened trait - flattened maps don't have entry wrapper
        if (member.hasTrait(XmlFlattenedTrait.class)) {
            return getXmlFieldName(member);
        }
        return "entry";
    }
    
    /**
     * Gets the XML tag name for map keys.
     * Checks the key member for @xmlName trait, defaults to "key".
     */
    private String getMapKeyTag(MapShape mapShape) {
        MemberShape keyMember = mapShape.getKey();
        return keyMember.getTrait(XmlNameTrait.class)
                .map(XmlNameTrait::getValue)
                .orElse("key");
    }
    
    /**
     * Gets the XML tag name for map values.
     * Checks the value member for @xmlName trait, defaults to "value".
     */
    private String getMapValueTag(MapShape mapShape) {
        MemberShape valueMember = mapShape.getValue();
        return valueMember.getTrait(XmlNameTrait.class)
                .map(XmlNameTrait::getValue)
                .orElse("value");
    }
    
    /**
     * Generates List extraction from XML response.
     * 
     * <p>For lists of scalars (Text, Int, etc.), uses aws.xml.extractAll.
     * For lists of structures, extracts all blocks and parses each one.
     */
    private void generateListExtraction(MemberShape member, ListShape listShape, String xmlName, 
                                        String varName, boolean isNonOptional, Model model, 
                                        String clientNamespace, UnisonWriter writer) {
        Shape elementShape = model.expectShape(listShape.getMember().getTarget());
        String elementTag = getListElementTag(member, listShape);
        
        if (elementShape.getType() == ShapeType.STRUCTURE) {
            // List of structures - need to extract blocks and parse each
            generateStructureListExtraction(member, (StructureShape) elementShape, xmlName, elementTag, 
                    varName, isNonOptional, model, clientNamespace, writer);
        } else {
            // List of scalars - use findAllText
            if (isNonOptional) {
                writer.write("$L = aws.xml.findAllText \"$L\" resultSoup", varName, elementTag);
            } else {
                writer.write("$LListSoup = aws.xml.findOpt \"$L\" resultSoup", varName, xmlName);
                writer.write("$L = match $LListSoup with", varName, varName);
                writer.indent();
                writer.write("None -> None");
                writer.write("Some listSoup -> Some (aws.xml.findAllText \"$L\" listSoup)", elementTag);
                writer.dedent();
            }
        }
    }
    
    /**
     * Generates extraction code for a nested structure from XML.
     * 
     * <p>Uses aws.xml.parseNested with a text-based parser function.
     */
    private void generateNestedStructureExtraction(MemberShape member, StructureShape structShape, 
                                                   String xmlName, String varName, boolean isNonOptional, 
                                                   Model model, String clientNamespace, UnisonWriter writer) {
        String baseTypeName = UnisonSymbolProvider.toUnisonTypeName(structShape.getId().getName());
        String parserName = UnisonSymbolProvider.toNamespacedFunctionName(
                "parse" + baseTypeName + "FromXml", clientNamespace);
        
        if (isNonOptional) {
            // Required nested structure - parse and extract, bug if missing
            String optVarName = varName + "Opt";
            writer.write("$L = aws.xml.parseNested \"$L\" $L resultSoup", optVarName, xmlName, parserName);
            writer.write("$L = Optional.getOrElse (bug \"Required nested structure '$L' not found\") $L", 
                    varName, xmlName, optVarName);
        } else {
            // Optional nested structure
            writer.write("$L = aws.xml.parseNested \"$L\" $L resultSoup", varName, xmlName, parserName);
        }
    }
    
    /**
     * Generates extraction code for a list of structures from XML.
     */
    private void generateStructureListExtraction(MemberShape member, StructureShape elementStructure, 
                                                  String xmlName, String elementTag, String varName, 
                                                  boolean isNonOptional, Model model, 
                                                  String clientNamespace, UnisonWriter writer) {
        String structTypeName = UnisonSymbolProvider.toNamespacedTypeName(
                elementStructure.getId().getName(), clientNamespace);
        String parserName = varName + "_parseElement";
        
        // Generate helper function to parse a single element
        writer.write("");
        writer.write("$L : Text -> $L", parserName, structTypeName);
        writer.write("$L elemXml =", parserName);
        writer.indent();
        
        // Convert XML text to Soup for field extraction
        writer.write("elemSoup = Soup.parseXML elemXml");
        writer.write("");
        
        // Extract each field from the element - handle all types properly
        List<MemberShape> structMembers = new ArrayList<>(elementStructure.getAllMembers().values());
        for (MemberShape structMember : structMembers) {
            String fieldName = UnisonSymbolProvider.toUnisonFunctionName(structMember.getMemberName());
            String fieldVarName = UnisonReservedWords.appendSuffix(fieldName, "Val");
            String fieldXmlName = getXmlFieldName(structMember);
            Shape fieldTarget = model.expectShape(structMember.getTarget());
            boolean isFieldNonOptional = structMember.isRequired() || structMember.hasTrait(DefaultTrait.class);
            
            // Handle different field types
            switch (fieldTarget.getType()) {
                case MAP:
                    // Map field in nested structure
                    generateMapExtractionInline(structMember, (MapShape) fieldTarget, fieldXmlName, 
                            fieldVarName, isFieldNonOptional, model, "elemXml", writer);
                    break;
                case LIST:
                    // Handle list fields in nested structures
                    ListShape fieldListShape = (ListShape) fieldTarget;
                    Shape fieldListElementShape = model.expectShape(fieldListShape.getMember().getTarget());
                    
                    if (fieldListElementShape.isStringShape() && fieldListElementShape.hasTrait(EnumTrait.class)) {
                        // List of enums - need to convert from text
                        String enumTypeName = UnisonSymbolProvider.toNamespacedTypeName(
                                fieldListElementShape.getId().getName(), clientNamespace);
                        String fromTextFunc = UnisonSymbolProvider.toUnisonFunctionName(
                                fieldListElementShape.getId().getName() + "FromText");
                        String enumTypeNamespace = clientNamespace;
                        
                        // Generate helper function for enum conversion
                        String enumConverterName = fieldVarName + "_convertEnum";
                        writer.write("");
                        writer.write("$L : Text -> $L", enumConverterName, enumTypeName);
                        writer.write("$L t = match $L.$L t with", enumConverterName, enumTypeNamespace, fromTextFunc);
                        writer.indent();
                        writer.write("Some v -> v");
                        writer.write("None -> bug (\"Invalid enum value: \" ++ t)");
                        writer.dedent();
                        writer.write("");
                        
                        if (isFieldNonOptional) {
                            writer.write("$LTexts = aws.xml.extractAll \"$L\" elemXml", fieldVarName, fieldXmlName);
                            writer.write("$L = List.map $L $LTexts", fieldVarName, enumConverterName, fieldVarName);
                        } else {
                            writer.write("$L = match aws.xml.extractElementOpt \"$L\" elemXml with", fieldVarName, fieldXmlName);
                            writer.indent();
                            writer.write("None -> None");
                            writer.write("Some elem ->");
                            writer.indent();
                            writer.write("texts = aws.xml.extractAll \"member\" elem");
                            writer.write("enums = List.map $L texts", enumConverterName);
                            writer.write("Some enums");
                            writer.dedent();
                            writer.dedent();
                        }
                    } else if (fieldListElementShape.isStructureShape()) {
                        // List of structures - need to parse each element
                        String elemStructTypeName = UnisonSymbolProvider.toNamespacedTypeName(
                                fieldListElementShape.getId().getName(), clientNamespace);
                        String elemParserName = UnisonSymbolProvider.toNamespacedFunctionName(
                                "parse" + fieldListElementShape.getId().getName() + "FromXml", clientNamespace);
                        
                        // Get the member tag name for the list elements
                        String memberTag = getListElementTag(structMember, fieldListShape);
                        
                        if (isFieldNonOptional) {
                            writer.write("$L = aws.xml.parseList \"$L\" $L elemSoup", 
                                    fieldVarName, memberTag, elemParserName);
                        } else {
                            writer.write("$L = aws.xml.parseOptionalWrappedList \"$L\" \"$L\" $L elemSoup",
                                    fieldVarName, fieldXmlName, memberTag, elemParserName);
                        }
                    } else {
                        // List of scalars (strings, integers, etc.)
                        String fieldExtractor = getXmlExtractor(fieldTarget, structMember.isRequired());
                        writer.write("$L = $L \"$L\" elemSoup", fieldVarName, fieldExtractor, fieldXmlName);
                    }
                    break;
                case STRUCTURE:
                    // Nested structure field - use parseNested with Soup-based parser
                    String nestedTypeName = UnisonSymbolProvider.toUnisonTypeName(fieldTarget.getId().getName());
                    String nestedParserName = UnisonSymbolProvider.toNamespacedFunctionName(
                            "parse" + nestedTypeName + "FromXml", clientNamespace);
                    
                    if (isFieldNonOptional) {
                        String optVarName = fieldVarName + "Opt";
                        writer.write("$L = aws.xml.parseNested \"$L\" $L elemSoup", optVarName, fieldXmlName, nestedParserName);
                        writer.write("$L = Optional.getOrElse (bug \"Required nested structure '$L' not found\") $L", 
                                fieldVarName, fieldXmlName, optVarName);
                    } else {
                        writer.write("$L = aws.xml.parseNested \"$L\" $L elemSoup", fieldVarName, fieldXmlName, nestedParserName);
                    }
                    break;
                default:
                    // Scalar types (including enums)
                    if (fieldTarget.isStringShape() && fieldTarget.hasTrait(EnumTrait.class)) {
                        // Single enum field - need to convert from text
                        String enumTypeName = getUnisonTypeName(fieldTarget, clientNamespace);
                        String fromTextFunc = UnisonSymbolProvider.toUnisonFunctionName(
                                fieldTarget.getId().getName() + "FromText");
                        String enumTypeNamespace = clientNamespace;
                        
                        if (isFieldNonOptional) {
                            writer.write("$LText = aws.xml.extractElement \"$L\" elemXml", fieldVarName, fieldXmlName);
                            writer.write("$L = match $L.$L $LText with", fieldVarName, enumTypeNamespace, fromTextFunc, fieldVarName);
                            writer.indent();
                            writer.write("Some v -> v");
                            writer.write("None -> bug (\"Invalid enum value: \" ++ $LText)", fieldVarName);
                            writer.dedent();
                        } else {
                            // For optional enum fields, return None for unrecognized values
                            writer.write("$L = Optional.flatMap $L.$L (aws.xml.extractElementOpt \"$L\" elemXml)", 
                                    fieldVarName, enumTypeNamespace, fromTextFunc, fieldXmlName);
                        }
                    } else {
                        // Other scalar types (text, int, boolean, etc.)
                        // Handle booleans and integers with required/default trait
                        if (fieldTarget.getType() == ShapeType.BOOLEAN) {
                            String extractor = getXmlExtractor(fieldTarget, false); // Always use optional extractor
                            if (isFieldNonOptional) {
                                writer.write("$LOpt = $L \"$L\" elemSoup", fieldVarName, extractor, fieldXmlName);
                                writer.write("$L = Optional.getOrElse false $LOpt", fieldVarName, fieldVarName);
                            } else {
                                writer.write("$L = $L \"$L\" elemSoup", fieldVarName, extractor, fieldXmlName);
                            }
                        } else if (fieldTarget.getType() == ShapeType.INTEGER || 
                                   fieldTarget.getType() == ShapeType.LONG ||
                                   fieldTarget.getType() == ShapeType.BYTE ||
                                   fieldTarget.getType() == ShapeType.SHORT) {
                            String extractor = getXmlExtractor(fieldTarget, false); // Always use optional extractor
                            if (isFieldNonOptional) {
                                writer.write("$LOpt = $L \"$L\" elemSoup", fieldVarName, extractor, fieldXmlName);
                                writer.write("$L = Optional.getOrElse +0 $LOpt", fieldVarName, fieldVarName);
                            } else {
                                writer.write("$L = $L \"$L\" elemSoup", fieldVarName, extractor, fieldXmlName);
                            }
                        } else {
                            // String, timestamp, and other types - use appropriate extractor based on optionality
                            String extractor = getXmlExtractor(fieldTarget, isFieldNonOptional);
                            writer.write("$L = $L \"$L\" elemSoup", fieldVarName, extractor, fieldXmlName);
                        }
                    }
                    break;
            }
        }
        
        // Construct the structure
        writer.write("$L", structTypeName + "." + UnisonSymbolProvider.toUnisonTypeName(elementStructure.getId().getName()));
        for (MemberShape structMember : structMembers) {
            String fieldName = UnisonSymbolProvider.toUnisonFunctionName(structMember.getMemberName());
            writer.write("  $L", UnisonReservedWords.appendSuffix(fieldName, "Val"));
        }
        
        writer.dedent();
        writer.write("");
        
        // Now extract the list using Soup
        if (isNonOptional) {
            writer.write("$LListSoup = aws.xml.findOpt \"$L\" resultSoup", varName, xmlName);
            writer.write("$LBlocks = match $LListSoup with", varName, varName);
            writer.indent();
            writer.write("None -> []");
            writer.write("Some listSoup ->");
            writer.indent();
            writer.write("xmlText = handle Soup.toXML listSoup with cases");
            writer.indent();
            writer.write("{ x } -> x");
            writer.write("{ Throw.throw err -> _ } -> Exception.raise (aws.xml.xmlErrorToFailure err)");
            writer.dedent();
            writer.write("aws.xml.extractAllBlocks \"$L\" xmlText", elementTag);
            writer.dedent();
            writer.dedent();
            writer.write("$L = List.map $L $LBlocks", varName, parserName, varName);
        } else {
            writer.write("$LListSoup = aws.xml.findOpt \"$L\" resultSoup", varName, xmlName);
            writer.write("$L = match $LListSoup with", varName, varName);
            writer.indent();
            writer.write("None -> None");
            writer.write("Some listSoup ->");
            writer.indent();
            writer.write("xmlText = handle Soup.toXML listSoup with cases");
            writer.indent();
            writer.write("{ x } -> x");
            writer.write("{ Throw.throw err -> _ } -> Exception.raise (aws.xml.xmlErrorToFailure err)");
            writer.dedent();
            writer.write("blocks = aws.xml.extractAllBlocks \"$L\" xmlText", elementTag);
            writer.write("Some (List.map $L blocks)", parserName);
            writer.dedent();
            writer.dedent();
        }
    }
    
    /**
     * Generates inline Map extraction for nested structures.
     * Similar to generateMapExtraction but uses a different root element variable.
     */
    private void generateMapExtractionInline(MemberShape member, MapShape mapShape, String xmlName, 
                                             String varName, boolean isNonOptional, Model model, 
                                             String rootVar, UnisonWriter writer) {
        String entryTag = getMapEntryTag(member);
        String keyTag = getMapKeyTag(mapShape);
        String valueTag = getMapValueTag(mapShape);
        
        if (isNonOptional) {
            writer.write("$LElement = aws.xml.extractElementOpt \"$L\" $L", varName, xmlName, rootVar);
            writer.write("$LList = match $LElement with", varName, varName);
            writer.indent();
            writer.write("None -> []");
            writer.write("Some elem -> aws.xml.extractMap \"$L\" \"$L\" \"$L\" elem", entryTag, keyTag, valueTag);
            writer.dedent();
            writer.write("$L = Map.fromList $LList", varName, varName);
        } else {
            writer.write("$LElement = aws.xml.extractElementOpt \"$L\" $L", varName, xmlName, rootVar);
            writer.write("$L = match $LElement with", varName, varName);
            writer.indent();
            writer.write("None -> None");
            writer.write("Some elem ->");
            writer.indent();
            writer.write("mapList = aws.xml.extractMap \"$L\" \"$L\" \"$L\" elem", entryTag, keyTag, valueTag);
            writer.write("Some (Map.fromList mapList)");
            writer.dedent();
            writer.dedent();
        }
    }
    
    /**
     * Gets the XML tag name for list elements.
     * Checks the list member for @xmlName trait, defaults to "member".
     */
    private String getListElementTag(MemberShape listMember, ListShape listShape) {
        MemberShape elementMember = listShape.getMember();
        return elementMember.getTrait(XmlNameTrait.class)
                .map(XmlNameTrait::getValue)
                .orElse("member");
    }
    
    // ========== Response Deserialization ==========
    
    @Override
    public void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        String clientNamespace = context.settings().getClientNamespace();
        
        Optional<StructureShape> outputShape = ProtocolUtils.getOutputShape(operation, model);
        if (outputShape.isEmpty()) {
            return; // No output to deserialize
        }
        
        StructureShape output = outputShape.get();
        String outputType = UnisonSymbolProvider.toNamespacedTypeName(output.getId().getName(), clientNamespace);
        String functionName = clientNamespace + "." + UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName() + "ResponseParser");
        String operationName = operation.getId().getName();
        
        writer.writeComment("Parse " + operationName + " response from XML");
        writer.writeSignature(functionName, "Http.Response -> '{Exception} " + outputType);
        writer.write("$L response = do", functionName);
        writer.indent();
        
        // Navigate response wrapper structure
        generateResponseWrapperNavigation(operation, writer);
        
        // Extract fields from result element
        generateFieldExtraction(output, model, clientNamespace, writer);
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates XML navigation to the result element containing operation output.
     * 
     * <p>AWS Query uses: &lt;OperationNameResponse&gt;&lt;OperationNameResult&gt;...&lt;/OperationNameResult&gt;&lt;/OperationNameResponse&gt;
     * <p>EC2 Query uses: &lt;OperationNameResponse&gt;...&lt;/OperationNameResponse&gt; (no nested Result element)
     * 
     * @param operation The operation shape
     * @param writer The Unison writer
     */
    protected void generateResponseWrapperNavigation(OperationShape operation, UnisonWriter writer) {
        String operationName = operation.getId().getName();
        
        writer.write("-- AWS Query response structure:");
        writer.write("-- <OperationNameResponse><OperationNameResult>...</OperationNameResult></OperationNameResponse>");
        writer.write("soup = Soup.parseXML (fromUtf8 (Http.Response.body response))");
        writer.write("resultSoup = aws.xml.runXml (aws.xml.findAndDrill soup [\"$LResponse\", \"$LResult\"])", operationName, operationName);
    }
    
    /**
     * Generates field extraction from XML result element.
     * 
     * <p>Extracts each field from the XML using appropriate extraction functions
     * based on the field's type, then constructs the output record.
     */
    private void generateFieldExtraction(StructureShape output, Model model, 
                                         String clientNamespace, UnisonWriter writer) {
        List<MemberShape> members = new ArrayList<>(output.getAllMembers().values());
        
        if (members.isEmpty()) {
            // No fields - return the unit type value directly
            // Empty structures are defined as type aliases (unit types), not records
            String outputTypeName = UnisonSymbolProvider.toNamespacedTypeName(output.getId().getName(), clientNamespace);
            writer.write(outputTypeName);
            return;
        }
        
        writer.write("");
        writer.write("-- Extract fields from XML result");
        
        // Extract each field
        for (MemberShape member : members) {
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            String varName = UnisonReservedWords.appendSuffix(memberName, "Val");
            String xmlName = getXmlFieldName(member);
            Shape target = model.expectShape(member.getTarget());
            
            // Check if field is non-optional (required or has default) - matches StructureGenerator logic
            boolean isNonOptional = member.isRequired() || member.hasTrait(DefaultTrait.class);
            
            String extractor = getXmlExtractor(target, member.isRequired());
            
            // Handle different return types from extractors (now using Soup)
            switch (target.getType()) {
                case BOOLEAN:
                    // Boolean extractor returns Optional Boolean
                    if (isNonOptional) {
                        writer.write("$L = aws.xml.findBool \"$L\" resultSoup |> Optional.getOrElse false", varName, xmlName);
                    } else {
                        writer.write("$L = aws.xml.findBool \"$L\" resultSoup", varName, xmlName);
                    }
                    break;
                case INTEGER:
                case LONG:
                case BYTE:
                case SHORT:
                    // Integer extractors return Optional Int
                    if (isNonOptional) {
                        writer.write("$L = aws.xml.findInt \"$L\" resultSoup |> Optional.getOrElse +0", varName, xmlName);
                    } else {
                        writer.write("$L = aws.xml.findInt \"$L\" resultSoup", varName, xmlName);
                    }
                    break;
                case FLOAT:
                case DOUBLE:
                    // Extract as text and parse
                    if (isNonOptional) {
                        writer.write("$L = aws.xml.findText \"$L\" resultSoup |> Optional.flatMap Float.fromText |> Optional.getOrElse 0.0", varName, xmlName);
                    } else {
                        writer.write("$L = aws.xml.findText \"$L\" resultSoup |> Optional.flatMap Float.fromText", varName, xmlName);
                    }
                    break;
                case MAP:
                    // Maps are extracted as list of key-value pairs from XML, then converted to Map
                    generateMapExtraction(member, (MapShape) target, xmlName, varName, isNonOptional, model, writer);
                    break;
                case LIST:
                    // Lists need special handling for structured element types
                    generateListExtraction(member, (ListShape) target, xmlName, varName, isNonOptional, model, clientNamespace, writer);
                    break;
                case STRUCTURE:
                    // Nested structure - use parseNested with Soup-based parser
                    generateNestedStructureExtraction(member, (StructureShape) target, xmlName, varName, isNonOptional, model, clientNamespace, writer);
                    break;
                default:
                    // String, timestamp, and other types
                    if (isNonOptional) {
                        writer.write("$L = aws.xml.findText \"$L\" resultSoup |> Optional.getOrElse \"\"", varName, xmlName);
                    } else {
                        writer.write("$L = aws.xml.findText \"$L\" resultSoup", varName, xmlName);
                    }
            }
        }
        
        // Construct output record with extracted fields
        writer.write("");
        writer.write("-- Construct output record");
        String outputTypeName = UnisonSymbolProvider.toUnisonTypeName(output.getId().getName());
        
        // Build positional arguments for constructor
        List<String> args = new ArrayList<>();
        for (MemberShape member : members) {
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            args.add(UnisonReservedWords.appendSuffix(memberName, "Val"));
        }
        
        // Generate constructor call with positional arguments
        StringBuilder sb = new StringBuilder();
        sb.append(outputTypeName).append(".").append(outputTypeName);
        for (String arg : args) {
            sb.append(" ").append(arg);
        }
        writer.write(sb.toString());
    }
    
    /**
     * Gets the XML field name for a member, using @xmlName trait if present.
     */
    private String getXmlFieldName(MemberShape member) {
        return member.getTrait(XmlNameTrait.class)
                .map(XmlNameTrait::getValue)
                .orElse(member.getMemberName());
    }
    
    /**
     * Gets the appropriate XML extractor function based on the shape type.
     * 
     * <p>Returns Soup-based functions like:
     * <ul>
     *   <li>aws.xml.findText - for text fields (returns Optional)</li>
     *   <li>aws.xml.findInt - for integer fields (returns Optional)</li>
     *   <li>aws.xml.findBool - for boolean fields (returns Optional)</li>
     * </ul>
     * 
     * <p>Note: All Soup extractors return Optional, so required fields
     * need to unwrap the Optional or provide a default value.
     */
    private String getXmlExtractor(Shape shape, boolean isRequired) {
        switch (shape.getType()) {
            case STRING:
            case TIMESTAMP:
                return "aws.xml.findText";
            case BOOLEAN:
                return "aws.xml.findBool";
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
                return "aws.xml.findInt";
            case FLOAT:
            case DOUBLE:
                // Use findText and parse
                return "aws.xml.findText";
            case LIST:
                return "aws.xml.findAllText";
            case STRUCTURE:
                return "aws.xml.findText";
            default:
                return "aws.xml.findText";
        }
    }
    
    // ========== Error Parsing ==========
    
    @Override
    public void generateErrorParser(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        ServiceShape service = context.serviceShape();
        String clientNamespace = context.settings().getClientNamespace();
        String errorTypeName = getErrorTypeName(service, clientNamespace);
        
        writer.writeDocComment(getErrorParserDocComment());
        writer.writeSignature(clientNamespace + ".parseError", "Http.Response -> " + errorTypeName);
        writer.write("$L.parseError response =", clientNamespace);
        writer.indent();
        
        generateErrorParserBody(service, clientNamespace, errorTypeName, writer);
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Gets the error type name for this service.
     * 
     * @param service The service shape
     * @param clientNamespace The client namespace
     * @return The fully qualified error type name
     */
    protected String getErrorTypeName(ServiceShape service, String clientNamespace) {
        String serviceName = service.getId().getName();
        
        // Remove "Service" suffix if present to avoid duplication
        if (serviceName.endsWith("Service")) {
            serviceName = serviceName.substring(0, serviceName.length() - 7);
        }
        
        return UnisonSymbolProvider.toNamespacedTypeName(
                serviceName + "ServiceError", clientNamespace);
    }
    
    /**
     * Gets the documentation comment for the error parser.
     * Can be overridden by subclasses to provide protocol-specific documentation.
     * 
     * @return The doc comment string
     */
    protected String getErrorParserDocComment() {
        return "Parse AWS Query error response\n\n" +
                "AWS Query error format:\n" +
                "<ErrorResponse>\n" +
                "  <Error>\n" +
                "    <Type>Sender</Type>\n" +
                "    <Code>InvalidParameterValue</Code>\n" +
                "    <Message>...</Message>\n" +
                "  </Error>\n" +
                "  <RequestId>xyz789</RequestId>\n" +
                "</ErrorResponse>";
    }
    
    /**
     * Generates the body of the error parser function.
     * Subclasses can override this to provide protocol-specific error parsing logic.
     * 
     * @param service The service shape
     * @param clientNamespace The client namespace
     * @param errorTypeName The error type name
     * @param writer The Unison writer
     */
    protected void generateErrorParserBody(ServiceShape service, String clientNamespace, 
                                          String errorTypeName, UnisonWriter writer) {
        // Parse AWS Query error XML structure using Soup
        writer.write("-- Parse AWS Query error response using Soup");
        writer.write("soup = Soup.parseXML (fromUtf8 (Http.Response.body response))");
        writer.write("errorSoup = aws.xml.runXml (aws.xml.findAndDrill soup [\"ErrorResponse\", \"Error\"])");
        writer.write("code = aws.xml.findText \"Code\" errorSoup |> Optional.getOrElse \"UnknownError\"");
        writer.write("message = aws.xml.findText \"Message\" errorSoup |> Optional.getOrElse \"\"");
        writer.write("");
        writer.write("-- Map to service-specific error type");
        writer.write("$L.fromCodeAndMessage code message", errorTypeName);
    }
}
