package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonWriter;

/**
 * Generates Unison functions for XML encoding and decoding.
 * 
 * <p>Used by the REST-XML protocol (S3, CloudFront, Route 53, SES) for
 * serializing request bodies and deserializing response bodies.
 * 
 * <h2>Generated Functions</h2>
 * <ul>
 *   <li>{@code Aws.Xml.encode} - Encode Unison values to XML</li>
 *   <li>{@code Aws.Xml.decode} - Decode XML to Unison values</li>
 *   <li>{@code Aws.Xml.extractElement} - Extract text content from XML element</li>
 *   <li>{@code Aws.Xml.extractAttribute} - Extract attribute value from XML element</li>
 * </ul>
 * 
 * <h2>XML Format</h2>
 * <p>AWS REST-XML uses a specific XML format:
 * <pre>
 * &lt;RootElement xmlns="http://s3.amazonaws.com/doc/2006-03-01/"&gt;
 *   &lt;ChildElement&gt;value&lt;/ChildElement&gt;
 *   &lt;NestedElement&gt;
 *     &lt;Field&gt;value&lt;/Field&gt;
 *   &lt;/NestedElement&gt;
 * &lt;/RootElement&gt;
 * </pre>
 */
public class XmlGenerator {
    
    /**
     * Creates a new XmlGenerator.
     */
    public XmlGenerator() {
    }
    
    /**
     * Generates all XML utility functions.
     * 
     * @param writer The Unison code writer
     */
    public void generate(UnisonWriter writer) {
        generateXmlEscape(writer);
        generateExtractElement(writer);
        generateExtractAttribute(writer);
        generateEncode(writer);
        generateDecode(writer);
    }
    
