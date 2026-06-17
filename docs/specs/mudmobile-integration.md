# Warlock ↔ MUD Mobile integration spec

**Audience:** the developer (human or Claude Code) working on the **Warlock** front-end client.
**Goal:** let a Warlock user sign in to MUD Mobile, see the characters they've set up,
pick one, log in with SGE, and have Warlock connect to MUD Mobile's hosted-Lich service
instead of directly to play.net — with no manual `.sal` editing.

> MUD Mobile (mudmobile.com) runs **Lich-5** on demand in the cloud. The user keeps using
> their own client (Warlock); we boot a per-session cloud machine running Lich, bridge the
> game stream, and tear it down on disconnect. This document is the contract Warlock codes
> against. You do **not** need access to the MUD Mobile source to implement the client side.

---

## 0. TL;DR — what Warlock has to do

```
1. User pastes a MUD Mobile device token (wlk_…) into Warlock once.            [setup]
2. GET  https://mudmobile.com/api/characters         (Bearer wlk_…)            → character list
3. User picks a character; Warlock prompts for that play.net account password.
4. Warlock runs its NORMAL SGE/EAccess login to eaccess.play.net               → {gamehost, gameport, key}
   (locally — the password and key NEVER go to MUD Mobile).
5. keyHash = sha256_hex(key)
6. POST https://mudmobile.com/api/sessions           (Bearer wlk_…)            → {sessionId, connect:{host,port,tls}}
   body: {game, character, gamehost, gameport, keyHash}
7. Open a TLS socket to connect.host:connect.port and send the SAME game key
   as the first line (exactly the handshake Warlock already sends to play.net).
   The router matches sha256(key) → bridges you to Lich → the game. DONE.
8. (optional) DELETE /api/sessions/{sessionId} when the user disconnects.
```

