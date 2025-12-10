package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.PaginatedTrait;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PaginationGenerator}.
 */
class PaginationGeneratorTest {
    
    private PaginationGenerator generator;
    private UnisonWriter writer;
    
    @BeforeEach
    void setUp() {
        generator = new PaginationGenerator();
        writer = new UnisonWriter("test");
    }
    
    @Test
    void testGetPaginatedOperations_withPaginatedOps() {
        Model model = Model.assembler()
            .addUnparsedModel("test.smithy",
                "$version: \"2.0\"\n" +
                "namespace test\n" +
                "service TestService {\n" +
                "  version: \"1.0\"\n" +
                "  operations: [ListItems, GetItem]\n" +
                "}\n" +
                "@paginated(inputToken: \"nextToken\", outputToken: \"nextToken\", items: \"items\")\n" +
                "operation ListItems {\n" +
                "  input: ListItemsInput\n" +
                "  output: ListItemsOutput\n" +
                "}\n" +
                "operation GetItem {\n" +
                "  input: GetItemInput\n" +
                "  output: GetItemOutput\n" +
                "}\n" +
                "structure ListItemsInput { nextToken: String }\n" +
                "structure ListItemsOutput { nextToken: String, items: ItemList }\n" +
                "structure GetItemInput { id: String }\n" +
                "structure GetItemOutput { name: String }\n" +
                "list ItemList { member: Item }\n" +
                "structure Item { name: String }\n")
            .assemble()
            .unwrap();
        
        ServiceShape service = model.expectShape(
            ShapeId.from("test#TestService"), ServiceShape.class);
        
        List<OperationShape> paginated = generator.getPaginatedOperations(service, model);
        
        assertEquals(1, paginated.size(), "Should find one paginated operation");
        assertEquals("test#ListItems", paginated.get(0).getId().toString());
    }
    
    @Test
    void testGetPaginatedOperations_noPaginatedOps() {
        Model model = Model.assembler()
            .addUnparsedModel("test.smithy",
                "$version: \"2.0\"\n" +
                "namespace test\n" +
                "service TestService {\n" +
                "  version: \"1.0\"\n" +
                "  operations: [GetItem]\n" +
                "}\n" +
                "operation GetItem {\n" +
                "  input: GetItemInput\n" +
                "  output: GetItemOutput\n" +
                "}\n" +
                "structure GetItemInput { id: String }\n" +
                "structure GetItemOutput { name: String }\n")
            .assemble()
            .unwrap();
        
        ServiceShape service = model.expectShape(
            ShapeId.from("test#TestService"), ServiceShape.class);
        
        List<OperationShape> paginated = generator.getPaginatedOperations(service, model);
        
        assertTrue(paginated.isEmpty(), "Should find no paginated operations");
    }
    
    @Test
    void testGeneratePaginationHelper() {
        Model model = Model.assembler()
            .addUnparsedModel("test.smithy",
                "$version: \"2.0\"\n" +
                "namespace test\n" +
                "service TestService {\n" +
                "  version: \"1.0\"\n" +
                "  operations: [ListItems]\n" +
                "}\n" +
                "@paginated(inputToken: \"nextToken\", outputToken: \"nextToken\", items: \"items\")\n" +
                "operation ListItems {\n" +
                "  input: ListItemsInput\n" +
                "  output: ListItemsOutput\n" +
                "}\n" +
                "structure ListItemsInput { nextToken: String }\n" +
                "structure ListItemsOutput { nextToken: String, items: ItemList }\n" +
                "list ItemList { member: Item }\n" +
                "structure Item { name: String }\n")
            .assemble()
            .unwrap();
        
        OperationShape operation = model.expectShape(
            ShapeId.from("test#ListItems"), OperationShape.class);
        
        generator.generatePaginationHelper(operation, model, writer);
        String output = writer.toString();
        
        // Verify function signature - now uses concrete type [Item]
        assertTrue(output.contains("listItemsAll"), 
            "Should generate listItemsAll helper");
        assertTrue(output.contains("Config -> ListItemsInput -> '{IO, Exception, Threads} [Item]"),
            "Should have correct signature with concrete item type");
        
        // Verify recursive structure - now uses concrete type [Item]
        assertTrue(output.contains("go : Optional Text -> [Item] -> '{IO, Exception, Threads} [Item]"),
            "Should have recursive go helper with concrete type");
        assertTrue(output.contains("go token acc = do"),
            "Should have token and accumulator parameters with do block");
        
        // Verify token handling - uses Unison record update syntax: TypeName.field.set value record
        assertTrue(output.contains("inputWithToken = ListItemsInput.nextToken.set token input"),
            "Should update input with token using Unison record update syntax");
        assertTrue(output.contains("ListItemsOutput.nextToken response"),
            "Should check output token using accessor function");
        
        // Verify items collection - uses accessor functions: TypeName.field record
        assertTrue(output.contains("ListItemsOutput.items response"),
            "Should access items field using accessor function");
        assertTrue(output.contains("newItems = Optional.getOrElse [] (ListItemsOutput.items response)"),
            "Should handle optional items with correct argument order (default first)");
        assertTrue(output.contains("allItems = (List.++) acc newItems"),
            "Should accumulate items with qualified List.++ operator");
        
        // Verify pagination loop - recursive call must be forced with !
        assertTrue(output.contains("Some nextToken -> !(go (Some nextToken) allItems)"),
            "Should recurse on next token with forced evaluation");
        assertTrue(output.contains("None -> allItems"),
            "Should return accumulated items when done");
        
        // Verify initial call
        assertTrue(output.contains("go None []"),
            "Should start with None token and empty list");
    }
    
