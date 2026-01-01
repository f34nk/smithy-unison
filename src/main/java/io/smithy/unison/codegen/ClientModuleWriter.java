package io.smithy.unison.codegen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import io.smithy.unison.codegen.aws.AwsProtocol;
import io.smithy.unison.codegen.aws.AwsProtocolDetector;
import io.smithy.unison.codegen.generators.EnumGenerator;
import io.smithy.unison.codegen.generators.PaginationGenerator;
import io.smithy.unison.codegen.generators.StructureGenerator;
import io.smithy.unison.codegen.generators.UnionGenerator;
import io.smithy.unison.codegen.protocols.AwsJsonProtocolGenerator;
import io.smithy.unison.codegen.protocols.ProtocolGenerator;
import io.smithy.unison.codegen.protocols.ProtocolGeneratorFactory;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.ErrorTrait;

/**
 * Core client module code generation logic for Unison.
 * 
 * <p>This class generates Unison client modules for AWS services, including:
 * <ul>
 *   <li>Configuration types (Config, Credentials)</li>
 *   <li>Operation functions with full HTTP request/response handling</li>
 *   <li>Error handling using Unison's exception ability</li>
 * </ul>
 * 
 * <p>For services with supported protocols (REST-XML, etc.), operations are
 * generated with complete implementation including URL building, header
 * construction, request signing, and response parsing.
 */
public final class ClientModuleWriter {
    
    private static final Logger LOGGER = Logger.getLogger(ClientModuleWriter.class.getName());
    
    private final ServiceShape service;
    private final Model model;
    private final String namespace;
    private final String clientNamespace;
    private final FileManifest fileManifest;
    private final String outputDir;
    private final UnisonContext context;
    
    /**
     * Creates a new client module writer.
     */
    public ClientModuleWriter(ServiceShape service, Model model, String namespace,
                               FileManifest fileManifest, String outputDir,
                               UnisonContext context) {
        this.service = service;
        this.model = model;
        this.namespace = namespace;
        this.clientNamespace = context.settings().getClientNamespace();
        this.fileManifest = fileManifest;
        this.outputDir = outputDir;
        this.context = context;
    }
    
    /**
     * Gets the client namespace for prefixing types and functions.
     * 
     * @return The client namespace (e.g., "Aws.S3")
     */
    public String getClientNamespace() {
        return clientNamespace;
    }
    
    /**
     * Converts a type name to a namespaced type name.
     * 
     * @param name The base type name
     * @return The namespaced type name (e.g., "Aws.S3.Config")
     */
    private String getNamespacedTypeName(String name) {
        return UnisonSymbolProvider.toNamespacedTypeName(name, clientNamespace);
    }
    
    /**
     * Converts a function name to a namespaced function name.
     * 
     * @param name The base function name
     * @return The namespaced function name (e.g., "Aws.S3.createBucket")
     */
    private String getNamespacedFunctionName(String name) {
        return UnisonSymbolProvider.toNamespacedFunctionName(name, clientNamespace);
    }
    
    /**
     * Creates a writer using UnisonContext.
     */
    public static ClientModuleWriter fromContext(UnisonContext context) {
        ServiceShape service = context.model().expectShape(
                context.settings().service(), ServiceShape.class);
        String namespace = context.settings().namespace();
        if (namespace == null || namespace.isEmpty()) {
            namespace = UnisonSymbolProvider.toUnisonFunctionName(service.getId().getName());
        }
        return new ClientModuleWriter(
                service,
                context.model(),
                namespace,
                context.fileManifest(),
                context.settings().outputDir(),
                context
        );
    }
    
