# Scripts a MUD can send to Warlock

A MUD that Warlock connects to over plain telnet can hand the client a Lua
script, the way it hands Mudlet an interface package, and that script can fill
in what the telnet stream never states: how long the roundtime is, when a spell
finishes casting, and anything else the game says only in its own text or its
own GMCP packages.

This page is for the people who run MUDs. Players need only leave "Run the
script the MUD sends" checked on the connection, which it is by default.

## Offering the script

Once GMCP is negotiated, Warlock announces itself:

```
Core.Hello {"client":"Warlock","version":"3"}
Core.Supports.Set ["Char 1","Char.Items 1","Room 1"]
```

Send the script the same way you would send Mudlet a package, by `Client.GUI`,
after checking that the client is Warlock (a Mudlet package sent to Warlock is
ignored, and a Lua file sent to Mudlet fails to install):

```
Client.GUI {"version": "3", "url": "https://mud.example/clients/warlock.lua"}
```

Warlock fetches the URL, keeps the script beside the character's settings with
its version, and runs it. On later logins the same version runs from that copy
without a fetch; a different version is fetched afresh. So bump the version
whenever the script changes.

Since what is fetched is run, the URL must be `https`, redirects are not
followed, the script may be at most 1 MiB, and a URL naming a private,
link-local or otherwise reserved address is refused. The one exception is
the player's own machine: `localhost` or a loopback address is fetched from,
over plain `http` too, so a MUD and its script can be tested locally. The
player is told when a fetch is refused or fails.

Two other forms are accepted. The script itself can be sent in place of a URL,
which is never kept, so it is fetched from you every login:

```
Client.GUI {"version": "3", "script": "onLine('^Roundtime: (%d+)', function(s) setRoundTime(tonumber(s)) end)"}
```

And Mudlet's older raw form, the version and the URL on two lines, works too.
The version may be a number or a string; a URL ending in `.mpackage`, `.zip`,
`.xml` or `.trigger` is taken for a Mudlet package and passed over.

The script runs under the name `mud-script`. Sending a new version mid-session
replaces the running one, and a player can stop it with `/kill mud-script`
like any other script.

## What the script can do

The script runs in the same sandbox as a player's own Lua scripts: the Lua
standard library without `io`, `os`, `package` or `debug`, and no file access.
It can send commands to the game as the player, print to the game window, read
and write the character's stored variables, and set the status bar. It runs
top to bottom, and if it registered any handlers it then stays alive, calling
them as things happen, until the player stops it or the connection closes.

### Handlers

```lua
-- Every GMCP message with this name, or under this package: a handler for
-- "Char" hears Char.Vitals and Char.Items.List alike. `data` is the message's
-- JSON as a Lua table (objects keyed by string, arrays from 1), or nil when the
-- message carried none.
onGmcp("Char.Status", function(data, name)
    if data.roundtime then setRoundTime(data.roundtime) end
end)

-- Every line of game text.
onLine(function(line)
    if line == "You feel the spell take hold." then setCastTime(0) end
end)

-- Lines matching a Lua pattern, called with the captures (or the whole match
-- when the pattern has none).
onLine("^Roundtime: (%d+) sec", function(seconds)
    setRoundTime(tonumber(seconds))
end)
```

An error in a handler is shown to the player as a script error; the other
handlers still run, and the script goes on.

### The status bar

```lua
setRoundTime(seconds)   -- start the roundtime bar counting down from `seconds`; 0 clears it
setCastTime(seconds)    -- the same for the cast time bar
```

Both take a number of seconds from now. A script's `put()` waits for the
roundtime to pass before sending, so setting it makes the player's own scripts
behave as they do on a Simutronics game.

### Talking GMCP

Warlock asks for the `Char`, `Char.Items` and `Room` packages at negotiation.
If the script wants others, it can ask for them itself:

```lua
sendGmcp("Core.Supports.Add", {"Char.Status 1"})
sendGmcp("Core.Ping")
sendGmcp("Custom.Hello", '{"already": "json"}')
```

The second argument may be a table, sent as JSON (a table whose keys are
exactly 1..n becomes an array), a string, sent as it is, or nothing.

### Everything else

The rest of the Lua API is available too:

| Function | What it does |
| --- | --- |
| `echo(text)`, `print(...)` | Show text in the game window. |
| `put(command)` | Send a command as the player, once the roundtime has passed. |
| `move(command)` | `put`, then wait for the next room. |
| `pause(seconds)` | Wait. Inside a handler this holds up the other handlers too. |
| `waitForPrompt()`, `waitForNav()`, `waitForRoundTime()` | Wait for the next prompt, room, or the end of the roundtime. |
| `MatchList()` | Wait for the first of several lines; see the player scripting docs. |
| `variables.name` | The character's stored variables, read and written. |
| `log(level, message)` | Write to the debug window (levels of 30 and above). |
| `exit()` | Stop the script. |

## A worked example

A game that reports roundtime in its prompt-side text and has a
`Char.Status` package:

```lua
-- warlock.lua, version 3
sendGmcp("Core.Supports.Add", {"Char.Status 1"})

onGmcp("Char.Status", function(status)
    if status.balance == 0 then
        setRoundTime(status.recovery or 3)
    end
    if status.casting then
        setCastTime(status.casting)
    end
end)

onLine("^You are stunned for (%d+) seconds", function(seconds)
    setRoundTime(tonumber(seconds))
end)
```
