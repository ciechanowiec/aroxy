package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.config.ConfigFile;
import eu.ciechanowiec.aroxy.config.EffectiveConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigSummaryTest {

    @Test
    void effectiveConfigurationRendersItsSettingsAndRegisteredListeners() {
        EffectiveConfig config = EffectiveConfig.merge(
            List.of(ConfigFile.parse("test", "[gateway]\n"))
        );
        String summary = new ConfigSummary(config).render();
        assertTrue(summary.contains("Secret masking"), summary);
        assertTrue(summary.contains("Claude Code"), summary);
        assertTrue(summary.contains("Codex CLI"), summary);
        assertTrue(summary.contains("Cursor CLI"), summary);
    }
}
