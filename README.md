# SimpleLoginMessages

SimpleLoginMessages customizes join and quit messages on Paper and records login
statistics. Message templates can vary by a player's LuckPerms primary group.
It also supports an additional private message on the first login recorded by
this plugin, and statistics for online players and previously recorded players.

Messages use MiniMessage formatting. LuckPerms supplies the primary group, prefix,
and suffix; command access uses Bukkit permission checks, which LuckPerms can manage.

## Requirements and compatibility

| Component | Target / status |
| --- | --- |
| Minecraft edition | Java Edition |
| Server | Paper for Minecraft 26.2; successful operation reported by the maintainer |
| Newer Paper versions | Compatibility has not been verified |
| Older Paper versions | Not supported targets |
| Java runtime | Java 25, as specified by the original project documentation |
| Compile-time Java target | Java 21 (`maven.compiler.release` in `pom.xml`) |
| Compile-time Paper API | `26.2.build.129-stable`, pinned in [pom.xml](pom.xml) |
| LuckPerms API | `5.4`; provided by a separately installed LuckPerms plugin |
| Other platforms | Bukkit, Spigot, Folia, and other server platforms are not supported targets |

Install LuckPerms for the intended setup. It is declared as a soft dependency,
but startup without its API has not been verified: the plugin directly references
LuckPerms classes. Fallbacks for an unavailable service do not establish that it
can run without the LuckPerms plugin installed.

The build target alone does not establish runtime compatibility. The exact Paper
build, Java runtime, and LuckPerms version used by the maintainer have not been
recorded. Repository build checks do not include live server tests.

## Installation and updates

1. Prepare Paper 26.2 with Java 25 and install LuckPerms separately.
2. Stop the server and place the built `SimpleLoginMessages.jar` in `plugins/`.
3. Start the server. If absent, `plugins/SimpleLoginMessages/config.yml` is created
   from the commented configuration bundled in the JAR.
4. Customize the messages and grant the permissions below. Run `/slm reload` to
   read the configuration again.

For updates, stop the server, back up `plugins/SimpleLoginMessages/`, replace the
old plugin JAR, and restart. Keep only one SimpleLoginMessages JAR installed.
Existing configuration files are not overwritten or automatically rewritten.
Compare them with the bundled [config.yml](src/main/resources/config.yml) when
adopting new settings. `/slm reload` does not reload Java code or a replacement JAR.

## Commands and permissions

| Command | Permission | Effect |
| --- | --- | --- |
| `/slm` or `/slm help` | None | Show help; restricted commands appear according to permissions |
| `/slm stats` | `slm.stats` | Show your own player statistics and server counters; players only |
| `/slm stats <player>` | `slm.stats.others` | Show online or stored player statistics and server counters |
| `/slm reload` | `slm.reload` | Reload both `config.yml` and `data.yml` from disk |

All three permissions default to **false**, including for operators. `slm.*`
also defaults to false and grants all three when explicitly assigned. There is
no `slm.use` permission. Join/quit messages and login recording do not require
these command permissions. Console users must specify a player for statistics.

Tab completion offers commands according to permissions and known player names
from stored data and the online list. The `stats` subcommand suggestion requires
`slm.stats`; player-name suggestions require `slm.stats.others`.

### LuckPerms example

Run these in the server console, adapting the group and player names:

```text
lp group default permission set slm.stats true
lp group moderator permission set slm.stats.others true
lp user YourAdminName permission set slm.reload true
```

The example assumes those groups already exist. Grant `slm.*` only where all
three permissions are intended. Check access with a normal player account.

Message selection uses LuckPerms' **primary group**, not every inherited group
or the group with a matching permission. The name is converted to lowercase
before looking up `messages.groups.<group>`. Creating a YAML section does not
create or assign a LuckPerms group. Prefix and suffix placeholders use LuckPerms'
cached player metadata. Without an available service, the code falls back to
stored metadata, then the `default` group or empty prefix/suffix values.

## Configuration

The complete commented starter template is [config.yml](src/main/resources/config.yml).
It contains neutral English messages, a fallback group, a commented group example,
and separate player/server statistics sections. It is copied only when the server
configuration does not exist; it does not discover or generate LuckPerms groups.

