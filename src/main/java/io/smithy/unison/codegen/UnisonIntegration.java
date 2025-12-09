package io.smithy.unison.codegen;

import software.amazon.smithy.codegen.core.SmithyIntegration;

/**
 * Integration interface for extending Unison code generation.
 * 
 * <p>This interface extends Smithy's {@link SmithyIntegration} to provide
 * type-safe integration points for Unison code generation.
 * 
 * <p>Integrations are discovered via Java SPI (Service Provider Interface).
 * To create a custom integration:
 * <ol>
 *   <li>Implement this interface</li>
 *   <li>Register the implementation in 
 *       {@code META-INF/services/io.smithy.unison.codegen.UnisonIntegration}</li>
 * </ol>
 * 
 * @see SmithyIntegration
 * @see UnisonContext
 * @see UnisonSettings
 */
public interface UnisonIntegration 
        extends SmithyIntegration<UnisonSettings, UnisonWriter, UnisonContext> {
    
    /**
     * Gets the name of this integration.
     */
    @Override
    default String name() {
        return getClass().getCanonicalName();
    }
    
    /**
     * Gets the priority of this integration.
     * Higher priority integrations run first.
     */
    @Override
    default byte priority() {
        return 0;
    }
    
    /**
     * Called before code generation begins.
     */
    default void preprocessModel(UnisonContext context) {
        // Default: no-op
    }
    
    /**
     * Called after code generation completes.
     */
    default void postprocessGeneration(UnisonContext context) {
        // Default: no-op
    }
}
