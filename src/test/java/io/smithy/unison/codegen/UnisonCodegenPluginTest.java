package io.smithy.unison.codegen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for UnisonCodegenPlugin.
 */
public class UnisonCodegenPluginTest {
    
    @Test
    public void testPluginName() {
        UnisonCodegenPlugin plugin = new UnisonCodegenPlugin();
        assertEquals("unison-codegen", plugin.getName());
    }
    
    @Test
    public void testPluginInstantiation() {
        UnisonCodegenPlugin plugin = new UnisonCodegenPlugin();
        assertNotNull(plugin);
    }
}
