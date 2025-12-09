package io.smithy.unison.codegen;

import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.Objects;
import java.util.Optional;

/**
 * Settings for the Unison code generator.
 * 
 * <p>This class follows the Smithy Development Guide recommendations for
 * code generator settings, providing a clean API with immutable configuration.
 * 
 * <p>Settings can be created from a smithy-build.json plugin configuration:
 * <pre>
 * {
 *   "plugins": {
 *     "unison-codegen": {
 *       "service": "com.example#MyService",
 *       "namespace": "aws.s3",
 *       "outputDir": "src/generated",
 *       "protocol": "aws.protocols#restJson1"
 *     }
 *   }
 * }
 * </pre>
 */
public final class UnisonSettings {
    
    private static final String DEFAULT_OUTPUT_DIR = "src/generated";
    
    private final ShapeId service;
    private final String namespace;
    private final String outputDir;
    private final String protocol;
    
    private UnisonSettings(Builder builder) {
        this.service = Objects.requireNonNull(builder.service, "service is required");
        this.namespace = builder.namespace;
        this.outputDir = builder.outputDir != null ? builder.outputDir : DEFAULT_OUTPUT_DIR;
        this.protocol = builder.protocol;
    }
    
    /**
     * Creates settings from a configuration ObjectNode.
     *
     * @param node Configuration object from smithy-build.json
     * @return UnisonSettings instance
     */
    public static UnisonSettings from(ObjectNode node) {
        Builder builder = builder();
        
        node.getStringMember("service")
                .map(n -> ShapeId.from(n.getValue()))
                .ifPresent(builder::service);
        
        node.getStringMember("namespace")
                .map(n -> n.getValue())
                .ifPresent(builder::namespace);
        
        node.getStringMember("outputDir")
                .map(n -> n.getValue())
                .ifPresent(builder::outputDir);
        
        node.getStringMember("protocol")
                .map(n -> n.getValue())
                .ifPresent(builder::protocol);
        
        return builder.build();
    }
    
    /**
     * Gets the service shape ID to generate a client for.
     */
    public ShapeId service() {
        return service;
    }
    
    /**
     * Gets the Unison namespace for the generated client.
     */
    public String namespace() {
        return namespace;
    }
    
    /**
     * Gets the output directory for generated files.
     */
    public String outputDir() {
        return outputDir;
    }
    
    /**
     * Gets the protocol to use for code generation.
     */
    public String protocol() {
        return protocol;
    }
    
    /**
     * Gets the protocol as an Optional.
     */
    public Optional<String> getProtocol() {
        return Optional.ofNullable(protocol);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public Builder toBuilder() {
        return builder()
                .service(service)
                .namespace(namespace)
                .outputDir(outputDir)
                .protocol(protocol);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnisonSettings that = (UnisonSettings) o;
        return Objects.equals(service, that.service) &&
               Objects.equals(namespace, that.namespace) &&
               Objects.equals(outputDir, that.outputDir) &&
               Objects.equals(protocol, that.protocol);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(service, namespace, outputDir, protocol);
    }
    
    @Override
    public String toString() {
        return "UnisonSettings{" +
               "service=" + service +
               ", namespace='" + namespace + '\'' +
               ", outputDir='" + outputDir + '\'' +
               ", protocol='" + protocol + '\'' +
               '}';
    }
    
    public static final class Builder {
        private ShapeId service;
        private String namespace;
        private String outputDir;
        private String protocol;
        
        private Builder() {}
        
        public Builder service(ShapeId service) {
            this.service = service;
            return this;
        }
        
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }
        
        public Builder outputDir(String outputDir) {
            this.outputDir = outputDir;
            return this;
        }
        
        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }
        
        public UnisonSettings build() {
            return new UnisonSettings(this);
        }
    }
}
