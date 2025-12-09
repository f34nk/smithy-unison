package io.smithy.unison.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing and working with URI templates.
 * 
 * <p>URI templates contain placeholders like {BucketName} that need to be
 * substituted with values from input parameters.
 */
public final class UriTemplate {
    
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    
    private final String template;
    private final List<String> placeholders;
    
    /**
     * Creates a new URI template.
     *
     * @param template The URI template string (e.g., "/{Bucket}/{Key+}")
     */
    public UriTemplate(String template) {
        this.template = template;
        this.placeholders = extractPlaceholders(template);
    }
    
    /**
     * Gets the original template string.
     */
    public String getTemplate() {
        return template;
    }
    
    /**
     * Gets the list of placeholder names in order of appearance.
     */
    public List<String> getPlaceholders() {
        return new ArrayList<>(placeholders);
    }
    
    /**
     * Checks if the template has any placeholders.
     */
    public boolean hasPlaceholders() {
        return !placeholders.isEmpty();
    }
    
    /**
     * Extracts placeholder names from a template string.
     */
    private static List<String> extractPlaceholders(String template) {
        List<String> result = new ArrayList<>();
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            // Remove greedy modifier (+) if present
            if (placeholder.endsWith("+")) {
                placeholder = placeholder.substring(0, placeholder.length() - 1);
            }
            result.add(placeholder);
        }
        
        return result;
    }
    
    /**
     * Checks if a placeholder is greedy (has a + suffix in the template).
     * 
     * <p>Greedy placeholders match multiple path segments and should not
     * have slashes URL-encoded.
     */
    public boolean isGreedy(String placeholder) {
        return template.contains("{" + placeholder + "+}");
    }
    
    @Override
    public String toString() {
        return template;
    }
}
