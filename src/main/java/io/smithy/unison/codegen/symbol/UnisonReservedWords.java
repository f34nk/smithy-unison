package io.smithy.unison.codegen.symbol;

import java.util.Set;

/**
 * Unison reserved words and keywords.
 * 
 * <p>Unison has relatively few reserved words compared to other languages.
 * This class provides a list of words that may need special handling
 * when generating Unison code.
 */
public final class UnisonReservedWords {
    
    /**
     * Unison keywords that cannot be used as identifiers.
     */
    public static final Set<String> KEYWORDS = Set.of(
        // Type and term keywords
        "type",
        "ability",
        "structural",
        "unique",
        "namespace",
        
        // Control flow
        "if",
        "then",
        "else",
        "match",
        "with",
        "cases",
        "let",
        "in",
        "where",
        "do",
        "handle",
        "handler",
        
        // Boolean literals
        "true",
        "false",
        
        // Module/namespace
        "use",
        "forall",
        
        // Special
        "termLink",
        "typeLink"
    );
    
    /**
     * Checks if a name is a Unison reserved word.
     *
     * @param name The name to check
     * @return true if the name is reserved
     */
    public static boolean isReserved(String name) {
        return KEYWORDS.contains(name);
    }
    
    /**
     * Escapes a name if it's a reserved word.
     * 
     * <p>In Unison, you can use backticks to escape reserved words:
     * {@code `type`} allows using the reserved word "type" as an identifier.
     *
     * @param name The name to potentially escape
     * @return The escaped name (wrapped in backticks if reserved)
     */
    public static String escape(String name) {
        if (isReserved(name)) {
            return "`" + name + "`";
        }
        return name;
    }
    
    /**
     * Unescapes a name if it's wrapped in backticks.
     * 
     * @param name The name to unescape
     * @return The unescaped name
     */
    public static String unescape(String name) {
        if (name != null && name.length() >= 2 && name.startsWith("`") && name.endsWith("`")) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }
    
    /**
     * Appends a suffix to a name, handling escaped names correctly.
     * 
     * <p>If the name is escaped (e.g., {@code `type`}), this unescapes it,
     * appends the suffix, and re-escapes if needed.
     * 
     * @param name The base name (may be escaped)
     * @param suffix The suffix to append
     * @return The combined name, escaped if necessary
     */
    public static String appendSuffix(String name, String suffix) {
        String unescaped = unescape(name);
        String combined = unescaped + suffix;
        return escape(combined);
    }
    
    /**
     * Gets the set of all reserved words.
     */
    public static Set<String> getReservedWords() {
        return KEYWORDS;
    }
    
    private UnisonReservedWords() {
        // Utility class
    }
}
