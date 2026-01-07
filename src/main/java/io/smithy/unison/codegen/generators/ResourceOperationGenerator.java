package io.smithy.unison.codegen.generators;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import io.smithy.unison.codegen.UnisonContext;
import io.smithy.unison.codegen.UnisonWriter;
import io.smithy.unison.codegen.protocols.ProtocolGenerator;
import io.smithy.unison.codegen.symbol.UnisonSymbolProvider;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Generates Unison code for operations bound to Smithy resources.
 * 
 * <p>This generator traverses the resource hierarchy of a service and
 * generates operations for:
 * <ul>
 *   <li>Lifecycle operations (create, read, update, delete, list, put)</li>
 *   <li>Collection operations (resource.operations[])</li>
 *   <li>Child resource operations (recursively)</li>
 * </ul>
 * 
 * <p>The actual code generation is delegated to protocol generators,
 * ensuring consistency with service-level operation generation.
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ResourceOperationGenerator generator = new ResourceOperationGenerator(
 *     model, context, protocolGenerator);
 * 
 * for (ShapeId resourceId : service.getResources()) {
 *     ResourceShape resource = model.expectShape(resourceId, ResourceShape.class);
 *     generator.generateResourceOperations(resource, writer);
 * }
 * }</pre>
 * 
 * <h2>Resource Traversal</h2>
 * <p>Resources are processed depth-first:
 * <ol>
 *   <li>Generate lifecycle operations for current resource</li>
 *   <li>Generate collection operations for current resource</li>
 *   <li>Recursively process child resources</li>
 * </ol>
 * 
 * <h2>Operation Deduplication</h2>
 * <p>Although Smithy's SingleOperationBinding rule prevents operations from
 * being bound multiple times, this generator maintains a set of processed
 * operations as a safety measure.
 * 
 * @see ResourceShape
 * @see ProtocolGenerator
 */
public class ResourceOperationGenerator {
    
    private final Model model;
    private final UnisonContext context;
    private final Optional<ProtocolGenerator> protocolGenerator;
    private final Set<ShapeId> processedOperations;
    private final Set<ShapeId> visitedResources;
    
    /**
     * Creates a new resource operation generator.
     * 
     * @param model The Smithy model containing resource and operation shapes
     * @param context The code generation context
     * @param protocolGenerator Optional protocol generator for operation implementation
     */
    public ResourceOperationGenerator(
            Model model,
            UnisonContext context,
            Optional<ProtocolGenerator> protocolGenerator) {
        this.model = model;
        this.context = context;
        this.protocolGenerator = protocolGenerator;
        this.processedOperations = new HashSet<>();
        this.visitedResources = new HashSet<>();
    }
    
    /**
     * Generate all operations for a resource and its children.
     * 
     * <p>This method processes:
     * <ul>
     *   <li>All lifecycle operations (create, put, read, update, delete, list)</li>
     *   <li>All collection operations from resource.operations[]</li>
     *   <li>All child resources (recursive)</li>
     * </ul>
     * 
     * <p>Cycle detection is performed to prevent infinite recursion if the
     * model contains circular resource references (though Smithy typically
     * prevents this).
     * 
     * @param resource The resource to process
     * @param writer The Unison code writer
     * @throws IllegalStateException if a circular resource reference is detected
     */
    public void generateResourceOperations(
            ResourceShape resource,
            UnisonWriter writer) {
        
        // Detect cycles in resource hierarchy
        if (visitedResources.contains(resource.getId())) {
            throw new IllegalStateException(
                "Circular resource reference detected: " + resource.getId());
        }
        
        visitedResources.add(resource.getId());
        
        try {
            // Generate lifecycle operations
            generateLifecycleOperations(resource, writer);
            
            // Generate collection operations
            generateCollectionOperations(resource, writer);
            
            // Generate child resource operations recursively
            for (ShapeId childId : resource.getResources()) {
                ResourceShape childResource = model.expectShape(
                    childId, ResourceShape.class);
                generateResourceOperations(childResource, writer);
            }
        } finally {
            // Backtrack for proper cycle detection in sibling branches
            visitedResources.remove(resource.getId());
        }
    }
    
