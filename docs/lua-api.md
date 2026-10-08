# The Lua script API

Warlock runs `.lua` scripts from the script folders, with the same `.name` command that runs
`.wsl` ones. This page lists what a script can call. [mud-scripts.md](mud-scripts.md) covers the
functions that only make sense on a telnet MUD (the hands row, the vitals bars, GMCP), and is the
place to look for those.

Scripts run in a sandbox: the base, `string`, `table`, `math`, `utf8` and `coroutine` libraries
are there, `io`, `os`, `debug`, `package`, `dofile` and `loadfile` are not. Libraries are loaded
with Warlock's own [`require`](#libraries).

A script's arguments are its varargs: `.hunt fast 3` runs `hunt.lua` with `...` being
`"fast", "3"`.

## Sending and echoing

| Function | |
|---|---|
| `put(command)` | Waits out any roundtime, then sends the command. |
| `move(command [, timeout])` | `put`, then waits until the room changes. Returns `true`, or `nil` when the time ran out. |
| `echo(text)`, `print(...)` | Writes to the game window. |
| `log(level, text)` | Writes to the debug window, from level 30 up. |
| `exit()` | Ends the script. |

## Waiting

Every wait that takes a `timeout` (in seconds) gives up when it runs out and returns `nil`.
Without one, it waits for as long as it takes.

