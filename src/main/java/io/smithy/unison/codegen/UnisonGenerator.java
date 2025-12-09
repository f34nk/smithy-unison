package io.smithy.unison.codegen;

import java.util.logging.Logger;

import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.CreateContextDirective;
import software.amazon.smithy.codegen.core.directed.CreateSymbolProviderDirective;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.codegen.core.directed.DirectedCodegen;
import software.amazon.smithy.codegen.core.directed.GenerateEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateErrorDirective;
import software.amazon.smithy.codegen.core.directed.GenerateIntEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;

/**
 * Directed code generator for Unison.
 * 
 * <p>This class implements Smithy's {@link DirectedCodegen} interface to provide
 * a structured approach to code generation.
 * 
 * <p><b>NOT FULLY IMPLEMENTED</b>: This is a first draft with stub implementations.
 * 
 * @see DirectedCodegen
 * @see UnisonContext
 * @see UnisonIntegration
 */
public final class UnisonGenerator 
        implements DirectedCodegen<UnisonContext, UnisonSettings, UnisonIntegration> {
    
    private static final Logger LOGGER = Logger.getLogger(UnisonGenerator.class.getName());
    
    /**
     * Creates a symbol provider for the code generation session.
     */
    @Override
    public SymbolProvider createSymbolProvider(CreateSymbolProviderDirective<UnisonSettings> directive) {
        LOGGER.fine("Creating symbol provider for service: " + directive.settings().service());
        return new UnisonSymbolProvider(directive.model(), directive.settings());
    }
    
    /**
     * Creates the code generation context.
     */
    @Override
    public UnisonContext createContext(CreateContextDirective<UnisonSettings, UnisonIntegration> directive) {
        LOGGER.fine("Creating code generation context");
        
        WriterDelegator<UnisonWriter> writerDelegator = new WriterDelegator<>(
                directive.fileManifest(),
                directive.symbolProvider(),
                UnisonWriter.factory()
        );
        
        return UnisonContext.builder()
                .model(directive.model())
                .settings(directive.settings())
                .symbolProvider(directive.symbolProvider())
                .fileManifest(directive.fileManifest())
                .writerDelegator(writerDelegator)
                .integrations(directive.integrations())
                .build();
    }
    
    /**
     * Generates code for a service shape.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    @Override
    public void generateService(GenerateServiceDirective<UnisonContext, UnisonSettings> directive) {
        ServiceShape service = directive.shape();
        UnisonContext context = directive.context();
        
        LOGGER.info("Generating service: " + service.getId());
        
        // TODO: Implement Unison service generation
        // See ErlangGenerator.generateService() for reference
        try {
            ClientModuleWriter writer = ClientModuleWriter.fromContext(context);
            writer.generate();
            writer.copyRuntimeModules();
            
            LOGGER.info("Service generation completed: " + service.getId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate service: " + service.getId(), e);
        }
    }
    
    /**
     * Generates code for a structure shape.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    @Override
    public void generateStructure(GenerateStructureDirective<UnisonContext, UnisonSettings> directive) {
        StructureShape structure = directive.shape();
        LOGGER.finest("Processing structure: " + structure.getId());
        
        // TODO: Implement Unison structure generation
        // Structures in Unison are record types: type Name = { field : Type }
    }
    
    /**
     * Generates code for a union shape.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    @Override
    public void generateUnion(GenerateUnionDirective<UnisonContext, UnisonSettings> directive) {
        UnionShape union = directive.shape();
        LOGGER.finest("Processing union: " + union.getId());
        
        // TODO: Implement Unison union generation
        // Unions in Unison are sum types: type Name = Variant1 | Variant2
    }
    
    /**
     * Generates code for an enum shape.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    @Override
    public void generateEnumShape(GenerateEnumDirective<UnisonContext, UnisonSettings> directive) {
        Shape enumShape = directive.shape();
        LOGGER.finest("Processing enum: " + enumShape.getId());
        
        // TODO: Implement Unison enum generation
        // Enums in Unison are represented as sum types
    }
    
    /**
     * Generates code for an integer enum shape.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    @Override
    public void generateIntEnumShape(GenerateIntEnumDirective<UnisonContext, UnisonSettings> directive) {
        Shape intEnum = directive.shape();
        LOGGER.finest("Processing int enum: " + intEnum.getId());
        
        // TODO: Implement Unison int enum generation
    }
    
    /**
     * Generates code for an error shape.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    @Override
    public void generateError(GenerateErrorDirective<UnisonContext, UnisonSettings> directive) {
        StructureShape errorShape = directive.shape();
        LOGGER.finest("Processing error: " + errorShape.getId());
        
        // TODO: Implement Unison error generation
    }
    
    /**
     * Hook called before any shape generation begins.
     */
    @Override
    public void customizeBeforeShapeGeneration(CustomizeDirective<UnisonContext, UnisonSettings> directive) {
        LOGGER.fine("Running pre-generation customization");
        
        for (UnisonIntegration integration : directive.context().integrations()) {
            LOGGER.finest("Running preprocessModel for: " + integration.name());
            integration.preprocessModel(directive.context());
        }
    }
    
    /**
     * Hook called after all shape generation completes.
     */
    public void customizeAfterShapeGeneration(CustomizeDirective<UnisonContext, UnisonSettings> directive) {
        LOGGER.fine("Running post-generation customization");
        
        for (UnisonIntegration integration : directive.context().integrations()) {
            LOGGER.finest("Running postprocessGeneration for: " + integration.name());
            integration.postprocessGeneration(directive.context());
        }
        
        directive.context().writerDelegator().flushWriters();
    }
}
