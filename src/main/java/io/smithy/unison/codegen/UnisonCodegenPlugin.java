package io.smithy.unison.codegen;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.util.logging.Logger;

/**
 * Smithy Build plugin for generating Unison client code using DirectedCodegen.
 * 
 * <p>This plugin uses Smithy's {@link CodegenDirector} pattern for structured
 * code generation with a modern architecture that supports:
 * <ul>
 *   <li>Extensibility via {@link UnisonIntegration}</li>
 *   <li>Shape-specific generators</li>
 *   <li>Standard model transformations</li>
 *   <li>SPI-based integration discovery</li>
 * </ul>
 * 
 * <h2>Plugin Configuration</h2>
 * <p>Configure in {@code smithy-build.json}:
 * <pre>
 * {
 *   "plugins": {
 *     "unison-codegen": {
 *       "service": "com.example#MyService",
 *       "namespace": "aws.s3",
 *       "outputDir": "src/generated"
 *     }
 *   }
 * }
 * </pre>
 * 
 * @see UnisonGenerator
 * @see UnisonIntegration
 * @see UnisonContext
 */
public final class UnisonCodegenPlugin implements SmithyBuildPlugin {
    
    private static final Logger LOGGER = Logger.getLogger(UnisonCodegenPlugin.class.getName());
    
    /**
     * Gets the name of this plugin.
     *
     * @return The plugin name
     */
    @Override
    public String getName() {
        return "unison-codegen";
    }
    
    /**
     * Executes the Unison code generation.
     * 
     * <p>This method sets up and runs the {@link CodegenDirector} with:
     * <ul>
     *   <li>{@link UnisonGenerator} as the directed codegen implementation</li>
     *   <li>{@link UnisonIntegration} for SPI discovery</li>
     *   <li>Model transformations for better code generation</li>
     * </ul>
     *
     * @param pluginContext The plugin context from Smithy build
     */
    @Override
    public void execute(PluginContext pluginContext) {
        LOGGER.info("Executing Unison code generation (DirectedCodegen)");
        
        Model model = pluginContext.getModel();
        ObjectNode settingsNode = pluginContext.getSettings();
        
        // Create settings from plugin configuration
        UnisonSettings settings = UnisonSettings.from(settingsNode);
        
        LOGGER.info("Generating Unison client for service: " + settings.service());
        LOGGER.info("Output directory: " + settings.outputDir());
        if (settings.namespace() != null) {
            LOGGER.info("Namespace: " + settings.namespace());
        }
        
        // Create and configure the CodegenDirector
        CodegenDirector<UnisonWriter, UnisonIntegration, UnisonContext, UnisonSettings> director = 
                new CodegenDirector<>();
        
        // Set the DirectedCodegen implementation
        director.directedCodegen(new UnisonGenerator());
        
        // Set the SmithyIntegration class for SPI discovery
        director.integrationClass(UnisonIntegration.class);
        
        // Set context from plugin
        director.fileManifest(pluginContext.getFileManifest());
        director.model(model);
        director.settings(settings);
        director.service(settings.service());
        
        // Apply standard model transformations
        director.performDefaultCodegenTransforms();
        
        // Sort members for deterministic output
        director.sortMembers();
        
        // Run the generator
        director.run();
        
        LOGGER.info("Unison code generation completed successfully");
    }
}
