package io.smithy.unison.codegen.generators;

import io.smithy.unison.codegen.UnisonWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XmlGenerator.
 */
public class XmlGeneratorTest {
    
    private XmlGenerator generator;
    private UnisonWriter writer;
    
    @BeforeEach
    void setUp() {
        generator = new XmlGenerator();
        writer = new UnisonWriter("aws.xml");
    }
    
    @Test
    void testGenerateXmlEscape() {
        generator.generateXmlEscape(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.Xml.escape : Text -> Text"),
                "Should generate correct signature. Got: " + output);
        
        // Check escaping of XML special characters
        assertTrue(output.contains("&amp;"), "Should escape ampersand");
        assertTrue(output.contains("&lt;"), "Should escape less-than");
        assertTrue(output.contains("&gt;"), "Should escape greater-than");
        assertTrue(output.contains("&quot;"), "Should escape quote");
        assertTrue(output.contains("&apos;"), "Should escape apostrophe");
    }
    
    @Test
    void testGenerateXmlUnescape() {
        generator.generateXmlUnescape(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.Xml.unescape : Text -> Text"),
                "Should generate correct signature. Got: " + output);
        
        // Check unescaping patterns
        assertTrue(output.contains("&amp;") && output.contains("&"),
                "Should unescape ampersand. Got: " + output);
    }
    
    @Test
    void testGenerateExtractElement() {
        generator.generateExtractElement(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.Xml.extractElement : Text -> Text -> Text"),
                "Should generate correct signature. Got: " + output);
        
        // Check that it looks for opening and closing tags
        assertTrue(output.contains("openTag") && output.contains("closeTag"),
                "Should build open and close tags. Got: " + output);
        
        // Check that it handles the case when element is not found
        assertTrue(output.contains("\"\""),
                "Should return empty string if not found. Got: " + output);
        
        // Check that it unescapes the content
        assertTrue(output.contains("unescape"),
                "Should unescape extracted content. Got: " + output);
    }
    
    @Test
    void testGenerateExtractAttribute() {
        generator.generateExtractAttribute(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.Xml.extractAttribute : Text -> Text -> Text"),
                "Should generate correct signature. Got: " + output);
        
        // Check that it handles the case when attribute is not found
        assertTrue(output.contains("None -> \"\""),
                "Should return empty string if not found. Got: " + output);
    }
    
    @Test
    void testGenerateEncode() {
        generator.generateEncode(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.Xml.encode : a -> Bytes"),
                "Should generate correct signature. Got: " + output);
        
        // Should be a placeholder
        assertTrue(output.contains("Bytes.empty") || output.contains("placeholder"),
                "Should be a placeholder implementation. Got: " + output);
    }
    
    @Test
    void testGenerateDecode() {
        generator.generateDecode(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.Xml.decode : Bytes -> a"),
                "Should generate correct signature. Got: " + output);
        
        // Should indicate it's not fully implemented
        assertTrue(output.contains("bug") || output.contains("placeholder"),
                "Should indicate placeholder status. Got: " + output);
    }
    
    @Test
    void testGenerateElement() {
        generator.generateElement(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.Xml.element : Text -> Text -> Text"),
                "Should generate correct signature. Got: " + output);
        
        // Check that it creates open and close tags
        assertTrue(output.contains("<") && output.contains(">") && output.contains("</"),
                "Should create XML tags. Got: " + output);
        
        // Check that it escapes content
        assertTrue(output.contains("escape"),
                "Should escape content. Got: " + output);
    }
    
    @Test
    void testGenerateElementWithAttrs() {
        generator.generateElementWithAttrs(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.Xml.elementWithAttrs : Text -> [(Text, Text)] -> Text -> Text"),
                "Should generate correct signature. Got: " + output);
        
        // Check that it handles attributes
        assertTrue(output.contains("attrStr"),
                "Should build attribute string. Got: " + output);
    }
    
    @Test
    void testGenerateOptionalElement() {
        generator.generateOptionalElement(writer);
        
        String output = writer.toString();
        
        // Check function signature
        assertTrue(output.contains("Aws.Xml.optionalElement : Text -> Optional Text -> Text"),
                "Should generate correct signature. Got: " + output);
        
        // Check that it handles Some and None
        assertTrue(output.contains("Some") && output.contains("None"),
                "Should handle Optional. Got: " + output);
        
        // Should return empty string for None
        assertTrue(output.contains("None -> \"\""),
                "Should return empty string for None. Got: " + output);
    }
    
    @Test
    void testGenerate_producesCoreFunctions() {
        generator.generate(writer);
        
        String output = writer.toString();
        
        // Check all core functions are generated
        assertTrue(output.contains("Aws.Xml.escape :"),
                "Should generate escape. Got: " + output);
        assertTrue(output.contains("Aws.Xml.extractElement :"),
                "Should generate extractElement. Got: " + output);
        assertTrue(output.contains("Aws.Xml.extractAttribute :"),
                "Should generate extractAttribute. Got: " + output);
        assertTrue(output.contains("Aws.Xml.encode :"),
                "Should generate encode. Got: " + output);
        assertTrue(output.contains("Aws.Xml.decode :"),
                "Should generate decode. Got: " + output);
    }
    
    @Test
    void testGenerateAll_producesAllFunctions() {
        generator.generateAll(writer);
        
        String output = writer.toString();
        
        // Check all functions including helpers
        assertTrue(output.contains("Aws.Xml.escape :"), "Should generate escape");
        assertTrue(output.contains("Aws.Xml.unescape :"), "Should generate unescape");
        assertTrue(output.contains("Aws.Xml.element :"), "Should generate element");
        assertTrue(output.contains("Aws.Xml.elementWithAttrs :"), "Should generate elementWithAttrs");
        assertTrue(output.contains("Aws.Xml.optionalElement :"), "Should generate optionalElement");
        assertTrue(output.contains("Aws.Xml.extractElement :"), "Should generate extractElement");
        assertTrue(output.contains("Aws.Xml.extractAttribute :"), "Should generate extractAttribute");
        assertTrue(output.contains("Aws.Xml.encode :"), "Should generate encode");
        assertTrue(output.contains("Aws.Xml.decode :"), "Should generate decode");
        
        // Check section comments
        assertTrue(output.contains("AWS XML Utilities"),
                "Should have utilities section");
    }
    
    @Test
    void testDocumentation_hasExamples() {
        generator.generateExtractElement(writer);
        
        String output = writer.toString();
        
        // Check that documentation includes examples
        assertTrue(output.contains("{{"),
                "Should have documentation. Got: " + output);
        assertTrue(output.contains("Example") || output.contains("example"),
                "Should have examples in docs. Got: " + output);
    }
}
