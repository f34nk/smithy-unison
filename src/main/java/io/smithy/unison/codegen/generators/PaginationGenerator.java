package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.EnumTrait;
import software.amazon.smithy.model.traits.PaginatedTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Generates pagination helper functions for paginated operations.
 * 
 * <p>This generator creates auto-paginating versions of operations that have
 * the {@code @paginated} trait. These helpers automatically handle continuation
 * tokens and collect all results across multiple pages.
 * 
 * <h2>Generated Functions</h2>
 * <p>For each paginated operation, generates:
 * <ul>
 *   <li>{@code operationAll} - Collects all items from all pages</li>
 * </ul>
 * 
 * <h2>Example</h2>
 * <p>For {@code ListObjectsV2} with pagination, generates:
 * <pre>
 * listObjectsV2All : Config -> ListObjectsV2Request -> '{IO, Exception} [S3Object]
 * listObjectsV2All config input =
 *   let
 *     go token acc =
 *       inputWithToken = ListObjectsV2Request.continuationToken.set token input
 *       response = listObjectsV2 config inputWithToken
 *       newItems = Optional.getOrElse [] (ListObjectsV2Output.contents response)
 *       allItems = acc ++ newItems
 *       match (ListObjectsV2Output.nextContinuationToken response) with
 *         Some next -> go (Some next) allItems
 *         None -> allItems
 *   go None []
 * </pre>
 * 
 * <h2>Supported Operations</h2>
 * <p>S3 paginated operations:
 * <ul>
 *   <li>ListBuckets</li>
 *   <li>ListDirectoryBuckets</li>
 *   <li>ListObjectsV2</li>
 *   <li>ListParts</li>
 *   <li>ListMultipartUploads</li>
 *   <li>ListObjectVersions</li>
 * </ul>
 */
