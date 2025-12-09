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
 * for different AWS protocols. Each protocol (REST-XML, REST-JSON, AWS Query, etc.)
 * has different serialization formats and HTTP binding rules.
 * 
 * <h2>Protocol Responsibilities</h2>
 * <ul>
 *   <li>Generate complete operation functions</li>
 *   <li>Serialize request bodies (XML, JSON, etc.)</li>
 *   <li>Deserialize response bodies</li>
 *   <li>Parse protocol-specific error responses</li>
 *   <li>Handle HTTP binding traits (@httpLabel, @httpQuery, @httpHeader, @httpPayload)</li>
 * </ul>
 * 
 * @see RestXmlProtocolGenerator
 * @see RestJsonProtocolGenerator
 */
public interface ProtocolGenerator {
    
    /**
     * Gets the protocol trait ID this generator handles.
     * 
     * @return The ShapeId of the protocol trait (e.g., "aws.protocols#restXml")
     */
    ShapeId getProtocol();
    
    /**
     * Gets a human-readable name for this protocol generator.
     * 
     * @return The protocol name (e.g., "restXml")
     */
    default String getName() {
        return getProtocol().getName();
    }
    
    /**
     * Generates the complete operation function.
     * 
     * <p>This is the main entry point for operation code generation. It should:
     * <ul>
     *   <li>Generate the function signature</li>
     *   <li>Build the HTTP request URL with path/query parameters</li>
     *   <li>Set request headers</li>
     *   <li>Serialize the request body</li>
     *   <li>Make the HTTP request</li>
     *   <li>Handle the response (success or error)</li>
     * </ul>
     * 
     * @param operation The operation to generate code for
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    void generateOperation(OperationShape operation, UnisonWriter writer, UnisonContext context);
    
    /**
     * Generates request serialization code for an operation.
     * 
     * <p>Serializes the input structure to the protocol format (XML, JSON, etc.).
     * Handles:
     * <ul>
     *   <li>Body members (serialized to request body)</li>
     *   <li>@httpPayload members (raw payload handling)</li>
     * </ul>
     * 
     * @param operation The operation to generate serialization for
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    void generateRequestSerializer(OperationShape operation, UnisonWriter writer, UnisonContext context);
    
    /**
     * Generates response deserialization code for an operation.
     * 
     * <p>Deserializes the HTTP response body to the output structure.
     * Handles:
     * <ul>
     *   <li>XML/JSON parsing</li>
     *   <li>@httpPayload members (raw response body)</li>
     *   <li>@httpHeader members (response headers)</li>
     *   <li>Empty responses</li>
     * </ul>
     * 
     * @param operation The operation to generate deserialization for
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context);
    
    /**
     * Generates error parsing code for the protocol.
     * 
     * <p>Parses error responses specific to this protocol format.
     * For REST-XML: parses XML error elements.
     * For REST-JSON: parses JSON error fields.
     * 
     * @param operation The operation (for operation-specific errors)
     * @param writer The Unison code writer
     * @param context The code generation context
     */
    void generateErrorParser(OperationShape operation, UnisonWriter writer, UnisonContext context);
    
    /**
     * Gets the Content-Type header value for this protocol.
     * 
     * @param service The service shape
     * @return The content type (e.g., "application/xml", "application/json")
     */
    String getContentType(ServiceShape service);
    
    /**
     * Gets the default HTTP method for this protocol.
     * 
     * <p>REST protocols typically return null since they use the @http trait.
     * RPC protocols like AWS Query default to POST.
     * 
     * @return The default method or null if determined by @http trait
     */
    default String getDefaultMethod() {
        return "POST";
    }
    
    /**
     * Gets the default URI path for this protocol.
     * 
     * <p>REST protocols typically return null since they use the @http trait.
     * RPC protocols default to "/".
     * 
     * @return The default URI or null if determined by @http trait
     */
    default String getDefaultUri() {
        return "/";
    }
    
    /**
     * Checks if this generator applies to a given service.
     * 
     * @param service The service to check
     * @return true if this generator handles the service's protocol
     */
    default boolean appliesTo(ServiceShape service) {
        return service.findTrait(getProtocol()).isPresent();
    }
}
