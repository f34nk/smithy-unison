package io.smithy.unison.codegen.protocols;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utility methods for protocol generators.
 */
public final class ProtocolUtils {
    
    /**
     * Gets the HTTP method from an operation's @http trait.
     */
    public static String getHttpMethod(OperationShape operation, String defaultMethod) {
        return operation.getTrait(HttpTrait.class)
                .map(HttpTrait::getMethod)
                .orElse(defaultMethod);
    }
    
    /**
     * Gets the HTTP URI from an operation's @http trait.
     */
    public static String getHttpUri(OperationShape operation, String defaultUri) {
        return operation.getTrait(HttpTrait.class)
                .map(trait -> trait.getUri().toString())
                .orElse(defaultUri);
    }
    
    /**
     * Gets the input shape for an operation.
     */
    public static Optional<StructureShape> getInputShape(OperationShape operation, Model model) {
        return operation.getInput()
                .map(id -> model.expectShape(id, StructureShape.class));
    }
    
    /**
     * Gets the output shape for an operation.
     */
    public static Optional<StructureShape> getOutputShape(OperationShape operation, Model model) {
        return operation.getOutput()
                .map(id -> model.expectShape(id, StructureShape.class));
    }
    
    /**
     * Gets members with @httpLabel trait.
     */
    public static List<MemberShape> getLabelMembers(StructureShape shape) {
        List<MemberShape> result = new ArrayList<>();
        for (MemberShape member : shape.getAllMembers().values()) {
            if (member.hasTrait(HttpLabelTrait.class)) {
                result.add(member);
            }
        }
        return result;
    }
    
    /**
     * Gets members with @httpQuery trait.
     */
    public static List<MemberShape> getQueryMembers(StructureShape shape) {
        List<MemberShape> result = new ArrayList<>();
        for (MemberShape member : shape.getAllMembers().values()) {
            if (member.hasTrait(HttpQueryTrait.class)) {
                result.add(member);
            }
        }
        return result;
    }
    
    /**
     * Gets members with @httpHeader trait.
     */
    public static List<MemberShape> getHeaderMembers(StructureShape shape) {
        List<MemberShape> result = new ArrayList<>();
        for (MemberShape member : shape.getAllMembers().values()) {
            if (member.hasTrait(HttpHeaderTrait.class)) {
                result.add(member);
            }
        }
        return result;
    }
    
    /**
     * Gets the member with @httpPayload trait, if any.
     */
    public static Optional<MemberShape> getPayloadMember(StructureShape shape) {
        for (MemberShape member : shape.getAllMembers().values()) {
            if (member.hasTrait(HttpPayloadTrait.class)) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }
    
    /**
     * Gets body members (not label, query, header, or payload).
     */
    public static List<MemberShape> getBodyMembers(StructureShape shape) {
        List<MemberShape> result = new ArrayList<>();
        for (MemberShape member : shape.getAllMembers().values()) {
            if (!member.hasTrait(HttpLabelTrait.class) &&
                !member.hasTrait(HttpQueryTrait.class) &&
                !member.hasTrait(HttpHeaderTrait.class) &&
                !member.hasTrait(HttpPayloadTrait.class)) {
                result.add(member);
            }
        }
        return result;
    }
    
    private ProtocolUtils() {
        // Utility class
    }
}
