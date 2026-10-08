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
| `waitFor(patterns [, timeout])` | Waits for a line of game text that matches one of `patterns`. Returns the line, the number of the pattern that matched, its captures, and the [links](#links) in the line. |
| `nextLine([timeout])` | The next line of game text, whatever it is, and its links. |
| `clearLines()` | Forgets the lines not yet read, so the next wait only sees what comes after. |
| `sendAndWait(command, patterns [, timeout])` | Waits out roundtime, clears the lines, sends the command, and waits as `waitFor` does. |
| `capture(command [, start [, finish [, timeout]]])` | See [Capturing a command's output](#capturing-a-commands-output). |
| `waitUntil(condition [, timeout [, interval]])` | Calls `condition()` every `interval` seconds (0.1 by default) until it returns something other than `nil` or `false`, and returns that. |
| `waitForPrompt([timeout])`, `waitForNav([timeout])` | Wait for the next prompt, or the next room. Return `true`. |
| `waitForRoundTime()`, `waitForCastTime()` | Wait until the roundtime or cast time is over. |
| `roundtime()`, `casttime()` | The seconds of roundtime or cast time left, `0` when there is none. |
| `now()` | Seconds since the script started, as a fraction, for timing things. |

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

### Links

A line of game text can have objects linked in it, the way the game marks a creature
("A `kobold` leaps from hiding!"). The waits return them as a list of `{ id, noun, name }`, `id`
being the exist id a command can use (`attack #1234`).

## Handlers

| Function | |
|---|---|
| `onLine(function(line, links) ... end)` | Called with every line of game text. |
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

These read what the game has told the client; each call is a fresh snapshot, so call it again
rather than keeping one for long. They are filled in on Simutronics games (GemStone IV,
DragonRealms); on a telnet MUD they are empty.

| Function | Returns |
|---|---|
| `room()` | `{ id, uid, title, description, exits, compass, objects }`. `id` is the room number from the game's navigation tag; `uid` the number the window title shows, when it shows one; `exits` the "Obvious exits" (`{"north", "out"}`); `compass` the lit compass directions (`{"n", "out"}`); `objects` the objects linked in the description. |
| `npcs()` | The creatures in the room: a list of `{ id, noun, name, status, flags }`. `status` is what the room text says ("stunned", "dead", "flying around"), or `nil`. `flags` are the game's own creature flags (`hostile`, `disengaged`...), where it sends them (DragonRealms), or `nil`. |
| `loot()` | The other things in the room, as `{ id, noun, name, status }`. |
| `pcs()` | The other characters in the room, as `{ id, noun, name, status }`. |
| `hands()` | `{ left, right, spell }`: the objects held, `nil` for an empty hand, and the prepared spell, `nil` when there is none. |
| `vitals()` | By name (`health`, `mana`, `stamina`, `spirit`, `concentration`, `stance`, `encumbrance`, `mind`): `{ percent, text, current, max }`. `current` and `max` are there when the bar says them ("mana 40/120"). |
| `status()` | The status icons lit, each `true`: `stunned`, `kneeling`, `sitting`, `prone`, `hidden`, `invisible`, `webbed`, `dead`, `joined`... |
| `effects([panel])` | One effects panel, `"spells"`, `"buffs"`, `"debuffs"` or `"cooldowns"`, as a list of `{ id, name, percent, remaining }`, `remaining` being seconds; or all four by name. |
| `effectActive(panel, nameOrId)` | Whether the effect is up, by its id (a spell number) or name (ignoring case), and the effect when it is. |
| `targets()` | `{ ids, current }`: the exist ids the game offers as targets, and the name of the one targeted. `targetIds()` is the ids alone. |
| `injuries()` | By body part (`head`, `rightArm`, `nsys`...): `{ kind = "injury" or "scar", severity }`. Healthy parts are left out. |
| `getHands()` | The hands and the spell as three strings, as before `hands()` existed. |

```lua
for _, npc in ipairs(npcs()) do
    if npc.status ~= "dead" then put("attack #" .. npc.id) break end
end
```

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
