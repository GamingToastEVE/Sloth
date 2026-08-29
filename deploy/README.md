# Deploying Sloth on a Debian server

Step by step, from a fresh Debian box to a bot that restarts itself whenever the
default branch moves. Written for **Debian 12 (bookworm)**; the only version-specific part is the
Java package.

The deploy works by pulling: a systemd timer checks the default branch every five minutes and
restarts the bot only when the branch actually changed. The branch is the
remote's default branch, resolved automatically (master in this repository). Nothing has to reach the
server from outside — no open ports, no SSH key in GitHub, no webhook secret.

Assumed throughout: repository at `/DiscordBot`, service running as `root`. To change
that, adjust `WorkingDirectory`, `ExecStart` and `User` in the unit files and pass
`REPO=` / `JAR=` to the deploy script.

---

## 1. Packages

```bash
apt update
apt install -y git openjdk-17-jdk mariadb-server
```

**Use JDK 17, not 21.** The build runs on Gradle 8.4, which does not support Java 21 —
it fails with an "Unsupported class file major version" error. If the server already
has several JDKs, pin 17 for the build:

```bash
update-alternatives --config java     # pick the 17 entry
java -version                         # must report 17.x
```

The bot itself only needs Java 14 or newer at runtime, so 17 covers both build and run.

---

## 2. Database

```bash
systemctl enable --now mariadb
mariadb-secure-installation          # set a root password, accept the defaults
```

Create the database and a dedicated user — the bot does not need root:

```bash
mariadb -u root -p <<'SQL'
CREATE DATABASE sloth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'sloth'@'localhost' IDENTIFIED BY 'CHANGE-ME';
GRANT ALL PRIVILEGES ON sloth.* TO 'sloth'@'localhost';
FLUSH PRIVILEGES;
SQL
```

**No schema import is needed.** The bot creates every table on first start and the
migration manager keeps them up to date on later starts.

`utf8mb4` matters: server and user names contain emoji, and `utf8` would truncate them.

### Bringing existing data along (optional)

Only if a bot is already running somewhere with data worth keeping. On the **old** host:

```bash
mysqldump -u root -p --single-transaction --routines sloth > sloth-dump.sql
```

Copy it over and import **before** the first start of the new bot:

```bash
mariadb -u root -p sloth < sloth-dump.sql
```

Then stop the old instance, so two bots are not online with the same token.

---

## 3. Repository

```bash
git clone https://github.com/GamingToastEVE/Sloth.git /DiscordBot
cd /DiscordBot
git checkout master        # the repository's default branch
chmod +x gradlew scripts/deploy.sh
```

---

## 4. Configuration

```bash
cp .env.example .env
nano .env
```

Four values matter:

```ini
TOKEN_KEY=TOKEN                 # without this the bot logs in as the TEST bot
TOKEN=your_production_token
DB_NAME=sloth
DB_USER=sloth
DB_PASSWORD=CHANGE-ME
```

`TOKEN_KEY` decides which key holds the token. It defaults to `TOKEN_TEST` so a
developer machine keeps using the test bot; the server has to say `TOKEN` explicitly.

Missing `DB_*` keys silently fall back to `localhost:3306/sloth` as `root`/`admin` —
convenient locally, not something to run a live bot on. Set them.

```bash
chmod 600 .env                  # the token is in here
```

The bot reads `.env` relative to its working directory, which is why the unit sets
`WorkingDirectory=/DiscordBot`. Without the file, startup aborts immediately — that
is intentional.

---

## 5. First build

```bash
cd /DiscordBot
./gradlew shadowJar             # first run downloads Gradle and the dependencies
cp build/libs/*-all.jar /DiscordBot/sloth.jar
```

Take the `-all.jar`. The thin `Sloth-1.0.jar` next to it has no dependencies bundled
and dies immediately with `NoClassDefFoundError`.

---

## 6. systemd

```bash
cp deploy/sloth.service deploy/sloth-deploy.service deploy/sloth-deploy.timer \
   /etc/systemd/system/
systemctl daemon-reload
```

Check that the database unit is really called `mariadb.service` on this system:

```bash
systemctl list-units | grep -iE 'maria|mysql'
```

If it is `mysql.service`, adjust both `After=` lines in `sloth.service`. If the
database is on another host, remove them.