| Setting | Default | Effect |
| --- | --- | --- |
| `messages.join-enabled` | `true` | Replace the event's join message with the selected template |
| `messages.quit-enabled` | `true` | Replace the event's quit message with the selected template |
| `messages.first-join-enabled` | `true` | Send an additional private message on the first recorded login |
| `messages.groups.default` | Neutral message templates | Fallback for group messages that are missing |
| `messages.groups.<group>.join` | Group-specific or fallback | Public join template |
| `messages.groups.<group>.quit` | Group-specific or fallback | Public quit template |
| `messages.groups.<group>.first-join` | Group-specific or fallback | Private first-login template; empty text disables it |
| `stats.player-header` / `stats.player-lines` | Player statistics | Heading and ordered list of player lines |
| `stats.server-header` / `stats.server-lines` | Server counters | Heading and ordered list of server lines |
| `country-fallback` | `Unknown` | Fixed text returned for `%country%` |

When join/quit replacement is disabled, the existing event message is left alone;
this is not a switch to silence the server's messages. A blank join/quit template
produces an empty message component. Missing group message keys fall back to the
corresponding `default` key. Explicitly empty values do not select that fallback.

For example, add this under `messages.groups` for a primary group named `vip`:

```yaml
vip:
  join: '<gold>VIP </gold><yellow>%playername%</yellow><gray> joined %world%.</gray>'
  quit: '<gold>VIP </gold><yellow>%playername%</yellow><gray> left the game.</gray>'
  first-join: ''
```

Use spaces rather than tabs and quote message strings. MiniMessage tags such as
`<gold>` and `<gray>` provide formatting. Legacy `&` color codes are not translated.

An empty statistics line list selects built-in defaults; it does not hide that
section. Legacy `stats.header` and `stats.lines` are rejected with a specific
log message, even if new keys are also present. Back up the file and move custom
templates into the player/server keys above; no automatic migration is performed.

### Configuration validation

At startup and on `/slm reload`, the file is parsed and validated before use.
Malformed YAML, unknown keys, missing required sections/values, wrong types,
non-string statistics lines, and malformed MiniMessage markup are rejected.
Use real YAML booleans (`true`/`false`), lowercase group names, and correctly
closed MiniMessage tags. MiniMessage's strict parser checks formatting structure;
it is not a whitelist of tag names or a check of expanded placeholder values.
Additional groups may omit message keys to use the `default` fallback.

An invalid startup configuration disables the plugin and logs an error. A failed
reload retains the previously active configuration and does not reload player
data. Neither case rewrites the file. Correct the reported error and restart the
plugin/server, or retry `/slm reload` if it is still enabled. Successful startup
prints `[SimpleLoginMessages] SimpleLoginMessages enabled.` in green to the console.

### Placeholders

| Placeholder | Value |
| --- | --- |
| `%playername%`, `%nickname%` | Player name; no separate nickname integration |
| `%group%` | LuckPerms primary group, or stored/fallback group |
| `%prefix%`, `%suffix%` | LuckPerms metadata, or stored/empty values |
| `%world%` | Actual world name, or stored world for an offline player |
| `%country%` | `country-fallback`; no GeoIP lookup |
| `%playerlist%` | Comma-separated names of currently online players |
| `%logins%` | Player's recorded login count, including the current login |
| `%totallogins%` | Stored global login count |
| `%uniqueplayers%` | Number of player entries in `data.yml` |
| `%onlineplayers%`, `%slots%` | Current online count and server slot limit |
| `%levels%`, `%health%`, `%gamemode%`, `%food%` | Live player values, or last stored snapshot |
| `%status%` | `Online` for live rendering; checked against online UUIDs for stored statistics |
| `%lastlogin%` | Latest recorded login in the server timezone; `Unknown` if unavailable |

`%lastlogin%` is updated when a player joins, so it represents that login rather
than the preceding visit. Offline snapshots are updated on joins and quits.
Placeholders are substituted before MiniMessage parsing; inserted text can
therefore contain formatting. Use trusted configuration and metadata.

## Stored data and limitations

`plugins/SimpleLoginMessages/data.yml` stores `totallogins` and a `players` section
keyed by UUID, including names, login counts, timestamps, and player snapshots.
The first-login check uses the absence of that player's stored login counter,
not Minecraft's historical first-visit flag. Deleting data resets that history.

The global counter is stored independently; unique players are calculated from
player entries. An old stored `uniqueplayers` field is removed when data is loaded.
There is no automatic PLM data importer. Stop the server before manually editing
data to avoid overwrites from login/logout saves; back it up first.

No Herochat routing, Vault integration, GeoIP lookup, vanish filtering, random
messages, per-player message rules, or timed return messages are implemented.
Join/quit messages use Paper events, so other plugins handling those events can
affect the final output. There is no automatic configuration migration.

