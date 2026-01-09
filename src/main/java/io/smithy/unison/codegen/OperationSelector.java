package io.smithy.unison.codegen;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Selects operations to generate based on configuration.
 * 
 * <p>Handles:
 * <ul>
 *   <li>Operation name matching (simple name or full shape ID)</li>
 *   <li>Resource operation resolution</li>
 *   <li>Operation validation</li>
 * </ul>
 */
public class OperationSelector {
    private static final Logger LOGGER = Logger.getLogger(OperationSelector.class.getName());
    
    private final Model model;
    private final ServiceShape service;
    private final UnisonSettings settings;
    
    public OperationSelector(Model model, ServiceShape service, UnisonSettings settings) {
        this.model = model;
        this.service = service;
        this.settings = settings;
    }
    
    /**
     * Selects operations to generate based on settings.
     * 
     * @return Set of operation shapes to generate
     */
    public Set<OperationShape> selectOperations() {
        // If no filter, return all operations
        if (!settings.hasOperationFilter()) {
            return collectAllOperations();
        }
        
        // Build filtered set
        Set<OperationShape> selected = new HashSet<>();
        Set<String> requestedOps = new HashSet<>(settings.operations());
        Set<String> foundOps = new HashSet<>();
        
        // Collect all available operations (service + resources)
        Set<OperationShape> allOperations = collectAllOperations();
        
        // Match requested operations
        for (OperationShape operation : allOperations) {
            String opName = operation.getId().getName();
            String opFullId = operation.getId().toString();
            
            // Match by simple name or full shape ID
            if (requestedOps.contains(opName) || requestedOps.contains(opFullId)) {
                selected.add(operation);
                foundOps.add(opName);
            }
        }
        
        // Report unmatched operations
        Set<String> notFound = new HashSet<>(requestedOps);
        notFound.removeAll(foundOps);
        if (!notFound.isEmpty()) {
            LOGGER.warning("Operations not found in service: " + notFound);
            LOGGER.warning("Available operations: " + 
                allOperations.stream()
                    .map(op -> op.getId().getName())
                    .sorted()
                    .toList());
        }
        
        // Log selection results
        LOGGER.info("Operation selection:");
        LOGGER.info("  Requested: " + requestedOps.size());
        LOGGER.info("  Found: " + foundOps.size());
        LOGGER.info("  Selected: " + selected.stream()
            .map(op -> op.getId().getName())
            .sorted()
            .toList());
        
        return selected;
    }
    
    /**
     * Collects all operations from service and resources.
     */
    private Set<OperationShape> collectAllOperations() {
        Set<OperationShape> all = new HashSet<>();
        
        // Collect service-level operations
        for (ShapeId opId : service.getOperations()) {
            all.add(model.expectShape(opId, OperationShape.class));
        }
        
        // Collect resource operations recursively
        for (ShapeId resourceId : service.getResources()) {
            ResourceShape resource = model.expectShape(resourceId, ResourceShape.class);
            collectResourceOperations(resource, all);
        }
        
        return all;
    }
    
    /**
     * Recursively collects operations from a resource and its children.
     */
    private void collectResourceOperations(ResourceShape resource, Set<OperationShape> operations) {
        // Collect all operations from this resource
        for (ShapeId opId : resource.getAllOperations()) {
            operations.add(model.expectShape(opId, OperationShape.class));
        }
        
        // Recursively collect from child resources
        for (ShapeId childResourceId : resource.getResources()) {
            ResourceShape childResource = model.expectShape(childResourceId, ResourceShape.class);
            collectResourceOperations(childResource, operations);
        }
    }
}