```bash
systemctl enable --now sloth.service
systemctl enable --now sloth-deploy.timer
```

Note that `sloth-deploy.service` is **not** enabled — the timer starts it.

---

## 7. Verify

```bash
systemctl status sloth
journalctl -u sloth -f
```

A healthy start logs, in this order:

```
Starting with token from TOKEN
Configuring HikariCP connection pool for MariaDB: jdbc:mariadb://localhost:3306/sloth
Successfully created HikariCP connection pool
Syncing guild: ...
```

Then check the timer:

```bash
systemctl list-timers sloth-deploy     # when the next check runs
systemctl start sloth-deploy.service   # trigger one now
journalctl -u sloth-deploy -n 30
```

A check that found nothing prints nothing, so the journal only shows real deploys.

---

## 8. On the Discord side

- **Server Members Intent** must be enabled in the developer portal, otherwise
  auto-roles on join, timed roles and role-change tracking stay dead.
- Message Content Intent is **not** needed — the bot does not request it.
- Slash commands are registered at startup. New or removed subcommands appear after
  the restart, occasionally with a few minutes of Discord-side caching.

---

## 9. Day-to-day

| Task | Command |
|------|---------|
| Deploy now instead of waiting | `systemctl start sloth-deploy.service` |
| Rebuild without any change | `FORCE=1 /DiscordBot/scripts/deploy.sh` |
| Restart the bot | `systemctl restart sloth` |
| Live log | `journalctl -u sloth -f` |
| What did the last deploys do | `journalctl -u sloth-deploy -n 50` |
| Stop auto-deploy temporarily | `systemctl stop sloth-deploy.timer` |

### What the deploy does in the awkward cases

- **default branch unchanged** → nothing happens, no restart, no log noise.
- **Build fails** → the running bot is left completely untouched with its old jar.
  The next timer run tries again.
- **Uncommitted changes on the server** → no deploy, only a note in the journal.
  Someone is editing in production and that is not the script's decision to override.
- **History diverged** (commits made on the server) → aborts instead of merging.
- **Bot does not come back up** → the script reports an error naming the
  `journalctl` command instead of claiming success.
- **Two deploys at once** → a lock file makes the second one exit immediately.

---

## 10. Rollback

The deploy is a fast-forward plus a jar swap, so going back is a checkout and a build:

```bash
cd /DiscordBot
systemctl stop sloth-deploy.timer          # stop it pulling the bad commit again

GOOD=abc1234                               # sha to go back to, see: git log --oneline
git checkout "$GOOD"
./gradlew shadowJar && cp build/libs/*-all.jar sloth.jar
systemctl restart sloth
```

Once the default branch is fixed:

```bash
git checkout master        # the repository's default branch
systemctl start sloth-deploy.timer
```

Note that a rollback does **not** undo database migrations — they only ever add
columns and tables, so an older build keeps working, but a column added by the newer
build stays.

---

## 11. Troubleshooting

| Symptom | Cause |
|---------|-------|
| `Unsupported class file major version` during build | Java 21 active, Gradle 8.4 needs 17 |
| Bot starts, every DB action fails | `DB_*` wrong or MariaDB not running — check the JDBC line in the log |
| `Starting with token from TOKEN_TEST` on the server | `TOKEN_KEY=TOKEN` missing from `.env` |
| Startup aborts with a Dotenv error | no `.env` in `/DiscordBot` |
| `NoClassDefFoundError` right after start | thin jar installed instead of `-all.jar` |
| Timer never fires | `sloth-deploy.timer` not enabled — `systemctl list-timers` |
| Deploy does nothing, journal says "uncommitted changes" | local edits in `/DiscordBot`, commit or `git checkout -- .` |
| Commands missing in Discord | bot restarted? Server Members Intent enabled? |

---

## Notes

- Up to five minutes pass between a push and the restart. Shorten it via
  `OnUnitActiveSec=` in `sloth-deploy.timer`, at the cost of more `git fetch` calls.
- The service runs as `root`. That works, but a dedicated user owning `/DiscordBot`
  would be enough and is worth changing later.
- `Restart=always` brings the bot back after a crash, independent of the deploy.
- Database migrations run automatically at startup, so a schema change needs no extra
  step — the restart is enough.
