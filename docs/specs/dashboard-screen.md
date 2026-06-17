# Warlock Dashboard screen - design spec

**Audience:** the designer working on Warlock's desktop UI.
**Goal:** describe what the Dashboard does, every element on it, and every state and dialog it
can show, so it can be redesigned without reading the code. This documents current behavior; treat
the visual treatment (layout, spacing, type, color, iconography) as open for you to define.

> Platform note: this covers the **desktop** app (the one users run on Mac, Windows, Linux). The
> mobile app has a simpler version of the same screen and does not yet expose the MUD Mobile
> features described here.

---

## 1. What the Dashboard is

The Dashboard is Warlock's home screen. It is the first thing the user sees on launch and the
screen they return to after disconnecting from a game. Its job is to let the user **get into a
game**, in one of three ways:

1. **Saved connections** - pick a previously used character and log straight in.
2. **Create a new connection** - log in to Simutronics (SGE) interactively, choose a game and
   character (a separate wizard flow, not part of this screen).
3. **MUD Mobile** - connect a MUD Mobile account, which adds that user's characters to the saved
   list so they can be played through MUD Mobile's hosted service.

Everything lives on one scrollable screen. There is no top navigation bar; the Dashboard is a full
panel.

---

## 2. Entry and exit

- **Shown when:** the app launches, or the user leaves a game / cancels the new-connection wizard.
- **Leaves to:**
  - the **New connection wizard** (when "Create a new connection" is pressed),
  - the **Game screen** (when a login succeeds),
  - an **Error screen** (a full-screen message with an "OK" button that returns here) when a
    connection fails outright.

---

## 3. Layout regions (top to bottom)

```
+--------------------------------------------------------------+
|  [ Create a new connection ]                                 |   (A) primary action
|                                                              |
|  <status message, when present>                              |   (B) status line
|                                                              |
|  -- scrollable area ----------------------------------------  |
|  MUD Mobile                                                  |   (C) MUD Mobile section
|  <inline MUD Mobile message, when present>                   |
|  [ Connect to MUD Mobile ]      (when not connected)         |
|     - or -                                                   |
|  [ Add a character ] [ Refresh ] [ Disconnect MUD Mobile ]   |
|                                                              |
|  Saved connections                                           |   (D) unified connection list
|  +--------------------------------------------------------+  |
|  | [Login]  Tannod (DR)                  [Edit] [Delete]  |  |
|  | [Login]  Khouseholder [MUD Mobile]    [Remove]         |  |
|  | ...                                                    |  |
|  +--------------------------------------------------------+  |
+--------------------------------------------------------------+
```

- **(A) Primary action:** a "Create a new connection" button, always at the top.
- **(B) Status line:** a single line of text used during an in-progress direct login (see States).
- **(C) MUD Mobile section:** account-level controls for the MUD Mobile integration.
- **(D) Saved connections list:** all saved characters, both normal and MUD Mobile, in one list.

(C) and (D) live together inside a single vertical scroll area.

---

## 4. Components

### 4.1 "Create a new connection" button
- Primary button. Always visible.
- Starts the interactive SGE wizard (account -> game -> character). That wizard is its own screen
  and is out of scope for this spec.
- Disabled while a direct login is in progress.

### 4.2 Status line (B)
- Plain text, shown only when there is something to say.
- During a normal saved-connection login it reads "Connecting..." and a **Cancel** button appears
  (see State: Connecting). The saved-connection list is hidden during this state.

### 4.3 MUD Mobile section (C)
A small header labelled "MUD Mobile" followed by either a connect prompt or a control row,
depending on whether the user has connected their MUD Mobile account (i.e. saved a device token).

- **Not connected:** one line of explanatory copy plus a **Connect to MUD Mobile** button.
- **Connected:** a row of three secondary buttons:
  - **Add a character** - opens the "Add a character" dialog (discover via SGE login).
  - **Refresh** - re-fetches the character list from MUD Mobile and updates the saved list.
  - **Disconnect MUD Mobile** - forgets the token and removes MUD Mobile entries from the list.
- An **inline message line** appears above these for validation / progress / error text
  (e.g. "That token was rejected.", "Added 3 character(s).").
- While the section is busy (validating a token, loading, discovering), the Add / Refresh /
  Connect buttons are disabled.

### 4.4 Saved connections list (D)
- Header "Saved connections".
- A list of connection rows (see anatomy below). Both normal and MUD Mobile connections appear
  here, in one combined list.
- **Empty state:** "There are currently no stored connections".

---

## 5. Connection list item anatomy

Each row represents one saved character. Three zones:

