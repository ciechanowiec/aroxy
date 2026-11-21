# Aroxy architecture

This document explains how aroxy is built and why. The binding rules for changing it live in [AGENTS.md](../AGENTS.md), which also names the engine behaviors that must not regress.

## Daemon and control channel

```text
aroxy CLI (picocli) ──state files + signal──> aroxy daemon (background JVM)
  start/stop/restart/status/config/reload/      ├─ listener 127.0.0.1:8788 ── Claude Code
  logs/config attach|detach|list                │     obfuscate ⇄ deobfuscate ── api.anthropic.com
                                                ├─ listener 127.0.0.1:8789 ── Codex CLI
                                                │     obfuscate ⇄ deobfuscate ── chatgpt.com/backend-api/codex
                                                ├─ listener 127.0.0.1:8790 ── Cursor CLI (HTTP/1.1 + HTTP/2)
                                                │     Run / RunSSE / BidiAppend ⇄ api2.cursor.sh
                                                ├─ listener 127.0.0.1:8791 ── GitHub Copilot CLI
                                                │     CONNECT + process-local CA ⇄ GitHub-selected Copilot API
                                                └─ reload watcher (polls control files)
                                        one Obfuscator + one Vault + one AuditLog, shared by all
```

The CLI drives the daemon with no control socket: `status`/`config`/`list` read the daemon's published `runtime` snapshot (and `status` also reads the `stats` traffic counters), `reload`/`attach` touch a `reload` trigger the daemon polls, and `stop` sends a process signal that the daemon's shutdown hook turns into a clean teardown. This removes the network-reachable control plane entirely.

State dir `~/.aroxy/`: `aroxy.pid`, `runtime` (the daemon's published snapshot), `stats` (traffic counters since start, republished at most once a second, apart from `runtime` because its `generation` is the reload handshake and must not move with every request), `reload` (the reload trigger), `temporaries` (this session's temporary attachments, cleared on start), `default.conf` (materialized, user-editable), `attachments` (persistent registry), `vault.tsv`, the Copilot CA files, `.daemon.jar` (the snapshot a running daemon executes, staged at launch so replacing the installed jar cannot disturb a live process), `logs/{aroxy.log,audit.log,wire/}`.

## Agents on one daemon

One shared daemon serves every agent session on the machine: Claude Code points `ANTHROPIC_BASE_URL` at `127.0.0.1:8788`, Codex points the built-in provider's `openai_base_url` in its user configuration file at `127.0.0.1:8789`, and Cursor CLI points `--endpoint` or `CURSOR_API_ENDPOINT` at `http://localhost:8790`. Cursor's listener accepts its native cleartext HTTP/2 stream and its HTTP/1.1 compatibility transport. They share one proxy, one vault, and one audit log, so a secret carries the same token whichever agent sends it. The installed `copilot` wrapper delegates to `aroxy copilot`.

