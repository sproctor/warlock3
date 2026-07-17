# Containerized dev environment for running Claude Code with
# --dangerously-skip-permissions, isolated from the host.
#
# Toolchain: JDK 21 (Temurin) + Android SDK (platform 36 / build-tools 36.0.0)
# to build the full Warlock KMP project (desktop + androidApp; iOS is not
# buildable on Linux).
#
# All host-specific paths are supplied as build args by claude-box.sh, so this
# file contains no hard-coded home directory or repo location. Sensible
# defaults let it also `docker build` standalone.
FROM eclipse-temurin:21-jdk

ARG USER_NAME=dev
ARG USER_UID=1000
ARG USER_GID=1000
# Container home + Android SDK path. Mirrored to the host's values so the repo's
# git-ignored local.properties (sdk.dir=...) resolves inside the container.
ARG USER_HOME=/home/dev
ARG ANDROID_SDK_DIR=/home/dev/Android/Sdk
# Bump if a newer AGP needs a newer sdkmanager. See:
# https://developer.android.com/studio#command-line-tools-only
ARG CMDLINE_TOOLS_VERSION=11076708

ENV DEBIAN_FRONTEND=noninteractive

# Base tooling. ripgrep + git + less are what Claude Code leans on; the rest
# supports the Android SDK download and general dev use.
#
# openssh-client: the git remote is git@github.com, so without it every fetch,
#   pull, and push dies with "cannot run ssh: No such file or directory" —
#   the container can read history but can never sync with the remote.
# jq: the only JSON parser here (no python3/node), needed to work with the
#   GitHub API and any tooling that emits JSON.
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl unzip zip git ca-certificates less procps ripgrep \
        openssh-client jq \
    && rm -rf /var/lib/apt/lists/*

# GitHub CLI, from GitHub's own apt repo (the documented install path).
# Without it, PR/issue work degrades to hand-rolled curl against the REST API.
# `gh` reuses SSH agent forwarding or GH_TOKEN, so it needs no extra secrets.
RUN set -eux; \
    curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
        -o /usr/share/keyrings/githubcli-archive-keyring.gpg; \
    chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg; \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
        > /etc/apt/sources.list.d/github-cli.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends gh; \
    rm -rf /var/lib/apt/lists/*

# Pin GitHub's SSH host keys at build time so the first push isn't blocked by an
# interactive "authenticity of host can't be established" prompt (there is no
# TTY to answer it during an agent run). `test -s` fails the build if the scan
# came back empty, rather than leaving that prompt to surface mid-push later.
RUN mkdir -p /etc/ssh \
    && ssh-keyscan -t rsa,ecdsa,ed25519 github.com > /etc/ssh/ssh_known_hosts 2>/dev/null \
    && test -s /etc/ssh/ssh_known_hosts

# Recreate the host user 1:1 so bind-mounted repo files keep correct ownership
# and absolute paths match the host exactly. The Ubuntu base image ships a
# default user at UID 1000, so evict it first.
RUN set -eux; \
    if getent passwd "${USER_UID}" >/dev/null; then \
        userdel -rf "$(getent passwd "${USER_UID}" | cut -d: -f1)"; \
    fi; \
    if ! getent group "${USER_GID}" >/dev/null; then \
        groupadd -g "${USER_GID}" "${USER_NAME}"; \
    fi; \
    useradd -u "${USER_UID}" -g "${USER_GID}" -d "${USER_HOME}" -m -s /bin/bash "${USER_NAME}"; \
    mkdir -p "${USER_HOME}/.claude" "${USER_HOME}/.gradle" "${USER_HOME}/.konan"; \
    chown -R "${USER_UID}:${USER_GID}" "${USER_HOME}"
ENV HOME=${USER_HOME}

# --- Android SDK ---------------------------------------------------------
# Installed at the host's sdk.dir path so local.properties resolves unchanged.
ENV ANDROID_HOME=${ANDROID_SDK_DIR}
ENV ANDROID_SDK_ROOT=${ANDROID_SDK_DIR}
RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL -o /tmp/cmdtools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
    && unzip -q /tmp/cmdtools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm /tmp/cmdtools.zip
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"
RUN yes | sdkmanager --licenses >/dev/null 2>&1 || true \
    && sdkmanager --install "platform-tools" "platforms;android-36" "build-tools;36.0.0" >/dev/null \
    && chown -R "${USER_UID}:${USER_GID}" "${ANDROID_HOME}"

# --- Desktop GUI runtime -------------------------------------------------
# Native libs AWT + Compose Desktop/Skiko need to render over X11, plus Mesa
# software OpenGL (llvmpipe) so `./gradlew run` works without GPU passthrough.
RUN apt-get update && apt-get install -y --no-install-recommends \
        libgl1 libgl1-mesa-dri libglu1-mesa \
        libx11-6 libxext6 libxrender1 libxtst6 libxi6 libxrandr2 \
        libxcursor1 libxinerama1 libxxf86vm1 \
        libfreetype6 libfontconfig1 fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod 755 /usr/local/bin/entrypoint.sh

# --- Claude Code (native binary install, as the target user) -------------
USER ${USER_NAME}
ENV PATH="${USER_HOME}/.local/bin:${PATH}"
RUN curl -fsSL https://claude.ai/install.sh | bash

# Keep ALL of Claude Code's state (account config + credentials) in the
# persisted ~/.claude volume. Without this, Claude reads a throwaway
# ~/.claude.json stub the installer leaves in $HOME and shows up logged-out.
ENV CLAUDE_CONFIG_DIR="${USER_HOME}/.claude"

WORKDIR ${USER_HOME}
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
# Default: drop straight into an unconstrained Claude Code session.
CMD ["claude", "--dangerously-skip-permissions"]
