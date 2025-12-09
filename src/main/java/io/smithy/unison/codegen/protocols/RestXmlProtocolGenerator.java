package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Protocol generator for REST-XML protocol.
 * 
 * <p>Used by S3, CloudFront, Route 53, and other XML-based AWS services.
 * 
 * <p><b>NOT IMPLEMENTED</b>: Stub for first draft.
 */
public class RestXmlProtocolGenerator implements ProtocolGenerator {
    
    public static final ShapeId REST_XML = ShapeId.from("aws.protocols#restXml");
    
    @Override
    public ShapeId getProtocol() {
        return REST_XML;
    }
    
    @Override
    public String getName() {
        return "restXml";
    }
    
    @Override
    public String getDefaultMethod() {
        return null; // REST protocols use @http trait for method
    }
    
    @Override
    public String getDefaultUri() {
        return null; // REST protocols use @http trait for URI
    }
    
    @Override
    public String getContentType(ServiceShape service) {
        return "application/xml";
    }
    
    @Override
    public void generateOperation(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement REST-XML operation generation
        // See ErlangWriter.RestXmlProtocolGenerator for reference
        
        String opName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName());
        
        writer.writeComment("REST-XML operation: " + opName);
        writer.writeComment("NOT IMPLEMENTED: Protocol generation is stubbed");
        writer.writeBlankLine();
    }
    
    @Override
    public void generateRequestSerializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement REST-XML request serialization
        writer.writeComment("TODO: Implement REST-XML request serialization");
    }
    
    @Override
    public void generateResponseDeserializer(OperationShape operation, UnisonWriter writer, UnisonContext context) {
        // TODO: Implement REST-XML response deserialization
        writer.writeComment("TODO: Implement REST-XML response deserialization");
    }
}
