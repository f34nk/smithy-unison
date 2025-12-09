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
     * <p>In Unison, you can use backticks to escape reserved words,
     * but it's generally better to use a different name.
     *
     * @param name The name to potentially escape
     * @return The escaped name (appends underscore if reserved)
     */
    public static String escape(String name) {
        if (isReserved(name)) {
            return name + "_";
        }
        return name;
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
