package io.smithy.unison.codegen;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolWriter;

import java.util.function.BiFunction;

/**
 * Unison code writer with Symbol support.
 * 
 * <p>This class extends {@link SymbolWriter} to provide Unison-specific
 * code generation utilities.
 * 
 * <h2>Custom Formatters</h2>
 * <ul>
 *   <li>{@code $T} - Formats a Symbol's Unison type</li>
 *   <li>{@code $N} - Formats a Symbol's name</li>
 * </ul>
 * 
 * <p><b>NOT FULLY IMPLEMENTED</b>: This is a first draft with stub implementations.
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
     */
    public String getNamespace() {
        return namespace;
    }
    
    // ========== Basic Writing Methods ==========
    
    /**
     * Writes a blank line.
     */
    public UnisonWriter writeBlankLine() {
        int currentIndent = getIndentLevel();
        dedent(currentIndent);
        write("");
        indent(currentIndent);
        return this;
    }
    
    /**
     * Writes a Unison comment.
     * 
     * <p>Generates: {@code -- comment text}
     * 
     * <p><b>NOT FULLY IMPLEMENTED</b>: Stub for first draft.
     */
    public UnisonWriter writeComment(String comment) {
        write("-- $L", comment);
        return this;
    }
    
    /**
     * Writes a doc comment (Unison docstrings).
     * 
     * <p>Generates:
     * <pre>
     * {{
     * Documentation text
     * }}
     * </pre>
     * 
     * <p><b>NOT FULLY IMPLEMENTED</b>: Stub for first draft.
     */
    public UnisonWriter writeDocComment(String doc) {
        // TODO: Implement proper doc comment formatting
        write("{{");
        write("$L", doc);
        write("}}");
        return this;
    }
    
    // ========== Type Definition Methods ==========
    
    /**
     * Writes a Unison record type definition.
     * 
     * <p>Generates:
     * <pre>
     * type MyType = {
     *   field1 : Text,
     *   field2 : Nat
     * }
     * </pre>
     * 
     * <p><b>NOT FULLY IMPLEMENTED</b>: Stub for first draft.
     */
    public UnisonWriter writeRecordType(String typeName, java.util.List<TypeField> fields) {
        // TODO: Implement Unison record type generation
        write("type $L = {", typeName);
        indent();
        for (int i = 0; i < fields.size(); i++) {
            TypeField field = fields.get(i);
            String comma = (i < fields.size() - 1) ? "," : "";
            write("$L : $L$L", field.name(), field.type(), comma);
        }
        dedent();
        write("}");
        writeBlankLine();
        return this;
    }
    
    /**
     * Writes a Unison union type (sum type).
     * 
     * <p>Generates:
     * <pre>
     * type S3Response a
     *   = S3Success a
     *   | S3Error { code : Text, message : Text }
     * </pre>
     * 
     * <p><b>NOT FULLY IMPLEMENTED</b>: Stub for first draft.
     */
    public UnisonWriter writeUnionType(String typeName, String typeParams, java.util.List<Variant> variants) {
        // TODO: Implement Unison union type generation
        if (typeParams != null && !typeParams.isEmpty()) {
            write("type $L $L", typeName, typeParams);
        } else {
            write("type $L", typeName);
        }
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
    
    // ========== Function Definition Methods ==========
    
    /**
     * Writes a function signature.
     * 
     * <p>Generates:
     * <pre>
     * functionName : ParamType -> '{IO, Exception} ReturnType
     * </pre>
     * 
     * <p><b>NOT FULLY IMPLEMENTED</b>: Stub for first draft.
     */
    public UnisonWriter writeSignature(String name, String signature) {
        // TODO: Implement Unison function signature generation
        write("$L : $L", name, signature);
        return this;
    }
    
    /**
     * Writes a function definition.
     * 
     * <p>Generates:
     * <pre>
     * functionName param1 param2 =
     *   body
     * </pre>
     * 
     * <p><b>NOT FULLY IMPLEMENTED</b>: Stub for first draft.
     */
    public UnisonWriter writeFunction(String name, String params, Runnable body) {
        // TODO: Implement Unison function definition generation
        write("$L $L =", name, params);
        indent();
        body.run();
        dedent();
        writeBlankLine();
        return this;
    }
    
    /**
     * Writes a let binding block.
     * 
     * <p><b>NOT FULLY IMPLEMENTED</b>: Stub for first draft.
     */
    public UnisonWriter writeLet(Runnable bindings, Runnable result) {
        // TODO: Implement Unison let block generation
        write("let");
        indent();
        bindings.run();
        dedent();
        result.run();
        return this;
    }
    
    /**
     * Writes a match expression.
     * 
     * <p>Generates:
     * <pre>
     * match expr with
     *   Pattern1 -> result1
     *   Pattern2 -> result2
     * </pre>
     * 
     * <p><b>NOT FULLY IMPLEMENTED</b>: Stub for first draft.
     */
    public UnisonWriter writeMatch(String expr, java.util.List<MatchCase> cases) {
        // TODO: Implement Unison match expression generation
        write("match $L with", expr);
        indent();
        for (MatchCase c : cases) {
            write("$L -> $L", c.pattern(), c.result());
        }
        dedent();
        return this;
    }
    
    /**
     * Writes an ability handler.
     * 
     * <p>Generates: {@code handle computation with handler}
     * 
     * <p><b>NOT FULLY IMPLEMENTED</b>: Stub for first draft.
     */
    public UnisonWriter writeHandle(String computation, String handler) {
        // TODO: Implement Unison ability handler generation
        write("handle $L with $L", computation, handler);
        return this;
    }
    
    // ========== HTTP/AWS Specific Methods ==========
    
    /**
     * Writes an HTTP request function body.
     * 
     * <p><b>NOT FULLY IMPLEMENTED</b>: Stub for first draft.
     */
    public UnisonWriter writeHttpRequest(String method, String url, String headers, String body) {
        // TODO: Implement Unison HTTP request generation
        write("-- TODO: HTTP request generation not implemented");
        write("request = Http.Request.$L $L $L", method.toLowerCase(), url, headers);
        return this;
    }
    
    /**
     * Writes response status code matching.
     * 
     * <p><b>NOT FULLY IMPLEMENTED</b>: Stub for first draft.
     */
    public UnisonWriter writeStatusMatch(Runnable successCase, Runnable errorCase) {
        // TODO: Implement Unison HTTP status match generation
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
     * Adds a use statement.
     */
    public UnisonWriter addUse(String namespace) {
        getImportContainer().addUse(namespace);
        return this;
    }
    
    // ========== Custom Formatters ==========
    
    /**
     * Formatter for Unison type names from Symbols.
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
    
    // ========== Factory Method ==========
    
    /**
     * Creates a factory for UnisonWriter instances.
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
            // Extract namespace from filename
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
     */
    public static final class TypeField {
        private final String name;
        private final String type;
        
        public TypeField(String name, String type) {
            this.name = name;
            this.type = type;
        }
        
        public String name() { return name; }
        public String type() { return type; }
    }
    
    /**
     * Represents a variant in a union type.
     */
    public static final class Variant {
        private final String name;
        private final String payload;
        
        public Variant(String name, String payload) {
            this.name = name;
            this.payload = payload;
        }
        
        public String name() { return name; }
        public String payload() { return payload; }
    }
    
    /**
     * Represents a case in a match expression.
     */
    public static final class MatchCase {
        private final String pattern;
        private final String result;
        
        public MatchCase(String pattern, String result) {
            this.pattern = pattern;
            this.result = result;
        }
        
        public String pattern() { return pattern; }
        public String result() { return result; }
    }
}