Shell completion is set up by `install.sh` (a managed block in the user's rc). The `generate-completion` subcommand it calls is hidden from `--help`. `uninstall.sh` reverses install.

Cursor's agent stream was observed negotiating `application/connect+proto` over cleartext HTTP/2, not the JSON dialect, and chunking its visible text at roughly eleven characters - shorter than a token. That is why `CursorTransport` carries protobuf field-number paths for the streamed values and why the response path holds a partial token across frames: without it every token in a reply reaches the terminal unreversed. The JSON dialect is still accepted and still reassembles, but it is the path Cursor does not take.

Copilot uses CONNECT interception because GitHub selects among several API hosts. `aroxy copilot` trusts aroxy's CA only in its child process, while unrelated hosts remain opaque tunnels.

## Adding a client

**Adding or removing a client is additive as far as the registry goes, and only that far.** `client/ClientRegistry` is the only place that enumerates agents, and everything else iterates it (listeners bound, `[gateway]` listen keys accepted and validated, `aroxy status`/`config` lines rendered, `runtime` records published). `ClientId` is an open, validated slug value rather than an enum, so nothing has to be taught that another agent exists.

How far that reaches depends on how the new agent talks, and there are two cases rather than one. An agent whose conversation is one of the shapes already carried is genuinely one `ClientProfile` (an id and display name, a `Gateway`, a `PathScope`, and a `ClientTransport`) plus one line in the registry: Codex was added that way, being a `BufferedTransport` like Claude Code with a different `SseDialect`. An agent needing a conversation shape aroxy does not yet have is a larger change, and Cursor is the worked example: `ClientTransport` is **sealed**, so a third variant means permitting it there, adding an arm to the exhaustive `switch` in `ProxyServer`, and writing the listener that arm returns. The seal is what makes that visible. It is why the compiler names every place that must change rather than letting a new transport reach a listener that silently cannot serve it, and it is the reason the cost lands at compile time instead of at the first request.

## Packages

Packages under `eu.ciechanowiec.aroxy`:

- `client` - the extension point: `ClientId`, `ClientProfile`, `ClientRegistry`, `Gateway`, `PathScope`, and the transport as *data* (buffered HTTP with an `SseDialect`, Cursor protobuf, or Copilot CONNECT interception), with one profile class per agent. No vendor vocabulary lives outside the corresponding profile or transport implementation.
- `obfuscation` - the engine: `Obfuscator`, `Vault`, `Rule` (composes `RulePattern` + `RuleLabel`), `RuleApplication`, `PiiToken`, `MaskedSecret`, `RuleCompiler` (loose/leet), `StreamDeobfuscator` (SSE carry-buffer over text fragments), `ObfuscationResult`/`ObfuscationMatch`.
- `config` - `ConfigFile` parser, `EffectiveConfig` merge, `AttachmentRegistry`, `Settings` (a `Gateway` per registered client, keyed by `ClientId`).
- `proxy` - `ProxyServer` (one per client) picking a `ProxyListener` off the sealed transport, request handling, `ZstdBody` request inflation, `WebSocketUpgrade` refusal, `SseTransformer` (interprets a dialect, knowing no vendor's event names), Cursor's protobuf (`ProtobufWire`, `ProtobufTransformer`, `ProtobufLeaf`) and Connect exchange (`ConnectJsonFrame`, `CursorConnectStream`, `CursorDeltaStream`, `CursorResponseStream`), `AuditLog`, wire dumps.
- `daemon` - background launcher, PID file, the file/signal control channel (`ControlFiles`, `RuntimeSnapshot`/`SnapshotPublisher`, `ReloadWatcher`), `GatewayAddress`/`GatewayListing`, `Home` path layout.
- `cli` - picocli root `Aroxy`, its subcommands, and shell autocompletion (`generate-completion`).

## Two HTTP stacks

**Two HTTP stacks, on purpose.** `JdkProxyListener` (the JDK `HttpServer`) serves the buffered clients and `Upstream` (the JDK `HttpClient`) forwards them. Cursor needs more than either can do: its native `Run` is cleartext HTTP/2, and it keeps the request open *while* the response streams, which the JDK client cannot express (it withholds response headers until the request publisher completes). So Cursor alone runs on **Jetty** - `CursorProxyListener` (HTTP/1.1 + h2c on one port) and `CursorDuplexUpstream` (async request content + streaming response listener). Cursor's *buffered* routes still use `Upstream`, so the plain case stays on the plain client.

Carrying two HTTP clients means their reach is made to agree deliberately, because their defaults do not. Trust already agrees (Jetty with no key store of its own builds from the platform default trust managers). The proxy does not: Jetty starts with an empty proxy configuration and would go direct where the JDK client honours `https.proxyHost`. `JdkProxySelection` reads the same `ProxySelector` and hands Jetty the answer, so a machine behind an egress proxy reaches the fixed upstreams consistently.

## Opaque encoded fields

An encoded field, such as base64 image data, a thinking signature, or reasoning ciphertext, is not text anyone wrote, so no rule can find a secret in one. A substitution inside one, however, destroys a value the vendor decodes, validates, or dereferences before it reads anything else.

This is not a rare case but what every screenshot costs: a base64 run is long enough that a short configured value occurs in it by chance, and the giveaway in the audit log is a match in a mixed case no author would type.

Opacity is keyed by the field name under its parent rather than by the bare name, because a bare name such as `data` is also a tool parameter whose content the rules must still reach. Where the protocol owns a name outright, the bare name is enough.

## Configuration model

Effective config = **default.conf** (always, bundled → materialized, user may empty) → **persistent** attachments (machine/company-wide, every start) → **temporary** attachments (this session only). Settings: last file wins per key. Rules: keyed by `id`, a later file replaces an `id` in place, new ids append, and order breaks equal-span match ties. Attach is allowed while running. **Detach only while stopped**.

**A declared key must be usable.** A key states an intent, so a file that declares one its stanza does not define, or one whose value names nothing the proxy can act on, is broken and is not loaded. An absent key states no intent and keeps the documented default. This is why `listen_claude = 87788`, an unknown `[gateway]` key such as `upstream`, `mask_secrets = yes`, and `type = literl` all break the file rather than falling back: a configuration that reads as applied and is not would leave the user trusting a proxy that never took the instruction. The `[gateway]` listen keys are *derived* from the client registry rather than listed in the parser, so a newly registered agent gets its key and that key's validation for free. Two agents pointed at one port is likewise rejected, at merge time rather than at bind time, because the clash is a property of the merge (one file may set only one of the keys). Tests reach ephemeral ports through `DaemonSeam`, not config.

**Placeholders are a closed vocabulary.** The same rule, applied to a rule's `placeholder`: it must be one of the names shipped in `default.conf` (enforced by `config/Placeholders`). The set is closed because free-form, code-like names (e.g. `COMPANY_D`) are reformatted by the model in its reply and can no longer be deobfuscated, whereas the curated names survive the round trip. A test pins the set to `default.conf` and to the README list.

## Release

The manual `.github/workflows/release.yaml` workflow publishes signed main, source, Javadoc, and POM artifacts to Maven Central after Extended verification. It then creates an immutable `v<version>` tag, smoke-tests the standalone installer against Maven Central, and creates a metadata-only GitHub Release. The `release` Maven profile rejects SNAPSHOT project, parent, and dependency coordinates.

**`install.sh` pins no version.** From a checkout it uses or builds `target/aroxy.jar`, and standalone it reads `maven-metadata.xml` for the artifact, takes the `<release>` element, and installs that version. It is served from `main`, where the declared version is a SNAPSHOT between releases, so a pin there would name a version Maven Central does not serve and the documented `curl | bash` install would break the moment a release lands. A metadata answer that is missing, malformed, or a SNAPSHOT aborts the install rather than becoming a download URL. The release workflow enforces the invariant from both ends: it fails if `install.sh` reintroduces a `RELEASE_VERSION` pin, waits until `maven-metadata.xml` names the new version (not only until the artifacts return HTTP 200, because that document syncs separately), and asserts the smoke-tested install reports exactly the released version.
