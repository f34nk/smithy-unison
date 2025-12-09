package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Protocol generator for AWS JSON 1.0/1.1 protocols.
 * 
 * <p>Used by DynamoDB, Lambda, Kinesis, and other JSON-based services.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public class AwsJsonProtocolGenerator implements ProtocolGenerator {
    
    public static final ShapeId AWS_JSON_1_0 = ShapeId.from("aws.protocols#awsJson1_0");
    public static final ShapeId AWS_JSON_1_1 = ShapeId.from("aws.protocols#awsJson1_1");
    
    private final ShapeId protocol;
    private final String version;
    
    public AwsJsonProtocolGenerator() {
        this(AWS_JSON_1_1, "1.1");
    }
    
    public AwsJsonProtocolGenerator(ShapeId protocol, String version) {
        this.protocol = protocol;
        this.version = version;
    }
    
    @Override
    public ShapeId getProtocol() {
        return protocol;
    }
    
    @Override
    public String getName() {
        return "awsJson" + version.replace(".", "_");
    }
    
    @Override
    public String getContentType(ServiceShape service) {
        return "application/x-amz-json-" + version;
    }
    
    @Override
    public void generateOperation(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS JSON operation generation
        String opName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName());
        writer.writeComment("AWS JSON " + version + " operation: " + opName + " (NOT IMPLEMENTED)");
    }
    
    @Override
    public void generateRequestSerializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS JSON request serialization
    }
    
    @Override
    public void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS JSON response deserialization
    }
    
    @Override
    public void generateErrorParser(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement AWS JSON error parsing
        writer.writeComment("AWS JSON error parsing (NOT IMPLEMENTED)");
    }
}
