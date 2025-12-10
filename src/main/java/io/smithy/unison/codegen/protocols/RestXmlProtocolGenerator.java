package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Protocol generator for REST-XML protocol.
 * 
 * <p>This generator handles code generation for services using the REST-XML
 * protocol, which is used by S3, CloudFront, Route 53, SES, and other XML-based
 * AWS services.
 * 
 * <h2>Protocol Characteristics</h2>
 * <ul>
 *   <li>HTTP Method: From {@code @http} trait (GET, PUT, POST, DELETE)</li>
 *   <li>URI Path: From {@code @http} trait with {@code @httpLabel} substitution</li>
 *   <li>Query Parameters: From {@code @httpQuery} members</li>
 *   <li>Headers: From {@code @httpHeader} members</li>
 *   <li>Content-Type: {@code application/xml}</li>
 *   <li>Request Body: XML encoded</li>
 *   <li>Response Body: XML decoded</li>
 *   <li>Authentication: AWS SigV4</li>
 * </ul>
 * 
 * <h2>Generated Unison Code</h2>
 * <p>Operations generate code with this pattern:
 * <pre>
 * getObject : Config -> GetObjectInput -> '{IO, Exception, Http} GetObjectOutput
 * getObject config input =
 *   let
 *     method = "GET"
 *     url = buildUrl config input
 *     headers = buildHeaders config input
 *     body = serializeBody input
 *     signedHeaders = signRequest config method url headers body
 *     response = Http.request (Http.Request.get url signedHeaders body)
 *   handleHttpResponse response
 *   parseGetObjectResponse response
 * </pre>
 * 
 * <h2>S3 Special Handling</h2>
 * <p>For S3 services, this generator uses virtual-hosted-style vs path-style 
 * URL routing based on the endpoint configuration.
 * 
 * <h2>Services Using REST-XML</h2>
 * <ul>
 *   <li>Amazon S3</li>
 *   <li>Amazon CloudFront</li>
 *   <li>Amazon Route 53</li>
 *   <li>Amazon SES</li>
 * </ul>
 * 
 * @see ProtocolGenerator
 */
public class RestXmlProtocolGenerator implements ProtocolGenerator {
    
    /** Protocol trait ID for REST-XML */
    public static final ShapeId REST_XML = ShapeId.from("aws.protocols#restXml");
    
    /**
     * Creates a REST-XML protocol generator.
     */
    public RestXmlProtocolGenerator() {
    }
    
    @Override
    public ShapeId getProtocol() {
        return REST_XML;
    }
    
    @Override
    public String getName() {
        return "restXml";
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
        return "application/xml";
    }
    
    @Override
    public void generateOperation(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        ServiceShape service = context.serviceShape();
        
        String opName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName());
        
        // Determine input and output types
        String inputType = operation.getInput()
                .map(id -> UnisonSymbolProvider.toUnisonTypeName(id.getName()))
                .orElse("()");
        String outputType = operation.getOutput()
                .map(id -> UnisonSymbolProvider.toUnisonTypeName(id.getName()))
                .orElse("()");
        
        // Get HTTP method and URI from @http trait
        String method = ProtocolUtils.getHttpMethod(operation, "GET");
        String uri = ProtocolUtils.getHttpUri(operation, "/");
        
        // Get HTTP binding members
        Optional<StructureShape> inputShape = ProtocolUtils.getInputShape(operation, model);
        List<MemberShape> httpLabelMembers = inputShape
                .map(ProtocolUtils::getLabelMembers)
                .orElse(Collections.emptyList());
        List<MemberShape> httpQueryMembers = inputShape
                .map(ProtocolUtils::getQueryMembers)
                .orElse(Collections.emptyList());
        List<MemberShape> httpHeaderInputMembers = inputShape
                .map(ProtocolUtils::getHeaderMembers)
                .orElse(Collections.emptyList());
        List<MemberShape> bodyMembers = inputShape
                .map(ProtocolUtils::getBodyMembers)
                .orElse(Collections.emptyList());
        Optional<MemberShape> payloadMember = inputShape.flatMap(ProtocolUtils::getPayloadMember);
        
        boolean isS3Service = isS3Service(service);
        boolean useS3Url = isS3Service && hasS3BucketParameter(httpLabelMembers);
        
        // Write documentation
        writer.writeDocComment(operation.getId().getName() + " operation\n\n" +
                "HTTP " + method + " " + uri + "\n" +
                "Raises exception on error, returns output directly on success.");
        
