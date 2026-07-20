# Claude Code sandbox for Warlock

A containerized dev environment for running **Claude Code with
`--dangerously-skip-permissions`** ("unconstrained") without giving it access
to your host. The container isolates the host filesystem and processes; only
this repo is writable inside it.

## Usage

```bash
# Unconstrained Claude Code session (builds the image on first run):
.devcontainer/claude-box.sh

# Drop into a shell in the same environment:
.devcontainer/claude-box.sh bash

# Run any command:
.devcontainer/claude-box.sh ./gradlew jvmTest
```

## What's inside

- **JDK 21** (Temurin) + **Gradle** (via the repo wrapper)
- **Android SDK**: platform `android-36`, `build-tools;36.0.0`, `platform-tools`,
  installed at your host's `sdk.dir` path so the git-ignored `local.properties`
  works unchanged
- **Claude Code** (native binary), pre-authenticated from your host login
- **git + ssh + gh + jq + ripgrep** — enough to fetch, rebase, push, and work
  a PR end to end without shelling out to the host

## Git & GitHub access

The remote is `git@github.com:sproctor/warlock3.git`, so **ssh is required** for
anything that talks to the remote — fetch, pull, push. Two knobs, both handled
by `claude-box.sh`:

| What | How | Notes |
|---|---|---|
| **Push/fetch auth** | Host **ssh-agent forwarded** into the container | Private keys never enter the container; the agent only signs |
| **Commit identity** | `user.name` / `user.email` read from your host git config, passed as `GIT_AUTHOR_*` / `GIT_COMMITTER_*` | Avoids bind-mounting `~/.gitconfig` (a mount of a missing file would create a *directory* on the host) |

You need an agent running on the host before launching:

```bash
eval "$(ssh-agent -s)" && ssh-add     # then: .devcontainer/claude-box.sh
```

Without one, the container still builds and tests fine — it just can't reach the
remote, and `claude-box.sh` warns you up front.

**Why agent forwarding and not `~/.ssh`?** Mounting your keys would hand an
unconstrained agent your credentials for *every* host they unlock, and they'd
persist in the image layer cache. The agent socket only permits signing, and
only while the container is running.

Prefer a scoped token instead? Export `GH_TOKEN` on the host and it's passed
through for `gh`; you can then use an HTTPS remote and skip the agent entirely.

## Portable — nothing is hard-coded

No home directory or repo path is baked into the committed files. On each run,
`claude-box.sh` derives everything from your environment and writes it to a
git-ignored `.env` that `docker-compose.yml` interpolates:

| Variable | Source |
|---|---|
| `HOST_USER` / `HOST_UID` / `HOST_GID` | `id -un` / `id -u` / `id -g` |
| `HOST_HOME` | `$HOME` |
| `REPO_DIR` | parent of `.devcontainer/` |
| `ANDROID_SDK_DIR` | `sdk.dir` from `local.properties` (else `$HOME/Android/Sdk`) |
| `GIT_USER_NAME` / `GIT_USER_EMAIL` | `git config --get user.name` / `user.email` |
| `HOST_SSH_AUTH_SOCK` | `$SSH_AUTH_SOCK` (else `/dev/null`) |

Move the repo, run as a different user, or check this into another clone — it
adapts. (Because these become build args, the image rebuilds when they change.)

## How it's wired

- Runs as your host user (matching uid/gid) so bind-mounted repo files keep
  correct ownership and absolute paths match the host.
- Login is **seeded read-only** from the host on first run into a persistent
  volume — both `~/.claude/.credentials.json` (token) and `~/.claude.json`
  (account identity, onboarding, folder trust). `CLAUDE_CONFIG_DIR` points
  Claude at that volume so it starts logged in as you and persists across runs.
  In-container writes never touch the host files.
- Persistent named volumes: `claude-home` (`~/.claude`), `gradle-cache`
  (`~/.gradle`), `konan-cache` (`~/.konan`).
- **Open network** — Gradle reaches Maven Central / Google repos with no config.
  The host is still protected by container isolation.

## Notes & limits

- **iOS targets** can't build on Linux; use desktop (`:desktopApp`) and
  `:androidApp` / `:core` modules.
- **Desktop GUI works out of the box.** `./gradlew run` renders on your host
  display via X11 passthrough (`claude-box.sh` grants access with `xhost`, and
  the image ships the X11 + software-OpenGL libs Compose Desktop needs). For
  hardware-accelerated rendering, see the commented `devices:` block in
  `docker-compose.yml`. On a pure-X11 host (not Wayland) it works the same.
- **Reset auth**: `docker volume rm devcontainer_claude-home` then re-run.
- **Rebuild image** after changing the Dockerfile:
  `docker compose -f .devcontainer/docker-compose.yml build --no-cache`.
- **GitHub host keys** are pinned at build time via `ssh-keyscan` (trust-on-first-use
  at build). To verify against GitHub's published fingerprints instead, check
  `/etc/ssh/ssh_known_hosts` against
  <https://docs.github.com/authentication/keeping-your-account-and-data-secure/githubs-ssh-key-fingerprints>.
- **No `python3`/`node`** in the image. `jq` covers JSON; add them to the
  Dockerfile if a workflow needs them.
