package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ActiveRulesTest {

    private static final int SEVERAL = 11;

    @Test
    void countsOneRuleInTheSingular() {
        assertEquals("1 rule in effect", ActiveRules.rules(1));
    }

    @Test
    void countsSeveralRulesInThePlural() {
        assertEquals(SEVERAL + " rules in effect", ActiveRules.rules(SEVERAL));
    }

    @Test
    void countsNoRulesInThePlural() {
        assertEquals("0 rules in effect", ActiveRules.rules(0));
    }
}
