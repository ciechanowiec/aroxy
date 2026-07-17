package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.config.AttachmentRegistry;
import eu.ciechanowiec.aroxy.config.ConfigFile;
import eu.ciechanowiec.aroxy.config.DefaultConfig;
import eu.ciechanowiec.aroxy.config.EffectiveConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the layered {@link EffectiveConfig} for a running daemon: the materialized
 * {@code default.conf} first, then every path in the persistent {@link AttachmentRegistry}, then the
 * in-session temporary files. The temporary list is held by the caller (the {@link Daemon}) so it can
 * grow at runtime and is intentionally lost on restart. The assembled config is finished by the
 * {@link DaemonSeam}, which overrides nothing in production.
 */
final class ConfigAssembly {

    private final Home home;
    private final AttachmentRegistry registry;
    private final DaemonSeam seam;

    ConfigAssembly(Home home, AttachmentRegistry registry) {
        this(home, registry, DaemonSeam.production());
    }

    ConfigAssembly(Home home, AttachmentRegistry registry, DaemonSeam seam) {
        this.home = home;
        this.registry = registry;
        this.seam = seam;
    }

    EffectiveConfig build(Iterable<Path> temporaries) {
        new DefaultConfig(this.home.defaultConfig()).materialize();
        List<ConfigFile> files = new ArrayList<>();
        files.add(ConfigFile.read(this.home.defaultConfig()));
        this.registry.list().forEach(path -> files.add(ConfigFile.read(path)));
        temporaries.forEach(path -> files.add(ConfigFile.read(path)));
        return this.seam.applyTo(EffectiveConfig.merge(files));
    }
}
