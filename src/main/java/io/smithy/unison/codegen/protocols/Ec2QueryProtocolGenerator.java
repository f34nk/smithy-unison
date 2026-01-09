package io.smithy.unison.codegen.protocols;

import io.smithy.unison.codegen.UnisonWriter;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Protocol generator for EC2 Query protocol.
 * 
 * <p>EC2 Query is a variant of AWS Query with these key differences:
 * <ul>
 *   <li>Response wrapper: Uses {@code <OperationNameResponse>} instead of 
 *       {@code <OperationNameResponse><OperationNameResult>}</li>
 *   <li>Error structure: Uses {@code <Response><Errors><Error>} instead of 
 *       {@code <ErrorResponse><Error>}</li>
 * </ul>
 * 
 * <h2>EC2 Query Response Format</h2>
 * <pre>
 * &lt;RunInstancesResponse&gt;
 *   &lt;requestId&gt;...&lt;/requestId&gt;
 *   &lt;reservationId&gt;...&lt;/reservationId&gt;
 *   ...
 * &lt;/RunInstancesResponse&gt;
 * </pre>
 * 
 * <h2>EC2 Query Error Format</h2>
 * <pre>
 * &lt;Response&gt;
 *   &lt;Errors&gt;
 *     &lt;Error&gt;
 *       &lt;Code&gt;InvalidParameterValue&lt;/Code&gt;
 *       &lt;Message&gt;...&lt;/Message&gt;
 *     &lt;/Error&gt;
 *   &lt;/Errors&gt;
 *   &lt;RequestID&gt;...&lt;/RequestID&gt;
 * &lt;/Response&gt;
 * </pre>
 * 
 * @see AwsQueryProtocolGenerator
 */
public class Ec2QueryProtocolGenerator extends AwsQueryProtocolGenerator {
    
    public static final ShapeId EC2_QUERY = ShapeId.from("aws.protocols#ec2Query");
    
    @Override
    public ShapeId getProtocol() {
        return EC2_QUERY;
    }
    
    @Override
    public String getName() {
        return "ec2Query";
    }
    
    /**
     * EC2 Query uses a simpler response structure without the nested Result element.
     * 
     * <p>AWS Query: {@code <OperationNameResponse><OperationNameResult>...</OperationNameResult></OperationNameResponse>}
     * <p>EC2 Query: {@code <OperationNameResponse>...</OperationNameResponse>}
     */
    @Override
    protected void generateResponseWrapperNavigation(OperationShape operation, UnisonWriter writer) {
        String operationName = operation.getId().getName();
        
        writer.write("-- EC2 Query response structure (no nested Result element):");
        writer.write("-- <OperationNameResponse>...</OperationNameResponse>");
        writer.write("soup = Soup.parseXML (fromUtf8 (Http.Response.body response))");
        writer.write("resultSoup = !(Soup.findFirst \"$LResponse\" soup)", operationName);
        writer.write("-- EC2 Query: response element IS the result element");
    }
    
    /**
     * EC2 Query uses a different error XML structure.
     * 
     * <p>AWS Query: {@code <ErrorResponse><Error>...}
     * <p>EC2 Query: {@code <Response><Errors><Error>...}
     */
    @Override
    protected String getErrorParserDocComment() {
        return "Parse EC2 Query error response\n\n" +
                "EC2 Query error format:\n" +
                "<Response>\n" +
                "  <Errors>\n" +
                "    <Error>\n" +
                "      <Code>InvalidParameterValue</Code>\n" +
                "      <Message>...</Message>\n" +
                "    </Error>\n" +
                "  </Errors>\n" +
                "  <RequestID>...</RequestID>\n" +
                "</Response>";
    }
    
    @Override
    protected void generateErrorParserBody(ServiceShape service, String clientNamespace, 
                                          String errorTypeName, UnisonWriter writer) {
        // Parse EC2 Query error XML structure using Soup
        writer.write("-- Parse EC2 Query error response using Soup");
        writer.write("soup = Soup.parseXML (fromUtf8 (Http.Response.body response))");
        writer.write("errorSoup = !(aws.xml.findAndDrill soup [\"Response\", \"Errors\", \"Error\"])");
        writer.write("code = aws.xml.findText \"Code\" errorSoup |> Optional.getOrElse \"UnknownError\"");
        writer.write("message = aws.xml.findText \"Message\" errorSoup |> Optional.getOrElse \"\"");
        writer.write("");
        writer.write("-- Map to service-specific error type");
        writer.write("$L.fromCodeAndMessage code message", errorTypeName);
    }
}
