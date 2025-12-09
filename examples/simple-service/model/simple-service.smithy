$version: "2.0"

namespace com.example

service SimpleService {
    version: "2024-01-01"
    operations: [GetItem, PutItem]
}

@http(method: "GET", uri: "/items/{id}")
operation GetItem {
    input: GetItemInput
    output: GetItemOutput
    errors: [ItemNotFound]
}

@http(method: "PUT", uri: "/items/{id}")
operation PutItem {
    input: PutItemInput
    output: PutItemOutput
}

@input
structure GetItemInput {
    @required
    @httpLabel
    id: String
}

@output
structure GetItemOutput {
    id: String
    name: String
    data: ItemData
}

@input
structure PutItemInput {
    @required
    @httpLabel
    id: String
    
    @required
    name: String
    
    data: ItemData
}

@output
structure PutItemOutput {
    id: String
}

structure ItemData {
    value: String
    timestamp: Timestamp
}

@error("client")
structure ItemNotFound {
    @required
    message: String
}