- **Leading: `Login` button.** Starts the login for that connection.
- **Headline: the connection name.** For MUD Mobile connections, the name is followed by a
  **`[MUD Mobile]` marker** so the user can tell the two kinds apart. (The marker text is a
  placeholder; a badge, icon, or color treatment is exactly the kind of thing to design here.)
- **Trailing: action buttons.**
  - Normal connection: **Edit** and **Delete**.
  - MUD Mobile connection: **Remove** only (no Edit; proxy/rename settings do not apply).

The only required visual difference between the two kinds is that MUD Mobile connections are
clearly marked and do not offer Edit. Everything else (the Login button, delete affordance) is
shared.

---

## 6. Screen-level states

The Dashboard has a few mutually-relevant states. Note there are two independent "busy" notions:
a **direct login** (covers the whole screen) and **MUD Mobile section activity** (local to that
section).

1. **Idle (default).** Primary button, MUD Mobile section, and the connection list are all shown
   and interactive.
2. **Connecting (direct login).** Triggered by pressing **Login** on a normal connection. The
   connection list is replaced by the status line ("Connecting...") and a **Cancel** button. On
   success the app moves to the Game screen; on failure the status line shows the reason (or a
   full-screen error appears for hard failures).
3. **MUD Mobile section busy.** Triggered by token validation, Refresh, or discovery. The MUD
   Mobile buttons are disabled and the inline message shows progress. The rest of the screen stays
   usable.
4. **MUD Mobile connecting.** Triggered by logging in to a MUD Mobile connection. A modal
   **Connecting dialog** (Section 7.5) is shown over the Dashboard with live status and a Cancel
   button.

---

## 7. Dialogs

All dialogs are centered modal windows with a title and a close (X). Buttons are bottom-right,
with the dismissive action on the left and the confirming action on the right.

### 7.1 Connect to MUD Mobile (token entry)
- **Opened by:** "Connect to MUD Mobile".
- **Purpose:** capture the user's MUD Mobile device token.
- **Body copy:** explains the token starts with "wlk_" and comes from the MUD Mobile dashboard's
  Tokens tab.
- **Fields:** a single-line text field for the token (placeholder "wlk_...").
- **Secondary action:** "Open mudmobile.com" (opens the site in a browser).
- **Buttons:** Cancel / Save.
- **After Save:** the token is validated by calling MUD Mobile; on success the user's characters
  are added to the saved list and the section switches to its connected state. On failure the
  section's inline message explains why ("That token was rejected. Check it and paste it again.").

### 7.2 Add a character (discovery)
- **Opened by:** "Add a character" (only available when connected to MUD Mobile).
- **Purpose:** log in to SGE locally to discover the user's characters for a game and register
  them with MUD Mobile so they appear in the list.
- **Body copy:** explains the login is local and that the password never leaves the machine.
- **Fields:**
  - **Account** - an editable combo box: the user can type any account name, or pick one of their
    saved accounts from a dropdown. Picking a saved account also fills the password.
  - **Password** - masked field.
  - **Game** - a dropdown of game codes (DR, DRX, DRF, GS3, GSX, GSF).
- **Buttons:** Cancel / Discover.
- **After Discover:** progress shows in the section's inline message ("Discovering characters...",
  then "Added N character(s)." or an error). New characters appear in the saved list.

### 7.3 Password prompt (MUD Mobile login)
- **Opened by:** pressing **Login** on a MUD Mobile connection for which no password is saved.
  (If a password is already saved, login proceeds with no dialog.)
- **Purpose:** collect the play.net password for that account.
- **Body copy:** "Enter the play.net password for account "<account>"." plus a reassurance that
  the password stays on this machine and is never sent to MUD Mobile.
- **Fields:** masked password (pre-filled if a saved password exists).
- **Buttons:** Cancel / Play.

### 7.4 Connection settings (normal connections only)
- **Opened by:** **Edit** on a normal connection.
- **Purpose:** rename the connection and configure an optional local proxy / Lich launch command.
- **Fields:** Name; an "Enable proxy" toggle; proxy launch command; proxy host; proxy port (with
  helper text about {host}/{port}/{home} substitutions).
- **Buttons:** Cancel / OK.

### 7.5 Connecting status (MUD Mobile)
- **Shown during:** a MUD Mobile login (State 4).
- **Purpose:** keep the user informed while the cloud session starts, and let them bail out.
- **Body:** a single status line that updates through the stages, e.g.:
  - "Logging in to SGE..."
  - "Starting your cloud session..."
  - "Waiting for the session to be ready..." (then live server status such as "Booting Lich...")
  - at ~25s: "Session isn't ready yet; connecting anyway shortly..."
  - at ~30s: "Session never reported ready; connecting anyway..."
  - "Connecting to the game..."
