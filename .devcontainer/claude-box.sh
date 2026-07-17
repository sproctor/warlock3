#!/usr/bin/env bash
# Build (if needed) and enter the containerized Claude Code environment.
#
#   ./claude-box.sh          -> unconstrained Claude Code session
#   ./claude-box.sh bash     -> interactive shell in the same container
#   ./claude-box.sh <cmd...>  -> run an arbitrary command in the container
#
# Nothing here is hard-coded: the repo location, your home dir, user/uid/gid,
# and Android SDK path are all derived from the environment and written to a
# git-ignored .env that docker-compose.yml interpolates.
set -euo pipefail

cd "$(dirname "$0")"                 # .devcontainer
REPO_DIR="$(cd .. && pwd)"           # repo root = parent of .devcontainer

# Match the container's Android SDK to the host's local.properties (sdk.dir)
# so that file resolves unchanged inside the container. Fall back to the
# Android Studio default if local.properties is absent.
sdk_dir="$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' \
    "$REPO_DIR/local.properties" 2>/dev/null | head -1)"
sdk_dir="${sdk_dir:-$HOME/Android/Sdk}"

# Commit identity, copied from the host's git config so commits made in the
# container are authored by you. Empty is fine — git just prompts instead.
git_user_name="$(git config --get user.name 2>/dev/null || true)"
git_user_email="$(git config --get user.email 2>/dev/null || true)"

# Forward the host's ssh-agent so git can push (the remote is git@github.com)
# without copying private keys into the container. If no agent is running we
# point at /dev/null: the mount stays valid and ssh auth is simply unavailable.
host_ssh_auth_sock="${SSH_AUTH_SOCK:-}"
if [ -z "$host_ssh_auth_sock" ] || [ ! -S "$host_ssh_auth_sock" ]; then
    host_ssh_auth_sock=/dev/null
    echo "[claude-box] No ssh-agent detected; git push over ssh will not work." >&2
    echo "[claude-box] Start one with: eval \"\$(ssh-agent -s)\" && ssh-add" >&2
fi

# Values docker-compose.yml interpolates. Written to .env so plain
# `docker compose` commands (down, logs, ...) keep working too.
cat > .env <<EOF
HOST_USER=$(id -un)
HOST_UID=$(id -u)
HOST_GID=$(id -g)
HOST_HOME=$HOME
REPO_DIR=$REPO_DIR
ANDROID_SDK_DIR=$sdk_dir
GIT_USER_NAME=$git_user_name
GIT_USER_EMAIL=$git_user_email
HOST_SSH_AUTH_SOCK=$host_ssh_auth_sock
EOF

COMPOSE=(docker compose -f docker-compose.yml)

# Let the container (same uid as host) reach the host X server for GUI apps.
if [ -n "${DISPLAY:-}" ] && command -v xhost >/dev/null 2>&1; then
    xhost +SI:localuser:"$(id -un)" >/dev/null 2>&1 || true
fi

"${COMPOSE[@]}" build
exec "${COMPOSE[@]}" run --rm claude "$@"
