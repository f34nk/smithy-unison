package io.smithy.unison.codegen;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolWriter;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Unison code writer with Symbol support.
 * 
 * <p>This class extends {@link SymbolWriter} to provide Unison-specific
 * code generation utilities. It includes custom formatters for working
 * with Smithy Symbols and helper methods for generating Unison code
 * structure elements.
 * 
 * <h2>Custom Formatters</h2>
 * <ul>
 *   <li>{@code $T} - Formats a Symbol's Unison type (from "unisonType" property)</li>
 *   <li>{@code $N} - Formats a Symbol's name</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>
 * UnisonWriter writer = new UnisonWriter("aws.s3");
 * 
 * writer.writeDocComment("Represents an S3 bucket.")
 *       .writeRecordType("Bucket", List.of(
 *           new TypeField("name", "Text"),
 *           new TypeField("creationDate", "Optional Text")
 *       ))
 *       .writeSignature("getBucket", "S3Config -> Text -> '{IO, Exception} Bucket")
 *       .writeFunction("getBucket", "config bucketName", () -> {
 *           writer.write("-- implementation");
 *       });
 * </pre>
 * 
 * @see UnisonImportContainer
 * @see SymbolWriter
 */
public final class UnisonWriter extends SymbolWriter<UnisonWriter, UnisonImportContainer> {
    
    /** Default indentation (2 spaces for Unison). */
    private static final String DEFAULT_INDENT = "  ";
    
    /** The namespace for this writer. */
    private final String namespace;
    
    /**
     * Creates a new UnisonWriter for the specified namespace.
     *
     * @param namespace The Unison namespace (e.g., "aws.s3")
     */
    public UnisonWriter(String namespace) {
        super(new UnisonImportContainer());
        this.namespace = namespace;
        
        setIndentText(DEFAULT_INDENT);
        
        // Register custom formatters
        putFormatter('T', new UnisonTypeFormatter());
        putFormatter('N', new UnisonNameFormatter());
    }
    
    /**
     * Gets the namespace for this writer.
     *
     * @return The Unison namespace
     */
    public String getNamespace() {
        return namespace;
    }
    
    // ========== Basic Writing Methods ==========
    
    /**
     * Writes a blank line without any indentation.
     * 
     * <p>This should be used instead of {@code write("")} when a truly
     * blank line is needed, as {@code write("")} would include the
     * current indentation level, causing trailing whitespace.
     *
     * @return This writer for method chaining
     */
    public UnisonWriter writeBlankLine() {
        int currentIndent = getIndentLevel();
        dedent(currentIndent);
        write("");
        indent(currentIndent);
        return this;
    }
    
    /**
     * Writes a Unison single-line comment.
     * 
     * <p>Generates: {@code -- comment text}
     *
     * @param comment The comment text
     * @return This writer for method chaining
     */
    public UnisonWriter writeComment(String comment) {
        write("-- $L", comment);
        return this;
    }
    
    /**
     * Writes a Unison documentation comment (docstring).
     * 
     * <p>Unison uses double curly braces for documentation:
     * <pre>
     * {{
     * Documentation text here.
     * Can span multiple lines.
     * }}
     * </pre>
     * 
     * <p>This method handles:
     * <ul>
     *   <li>Single-line documentation</li>
     *   <li>Multi-line documentation (splits on newlines)</li>
     *   <li>Empty documentation (writes empty docstring)</li>
     * </ul>
     *
     * @param doc The documentation text (may contain newlines)
     * @return This writer for method chaining
     */
    public UnisonWriter writeDocComment(String doc) {
        if (doc == null || doc.isEmpty()) {
            write("{{}}");
            return this;
        }
        
        String[] lines = doc.split("\n");
        if (lines.length == 1) {
            // Single line - keep it compact
            write("{{ $L }}", doc.trim());
        } else {
            // Multi-line documentation
            write("{{");
            for (String line : lines) {
                write("$L", line);
            }
            write("}}");
        }
        return this;
    }
    
    // ========== Type Definition Methods ==========
    