The single behavioral change versus a normal play.net connection: **substitute the
destination host/port** (from step 6's response) for the real gamehost/gameport, and wrap
the socket in TLS. The key line and everything after it is byte-for-byte identical to a
normal connection.

---

## 1. Concepts & glossary

| Term | Meaning |
|------|---------|
| **Device token** (`wlk_…`) | Per-device API credential the user creates in the MUD Mobile dashboard and pastes into Warlock. Sent as `Authorization: Bearer wlk_…`. This is the only thing Warlock stores. |
| **SGE / EAccess** | Simutronics' auth handshake to `eaccess.play.net:7910` (TLS). Warlock already implements this. It takes `account + password (+ game + character)` and returns `{gamehost, gameport, key}`. |
| **Game key** | The 32-hex launch token EAccess returns. It is the real credential the game server checks. It is **stable per character** and rotates roughly hourly. |
| **keyHash** | `sha256(key)` as lowercase hex (64 chars). MUD Mobile only ever sees this hash, never the key. |
| **Character profile** | A `{account, game, characterCode, characterName}` record the user saved in MUD Mobile. Lets Warlock pre-populate a character picker. |
| **Game code** | EAccess game identifier: `DR`, `DRX`, `DRF` (DragonRealms / Platinum / Fallen), `GS3`, `GSX`, `GSF` (GemStone IV / Platinum / Shattered). |
| **Router** | MUD Mobile's single public TCP endpoint (`play.mudmobile.com`). You connect here, not to play.net. |
| **Session** | One booted cloud Lich instance, created by `POST /api/sessions`, identified by `sessionId`. |

---

## 2. Hard security invariants (do not violate)

These mirror MUD Mobile's own non-negotiable rules. Warlock must uphold the client half:

1. **Never send the play.net password to MUD Mobile.** SGE runs locally in Warlock against
   `eaccess.play.net` exactly as it does today. mudmobile.com has no password endpoint on
   the device-token path, by design.
2. **Never send the raw game key to the MUD Mobile HTTP API.** The API only accepts
   `keyHash = sha256(key)`. The raw key is transmitted *only* as the first line of the game
   TCP stream to the router (same as you'd send it to play.net), where it's forwarded
   byte-for-byte to Lich.
3. **Store the device token securely** (OS keychain / credential store if available). Treat
   it like a password; it grants the ability to start billable sessions on the user's account.
4. **Use TLS for the router connection** when `connect.tls` is true (it is, on the default
   port 443). Don't fall back to plaintext silently.

---

## 3. Authentication: the device token

### How the user gets one
1. User logs in to **https://mudmobile.com** (magic-link email).
2. Dashboard → **Tokens** tab → **+ New token** → optional label (e.g. "Desktop Warlock").
3. The raw token (`wlk_…`, ~36 chars) is shown **exactly once**. User copies it into Warlock.

Warlock should provide a settings field for this and validate it by calling
`GET /api/characters` (a 401 means a bad/revoked token).

### How Warlock uses it
Every API request below includes:

```
Authorization: Bearer wlk_xxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

The server stores only `sha256(token)`; a revoked token (deleted in the dashboard) starts
returning `401`. Warlock should surface "reconnect your MUD Mobile account" on a 401.

---

## 4. API reference

Base URL: **`https://mudmobile.com`**. All bodies are JSON; all responses are JSON.

Common error envelope: `{ "error": "<code>", "detail"?: <string|object> }`.

| Code | Meaning | Warlock should… |
|------|---------|-----------------|
| 401 `unauthorized` | Missing/invalid/revoked token | Prompt user to re-paste their token |
| 402 `subscription_required` | No active subscription / beta access | Show "subscription required", link to mudmobile.com |
| 409 `concurrent_limit_reached` | User already at their concurrent-session cap (`limit`, `active` in body) | Offer to end an existing session, or explain the add-on |
| 400 `invalid_body` | Request failed validation (`detail` = field errors) | Bug in Warlock; log it |
| 502 `machine_create_failed` | Cloud boot failed | Show a transient error, allow retry |

### 4.1 `GET /api/characters` — list the user's saved characters

> **STATUS: live** at `https://mudmobile.com/api/characters`.

**Auth:** `Bearer wlk_…` (also accepts a dashboard cookie session, so the same endpoint
works from the browser; Warlock uses the Bearer token).

**Response 200:**
```json
{
  "characters": [
    {
      "id": "clx…",
      "account": "myaccount",
      "game": "DR",
      "characterCode": "C001234567",
      "characterName": "Tannod",
      "lastUsedAt": "2026-06-15T18:04:11.000Z"
    }
  ]
}
```
- Sorted most-recently-used first.
- `account` is the play.net **account name** (not a password). Multiple characters may share
  one account; group them in the UI and ask for the password once per account.
- `game` is the EAccess game code (see glossary). `characterCode` is what SGE's launch step
  needs. `characterName` is for display.
- An empty list (`{"characters": []}`) means the user hasn't set up any characters yet —
  Warlock should point them to the **Play** tab on mudmobile.com (or let them type an account
  name + password and discover characters via SGE directly; see §5, note).

**Errors:** `401`.

### 4.1b `POST /api/characters` — register a newly discovered character

> **STATUS: live** at `https://mudmobile.com/api/characters`.

Use this to add a character to the user's MUD Mobile picker after Warlock discovers it via a
**local SGE login** (account + password → EAccess character list). This is how a brand-new
user with an empty `GET /api/characters` populates their list from Warlock instead of having
to visit the dashboard. It is an **idempotent upsert** keyed on
`(user, account, game, characterCode)`, so calling it again just refreshes the name /
last-used time — safe to call on every successful SGE launch.

**Auth:** `Bearer wlk_…` (or dashboard cookie).

**Request body** (no secrets — never send the password or game key):
```json
{
  "account": "myaccount",
  "game": "DR",
  "characterCode": "C001234567",
  "characterName": "Tannod"
}
```
Field rules: all four are non-empty strings (`account`/`characterCode`/`characterName` ≤ 64
chars, `game` ≤ 16). `account`, `characterCode`, and `characterName` come straight from the
EAccess character list; `game` is the game code you logged in with.

**Response 200:**
```json
{
  "character": {
    "id": "clx…",
    "account": "myaccount",
    "game": "DR",
    "characterCode": "C001234567",
    "characterName": "Tannod",
    "lastUsedAt": "2026-06-17T18:04:11.000Z"
  }
}
```

**Errors:** `401`, `400 invalid_body` (`detail` = field errors).

> Suggested Warlock flow: after the user enters account + password and Warlock's SGE returns
> the character list, register each character the user chooses to keep (or all of them) with
> `POST /api/characters`. Then the connect path (§4.2 onward) and future sessions see them in
> `GET /api/characters`.

### 4.1c `DELETE /api/characters/{id}` — remove a saved character

**Auth:** `Bearer wlk_…` (or dashboard cookie). `{id}` is the `id` from a `GET`/`POST` result.

**Response 200:** `{ "ok": true }`. **Errors:** `401`, `404 not_found` (unknown id or not the
caller's profile).

### 4.2 `POST /api/sessions` — boot a hosted Lich session

Call this **after** Warlock has done SGE and has the real `{gamehost, gameport, key}`.

**Auth:** `Bearer wlk_…`

**Request body:**
```json
{
  "game": "DR",
  "character": "Tannod",
  "gamehost": "dr.simutronics.net",
  "gameport": 11024,
  "keyHash": "3f2a…(64 lowercase hex)…"
}
```
Field rules (server-enforced):
- `game` — non-empty string. Pass the game code from the character profile.
- `character` — non-empty string. The character name (used for display + collision cleanup).
- `gamehost` — **must** be a `*.play.net` or `*.simutronics.net` host (anti-SSRF allowlist).
  Use the `gamehost` value SGE returned verbatim. (GemStone → `*.play.net`,
  DragonRealms → `*.simutronics.net`.)
- `gameport` — positive integer; the `gameport` SGE returned.
- `keyHash` — `sha256(key)` as 64 lowercase hex chars. **Not** the key.

**Response 200:**
```json
{
  "sessionId": "clx…",
  "connect": { "host": "play.mudmobile.com", "port": 443, "tls": true }
}
```
- A 200 means the session row is already **active and routable** — the cloud machine has
  been created (it's still cold-booting, but the router holds your connection through that;
  see §6). **Do not poll for "ready" before connecting** — connect right away.
- Always use the returned `connect.host` / `connect.port` / `connect.tls`; don't hardcode
  them (they can change). They will currently be `play.mudmobile.com` / `443` / `true`.

**Errors:** `401`, `402`, `409` (includes `limit` + `active` ints), `400`, `502`.

**Important:** starting a new session for a character/key automatically tears down any prior
live session for that same character or key on the account. So a "reconnect" is just calling
`POST /api/sessions` again.

### 4.3 `GET /api/sessions/{sessionId}` — poll session status (optional)

**Auth:** `Bearer wlk_…`

**Response 200:**
```json
{
  "id": "clx…",
  "status": "launching" | "active" | "ended" | "failed",
  "statusDetail": "Booting Lich…",
  "ready": true,
  "readyAt": "2026-06-15T18:04:40.000Z",
  "game": "DR",
  "character": "Tannod",
  "createdAt": "2026-06-15T18:04:05.000Z"
}
```
- Purely informational (nice for a status spinner). **Not required** for connecting —
  routing depends only on the session being `active`, which it is by the time `POST` returns.
- `ready`/`readyAt` reflect the runner's readiness callback and may lag or never arrive in
  some environments; don't gate connecting on it.

**Errors:** `401`, `404 not_found`.

### 4.4 `DELETE /api/sessions/{sessionId}` — end a session (optional but recommended)

**Auth:** `Bearer wlk_…`

**Response 200:** `{ "ok": true }`. Destroys the cloud machine and frees the concurrency slot.

**Errors:** `401`, `404 not_found`.

The machine also self-destructs on disconnect/idle, so this is a courtesy/cleanup call —
but calling it on user-initiated disconnect frees the concurrency slot immediately (relevant
for users at their limit who want to switch characters fast).

---

## 5. SGE: done locally by Warlock

Warlock already speaks SGE/EAccess — keep using your existing implementation. The only thing
this integration adds is *where the character/account identifiers come from* (the
`GET /api/characters` list) and *where you connect afterward* (the router, not play.net).

The SGE inputs/outputs you need:
- **Inputs:** `account` + `password` (entered by the user) + `game` (code) + `characterCode`
  — `account`, `game`, `characterCode` all come from the chosen `GET /api/characters` entry;
  the user supplies only the password.
- **Output:** `{ gamehost, gameport, key }` for the chosen character, exactly as today.
- The launch step uses the **STORM** front-end designation (`L\t{characterCode}\tSTORM\n`),
  which is what yields a Stormfront-protocol stream — the protocol the hosted Lich expects.

> **Note (no profiles yet / discovery):** If `GET /api/characters` is empty, or you want to
> support accounts the user hasn't saved in MUD Mobile, Warlock can fall back to its native
> SGE flow: ask for account + password, list characters directly from EAccess, let the user
> pick. The MUD Mobile list is a convenience layer over the same SGE data, not a hard
> dependency for the connect path. Saved profiles get created either when the user uses the
> Play tab on mudmobile.com, or directly from Warlock via `POST /api/characters` (§4.1b)
> after a local SGE discovery.

**Key hashing:** compute `keyHash` over the exact key string EAccess returned, e.g.
`sha256_hex(key)` → 64 lowercase hex chars. This must match what the router computes from the
key you later send on the wire, so don't trim/transform the key differently in the two places.

---

## 6. Connecting to the router (the game stream)

After `POST /api/sessions` returns `connect`:

1. **Open a socket** to `connect.host:connect.port`. If `connect.tls` is true (default,
   port 443), perform a standard TLS handshake first (SNI = `connect.host`; it's a normal
   CA-valid cert, terminated at MUD Mobile's edge — verify it normally).
   - A plaintext endpoint also exists on the same host at **port 7000** if you ever need it,
     but prefer the TLS endpoint the API hands you.
2. **Send your normal Stormfront handshake as the first thing** — i.e. the game **key** line
   followed by your usual `/FE:` / version lines, exactly as Warlock sends to play.net. You
   do **not** need to change how you frame the key; the router is built to tolerate Warlock's
   framing:
   - It reads the first line (up to the first `\n`; `\r\n` is fine), decodes it as latin1,
     and extracts the **isolated 32-hex key token** from it (so a leading `<c>` tag or other
     framing is handled). It hashes *that token*, not the whole line.
   - It then forwards the entire buffered first line (key + handshake) and everything after
     it **byte-for-byte** to Lich. Nothing in the stream is rewritten.
3. **Send the key promptly.** The router drops connections that send no first line within
   **15 seconds**.
4. **Connect once and be patient — do NOT add a reconnect loop.** The cloud machine
   cold-boots in ~25–60s. The router *holds your connection* and retries dialing the machine
   internally for up to ~60–90s, so a patient client rides the boot transparently. A
   client-side reconnect loop will fight this and create duplicate/again-failing attempts.
   Set your connect/read timeout generously (≥ 90s for the initial bridge).
5. Once bridged, it's a normal game session: Lich ↔ play.net, streamed through to Warlock.
   Scripts and settings the user stored in MUD Mobile are already synced into the machine.

### Failure cases on the wire
- **Connection accepted then closed immediately, before any game data:** usually means no
  matching active session for your key hash (e.g. the session was never created, already
  ended, or the key rotated and no longer matches the `keyHash` you registered). Re-run
  `POST /api/sessions` with a fresh SGE key and reconnect.
- **Bridge never completes within ~90s:** the machine failed to boot. Surface an error;
  `GET /api/sessions/{id}` may show `failed`. Let the user retry.

---

## 7. End-to-end reference (pseudocode)

```pseudo
token = settings.mudmobileToken          # "wlk_…"

# 1. Discover characters
resp = GET "https://mudmobile.com/api/characters"
            headers: { Authorization: "Bearer " + token }
if resp.status == 401: prompt_reconnect(); return
chars = resp.json().characters
choice = user_pick(chars)                # {account, game, characterCode, characterName}

# 2. Local SGE (Warlock's existing engine) — password stays on this machine
password = user_prompt_password(choice.account)
sge = warlock_sge_login(account=choice.account, password=password,
                        game=choice.game, characterCode=choice.characterCode)
# sge = { gamehost, gameport, key }

# 3. Register the session with MUD Mobile (only the hash leaves the machine)
keyHash = sha256_hex(sge.key)
resp = POST "https://mudmobile.com/api/sessions"
            headers: { Authorization: "Bearer " + token, Content-Type: "application/json" }
            body: { game: choice.game, character: choice.characterName,
                    gamehost: sge.gamehost, gameport: sge.gameport, keyHash: keyHash }
switch resp.status:
  200: pass
  402: show("MUD Mobile subscription required"); return
  409: show("Already at your session limit"); offer_end_existing(); return
  else: show("Couldn't start session: " + resp.body.error); return
conn = resp.json().connect            # { host, port, tls }
sessionId = resp.json().sessionId

# 4. Connect to the router and play (no reconnect loop; timeout ≥ 90s)
sock = conn.tls ? tls_connect(conn.host, conn.port, verify=true, sni=conn.host)
                : tcp_connect(conn.host, conn.port)
sock.write(stormfront_handshake(sge.key))     # SAME bytes you'd send play.net
bridge(sock, warlock_game_io)                 # normal play from here

# 5. On user disconnect (optional, frees the slot immediately)
DELETE "https://mudmobile.com/api/sessions/" + sessionId
        headers: { Authorization: "Bearer " + token }
```

---

## 8. Work required on the MUD Mobile side (not Warlock)

For completeness / coordination — the Warlock dev does not implement these, but the
integration depends on them:

1. ~~**Add `GET /api/characters`**~~ — **DONE / live.** Returns the authenticated user's
   `CharacterProfile` rows in the shape defined in §4.1, on the `wlk_` Bearer path (and the
   dashboard cookie) via the existing `authenticateUserOrToken` helper.
2. ~~(Optional, future) An endpoint for Warlock to **create/update** a character profile~~ —
   **DONE / live.** `POST /api/characters` (upsert, §4.1b) and `DELETE /api/characters/{id}`
   (§4.1c) let Warlock register characters discovered via local SGE and remove stale ones.

Everything else Warlock needs — device tokens, `POST/GET/DELETE /api/sessions`, the router
wire protocol, the gamehost allowlist — already exists and is documented above as-built.

---

## 9. Quick test checklist

- [ ] Paste a `wlk_` token; `GET /api/characters` returns the user's characters (or `[]`).
- [ ] Bad token → 401 handled with a clear message.
- [ ] Pick a character, enter password, local SGE succeeds, key obtained.
- [ ] `POST /api/sessions` returns `connect`; verify the password and raw key were **never**
      sent to mudmobile.com (only `keyHash`).
- [ ] TLS connect to `connect.host:connect.port`, send the key line, ride the cold boot
      (≥90s timeout, no reconnect loop), reach the game.
- [ ] 402 (no subscription) and 409 (at concurrency limit) render sensible UI.
- [ ] `DELETE /api/sessions/{id}` on disconnect frees the slot.
```