        // Write signature
        // Note: HTTP operations use {IO, Exception} abilities - there is no separate Http ability in Unison
        String signature = String.format("Config -> %s -> '{IO, Exception} %s", inputType, outputType);
        writer.writeSignature(opName, signature);
        
        // Write function definition
        writer.write("$L config input =", opName);
        writer.indent();
        
        // Build let bindings
        writer.write("let");
        writer.indent();
        
        // HTTP method
        writer.write("method = \"$L\"", method);
        
        // Build URL with path parameters
        generateUrlBuilding(uri, httpLabelMembers, useS3Url, writer);
        
        // Build query string
        generateQueryString(httpQueryMembers, writer);
        
        // Build full URL
        writer.write("fullUrl = url ++ queryString");
        
        // Build headers
        generateRequestHeaders(httpHeaderInputMembers, writer);
        
        // Build request body
        generateRequestBodyBinding(operation, model, bodyMembers, payloadMember, writer);
        
        // Sign request (placeholder)
        writer.write("signedHeaders = Aws.signRequest config method fullUrl headers body");
        
        // Make HTTP request
        writer.write("response = Http.request (Http.Request.$L fullUrl signedHeaders body)", method.toLowerCase());
        
        writer.dedent();  // end let bindings
        
        // Handle response
        writer.writeBlankLine();
        writer.write("-- Check for errors and parse response");
        writer.write("handleHttpResponse response");
        generateResponseParsing(operation, model, writer);
        
