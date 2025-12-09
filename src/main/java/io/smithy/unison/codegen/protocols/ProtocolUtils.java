package io.smithy.unison.codegen.protocols;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utility methods for protocol generators.
 * 
 * <p>Provides helper methods for extracting HTTP binding trait information from
 * Smithy shapes. These utilities are used by protocol generators to determine
 * how to serialize requests and deserialize responses.
 * 
 * <h2>Supported HTTP Binding Traits</h2>
 * <ul>
 *   <li>{@code @http} - HTTP method, URI pattern, and expected status code</li>
 *   <li>{@code @httpLabel} - Path parameter bindings</li>
 *   <li>{@code @httpQuery} - Query string parameter bindings</li>
 *   <li>{@code @httpHeader} - HTTP header bindings (request and response)</li>
 *   <li>{@code @httpPayload} - Raw body content bindings</li>
 *   <li>{@code @httpResponseCode} - Response status code binding</li>
 *   <li>{@code @httpPrefixHeaders} - Map of headers with a common prefix</li>
 *   <li>{@code @httpQueryParams} - Map of query parameters</li>
 * </ul>
 */
public final class ProtocolUtils {
    
    // ========== @http Trait Methods ==========
    
    /**
     * Gets the HTTP method from an operation's @http trait.
     * 
     * @param operation The operation shape
     * @param defaultMethod The default method if no @http trait is present
     * @return The HTTP method (e.g., "GET", "POST", "PUT", "DELETE")
     */
    public static String getHttpMethod(OperationShape operation, String defaultMethod) {
        return operation.getTrait(HttpTrait.class)
                .map(HttpTrait::getMethod)
                .orElse(defaultMethod);
    }
    
    /**
     * Gets the HTTP URI from an operation's @http trait.
     * 
     * @param operation The operation shape
     * @param defaultUri The default URI if no @http trait is present
     * @return The URI pattern (e.g., "/{Bucket}/{Key}")
     */
    public static String getHttpUri(OperationShape operation, String defaultUri) {
        return operation.getTrait(HttpTrait.class)
                .map(trait -> trait.getUri().toString())
                .orElse(defaultUri);
    }
    
    /**
     * Gets the expected HTTP status code from an operation's @http trait.
     * 
     * @param operation The operation shape
     * @param defaultCode The default status code if no @http trait is present
     * @return The expected HTTP status code for successful responses
     */
    public static int getHttpStatusCode(OperationShape operation, int defaultCode) {
        return operation.getTrait(HttpTrait.class)
                .map(HttpTrait::getCode)
                .orElse(defaultCode);
    }
    
    /**
     * Checks if the operation has an @http trait.
     * 
     * @param operation The operation shape
     * @return true if the operation has an @http trait
     */
    public static boolean hasHttpTrait(OperationShape operation) {
        return operation.hasTrait(HttpTrait.class);
    }
    
    // ========== Input/Output Shape Methods ==========
    
    /**
     * Gets the input shape for an operation.
     * 
     * @param operation The operation shape
     * @param model The Smithy model
     * @return The input structure shape, if present
     */
    public static Optional<StructureShape> getInputShape(OperationShape operation, Model model) {
        return operation.getInput()
                .map(id -> model.expectShape(id, StructureShape.class));
    }
    
    /**
     * Gets the output shape for an operation.
     * 
     * @param operation The operation shape
     * @param model The Smithy model
     * @return The output structure shape, if present
     */
    public static Optional<StructureShape> getOutputShape(OperationShape operation, Model model) {
        return operation.getOutput()
                .map(id -> model.expectShape(id, StructureShape.class));
    }
    
    // ========== @httpLabel Trait Methods ==========
    
    /**
     * Gets members with @httpLabel trait.
     * 
     * <p>The @httpLabel trait binds a member to a URI path segment.
     * For example, in URI "/{Bucket}/{Key}", members named "Bucket" and "Key"
     * would have @httpLabel traits.
     * 
     * @param shape The structure shape to inspect
     * @return List of members with @httpLabel trait
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
     * Checks if the URI contains a greedy label (ending with +).
     * 
     * <p>Greedy labels like "{Key+}" match everything including slashes,
     * which is important for S3 object keys that can contain slashes.
     * 
     * @param uri The URI pattern
     * @param labelName The label name (without braces)
     * @return true if the label is greedy
     */
    public static boolean isGreedyLabel(String uri, String labelName) {
        return uri.contains("{" + labelName + "+}");
    }
    
    // ========== @httpQuery Trait Methods ==========
    
    /**
     * Gets members with @httpQuery trait.
     * 
     * <p>The @httpQuery trait binds a member to a query string parameter.
     * The trait value specifies the query parameter name.
     * 
     * @param shape The structure shape to inspect
     * @return List of members with @httpQuery trait
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
     * Gets the query parameter name for a member with @httpQuery trait.
     * 
     * @param member The member shape
     * @return The query parameter name, or the member name if not specified
     */
    public static String getQueryParamName(MemberShape member) {
        return member.getTrait(HttpQueryTrait.class)
                .map(HttpQueryTrait::getValue)
                .filter(v -> !v.isEmpty())
                .orElse(member.getMemberName());
    }
    