| Function | |
|---|---|
| `pause([seconds])` | Waits, one second by default. |
| `waitFor(patterns [, timeout])` | Waits for a line of game text that matches one of `patterns`. Returns the line, the number of the pattern that matched, and its captures. |
| `nextLine([timeout])` | The next line of game text, whatever it is. |
| `clearLines()` | Forgets the lines not yet read, so the next wait only sees what comes after. |
| `sendAndWait(command, patterns [, timeout])` | Waits out roundtime, clears the lines, sends the command, and waits as `waitFor` does. |
| `capture(command [, start [, finish [, timeout]]])` | See [Capturing a command's output](#capturing-a-commands-output). |
| `waitUntil(condition [, timeout [, interval]])` | Calls `condition()` every `interval` seconds (0.1 by default) until it returns something other than `nil` or `false`, and returns that. |
| `waitForPrompt([timeout])`, `waitForNav([timeout])` | Wait for the next prompt, or the next room. Return `true`. |
| `waitForRoundTime()`, `waitForCastTime()` | Wait until the roundtime or cast time is over. |
| `roundtime()`, `casttime()` | The seconds of roundtime or cast time left, `0` when there is none. |
| `now()` | Seconds since the script started, as a fraction, for timing things. |
| `gameTime()` | The game's clock, in seconds since 1970, as the client keeps it in step with the server. |

`patterns` is a [regular expression](#regular-expressions) string, a compiled `re` object, or a
list of them.

**Lines are kept from the moment the script starts**, so a wait reads every line the game sent
since the script last read one; nothing is missed between sending a command and waiting for the
answer. That also means a wait can match an old line the script never read; call `clearLines()`
first when only what comes next counts, or use `sendAndWait`, which does.

```lua
local line, which, captures = sendAndWait("search", { "You find (.+)\\.", "You find nothing" }, 5)
if which == 1 then echo("found " .. captures[1]) end
```

### Capturing a command's output

`capture(command, start, finish, timeout)` sends the command without echoing it and takes the
game's answer off the screen: from the first line matching `start` (from the first line at all,
without one) through the line matching `finish` (up to the next prompt, without one). Lines before
the start are left on the screen. It returns the lines taken, and whether the end was reached
before the time ran out:

```lua
local lines, complete = capture("spell active", "^You currently have", "^End of list", 5)
```

Lines that are taken are not shown and are not passed to any script, including this one's own
`waitFor`; they still go into the log.

## Handlers

| Function | |
|---|---|
| `onLine(function(line) ... end)` | Called with every line of game text. |
| `onLine(pattern, function(...) ... end)` | Called with the captures whenever a line matches the **Lua** pattern. |
| `onGmcp(name, function(data, name) ... end)` | See [mud-scripts.md](mud-scripts.md#handlers). |

Handlers are called **whenever the script is waiting** in one of the waits above, `pause` and
`put` included, and, once the script has run its last line, for as long as it is left running.
So a script can watch for things in the background while it gets on with something else:

```lua
local ambushed = false
onLine("leaps out of hiding", function() ambushed = true end)
while true do
    put("search")
    pause(5)
    if ambushed then ... end
end
```

A handler should be quick. It may send commands and echo, but it may not wait while the script is
already waiting; that is an error. Set a flag, as above, and do the waiting in the script.

## Game state

Warlock itself tells a script only a little about the game:

| Function | |
|---|---|
| `status()` | The status icons lit, each `true`: `stunned`, `kneeling`, `sitting`, `prone`, `hidden`, `invisible`, `webbed`, `dead`, `joined`... |
| `getHands()` | What the hands hold and the spell prepared, as three strings. |
| `roundtime()`, `casttime()` | See [Waiting](#waiting). |

The rest (the room and what is in it, with exist ids; vitals; active spells and other effects;
targets; injuries) a script reads from the game's protocol itself, through a library. The
[`gamestate` library](https://github.com/WarlockFE/scripts/blob/main/lib/gamestate.lua) in the
Warlock scripts repository does that:

```lua
local gamestate = require("gamestate")
for _, npc in ipairs(gamestate.npcs()) do
    if npc.status ~= "dead" then put("attack #" .. npc.id) break end
end
```

| Function | |
|---|---|
| `protocolLines()` | The lines of the game's protocol (markup and all) that arrived since the last call, as a list. The first call returns the connection's recent history, its last few thousand lines, so a library starts out knowing what was sent before the script began. The second value is `true` when lines were lost because the script went too long without asking; a library can then treat what it knows as out of date until the game sends it again. On a telnet MUD there are none. |

## Regular expressions

`re` has regular expressions in Kotlin's flavour, which unlike Lua patterns have alternation (`a|b`),
optional groups and counted repeats. Flags are a string: `"i"` ignores case, `"m"` makes `^` and `$`
match at line breaks. Positions are Lua's, the same as `string.find` gives, so they can be used
with `string.sub`. Write patterns in `[[long brackets]]` to keep the backslashes:

| Function | |
|---|---|
| `re.test(pattern, text [, flags])` | Whether it matches. |
| `re.match(pattern, text [, flags])` | The captures, or the whole match when there are none; `nil` when it does not match. |
| `re.find(pattern, text [, init [, flags]])` | The start, the end, and the captures. |
| `re.gmatch(pattern, text [, flags])` | An iterator over the matches, as `string.gmatch`. |
| `re.gsub(pattern, text, replacement [, flags])` | Replaces every match, and returns the text and how many there were. `replacement` is a string (`$1` for a group), a function given the captures, or a table keyed by the first capture. |
| `re.split(pattern, text [, flags])` | The text between the matches. |
| `re.escape(text)` | A pattern that matches the text as it is. |
| `re.compile(pattern [, flags])` | A pattern to keep, with the methods `test`, `match`, `find`, `gmatch`, `gsub` and `split`. |

```lua
local hit = re.compile([[^You (hit|miss) the (.+?)(?: for (\d+) points)?\.]], "i")
local verb, target, damage = hit:match(line)
```

## Libraries

`require("name")` loads `lib/name.lua` from the first script folder that has one, and
`require("gs.status")` loads `lib/gs/status.lua`. A library is loaded once per script, and
`require` returns what it returned. Library names may use letters, digits, `_` and `-`.

## Other scripts

| Function | |
|---|---|
| `scripts.start(command)` | Starts a script, as typing `.command` would. `false` when there is no such script. |
| `scripts.run(command)` | Starts a script and waits for it to finish. |
| `scripts.isRunning(name)` | Whether a script of that name is running. |
| `scripts.stop(name)`, `scripts.pause(name)`, `scripts.resume(name)` | `false` when there is no such script running. |
| `scripts.list()` | The names of the scripts running. |

Starting a script stops any other running script of the same name, so a script cannot start
another copy of itself.

## Storage

| Function | |
|---|---|
| `storage.load()` | The table the script saved last, for this character; an empty table the first time. |
| `storage.save(table)` | Saves the table, replacing what was saved before. |

Tables are kept as JSON, so they hold strings, numbers, booleans and other such tables; functions
are lost. Each script has its own, for each character, kept under Warlock's data folder in
`script-data/<character>/<script>.json`.

## Variables

`variables.name` reads and `variables.name = value` writes the character's variables, the same
ones WSL scripts and the settings use. Setting one to `nil` deletes it.

## MatchList

The older way to wait for one of several things, kept for scripts that use it:

```lua
local list = MatchList()
list:addMatch("You find", "found")      -- text, ignoring case
list:addMatchRe("^Roundtime", "rt")     -- regular expression
local result = list:wait()
```

Unlike `waitFor`, it only sees lines that arrive after `wait()` is called.
