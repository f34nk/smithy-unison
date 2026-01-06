package io.smithy.unison.codegen.protocols;

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
import software.amazon.smithy.model.traits.XmlNameTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        
        // Placeholder for HTTP POST (Step 2.5)
        writer.write("");
        writer.write("-- TODO: Sign request with SigV4");
        writer.write("-- allHeaders = !(aws.sigv4.addSigningHeaders signingConfig method uri \"\" headers bodyBytes)");
        
        // Placeholder for HTTP POST (Step 2.5)
        writer.write("");
        writer.write("-- TODO: Execute HTTP POST");
        writer.write("-- request = Http.Request.post url allHeaders bodyBytes");
        writer.write("-- response = !(executeRequest request)");
        
        // Placeholder for response parsing (Step 2.6)
        writer.write("");
        writer.write("-- TODO: Parse XML response");
        writer.write("-- parseResponse response");
        
        // Temporary stub return
        writer.write("");
        writer.write("bug \"AWS Query operation not yet implemented\"");
        
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
            // Build concatenation chain: list1 ++ list2 ++ list3
            String result = paramLists.get(0);
            for (int i = 1; i < paramLists.size(); i++) {
                result = "(List.++) " + result + " " + paramLists.get(i);
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
                generateScalarSerialization(member, paramName, varName, memberName, inputTypeName, target, writer);
                break;
            case LIST:
                generateListSerialization(member, (ListShape) target, paramName, varName, memberName, 
                        inputTypeName, model, clientNamespace, writer);
                break;
            case MAP:
                generateMapSerialization(member, (MapShape) target, paramName, varName, memberName, 
                        inputTypeName, model, writer);
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
                                             UnisonWriter writer) {
        String accessor = inputTypeName + "." + memberName + " input";
        String toTextFunc = getToTextFunction(target);
        
        if (member.isRequired()) {
            writer.write("$L = [(\"$L\", $L ($L))]", varName, paramName, toTextFunc, accessor);
        } else {
            writer.write("$L = match $L with", varName, accessor);
            writer.indent();
            writer.write("None -> []");
            writer.write("Some val -> [(\"$L\", $L val)]", paramName, toTextFunc);
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
        String toTextFunc = getToTextFunction(elementShape);
        
        if (member.isRequired()) {
            writer.write("$L =", varName);
            writer.indent();
            writer.write("$L", accessor);
            writer.write("|> List.indexed");
            writer.write("|> List.map (pair -> match pair with");
            writer.indent();
            writer.write("(idx, val) -> (\"$L.\" ++ Int.toText (idx + 1), $L val))", paramName, toTextFunc);
            writer.dedent();
            writer.dedent();
        } else {
            writer.write("$L = match $L with", varName, accessor);
            writer.indent();
            writer.write("None -> []");
            writer.write("Some list ->");
            writer.indent();
            writer.write("list");
            writer.write("|> List.indexed");
            writer.write("|> List.map (pair -> match pair with");
            writer.indent();
            writer.write("(idx, val) -> (\"$L.\" ++ Int.toText (idx + 1), $L val))", paramName, toTextFunc);
            writer.dedent();
            writer.dedent();
            writer.dedent();
        }
    }
    
    /**
     * Generates serialization for a map field.
     * AWS Query format: MapName.1.Key=k1&MapName.1.Value=v1
     */
    private void generateMapSerialization(MemberShape member, MapShape mapShape, String paramName, 
                                          String varName, String memberName, String inputTypeName,
                                          Model model, UnisonWriter writer) {
        String accessor = inputTypeName + "." + memberName + " input";
        Shape keyShape = model.expectShape(mapShape.getKey().getTarget());
        Shape valueShape = model.expectShape(mapShape.getValue().getTarget());
        String keyToText = getToTextFunction(keyShape);
        String valueToText = getToTextFunction(valueShape);
        
        if (member.isRequired()) {
            writer.write("$L =", varName);
            writer.indent();
            writer.write("Map.toList ($L)", accessor);
            writer.write("|> List.indexed");
            writer.write("|> List.flatMap (pair -> match pair with");
            writer.indent();
            writer.write("(idx, (k, v)) ->");
            writer.indent();
            writer.write("idxText = Int.toText (idx + 1)");
            writer.write("[ (\"$L.\" ++ idxText ++ \".Key\", $L k),", paramName, keyToText);
            writer.write("  (\"$L.\" ++ idxText ++ \".Value\", $L v) ]", paramName, valueToText);
            writer.dedent();
            writer.dedent();
            writer.dedent();
        } else {
            writer.write("$L = match $L with", varName, accessor);
            writer.indent();
            writer.write("None -> []");
            writer.write("Some map ->");
            writer.indent();
            writer.write("Map.toList map");
            writer.write("|> List.indexed");
            writer.write("|> List.flatMap (pair -> match pair with");
            writer.indent();
            writer.write("(idx, (k, v)) ->");
            writer.indent();
            writer.write("idxText = Int.toText (idx + 1)");
            writer.write("[ (\"$L.\" ++ idxText ++ \".Key\", $L k),", paramName, keyToText);
            writer.write("  (\"$L.\" ++ idxText ++ \".Value\", $L v) ]", paramName, valueToText);
            writer.dedent();
            writer.dedent();
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
            String toTextFunc = getToTextFunction(nestedTarget);
            
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
     */
    private String getToTextFunction(Shape shape) {
        switch (shape.getType()) {
            case STRING:
                return "text -> text"; // Identity function
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
            case TIMESTAMP:
                return "text -> text"; // Timestamps are already text
            default:
                return "Text.fromAny"; // Fallback
        }
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
