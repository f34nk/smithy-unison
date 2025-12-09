package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Protocol generator for EC2 Query protocol.
 * 
 * <p>Used by EC2 and Auto Scaling services.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public class Ec2QueryProtocolGenerator implements ProtocolGenerator {
    
    public static final ShapeId EC2_QUERY = ShapeId.from("aws.protocols#ec2Query");
    
    @Override
    public ShapeId getProtocol() {
        return EC2_QUERY;
    }
    
    @Override
    public String getName() {
        return "ec2Query";
    }
    
    @Override
    public String getContentType(ServiceShape service) {
        return "application/x-www-form-urlencoded";
    }
    
    @Override
    public void generateOperation(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement EC2 Query operation generation
        String opName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName());
        writer.writeComment("EC2 Query operation: " + opName + " (NOT IMPLEMENTED)");
    }
    
    @Override
    public void generateRequestSerializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement EC2 Query request serialization
    }
    
    @Override
    public void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement EC2 Query response deserialization
    }
    
    @Override
    public void generateErrorParser(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement EC2 Query error parsing
        writer.writeComment("EC2 Query error parsing (NOT IMPLEMENTED)");
    }
}
