#!/usr/bin/env bash
# Deploy check for the Sloth bot, run by sloth-deploy.timer.
#
# Fetches the main branch and does nothing at all unless it actually moved.
# When it did: fast-forward, build, and only restart the bot once a usable jar
# exists - a failed build leaves the running bot untouched.
#
#   /DiscordBot/scripts/deploy.sh          normal run (used by the timer)
#   FORCE=1 /DiscordBot/scripts/deploy.sh  rebuild and restart even without changes
#
# Overridable:
#   REPO=/DiscordBot  MAIN=main  SERVICE=sloth  JAR=/DiscordBot/sloth.jar

set -euo pipefail

REPO="${REPO:-/DiscordBot}"
SERVICE="${SERVICE:-sloth}"
JAR="${JAR:-$REPO/sloth.jar}"
# /var/lock is standard on Linux; fall back to /tmp so the script still runs where
# it does not exist rather than aborting before it has done anything.
if [ -z "${LOCK:-}" ]; then
    if [ -d /var/lock ]; then LOCK=/var/lock/sloth-deploy.lock; else LOCK=/tmp/sloth-deploy.lock; fi
fi

log() { echo "[deploy] $*"; }
fail() { echo "[deploy] !! $*" >&2; exit 1; }

# Never let two deploys overlap: a manual run during a timer run would have both
# writing build/libs at the same time.
exec 9>"$LOCK"
if ! flock -n 9; then
    log "another deploy is already running, skipping this check"
    exit 0
fi

cd "$REPO" || fail "repository not found at $REPO"
git rev-parse --git-dir >/dev/null 2>&1 || fail "$REPO is not a git repository"

# Remote name: whatever this checkout actually uses. The developer machine calls
# it 'master', a fresh server clone calls it 'origin' - resolve instead of assuming.
if [ -z "${REMOTE:-}" ]; then
    if git remote | grep -qx origin; then
        REMOTE=origin
    else
        REMOTE="$(git remote | head -n1)"
    fi
fi
[ -n "$REMOTE" ] || fail "no git remote configured"

# Branch to deploy. Resolved from the remote's own default branch rather than
# assumed to be "main" - this repository's default branch is master, and hardcoding
# main made every deploy fail at the fetch below.
if [ -z "${MAIN:-}" ]; then
    MAIN="$(git symbolic-ref --quiet --short "refs/remotes/$REMOTE/HEAD" 2>/dev/null | sed "s#^$REMOTE/##")"
fi
if [ -z "${MAIN:-}" ]; then
    MAIN="$(git remote show "$REMOTE" 2>/dev/null | sed -n 's/.*HEAD branch: //p' | head -n1)"
fi
if [ -z "${MAIN:-}" ]; then
    if git ls-remote --exit-code --heads "$REMOTE" main >/dev/null 2>&1; then MAIN=main; else MAIN=master; fi
fi
git ls-remote --exit-code --heads "$REMOTE" "$MAIN" >/dev/null 2>&1 \
    || fail "branch '$MAIN' does not exist on remote '$REMOTE' - set MAIN= explicitly"

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"

# Refuse to touch a dirty checkout. Local edits on a deploy server mean someone is
# debugging in production; silently discarding or merging over that would be worse
# than not deploying. Untracked files (logs, .env, the jar) are fine.
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    log "uncommitted changes present - not deploying"
    git status --short --untracked-files=no
    exit 0
fi

if [ "$CURRENT_BRANCH" != "$MAIN" ]; then
    log "checkout is on '$CURRENT_BRANCH', switching to '$MAIN'"
    git checkout "$MAIN" || fail "could not switch to $MAIN"
fi

git fetch --quiet "$REMOTE" "$MAIN" --prune

LOCAL="$(git rev-parse HEAD)"
UPSTREAM="$(git rev-parse "$REMOTE/$MAIN")"

if [ "$LOCAL" = "$UPSTREAM" ] && [ "${FORCE:-0}" != "1" ] && [ -f "$JAR" ]; then
    # Nothing to do. Kept quiet so the journal is not flooded every 5 minutes.
    exit 0
fi

if [ "$LOCAL" = "$UPSTREAM" ]; then
    if [ ! -f "$JAR" ]; then
        log "no jar at $JAR yet - building the current checkout"
    else
        log "FORCE set - rebuilding without changes"
    fi
else
    log "main moved: $(git rev-parse --short "$LOCAL") -> $(git rev-parse --short "$UPSTREAM")"
    git --no-pager log --oneline "$LOCAL..$UPSTREAM" | sed 's/^/[deploy]   /'

    # Fast-forward only: if history diverged, someone committed on the server and
    # a merge would need a human.
    git merge --ff-only "$REMOTE/$MAIN" || fail "cannot fast-forward to $REMOTE/$MAIN - resolve manually"
fi

log "building..."
./gradlew --quiet --console=plain shadowJar || fail "build failed - the running bot was left untouched"

# Strictly the shadow jar. build/libs also holds a thin Sloth-1.0.jar without any
# dependencies; picking that one by mtime would deploy a jar that dies instantly
# with NoClassDefFoundError.
BUILT="$(ls -t build/libs/*-all.jar 2>/dev/null | head -n1 || true)"
[ -n "$BUILT" ] || fail "build produced no shadow jar (build/libs/*-all.jar) - the running bot was left untouched"

# Install to the fixed path the service unit points at. Copy to a temporary file
# and move it into place, so the service never sees a half-written jar.
cp "$BUILT" "$JAR.new"
mv "$JAR.new" "$JAR"
log "installed $(basename "$BUILT") -> $JAR"

log "restarting $SERVICE"
systemctl restart "$SERVICE"

# Give it a moment to come up, then report honestly whether it did.
sleep 5
if systemctl is-active --quiet "$SERVICE"; then
    log "deploy done, $SERVICE is running at $(git rev-parse --short HEAD)"
else
    fail "$SERVICE did not come up - check: journalctl -u $SERVICE -n 50"
fi