    @Test
    void testGeneratePaginationHelper_withCustomTokens() {
        Model model = Model.assembler()
            .addUnparsedModel("test.smithy",
                "$version: \"2.0\"\n" +
                "namespace test\n" +
                "service TestService {\n" +
                "  version: \"1.0\"\n" +
                "  operations: [ListParts]\n" +
                "}\n" +
                "@paginated(inputToken: \"partNumberMarker\", outputToken: \"nextPartNumberMarker\", items: \"parts\")\n" +
                "operation ListParts {\n" +
                "  input: ListPartsInput\n" +
                "  output: ListPartsOutput\n" +
                "}\n" +
                "structure ListPartsInput { partNumberMarker: String }\n" +
                "structure ListPartsOutput { nextPartNumberMarker: String, parts: PartList }\n" +
                "list PartList { member: Part }\n" +
                "structure Part { partNumber: Integer }\n")
            .assemble()
            .unwrap();
        
        OperationShape operation = model.expectShape(
            ShapeId.from("test#ListParts"), OperationShape.class);
        
        generator.generatePaginationHelper(operation, model, writer);
        String output = writer.toString();
        
        // Verify custom token names are used - uses Unison record update syntax and accessor functions
        assertTrue(output.contains("ListPartsInput.partNumberMarker.set token input"),
            "Should use custom input token name with Unison record update syntax");
        assertTrue(output.contains("ListPartsOutput.nextPartNumberMarker response"),
            "Should use custom output token name with accessor function");
        assertTrue(output.contains("ListPartsOutput.parts response"),
            "Should use custom items field name with accessor function");
    }
    
    @Test
    void testGenerate_multipleOperations() {
        Model model = Model.assembler()
            .addUnparsedModel("test.smithy",
                "$version: \"2.0\"\n" +
                "namespace test\n" +
                "service TestService {\n" +
                "  version: \"1.0\"\n" +
                "  operations: [ListItems, ListBuckets, GetItem]\n" +
                "}\n" +
                "@paginated(inputToken: \"token\", outputToken: \"nextToken\", items: \"items\")\n" +
                "operation ListItems {\n" +
                "  input: ListItemsInput\n" +
                "  output: ListItemsOutput\n" +
                "}\n" +
                "@paginated(inputToken: \"marker\", outputToken: \"nextMarker\", items: \"buckets\")\n" +
                "operation ListBuckets {\n" +
                "  input: ListBucketsInput\n" +
                "  output: ListBucketsOutput\n" +
                "}\n" +
                "operation GetItem {\n" +
                "  input: GetItemInput\n" +
                "  output: GetItemOutput\n" +
                "}\n" +
                "structure ListItemsInput { token: String }\n" +
                "structure ListItemsOutput { nextToken: String, items: ItemList }\n" +
                "structure ListBucketsInput { marker: String }\n" +
                "structure ListBucketsOutput { nextMarker: String, buckets: BucketList }\n" +
                "structure GetItemInput { id: String }\n" +
                "structure GetItemOutput { name: String }\n" +
                "list ItemList { member: Item }\n" +
                "list BucketList { member: Bucket }\n" +
                "structure Item { name: String }\n" +
                "structure Bucket { name: String }\n")
            .assemble()
            .unwrap();
        
        ServiceShape service = model.expectShape(
            ShapeId.from("test#TestService"), ServiceShape.class);
        
        generator.generate(service, model, writer);
        String output = writer.toString();
        
        // Should generate helpers for both paginated operations
        assertTrue(output.contains("listItemsAll"),
            "Should generate listItemsAll");
        assertTrue(output.contains("listBucketsAll"),
            "Should generate listBucketsAll");
        
        // Should have section header
        assertTrue(output.contains("Pagination Helpers"),
            "Should have pagination section header");
    }
    
    @Test
    void testDocumentation() {
        Model model = Model.assembler()
            .addUnparsedModel("test.smithy",
                "$version: \"2.0\"\n" +
                "namespace test\n" +
                "service TestService {\n" +
                "  version: \"1.0\"\n" +
                "  operations: [ListItems]\n" +
                "}\n" +
                "@paginated(inputToken: \"nextToken\", outputToken: \"nextToken\", items: \"items\")\n" +
                "operation ListItems {\n" +
                "  input: ListItemsInput\n" +
                "  output: ListItemsOutput\n" +
                "}\n" +
                "structure ListItemsInput { nextToken: String }\n" +
                "structure ListItemsOutput { nextToken: String, items: ItemList }\n" +
                "list ItemList { member: Item }\n" +
                "structure Item { name: String }\n")
            .assemble()
            .unwrap();
        
        OperationShape operation = model.expectShape(
            ShapeId.from("test#ListItems"), OperationShape.class);
        
        generator.generatePaginationHelper(operation, model, writer);
        String output = writer.toString();
        
        // Verify documentation
        assertTrue(output.contains("{{"),
            "Should have doc comment");
        assertTrue(output.contains("Auto-paginating version"),
            "Should document as auto-paginating");
        assertTrue(output.contains("items"),
            "Should mention items field");
        assertTrue(output.contains("nextToken"),
            "Should mention token fields");
    }
}
