package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Protocol generator for AWS Query protocol.
 * 
 * <p>Used by SQS, SNS, RDS, IAM, STS, and other Query-based services.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public class AwsQueryProtocolGenerator implements ProtocolGenerator {
    
    public static final ShapeId AWS_QUERY = ShapeId.from("aws.protocols#awsQuery");
    
    @Override
    public ShapeId getProtocol() {
        return AWS_QUERY;
    }
    
    @Override
    public String getName() {
        return "awsQuery";
    }
    
    @Override
    public String getContentType(ServiceShape service) {
        return "application/x-www-form-urlencoded";
    }
    
    @Override
    public void generateOperation(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS Query operation generation
        String opName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName());
        writer.writeComment("AWS Query operation: " + opName + " (NOT IMPLEMENTED)");
    }
    
    @Override
    public void generateRequestSerializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS Query request serialization
    }
    
    @Override
    public void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS Query response deserialization
    }
    
    @Override
    public void generateErrorParser(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS Query error parsing
        writer.writeComment("AWS Query error parsing (NOT IMPLEMENTED)");
    }
}
