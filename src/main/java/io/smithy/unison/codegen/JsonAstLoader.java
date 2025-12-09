package io.smithy.unison.codegen;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Utility for loading Smithy models from JSON AST files.
 */
public final class JsonAstLoader {
    
    private static final Logger LOGGER = Logger.getLogger(JsonAstLoader.class.getName());
    
    /**
     * Loads a Smithy model from a JSON AST file.
     *
     * @param astPath Path to the JSON AST file
     * @return The loaded Model
     */
    public static Model loadModel(Path astPath) {
        LOGGER.fine("Loading model from: " + astPath);
        
        ModelAssembler assembler = Model.assembler()
                .discoverModels()
                .addImport(astPath);
        
        return assembler.assemble().unwrap();
    }
    
    /**
     * Loads a Smithy model from multiple JSON AST files.
     *
     * @param astPaths Paths to JSON AST files
     * @return The assembled Model
     */
    public static Model loadModel(Path... astPaths) {
        ModelAssembler assembler = Model.assembler().discoverModels();
        
        for (Path path : astPaths) {
            LOGGER.fine("Adding model from: " + path);
            assembler.addImport(path);
        }
        
        return assembler.assemble().unwrap();
    }
    
    private JsonAstLoader() {
        // Utility class
    }
}
