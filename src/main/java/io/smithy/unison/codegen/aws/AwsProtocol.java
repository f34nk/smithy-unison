package io.smithy.unison.codegen.aws;

import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Enumeration of AWS protocols supported by the Unison code generator.
 * 
 * <p><b>NOT FULLY IMPLEMENTED</b>: Protocols are recognized but code generation is stubbed.
 */
public enum AwsProtocol {
    
    AWS_JSON_1_0("aws.protocols#awsJson1_0", "application/x-amz-json-1.0"),
    AWS_JSON_1_1("aws.protocols#awsJson1_1", "application/x-amz-json-1.1"),
    REST_JSON_1("aws.protocols#restJson1", "application/json"),
    REST_XML("aws.protocols#restXml", "application/xml"),
    AWS_QUERY("aws.protocols#awsQuery", "application/x-www-form-urlencoded"),
    EC2_QUERY("aws.protocols#ec2Query", "application/x-www-form-urlencoded"),
    UNKNOWN("unknown#unknown", "application/octet-stream");
    
    private final ShapeId traitId;
    private final String contentType;
    
    AwsProtocol(String traitId, String contentType) {
        this.traitId = ShapeId.from(traitId);
        this.contentType = contentType;
    }
    
    /**
     * Gets the trait ShapeId for this protocol.
     */
    public ShapeId getTraitId() {
        return traitId;
    }
    
    /**
     * Gets the Content-Type header value for this protocol.
     */
    public String getContentType() {
        return contentType;
    }
    
    /**
     * Checks if this protocol uses JSON serialization.
     */
    public boolean isJson() {
        return this == AWS_JSON_1_0 || this == AWS_JSON_1_1 || this == REST_JSON_1;
    }
    
    /**
     * Checks if this protocol uses XML serialization.
     */
    public boolean isXml() {
        return this == REST_XML || this == AWS_QUERY || this == EC2_QUERY;
    }
    
    /**
     * Gets the AwsProtocol from a trait ShapeId.
     */
    public static AwsProtocol fromTraitId(ShapeId traitId) {
        for (AwsProtocol protocol : values()) {
            if (protocol.traitId.equals(traitId)) {
                return protocol;
            }
        }
        return UNKNOWN;
    }
}
