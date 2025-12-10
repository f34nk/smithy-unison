package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
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
    
    /**
     * Creates a new PaginationGenerator.
     */
    public PaginationGenerator() {
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
        Optional<PaginatedTrait> paginatedTrait = operation.getTrait(PaginatedTrait.class);
        if (paginatedTrait.isEmpty()) {
            return;
        }
        
        PaginatedTrait pagination = paginatedTrait.get();
        String opName = UnisonSymbolProvider.toUnisonFunctionName(operation.getId().getName());
        
        // Get pagination configuration
        String inputToken = pagination.getInputToken().orElse("continuationToken");
        String outputToken = pagination.getOutputToken().orElse("nextContinuationToken");
        String items = pagination.getItems().orElse("contents");
        
        // Get input/output types
        String inputType = operation.getInput()
                .map(id -> UnisonSymbolProvider.toUnisonTypeName(id.getName()))
                .orElse("()");
        String outputType = operation.getOutput()
                .map(id -> UnisonSymbolProvider.toUnisonTypeName(id.getName()))
                .orElse("()");
        
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
            if (itemsMember.isPresent()) {
                Shape itemsShape = model.expectShape(itemsMember.get().getTarget());
                if (itemsShape instanceof ListShape) {
                    ListShape listShape = (ListShape) itemsShape;
                    Shape memberShape = model.expectShape(listShape.getMember().getTarget());
                    itemType = UnisonSymbolProvider.toUnisonTypeName(memberShape.getId().getName());
                }
            }
        }
        
        writer.writeDocComment(
            "Auto-paginating version of " + opName + ".\n\n" +
            "Automatically fetches all pages and collects all items from the '" + items + "' field.\n" +
            "Uses '" + inputToken + "' as input token and '" + outputToken + "' as output token.");
        
        // Function signature with concrete item type
        // Note: HTTP operations use {IO, Exception, Threads} abilities for real HTTP via @unison/http
        String helperName = opName + "All";
        writer.writeSignature(helperName, "Config -> " + inputType + " -> '{IO, Exception, Threads} [" + itemType + "]");
        
        writer.write("$L config input =", helperName);
        writer.indent();
        writer.write("let");
        writer.indent();
        
        // Recursive helper function with concrete type
        // In do blocks, bindings are scoped to the rest of the block (no need for inner 'let')
        writer.write("go : Optional Text -> [" + itemType + "] -> '{IO, Exception, Threads} [" + itemType + "]");
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
}