    /**
     * Writes a Unison record type definition.
     * 
     * <p>Generates a record type with named fields (commas between fields):
     * <pre>
     * type MyType = {
     *   field1 : Text,
     *   field2 : Int
     * }
     * </pre>
     * 
     * <p>Note: Record TYPE definitions use commas, but record LITERALS (construction)
     * use positional arguments without commas.
     * 
     * <p>For empty records:
     * <pre>
     * type EmptyType = {}
     * </pre>
     *
     * @param typeName The name of the type (e.g., "GetObjectInput")
     * @param fields The list of fields with names and types
     * @return This writer for method chaining
     */
    public UnisonWriter writeRecordType(String typeName, List<TypeField> fields) {
        if (fields == null || fields.isEmpty()) {
            // Empty records: use a simple constructor with no fields
            // Unison doesn't support empty record syntax {}
            write("type $L = $L", typeName, typeName);
            writeBlankLine();
            return this;
        }
        
        write("type $L = {", typeName);
        indent();
        for (int i = 0; i < fields.size(); i++) {
            TypeField field = fields.get(i);
            // Commas ARE required in record TYPE definitions
            String comma = (i < fields.size() - 1) ? "," : "";
            write("$L : $L$L", field.name(), field.type(), comma);
        }
        dedent();
        write("}");
        writeBlankLine();
        return this;
    }
    
    /**
     * Writes a Unison union type (sum type / algebraic data type).
     * 
     * <p>Generates a sum type with variants:
     * <pre>
     * type S3Response a
     *   = S3Success a
     *   | S3Error { code : Text, message : Text }
     * </pre>
     * 
     * <p>For simple enums (variants without payloads):
     * <pre>
     * type Status
     *   = Active
     *   | Inactive
     *   | Pending
     * </pre>
     *
     * @param typeName The name of the type (e.g., "S3Response")
     * @param typeParams Optional type parameters (e.g., "a" or "a b"), can be null or empty
     * @param variants The list of variants with optional payloads
     * @return This writer for method chaining
     */
    public UnisonWriter writeUnionType(String typeName, String typeParams, List<Variant> variants) {
        if (variants == null || variants.isEmpty()) {
            if (typeParams != null && !typeParams.isEmpty()) {
                write("type $L $L", typeName, typeParams);
            } else {
                write("type $L", typeName);
            }
            writeBlankLine();
            return this;
        }
        
        // Write type declaration
        if (typeParams != null && !typeParams.isEmpty()) {
            write("type $L $L", typeName, typeParams);
        } else {
            write("type $L", typeName);
        }
        
        // Write variants
        indent();
        for (int i = 0; i < variants.size(); i++) {
            Variant v = variants.get(i);
            String prefix = (i == 0) ? "= " : "| ";
            if (v.payload() != null && !v.payload().isEmpty()) {
                write("$L$L $L", prefix, v.name(), v.payload());
            } else {
                write("$L$L", prefix, v.name());
            }
        }
        dedent();
        writeBlankLine();
        return this;
    }
    
    /**
     * Writes a Unison union type without type parameters.
     * 
     * <p>Convenience method equivalent to {@code writeUnionType(typeName, null, variants)}.
     *
     * @param typeName The name of the type
     * @param variants The list of variants
     * @return This writer for method chaining
     */
    public UnisonWriter writeUnionType(String typeName, List<Variant> variants) {
        return writeUnionType(typeName, null, variants);
    }
    
    // ========== Function Definition Methods ==========
    
    /**
     * Writes a Unison type signature for a function.
     * 
     * <p>Generates:
     * <pre>
     * functionName : ParamType -> '{IO, Exception} ReturnType
     * </pre>
     * 
     * <p>Examples:
     * <ul>
     *   <li>{@code getObject : S3Config -> GetObjectInput -> '{IO, Exception} GetObjectOutput}</li>
     *   <li>{@code listBuckets : S3Config -> '{IO, Exception} [Bucket]}</li>
     * </ul>
     *
     * @param name The function name
     * @param signature The type signature (without the name and colon)
     * @return This writer for method chaining
     */
    public UnisonWriter writeSignature(String name, String signature) {
        write("$L : $L", name, signature);
        return this;
    }
    
    /**
     * Writes a Unison function definition with a body.
     * 
     * <p>Generates:
     * <pre>
     * functionName param1 param2 =
     *   body
     * </pre>
     * 
     * <p>Example usage:
     * <pre>
     * writer.writeFunction("getObject", "config input", () -> {
     *     writer.write("let");
     *     writer.indent();
     *     writer.write("url = buildUrl config input");
     *     writer.dedent();
     *     writer.write("Http.get url");
     * });
     * </pre>
     *
     * @param name The function name
     * @param params The function parameters (space-separated)
     * @param body A runnable that writes the function body
     * @return This writer for method chaining
     */
    public UnisonWriter writeFunction(String name, String params, Runnable body) {
        if (params == null || params.isEmpty()) {
            write("$L =", name);
        } else {
            write("$L $L =", name, params);
        }
        indent();
        body.run();
        dedent();
        writeBlankLine();
        return this;
    }
    
