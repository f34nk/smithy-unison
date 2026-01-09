package io.smithy.unison.codegen;

import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final List<String> operations;
    private final boolean generateAllOperations;
    
    private UnisonSettings(Builder builder) {
        this.service = Objects.requireNonNull(builder.service, "service is required");
        this.namespace = builder.namespace;
        this.outputDir = builder.outputDir != null ? builder.outputDir : DEFAULT_OUTPUT_DIR;
        this.protocol = builder.protocol;
        this.operations = builder.operations != null ? List.copyOf(builder.operations) : Collections.emptyList();
        this.generateAllOperations = builder.generateAllOperations;
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
        
        // Parse operations list
        node.getArrayMember("operations")
            .map(arrayNode -> {
                List<String> ops = new ArrayList<>();
                for (var element : arrayNode.getElements()) {
                    if (element.isStringNode()) {
                        ops.add(element.asStringNode().get().getValue());
                    }
                }
                return ops;
            })
            .ifPresent(builder::operations);
        
        // Parse generateAllOperations flag (defaults to true)
        node.getBooleanMember("generateAllOperations")
            .map(BooleanNode::getValue)
            .ifPresent(builder::generateAllOperations);
        
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
    
    /**
     * Gets the Unison namespace for client types and operations.
     * 
     * <p>Returns the namespace as-is (lowercase per Unison naming convention):
     * <ul>
     *   <li>"aws.s3" → "aws.s3"</li>
     *   <li>"aws.dynamodb" → "aws.dynamodb"</li>
     *   <li>"aws.lambda" → "aws.lambda"</li>
     * </ul>
     * 
     * @return The client namespace prefix, or empty string if no namespace configured
     */
    public String getClientNamespace() {
        if (namespace == null || namespace.isEmpty()) {
            return "";
        }
        // Return namespace as-is (lowercase per Unison naming convention)
        return namespace;
    }
    
    /**
     * Gets the list of operations to generate.
     * 
     * @return List of operation names, or empty list if none specified
     */
    public List<String> operations() {
        return operations;
    }
    
    /**
     * Gets whether to generate all operations (default behavior).
     * 
     * @return true to generate all operations, false for selective generation
     */
    public boolean generateAllOperations() {
        return generateAllOperations;
    }
    
    /**
     * Checks if selective operation filtering is enabled.
     * 
     * @return true if operations list is specified and generateAllOperations is false
     */
    public boolean hasOperationFilter() {
        return !operations.isEmpty() && !generateAllOperations;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public Builder toBuilder() {
        return builder()
                .service(service)
                .namespace(namespace)
                .outputDir(outputDir)
                .protocol(protocol)
                .operations(operations)
                .generateAllOperations(generateAllOperations);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnisonSettings that = (UnisonSettings) o;
        return generateAllOperations == that.generateAllOperations &&
               Objects.equals(service, that.service) &&
               Objects.equals(namespace, that.namespace) &&
               Objects.equals(outputDir, that.outputDir) &&
               Objects.equals(protocol, that.protocol) &&
               Objects.equals(operations, that.operations);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(service, namespace, outputDir, protocol, operations, generateAllOperations);
    }
    
    @Override
    public String toString() {
        return "UnisonSettings{" +
               "service=" + service +
               ", namespace='" + namespace + '\'' +
               ", outputDir='" + outputDir + '\'' +
               ", protocol='" + protocol + '\'' +
               ", operations=" + operations +
               ", generateAllOperations=" + generateAllOperations +
               '}';
    }
    
    public static final class Builder {
        private ShapeId service;
        private String namespace;
        private String outputDir;
        private String protocol;
        private List<String> operations;
        private boolean generateAllOperations = true;  // Default: generate all operations
        
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
        
        public Builder operations(List<String> operations) {
            this.operations = operations;
            return this;
        }
        
        public Builder generateAllOperations(boolean generateAllOperations) {
            this.generateAllOperations = generateAllOperations;
            return this;
        }
        
        public UnisonSettings build() {
            // Validate: can't specify operations AND generateAllOperations=true
            if (operations != null && !operations.isEmpty() && generateAllOperations) {
                throw new IllegalArgumentException(
                    "Cannot specify both 'operations' list and 'generateAllOperations=true'. " +
                    "Either omit 'operations' to generate all, or set 'generateAllOperations=false'");
            }
            
            return new UnisonSettings(this);
        }
    }
}
