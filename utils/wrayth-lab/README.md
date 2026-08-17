# wrayth-lab

Runs the real Wrayth client (`Wrayth.exe`, under wine) against a fake game
server you control, so you can feed it arbitrary protocol data and screenshot
how it renders. Use it to settle questions like "what does Wrayth actually do
with this tag" without connecting to a live game.

Nothing here talks to a real game server, and no account credentials are
involved: the fake server accepts whatever key Wrayth sends.

Requirements: `wine` with Wrayth installed in the prefix (default
`~/.wine/drive_c/Program Files (x86)/SIMU/Wrayth`), plus `xwininfo` (x11-utils)
and `import`/`convert` (imagemagick) for screenshots. Python standard library
only. On Wayland this works through XWayland, since wine runs as an X11 client.

## Quick start

```bash
utils/wrayth-lab/wlab.py up                # fake server + Wrayth, ready to feed
utils/wrayth-lab/wlab.py send '<pushBold/>bold text<popBold/>'
utils/wrayth-lab/wlab.py shot bold.png     # -> /tmp/wrayth-lab/shots/bold.png
utils/wrayth-lab/wlab.py recv              # what Wrayth sent back to us
utils/wrayth-lab/wlab.py stop
```

Multi-line payloads read from stdin, one protocol line per text line:

```bash
utils/wrayth-lab/wlab.py send <<'EOF'
<style id="roomName"/>[The Wrayth Lab, Workbench]<style id=""/>
A <a exist="1234" noun="dummy">test dummy</a> stands here, patiently.
<prompt time="1700000001">&gt;</prompt>
EOF
```

Everything (logs, screenshots) lands in `/tmp/wrayth-lab`; override with `--dir`
or `$WLAB_DIR`.

## Commands

| Command | What it does |
| --- | --- |
| `up` | start the server in the background, then launch Wrayth |
| `serve` | run the fake server in the foreground (logs to stdout) |
| `launch` | launch Wrayth against an already running server |
| `send` | send protocol lines (args, or stdin); `-e` for `\n`/`\xNN` escapes, `--raw` for exact bytes with no line terminator |
| `sendfile` | send a file, one protocol line per text line, `--delay` to pace it |
| `boot` | resend the bootstrap sequence (`bootstrap.txt`, or a file you pass) |
| `recv` | show the lines Wrayth has sent us |
| `status` | server, connection, handshake and window state |
| `shot` | screenshot the window; `--crop WxH+X+Y`, `--window`, `--full` |
| `windows` | list X windows, for picking `--window` |
| `resize` | resize the window, e.g. `resize 1280x900` |
| `type` / `key` / `click` | fake input via XTEST, e.g. `type "look" --enter`, `key alt+d`, `click 40 890` |
| `stop` | shut down the server and kill wine |

`bootstrap.txt` is sent automatically on connect to get Wrayth out of its login
state and into a normal game screen (game mode, an `<app>` tag, stream windows,
vitals). Edit it freely, or start from a different file with
`up --bootstrap my-file.txt`.

## What we learned making this work

These are the non-obvious bits, kept here because they cost real time to find:

- **Wrayth ignores a `.sal` path on its command line.** A `.sal` is handed to
  SGE's `Launcher.exe` (the `.sal` file association is
  `Simutronics.Autolaunch` -> `"C:\Program Files\SIMU\SGE\Launcher.exe" %1`),
  which translates it into switches and starts the client. Pass a `.sal` to
  `Wrayth.exe` yourself and it just says "You must enter the game via the
  website."
- **The real command line is** `WRAYTH.EXE /G<gamecode>/H<host>/P<port>/K<key>`,
  concatenated with no spaces, e.g. `/GDR/H127.0.0.1/P7000/Kwlab`. Found by
  running `Launcher.exe` under `WINEDEBUG=+process` and reading the child
  process command line. `wlab launch` uses this form directly, so SGE's launcher
  is not involved.
- **Wrayth must run with its install directory as the working directory**, or it
  dies on startup with "Error loading skin from [storm.skn]".
- **Wrayth parses one line at a time and each line must be well formed.** A tag
  opened on one line and closed on the next is rejected; it reports the failure
  back over the socket as `<c>_error error at offset N: Ending tag not found
  for ...`, which is a handy way to check whether it accepts a construct.
- **Wrayth prefixes what it sends with `<c>`**, including the first two
  handshake lines (the key, then
  `/FE:WRAYTH /VERSION:1.0.1.28 /P:WIN_UNKNOWN /XML`). With chat mode on, a
  typed command arrives as `<c>'look at dummy`.