    /**
     * Generate lifecycle operations (create, read, update, delete, list, put).
     * 
     * <p>Lifecycle operations define the standard CRUD operations for a resource:
     * <ul>
     *   <li><b>create:</b> Create a new resource with service-generated ID</li>
     *   <li><b>put:</b> Create or replace a resource with client-specified ID</li>
     *   <li><b>read:</b> Retrieve a specific resource instance</li>
     *   <li><b>update:</b> Modify an existing resource</li>
     *   <li><b>delete:</b> Remove a resource</li>
     *   <li><b>list:</b> Retrieve a collection of resources</li>
     * </ul>
     * 
     * <p>Not all resources define all lifecycle operations. This method only
     * generates operations that are present in the resource definition.
     * 
     * @param resource The resource containing lifecycle operations
     * @param writer The Unison code writer
     */
    private void generateLifecycleOperations(
            ResourceShape resource,
            UnisonWriter writer) {
        
        // Process each lifecycle operation if present
        resource.getCreate().ifPresent(opId -> 
            generateOperation(opId, writer, "create"));
        
        resource.getPut().ifPresent(opId -> 
            generateOperation(opId, writer, "put"));
        
        resource.getRead().ifPresent(opId -> 
            generateOperation(opId, writer, "read"));
        
        resource.getUpdate().ifPresent(opId -> 
            generateOperation(opId, writer, "update"));
        
        resource.getDelete().ifPresent(opId -> 
            generateOperation(opId, writer, "delete"));
        
        resource.getList().ifPresent(opId -> 
            generateOperation(opId, writer, "list"));
    }
    
    /**
     * Generate collection operations from resource.operations[].
     * 
     * <p>Collection operations are custom operations that act on resource
     * instances. They are listed in the resource's operations array and
     * typically require resource identifiers in their input.
     * 
     * <p>Examples:
     * <ul>
     *   <li>Lambda Invoke - executes a function</li>
     *   <li>SQS SendMessage - sends message to queue</li>
     *   <li>S3 CopyObject - copies an object</li>
     * </ul>
     * 
     * @param resource The resource containing collection operations
     * @param writer The Unison code writer
     */
    private void generateCollectionOperations(
            ResourceShape resource,
            UnisonWriter writer) {
        
        for (ShapeId opId : resource.getOperations()) {
            generateOperation(opId, writer, "collection");
        }
    }
    
    /**
     * Generate a single operation using the protocol generator.
     * 
     * <p>This is the same code path used for service-level operations,
     * ensuring consistency in generated code. The operation type parameter
     * is used for documentation purposes only.
     * 
     * <p>Operations are deduplicated using a processed set. While Smithy's
     * SingleOperationBinding rule should prevent duplicate bindings, this
     * provides an additional safety check.
     * 
     * @param operationId The shape ID of the operation to generate
     * @param writer The Unison code writer
     * @param operationType The type of operation (lifecycle or collection) for logging
     */
    private void generateOperation(
            ShapeId operationId, 
            UnisonWriter writer,
            String operationType) {
        
        // Skip if already processed (safety check - shouldn't happen)
        if (processedOperations.contains(operationId)) {
            return;
        }
        processedOperations.add(operationId);
        
        // Load operation shape
        OperationShape operation = model.expectShape(
            operationId, OperationShape.class);
        
        // Generate using protocol generator or stub
        if (protocolGenerator.isPresent()) {
            protocolGenerator.get().generateOperation(
                operation, writer, context);
        } else {
            // Fallback: generate stub operation
            generateOperationStub(operation, writer);
        }
    }
    
    /**
     * Generate stub operation when no protocol generator is available.
     * 
     * <p>This generates a minimal operation implementation that returns
     * placeholder values. This is used when:
     * <ul>
     *   <li>No protocol trait is found on the service</li>
     *   <li>Protocol is not yet supported by code generator</li>
     * </ul>
     * 
     * @param operation The operation to generate a stub for
     * @param writer The Unison code writer
     */
    private void generateOperationStub(
            OperationShape operation,
            UnisonWriter writer) {
        
        ServiceShape service = context.serviceShape();
        String clientNamespace = context.settings().getClientNamespace();
        
        String opName = UnisonSymbolProvider.toNamespacedFunctionName(
            operation.getId().getName(), clientNamespace);
        
        // Determine input and output types
        String inputType = operation.getInput()
            .map(id -> UnisonSymbolProvider.toNamespacedTypeName(
                id.getName(), clientNamespace))
            .orElse("()");
        
        String outputType = operation.getOutput()
            .map(id -> UnisonSymbolProvider.toNamespacedTypeName(
                id.getName(), clientNamespace))
            .orElse("()");
        
        String configType = UnisonSymbolProvider.toNamespacedTypeName(
            "Config", clientNamespace);
        
        // Write operation stub
        writer.writeDocComment(operation.getId().getName() + " operation (stub implementation)");
        writer.writeSignature(opName, 
            String.format("%s -> %s -> '{IO, Exception} %s", 
                configType, inputType, outputType));
        writer.write("$L config input = do", opName);
        writer.indent();
        writer.writeComment("TODO: Implement operation");
        
        if (outputType.equals("()")) {
            writer.write("()");
        } else {
            writer.writeComment("Placeholder - returns empty response");
            writer.write("bug \"Operation not implemented: $L\"", opName);
        }
        
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Get the set of operations that have been processed.
     * 
     * <p>This is primarily useful for testing and debugging to verify
     * that all expected operations were generated.
     * 
     * @return Unmodifiable set of processed operation shape IDs
     */
    public Set<ShapeId> getProcessedOperations() {
        return Set.copyOf(processedOperations);
    }
}
