package io.smithy.unison.codegen.aws;

import java.util.Objects;

/**
 * Information about an AWS service endpoint.
 */
public final class EndpointInfo {
    
    private final String hostname;
    private final String protocol;
    private final String signingRegion;
    
    public EndpointInfo(String hostname, String protocol, String signingRegion) {
        this.hostname = Objects.requireNonNull(hostname, "hostname is required");
        this.protocol = Objects.requireNonNull(protocol, "protocol is required");
        this.signingRegion = Objects.requireNonNull(signingRegion, "signingRegion is required");
    }
    
    /**
     * Gets the hostname for the endpoint.
     */
    public String getHostname() {
        return hostname;
    }
    
    /**
     * Gets the protocol (http or https).
     */
    public String getProtocol() {
        return protocol;
    }
    
    /**
     * Gets the AWS region for signing requests.
     */
    public String getSigningRegion() {
        return signingRegion;
    }
    
    /**
     * Gets the full URL for this endpoint.
     */
    public String getUrl() {
        return protocol + "://" + hostname;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointInfo that = (EndpointInfo) o;
        return Objects.equals(hostname, that.hostname) &&
               Objects.equals(protocol, that.protocol) &&
               Objects.equals(signingRegion, that.signingRegion);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(hostname, protocol, signingRegion);
    }
    
    @Override
    public String toString() {
        return "EndpointInfo{" +
               "hostname='" + hostname + '\'' +
               ", protocol='" + protocol + '\'' +
               ", signingRegion='" + signingRegion + '\'' +
               '}';
    }
}