- **Buttons:** Cancel (cancels the attempt and tears down the half-started session).
- This is a good candidate for a spinner / progress affordance and a calm, non-alarming treatment
  of the "connecting anyway" messages (they are normal, not errors).

### 7.6 Confirmation: Delete / Remove
- **Opened by:** Delete (normal) or Remove (MUD Mobile).
- **Copy:**
  - Normal: "Are you sure that you want to delete: <name>".
  - MUD Mobile: "Remove <character> from your MUD Mobile characters? This does not affect your
    play.net account."
- **Buttons:** dismiss / confirm.

---

## 8. Key flows

**Play a saved (normal) character**
1. User presses Login on the row.
2. Screen enters Connecting state (status line + Cancel).
3. Success -> Game screen. Failure -> reason shown, or full-screen error.

**Connect a MUD Mobile account**
1. User presses "Connect to MUD Mobile" -> token dialog -> Save.
2. Token is validated; the user's MUD Mobile characters appear in the saved list, each marked
   `[MUD Mobile]`.

**Play a MUD Mobile character**
1. User presses Login on a `[MUD Mobile]` row.
2. If no saved password, the password prompt appears; otherwise it proceeds directly.
3. The Connecting status dialog shows the staged progress (Section 7.5).
4. Success -> Game screen.

**Add characters from Warlock (no dashboard visit needed on the website)**
1. Connected to MUD Mobile, user presses "Add a character".
2. Fills account / password / game, presses Discover.
3. Discovered characters are registered and appear in the list.

**Remove a MUD Mobile character / disconnect**
- Remove on a row deletes that character from MUD Mobile and the list.
- "Disconnect MUD Mobile" forgets the token and removes all `[MUD Mobile]` rows at once.

---

## 9. Microcopy reference (current strings)

These are the strings in the build today. They are functional, not final; feel free to rewrite for
tone and clarity.

- Primary: "Create a new connection"
- Connecting: "Connecting..." / "Connecting to MUD Mobile..." with "Cancel"
- List header: "Saved connections"; empty: "There are currently no stored connections"
- MUD Mobile header: "MUD Mobile"
- Not connected: "Connect your MUD Mobile account to play through MUD Mobile's hosted Lich. Your
  MUD Mobile characters then appear in the list below."
- Buttons: "Connect to MUD Mobile", "Add a character", "Refresh", "Disconnect MUD Mobile"
- Token dialog: "Paste a device token from your MUD Mobile dashboard (Tokens tab). It starts with
  "wlk_"." / "Open mudmobile.com" / Save
- Errors: "That token was rejected. Check it and paste it again." / "Your MUD Mobile token is no
  longer valid. Reconnect your account." / "Couldn't reach MUD Mobile: <detail>"
- MUD Mobile row marker: "[MUD Mobile]"

---

## 10. States to design for (checklist)

- Empty list (no connections, MUD Mobile not connected) - the true first-run state.
- A list mixing normal and MUD Mobile connections (the marker must read clearly).
- MUD Mobile connected but with **zero** characters yet (user should be guided to "Add a character"
  or the website).
- Direct login in progress (status + Cancel, list hidden).
- MUD Mobile login in progress (status dialog with staged messages).
- Long lists (scroll behavior; the MUD Mobile section and the list share one scroll area).
- Error messaging in the section's inline message line vs. a full-screen error.

---

## 11. Things for the designer to decide

- **MUD Mobile marker treatment.** Today it is the literal text "[MUD Mobile]" after the name. A
  badge, lockup, icon, or accent color would likely read better. Should normal vs. MUD Mobile rows
  be visually grouped, or fully interleaved (current behavior is interleaved, sorted as stored)?
- **MUD Mobile section vs. list.** The section (account controls) and the list are currently
  stacked. Consider whether the MUD Mobile controls belong in a header, a card, a menu, or inline.
- **Row affordances.** Login as a button vs. row click; Edit/Delete/Remove as buttons vs. an
  overflow menu or hover actions.
- **Connecting feedback.** Status line vs. inline progress vs. the modal dialog used for MUD
  Mobile; whether the direct-login "Connecting" state should also be a dialog for consistency.
- **First-run guidance.** How strongly to steer a brand-new user toward either "Create a new
  connection" or "Connect to MUD Mobile".
- **Iconography and density** for the list rows and section buttons.

---

## 12. Accessibility / behavior notes

- The connection list already exposes accessibility descriptions ("List of stored connections",
  "Saved connection"); preserve equivalents in any redesign.
- The MUD Mobile marker must be conveyed by something other than color alone.
- All actions are keyboard reachable; dialogs trap focus and close on the X / Cancel.
- Status messages during connection should be announced (live region) so they are not silent.
