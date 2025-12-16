package io.smithy.unison.codegen.protocols;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;

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
        // Use '{IO, Exception, Threads} to support real HTTP via @unison/http bridge
        String signature = String.format("%s -> %s -> '{IO, Exception, Threads} %s", configType, inputType, outputType);
        writer.writeSignature(opName, signature);
        
        // Write function definition with do block for delayed computation
        // The '{IO, Exception, Threads} return type requires a do block
        // In Unison, do blocks allow bindings directly without 'let'
        writer.write("$L config input = do", opName);
        writer.indent();
        
        // HTTP method
        writer.write("method = \"$L\"", method);
        
        // Build URL with path parameters
        generateUrlBuilding(uri, httpLabelMembers, useS3Url, inputType, writer);
        
        // Build query string
        generateQueryString(httpQueryMembers, inputType, model, clientNamespace, writer);
        
        // Build full URL
        writer.write("fullUrl = url ++ queryString");
        
        // Build headers
        generateRequestHeaders(httpHeaderInputMembers, inputType, model, clientNamespace, writer);
        
        // Build request body
        generateRequestBodyBinding(operation, model, bodyMembers, payloadMember, inputType, writer);
        
        // Sign request 
        // TODO: Implement proper SigV4 signing - for now just use headers as-is
        writer.write("signedHeaders = headers");
        
        // Make HTTP request - force the delayed computation with !
        // Uses executeRequest which can be overridden when bridge module is loaded
        // Some methods don't take a body parameter
        String methodLower = method.toLowerCase();
        if (methodLower.equals("get") || methodLower.equals("delete") || methodLower.equals("head")) {
            writer.write("response = !(executeRequest (Http.Request.$L fullUrl signedHeaders))", methodLower);
        } else {
            writer.write("response = !(executeRequest (Http.Request.$L fullUrl signedHeaders body))", methodLower);
        }
        
        // Handle response - still in scope since we're in the do block
        writer.write("-- Check for errors and parse response");
        writer.write("_ = handleHttpResponse response");
        generateResponseParsing(operation, model, clientNamespace, writer);
        
        writer.dedent();  // end function
        writer.writeBlankLine();
    }
    
    /**
     * Generates URL building code with path parameter substitution.
     */
    private void generateUrlBuilding(String uri, List<MemberShape> httpLabelMembers, 
                                      boolean useS3Url, String inputType, UnisonWriter writer) {
        if (useS3Url) {
            // S3-specific URL building with bucket routing
            // Note: Unison uses accessor functions for record fields: Config.endpoint config
            // Check which label members exist (bucket is required, key is optional for some operations)
            boolean hasBucket = httpLabelMembers.stream()
                    .anyMatch(m -> m.getMemberName().equalsIgnoreCase("bucket"));
            boolean hasKey = httpLabelMembers.stream()
                    .anyMatch(m -> m.getMemberName().equalsIgnoreCase("key"));
            
            writer.write("-- S3-specific URL building with bucket routing");
            if (hasBucket) {
                writer.write("bucket = $L.bucket input", inputType);
            } else {
                writer.write("bucket = \"\"");
            }
            if (hasKey) {
                // Check if key is optional in the input
                MemberShape keyMember = httpLabelMembers.stream()
                        .filter(m -> m.getMemberName().equalsIgnoreCase("key"))
                        .findFirst().orElse(null);
                if (keyMember != null && !keyMember.isRequired()) {
                    // Optional key - use getOrElse
                    writer.write("key = Optional.getOrElse \"\" ($L.key input)", inputType);
                } else {
                    writer.write("key = $L.key input", inputType);
                }
            } else {
                writer.write("key = \"\"");
            }
            writer.write("endpoint = Config.endpoint config");
            writer.write("usePathStyle = Config.usePathStyle config");
            writer.write("url = Aws.S3.buildUrl endpoint bucket key usePathStyle");
        } else if (httpLabelMembers.isEmpty()) {
            // No path parameters
            writer.write("url = (Config.endpoint config) ++ \"$L\"", uri);
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
                
                writer.write("$LValue = $L.$L input", memberName, inputType, memberName);
                writer.write("$L = Text.replaceAll \"$L\" (Aws.urlEncode $LValue) $L", 
                        nextUri, placeholder, memberName, currentUri);
                currentUri = nextUri;
            }
            
            writer.write("url = (Config.endpoint config) ++ $L", currentUri);
        }
    }
    
    /**
     * Generates query string building code.
     * 
     * <p>Builds query string by converting each parameter to Optional Text,
     * then using List.filterMap to extract values and build the string.
     * Handles both required and optional fields, using type-specific toText functions.
     */
    private void generateQueryString(List<MemberShape> httpQueryMembers, String inputType, 
                                      Model model, String clientNamespace, UnisonWriter writer) {
        if (httpQueryMembers.isEmpty()) {
            writer.write("queryString = \"\"");
            return;
        }
        
        writer.write("-- Build query string from @httpQuery members");
        writer.write("-- Each parameter is converted to Optional Text for homogeneous list");
        writer.write("queryParts : [Optional Text]");
        writer.write("queryParts = [");
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
            
            // Get the target shape to determine the correct toText function
            Shape targetShape = model.expectShape(member.getTarget());
            String toTextFunc = getToTextFunction(targetShape, clientNamespace);
            
            // Check if the member is required (not optional)
            boolean isRequired = member.isRequired();
            
            if (isRequired) {
                // Required field: convert value directly and wrap in Some
                writer.write("Some (\"$L=\" ++ Aws.Http.urlEncode ($L ($L.$L input)))$L", 
                        queryName, toTextFunc, inputType, memberName, comma);
            } else {
                // Optional field: map over the Optional
                writer.write("Optional.map (v -> \"$L=\" ++ Aws.Http.urlEncode ($L v)) ($L.$L input)$L", 
                        queryName, toTextFunc, inputType, memberName, comma);
            }
        }
        
        writer.dedent();
        writer.write("]");
        // Build query string by filtering and joining non-None values
        writer.write("filteredParts = List.filterMap (x -> x) queryParts");
        writer.write("queryString = if List.isEmpty filteredParts then \"\" else \"?\" ++ Text.join \"&\" filteredParts");
    }
    
    /**
     * Gets the appropriate toText function for a given shape type.
     * 
     * <p>Note: Timestamps are generated as Text in Unison (for HTTP serialization),
     * so they don't need conversion.
     * 
     * @param shape The shape to get toText function for
     * @param clientNamespace The client namespace for namespacing enum functions
     */
    private String getToTextFunction(Shape shape, String clientNamespace) {
        // Check for Smithy 2.0 enums first
        if (shape instanceof EnumShape) {
            // Enums use a namespaced function like: Aws.S3.requestPayerToText
            return UnisonSymbolProvider.toNamespacedFunctionName(
                    shape.getId().getName() + "ToText", clientNamespace);
        }
        // Check for Smithy 1.0 style enums (strings with @enum trait)
        if (shape.isStringShape() && shape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class)) {
            // Enums use a namespaced function like: Aws.S3.requestPayerToText
            return UnisonSymbolProvider.toNamespacedFunctionName(
                    shape.getId().getName() + "ToText", clientNamespace);
        }
        if (shape.isStringShape()) {
            return "";  // No conversion needed for Text
        } else if (shape.isIntegerShape()) {
            return "Int.toText";
        } else if (shape.isLongShape()) {
            return "Int.toText";
        } else if (shape.isBooleanShape()) {
            return "Boolean.toText";
        } else if (shape.isFloatShape() || shape.isDoubleShape()) {
            return "Float.toText";
        } else if (shape.isTimestampShape()) {
            // Timestamps are generated as Text in Unison for HTTP serialization
            return "";
        } else {
            // Default fallback - most query parameters are strings
            return "";
        }
    }
    
    /**
     * Generates request header building code.
     * 
     * <p>Converts all header values to Optional Text for homogeneous list types.
     */
    private void generateRequestHeaders(List<MemberShape> httpHeaderMembers, String inputType, 
                                        Model model, String clientNamespace, UnisonWriter writer) {
        writer.write("-- Build headers from @httpHeader members");
        
        if (httpHeaderMembers.isEmpty()) {
            writer.write("headers = [(\"Content-Type\", \"application/xml\")]");
            return;
        }
        
        writer.write("baseHeaders = [(\"Content-Type\", \"application/xml\")]");
        writer.write("-- Each header is converted to (Text, Optional Text) for homogeneous list");
        writer.write("customHeaderParts : [(Text, Optional Text)]");
        writer.write("customHeaderParts = [");
        writer.indent();
        
        for (int i = 0; i < httpHeaderMembers.size(); i++) {
            MemberShape member = httpHeaderMembers.get(i);
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            HttpHeaderTrait headerTrait = member.expectTrait(HttpHeaderTrait.class);
            String headerName = headerTrait.getValue();
            if (headerName == null || headerName.isEmpty()) {
                headerName = member.getMemberName();
            }
            
            // Get the target shape to determine the correct toText function
            Shape targetShape = model.expectShape(member.getTarget());
            
            String comma = (i < httpHeaderMembers.size() - 1) ? "," : "";
            
            // Check if the member is required
            boolean isRequired = member.isRequired();
            
            // Check if this is a list type - needs special handling
            if (targetShape.isListShape()) {
                ListShape listShape = targetShape.asListShape().get();
                Shape elementShape = model.expectShape(listShape.getMember().getTarget());
                String elementToText = getToTextFunction(elementShape, clientNamespace);
                
                // Lists are serialized as comma-separated values
                // Text.join "," (List.map ElementType.toText list)
                if (isRequired) {
                    if (elementToText.isEmpty()) {
                        // Elements are already Text
                        writer.write("(\"$L\", Some (Text.join \",\" ($L.$L input)))$L", 
                                headerName, inputType, memberName, comma);
                    } else {
                        writer.write("(\"$L\", Some (Text.join \",\" (List.map $L ($L.$L input))))$L", 
                                headerName, elementToText, inputType, memberName, comma);
                    }
                } else {
                    // Optional list
                    if (elementToText.isEmpty()) {
                        writer.write("(\"$L\", Optional.map (lst -> Text.join \",\" lst) ($L.$L input))$L", 
                                headerName, inputType, memberName, comma);
                    } else {
                        writer.write("(\"$L\", Optional.map (lst -> Text.join \",\" (List.map $L lst)) ($L.$L input))$L", 
                                headerName, elementToText, inputType, memberName, comma);
                    }
                }
            } else {
                // Non-list types - original logic
                String toTextFunc = getToTextFunction(targetShape, clientNamespace);
                
                if (isRequired) {
                    // Required field - wrap in Some and convert to Text
                    if (toTextFunc.isEmpty()) {
                        writer.write("(\"$L\", Some ($L.$L input))$L", headerName, inputType, memberName, comma);
                    } else {
                        writer.write("(\"$L\", Some ($L ($L.$L input)))$L", headerName, toTextFunc, inputType, memberName, comma);
                    }
                } else {
                    // Optional field - map to Text
                    if (toTextFunc.isEmpty()) {
                        // Already Optional Text, just use it
                        writer.write("(\"$L\", $L.$L input)$L", headerName, inputType, memberName, comma);
                    } else {
                        // Convert using map
                        writer.write("(\"$L\", Optional.map $L ($L.$L input))$L", headerName, toTextFunc, inputType, memberName, comma);
                    }
                }
            }
        }
        
        writer.dedent();
        writer.write("]");
        // Build headers by extracting Some values
        // Use simple helper to convert (Text, Optional Text) to Optional (Text, Text)
        writer.write("toHeader : (Text, Optional Text) -> Optional (Text, Text)");
        writer.write("toHeader pair = match pair with");
        writer.indent();
        writer.write("(name, Some v) -> if Text.isEmpty v then None else Some (name, v)");
        writer.write("(_, None) -> None");
        writer.dedent();
        writer.write("filteredHeaders = List.filterMap toHeader customHeaderParts");
        writer.write("headers = baseHeaders ++ filteredHeaders");
    }
    
    /**
     * Generates request body serialization code.
     */
    private void generateRequestBodyBinding(OperationShape operation, Model model,
                                            List<MemberShape> bodyMembers,
                                            Optional<MemberShape> payloadMember,
                                            String inputType,
                                            UnisonWriter writer) {
        if (payloadMember.isPresent()) {
            // @httpPayload present - use the payload member directly
            MemberShape payload = payloadMember.get();
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(payload.getMemberName());
            Shape targetShape = model.expectShape(payload.getTarget());
            
            writer.write("-- @httpPayload: use payload member directly");
            if (targetShape.isBlobShape()) {
                writer.write("body = $L.$L input", inputType, memberName);
            } else if (targetShape.isStringShape()) {
                writer.write("body = Text.toUtf8 ($L.$L input)", inputType, memberName);
            } else {
                // Structure payload - serialize as XML
                writer.write("body = Aws.Xml.encode ($L.$L input)", inputType, memberName);
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
    private void generateResponseParsing(OperationShape operation, Model model, 
            String clientNamespace, UnisonWriter writer) {
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
            // In do blocks, bindings are scoped to the rest of the block (no need for 'let')
            
            // Extract response headers
            generateResponseHeaderExtraction(headerMembers, model, clientNamespace, writer);
            
            // Extract response code if needed
            if (responseCodeMember.isPresent()) {
                // Add 'Val' suffix to avoid name clash with accessor functions
                String memberName = UnisonSymbolProvider.toUnisonFunctionName(
                        responseCodeMember.get().getMemberName()) + "Val";
                writer.write("$L = Response.statusCode response", memberName);
            }
            
            // Extract body content
            if (payloadMember.isPresent()) {
                generatePayloadExtraction(payloadMember.get(), model, writer);
            } else if (!bodyMembers.isEmpty()) {
                // Generate XML parsing for body members
                writer.write("xmlText = fromUtf8 (Response.body response)");
                for (MemberShape member : bodyMembers) {
                    String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
                    String varName = memberName + "Val";
                    String xmlElementName = getXmlElementName(member);
                    Shape targetShape = model.expectShape(member.getTarget());
                    generateXmlFieldExtraction(varName, xmlElementName, targetShape, member, model, clientNamespace, writer);
                }
            }
            
            // Build the result record (final expression of the do block)
            generateResultRecordConstruction(output, payloadMember, headerMembers, 
                    responseCodeMember, bodyMembers, clientNamespace, writer);
        } else if (payloadMember.isPresent()) {
            // Simple payload extraction - use positional arguments
            MemberShape payload = payloadMember.get();
            Shape targetShape = model.expectShape(payload.getTarget());
            String outputTypeName = UnisonSymbolProvider.toNamespacedTypeName(
                    output.getId().getName(), clientNamespace);
            boolean isOptional = !payload.isRequired();
            
            if (targetShape.isBlobShape()) {
                if (isOptional) {
                    writer.write("$L.$L (Some (Response.body response))", outputTypeName, outputTypeName);
                } else {
                    writer.write("$L.$L (Response.body response)", outputTypeName, outputTypeName);
                }
            } else if (targetShape.isStringShape()) {
                if (isOptional) {
                    writer.write("$L.$L (Some (Aws.Http.bytesToText (Response.body response)))", outputTypeName, outputTypeName);
                } else {
                    writer.write("$L.$L (Aws.Http.bytesToText (Response.body response))", outputTypeName, outputTypeName);
                }
            } else {
                // Structure payload - generate XML parsing
                generateXmlResponseParsing(output, bodyMembers, model, clientNamespace, writer);
            }
        } else if (bodyMembers.isEmpty()) {
            // No body content expected - return empty record using constructor
            // For empty records like "type X = X", we construct with just "X"
            String outputTypeName = UnisonSymbolProvider.toNamespacedTypeName(
                    operation.getOutput().get().getName(), clientNamespace);
            writer.write("-- No body content expected");
            writer.write("$L.$L", outputTypeName, outputTypeName);
        } else {
            // Has body members - generate XML parsing
            generateXmlResponseParsing(output, bodyMembers, model, clientNamespace, writer);
        }
    }
    
    /**
     * Generates code to extract response headers.
     * 
     * <p>Handles different target types:
     * <ul>
     *   <li>Text - direct extraction from header</li>
     *   <li>Boolean - parse "true"/"false" text to Boolean</li>
     *   <li>Integer/Long - parse text to number</li>
     *   <li>Enum types - convert Text to enum using fromText function</li>
     * </ul>
     */
    private void generateResponseHeaderExtraction(List<MemberShape> headerMembers, Model model, 
            String clientNamespace, UnisonWriter writer) {
        if (headerMembers.isEmpty()) {
            return;
        }
        
        writer.write("-- Extract response headers");
        for (MemberShape member : headerMembers) {
            // Add 'Val' suffix to avoid name clash with accessor functions
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName()) + "Val";
            String headerName = ProtocolUtils.getHeaderName(member);
            Shape targetShape = model.expectShape(member.getTarget());
            
            // Check if target is an enum type (need to convert Text -> EnumType)
            boolean isEnumType = targetShape instanceof EnumShape || 
                    (targetShape.isStringShape() && targetShape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class));
            
            if (isEnumType) {
                // Enum type - need to convert Optional Text to Optional EnumType
                // Using pattern matching per UNISON_LANGUAGE_SPEC.md
                String enumFromText = UnisonSymbolProvider.toNamespacedFunctionName(
                        targetShape.getId().getName() + "FromText", clientNamespace);
                
                writer.write("$L = match Response.getHeader \"$L\" response with", memberName, headerName);
                writer.indent();
                writer.write("Some text -> $L text", enumFromText);
                writer.write("None -> None");
                writer.dedent();
            } else if (targetShape.isBooleanShape()) {
                // Boolean type - parse "true"/"false" text
                writer.write("$L = match Response.getHeader \"$L\" response with", memberName, headerName);
                writer.indent();
                writer.write("Some \"true\" -> Some true");
                writer.write("Some \"false\" -> Some false");
                writer.write("_ -> None");
                writer.dedent();
            } else if (targetShape.isIntegerShape() || targetShape.isLongShape()) {
                // Integer type - parse text to number using Int.fromText
                writer.write("$L = match Response.getHeader \"$L\" response with", memberName, headerName);
                writer.indent();
                writer.write("Some text -> Int.fromText text");
                writer.write("None -> None");
                writer.dedent();
            } else if (member.isRequired()) {
                // Required Text field - use getOrElse to extract Text
                writer.write("$L = Optional.getOrElse \"\" (Response.getHeader \"$L\" response)", 
                        memberName, headerName);
            } else {
                // Optional Text field - return Optional Text directly
                writer.write("$L = Response.getHeader \"$L\" response", 
                        memberName, headerName);
            }
        }
    }
    
    /**
     * Generates code to extract the payload member.
     */
    private void generatePayloadExtraction(MemberShape payloadMember, Model model, UnisonWriter writer) {
        Shape targetShape = model.expectShape(payloadMember.getTarget());
        // Add 'Val' suffix to avoid name clash with accessor functions
        String memberName = UnisonSymbolProvider.toUnisonFunctionName(payloadMember.getMemberName()) + "Val";
        
        if (targetShape.isBlobShape()) {
            writer.write("$L = Response.body response", memberName);
        } else if (targetShape.isStringShape()) {
            writer.write("$L = Aws.Http.bytesToText (Response.body response)", memberName);
        } else {
            writer.write("$L = Aws.Xml.decode (Response.body response)", memberName);
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
                                                   String clientNamespace,
                                                   UnisonWriter writer) {
        String outputTypeName = UnisonSymbolProvider.toNamespacedTypeName(
                output.getId().getName(), clientNamespace);
        
        // Build a map of member name to value expression
        // Use 'Val' suffix on local variables to avoid name clash with accessor functions
        Map<String, String> memberValues = new LinkedHashMap<>();
        
        // Add header members
        for (MemberShape member : headerMembers) {
            String memberName = member.getMemberName();
            String varName = UnisonSymbolProvider.toUnisonFunctionName(memberName) + "Val";
            memberValues.put(memberName, varName);
        }
        
        // Add response code member
        if (responseCodeMember.isPresent()) {
            String memberName = responseCodeMember.get().getMemberName();
            String varName = UnisonSymbolProvider.toUnisonFunctionName(memberName) + "Val";
            memberValues.put(memberName, varName);
        }
        
        // Add payload member
        if (payloadMember.isPresent()) {
            String memberName = payloadMember.get().getMemberName();
            String varName = UnisonSymbolProvider.toUnisonFunctionName(memberName) + "Val";
            memberValues.put(memberName, varName);
        }
        
        // Add body members (from XML extraction)
        // Uses the extracted *Val variables defined in generateXmlFieldExtraction
        if (!bodyMembers.isEmpty() && !payloadMember.isPresent()) {
            for (MemberShape member : bodyMembers) {
                String memberName = member.getMemberName();
                String varName = UnisonSymbolProvider.toUnisonFunctionName(memberName) + "Val";
                memberValues.put(memberName, varName);
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
        String clientNamespace = context.settings().getClientNamespace();
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
                writer.write("{ $L = Response.body response }", memberName);
            } else if (targetShape.isStringShape()) {
                writer.write("{ $L = Aws.Http.bytesToText (Response.body response) }", memberName);
            } else {
                // Structure payload - generate inline XML parsing
                writer.writeComment("Structure payload - parse XML");
                generateXmlResponseParsing(outputShape.get(), ProtocolUtils.getBodyMembers(outputShape.get()), model, clientNamespace, writer);
            }
        } else {
            List<MemberShape> bodyMembers = ProtocolUtils.getBodyMembers(outputShape.get());
            
            if (bodyMembers.isEmpty()) {
                writer.writeComment("No body content expected - return empty response");
                writer.write("{}");
            } else {
                writer.writeComment("Decode XML response body");
                generateXmlResponseParsing(outputShape.get(), bodyMembers, model, clientNamespace, writer);
            }
        }
    }
    
    @Override
    public void generateErrorParser(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        ServiceShape service = context.serviceShape();
        String clientNamespace = context.settings().getClientNamespace();
        String serviceName = service.getId().getName();
        // Remove "Service" suffix if present to avoid "S3ServiceServiceError"
        if (serviceName.endsWith("Service")) {
            serviceName = serviceName.substring(0, serviceName.length() - 7);
        }
        String errorTypeName = UnisonSymbolProvider.toNamespacedTypeName(
                serviceName + "ServiceError", clientNamespace);
        
        writer.writeDocComment("Parse REST-XML error response");
        writer.write("parseError : Response -> $L", errorTypeName);
        writer.write("parseError response =");
        writer.indent();
        writer.write("errorBody = Aws.Http.bytesToText (Response.body response)");
        writer.write("-- Parse XML error: <Error><Code>...</Code><Message>...</Message></Error>");
        writer.write("code = Aws.Xml.extractElement \"Code\" errorBody");
        writer.write("message = Aws.Xml.extractElement \"Message\" errorBody");
        writer.write("$L.fromCodeAndMessage code message", errorTypeName);
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates XML response parsing code that extracts fields from XML and constructs
     * the output record.
     * 
     * <p>This generates code like:
     * <pre>
     * xmlText = fromUtf8 (Response.body response)
     * field1Val = Aws.Xml.extractElementOpt "Field1" xmlText
     * field2Val = Aws.Xml.extractInt "Field2" xmlText
     * OutputType.OutputType field1Val field2Val ...
     * </pre>
     */
    private void generateXmlResponseParsing(StructureShape output, List<MemberShape> bodyMembers, 
            Model model, String clientNamespace, UnisonWriter writer) {
        String outputTypeName = UnisonSymbolProvider.toNamespacedTypeName(
                output.getId().getName(), clientNamespace);
        
        // Convert response body to text
        writer.write("xmlText = fromUtf8 (Response.body response)");
        
        // Get all members in order (important for constructor)
        List<MemberShape> allMembers = new ArrayList<>(output.getAllMembers().values());
        
        // Extract each body member from XML
        for (MemberShape member : allMembers) {
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            String varName = memberName + "Val";
            Shape targetShape = model.expectShape(member.getTarget());
            
            // Get the XML element name - use member name by default
            String xmlElementName = getXmlElementName(member);
            
            generateXmlFieldExtraction(varName, xmlElementName, targetShape, member, model, clientNamespace, writer);
        }
        
        // Construct the output record with extracted values
        writer.write("$L.$L", outputTypeName, outputTypeName);
        writer.indent();
        for (int i = 0; i < allMembers.size(); i++) {
            MemberShape member = allMembers.get(i);
            String memberName = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
            String varName = memberName + "Val";
            writer.write(varName);
        }
        writer.dedent();
    }
    
    /**
     * Generates code to extract a single field from XML.
     */
    private void generateXmlFieldExtraction(String varName, String xmlElementName, 
            Shape targetShape, MemberShape member, Model model, String clientNamespace, UnisonWriter writer) {
        boolean isOptional = !member.isRequired();
        
        // Check enum types FIRST (before string check, since enums may inherit from string)
        if (targetShape instanceof EnumShape || 
                (targetShape.isStringShape() && targetShape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
            // Enum type - extract text and convert using enumFromText
            String enumFromText = UnisonSymbolProvider.toNamespacedFunctionName(
                    targetShape.getId().getName() + "FromText", clientNamespace);
            String textVarName = varName + "Text";
            writer.write("$L = Aws.Xml.extractElementOpt \"$L\" xmlText", textVarName, xmlElementName);
            writer.write("$L = Optional.flatMap $L $L", varName, enumFromText, textVarName);
        } else if (targetShape.isStringShape()) {
            // Regular string type
            if (isOptional) {
                writer.write("$L = Aws.Xml.extractElementOpt \"$L\" xmlText", varName, xmlElementName);
            } else {
                writer.write("$L = Aws.Xml.extractElement \"$L\" xmlText", varName, xmlElementName);
            }
        } else if (targetShape.isIntegerShape() || targetShape.isLongShape()) {
            // Int extraction returns Optional Int
            writer.write("$L = Aws.Xml.extractInt \"$L\" xmlText", varName, xmlElementName);
        } else if (targetShape.isBooleanShape()) {
            // Bool extraction returns Optional Boolean
            writer.write("$L = Aws.Xml.extractBool \"$L\" xmlText", varName, xmlElementName);
        } else if (targetShape.isBlobShape()) {
            // Blob from base64 encoded text
            if (isOptional) {
                writer.write("$L = Optional.map base64.decode (Aws.Xml.extractElementOpt \"$L\" xmlText)", 
                        varName, xmlElementName);
            } else {
                String textVar = varName + "Text";
                writer.write("$L = Aws.Xml.extractElement \"$L\" xmlText", textVar, xmlElementName);
                writer.write("$L = base64.decode $L", varName, textVar);
            }
        } else if (targetShape instanceof ListShape) {
            // List - extract all elements with the member name
            ListShape listShape = (ListShape) targetShape;
            Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            String itemElementName = getXmlElementName(listShape.getMember());
            String itemsVarName = varName + "Items";
            
            if (memberTarget.isStringShape()) {
                // List of strings
                writer.write("$L = Aws.Xml.extractAll \"$L\" xmlText", itemsVarName, itemElementName);
                if (isOptional) {
                    writer.write("$L = if List.isEmpty $L then None else Some $L", 
                            varName, itemsVarName, itemsVarName);
                } else {
                    writer.write("$L = $L", varName, itemsVarName);
                }
            } else if (memberTarget instanceof StructureShape) {
                // List of structures - use parseListFromXml with inline parser
                StructureShape structShape = (StructureShape) memberTarget;
                String baseTypeName = UnisonSymbolProvider.toUnisonTypeName(structShape.getId().getName());
                String parserName = UnisonSymbolProvider.toNamespacedFunctionName(
                        "parse" + baseTypeName + "FromXml", clientNamespace);
                
                // Generate inline parsing that uses the structure parser
                if (isOptional) {
                    writer.write("$L = Aws.Xml.parseOptionalWrappedListFromXml \"$L\" \"$L\" $L xmlText",
                            varName, xmlElementName, itemElementName, parserName);
                } else {
                    writer.write("$L = Aws.Xml.parseListFromXml \"$L\" $L xmlText",
                            varName, itemElementName, parserName);
                }
            } else if (memberTarget instanceof EnumShape || 
                    (memberTarget.isStringShape() && memberTarget.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class))) {
                // List of enums - extract text and map using fromText function
                String enumFromText = UnisonSymbolProvider.toNamespacedFunctionName(
                        memberTarget.getId().getName() + "FromText", clientNamespace);
                writer.write("$L = Aws.Xml.extractAll \"$L\" xmlText", itemsVarName, itemElementName);
                writer.write("$L = List.filterMap $L $L", varName + "Parsed", enumFromText, itemsVarName);
                if (isOptional) {
                    writer.write("$L = if List.isEmpty $L then None else Some $L", 
                            varName, varName + "Parsed", varName + "Parsed");
                } else {
                    writer.write("$L = $L", varName, varName + "Parsed");
                }
            } else {
                // Fallback for other list types (integers, etc.)
                if (memberTarget.isIntegerShape() || memberTarget.isLongShape()) {
                    writer.write("$L = Aws.Xml.extractAll \"$L\" xmlText", itemsVarName, itemElementName);
                    writer.write("$L = List.filterMap Int.fromText $L", varName + "Parsed", itemsVarName);
                    if (isOptional) {
                        writer.write("$L = if List.isEmpty $L then None else Some $L", 
                                varName, varName + "Parsed", varName + "Parsed");
                    } else {
                        writer.write("$L = $L", varName, varName + "Parsed");
                    }
                } else {
                    // Unknown list member type
                    if (isOptional) {
                        writer.write("$L = None -- unsupported list member type: $L", varName, memberTarget.getType());
                    } else {
                        writer.write("$L = [] -- unsupported list member type: $L", varName, memberTarget.getType());
                    }
                }
            }
        } else if (targetShape instanceof StructureShape) {
            // Nested structure - use parseNestedFromXml with structure parser
            StructureShape structShape = (StructureShape) targetShape;
            String baseTypeName = UnisonSymbolProvider.toUnisonTypeName(structShape.getId().getName());
            String parserName = UnisonSymbolProvider.toNamespacedFunctionName(
                    "parse" + baseTypeName + "FromXml", clientNamespace);
            
            if (isOptional) {
                writer.write("$L = Aws.Xml.parseNestedFromXml \"$L\" $L xmlText", varName, xmlElementName, parserName);
            } else {
                // Required nested structure - parse and extract, bug if missing
                String optVarName = varName + "Opt";
                writer.write("$L = Aws.Xml.parseNestedFromXml \"$L\" $L xmlText", optVarName, xmlElementName, parserName);
                writer.write("$L = Optional.getOrElse (bug \"Required nested structure '$L' not found\") $L", 
                        varName, xmlElementName, optVarName);
            }
        } else {
            // Unknown type - use None as placeholder
            writer.write("$L = None -- TODO: parse $L", varName, targetShape.getType());
        }
    }
    
    /**
     * Gets the XML element name for a member.
     * Uses @xmlName trait if present, otherwise uses the member name.
     */
    private String getXmlElementName(MemberShape member) {
        // Check for @xmlName trait
        Optional<software.amazon.smithy.model.traits.XmlNameTrait> xmlNameTrait = 
                member.getTrait(software.amazon.smithy.model.traits.XmlNameTrait.class);
        if (xmlNameTrait.isPresent()) {
            return xmlNameTrait.get().getValue();
        }
        // Use member name with first letter capitalized (AWS XML convention)
        String name = member.getMemberName();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
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