    /**
     * Generates the complete client module.
     * 
     * <p>Detects the service protocol and uses the appropriate protocol
     * generator to create operation implementations.
     */
    public void generate() throws IOException {
        LOGGER.info("Generating Unison client for service: " + service.getId());
        
        UnisonWriter writer = new UnisonWriter(namespace);
        
        // Detect protocol
        AwsProtocol protocol = AwsProtocolDetector.detectProtocol(service);
        Optional<ProtocolGenerator> protocolGenerator = ProtocolGeneratorFactory.getGenerator(protocol);
        boolean useProtocolGenerator = protocolGenerator.isPresent() 
                && ProtocolGeneratorFactory.isFullyImplemented(protocol);
        
        // Check if this is an AWS service
        RuntimeModuleCopier copier = new RuntimeModuleCopier(fileManifest, outputDir);
        boolean isAws = copier.isAwsService(service, protocol);
        
        // Write header comment
        writer.writeComment("Generated Unison client for " + service.getId().getName());
        if (useProtocolGenerator) {
            writer.writeComment("Protocol: " + protocol.name());
        } else if (isAws) {
            writer.writeComment("Protocol " + protocol.name() + " - operations are stubs");
        }
        writer.writeBlankLine();
        
        // Write Config type (conditional based on service type)
        if (isAws) {
            generateAwsConfigTypes(writer);
        } else {
            generateGenericConfigType(writer);
        }
        
        // Generate model types (structures, enums, errors) referenced by operations
        // Types are always needed - protocol generators use these types but don't generate them
        generateModelTypes(writer, protocol);
        
        // Generate error parser for protocol (if using protocol generator)
        if (useProtocolGenerator && protocolGenerator.isPresent()) {
            ProtocolGenerator gen = protocolGenerator.get();
            
            // Generate error parser (once per service)
            ShapeId firstOpId = service.getOperations().iterator().next();
            OperationShape firstOp = model.expectShape(firstOpId, OperationShape.class);
            gen.generateErrorParser(firstOp, writer, context);
            
            // For AWS JSON protocols, generate standalone serializer/deserializer functions
            // (REST protocols do inline serialization within each operation)
            if (protocol == AwsProtocol.AWS_JSON_1_0 || protocol == AwsProtocol.AWS_JSON_1_1) {
                writer.writeComment("=== Request/Response Serializers ===");
                writer.writeBlankLine();
                
                for (ShapeId opId : service.getOperations()) {
                    OperationShape operation = model.expectShape(opId, OperationShape.class);
                    gen.generateRequestSerializer(operation, writer, context);
                    gen.generateResponseDeserializer(operation, writer, context);
                }
                
                writer.writeBlankLine();
            }
        }
        
        // Generate operations
        for (ShapeId opId : service.getOperations()) {
            OperationShape operation = model.expectShape(opId, OperationShape.class);
            
            if (useProtocolGenerator && protocolGenerator.isPresent()) {
                // Use protocol generator for full implementation
                protocolGenerator.get().generateOperation(operation, writer, context);
            } else {
                // Fall back to stub generation
                generateOperationStub(operation, writer);
            }
        }
        
        // Generate pagination helpers
        PaginationGenerator paginationGenerator = new PaginationGenerator(clientNamespace);
        paginationGenerator.generate(service, model, writer);
        
        // Write to file
        writeToFile(writer);
        
        // Copy runtime modules (only for AWS services)
        copyRuntimeModules(protocol);
        
        if (useProtocolGenerator) {
            LOGGER.info("Client generation completed with full operation implementations");
        } else {
            LOGGER.info("Client generation completed (stub operations)");
        }
    }
    
    /**
     * Generates AWS-specific Config and Credentials types.
     * 
     * <p>Used for AWS services that require authentication and S3-style configuration.
     */
    private void generateAwsConfigTypes(UnisonWriter writer) {
        String configType = getNamespacedTypeName("Config");
        String credentialsType = getNamespacedTypeName("Credentials");
        
        writer.writeDocComment("Configuration for the " + service.getId().getName() + " client");
        writer.write("type $L = {", configType);
        writer.indent();
        writer.write("endpoint : Text,");
        writer.write("region : Text,");
        writer.write("credentials : $L,", credentialsType);
        writer.write("usePathStyle : Boolean");
        writer.dedent();
        writer.write("}");
        writer.writeBlankLine();
        
        writer.write("type $L = {", credentialsType);
        writer.indent();
        writer.write("accessKeyId : Text,");
        writer.write("secretAccessKey : Text,");
        writer.write("sessionToken : Optional Text");
        writer.dedent();
        writer.write("}");
        writer.writeBlankLine();
    }
    
    /**
     * Generates a generic Config type for non-AWS services.
     * 
     * <p>Used for services that don't require AWS authentication.
     */
    private void generateGenericConfigType(UnisonWriter writer) {
        String configType = getNamespacedTypeName("Config");
        
        writer.writeDocComment("Configuration for the " + service.getId().getName() + " client");
        writer.write("type $L = {", configType);
        writer.indent();
        writer.write("endpoint : Text,");
        writer.write("headers : [(Text, Text)]");
        writer.dedent();
        writer.write("}");
        writer.writeBlankLine();
    }
    
