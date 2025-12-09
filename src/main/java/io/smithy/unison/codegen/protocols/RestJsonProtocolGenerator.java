package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Protocol generator for REST-JSON protocol.
 * 
 * <p>Used by API Gateway, Step Functions, and other REST-JSON services.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public class RestJsonProtocolGenerator implements ProtocolGenerator {
    
    public static final ShapeId REST_JSON = ShapeId.from("aws.protocols#restJson1");
    
    @Override
    public ShapeId getProtocol() {
        return REST_JSON;
    }
    
    @Override
    public String getName() {
        return "restJson1";
    }
    
    @Override
    public String getDefaultMethod() {
        return null;
    }
    
    @Override
    public String getDefaultUri() {
        return null;
    }
    
    @Override
    public String getContentType(ServiceShape service) {
        return "application/json";
    }
    
    @Override
    public void generateOperation(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement REST-JSON operation generation
        String opName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName());
        writer.writeComment("REST-JSON operation: " + opName + " (NOT IMPLEMENTED)");
    }
    
    @Override
    public void generateRequestSerializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement REST-JSON request serialization
    }
    
    @Override
    public void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement REST-JSON response deserialization
    }
}