    /**
     * Writes a function definition for a delayed computation (thunk).
     * 
     * <p>In Unison, functions that perform effects often take a unit parameter:
     * <pre>
     * myFunction config _ =
     *   body
     * </pre>
     *
     * @param name The function name
     * @param params The function parameters (without the trailing underscore)
     * @param body A runnable that writes the function body
     * @return This writer for method chaining
     */
    public UnisonWriter writeFunctionWithThunk(String name, String params, Runnable body) {
        String fullParams = (params == null || params.isEmpty()) ? "_" : params + " _";
        return writeFunction(name, fullParams, body);
    }
    
    /**
     * Writes a let binding block.
     * 
     * <p>Generates:
     * <pre>
     * let
     *   binding1 = value1
     *   binding2 = value2
     * result
     * </pre>
     *
     * @param bindings A runnable that writes the let bindings
     * @param result A runnable that writes the result expression
     * @return This writer for method chaining
     */
    public UnisonWriter writeLet(Runnable bindings, Runnable result) {
        write("let");
        indent();
        bindings.run();
        dedent();
        result.run();
        return this;
    }
    
    /**
     * Writes a Unison match expression.
     * 
     * <p>Generates:
     * <pre>
     * match expr with
     *   Pattern1 -> result1
     *   Pattern2 -> result2
     * </pre>
     * 
     * <p>Example usage:
     * <pre>
     * writer.writeMatch("response", List.of(
     *     new MatchCase("Http.Response.Ok body", "parseBody body"),
     *     new MatchCase("Http.Response.Error err", "abort (Exception.raise err)")
     * ));
     * </pre>
     *
     * @param expr The expression to match on
     * @param cases The list of match cases (pattern -> result pairs)
     * @return This writer for method chaining
     */
    public UnisonWriter writeMatch(String expr, List<MatchCase> cases) {
        if (cases == null || cases.isEmpty()) {
            write("match $L with", expr);
            indent();
            write("_ -> ()");
            dedent();
            return this;
        }
        
        write("match $L with", expr);
        indent();
        for (MatchCase c : cases) {
            write("$L -> $L", c.pattern(), c.result());
        }
        dedent();
        return this;
    }
    
    /**
     * Writes a match expression with multi-line case bodies.
     * 
     * <p>Generates:
     * <pre>
     * match expr with
     *   Pattern1 ->
     *     body1
     *   Pattern2 ->
     *     body2
     * </pre>
     *
     * @param expr The expression to match on
     * @param cases The list of match cases with body runnables
     * @return This writer for method chaining
     */
    public UnisonWriter writeMatchWithBodies(String expr, List<MatchCaseWithBody> cases) {
        if (cases == null || cases.isEmpty()) {
            write("match $L with", expr);
            indent();
            write("_ -> ()");
            dedent();
            return this;
        }
        
        write("match $L with", expr);
        indent();
        for (MatchCaseWithBody c : cases) {
            write("$L ->", c.pattern());
            indent();
            c.body().run();
            dedent();
        }
        dedent();
        return this;
    }
    
    /**
     * Writes an ability handler expression.
     * 
     * <p>Generates: {@code handle computation with handler}
     * 
     * <p>This is used for handling Unison's effect system (abilities).
     *
     * @param computation The computation expression
     * @param handler The handler expression
     * @return This writer for method chaining
     */
    public UnisonWriter writeHandle(String computation, String handler) {
        write("handle $L with $L", computation, handler);
        return this;
    }
    
    // ========== HTTP/AWS Specific Methods ==========
    
    /**
     * Writes an HTTP request function body placeholder.
     * 
     * <p>Generates a basic HTTP request structure for AWS SDK calls.
     *
     * @param method The HTTP method (GET, POST, PUT, DELETE, etc.)
     * @param url The URL expression
     * @param headers The headers expression
     * @param body The body expression
     * @return This writer for method chaining
     */
    public UnisonWriter writeHttpRequest(String method, String url, String headers, String body) {
        write("request = Http.Request.$L $L $L $L", method.toLowerCase(), url, headers, body);
        return this;
    }
    