        writer.dedent();  // end function
        writer.writeBlankLine();
    }
    
    /**
     * Generates URL building code with path parameter substitution.
     */
    private void generateUrlBuilding(String uri, List<MemberShape> httpLabelMembers, 
                                      boolean useS3Url, UnisonWriter writer) {
        if (useS3Url) {
            // S3-specific URL building
            writer.write("-- S3-specific URL building with bucket routing");
            writer.write("bucket = input.bucket");
            writer.write("key = Optional.getOrElse \"\" input.key");
            writer.write("url = Aws.S3.buildUrl config bucket key");
        } else if (httpLabelMembers.isEmpty()) {
            // No path parameters
            writer.write("url = config.endpoint ++ \"$L\"", uri);
        } else {
            // Build URL with path parameter substitution
            writer.write("baseUri = \"$L\"", uri);
            
            // Generate substitution for each path parameter
            String currentUri = "baseUri";
            for (int i = 0; i < httpLabelMembers.size(); i++) {
                MemberShape member = httpLabelMembers.get(i);
                String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
                String placeholder = "{" + member.getMemberName() + "}";
                String nextUri = "uri" + (i + 1);
                
                writer.write("$LValue = input.$L", memberName, memberName);
                writer.write("$L = Text.replace \"$L\" (Aws.urlEncode $LValue) $L", 
                        nextUri, placeholder, memberName, currentUri);
                currentUri = nextUri;
            }
            
            writer.write("url = config.endpoint ++ $L", currentUri);
        }
    }
    
    /**
     * Generates query string building code.
     */
    private void generateQueryString(List<MemberShape> httpQueryMembers, UnisonWriter writer) {
        if (httpQueryMembers.isEmpty()) {
            writer.write("queryString = \"\"");
            return;
        }
        
        writer.write("-- Build query string from @httpQuery members");
        writer.write("queryParams = [");
        writer.indent();
        
        for (int i = 0; i < httpQueryMembers.size(); i++) {
            MemberShape member = httpQueryMembers.get(i);
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            HttpQueryTrait queryTrait = member.expectTrait(HttpQueryTrait.class);
            String queryName = queryTrait.getValue();
            if (queryName == null || queryName.isEmpty()) {
                queryName = member.getMemberName();
            }
            
            String comma = (i < httpQueryMembers.size() - 1) ? "," : "";
            writer.write("(\"$L\", input.$L)$L", queryName, memberName, comma);
        }
        
        writer.dedent();
        writer.write("]");
        writer.write("queryString = Aws.buildQueryString queryParams");
    }
    
    /**
     * Generates request header building code.
     */
    private void generateRequestHeaders(List<MemberShape> httpHeaderMembers, UnisonWriter writer) {
        writer.write("-- Build headers from @httpHeader members");
        
        if (httpHeaderMembers.isEmpty()) {
            writer.write("headers = [(\"Content-Type\", \"application/xml\")]");
            return;
        }
        
        writer.write("baseHeaders = [(\"Content-Type\", \"application/xml\")]");
        writer.write("customHeaders = [");
        writer.indent();
        
        for (int i = 0; i < httpHeaderMembers.size(); i++) {
            MemberShape member = httpHeaderMembers.get(i);
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            HttpHeaderTrait headerTrait = member.expectTrait(HttpHeaderTrait.class);
            String headerName = headerTrait.getValue();
            if (headerName == null || headerName.isEmpty()) {
                headerName = member.getMemberName();
            }
            
            String comma = (i < httpHeaderMembers.size() - 1) ? "," : "";
            writer.write("(\"$L\", input.$L)$L", headerName, memberName, comma);
        }
        
        writer.dedent();
        writer.write("]");
        writer.write("headers = baseHeaders ++ (List.filter (cases (_, v) -> not (Text.isEmpty v)) customHeaders)");
    }
    
    /**
     * Generates request body serialization code.
     */
    private void generateRequestBodyBinding(OperationShape operation, Model model,
                                            List<MemberShape> bodyMembers,
                                            Optional<MemberShape> payloadMember,
                                            UnisonWriter writer) {
        if (payloadMember.isPresent()) {
            // @httpPayload present - use the payload member directly
            MemberShape payload = payloadMember.get();
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(payload.getMemberName());
            Shape targetShape = model.expectShape(payload.getTarget());
            
            writer.write("-- @httpPayload: use payload member directly");
            if (targetShape.isBlobShape()) {
                writer.write("body = input.$L", memberName);
            } else if (targetShape.isStringShape()) {
                writer.write("body = Text.toUtf8 input.$L", memberName);
            } else {
                // Structure payload - serialize as XML
                writer.write("body = Aws.Xml.encode input.$L", memberName);
            }
        } else if (bodyMembers.isEmpty()) {
            // No body members - empty body
            writer.write("body = Bytes.empty");
        } else {
            // Serialize body members as XML - pass input directly
            writer.write("-- Serialize body members as XML");
            writer.write("body = Aws.Xml.encode input");
        }
    }
    
    /**
     * Generates response parsing code.
     * 
     * <p>Handles:
     * <ul>
     *   <li>@httpPayload - Extract raw body (blob/string) or decode structure</li>
     *   <li>@httpHeader - Extract response headers into output members</li>
     *   <li>@httpResponseCode - Extract HTTP status code into output member</li>
     *   <li>Body members - Decode from XML response body</li>
     * </ul>
     */
    private void generateResponseParsing(OperationShape operation, Model model, UnisonWriter writer) {
        Optional<StructureShape> outputShape = ProtocolUtils.getOutputShape(operation, model);
        
        if (!outputShape.isPresent()) {
            writer.write("()");
            return;
        }
        
        StructureShape output = outputShape.get();
        
        // Get output binding members
        Optional<MemberShape> payloadMember = ProtocolUtils.getPayloadMember(output);
        List<MemberShape> headerMembers = ProtocolUtils.getHeaderMembers(output);
        Optional<MemberShape> responseCodeMember = ProtocolUtils.getResponseCodeMember(output);
        List<MemberShape> bodyMembers = ProtocolUtils.getBodyMembers(output);
        
        // Check if we need to build a complex response with multiple sources
        boolean hasHeadersOrStatusCode = !headerMembers.isEmpty() || responseCodeMember.isPresent();
        
        if (hasHeadersOrStatusCode) {
            // Need to build response from multiple sources
            writer.write("let");
            writer.indent();
            
            // Extract response headers
            generateResponseHeaderExtraction(headerMembers, writer);
            
            // Extract response code if needed
            if (responseCodeMember.isPresent()) {
                String memberName = UnisonSymbolProvider.toUnisonFunctionName(
                        responseCodeMember.get().getMemberName());
                writer.write("$L = Http.Response.status response |> Http.Status.code", memberName);
            }
            
            // Extract body content
            if (payloadMember.isPresent()) {
                generatePayloadExtraction(payloadMember.get(), model, writer);
            } else if (!bodyMembers.isEmpty()) {
                writer.write("bodyData = Aws.Xml.decode (Http.Response.body response)");
            }
            
            writer.dedent();
            
            // Build the result record
            generateResultRecordConstruction(output, payloadMember, headerMembers, 
                    responseCodeMember, bodyMembers, writer);
        } else if (payloadMember.isPresent()) {
            // Simple payload extraction - use positional arguments
            Shape targetShape = model.expectShape(payloadMember.get().getTarget());
            String outputTypeName = UnisonSymbolProvider.toUnisonTypeName(output.getId().getName());
            
            if (targetShape.isBlobShape()) {
                writer.write("$L.$L (Http.Response.body response)", outputTypeName, outputTypeName);
            } else if (targetShape.isStringShape()) {
                writer.write("$L.$L (Bytes.toUtf8 (Http.Response.body response))", outputTypeName, outputTypeName);
            } else {
                writer.write("Aws.Xml.decode (Http.Response.body response)");
            }
        } else if (bodyMembers.isEmpty()) {
            // No body content expected - return unit or empty record
            String outputTypeName = UnisonSymbolProvider.toUnisonTypeName(
                    operation.getOutput().get().getName());
            writer.write("-- No body content expected");
            writer.write("$L.default", outputTypeName);
        } else {
            writer.write("Aws.Xml.decode (Http.Response.body response)");
        }
    }
    
    /**
     * Generates code to extract response headers.
     */
    private void generateResponseHeaderExtraction(List<MemberShape> headerMembers, UnisonWriter writer) {
        if (headerMembers.isEmpty()) {
            return;
        }
        
        writer.write("-- Extract response headers");
        for (MemberShape member : headerMembers) {
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            String headerName = ProtocolUtils.getHeaderName(member);
            writer.write("$L = Http.Response.header \"$L\" response |> Optional.getOrElse \"\"", 
                    memberName, headerName);
        }
    }
    
    /**
     * Generates code to extract the payload member.
     */
    private void generatePayloadExtraction(MemberShape payloadMember, Model model, UnisonWriter writer) {
        Shape targetShape = model.expectShape(payloadMember.getTarget());
        String memberName = UnisonSymbolProvider.toUnisonFunctionName(payloadMember.getMemberName());
        
        if (targetShape.isBlobShape()) {
            writer.write("$L = Http.Response.body response", memberName);
        } else if (targetShape.isStringShape()) {
            writer.write("$L = Bytes.toUtf8 (Http.Response.body response)", memberName);
        } else {
            writer.write("$L = Aws.Xml.decode (Http.Response.body response)", memberName);
        }
    }
    
    /**
     * Generates code to construct the result record from extracted parts.
     * 
     * <p>In Unison, records are constructed with POSITIONAL arguments, NOT named fields:
     * <pre>
     * TypeName.TypeName value1 value2 value3
     * </pre>
     * 
     * <p>Fields must be provided in the same order as defined in the type.
     */
    private void generateResultRecordConstruction(StructureShape output,
                                                   Optional<MemberShape> payloadMember,
                                                   List<MemberShape> headerMembers,
                                                   Optional<MemberShape> responseCodeMember,
                                                   List<MemberShape> bodyMembers,
                                                   UnisonWriter writer) {
        String outputTypeName = UnisonSymbolProvider.toUnisonTypeName(output.getId().getName());
        
        // Build a map of member name to value expression
        Map<String, String> memberValues = new LinkedHashMap<>();
        
        // Add header members
        for (MemberShape member : headerMembers) {
            String memberName = member.getMemberName();
            String varName = UnisonSymbolProvider.toUnisonFunctionName(memberName);
            memberValues.put(memberName, varName);
        }
        
        // Add response code member
        if (responseCodeMember.isPresent()) {
            String memberName = responseCodeMember.get().getMemberName();
            String varName = UnisonSymbolProvider.toUnisonFunctionName(memberName);
            memberValues.put(memberName, varName);
        }
        
        // Add payload member
        if (payloadMember.isPresent()) {
            String memberName = payloadMember.get().getMemberName();
            String varName = UnisonSymbolProvider.toUnisonFunctionName(memberName);
            memberValues.put(memberName, varName);
        }
        
        // Add body members (from decoded body data)
        if (!bodyMembers.isEmpty() && !payloadMember.isPresent()) {
            for (MemberShape member : bodyMembers) {
                String memberName = member.getMemberName();
                String varName = UnisonSymbolProvider.toUnisonFunctionName(memberName);
                memberValues.put(memberName, "bodyData." + varName);
            }
        }
        
        // Generate positional arguments in the order of the output structure's members
        List<String> args = new ArrayList<>();
        for (MemberShape member : output.getAllMembers().values()) {
            String memberName = member.getMemberName();
            String value = memberValues.getOrDefault(memberName, "None");  // Default to None for missing optional fields
            args.add(value);
        }
        
        // Write the constructor call with positional arguments
        if (args.isEmpty()) {
            writer.write("$L.$L", outputTypeName, outputTypeName);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(outputTypeName).append(".").append(outputTypeName);
            for (String arg : args) {
                sb.append(" ").append(arg);
            }
            writer.write("$L", sb.toString());
        }
    }
    
    @Override
    public void generateRequestSerializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        Optional<StructureShape> inputShape = ProtocolUtils.getInputShape(operation, model);
        
        if (!inputShape.isPresent()) {
            writer.writeComment("No input - empty request body");
            writer.write("body = Bytes.empty");
            return;
        }
        
        Optional<MemberShape> payloadMember = ProtocolUtils.getPayloadMember(inputShape.get());
        
        if (payloadMember.isPresent()) {
            MemberShape payload = payloadMember.get();
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(payload.getMemberName());
            Shape targetShape = model.expectShape(payload.getTarget());
            
            writer.writeComment("@httpPayload - serialize payload member");
            if (targetShape.isBlobShape()) {
                writer.write("body = input.$L", memberName);
            } else if (targetShape.isStringShape()) {
                writer.write("body = Text.toUtf8 input.$L", memberName);
            } else {
                writer.write("body = Aws.Xml.encode input.$L", memberName);
            }
        } else {
            List<MemberShape> bodyMembers = ProtocolUtils.getBodyMembers(inputShape.get());
            
            if (bodyMembers.isEmpty()) {
                writer.writeComment("No body members - empty request body");
                writer.write("body = Bytes.empty");
            } else {
                writer.writeComment("Encode body members as XML");
                writer.write("body = Aws.Xml.encode input");
            }
        }
    }
    
    @Override
    public void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        Model model = context.model();
        Optional<StructureShape> outputShape = ProtocolUtils.getOutputShape(operation, model);
        
        if (!outputShape.isPresent()) {
            writer.writeComment("No output - return unit");
            writer.write("()");
            return;
        }
        
        Optional<MemberShape> payloadMember = ProtocolUtils.getPayloadMember(outputShape.get());
        
        if (payloadMember.isPresent()) {
            MemberShape payload = payloadMember.get();
            Shape targetShape = model.expectShape(payload.getTarget());
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(payload.getMemberName());
            
            writer.writeComment("@httpPayload - extract payload member");
            if (targetShape.isBlobShape()) {
                writer.write("{ $L = Http.Response.body response }", memberName);
            } else if (targetShape.isStringShape()) {
                writer.write("{ $L = Bytes.toUtf8 (Http.Response.body response) }", memberName);
            } else {
                writer.write("Aws.Xml.decode (Http.Response.body response)");
            }
        } else {
            List<MemberShape> bodyMembers = ProtocolUtils.getBodyMembers(outputShape.get());
            
            if (bodyMembers.isEmpty()) {
                writer.writeComment("No body content expected - return empty response");
                writer.write("{}");
            } else {
                writer.writeComment("Decode XML response body");
                writer.write("Aws.Xml.decode (Http.Response.body response)");
            }
        }
    }
    
    @Override
    public void generateErrorParser(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        ServiceShape service = context.serviceShape();
        String serviceName = service.getId().getName();
        // Remove "Service" suffix if present to avoid "S3ServiceServiceError"
        if (serviceName.endsWith("Service")) {
            serviceName = serviceName.substring(0, serviceName.length() - 7);
        }
        String errorTypeName = UnisonSymbolProvider.toUnisonTypeName(serviceName) + "ServiceError";
        
        writer.writeDocComment("Parse REST-XML error response");
        writer.write("parseError : Http.Response -> $L", errorTypeName);
        writer.write("parseError response =");
        writer.indent();
        writer.write("errorBody = Bytes.toUtf8 (Http.Response.body response)");
        writer.write("-- Parse XML error: <Error><Code>...</Code><Message>...</Message></Error>");
        writer.write("code = Aws.Xml.extractElement \"Code\" errorBody");
        writer.write("message = Aws.Xml.extractElement \"Message\" errorBody");
        writer.write("$L.fromCodeAndMessage code message", errorTypeName);
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Checks if the service is an S3 service.
     */
    private boolean isS3Service(ServiceShape service) {
        String serviceName = service.getId().getName().toLowerCase();
        return serviceName.contains("s3") || 
               serviceName.equals("simplestorage") ||
               serviceName.equals("simplestorageservice");
    }
    
    /**
     * Checks if the operation has a Bucket parameter (for S3 URL routing).
     */
    private boolean hasS3BucketParameter(List<MemberShape> httpLabelMembers) {
        return httpLabelMembers.stream()
                .anyMatch(m -> m.getMemberName().equalsIgnoreCase("Bucket"));
    }
}