    /**
     * Generates the XML escape function for special characters.
     */
    public void generateXmlEscape(UnisonWriter writer) {
        writer.writeDocComment(
            "Escape special XML characters in text content.\n\n" +
            "Converts: & -> &amp; < -> &lt; > -> &gt; \" -> &quot; ' -> &apos;");
        
        writer.writeSignature("Aws.Xml.escape", "Text -> Text");
        writer.write("Aws.Xml.escape text =");
        writer.indent();
        writer.write("text");
        writer.indent();
        writer.writeWithNoFormatting("|> Text.replace \"&\" \"&amp;\"");
        writer.writeWithNoFormatting("|> Text.replace \"<\" \"&lt;\"");
        writer.writeWithNoFormatting("|> Text.replace \">\" \"&gt;\"");
        writer.writeWithNoFormatting("|> Text.replace \"\\\"\" \"&quot;\"");
        writer.writeWithNoFormatting("|> Text.replace \"'\" \"&apos;\"");
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the XML unescape function.
     */
    public void generateXmlUnescape(UnisonWriter writer) {
        writer.writeDocComment(
            "Unescape XML entities in text content.\n\n" +
            "Converts: &amp; -> & &lt; -> < &gt; -> > &quot; -> \" &apos; -> '");
        
        writer.writeSignature("Aws.Xml.unescape", "Text -> Text");
        writer.write("Aws.Xml.unescape text =");
        writer.indent();
        writer.write("text");
        writer.indent();
        writer.writeWithNoFormatting("|> Text.replace \"&amp;\" \"&\"");
        writer.writeWithNoFormatting("|> Text.replace \"&lt;\" \"<\"");
        writer.writeWithNoFormatting("|> Text.replace \"&gt;\" \">\"");
        writer.writeWithNoFormatting("|> Text.replace \"&quot;\" \"\\\"\"");
        writer.writeWithNoFormatting("|> Text.replace \"&apos;\" \"'\"");
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the XML element extraction function.
     * 
     * <p>Extracts the text content of a named XML element:
     * <pre>
     * extractElement "Code" "&lt;Error&gt;&lt;Code&gt;NoSuchKey&lt;/Code&gt;&lt;/Error&gt;"
     * -- Returns: "NoSuchKey"
     * </pre>
     */
    public void generateExtractElement(UnisonWriter writer) {
        writer.writeDocComment(
            "Extract text content from an XML element by tag name.\n\n" +
            "Example: extractElement \"Code\" \"<Error><Code>NoSuchKey</Code></Error>\"\n" +
            "Returns: \"NoSuchKey\"\n\n" +
            "Returns empty string if element not found.");
        
        writer.writeSignature("Aws.Xml.extractElement", "Text -> Text -> Text");
        writer.write("Aws.Xml.extractElement tagName xml =");
        writer.indent();
        writer.write("let");
        writer.indent();
        writer.writeWithNoFormatting("openTag = \"<\" ++ tagName ++ \">\"");
        writer.writeWithNoFormatting("closeTag = \"</\" ++ tagName ++ \">\"");
        writer.write("startIdx = Text.indexOf openTag xml");
        writer.write("endIdx = Text.indexOf closeTag xml");
        writer.dedent();
        writer.write("match (startIdx, endIdx) with");
        writer.indent();
        writer.write("(Some start, Some end) ->");
        writer.indent();
        writer.write("contentStart = start + Text.size openTag");
        writer.write("Text.slice contentStart end xml |> Aws.Xml.unescape");
        writer.dedent();
        writer.write("_ -> \"\"");
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the XML attribute extraction function.
     */
    public void generateExtractAttribute(UnisonWriter writer) {
        writer.writeDocComment(
            "Extract an attribute value from an XML element.\n\n" +
            "Example: extractAttribute \"xmlns\" \"<Bucket xmlns=\\\"http://...\\\">...</Bucket>\"\n" +
            "Returns: \"http://...\"\n\n" +
            "Returns empty string if attribute not found.");
        
        writer.writeSignature("Aws.Xml.extractAttribute", "Text -> Text -> Text");
        writer.write("Aws.Xml.extractAttribute attrName xml =");
        writer.indent();
        writer.write("let");
        writer.indent();
        writer.writeWithNoFormatting("pattern = attrName ++ \"=\\\"\"");
        writer.write("startIdx = Text.indexOf pattern xml");
        writer.dedent();
        writer.write("match startIdx with");
        writer.indent();
        writer.write("Some start ->");
        writer.indent();
        writer.write("valueStart = start + Text.size pattern");
        writer.writeWithNoFormatting("rest = Text.drop valueStart xml");
        writer.writeWithNoFormatting("endIdx = Text.indexOf \"\\\"\" rest");
        writer.write("match endIdx with");
        writer.indent();
        writer.write("Some end -> Text.take end rest");
        writer.write("None -> \"\"");
        writer.dedent();
        writer.dedent();
        writer.write("None -> \"\"");
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the XML encode function.
     * 
     * <p>This is a placeholder that generates stub code, as full XML encoding
     * requires reflection/generic programming which Unison handles differently.
     */
    public void generateEncode(UnisonWriter writer) {
        writer.writeDocComment(
            "Encode a value to XML bytes.\n\n" +
            "Note: This is a placeholder. Actual encoding requires type-specific\n" +
            "serialization functions generated per structure.");
        
        writer.writeSignature("Aws.Xml.encode", "a -> Bytes");
        writer.write("Aws.Xml.encode value =");
        writer.indent();
        writer.write("-- Placeholder: actual implementation depends on type");
        writer.write("-- Each structure type will have its own toXml function");
        writer.write("Bytes.empty");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates the XML decode function.
     * 
     * <p>This is a placeholder that generates stub code, as full XML decoding
     * requires reflection/generic programming.
     */
    public void generateDecode(UnisonWriter writer) {
        writer.writeDocComment(
            "Decode XML bytes to a value.\n\n" +
            "Note: This is a placeholder. Actual decoding requires type-specific\n" +
            "deserialization functions generated per structure.");
        
        writer.writeSignature("Aws.Xml.decode", "Bytes -> a");
        writer.write("Aws.Xml.decode bytes =");
        writer.indent();
        writer.write("-- Placeholder: actual implementation depends on type");
        writer.write("-- Each structure type will have its own fromXml function");
        writer.write("bug \"Aws.Xml.decode: not implemented - use type-specific fromXml\"");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates a helper to wrap content in an XML element.
     */
    public void generateElement(UnisonWriter writer) {
        writer.writeDocComment(
            "Wrap content in an XML element.\n\n" +
            "Example: element \"Name\" \"MyBucket\" -> \"<Name>MyBucket</Name>\"");
        
        writer.writeSignature("Aws.Xml.element", "Text -> Text -> Text");
        writer.write("Aws.Xml.element tagName content =");
        writer.indent();
        writer.writeWithNoFormatting("\"<\" ++ tagName ++ \">\" ++ Aws.Xml.escape content ++ \"</\" ++ tagName ++ \">\"");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates a helper to create an XML element with attributes.
     */
    public void generateElementWithAttrs(UnisonWriter writer) {
        writer.writeDocComment(
            "Create an XML element with attributes.\n\n" +
            "Example: elementWithAttrs \"Bucket\" [(\"xmlns\", \"http://...\")] \"content\"\n" +
            "-> \"<Bucket xmlns=\\\"http://...\\\">content</Bucket>\"");
        
        writer.writeSignature("Aws.Xml.elementWithAttrs", "Text -> [(Text, Text)] -> Text -> Text");
        writer.write("Aws.Xml.elementWithAttrs tagName attrs content =");
        writer.indent();
        writer.write("let");
        writer.indent();
        writer.write("attrStr = attrs");
        writer.indent();
        writer.writeWithNoFormatting("|> List.map (cases (name, value) -> \" \" ++ name ++ \"=\\\"\" ++ value ++ \"\\\"\")");
        writer.writeWithNoFormatting("|> Text.join \"\"");
        writer.dedent();
        writer.dedent();
        writer.writeWithNoFormatting("\"<\" ++ tagName ++ attrStr ++ \">\" ++ content ++ \"</\" ++ tagName ++ \">\"");
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates a helper to create an optional XML element.
     */
    public void generateOptionalElement(UnisonWriter writer) {
        writer.writeDocComment(
            "Create an XML element only if the value is present.\n\n" +
            "Example: optionalElement \"VersionId\" (Some \"v1\") -> \"<VersionId>v1</VersionId>\"\n" +
            "Example: optionalElement \"VersionId\" None -> \"\"");
        
        writer.writeSignature("Aws.Xml.optionalElement", "Text -> Optional Text -> Text");
        writer.write("Aws.Xml.optionalElement tagName maybeValue =");
        writer.indent();
        writer.write("match maybeValue with");
        writer.indent();
        writer.write("Some value -> Aws.Xml.element tagName value");
        writer.write("None -> \"\"");
        writer.dedent();
        writer.dedent();
        writer.writeBlankLine();
    }
    
    /**
     * Generates all XML functions including helpers.
     * 
     * @param writer The Unison code writer
     */
    public void generateAll(UnisonWriter writer) {
        writer.writeComment("=== AWS XML Utilities ===");
        writer.writeBlankLine();
        
        generateXmlEscape(writer);
        generateXmlUnescape(writer);
        generateElement(writer);
        generateElementWithAttrs(writer);
        generateOptionalElement(writer);
        generateExtractElement(writer);
        generateExtractAttribute(writer);
        
        writer.writeComment("=== XML Encode/Decode Placeholders ===");
        writer.writeBlankLine();
        
        generateEncode(writer);
        generateDecode(writer);
    }
}