    /**
     * Gets the member with @httpQueryParams trait, if any.
     * 
     * <p>The @httpQueryParams trait binds a map member to the entire query string,
     * allowing dynamic query parameters.
     * 
     * @param shape The structure shape to inspect
     * @return The member with @httpQueryParams trait, if present
     */
    public static Optional<MemberShape> getQueryParamsMember(StructureShape shape) {
        for (MemberShape member : shape.getAllMembers().values()) {
            if (member.hasTrait(HttpQueryParamsTrait.class)) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }
    
    // ========== @httpHeader Trait Methods ==========
    
    /**
     * Gets members with @httpHeader trait.
     * 
     * <p>The @httpHeader trait binds a member to an HTTP header.
     * The trait value specifies the header name.
     * 
     * @param shape The structure shape to inspect
     * @return List of members with @httpHeader trait
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
     * Gets the header name for a member with @httpHeader trait.
     * 
     * @param member The member shape
     * @return The header name, or the member name if not specified
     */
    public static String getHeaderName(MemberShape member) {
        return member.getTrait(HttpHeaderTrait.class)
                .map(HttpHeaderTrait::getValue)
                .filter(v -> !v.isEmpty())
                .orElse(member.getMemberName());
    }
    
    /**
     * Gets the member with @httpPrefixHeaders trait, if any.
     * 
     * <p>The @httpPrefixHeaders trait binds a map member to headers
     * with a common prefix, like "x-amz-meta-*".
     * 
     * @param shape The structure shape to inspect
     * @return The member with @httpPrefixHeaders trait, if present
     */
    public static Optional<MemberShape> getPrefixHeadersMember(StructureShape shape) {
        for (MemberShape member : shape.getAllMembers().values()) {
            if (member.hasTrait(HttpPrefixHeadersTrait.class)) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }
    
    /**
     * Gets the header prefix from a @httpPrefixHeaders trait.
     * 
     * @param member The member shape
     * @return The header prefix, or empty string if not present
     */
    public static String getHeaderPrefix(MemberShape member) {
        return member.getTrait(HttpPrefixHeadersTrait.class)
                .map(HttpPrefixHeadersTrait::getValue)
                .orElse("");
    }
    
    // ========== @httpPayload Trait Methods ==========
    
    /**
     * Gets the member with @httpPayload trait, if any.
     * 
     * <p>The @httpPayload trait binds a member to the HTTP request/response body.
     * Only one member per structure can have this trait. The member type determines
     * the content handling:
     * <ul>
     *   <li>blob - raw binary body</li>
     *   <li>string - text body</li>
     *   <li>structure - serialized body (XML, JSON, etc.)</li>
     * </ul>
     * 
     * @param shape The structure shape to inspect
     * @return The member with @httpPayload trait, if present
     */
    public static Optional<MemberShape> getPayloadMember(StructureShape shape) {
        for (MemberShape member : shape.getAllMembers().values()) {
            if (member.hasTrait(HttpPayloadTrait.class)) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }
    
    // ========== @httpResponseCode Trait Methods ==========
    
    /**
     * Gets the member with @httpResponseCode trait, if any.
     * 
     * <p>The @httpResponseCode trait binds an integer member to the HTTP
     * response status code. This allows the response status to be captured
     * in the output structure.
     * 
     * @param shape The structure shape to inspect
     * @return The member with @httpResponseCode trait, if present
     */
    public static Optional<MemberShape> getResponseCodeMember(StructureShape shape) {
        for (MemberShape member : shape.getAllMembers().values()) {
            if (member.hasTrait(HttpResponseCodeTrait.class)) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }
    
    // ========== Body Members ==========
    
    /**
     * Gets body members (not bound to label, query, header, payload, or response code).
     * 
     * <p>Body members are serialized into the request/response body using the
     * protocol's serialization format (XML, JSON, etc.).
     * 
     * @param shape The structure shape to inspect
     * @return List of members to be serialized in the body
     */
    public static List<MemberShape> getBodyMembers(StructureShape shape) {
        List<MemberShape> result = new ArrayList<>();
        for (MemberShape member : shape.getAllMembers().values()) {
            if (!member.hasTrait(HttpLabelTrait.class) &&
                !member.hasTrait(HttpQueryTrait.class) &&
                !member.hasTrait(HttpQueryParamsTrait.class) &&
                !member.hasTrait(HttpHeaderTrait.class) &&
                !member.hasTrait(HttpPrefixHeadersTrait.class) &&
                !member.hasTrait(HttpPayloadTrait.class) &&
                !member.hasTrait(HttpResponseCodeTrait.class)) {
                result.add(member);
            }
        }
        return result;
    }
    
    /**
     * Checks if a shape is a streaming shape (for @httpPayload).
     * 
     * @param shape The shape to check
     * @return true if the shape has the @streaming trait
     */
    public static boolean isStreamingShape(Shape shape) {
        return shape.hasTrait(StreamingTrait.class);
    }
    
    private ProtocolUtils() {
        // Utility class
    }
}
