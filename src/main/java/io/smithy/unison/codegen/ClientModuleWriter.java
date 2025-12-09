package io.smithy.unison.codegen;

import java.io.IOException;
import java.util.logging.Logger;

import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;

/**
 * Core client module code generation logic for Unison.
 * 
 * <p>This class contains the actual Unison client code generation logic
 * used by the DirectedCodegen architecture via UnisonGenerator.
 * 
 * <p><b>NOT FULLY IMPLEMENTED</b>: This is a first draft with stub implementations.
 */
public final class ClientModuleWriter {
    
    private static final Logger LOGGER = Logger.getLogger(ClientModuleWriter.class.getName());
    
    private final ServiceShape service;
    private final Model model;
    private final String namespace;
    private final FileManifest fileManifest;
    private final String outputDir;
    
    /**
     * Creates a new client module writer.
     */
    public ClientModuleWriter(ServiceShape service, Model model, String namespace,
                               FileManifest fileManifest, String outputDir) {
        this.service = service;
        this.model = model;
        this.namespace = namespace;
        this.fileManifest = fileManifest;
        this.outputDir = outputDir;
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
                context.settings().outputDir()
        );
    }
    
    /**
     * Generates the complete client module.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    public void generate() throws IOException {
        LOGGER.info("Generating Unison client for service: " + service.getId());
        
        // TODO: Implement Unison code generation
        // See ErlangWriter.ClientModuleWriter.generate() for reference
        
        UnisonWriter writer = new UnisonWriter(namespace);
        
        // Write header comment
        writer.writeComment("Generated Unison client for " + service.getId().getName());
        writer.writeComment("THIS IS A STUB - Code generation not yet implemented");
        writer.writeBlankLine();
        
        // Write placeholder type
        writer.writeDocComment("Configuration for the " + service.getId().getName() + " client");
        writer.write("type Config = {");
        writer.indent();
        writer.write("endpoint : Text,");
        writer.write("region : Text,");
        writer.write("credentials : Credentials");
        writer.dedent();
        writer.write("}");
        writer.writeBlankLine();
        
        // Write placeholder credentials type
        writer.write("type Credentials = {");
        writer.indent();
        writer.write("accessKeyId : Text,");
        writer.write("secretAccessKey : Text,");
        writer.write("sessionToken : Optional Text");
        writer.dedent();
        writer.write("}");
        writer.writeBlankLine();
        
        // Note: We use exception-based error handling (idiomatic Unison pattern)
        // Operations raise exceptions via Exception.raise on error
        // No Response sum type needed - operations return output directly
        
        // Write stub for each operation
        for (ShapeId opId : service.getOperations()) {
            OperationShape operation = model.expectShape(opId, OperationShape.class);
            generateOperationStub(operation, writer);
        }
        
        // Write to file
        writeToFile(writer);
        
        LOGGER.info("Client generation completed (stub only)");
    }
    
    /**
     * Generates a stub for an operation using exception-based error handling.
     * 
     * <p>Operations use idiomatic Unison pattern:
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
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    public void copyRuntimeModules() throws IOException {
        LOGGER.info("Runtime module copying not yet implemented");
        
        // TODO: Implement runtime module copying
        // Runtime modules for Unison would include:
        // - aws_sigv4.u (SigV4 signing)
        // - aws_http.u (HTTP helpers)
        // - aws_xml.u (XML parsing)
        // - aws_json.u (JSON helpers)
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