public class PaginationGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(PaginationGenerator.class.getName());
    
    private final String clientNamespace;
    private Model model;
    
    /**
     * Creates a new PaginationGenerator without namespace.
     */
    public PaginationGenerator() {
        this("");
    }
    
    /**
     * Creates a new PaginationGenerator with namespace.
     *
     * @param clientNamespace The client namespace for prefixing types
     */
    public PaginationGenerator(String clientNamespace) {
        this.clientNamespace = clientNamespace != null ? clientNamespace : "";
    }
    
    /**
     * Gets all paginated operations from a service.
     * 
     * @param service The service shape
     * @param model The Smithy model
     * @return List of operations with @paginated trait
     */
    public List<OperationShape> getPaginatedOperations(ServiceShape service, Model model) {
        List<OperationShape> result = new ArrayList<>();
        
        for (ShapeId opId : service.getOperations()) {
            OperationShape operation = model.expectShape(opId, OperationShape.class);
            if (operation.hasTrait(PaginatedTrait.class)) {
                result.add(operation);
            }
        }
        
        return result;
    }
    
    /**
     * Generates pagination helper functions for all paginated operations.
     * 
     * @param service The service shape
     * @param model The Smithy model
     * @param writer The Unison code writer
     */
    public void generate(ServiceShape service, Model model, UnisonWriter writer) {
        List<OperationShape> paginatedOps = getPaginatedOperations(service, model);
        
        if (paginatedOps.isEmpty()) {
            LOGGER.fine("No paginated operations found in service: " + service.getId());
            return;
        }
        
        LOGGER.fine("Found " + paginatedOps.size() + " paginated operations in " + service.getId());
        
        writer.writeComment("=== Pagination Helpers ===");
        writer.writeBlankLine();
        
        for (OperationShape operation : paginatedOps) {
            generatePaginationHelper(operation, model, writer);
        }
    }
    
    /**
     * Generates a pagination helper for a single operation.
     * 
     * @param operation The paginated operation
     * @param model The Smithy model
     * @param writer The Unison code writer
     */
    public void generatePaginationHelper(OperationShape operation, Model model, UnisonWriter writer) {
        this.model = model;  // Store model for getUnisonType
        
        Optional<PaginatedTrait> paginatedTrait = operation.getTrait(PaginatedTrait.class);
        if (paginatedTrait.isEmpty()) {
            return;
        }
        
        PaginatedTrait pagination = paginatedTrait.get();
        String opName = UnisonSymbolProvider.toNamespacedFunctionName(
                operation.getId().getName(), clientNamespace);
        
        // Get pagination configuration
        String inputToken = pagination.getInputToken().orElse("continuationToken");
        String outputToken = pagination.getOutputToken().orElse("nextContinuationToken");
        String items = pagination.getItems().orElse("contents");
        
        // Get input/output types with namespace
        String inputType = operation.getInput()
                .map(id -> UnisonSymbolProvider.toNamespacedTypeName(id.getName(), clientNamespace))
                .orElse("()");
        String outputType = operation.getOutput()
                .map(id -> UnisonSymbolProvider.toNamespacedTypeName(id.getName(), clientNamespace))
                .orElse("()");
        
        // Config type with namespace
        String configType = UnisonSymbolProvider.toNamespacedTypeName("Config", clientNamespace);
        
        // Determine the token type from the input structure
        String tokenType = "Text"; // default
        if (operation.getInput().isPresent()) {
            StructureShape inputShape = model.expectShape(operation.getInput().get(), StructureShape.class);
            Optional<MemberShape> tokenMember = inputShape.getMember(inputToken);
            if (tokenMember.isPresent()) {
                Shape tokenTargetShape = model.expectShape(tokenMember.get().getTarget());
                tokenType = getUnisonType(tokenTargetShape);
                LOGGER.info("Token type for " + operation.getId().getName() + ": " + tokenType);
            }
        }
        
        // Get the item type from the output structure
        String itemsField = UnisonSymbolProvider.toUnisonFunctionName(items);
        String itemType = "a"; // default to polymorphic
        if (operation.getOutput().isPresent()) {
            StructureShape outputShape = model.expectShape(operation.getOutput().get(), StructureShape.class);
            // Try to find the items member - check both as-is and with first letter capitalized
            Optional<MemberShape> itemsMember = outputShape.getMember(items);
            if (itemsMember.isEmpty() && !items.isEmpty()) {
                // Try capitalized version
                String capitalizedItems = items.substring(0, 1).toUpperCase() + items.substring(1);
                itemsMember = outputShape.getMember(capitalizedItems);
            }
            if (itemsMember.isEmpty()) {
                // Try lowercase version
                itemsMember = outputShape.getMember(items.toLowerCase());
            }
            // If still not found, scan for the first list field in the output
            if (itemsMember.isEmpty()) {
                LOGGER.warning("Items field '" + items + "' not found in " + outputShape.getId() + 
                             ", scanning for first list field");
                for (MemberShape member : outputShape.getAllMembers().values()) {
                    Shape memberTarget = model.expectShape(member.getTarget());
                    if (memberTarget instanceof ListShape) {
                        itemsMember = Optional.of(member);
                        itemsField = UnisonSymbolProvider.toUnisonFunctionName(member.getMemberName());
                        LOGGER.info("Found list field: " + member.getMemberName() + 
                                  " (converted to " + itemsField + ")");
                        break;
                    }
                }
            }
            if (itemsMember.isPresent()) {
                Shape itemsShape = model.expectShape(itemsMember.get().getTarget());
                if (itemsShape instanceof ListShape) {
                    ListShape listShape = (ListShape) itemsShape;
                    Shape memberShape = model.expectShape(listShape.getMember().getTarget());
                    // Use getUnisonType to properly resolve the item type
                    LOGGER.info("Resolving item type for " + operation.getId().getName() + 
                              ": shape=" + memberShape.getId() + ", type=" + memberShape.getType());
                    itemType = getUnisonType(memberShape);
                    LOGGER.info("Resolved item type: " + itemType);
                }
            }
        }
        
        writer.writeDocComment(
            "Auto-paginating version of " + opName + ".\n\n" +
            "Automatically fetches all pages and collects all items from the '" + items + "' field.\n" +
            "Uses '" + inputToken + "' (type: " + tokenType + ") as input token and '" + outputToken + "' as output token.");
        
        // Function signature with concrete item type and namespaced types
        // Note: HTTP operations use {IO, Http, Exception, Threads} abilities for real HTTP via @unison/http
        String helperName = opName + "All";
        writer.writeSignature(helperName, configType + " -> " + inputType + " -> '{IO, Http, Exception, Threads} [" + itemType + "]");
        
        writer.write("$L config input =", helperName);
        writer.indent();
        writer.write("let");
        writer.indent();
        
        // Recursive helper function with concrete types for token and items
        // Token type is determined from the input structure's pagination field
        // Wrap complex types in parentheses for Optional
        String wrappedTokenType = tokenType.contains(" ") ? "(" + tokenType + ")" : tokenType;
        writer.write("go : Optional " + wrappedTokenType + " -> [" + itemType + "] -> '{IO, Http, Exception, Threads} [" + itemType + "]");
        writer.write("go token acc = do");
        writer.indent();
        
        // Build input with updated token field
        // Unison record update syntax: TypeName.field.set newValue record
        String inputTokenField = UnisonSymbolProvider.toUnisonFunctionName(inputToken);
        writer.write("inputWithToken = $L.$L.set token input", inputType, inputTokenField);
        // Force the delayed computation with !
        writer.write("response = !($L config inputWithToken)", opName);
        // Note: Unison uses accessor functions: TypeName.field record, not record.field
        // Optional.getOrElse takes default first, then optional
        writer.write("newItems = Optional.getOrElse [] ($L.$L response)", outputType, itemsField);
        writer.write("allItems = (List.++) acc newItems");
        
        // Check for next page - recursive call needs to be forced with !
        String outputTokenField = UnisonSymbolProvider.toUnisonFunctionName(outputToken);
        writer.write("match ($L.$L response) with", outputType, outputTokenField);
        writer.indent();
        // For non-Text tokens, we need to wrap them in Some
        writer.write("Some nextToken -> !(go (Some nextToken) allItems)");
        writer.write("None -> allItems");
        writer.dedent();
        
        writer.dedent();  // end go function
        
        // Initial call - final expression of the let block
        writer.write("go None []");
        
        writer.dedent();  // end let
        writer.dedent();  // end helper function
        writer.writeBlankLine();
    }
    
    /**
     * Generates a streaming/lazy pagination helper that yields pages one at a time.
     * 
     * <p>This is useful when you don't want to load all items into memory at once.
     * 
     * @param operation The paginated operation
     * @param model The Smithy model
     * @param writer The Unison code writer
     */
    public void generateStreamingPaginationHelper(OperationShape operation, Model model, UnisonWriter writer) {
        Optional<PaginatedTrait> paginatedTrait = operation.getTrait(PaginatedTrait.class);
        if (paginatedTrait.isEmpty()) {
            return;
        }
        
        PaginatedTrait pagination = paginatedTrait.get();
        String opName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName());
        
        String inputToken = pagination.getInputToken().orElse("continuationToken");
        String outputToken = pagination.getOutputToken().orElse("nextContinuationToken");
        
        String inputType = operation.getInput()
                .map(id -> UnisonSymbolProvider.toUnisonTypeName(id.getName()))
                .orElse("()");
        String outputType = operation.getOutput()
                .map(id -> UnisonSymbolProvider.toUnisonTypeName(id.getName()))
                .orElse("()");
        
        writer.writeDocComment(
            "Paginator for " + opName + ".\n\n" +
            "Returns a stream of response pages. Use this when you want to process\n" +
            "pages one at a time without loading all results into memory.");
        
        String helperName = opName + "Pages";
        writer.writeSignature(helperName, "Config -> " + inputType + " -> '{IO, Exception, Stream} " + outputType);
        
        writer.write("$L config input =", helperName);
        writer.indent();
        writer.write("let");
        writer.indent();
        
        String inputTokenField = UnisonSymbolProvider.toUnisonFunctionName(inputToken);
        String outputTokenField = UnisonSymbolProvider.toUnisonFunctionName(outputToken);
        
        writer.write("go : Optional Text -> '{IO, Exception, Stream} ()");
        writer.write("go token =");
        writer.indent();
        writer.write("let");
        writer.indent();
        // Unison record update syntax: TypeName.field.set newValue record
        writer.write("inputWithToken = $L.$L.set token input", inputType, inputTokenField);
        // Force the delayed computation with !
        writer.write("response = !($L config inputWithToken)", opName);
        writer.write("Stream.emit response");
        writer.dedent();
        // Note: Unison uses accessor functions: TypeName.field record, not record.field
        writer.write("match ($L.$L response) with", outputType, outputTokenField);
        writer.indent();
        writer.write("Some nextToken -> go (Some nextToken)");
        writer.write("None -> ()");
        writer.dedent();
        writer.dedent();
        
        writer.dedent();
        writer.write("go None");
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Converts a Smithy shape to its corresponding Unison type string.
     * Handles primitives, collections, and custom types appropriately.
     * 
     * @param shape The Smithy shape to convert
     * @return The Unison type string
     */
    private String getUnisonType(Shape shape) {
        if (shape instanceof StringShape) {
            if (shape.hasTrait(EnumTrait.class)) {
                return UnisonSymbolProvider.toNamespacedTypeName(shape.getId().getName(), clientNamespace);
            }
            return "Text";
        } else if (shape instanceof IntegerShape || shape instanceof LongShape ||
                   shape instanceof ShortShape || shape instanceof ByteShape ||
                   shape instanceof BigIntegerShape) {
            return "Int";
        } else if (shape instanceof FloatShape || shape instanceof DoubleShape ||
                   shape instanceof BigDecimalShape) {
            return "Float";
        } else if (shape instanceof BooleanShape) {
            return "Boolean";
        } else if (shape instanceof BlobShape) {
            return "Bytes";
        } else if (shape instanceof TimestampShape) {
            return "Text";
        } else if (shape instanceof DocumentShape) {
            return "Aws.Json.JsonValue";
        } else if (shape instanceof ListShape) {
            ListShape list = (ListShape) shape;
            Shape memberShape = model.expectShape(list.getMember().getTarget());
            String memberType = getUnisonType(memberShape);
            return "[" + memberType + "]";
        } else if (shape instanceof SetShape) {
            SetShape set = (SetShape) shape;
            Shape memberShape = model.expectShape(set.getMember().getTarget());
            String memberType = getUnisonType(memberShape);
            return "[" + memberType + "]";
        } else if (shape instanceof MapShape) {
            MapShape map = (MapShape) shape;
            Shape keyShape = model.expectShape(map.getKey().getTarget());
            Shape valueShape = model.expectShape(map.getValue().getTarget());
            String keyType = getUnisonType(keyShape);
            String valueType = getUnisonType(valueShape);
            return "Map " + keyType + " " + valueType;
        } else if (shape instanceof StructureShape) {
            return UnisonSymbolProvider.toNamespacedTypeName(shape.getId().getName(), clientNamespace);
        } else if (shape instanceof UnionShape) {
            // Check if this is DynamoDB AttributeValue - use runtime type
            UnionShape unionShape = (UnionShape) shape;
            if (isDynamoDBAttributeValue(unionShape)) {
                return "Aws.Json.AttributeValue";
            }
            return UnisonSymbolProvider.toNamespacedTypeName(shape.getId().getName(), clientNamespace);
        } else if (shape instanceof EnumShape) {
            return UnisonSymbolProvider.toNamespacedTypeName(shape.getId().getName(), clientNamespace);
        } else if (shape instanceof IntEnumShape) {
            return UnisonSymbolProvider.toNamespacedTypeName(shape.getId().getName(), clientNamespace);
        }
        return "a";  // Generic type parameter as fallback
    }
    
    /**
     * Checks if a union shape is the DynamoDB AttributeValue type.
     * 
     * @param unionShape The union shape to check
     * @return true if this is DynamoDB's AttributeValue type
     */
    private boolean isDynamoDBAttributeValue(UnionShape unionShape) {
        String shapeName = unionShape.getId().getName();
        String namespace = unionShape.getId().getNamespace();
        
        // DynamoDB AttributeValue is in com.amazonaws.dynamodb namespace
        return "AttributeValue".equals(shapeName) && 
               namespace.contains("dynamodb");
    }
}
