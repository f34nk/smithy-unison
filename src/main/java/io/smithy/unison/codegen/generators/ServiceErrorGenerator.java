package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.ErrorTrait;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Generates a service-level error sum type that aggregates all error types for a service.
 * 
 * <p>This generator collects all error shapes referenced by operations in a service
 * and creates a unified sum type for error handling. This enables idiomatic Unison
 * pattern matching on service errors.
 * 
 * <h2>Example Output for S3</h2>
 * <pre>
 * type S3ServiceError
 *   = S3ServiceError'NoSuchBucket NoSuchBucket
 *   | S3ServiceError'NoSuchKey NoSuchKey
 *   | S3ServiceError'BucketAlreadyExists BucketAlreadyExists
 *   | S3ServiceError'UnknownError Text
 * 
 * S3ServiceError.toFailure : S3ServiceError -> IO.Failure
 * S3ServiceError.toFailure = cases
 *   S3ServiceError'NoSuchBucket e -> NoSuchBucket.toFailure e
 *   S3ServiceError'NoSuchKey e -> NoSuchKey.toFailure e
 *   S3ServiceError'BucketAlreadyExists e -> BucketAlreadyExists.toFailure e
 *   S3ServiceError'UnknownError msg -> IO.Failure.Failure (typeLink Text) msg (Any msg)
 * </pre>
 * 
 * @see ErrorGenerator
 */