    /**
     * Generates Unison types for all structures referenced by service operations.
     * 
     * <p>Collects all shapes referenced by operations (input, output, errors, nested)
     * and generates Unison record types for structures and sum types for enums.
     */
    private void generateModelTypes(UnisonWriter writer, AwsProtocol protocol) {
        Set<ShapeId> generatedTypes = new HashSet<>();
        Set<StructureShape> structures = new HashSet<>();
        Set<StructureShape> errors = new HashSet<>();
        Set<Shape> enums = new HashSet<>();
        
        // Collect all shapes referenced by operations
        for (ShapeId opId : service.getOperations()) {
            OperationShape operation = model.expectShape(opId, OperationShape.class);
            
            // Collect input shape
            operation.getInput().ifPresent(inputId -> {
                collectReferencedShapes(inputId, structures, errors, enums, generatedTypes);
            });
            
            // Collect output shape
            operation.getOutput().ifPresent(outputId -> {
                collectReferencedShapes(outputId, structures, errors, enums, generatedTypes);
            });
            
            // Collect error shapes
            for (ShapeId errorId : operation.getErrors()) {
                collectReferencedShapes(errorId, structures, errors, enums, generatedTypes);
            }
        }
        
        // Generate enums first (they may be referenced by structures)
        if (!enums.isEmpty()) {
            writer.writeComment("=== Enums ===");
            writer.writeBlankLine();
            
            for (Shape enumShape : enums) {
                if (enumShape instanceof EnumShape) {
                    EnumGenerator generator = new EnumGenerator((EnumShape) enumShape, model, clientNamespace);
                    generator.generate(writer);
                    writer.writeBlankLine();
                } else if (enumShape instanceof StringShape && enumShape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class)) {
                    EnumGenerator generator = new EnumGenerator((StringShape) enumShape, context);
                    generator.generate(writer);
                    writer.writeBlankLine();
                } else if (enumShape instanceof UnionShape) {
                    // Check if this is DynamoDB AttributeValue - skip generation, use runtime type
                    UnionShape unionShape = (UnionShape) enumShape;
                    if (!isDynamoDBAttributeValue(unionShape)) {
                        // Generate union types as sum types
                        UnionGenerator generator = new UnionGenerator(unionShape, model, clientNamespace);
                        generator.generate(writer);
                        writer.writeBlankLine();
                    } else {
                        LOGGER.fine("Skipping AttributeValue union generation - using runtime type Aws.Json.AttributeValue");
                    }
                }
            }
        }
        
        // Generate structures (non-errors)
        if (!structures.isEmpty()) {
            writer.writeComment("=== Types ===");
            writer.writeBlankLine();
            
            for (StructureShape structure : structures) {
                StructureGenerator generator = new StructureGenerator(
                    structure, model, context.symbolProvider(), clientNamespace);
                generator.generate(writer);
                writer.writeBlankLine();
            }
            
            // Generate XML parsers for structures (used by response parsing)
            // Only generate for XML-based protocols (REST-XML, AWS Query, EC2 Query)
            if (protocol.isXml()) {
                generateXmlParsers(structures, writer);
            }
            
            // Generate JSON serializers for nested structures (used by request serialization)
            // Only generate for AWS JSON protocols
            if (protocol == AwsProtocol.AWS_JSON_1_0 || protocol == AwsProtocol.AWS_JSON_1_1) {
                generateJsonSerializers(structures, writer);
            }
        }
        
