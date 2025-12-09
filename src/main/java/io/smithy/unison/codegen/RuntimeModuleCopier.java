package io.smithy.unison.codegen;

import software.amazon.smithy.build.FileManifest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Copies Unison runtime modules to the output directory.
 * 
 * <p>Runtime modules are pre-written Unison source files that provide common
 * functionality needed by generated code, such as:
 * <ul>
 *   <li>{@code aws_sigv4.u} - AWS SigV4 request signing</li>
 *   <li>{@code aws_xml.u} - XML encoding/decoding (planned)</li>
 *   <li>{@code aws_http.u} - HTTP request helpers (planned)</li>
 *   <li>{@code aws_s3.u} - S3-specific utilities (planned)</li>
 *   <li>{@code aws_config.u} - Configuration types (planned)</li>
 *   <li>{@code aws_credentials.u} - Credential loading (planned)</li>
 * </ul>
 * 
 * <p>These modules are bundled as resources in the generator JAR and copied
 * to the output directory during code generation.
 */
public final class RuntimeModuleCopier {
    
    private static final Logger LOGGER = Logger.getLogger(RuntimeModuleCopier.class.getName());
    
    /**
     * Base path for runtime modules in resources.
     */
    private static final String RUNTIME_RESOURCE_PATH = "runtime/";
    
    /**
     * Available runtime modules.
     */
    public enum RuntimeModule {
        /**
         * AWS SigV4 signing implementation.
         */
        AWS_SIGV4("aws_sigv4.u", "AWS SigV4 request signing"),
        
        /**
         * XML encoding/decoding utilities.
         */
        AWS_XML("aws_xml.u", "XML encoding/decoding"),
        
        /**
         * HTTP request helpers.
         */
        AWS_HTTP("aws_http.u", "HTTP request helpers"),
        
        /**
         * S3-specific utilities.
         */
        AWS_S3("aws_s3.u", "S3-specific utilities"),
        
        /**
         * Configuration types.
         */
        AWS_CONFIG("aws_config.u", "Configuration types"),
        
        /**
         * Credential loading.
         */
        AWS_CREDENTIALS("aws_credentials.u", "Credential loading");
        
        private final String filename;
        private final String description;
        
        RuntimeModule(String filename, String description) {
            this.filename = filename;
            this.description = description;
        }
        
        /**
         * Gets the filename for this module.
         */
        public String getFilename() {
            return filename;
        }
        
        /**
         * Gets the description for this module.
         */
        public String getDescription() {
            return description;
        }
        
        /**
         * Gets the resource path for this module.
         */
        public String getResourcePath() {
            return RUNTIME_RESOURCE_PATH + filename;
        }
    }
    
    private final FileManifest fileManifest;
    private final String outputDir;
    
    /**
     * Creates a new RuntimeModuleCopier.
     * 
     * @param fileManifest The file manifest for writing output
     * @param outputDir Optional output directory (may be null)
     */
    public RuntimeModuleCopier(FileManifest fileManifest, String outputDir) {
        this.fileManifest = fileManifest;
        this.outputDir = outputDir;
    }
    
    /**
     * Creates a RuntimeModuleCopier from a UnisonContext.
     */
    public static RuntimeModuleCopier fromContext(UnisonContext context) {
        return new RuntimeModuleCopier(
                context.fileManifest(),
                context.settings().outputDir()
        );
    }
    
