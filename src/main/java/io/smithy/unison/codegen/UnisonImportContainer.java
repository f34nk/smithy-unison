package io.smithy.unison.codegen;

import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages Unison imports (use statements).
 * 
 * <p>In Unison, imports are handled via the codebase manager (UCM) and
 * the `use` statement. This container tracks what namespaces need to be
 * imported in the generated code.
 * 
 * <p><b>NOT FULLY IMPLEMENTED</b>: Stub for first draft.
 */
public final class UnisonImportContainer implements ImportContainer {
    
    private final Set<String> useStatements = new HashSet<>();
    
    /**
     * Adds a use statement for a symbol.
     */
    @Override
    public void importSymbol(Symbol symbol, String alias) {
        // TODO: Implement Unison use statement generation
        // Unison uses: use namespace.path
        String namespace = symbol.getNamespace();
        if (namespace != null && !namespace.isEmpty()) {
            useStatements.add(namespace);
        }
    }
    
    /**
     * Adds a use statement for a namespace.
     */
    public void addUse(String namespace) {
        if (namespace != null && !namespace.isEmpty()) {
            useStatements.add(namespace);
        }
    }
    
    /**
     * Checks if there are any use statements.
     */
    public boolean hasUseStatements() {
        return !useStatements.isEmpty();
    }
    
    /**
     * Gets all use statements.
     */
    public Set<String> getUseStatements() {
        return new HashSet<>(useStatements);
    }
    
    /**
     * Renders the use statements as Unison code.
     */
    @Override
    public String toString() {
        if (useStatements.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (String namespace : useStatements) {
            sb.append("use ").append(namespace).append("\n");
        }
        return sb.toString();
    }
}
