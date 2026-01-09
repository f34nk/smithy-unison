package io.smithy.unison.codegen;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.ErrorTrait;
import java.util.*;
import java.util.logging.Logger;

/**
 * Collects all shapes transitively referenced by a set of operations.
 * 
 * <p>For each operation, collects:
 * <ul>
 *   <li>Input structure and all nested types</li>
 *   <li>Output structure and all nested types</li>
 *   <li>Error structures and all nested types</li>
 *   <li>Enums referenced anywhere</li>
 *   <li>Unions and their variants</li>
 * </ul>
 */
public class TransitiveDependencyCollector {
    private static final Logger LOGGER = Logger.getLogger(TransitiveDependencyCollector.class.getName());
    
    private final Model model;
    private final Set<OperationShape> operations;
    
    public TransitiveDependencyCollector(Model model, Set<OperationShape> operations) {
        this.model = model;
        this.operations = operations;
    }
    
    /**
     * Collects all structures referenced by selected operations.
     */
    public Set<StructureShape> collectStructures() {
        Set<StructureShape> structures = new HashSet<>();
        Set<ShapeId> visited = new HashSet<>();
        
        for (OperationShape operation : operations) {
            // Collect from input
            operation.getInput().ifPresent(inputId -> {
                collectStructuresRecursively(inputId, structures, visited);
            });
            
            // Collect from output
            operation.getOutput().ifPresent(outputId -> {
                collectStructuresRecursively(outputId, structures, visited);
            });
            
            // Collect from errors (handled separately as error structures)
        }
        
        return structures;
    }
    
    /**
     * Collects all error structures referenced by selected operations.
     */
    @SuppressWarnings("deprecation")
    public Set<StructureShape> collectErrors() {
        Set<StructureShape> errors = new HashSet<>();
        Set<ShapeId> visited = new HashSet<>();
        
        for (OperationShape operation : operations) {
            for (ShapeId errorId : operation.getIntroducedErrors()) {
                collectStructuresRecursively(errorId, errors, visited);
            }
        }
        
        return errors;
    }
    
    /**
     * Collects all enums referenced by selected operations.
     */
    @SuppressWarnings("deprecation")
    public Set<Shape> collectEnums() {
        Set<Shape> enums = new HashSet<>();
        Set<ShapeId> visited = new HashSet<>();
        
        for (OperationShape operation : operations) {
            operation.getInput().ifPresent(inputId -> {
                collectEnumsRecursively(inputId, enums, visited);
            });
            operation.getOutput().ifPresent(outputId -> {
                collectEnumsRecursively(outputId, enums, visited);
            });
            for (ShapeId errorId : operation.getIntroducedErrors()) {
                collectEnumsRecursively(errorId, enums, visited);
            }
        }
        
        return enums;
    }
    
    /**
     * Recursively collects structure shapes.
     */
    private void collectStructuresRecursively(
            ShapeId shapeId, 
            Set<StructureShape> structures, 
            Set<ShapeId> visited) {
        
        if (visited.contains(shapeId)) {
            return;
        }
        visited.add(shapeId);
        
        Shape shape = model.expectShape(shapeId);
        
        if (shape.isStructureShape()) {
            StructureShape structure = shape.asStructureShape().get();
            
            // Skip if it's an error (errors collected separately)
            if (!structure.hasTrait(ErrorTrait.class)) {
                structures.add(structure);
            }
            
            // Recurse into members
            for (MemberShape member : structure.getAllMembers().values()) {
                collectStructuresRecursively(member.getTarget(), structures, visited);
            }
        }
        else if (shape.isListShape()) {
            ListShape list = shape.asListShape().get();
            collectStructuresRecursively(list.getMember().getTarget(), structures, visited);
        }
        else if (shape.isMapShape()) {
            MapShape map = shape.asMapShape().get();
            collectStructuresRecursively(map.getKey().getTarget(), structures, visited);
            collectStructuresRecursively(map.getValue().getTarget(), structures, visited);
        }
        else if (shape.isUnionShape()) {
            UnionShape union = shape.asUnionShape().get();
            for (MemberShape member : union.getAllMembers().values()) {
                collectStructuresRecursively(member.getTarget(), structures, visited);
            }
        }
    }
    
    /**
     * Recursively collects enum shapes (EnumShape, IntEnumShape, StringShape with @enum trait, UnionShape).
     */
    @SuppressWarnings("deprecation")
    private void collectEnumsRecursively(
            ShapeId shapeId,
            Set<Shape> enums,
            Set<ShapeId> visited) {
        
        if (visited.contains(shapeId)) {
            return;
        }
        visited.add(shapeId);
        
        Shape shape = model.expectShape(shapeId);
        
        // Collect enum types
        if (shape.isEnumShape() || shape instanceof IntEnumShape) {
            enums.add(shape);
        }
        else if (shape.isStringShape() && shape.hasTrait(software.amazon.smithy.model.traits.EnumTrait.class)) {
            enums.add(shape);
        }
        else if (shape.isUnionShape()) {
            enums.add(shape);
            // Recurse into union members
            UnionShape union = shape.asUnionShape().get();
            for (MemberShape member : union.getAllMembers().values()) {
                collectEnumsRecursively(member.getTarget(), enums, visited);
            }
        }
        // Recurse into container types
        else if (shape.isStructureShape()) {
            StructureShape structure = shape.asStructureShape().get();
            for (MemberShape member : structure.getAllMembers().values()) {
                collectEnumsRecursively(member.getTarget(), enums, visited);
            }
        }
        else if (shape.isListShape()) {
            ListShape list = shape.asListShape().get();
            collectEnumsRecursively(list.getMember().getTarget(), enums, visited);
        }
        else if (shape.isMapShape()) {
            MapShape map = shape.asMapShape().get();
            collectEnumsRecursively(map.getValue().getTarget(), enums, visited);
        }
    }
    
    /**
     * Logs summary of collected dependencies.
     */
    public void logSummary(Set<StructureShape> structures, Set<StructureShape> errors, Set<Shape> enums) {
        LOGGER.info("Transitive dependency collection:");
        LOGGER.info("  Operations: " + operations.size());
        LOGGER.info("  Structures: " + structures.size());
        LOGGER.info("  Errors: " + errors.size());
        LOGGER.info("  Enums/Unions: " + enums.size());
    }
}