    /**
     * Writes HTTP status code matching for AWS responses.
     * 
     * <p>Generates:
     * <pre>
     * match Http.Response.status response with
     *   200 ->
     *     successHandler
     *   code ->
     *     errorHandler
     * </pre>
     *
     * @param successCase Runnable that writes the success case body
     * @param errorCase Runnable that writes the error case body
     * @return This writer for method chaining
     */
    public UnisonWriter writeStatusMatch(Runnable successCase, Runnable errorCase) {
        write("match Http.Response.status response with");
        indent();
        write("200 ->");
        indent();
        successCase.run();
        dedent();
        write("code ->");
        indent();
        errorCase.run();
        dedent();
        dedent();
        return this;
    }
    
    // ========== Use Statements ==========
    
    /**
     * Writes use statements from the import container.
     * 
     * <p>Generates:
     * <pre>
     * use lib.base
     * use aws.http
     * </pre>
     *
     * @return This writer for method chaining
     */
    public UnisonWriter writeUseStatements() {
        UnisonImportContainer imports = getImportContainer();
        if (imports.hasUseStatements()) {
            writeWithNoFormatting(imports.toString());
            writeBlankLine();
        }
        return this;
    }
    
    /**
     * Adds a use statement to the import container.
     *
     * @param namespace The namespace to use
     * @return This writer for method chaining
     */
    public UnisonWriter addUse(String namespace) {
        getImportContainer().addUse(namespace);
        return this;
    }
    
    // ========== Enum Helper Methods ==========
    
    /**
     * Writes an enum-to-text conversion function.
     * 
     * <p>Generates:
     * <pre>
     * enumNameToText : EnumName -> Text
     * enumNameToText val = match val with
     *   EnumName'Variant1 -> "variant1"
     *   EnumName'Variant2 -> "variant2"
     * </pre>
     *
     * @param enumName The name of the enum type
     * @param cases List of variant name to text value mappings
     * @return This writer for method chaining
     */
    public UnisonWriter writeEnumToTextFunction(String enumName, List<EnumMapping> cases) {
        String funcName = toLowerCamelCase(enumName) + "ToText";
        writeSignature(funcName, enumName + " -> Text");
        writeFunction(funcName, "val", () -> {
            writeMatch("val", cases.stream()
                .map(c -> new MatchCase(enumName + "'" + c.variantName(), "\"" + c.textValue() + "\""))
                .toList());
        });
        return this;
    }
    
    /**
     * Writes a text-to-enum conversion function.
     * 
     * <p>Generates:
     * <pre>
     * enumNameFromText : Text -> Optional EnumName
     * enumNameFromText t = match t with
     *   "variant1" -> Some EnumName'Variant1
     *   "variant2" -> Some EnumName'Variant2
     *   _ -> None
     * </pre>
     *
     * @param enumName The name of the enum type
     * @param cases List of text value to variant name mappings
     * @return This writer for method chaining
     */
    public UnisonWriter writeEnumFromTextFunction(String enumName, List<EnumMapping> cases) {
        String funcName = toLowerCamelCase(enumName) + "FromText";
        writeSignature(funcName, "Text -> Optional " + enumName);
        writeFunction(funcName, "t", () -> {
            List<MatchCase> matchCases = new java.util.ArrayList<>(cases.stream()
                .map(c -> new MatchCase("\"" + c.textValue() + "\"", "Some " + enumName + "'" + c.variantName()))
                .toList());
            matchCases.add(new MatchCase("_", "None"));
            writeMatch("t", matchCases);
        });
        return this;
    }
    
    // ========== Custom Formatters ==========
    
    /**
     * Formatter for Unison type names from Symbols.
     * 
     * <p>Usage in format strings: {@code $T}
     * 
     * <p>If the value is a {@link Symbol}, returns its "unisonType" property
     * or "a" as default. Otherwise, returns the string representation.
     */
    private static final class UnisonTypeFormatter implements BiFunction<Object, String, String> {
        @Override
        public String apply(Object value, String indent) {
            if (value instanceof Symbol) {
                Symbol symbol = (Symbol) value;
                return symbol.getProperty("unisonType", String.class).orElse("a");
            }
            return String.valueOf(value);
        }
    }
    
    /**
     * Formatter for Unison names from Symbols.
     * 
     * <p>Usage in format strings: {@code $N}
     * 
     * <p>If the value is a {@link Symbol}, returns its name.
     * Otherwise, returns the string representation.
     */
    private static final class UnisonNameFormatter implements BiFunction<Object, String, String> {
        @Override
        public String apply(Object value, String indent) {
            if (value instanceof Symbol) {
                return ((Symbol) value).getName();
            }
            return String.valueOf(value);
        }
    }
    
    // ========== Utility Methods ==========
    
    /**
     * Converts a PascalCase string to lowerCamelCase.
     */
    private String toLowerCamelCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
    
