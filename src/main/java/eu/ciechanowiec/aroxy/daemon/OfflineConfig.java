package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.config.AttachmentRegistry;
import eu.ciechanowiec.aroxy.config.EffectiveConfig;
import java.nio.file.Path;
import java.util.List;

/**
 * Computes the effective configuration straight from the on-disk layout (default file plus persistent
 * registry plus the supplied session temporaries) so the CLI can render {@code aroxy config} and list
 * attachments without a control socket. The CLI passes the temporaries it reads from the {@link
 * ControlFiles} temporaries file (empty while the daemon is stopped).
 */
public final class OfflineConfig {

    private final Home home;
    private final AttachmentRegistry registry;

    /**
     * Creates an offline config view over the given state directory.
     *
     * @param home the state directory layout the configuration is computed from
     */
    public OfflineConfig(Home home) {
        this.home = home;
        this.registry = new AttachmentRegistry(home.attachments());
    }

    /**
     * Renders {@code aroxy config} from the on-disk layout plus the supplied session temporaries.
     *
     * @param temporaries the session temporary attachment paths to include
     * @return the rendered configuration summary
     */
    public String render(Iterable<Path> temporaries) {
        return new ConfigSummary(this.effective(temporaries)).render();
    }

    /**
     * The persistent attachment paths, as strings.
     *
     * @return the persistent attachment paths
     */
    public List<String> attachments() {
        return this.registry.list().stream().map(Path::toString).toList();
    }

    private EffectiveConfig effective(Iterable<Path> temporaries) {
        return new ConfigAssembly(this.home, this.registry).build(temporaries);
    }
}