    /**
     * Copies a specific runtime module to the output directory.
     * 
     * @param module The runtime module to copy
     * @return true if the module was copied successfully
     */
    public boolean copyModule(RuntimeModule module) {
        String resourcePath = module.getResourcePath();
        String content = loadResource(resourcePath);
        
        if (content == null) {
            LOGGER.warning("Runtime module not found: " + resourcePath);
            return false;
        }
        
        try {
            writeToOutput(module.getFilename(), content);
            LOGGER.info("Copied runtime module: " + module.getFilename());
            return true;
        } catch (IOException e) {
            LOGGER.warning("Failed to copy runtime module " + module.getFilename() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Copies all available runtime modules to the output directory.
     * 
     * @return List of successfully copied module names
     */
    public List<String> copyAllModules() {
        List<String> copied = new ArrayList<>();
        
        for (RuntimeModule module : RuntimeModule.values()) {
            if (isModuleAvailable(module)) {
                if (copyModule(module)) {
                    copied.add(module.getFilename());
                }
            }
        }
        
        return copied;
    }
    
    /**
     * Copies required runtime modules for AWS services.
     * 
     * <p>Currently copies:
     * <ul>
     *   <li>{@code aws_sigv4.u} - Required for all AWS services</li>
     *   <li>{@code aws_xml.u} - Required for REST-XML protocol services</li>
     *   <li>{@code aws_http.u} - HTTP utilities for all services</li>
     *   <li>{@code aws_s3.u} - S3-specific utilities</li>
     *   <li>{@code aws_config.u} - Configuration types</li>
     * </ul>
     * 
     * @return List of successfully copied module names
     */
    public List<String> copyAwsModules() {
        List<String> copied = new ArrayList<>();
        
        // SigV4 is required for all AWS services
        if (copyModule(RuntimeModule.AWS_SIGV4)) {
            copied.add(RuntimeModule.AWS_SIGV4.getFilename());
        }
        
        // XML is required for REST-XML services (S3, CloudFront, Route 53, etc.)
        if (copyModule(RuntimeModule.AWS_XML)) {
            copied.add(RuntimeModule.AWS_XML.getFilename());
        }
        
        // HTTP utilities for request/response handling
        if (copyModule(RuntimeModule.AWS_HTTP)) {
            copied.add(RuntimeModule.AWS_HTTP.getFilename());
        }
        
        // S3-specific utilities (URL building, bucket validation)
        if (copyModule(RuntimeModule.AWS_S3)) {
            copied.add(RuntimeModule.AWS_S3.getFilename());
        }
        
        // Configuration types and helpers
        if (copyModule(RuntimeModule.AWS_CONFIG)) {
            copied.add(RuntimeModule.AWS_CONFIG.getFilename());
        }
        
        return copied;
    }
    
    /**
     * Checks if a runtime module is available as a resource.
     * 
     * @param module The module to check
     * @return true if the module resource exists
     */
    public boolean isModuleAvailable(RuntimeModule module) {
        String resourcePath = module.getResourcePath();
        return getClass().getClassLoader().getResource(resourcePath) != null;
    }
    
    /**
     * Gets the content of a runtime module.
     * 
     * @param module The module to read
     * @return The module content, or null if not found
     */
    public String getModuleContent(RuntimeModule module) {
        return loadResource(module.getResourcePath());
    }
    
    /**
     * Loads a resource file as a string.
     */
    private String loadResource(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            LOGGER.warning("Error reading resource " + resourcePath + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Writes content to the output directory.
     */
    private void writeToOutput(String filename, String content) throws IOException {
        Path outputPath;
        
        if (outputDir != null && !outputDir.isEmpty()) {
            // Write to custom output directory
            Path baseDir = fileManifest.getBaseDir();
            Path projectRoot = baseDir;
            
            // Navigate up from build output to project root
            for (int i = 0; i < 4 && projectRoot != null && projectRoot.getParent() != null; i++) {
                projectRoot = projectRoot.getParent();
            }
            
            if (projectRoot == null) {
                projectRoot = baseDir;
            }
            
            outputPath = projectRoot.resolve(outputDir).resolve(filename);
            
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            Files.writeString(outputPath, content);
        } else {
            // Write to file manifest
            outputPath = fileManifest.getBaseDir().resolve("src/" + filename);
            fileManifest.writeFile(outputPath, content);
        }
        
        LOGGER.fine("Wrote runtime module to: " + outputPath);
    }
}