    // ========== Factory Method ==========
    
    /**
     * Creates a factory for UnisonWriter instances.
     * 
     * <p>This is useful when integrating with Smithy's WriterDelegator.
     *
     * @return A factory that creates UnisonWriter instances
     */
    public static Factory factory() {
        return new Factory();
    }
    
    /**
     * Factory class for creating UnisonWriter instances.
     */
    public static final class Factory implements SymbolWriter.Factory<UnisonWriter> {
        @Override
        public UnisonWriter apply(String filename, String namespace) {
            // Extract namespace from filename (remove .u extension)
            String ns = filename;
            if (filename.endsWith(".u")) {
                ns = filename.substring(0, filename.length() - 2);
            }
            return new UnisonWriter(ns);
        }
    }
    
    // ========== Helper Classes ==========
    
    /**
     * Represents a field in a record type.
     * 
     * <p>Example: {@code new TypeField("bucket", "Text")} generates {@code bucket : Text}
     */
    public static final class TypeField {
        private final String name;
        private final String type;
        
        /**
         * Creates a new TypeField.
         *
         * @param name The field name (should be lowerCamelCase)
         * @param type The Unison type (e.g., "Text", "Optional Int", "[Byte]")
         */
        public TypeField(String name, String type) {
            this.name = name;
            this.type = type;
        }
        
        /** Returns the field name. */
        public String name() { return name; }
        
        /** Returns the field type. */
        public String type() { return type; }
    }
    
    /**
     * Represents a variant in a union type.
     * 
     * <p>Examples:
     * <ul>
     *   <li>{@code new Variant("Success", "a")} generates {@code Success a}</li>
     *   <li>{@code new Variant("Empty", null)} generates {@code Empty}</li>
     *   <li>{@code new Variant("Error", "{ code : Text, message : Text }")} generates {@code Error { code : Text, message : Text }}</li>
     * </ul>
     */
    public static final class Variant {
        private final String name;
        private final String payload;
        
        /**
         * Creates a new Variant.
         *
         * @param name The variant name (should be PascalCase)
         * @param payload The payload type, or null/empty for unit variants
         */
        public Variant(String name, String payload) {
            this.name = name;
            this.payload = payload;
        }
        
        /**
         * Creates a unit variant (no payload).
         *
         * @param name The variant name
         */
        public Variant(String name) {
            this(name, null);
        }
        
        /** Returns the variant name. */
        public String name() { return name; }
        
        /** Returns the payload type, or null if this is a unit variant. */
        public String payload() { return payload; }
    }
    
    /**
     * Represents a case in a match expression (single-line result).
     * 
     * <p>Example: {@code new MatchCase("Some x", "x")} generates {@code Some x -> x}
     */
    public static final class MatchCase {
        private final String pattern;
        private final String result;
        
        /**
         * Creates a new MatchCase.
         *
         * @param pattern The pattern to match
         * @param result The result expression (single line)
         */
        public MatchCase(String pattern, String result) {
            this.pattern = pattern;
            this.result = result;
        }
        
        /** Returns the pattern. */
        public String pattern() { return pattern; }
        
        /** Returns the result expression. */
        public String result() { return result; }
    }
    
    /**
     * Represents a case in a match expression with a multi-line body.
     * 
     * <p>Use this when the case body spans multiple lines.
     */
    public static final class MatchCaseWithBody {
        private final String pattern;
        private final Runnable body;
        
        /**
         * Creates a new MatchCaseWithBody.
         *
         * @param pattern The pattern to match
         * @param body A runnable that writes the case body
         */
        public MatchCaseWithBody(String pattern, Runnable body) {
            this.pattern = pattern;
            this.body = body;
        }
        
        /** Returns the pattern. */
        public String pattern() { return pattern; }
        
        /** Returns the body runnable. */
        public Runnable body() { return body; }
    }
    
    /**
     * Represents a mapping between an enum variant and its text representation.
     */
    public static final class EnumMapping {
        private final String variantName;
        private final String textValue;
        
        /**
         * Creates a new EnumMapping.
         *
         * @param variantName The variant name (PascalCase, without type prefix)
         * @param textValue The text representation (e.g., "us-east-1")
         */
        public EnumMapping(String variantName, String textValue) {
            this.variantName = variantName;
            this.textValue = textValue;
        }
        
        /** Returns the variant name. */
        public String variantName() { return variantName; }
        
        /** Returns the text value. */
        public String textValue() { return textValue; }
    }
}
