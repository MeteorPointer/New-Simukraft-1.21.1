package client.cn.nsimu.simukraft.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimuKraftNameSearchTest {
    @Test
    void matches_emptyQueryAcceptsAnyName() {
        assertTrue(SimuKraftNameSearch.matches("Harbor", ""));
        assertTrue(SimuKraftNameSearch.matches("Harbor", "  "));
    }

    @Test
    void matches_isCaseInsensitiveContains() {
        assertTrue(SimuKraftNameSearch.matches("Harbor", "har"));
        assertTrue(SimuKraftNameSearch.matches("Harbor", "BOR"));
        assertFalse(SimuKraftNameSearch.matches("Harbor", "city"));
        assertFalse(SimuKraftNameSearch.matches("", "a"));
        assertFalse(SimuKraftNameSearch.matches(null, "a"));
    }
}
