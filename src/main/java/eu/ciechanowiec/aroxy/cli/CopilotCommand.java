package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.client.CopilotCli;
import eu.ciechanowiec.aroxy.daemon.GatewayAddress;
import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.Launcher;
import eu.ciechanowiec.aroxy.daemon.RuntimeSnapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Runs the real GitHub Copilot CLI with proxy and CA trust scoped to that child process. The shell
 * installer delegates the ordinary {@code copilot} function here, so the safe path requires no flags
 * and makes no persistent change to either the shell environment or the operating-system trust store.
 */
@Command(
    name = "copilot",
    description = "Run Copilot through aroxy."
)
final class CopilotCommand implements Callable<Integer> {

    private static final int FAILED = 1;
    private static final int NOT_FOUND = 127;
    private static final String EXTRA_CERTIFICATES = "NODE_EXTRA_CA_CERTS";
    private static final String AUTHORITY_FILE = "copilot-ca.pem";
    private static final List<String> PROXY_VARIABLES = List.of(
        "HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy"
    );
    private static final List<String> BYPASS_VARIABLES = List.of("NO_PROXY", "no_proxy");

    @Parameters(arity = "0..*", paramLabel = "COPILOT_ARGUMENT")
    private final List<String> arguments;
    private final CliOutput out;

    CopilotCommand() {
        this.arguments = List.of();
        this.out = CliOutput.standard();
    }

    @Override
    public Integer call() throws InterruptedException {
        Home home = Home.resolve();
        Launcher.Outcome outcome = new Launcher(home).start();
        if (outcome == Launcher.Outcome.FAILED) {
            this.out.error("Could not start the proxy for the GitHub Copilot command-line tool");
            return FAILED;
        }
        String listen = copilotAddress(home).orElseThrow(
            () -> new IllegalStateException("The running aroxy daemon has no Copilot listener")
        );
        return this.run(home, listen);
    }

    private int run(Home home, String listen) throws InterruptedException {
        Path bundle = trustBundle(home);
        try {
            ProcessBuilder builder = new ProcessBuilder(command());
            builder.inheritIO();
            configure(builder.environment(), listen, bundle);
            return builder.start().waitFor();
        } catch (IOException exception) {
            this.out.error("The GitHub Copilot command-line tool is not executable: " + exception.getMessage());
            return NOT_FOUND;
        } finally {
            delete(bundle);
        }
    }

    private List<String> command() {
        List<String> command = new ArrayList<>();
        command.add("copilot");
        command.addAll(this.arguments);
        return command;
    }

    private static Optional<String> copilotAddress(Home home) {
        String displayName = CopilotCli.profile().displayName();
        return RuntimeSnapshot.read(home.runtimeFile()).stream()
            .flatMap(snapshot -> snapshot.gateways().stream())
            .filter(gateway -> gateway.client().equals(displayName))
            .map(GatewayAddress::listen)
            .findFirst();
    }

    private static void configure(Map<String, String> environment, String listen, Path bundle) {
        String proxy = "http://" + listen;
        PROXY_VARIABLES.forEach(variable -> environment.put(variable, proxy));
        BYPASS_VARIABLES.forEach(environment::remove);
        environment.put(EXTRA_CERTIFICATES, bundle.toString());
    }

    private static Path trustBundle(Home home) {
        try {
            Path bundle = Files.createTempFile(home.base(), "copilot-trust-", ".pem");
            String inherited = inheritedCertificates();
            String authority = Files.readString(home.base().resolve(AUTHORITY_FILE));
            Files.writeString(bundle, inherited + authority);
            return bundle;
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not prepare Copilot process trust", exception);
        }
    }

    private static String inheritedCertificates() {
        return Optional.ofNullable(System.getenv(EXTRA_CERTIFICATES))
            .filter(value -> !value.isBlank())
            .map(Path::of)
            .filter(Files::isRegularFile)
            .map(CopilotCommand::read)
            .map(text -> text.endsWith("\n") ? text : text + "\n")
            .orElse("");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read existing Node certificate bundle " + path, exception);
        }
    }

    private static void delete(Path bundle) {
        try {
            Files.deleteIfExists(bundle);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not remove temporary Copilot trust bundle", exception);
        }
    }
}