## Build and validation

Use the existing JDK 25 and Maven 3.9.11 installations used for the local build.
The project includes [build.sh](build.sh), which selects a fixed user-local tool
location instead of any temporary path.

```sh
./build.sh
```

The script exports `JAVA_HOME` and starts Maven from
`~/.local/share/minecraft-devtools` by default. Set `SIMPLELOGINMESSAGES_TOOLS_DIR`
to change that shared tools directory, or set `SIMPLELOGINMESSAGES_JAVA_HOME`
and `SIMPLELOGINMESSAGES_MAVEN_HOME` to override the installations individually.
Check that Maven reports the intended JDK. Maven uses its default user-local dependency repository
(`~/.m2/repository`); do not override `maven.repo.local` with a temporary path.
The first build requires network access to obtain dependencies.

Output: `target/SimpleLoginMessages.jar`. Paper and LuckPerms APIs are
`provided` dependencies and are not bundled. LICENSE and NOTICE are included
under `META-INF/`. The build runs JUnit 5 tests with Mockito, real Paper event objects, MiniMessage,
and YAML files in temporary directories. Tests cover login counters and persistence,
first-login messages, group selection and fallback, command permissions, offline
statistics, reload behavior, tab completion, and initial configuration creation
without overwriting existing files. Validation tests cover obsolete keys, invalid
YAML/types/templates, startup failure, safe reload, and the green startup message. Resource checks also verify the plugin entry
point, version filtering, and default permissions.

Run `mvn test` for tests only, or `mvn clean verify` for a clean build with tests.
Reports are written to `target/surefire-reports/`. Test dependencies are not bundled
in the plugin JAR. Surefire loads Mockito as a test JVM agent explicitly, using
the same pinned version as the test dependency, so Mockito does not need dynamic
agent attachment. Compilation uses a separate `javac` process to avoid an
observed in-process compiler failure on JDK 25.

These are isolated regression tests with mocked server services, not a running
Paper server. They do not verify plugin loading, actual LuckPerms installation,
or interactions with other server plugins.

### Release archive

`mvn clean verify` also creates `target/SimpleLoginMessages-1.0.9.zip` containing:

- `SimpleLoginMessages.jar`, README, LICENSE, and NOTICE at the archive root.
- The corresponding project under `source/`, including `pom.xml`, documentation,
  licenses, Java sources, resources, tests, and the ZIP assembly descriptor.

Extract the ZIP, change into `source/`, and run `./build.sh` or `mvn clean verify`
with JDK 25 and Maven 3.9.11. The first build needs access to dependency repositories.
Local IDE settings, Git data, build outputs, and server runtime files are excluded.
Archive timestamps are fixed through `project.build.outputTimestamp`; use the same
JDK, Maven, and dependencies when comparing rebuilt artifacts.

Publish the ZIP alongside the standalone JAR. A release tag must identify the
source used to build both artifacts. The ZIP filename includes the Maven project
version; the standalone JAR filename remains `SimpleLoginMessages.jar`.

The plugin version is defined in `pom.xml`; Maven inserts it into `plugin.yml`
during the build. The JAR filename remains `SimpleLoginMessages.jar`.

### Changes in 1.0.9

- Clarify nullability in test fixtures and Mockito helpers, including player and UUID values.
- Complete nullability annotations at configuration validation boundaries.
- Verify production and test sources with Eclipse null analysis; plugin behavior is unchanged.

### Changes in 1.0.8

- Validate configuration syntax, structure, value types, and MiniMessage formatting.
- Reject legacy statistics keys with actionable log messages; no automatic migration.
- Disable startup on invalid configuration and preserve active settings on failed reload.
- Print the successful startup message in green.
- Add regression tests for validation, reload failure, and startup reporting.

### Changes in 1.0.7

- Clarified nullability contracts and removed redundant interface declarations.
- Added a neutral, commented starter configuration for new installations.

Existing server configuration files are preserved. Commands and permissions
remain unchanged.

## License and credits

SimpleLoginMessages is distributed under the **GNU General Public License,
version 2 or later** (`GPL-2.0-or-later`). See [LICENSE](LICENSE) for the full text
and [NOTICE](NOTICE) for attribution and provenance. External dependencies retain
their own licenses. The software comes without warranty.

SimpleLoginMessages was created to replace the join and quit messaging
functionality previously provided by PLM on the maintainer's server.
