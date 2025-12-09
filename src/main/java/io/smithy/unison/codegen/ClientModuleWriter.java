package io.smithy.unison.codegen;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

import io.smithy.unison.codegen.aws.AwsProtocol;
import io.smithy.unison.codegen.aws.AwsProtocolDetector;
import io.smithy.unison.codegen.generators.PaginationGenerator;
import io.smithy.unison.codegen.protocols.ProtocolGenerator;
import io.smithy.unison.codegen.protocols.ProtocolGeneratorFactory;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;

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
        this.fileManifest = fileManifest;
        this.outputDir = outputDir;
        this.context = context;
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
        
        // Write header comment
        writer.writeComment("Generated Unison client for " + service.getId().getName());
        if (useProtocolGenerator) {
            writer.writeComment("Protocol: " + protocol.name());
        } else {
            writer.writeComment("Protocol " + protocol.name() + " - operations are stubs");
        }
        writer.writeBlankLine();
        
        // Write Config type
        writer.writeDocComment("Configuration for the " + service.getId().getName() + " client");
        writer.write("type Config = {");
        writer.indent();
        writer.write("endpoint : Text,");
        writer.write("region : Text,");
        writer.write("credentials : Credentials,");
        writer.write("usePathStyle : Boolean");
        writer.dedent();
        writer.write("}");
        writer.writeBlankLine();
        
        // Write Credentials type
        writer.write("type Credentials = {");
        writer.indent();
        writer.write("accessKeyId : Text,");
        writer.write("secretAccessKey : Text,");
        writer.write("sessionToken : Optional Text");
        writer.dedent();
        writer.write("}");
        writer.writeBlankLine();
        
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
        PaginationGenerator paginationGenerator = new PaginationGenerator();
        paginationGenerator.generate(service, model, writer);
        
        // Write to file
        writeToFile(writer);
        
        // Copy runtime modules
        copyRuntimeModules();
        
        if (useProtocolGenerator) {
            LOGGER.info("Client generation completed with full operation implementations");
        } else {
            LOGGER.info("Client generation completed (stub operations)");
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
        String opName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName());
        
        // Get input/output type names
        String inputType = operation.getInput()
                .map(id -> UnisonSymbolProvider.toUnisonTypeName(id.getName()))
                .orElse("()");
        String outputType = operation.getOutput()
                .map(id -> UnisonSymbolProvider.toUnisonTypeName(id.getName()))
                .orElse("()");
        
        writer.writeDocComment(operation.getId().getName() + " operation (NOT IMPLEMENTED)\n\n" +
                "Raises exception on error, returns output directly on success.");
        
        // Exception-based signature: returns output directly, raises on error
        String signature = String.format("Config -> %s -> '{IO, Exception, Http} %s", inputType, outputType);
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
     * Copies all required runtime modules to the output directory.
     * 
     * <p>Copies pre-written Unison runtime modules that provide common
     * functionality needed by generated code, such as:
     * <ul>
     *   <li>{@code aws_sigv4.u} - AWS SigV4 request signing</li>
     * </ul>
     * 
     * @return List of copied module filenames
     */
    public java.util.List<String> copyRuntimeModules() throws IOException {
        RuntimeModuleCopier copier = new RuntimeModuleCopier(fileManifest, outputDir);
        java.util.List<String> copied = copier.copyAwsModules();
        
        if (!copied.isEmpty()) {
            LOGGER.info("Copied runtime modules: " + String.join(", ", copied));
        }
        
        return copied;
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