        // Generate error types
        if (!errors.isEmpty()) {
            writer.writeComment("=== Errors ===");
            writer.writeBlankLine();
            
            for (StructureShape error : errors) {
                StructureGenerator generator = new StructureGenerator(
                    error, model, context.symbolProvider(), clientNamespace);
                generator.generate(writer);
                
                // Generate toFailure function for errors
                generateErrorToFailure(error, writer);
                writer.writeBlankLine();
            }
            
            // Generate service-level error union type (for all AWS protocols that need error parsing)
            if (protocol == AwsProtocol.REST_XML || protocol == AwsProtocol.REST_JSON_1 ||
                protocol == AwsProtocol.AWS_JSON_1_0 || protocol == AwsProtocol.AWS_JSON_1_1) {
                generateServiceErrorUnion(errors, writer);
            }
        }
    }
    
    /**
     * Recursively collects all shapes referenced by a shape.
     */
    private void collectReferencedShapes(ShapeId shapeId, Set<StructureShape> structures,
                                         Set<StructureShape> errors, Set<Shape> enums, Set<ShapeId> visited) {
        if (visited.contains(shapeId)) {
            return;
        }
        visited.add(shapeId);
        
        Shape shape = model.expectShape(shapeId);
        
        if (shape instanceof StructureShape) {
            StructureShape structure = (StructureShape) shape;
            
            // Check if this is an error type
            if (structure.hasTrait(ErrorTrait.class)) {
                errors.add(structure);
            } else {
                structures.add(structure);
            }
            
            // Recursively collect member shapes
            for (MemberShape member : structure.getAllMembers().values()) {
                collectReferencedShapes(member.getTarget(), structures, errors, enums, visited);
            }
        } else if (shape instanceof EnumShape || shape instanceof IntEnumShape) {
            // Collect enum types
            enums.add(shape);
        } else if (shape instanceof ListShape) {
            ListShape list = (ListShape) shape;
            collectReferencedShapes(list.getMember().getTarget(), structures, errors, enums, visited);
        } else if (shape instanceof MapShape) {
            MapShape map = (MapShape) shape;
            collectReferencedShapes(map.getKey().getTarget(), structures, errors, enums, visited);
            collectReferencedShapes(map.getValue().getTarget(), structures, errors, enums, visited);
        } else if (shape instanceof UnionShape) {
            // Collect union types (treat similar to enums)
            enums.add(shape);
            // Recursively collect member shapes
            UnionShape union = (UnionShape) shape;
            for (MemberShape member : union.getAllMembers().values()) {
                collectReferencedShapes(member.getTarget(), structures, errors, enums, visited);
            }
        }
        // Simple types (String, Integer, etc.) don't need generation
    }
    
    /**
     * Generates a toFailure conversion function for an error type.
     */
    private void generateErrorToFailure(StructureShape error, UnisonWriter writer) {
        String typeName = getNamespacedTypeName(error.getId().getName());
        String funcName = typeName + ".toFailure";
        
        writer.writeSignature(funcName, typeName + " -> Failure");
        writer.write("$L err =", funcName);
        writer.indent();
        
        // Check if there's a message field and whether it's required
        var messageField = error.getAllMembers().values().stream()
            .filter(m -> m.getMemberName().equalsIgnoreCase("message"))
            .findFirst();
        
        if (messageField.isPresent()) {
            boolean isRequired = messageField.get().isRequired();
            if (isRequired) {
                // Required message field - access directly
                writer.write("Failure (typeLink $L) ($L.message err) (Any err)", typeName, typeName);
            } else {
                // Optional message field - use getOrElse with default
                writer.write("Failure (typeLink $L) (Optional.getOrElse \"\" ($L.message err)) (Any err)", 
                    typeName, typeName);
            }
        } else {
            // No message field - use type name as message
            writer.write("Failure (typeLink $L) \"$L error\" (Any err)", typeName, typeName);
        }
        
        writer.dedent();
    }
    
    /**
     * Generates a service-level error union type that encompasses all service-specific errors.
     * Also generates a fromCodeAndMessage function to map error codes to specific error types.
     */
    private void generateServiceErrorUnion(Set<StructureShape> errors, UnisonWriter writer) {
        String serviceName = service.getId().getName();
        // Remove "Service" suffix if present to avoid "S3ServiceServiceError"
        if (serviceName.endsWith("Service")) {
            serviceName = serviceName.substring(0, serviceName.length() - 7);
        }
        String errorUnionName = UnisonSymbolProvider.toNamespacedTypeName(
                serviceName + "ServiceError", clientNamespace);
        
        writer.writeDocComment("Service-level error union type for " + serviceName);
        writer.write("unique type $L", errorUnionName);
        writer.indent();
        
        // Generate a constructor for each error type
        boolean isFirst = true;
        for (StructureShape error : errors) {
            String errorTypeName = UnisonSymbolProvider.toUnisonTypeName(error.getId().getName());
            String constructorName = errorTypeName;
            String prefix = isFirst ? "=" : "|";
            writer.write("$L $L $L", prefix, constructorName, getNamespacedTypeName(error.getId().getName()));
            isFirst = false;
        }
        
        // Add a generic unknown error constructor
        writer.write("| UnknownError Text Text"); // code, message
        writer.dedent();
        writer.writeBlankLine();
        
        // Generate fromCodeAndMessage function
        writer.writeDocComment("Maps error code and message to the appropriate error type");
        writer.write("$L.fromCodeAndMessage : Text -> Text -> $L", errorUnionName, errorUnionName);
        writer.write("$L.fromCodeAndMessage code message =", errorUnionName);
        writer.indent();
        writer.write("match code with");
        writer.indent();
        
        // Generate a match case for each error - use the error name trait if available
        for (StructureShape error : errors) {
            String errorTypeName = UnisonSymbolProvider.toUnisonTypeName(error.getId().getName());
            String errorCode = error.getId().getName(); // Default to shape name
            
            // Try to get the actual error code from traits
            if (error.hasTrait("smithy.api#error")) {
                // Use the shape name as the error code
                errorCode = error.getId().getName();
            }
            
            String constructorName = errorTypeName;
            String fullTypeName = getNamespacedTypeName(error.getId().getName());
            writer.write("\"$L\" -> $L.$L ($L.fromMessage message)", 
                    errorCode, errorUnionName, constructorName, fullTypeName);
        }
        
        // Default case for unknown errors
        writer.write("_ -> $L.UnknownError code message", errorUnionName);
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
        
        // Generate fromMessage helper for each error type if needed
        for (StructureShape error : errors) {
            String fullTypeName = getNamespacedTypeName(error.getId().getName());
            String typeName = UnisonSymbolProvider.toUnisonTypeName(error.getId().getName());
            
            writer.write("$L.fromMessage : Text -> $L", fullTypeName, fullTypeName);
            writer.write("$L.fromMessage message =", fullTypeName);
            writer.indent();
            
            // Get all members
            List<MemberShape> members = new ArrayList<>(error.getAllMembers().values());
            
            // Construct the error with the message field set and other fields as None
            writer.write("$L", typeName);
            writer.indent();
            for (MemberShape member : members) {
                if (member.getMemberName().equalsIgnoreCase("message")) {
                    writer.write("(Some message)");
                } else {
                    writer.write("None");
                }
            }
            writer.dedent();
            writer.dedent();
            writer.writeBlankLine();
        }
        
        // Generate toFailure function for the service error union
        writer.writeDocComment("Convert service error to Failure for exception raising");
        writer.write("$L.toFailure : $L -> Failure", errorUnionName, errorUnionName);
        writer.write("$L.toFailure err =", errorUnionName);
        writer.indent();
        writer.write("match err with");
        writer.indent();
        
        // Generate a match case for each error type
        for (StructureShape error : errors) {
            String errorTypeName = UnisonSymbolProvider.toUnisonTypeName(error.getId().getName());
            String constructorName = errorTypeName;
            String fullTypeName = getNamespacedTypeName(error.getId().getName());
            writer.write("$L.$L e -> $L.toFailure e", errorUnionName, constructorName, fullTypeName);
        }
        
        // Handle UnknownError case
        writer.write("$L.UnknownError code msg -> Failure (typeLink Generic) (code ++ \": \" ++ msg) (Any err)", errorUnionName);
        
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates XML parser functions for structure types.
     * 
     * <p>For each structure, generates a parseXxxFromXml function that
     * extracts fields from XML text and constructs the record.
     * 
     * <p>Generated pattern:
     * <pre>
     * parseBucketFromXml : Text -> Bucket
     * parseBucketFromXml xml =
     *   Bucket.Bucket
     *     (Aws.Xml.extractElementOpt "BucketArn" xml)
     *     (Aws.Xml.extractElementOpt "BucketRegion" xml)
     *     ...
     * </pre>
     */
    private void generateXmlParsers(Set<StructureShape> structures, UnisonWriter writer) {
        if (structures.isEmpty()) {
            return;
        }
        
        writer.writeComment("=== XML Parsers ===");
        writer.writeBlankLine();
        
        for (StructureShape structure : structures) {
            generateXmlParserForStructure(structure, writer);
            writer.writeBlankLine();
        }
    }
    
    /**
     * Generates JSON serializer and deserializer functions for all nested structures.
     * 
     * <p>For AWS JSON protocols, nested structures need ToJson serializers
     * and FromJson deserializers so they can be used in lists, maps, or as nested fields.
     */
    private void generateJsonSerializers(Set<StructureShape> structures, UnisonWriter writer) {
        if (structures.isEmpty()) {
            return;
        }
        
        // Get the protocol generator
        Optional<ProtocolGenerator> protocolGenerator = ProtocolGeneratorFactory.getGenerator(
                AwsProtocolDetector.detectProtocol(service));
        if (protocolGenerator.isEmpty() || !(protocolGenerator.get() instanceof AwsJsonProtocolGenerator)) {
            return;
        }
        
        AwsJsonProtocolGenerator jsonGen = (AwsJsonProtocolGenerator) protocolGenerator.get();
        
        writer.writeComment("=== Structure JSON Serializers/Deserializers ===");
        writer.writeBlankLine();
        
        for (StructureShape structure : structures) {
            jsonGen.generateStructureSerializer(structure, writer, context);
            jsonGen.generateStructureDeserializer(structure, writer, context);
        }
    }
    
    /**
     * Generates an XML parser function for a single structure.
     */
    private void generateXmlParserForStructure(StructureShape structure, UnisonWriter writer) {
        String typeName = getNamespacedTypeName(structure.getId().getName());
        String baseTypeName = UnisonSymbolProvider.toUnisonTypeName(structure.getId().getName());
        String funcName = getNamespacedFunctionName("parse" + baseTypeName + "FromXml");
        
        // Write doc comment
        writer.writeDocComment("Parse " + typeName + " from XML text.");
        
        // Write signature
        writer.writeSignature(funcName, "Text -> " + typeName);
        
        // Write function body
        writer.write("$L xml =", funcName);
        writer.indent();
        
        // Write constructor call with field extractions
        // Use base type name for constructor (Unison namespacing quirk)
        writer.write("$L", baseTypeName);
        writer.indent();
        
        for (MemberShape member : structure.getAllMembers().values()) {
            String fieldName = member.getMemberName();
            // Get XML element name (capitalize first letter by convention)
            String xmlElementName = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            
            Shape targetShape = model.expectShape(member.getTarget());
            String extraction = generateFieldExtraction(member, targetShape, xmlElementName);
            writer.write(extraction);
        }
        
        writer.dedent();
        writer.dedent();
    }
    
    /**
     * Generates field extraction code for a member.
     */
    private String generateFieldExtraction(MemberShape member, Shape targetShape, String xmlElementName) {
        boolean isRequired = member.hasTrait(software.amazon.smithy.model.traits.RequiredTrait.class);
        boolean hasDefault = member.hasTrait(software.amazon.smithy.model.traits.DefaultTrait.class);
        boolean isOptional = !isRequired && !hasDefault;
        
        if (targetShape instanceof StructureShape) {
            // Nested structure - use parser function
            String baseTypeName = UnisonSymbolProvider.toUnisonTypeName(targetShape.getId().getName());
            String parserName = getNamespacedFunctionName("parse" + baseTypeName + "FromXml");
            if (isOptional) {
                return "(Aws.Xml.parseNestedFromXml \"" + xmlElementName + "\" " + parserName + " xml)";
            } else {
                // Required nested structure - parse and extract, bug if missing
                return "(Optional.getOrElse (bug \"Required nested field '" + xmlElementName + "' not found\") (Aws.Xml.parseNestedFromXml \"" + xmlElementName + "\" " + parserName + " xml))";
            }
        } else if (targetShape instanceof ListShape) {
            ListShape listShape = (ListShape) targetShape;
            Shape memberTarget = model.expectShape(listShape.getMember().getTarget());
            
            if (memberTarget instanceof StructureShape) {
                // List of structures
                String baseItemTypeName = UnisonSymbolProvider.toUnisonTypeName(memberTarget.getId().getName());
                String parserName = getNamespacedFunctionName("parse" + baseItemTypeName + "FromXml");
                // Get item element name from list member
                String itemElementName = Character.toUpperCase(listShape.getMember().getMemberName().charAt(0)) 
                        + listShape.getMember().getMemberName().substring(1);
                if (isOptional) {
                    return "(Aws.Xml.parseOptionalWrappedListFromXml \"" + xmlElementName + "\" \"" + itemElementName + "\" " + parserName + " xml)";
                } else {
                    return "(Aws.Xml.parseWrappedListFromXml \"" + xmlElementName + "\" \"" + itemElementName + "\" " + parserName + " xml)";
                }
            } else if (memberTarget instanceof EnumShape || 
                    memberTarget.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class)) {
                // List of enums - extract text and convert
                String itemElementName = Character.toUpperCase(listShape.getMember().getMemberName().charAt(0)) 
                        + listShape.getMember().getMemberName().substring(1);
                String enumFromText = getNamespacedFunctionName(memberTarget.getId().getName() + "FromText");
                if (isOptional) {
                    return "(Some (List.filterMap " + enumFromText + " (Aws.Xml.extractAll \"" + itemElementName + "\" xml)))";
                } else {
                    return "(List.filterMap " + enumFromText + " (Aws.Xml.extractAll \"" + itemElementName + "\" xml))";
                }
            } else if (memberTarget.isStringShape()) {
                // List of strings (plain, not enums)
                String itemElementName = Character.toUpperCase(listShape.getMember().getMemberName().charAt(0)) 
                        + listShape.getMember().getMemberName().substring(1);
                if (isOptional) {
                    // Use Optional.some with list - if empty we still return Some []
                    return "(Some (Aws.Xml.extractAll \"" + itemElementName + "\" xml))";
                } else {
                    return "(Aws.Xml.extractAll \"" + itemElementName + "\" xml)";
                }
            } else {
                // Fallback for other list types
                if (isOptional) {
                    return "None -- list parsing: " + memberTarget.getType();
                } else {
                    return "[] -- list parsing: " + memberTarget.getType();
                }
            }
        } else if (targetShape instanceof EnumShape || 
                targetShape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class)) {
            // Enum - extract text and convert (check before isStringShape since EnumShape extends StringShape)
            String enumFromText = getNamespacedFunctionName(targetShape.getId().getName() + "FromText");
            if (isOptional) {
                return "(Optional.flatMap " + enumFromText + " (Aws.Xml.extractElementOpt \"" + xmlElementName + "\" xml))";
            } else {
                // Required enum - extract text and convert, crash if missing or invalid
                return "(Optional.getOrElse (bug \"Required enum field '" + xmlElementName + "' not found or invalid\") (" + enumFromText + " (Aws.Xml.extractElement \"" + xmlElementName + "\" xml)))";
            }
        } else if (targetShape.isStringShape()) {
            // Plain string (not enum)
            if (isOptional) {
                return "(Aws.Xml.extractElementOpt \"" + xmlElementName + "\" xml)";
            } else {
                return "(Aws.Xml.extractElement \"" + xmlElementName + "\" xml)";
            }
        } else if (targetShape.isIntegerShape() || targetShape.isLongShape()) {
            if (isOptional) {
                return "(Aws.Xml.extractInt \"" + xmlElementName + "\" xml)";
            } else {
                return "(Optional.getOrElse +0 (Aws.Xml.extractInt \"" + xmlElementName + "\" xml))";
            }
        } else if (targetShape.isBooleanShape()) {
            if (isOptional) {
                return "(Aws.Xml.extractBool \"" + xmlElementName + "\" xml)";
            } else {
                return "(Optional.getOrElse false (Aws.Xml.extractBool \"" + xmlElementName + "\" xml))";
            }
        } else if (targetShape.isBlobShape()) {
            // Blob fields in XML are typically base64 encoded text
            // Convert to bytes using toUtf8 for now (proper base64 decode would need fromBase64)
            if (isOptional) {
                return "(Optional.map toUtf8 (Aws.Xml.extractElementOpt \"" + xmlElementName + "\" xml))";
            } else {
                return "(toUtf8 (Aws.Xml.extractElement \"" + xmlElementName + "\" xml))";
            }
        } else {
            // Fallback
            if (isOptional) {
                return "None -- " + targetShape.getType();
            } else {
                return "(bug \"unsupported required field type: " + targetShape.getType() + "\")";
            }
        }
    }
    
    /**
     * Generates a stub for an operation using exception-based error handling.
     * 
     * <p>Used when the protocol is not fully implemented. Operations use
     * idiomatic Unison pattern:
     * <ul>
     *   <li>Return output directly on success (not wrapped in Response)</li>
     *   <li>Raise exceptions via Exception.raise on error</li>
     *   <li>Use '{IO, Exception, Http} abilities</li>
     * </ul>
     */
    private void generateOperationStub(OperationShape operation, UnisonWriter writer) {
        String opName = getNamespacedFunctionName(operation.getId().getName());
        
        // Get input/output type names (namespaced)
        String inputType = operation.getInput()
                .map(id -> getNamespacedTypeName(id.getName()))
                .orElse("()");
        String outputType = operation.getOutput()
                .map(id -> getNamespacedTypeName(id.getName()))
                .orElse("()");
        
        String configType = getNamespacedTypeName("Config");
        
        writer.writeDocComment(operation.getId().getName() + " operation (NOT IMPLEMENTED)\n\n" +
                "Raises exception on error, returns output directly on success.");
        
        // Exception-based signature: returns output directly, raises on error
        String signature = String.format("%s -> %s -> '{IO, Exception, Http} %s", configType, inputType, outputType);
        writer.writeSignature(opName, signature);
        
        writer.write("$L config input =", opName);
        writer.indent();
        writer.write("-- TODO: Implement $L operation", operation.getId().getName());
        writer.write("-- On success: return " + outputType + " directly");
        writer.write("-- On error: Exception.raise (ServiceError.toFailure error)");
        writer.write("bug \"Operation not yet implemented: $L\"", opName);
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Copies required runtime modules to the output directory based on service type.
     * 
     * <p>Only copies AWS runtime modules if an AWS service is detected.
     * Detection uses the {@code aws.api#service} trait as the primary marker,
     * with fallbacks to {@code aws.auth#sigv4} trait and protocol detection.
     * 
     * <p>Copies pre-written Unison runtime modules that provide common
     * functionality needed by generated code, such as:
     * <ul>
     *   <li>{@code aws_sigv4.u} - AWS SigV4 request signing</li>
     *   <li>{@code aws_xml.u} - XML encoding/decoding (for XML protocols)</li>
     *   <li>{@code aws_s3.u} - S3-specific utilities (for S3 only)</li>
     * </ul>
     * 
     * @param protocol The detected AWS protocol
     * @return List of copied module filenames
     */
    public java.util.List<String> copyRuntimeModules(AwsProtocol protocol) throws IOException {
        RuntimeModuleCopier copier = new RuntimeModuleCopier(fileManifest, outputDir);
        
        // Only copy AWS modules if this is an AWS service
        if (!copier.isAwsService(service, protocol)) {
            LOGGER.info("Non-AWS service detected, skipping AWS runtime modules");
            return java.util.Collections.emptyList();
        }
        
        java.util.List<String> copied = copier.copyAwsModulesForProtocol(protocol, service);
        
        if (!copied.isEmpty()) {
            LOGGER.info("Copied runtime modules: " + String.join(", ", copied));
        }
        
        return copied;
    }
    
    /**
     * Checks if a union shape is the DynamoDB AttributeValue type.
     * 
     * <p>DynamoDB's AttributeValue is a special union that should use the runtime
     * type Aws.Json.AttributeValue instead of generating a new type.
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
     * Writes the generated code to a file.
     */
    private void writeToFile(UnisonWriter writer) throws IOException {
        String filename = namespace.replace(".", "_") + "_client.u";
        String content = writer.toString().stripTrailing() + "\n";
        
        java.nio.file.Path outputPath;
        if (outputDir != null && !outputDir.isEmpty()) {
            java.nio.file.Path baseDir = fileManifest.getBaseDir();
            java.nio.file.Path projectRoot = baseDir;
            
            for (int i = 0; i < 4 && projectRoot != null && projectRoot.getParent() != null; i++) {
                projectRoot = projectRoot.getParent();
            }
            
            if (projectRoot == null) {
                projectRoot = baseDir;
            }
            
            outputPath = projectRoot.resolve(outputDir).resolve(filename);
            
            try {
                if (outputPath.getParent() != null) {
                    java.nio.file.Files.createDirectories(outputPath.getParent());
                }
                java.nio.file.Files.writeString(outputPath, content);
                LOGGER.info("Generated client module: " + outputPath);
            } catch (java.nio.file.FileSystemException e) {
                LOGGER.warning("Cannot write to custom directory, using FileManifest: " + e.getMessage());
                outputPath = fileManifest.getBaseDir().resolve("src/" + filename);
                fileManifest.writeFile(outputPath, content);
            }
        } else {
            outputPath = fileManifest.getBaseDir().resolve("src/" + filename);
            fileManifest.writeFile(outputPath, content);
        }
    }
}
