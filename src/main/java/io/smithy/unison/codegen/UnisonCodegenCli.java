package io.smithy.unison.codegen;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Command-line interface for the Unison code generator.
 * 
 * <p><b>NOT FULLY IMPLEMENTED</b>: This is a first draft with minimal implementation.
 */
public final class UnisonCodegenCli {
    
    private static final Logger LOGGER = Logger.getLogger(UnisonCodegenCli.class.getName());
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: smithy-unison <model-path> [options]");
            System.err.println();
            System.err.println("Arguments:");
            System.err.println("  <model-path>    Path to Smithy model file (.smithy or .json)");
            System.err.println();
            System.err.println("Options:");
            System.err.println("  --service       Service shape ID (e.g., com.example#MyService)");
            System.err.println("  --namespace     Output namespace (e.g., aws.s3)");
            System.err.println("  --output        Output directory (default: src/generated)");
            System.err.println();
            System.err.println("Example:");
            System.err.println("  smithy-unison model.smithy --service com.example#MyService --output src/generated");
            System.exit(1);
        }
        
        // TODO: Implement CLI argument parsing and code generation
        // See ErlangCodegenCli for reference implementation
        
        LOGGER.info("Smithy-Unison CLI not yet fully implemented");
        LOGGER.info("Use the Smithy Build plugin instead (smithy-build.json)");
        
        System.out.println("smithy-unison CLI v0.1.0 (first draft)");
        System.out.println();
        System.out.println("CLI code generation is not yet implemented.");
        System.out.println("Please use the Smithy Build plugin by adding to smithy-build.json:");
        System.out.println();
        System.out.println("{");
        System.out.println("  \"plugins\": {");
        System.out.println("    \"unison-codegen\": {");
        System.out.println("      \"service\": \"your.service#Name\",");
        System.out.println("      \"namespace\": \"your.namespace\",");
        System.out.println("      \"outputDir\": \"src/generated\"");
        System.out.println("    }");
        System.out.println("  }");
        System.out.println("}");
    }
}
