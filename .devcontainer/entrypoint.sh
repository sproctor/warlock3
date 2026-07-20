#!/usr/bin/env bash
# Seeds Claude Code credentials from the host on first run, then execs the
# requested command. Runs as the unprivileged container user.
set -euo pipefail

CLAUDE_DIR="${CLAUDE_CONFIG_DIR:-${HOME}/.claude}"
mkdir -p "${CLAUDE_DIR}"

# Commit identity arrives as GIT_AUTHOR_*/GIT_COMMITTER_* from the host's git
# config. Git treats a *set but empty* ident as an error ("empty ident name not
# allowed") rather than falling back, so drop the vars entirely when the host
# had none — that way git gives its normal "tell me who you are" hint instead.
for _var in GIT_AUTHOR_NAME GIT_AUTHOR_EMAIL GIT_COMMITTER_NAME GIT_COMMITTER_EMAIL; do
    if [ -z "${!_var:-}" ]; then
        unset "${_var}"
    fi
done
unset _var

# One-time seed: copy the host's login into the persistent volume so the
# container starts already authenticated *as your account*. We copy (not
# bind-mount) so in-container writes/token-refreshes never touch host files.
# Delete the claude-home volume to re-seed from the host.
#
#   .credentials.json -> OAuth token
#   .claude.json      -> account identity (email/org), onboarding, folder trust
if [ ! -f "${CLAUDE_DIR}/.credentials.json" ] && [ -f /seed/credentials.json ]; then
    cp /seed/credentials.json "${CLAUDE_DIR}/.credentials.json"
    chmod 600 "${CLAUDE_DIR}/.credentials.json"
    echo "[entrypoint] Seeded Claude Code credentials from host."
fi
if [ ! -f "${CLAUDE_DIR}/.claude.json" ] && [ -f /seed/claude.json ]; then
    cp /seed/claude.json "${CLAUDE_DIR}/.claude.json"
    echo "[entrypoint] Seeded Claude Code account config from host."
fi

exec "$@"
