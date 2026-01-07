$version: "2.0"

metadata suppressions = [
    {
        id: "UnresolvedShape"
        namespace: "*"
    }
]

namespace example.sqs

/// Test service for AWS Query protocol
@trait(selector: "service")
structure awsQuery {}

@awsQuery
service SQS {
    version: "2012-11-05"
    operations: [
        SendMessage
        SendMessageBatch
        ReceiveMessage
        DeleteMessage
    ]
}

/// Send a message to a queue
operation SendMessage {
    input: SendMessageRequest
    output: SendMessageResponse
    errors: [
        InvalidMessageContents
        UnsupportedOperation
    ]
}

/// Send multiple messages in a batch
operation SendMessageBatch {
    input: SendMessageBatchRequest
    output: SendMessageBatchResponse
}

/// Receive messages from a queue
operation ReceiveMessage {
    input: ReceiveMessageRequest
    output: ReceiveMessageResponse
}

/// Delete a message from a queue
operation DeleteMessage {
    input: DeleteMessageRequest
    output: DeleteMessageResponse
}

// ========== Input Structures ==========

structure SendMessageRequest {
    @required
    QueueUrl: String
    
    @required
    MessageBody: String
    
    DelaySeconds: Integer
    
    MessageAttributes: MessageAttributeMap
}

structure SendMessageBatchRequest {
    @required
    QueueUrl: String
    
    @required
    Entries: SendMessageBatchRequestEntryList
}

structure ReceiveMessageRequest {
    @required
    QueueUrl: String
    
    MaxNumberOfMessages: Integer
    
    VisibilityTimeout: Integer
    
    WaitTimeSeconds: Integer
    
    AttributeNames: AttributeNameList
}

structure DeleteMessageRequest {
    @required
    QueueUrl: String
    
    @required
    ReceiptHandle: String
}

// ========== Output Structures ==========

structure SendMessageResponse {
    MessageId: String
    
    MD5OfMessageBody: String
    
    SequenceNumber: String
}

structure SendMessageBatchResponse {
    @required
    Successful: SendMessageBatchResultEntryList
    
    @required
    Failed: BatchResultErrorEntryList
}

structure ReceiveMessageResponse {
    Messages: MessageList
}

structure DeleteMessageResponse {
}

// ========== Nested Structures ==========

structure SendMessageBatchRequestEntry {
    @required
    Id: String
    
    @required
    MessageBody: String
    
    DelaySeconds: Integer
}

structure SendMessageBatchResultEntry {
    @required
    Id: String
    
    @required
    MessageId: String
    
    MD5OfMessageBody: String
}

structure BatchResultErrorEntry {
    @required
    Id: String
    
    @required
    SenderFault: Boolean
    
    @required
    Code: String
    
    Message: String
}

structure Message {
    MessageId: String
    
    ReceiptHandle: String
    
    MD5OfBody: String
    
    Body: String
    
    Attributes: MessageSystemAttributeMap
}

structure MessageAttribute {
    StringValue: String
    
    BinaryValue: Blob
    
    @required
    DataType: String
}

// ========== Lists and Maps ==========

list SendMessageBatchRequestEntryList {
    member: SendMessageBatchRequestEntry
}

list SendMessageBatchResultEntryList {
    member: SendMessageBatchResultEntry
}

list BatchResultErrorEntryList {
    member: BatchResultErrorEntry
}

list MessageList {
    member: Message
}

list AttributeNameList {
    member: String
}

map MessageAttributeMap {
    key: String
    value: MessageAttribute
}

map MessageSystemAttributeMap {
    key: String
    value: String
}

// ========== Error Structures ==========

@error("client")
structure InvalidMessageContents {
    @required
    message: String
}

@error("client")
structure UnsupportedOperation {
    @required
    message: String
}