public final class ServiceErrorGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(ServiceErrorGenerator.class.getName());
    
    private final String serviceName;
    private final String typeName;
    private final List<ErrorVariant> errorVariants;
    
    /**
     * Represents an error variant in the service error sum type.
     */
    public static final class ErrorVariant {
        private final String variantName;
        private final String errorTypeName;
        
        public ErrorVariant(String variantName, String errorTypeName) {
            this.variantName = variantName;
            this.errorTypeName = errorTypeName;
        }
        
        /** The variant name (e.g., "NoSuchBucket"). */
        public String variantName() { return variantName; }
        
        /** The error type name (e.g., "NoSuchBucket"). */
        public String errorTypeName() { return errorTypeName; }
    }
    
    /**
     * Creates a service error generator from a Smithy service shape.
     *
     * @param service The service shape
     * @param model The Smithy model
     */
    public ServiceErrorGenerator(ServiceShape service, Model model) {
        Objects.requireNonNull(service, "service is required");
        Objects.requireNonNull(model, "model is required");
        
        this.serviceName = UnisonSymbolProvider.toUnisonTypeName(service.getId().getName());
        this.typeName = serviceName + "ServiceError";
        this.errorVariants = collectErrorVariants(service, model);
    }
    
    /**
     * Creates a service error generator with explicit values (for testing).
     *
     * @param serviceName The service name
     * @param errorVariants The error variants
     */
    public ServiceErrorGenerator(String serviceName, List<ErrorVariant> errorVariants) {
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName is required");
        this.typeName = serviceName + "ServiceError";
        this.errorVariants = errorVariants != null ? errorVariants : new ArrayList<>();
    }
    
    /**
     * Gets the service name.
     *
     * @return The PascalCase service name
     */
    public String getServiceName() {
        return serviceName;
    }
    
    /**
     * Gets the service error type name.
     *
     * @return The type name (e.g., "S3ServiceError")
     */
    public String getTypeName() {
        return typeName;
    }
    
    /**
     * Gets the error variants.
     *
     * @return List of error variants
     */
    public List<ErrorVariant> getErrorVariants() {
        return errorVariants;
    }
    
    /**
     * Gets the full variant name for an error type.
     *
     * @param errorTypeName The error type name
     * @return The full variant name (TypeName'ErrorName)
     */
    public String getVariantName(String errorTypeName) {
        return UnisonSymbolProvider.toUnisonEnumVariant(typeName, errorTypeName);
    }
    
    /**
     * Generates the complete service error code: type definition and helper functions.
     *
     * @param writer The writer to output code to
     */
    public void generate(UnisonWriter writer) {
        LOGGER.fine("Generating service error type: " + typeName);
        
        generateTypeDefinition(writer);
        generateToFailureFunction(writer);
        generateFromCodeAndMessageFunction(writer);
    }
    
    /**
     * Generates the complete service error code with protocol-specific parsing.
     *
     * @param writer The writer to output code to
     * @param protocol The protocol type ("xml" or "json")
     */
    public void generate(UnisonWriter writer, String protocol) {
        LOGGER.fine("Generating service error type: " + typeName + " with " + protocol + " parsing");
        
        generateTypeDefinition(writer);
        generateToFailureFunction(writer);
        generateFromCodeAndMessageFunction(writer);
        
        if ("xml".equalsIgnoreCase(protocol)) {
            generateFromXmlFunction(writer);
        } else if ("json".equalsIgnoreCase(protocol)) {
            generateFromJsonFunction(writer);
        }
    }
    
    /**
     * Generates the service error sum type definition.
     *
     * @param writer The writer to output code to
     */
    public void generateTypeDefinition(UnisonWriter writer) {
        // Write documentation
        writer.writeDocComment("Aggregated error type for " + serviceName + " service.\n\n" +
                "Use pattern matching to handle specific error types.");
        
        // Build variants list
        List<UnisonWriter.Variant> variants = new ArrayList<>();
        
        // Add variant for each error type
        for (ErrorVariant errorVariant : errorVariants) {
            String fullVariantName = getVariantName(errorVariant.variantName());
            variants.add(new UnisonWriter.Variant(fullVariantName, errorVariant.errorTypeName()));
        }
        
        // Add UnknownError catch-all variant
        String unknownVariant = getVariantName("UnknownError");
        variants.add(new UnisonWriter.Variant(unknownVariant, "Text"));
        
        // Write union type
        writer.writeUnionType(typeName, variants);
    }
    
    /**
     * Generates the toFailure function for the service error type.
     *
     * @param writer The writer to output code to
     */
    public void generateToFailureFunction(UnisonWriter writer) {
        String funcName = typeName + ".toFailure";
        
        // Write signature
        writer.write("$L : $L -> IO.Failure", funcName, typeName);
        writer.write("$L = cases", funcName);
        writer.indent();
        
        // Generate case for each error variant
        for (ErrorVariant errorVariant : errorVariants) {
            String fullVariantName = getVariantName(errorVariant.variantName());
            writer.write("$L e -> $L.toFailure e", fullVariantName, errorVariant.errorTypeName());
        }
        
        // Generate case for UnknownError
        String unknownVariant = getVariantName("UnknownError");
        writer.write("$L msg -> IO.Failure.Failure (typeLink Text) msg (Any msg)", unknownVariant);
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the fromCodeAndMessage function for parsing error responses.
     * 
     * <p>This is the core error parsing function that maps error codes to
     * appropriate service error variants.
     *
     * <h2>Example Output</h2>
     * <pre>
     * S3ServiceError.fromCodeAndMessage : Text -> Text -> S3ServiceError
     * S3ServiceError.fromCodeAndMessage code message = match code with
     *   "NoSuchBucket" -> S3ServiceError'NoSuchBucket { message }
     *   "NoSuchKey" -> S3ServiceError'NoSuchKey { message }
     *   _ -> S3ServiceError'UnknownError (code ++ ": " ++ message)
     * </pre>
     *
     * @param writer The writer to output code to
     */
    public void generateFromCodeAndMessageFunction(UnisonWriter writer) {
        String funcName = typeName + ".fromCodeAndMessage";
        
        // Write signature
        writer.write("$L : Text -> Text -> $L", funcName, typeName);
        writer.write("$L code message = match code with", funcName);
        writer.indent();
        
        // Generate case for each error variant
        for (ErrorVariant errorVariant : errorVariants) {
            String fullVariantName = getVariantName(errorVariant.variantName());
            // Use variant name as the error code (typically matches Smithy shape name)
            writer.write("\"$L\" -> $L { message }", errorVariant.variantName(), fullVariantName);
        }
        
        // Generate catch-all for unknown error codes
        String unknownVariant = getVariantName("UnknownError");
        writer.write("_ -> $L (code ++ \": \" ++ message)", unknownVariant);
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the fromXml function for parsing XML error responses.
     * 
     * <p>This is used by REST-XML protocols like S3.
     *
     * <h2>Example Output</h2>
     * <pre>
     * S3ServiceError.fromXml : Text -> S3ServiceError
     * S3ServiceError.fromXml xmlText =
     *   -- Parse error code and message from XML
     *   -- Expected format: &lt;Error&gt;&lt;Code&gt;...&lt;/Code&gt;&lt;Message&gt;...&lt;/Message&gt;&lt;/Error&gt;
     *   code = extractXmlTag "Code" xmlText |> Optional.getOrElse "UnknownError"
     *   message = extractXmlTag "Message" xmlText |> Optional.getOrElse ""
     *   S3ServiceError.fromCodeAndMessage code message
     * </pre>
     *
     * @param writer The writer to output code to
     */
    public void generateFromXmlFunction(UnisonWriter writer) {
        String funcName = typeName + ".fromXml";
        
        // Write documentation
        writer.writeDocComment("Parse XML error response to " + typeName + ".\n\n" +
                "Expected format: <Error><Code>...</Code><Message>...</Message></Error>");
        
        // Write signature
        writer.write("$L : Text -> $L", funcName, typeName);
        writer.write("$L xmlText =", funcName);
        writer.indent();
        writer.write("-- Extract error code and message from XML");
        writer.write("code = extractXmlTag \"Code\" xmlText |> Optional.getOrElse \"UnknownError\"");
        writer.write("message = extractXmlTag \"Message\" xmlText |> Optional.getOrElse \"\"");
        writer.write("$L.fromCodeAndMessage code message", typeName);
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the fromJson function for parsing JSON error responses.
     * 
     * <p>This is used by REST-JSON protocols.
     *
     * <h2>Example Output</h2>
     * <pre>
     * ServiceError.fromJson : Text -> ServiceError
     * ServiceError.fromJson jsonText =
     *   -- Parse error code and message from JSON
     *   -- Expected format: {"__type": "ErrorCode", "message": "..."}
     *   code = extractJsonField "__type" jsonText |> Optional.getOrElse "UnknownError"
     *   message = extractJsonField "message" jsonText |> Optional.getOrElse ""
     *   ServiceError.fromCodeAndMessage code message
     * </pre>
     *
     * @param writer The writer to output code to
     */
    public void generateFromJsonFunction(UnisonWriter writer) {
        String funcName = typeName + ".fromJson";
        
        // Write documentation
        writer.writeDocComment("Parse JSON error response to " + typeName + ".\n\n" +
                "Expected format: {\"__type\": \"ErrorCode\", \"message\": \"...\"}");
        
        // Write signature
        writer.write("$L : Text -> $L", funcName, typeName);
        writer.write("$L jsonText =", funcName);
        writer.indent();
        writer.write("-- Extract error code and message from JSON");
        writer.write("code = extractJsonField \"__type\" jsonText |> Optional.getOrElse \"UnknownError\"");
        writer.write("message = extractJsonField \"message\" jsonText |> Optional.getOrElse \"\"");
        writer.write("$L.fromCodeAndMessage code message", typeName);
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Collects all error variants from the service's operations.
     */
    private List<ErrorVariant> collectErrorVariants(ServiceShape service, Model model) {
        // Use LinkedHashSet to maintain order and avoid duplicates
        Set<String> seenErrors = new LinkedHashSet<>();
        List<ErrorVariant> variants = new ArrayList<>();
        
        // Collect errors from all operations
        for (ShapeId operationId : service.getAllOperations()) {
            OperationShape operation = model.expectShape(operationId, OperationShape.class);
            
            for (ShapeId errorId : operation.getErrors()) {
                String errorName = errorId.getName();
                
                if (!seenErrors.contains(errorName)) {
                    seenErrors.add(errorName);
                    
                    // Verify it's actually an error shape
                    StructureShape errorShape = model.expectShape(errorId, StructureShape.class);
                    if (errorShape.hasTrait(ErrorTrait.class)) {
                        String typeName = UnisonSymbolProvider.toUnisonTypeName(errorName);
                        variants.add(new ErrorVariant(typeName, typeName));
                    }
                }
            }
        }
        
        return variants;
    }
}
