package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Interface for protocol-specific code generation.
 * 
 * <p>This interface defines the contract for generating Unison client code
 * for different AWS protocols.
 * 
 * <p><b>NOT FULLY IMPLEMENTED</b>: Implementations are stubs in the first draft.
 */
public interface ProtocolGenerator {
    
    /**
     * Gets the protocol trait ID this generator handles.
     */
    ShapeId getProtocol();
    
    /**
     * Gets a human-readable name for this protocol generator.
     */
    default String getName() {
        return getProtocol().getName();
    }
    
    /**
     * Generates the complete operation function.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    void generateOperation(OperationShape operation, UnisonWriter writer, UnisonContext context);
    
    /**
     * Generates request serialization code for an operation.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    void generateRequestSerializer(OperationShape operation, UnisonWriter writer, UnisonContext context);
    
    /**
     * Generates response deserialization code for an operation.
     * 
     * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
     */
    void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context);
    
    /**
     * Gets the Content-Type header value for this protocol.
     */
    String getContentType(ServiceShape service);
    
    /**
     * Gets the default HTTP method for this protocol.
     */
    default String getDefaultMethod() {
        return "POST";
    }
    
    /**
     * Gets the default URI path for this protocol.
     */
    default String getDefaultUri() {
        return "/";
    }
    
    /**
     * Checks if this generator applies to a given service.
     */
    default boolean appliesTo(ServiceShape service) {
        return service.findTrait(getProtocol()).isPresent();
    }
}
