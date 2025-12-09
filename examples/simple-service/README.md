# Error Types Example

A minimal Smithy model demonstrating error type generation for smithy-unison.

## Error Types Included

### Client Errors (4xx)

| Error | HTTP Status | Description |
|-------|-------------|-------------|
| `ResourceNotFound` | 404 | Resource does not exist |
| `ResourceAlreadyExists` | 409 | Resource already exists |
| `AccessDenied` | 403 | Access to resource denied |
| `ValidationError` | 400 | Request validation failed |
| `ResourceInUse` | 409 | Resource is in use |
| `QuotaExceeded` | 429 | Quota limit exceeded |

### Server Errors (5xx)

| Error | HTTP Status | Description |
|-------|-------------|-------------|
| `InternalError` | 500 | Internal server error |

## Expected Unison Output

```unison
{{ The specified resource does not exist.

Error category: client
HTTP status: 404 }}
type ResourceNotFound = {
  message : Text,
  resourceId : Optional Text
}

{{ Access to the resource is denied.

Error category: client
HTTP status: 403 }}
type AccessDenied = {
  message : Text,
  action : Optional Text,
  resource : Optional Text
}

{{ An internal server error occurred.

Error category: server
HTTP status: 500 }}
type InternalError = {
  message : Text,
  requestId : Optional Text
}
```

## Building

```bash
# From project root
./gradlew :examples:error-types:build
```
