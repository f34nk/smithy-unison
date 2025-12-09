$version: "2.0"

// A minimal service demonstrating error types for smithy-unison
//
// This example shows how Smithy error shapes are represented in Unison

namespace com.example.errors

use aws.protocols#restXml

/// A minimal service demonstrating error types
@restXml
service ErrorDemoService {
    version: "2024-01-01"
    operations: [
        GetResource
        CreateResource
        DeleteResource
    ]
}

// ============================================================================
// Operations
// ============================================================================

/// Get a resource by ID
@readonly
@http(method: "GET", uri: "/resources/{resourceId}")
operation GetResource {
    input: GetResourceInput
    output: GetResourceOutput
    errors: [
        ResourceNotFound
        AccessDenied
        InternalError
    ]
}

/// Create a new resource
@http(method: "POST", uri: "/resources")
operation CreateResource {
    input: CreateResourceInput
    output: CreateResourceOutput
    errors: [
        ResourceAlreadyExists
        ValidationError
        QuotaExceeded
        InternalError
    ]
}

/// Delete a resource
@idempotent
@http(method: "DELETE", uri: "/resources/{resourceId}")
operation DeleteResource {
    input: DeleteResourceInput
    output: DeleteResourceOutput
    errors: [
        ResourceNotFound
        ResourceInUse
        AccessDenied
        InternalError
    ]
}

// ============================================================================
// Input/Output Structures
// ============================================================================

structure GetResourceInput {
    @required
    @httpLabel
    resourceId: String
}

structure GetResourceOutput {
    @required
    resourceId: String
    
    @required
    name: String
    
    createdAt: String
}

structure CreateResourceInput {
    @required
    name: String
    
    description: String
}

structure CreateResourceOutput {
    @required
    resourceId: String
}

structure DeleteResourceInput {
    @required
    @httpLabel
    resourceId: String
}

structure DeleteResourceOutput {
    success: Boolean
}

// ============================================================================
// Error Types - Client Errors (4xx)
// ============================================================================

/// The specified resource does not exist.
@error("client")
@httpError(404)
structure ResourceNotFound {
    @required
    message: String
    
    /// The resource ID that was not found
    resourceId: String
}

/// The resource already exists.
@error("client")
@httpError(409)
structure ResourceAlreadyExists {
    @required
    message: String
    
    /// The conflicting resource ID
    existingResourceId: String
}

/// Access to the resource is denied.
@error("client")
@httpError(403)
structure AccessDenied {
    @required
    message: String
    
    /// The action that was denied
    action: String
    
    /// The resource that access was denied to
    resource: String
}

/// The request failed validation.
@error("client")
@httpError(400)
structure ValidationError {
    @required
    message: String
    
    /// The field that failed validation
    fieldName: String
    
    /// The reason for the validation failure
    reason: String
}

/// The resource is currently in use and cannot be modified.
@error("client")
@httpError(409)
structure ResourceInUse {
    @required
    message: String
    
    /// The resource that is in use
    resourceId: String
    
    /// What is using the resource
    usedBy: String
}

/// The quota has been exceeded.
@error("client")
@httpError(429)
structure QuotaExceeded {
    @required
    message: String
    
    /// The quota that was exceeded
    quotaName: String
    
    /// The current limit
    limit: Integer
    
    /// The current usage
    currentUsage: Integer
}

// ============================================================================
// Error Types - Server Errors (5xx)
// ============================================================================

/// An internal server error occurred.
@error("server")
@httpError(500)
structure InternalError {
    @required
    message: String
    
    /// A unique request ID for debugging
    requestId: String
}
