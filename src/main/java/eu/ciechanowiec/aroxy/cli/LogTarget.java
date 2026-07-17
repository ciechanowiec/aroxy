package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.Home;
import java.nio.file.Path;

/**
 * Which diagnostic file the {@code logs} command reads: the audit trail (the default) or the daemon's
 * app log. Each constant resolves its file from the {@link Home} layout, keeping the command free of
 * branching on flags. The wire dumps and the {@code --all} view are handled separately because they
 * are not single appendable files.
 */
enum LogTarget {

    AUDIT {

        @Override
        Path file(Home home) {
            return home.auditLog();
        }
    },
    APP {

        @Override
        Path file(Home home) {
            return home.appLog();
        }
    };

    abstract Path file(Home home);
}