- **The empty command Wrayth sends answers `settingsInfo`.** A connection hangs until
  the client sends an empty command, and the handshake runs: the key, then
  `/FE:WRAYTH /VERSION:... /XML`, then - once the server has sent `<settingsInfo .../>`
  to a client whose parser is on - a burst of `<c>`, the client's whole settings upload
  as `<db><settings>...`, and `<c>_STATE CHATMODE ON`. Narrowed by feeding elements one
  at a time against an empty bootstrap: `mode` alone gets nothing back, `settingsInfo`
  alone gets nothing back, `settingsInfo` after `mode` gets the burst. `playerId` is not
  involved.

  The "after `mode`" part is not an ordering rule of its own, and it is worth being
  precise about, because reading one into it invites a client to track game mode and
  gate on it for no reason. `settingsInfo` sent before any `<mode>` is not queued,
  dropped, or refused - it is **never parsed**, because the parser is not on yet. The
  screenshot is unambiguous: the tag lands in the Story window as literal text,
  `<settingsInfo space_not_found="0" crc="0" instance="DR"/>`, the same as any other
  raw-mode text. Sending `mode` afterwards does not make up for it because there is
  nothing to make up for; re-sending `settingsInfo` works because that copy is the
  first one the parser ever sees. See the `<mode>` note below - it is the whole
  mechanism.
- **`_STATE CHATMODE` is a report, not a handshake step.** It carries whichever mode
  that client is configured for, `ON` or `OFF`, and arrives after the settings upload;
  it is the empty command above that unblocks the connection. What a real server does
  with the value is not something the bench can answer - its server is a stub.
- **`<mode>` chooses the parser, and the default is raw.** With `mode` left out of the
  bootstrap entirely, Wrayth prints what arrives as plain text: tags show up literally
  on screen, `<pushBold/>` and friends do nothing, and the vitals and stream windows
  never appear. Send `<mode id="GAME"/>` at any later point and it switches to the XML
  parser mid-stream, from that tag on. `<mode id="CMGR"/>` switches back - the raw mode
  is what character creation and book reading run in. So a client starts in raw mode and
  waits to be told otherwise, which is why ours does too.

  This one bit of state explains the handshake note above on its own. A client has no
  need to know which mode the *game* is in, or to gate any particular tag on it: a tag
  that arrives while the parser is off is text, and a tag that arrives while it is on is
  a tag.
- **`<compDef>` on the main stream is a live placeholder, and it keeps the style it was
  defined under.** `<compDef id='x'/>` in ordinary main-stream text reserves a spot that a
  later `<component id='x'>…</component>` fills **in place**, rewriting a line that has
  already scrolled by. The styling travels with the placeholder, not with the content: a
  `compDef` on a `<style id="roomName"/>` line shows its content on the room-name
  background even though the `<component>` that filled it came from an unstyled line.
  Two things that surprised us:
  - **A `<component>` whose placeholder does not exist yet is not retained.** Send the
    `<component>` first and the later `compDef` renders empty and stays empty; send it
    again afterwards and the spot fills. So this is a push to live placeholders, not a
    store the client reads from.
  - **A `compDef` inside a pushed style span never fills at all.** Inside
    `<pushBold/>…<popBold/>` or `<preset id='…'>…</preset>` the spot renders empty and
    stays empty however many `<component>` tags follow, while an identical `compDef` on
    a plain line, or on a `<style>` line, fills fine. Reproduced on a freshly started
    client with no other state. No parse error is reported, so the line is accepted -
    it just never updates.
  - **A `compDef` counts as output for prompt handling, even rendering nothing.** Two
    `<prompt>`s with nothing between them collapse to one, and two with a `compDef`
    between them both draw - same as if ordinary text had been sent. Useful as a
    three-way control when checking prompt behaviour: nothing / text / `compDef`.
- **`<style>` is connection state, not per-stream state.** A `<style id="roomName"/>` set
  between `<pushStream id='thoughts'/>` and `<popStream/>` is still in force for the next
  line written to main. So a client wanting to match Wrayth keeps one current style for
  the connection rather than one per stream, and lets it persist across lines until
  another `<style>` or a prompt clears it.
- **Skin names are matched case-insensitively.** A widget names a skin entry with
  `<skin name='...'>`, and the real server and the real skin disagree about case:
  GS4 sends `name='healthBar'` (and `manaBar`, `staminaBar`, `spiritBar`) while
  `storm.skn` defines `HealthBar`, `ManaBar`, `StaminaBar`, `SpiritBar`. Wrayth
  colours those bars correctly all the same, which is why our own skin lookups use
  `getIgnoringCase`. Sending `name='HeAlThBaR'` and `name='sTaMiNaBaR'` still gets
  the health bar its red and the stamina bar its gold, so this is a case-insensitive
  compare rather than some narrower tolerance.
- **A skin name it cannot resolve is reported in the stream**, as
  `* Did not find the skin object: <name>`, and the widgets in that `dialogData`
  do not render at all - so one bad name takes the whole group with it. Handy as an
  oracle: it says plainly whether a name matched, which is what makes a
  deliberately-wrong name a usable control when testing what Wrayth accepts.
- **`wine explorer /desktop=NAME,WxH` mangles arguments** when the program path
  contains spaces, and at least under GNOME/mutter the virtual desktop ignores
  the requested size and fills the screen. So `launch` runs Wrayth as a plain
  window by default and resizes it over X11 instead; `--desktop WxH` still opts
  into a virtual desktop. Wrayth also remembers `<app maximized='t'/>` in its
  settings and re-maximizes itself a moment after the window appears, so
  `launch` retries the resize until it sticks.
