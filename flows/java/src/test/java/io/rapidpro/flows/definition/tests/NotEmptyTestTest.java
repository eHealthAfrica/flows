package io.rapidpro.flows.definition.tests;

import org.junit.Test;

/**
 * Test for {@link NotEmptyTest}
 */
public class NotEmptyTestTest extends BaseTestTest {
    @Test
    public void evaluate() {
        NotEmptyTest test = new NotEmptyTest();
        assertTest(test, " ok  ", true, "ok");
        assertTest(test, "  ", false, null);
        assertTest(test, "", false, null);
    }
}
