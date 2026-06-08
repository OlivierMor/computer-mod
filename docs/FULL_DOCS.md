# Computer Mod

Computer Mod is an addon for the Create tech mod (NeoForge 1.21.1). It adds a programmable computer
that runs real Lua code, plus three companion pieces that connect that code to the world: a **Sensor**
(input), a **Receiver** (output), and a handheld **Controller** (manual input). They communicate over
named **wireless channels**.

This documentation covers the whole mod from the ground up: writing Lua even if you have never coded,
every block and item, every function, and how to read blocks from other mods and Create addons.

## The model

The computer is a brain. It runs Lua, does math, stores data, and reads and writes channels. It does
not touch neighbouring blocks directly. Other pieces handle the physical world:

- A **Sensor** mounts on a block and publishes everything it reads about that block to a channel.
- The **computer** reads channels, decides what to do, and publishes values back to channels.
- A **Receiver** converts a channel value into a redstone signal to drive machines, lamps, Create
  gearshifts, pistons, and anything else redstone controls.
- A **Controller** lets you, the player, drive channels by hand from your keyboard and mouse.

```
  world  ->  [Sensor]  ->  channel  ->  [Computer]  ->  channel  ->  [Receiver]  ->  world
            reads a block   wireless     your Lua code   wireless     emits redstone
```

Computers can also message each other over channels and store data that survives reboots.

## Works with other mods

The Sensor reads blocks generically, with no mod-specific code. It pulls standard NeoForge data (item
inventories, fluid tanks, Forge Energy), every blockstate property, and the block entity's full NBT.
Because almost every modded machine stores its state in NBT, you can read the state of practically any
block from any mod, even one with no documented API. See
[Reading Any Mod or Addon](#reading-any-mod-or-addon).

## Copy for an LLM

The **Copy all docs** button at the top copies this entire site as clean Markdown. Paste it into an
LLM and ask it to write a program, for example "turn on a lamp when a tank is over half full". The
text lists every function that exists, so the model stays within the real API.

## Where to next

- New here: start with [Getting Started](#getting-started).
- Never coded: read [Lua Basics](#lua-basics).
- Want the function list: see [API Reference](#api-reference).
- Reading a specific machine: see [The Sensor](#the-sensor) and [Reading Any Mod or Addon](#reading-any-mod-or-addon).

# Getting Started

This page gets a working computer running in about five minutes.

## 1. Place and power the computer

The Computer is a full block with a cogwheel running horizontally through its centre, like Create's
Encased Cogwheel. Place it, then power it through that cogwheel. It accepts either of Create's two
power types:

- **Rotation (recommended, free):** drive the central cogwheel. Mesh another cogwheel with it from the
  side, or run a shaft into either end of its horizontal axis. Any non-zero RPM works while the network
  is not overstressed.
- **Electricity (Forge Energy):** connect an FE cable or generator from a mod that provides one.

When powered, the computer boots. When power is removed, it halts immediately. See
[The Computer](#the-computer) for how this works in detail.

## 2. Write a program

Right-click the computer to open its screen. It has two tabs:

- **Code** is a text editor with a cursor, selection, copy and paste, Lua syntax highlighting, and line
  numbers.
- **Console** shows `print(...)` output and errors.

Type a first program in the Code tab:

```lua
print("Hello from my computer!")
```

## 3. Flash it

Press **Flash** to store the program in the computer permanently. It survives closing the world. If the
computer is powered, flashing reboots it right away. Switch to the Console tab to see your message.

> A program that runs top to bottom and ends shows the state FINISHED. To keep working, use a loop, as
> shown next.

## 4. Make it run continuously

Most useful programs loop. Always put a `sleep` inside a forever-loop so it paces itself:

```lua
print("counting...")
local n = 0
while true do
  n = n + 1
  print("tick " .. n)
  sleep(1)        -- wait 1 second
end
```

## 5. Connect it to the world

1. Place a **Sensor** against a block you care about, such as a chest. It snaps flat to that face like a
   button. Right-click it and set a channel name, for example `chest`.
2. Place a **Receiver** where its redstone can drive a machine or lamp. Right-click it and set a channel
   name, for example `alarm`.
3. Flash this onto the computer:

```lua
while true do
  local c = channel("chest")              -- the sensor's readings (a table)
  local count = (c and c.item_count) or 0
  if count > 0 then emit("alarm", 15)     -- chest not empty -> redstone on
  else emit("alarm", 0) end
  sleep(0.5)
end
```

That is the full loop: Sensor to channel to Computer to channel to Receiver. The rest of this site
builds on these pieces.

# The Computer

The computer behaves like a microcontroller. You flash it with a program, it runs while it has power,
and it forgets its variables the moment power is lost.

## Flashing (the program is permanent)

- Pressing **Flash** stores your Lua source in the block itself, saved with the world.
- Flashing while powered reboots the computer cleanly with the new code.
- The flashed code stays until you flash something else. Cutting power does not erase it.

## Power

The computer accepts either power type. Whichever is present runs it:

| Source | How | Notes |
|---|---|---|
| Rotation (Create) | Drive the central cogwheel: mesh a cogwheel from the side, or run a shaft into either end of its horizontal axis | Free to run. Needs non-zero RPM and a network that is not overstressed. Imposes a small stress load. |
| Forge Energy (FE) | Feed FE from a cable or generator into the block | Consumes a fixed amount of FE per tick, and only when there is no rotation. Rotation is preferred and free. |

The computer is powered when it has rotation or is actively receiving FE. If neither is present, it is
OFF.

## Power loss is immediate

Like a real microcontroller, the computer runs only while power is actually being supplied. It has a
small internal FE buffer that smooths the incoming supply, but that buffer is not a battery: it cannot
keep the computer running on its own. Stop driving the cogwheel, or disconnect the FE source, and the
computer powers down within a single tick. It does not coast on stored charge.

## Boot and halt behaviour

- **Power on:** the computer boots a fresh runtime from the flashed code. Variables (RAM) start empty
  and the Console is cleared. The program runs from the top.
- **Power off:** the computer halts at once and discards all variables (RAM). The last Console output
  stays frozen on screen so you can read it. The next boot clears it.
- **Program finishes:** if your code reaches the end with no loop, it stops at FINISHED until the next
  power cycle.

> RAM, disk, and flash are three different stores. Flash holds your code and is permanent. Disk is a
> key/value store you control that also persists (see [API Reference](#api-reference)). RAM holds your
> variables while running and is wiped on power loss. Put anything that must survive into `disk`.

## Run states

The screen shows one of four states:

- **OFF:** no power, not running.
- **RUNNING:** executing your program.
- **FINISHED:** the program ended without error.
- **ERROR:** the program crashed. The message is printed to the Console.

## Performance

The program runs on its own thread at a fixed clock speed, measured in Lua instructions per game tick
(default 200,000 per tick, about 4,000,000 per second, configurable). Pure computation and a
`while true` loop run at full speed. Logic does not need to match the 20 ticks per second game rate.
Only `sleep` and world round-trips are tick-bound. See [Patterns and Recipes](#patterns-and-recipes)
for loop pacing.

## Configuration

Server config file `computermod-common.toml` (NeoForge):

| Key | Default | Meaning |
|---|---|---|
| `maxOpsPerTick` | 200000 | Lua instructions per game tick (times 20 for per second). Higher means faster compute and more CPU per running computer. |
| `minRpm` | 1.0 | Minimum absolute RPM for a kinetically powered computer to run. |
| `feCapacity` | 100000 | Internal Forge Energy smoothing buffer (FE). |
| `fePerTick` | 20 | FE consumed per tick when running on electricity. Ignored while kinetically powered. |

# The Sensor

The Sensor is a thin plate that mounts flat against a block, like a button or Create's Redstone Link.
Whatever block it is attached to is the block it reads.

## Placing it

Aim at the face of a block and place the Sensor. It snaps onto that face. If the block it is mounted to
is removed, the Sensor pops off and drops. It mounts on any side, including walls, floor, and ceiling.

## What it does

The Sensor scans the block it is attached to every tick and publishes the complete readings table the
instant anything changes. Add an item, change a fluid level, or flip a blockstate, and it transmits on
that same tick with no polling delay. If nothing changed, it stays quiet, since the channel already
holds the latest value. Right-click the Sensor to set its channel name, which is the only setting.

> Because it always publishes the whole table, `channel("name")` from a sensor is always a Lua table.
> Index into it, for example `channel("name").item_count`. It never publishes a bare value.

## The live readings panel

The Sensor's GUI shows, live, exactly what it currently sees, as a collapsible tree. Container values
such as `state`, `items`, and `nbt` show an expand arrow. Click it to open their nested fields. This
lets you discover which fields a given block exposes before you write any code. Scroll with the mouse
wheel.

## Reading fields

Which fields appear depends on the block. Always guard with `if t.field then ... end`. The full set of
possible fields:

| Field | Type | Appears when | Meaning |
|---|---|---|---|
| `block` | string | always | Block id, e.g. `"minecraft:chest"`. |
| `is_air` | boolean | always | True if the space is empty. |
| `has_block_entity` | boolean | always | True if the block has a block entity. |
| `rpm` | number | Create kinetic blocks | Current rotation speed (signed). |
| `generated_rpm` | number | Create kinetic blocks | Speed it generates if it's a source. |
| `overstressed` | boolean | Create kinetic blocks | Whether its kinetic network is overstressed. |
| `is_kinetic` | boolean | Create kinetic blocks | Marker that kinetic fields are present. |
| `item_count` | number | block has an item inventory | Total items across all slots. |
| `slots` | number | block has an item inventory | Number of inventory slots. |
| `items` | array | block has an item inventory | One entry `{ slot, item="<id>", count }` per **non-empty** slot. |
| `tanks` | array | block has fluid tanks | One entry `{ fluid="<id>", amount=<mB>, capacity }` per tank. |
| `energy` / `energy_capacity` | number | block stores Forge Energy | Stored / maximum FE. |
| `state` | table | always | Every blockstate property, e.g. `state.powered`, `state.facing`, `state.level`. |
| `analog_output` | number | block emits a comparator signal | Comparator output 0 through 15, such as container fullness. |
| `nbt` | table | block has a block entity | The block entity's entire saved data as a nested table, the universal fallback. See [Reading Any Mod or Addon](#reading-any-mod-or-addon). |

### Example

```lua
-- a Sensor on a fluid tank publishes to channel "tank"
local t = channel("tank")
if t and t.tanks and t.tanks[1] then
  local tk = t.tanks[1]
  print(tk.fluid .. ": " .. tk.amount .. " / " .. tk.capacity .. " mB")
end
```

# The Receiver

The Receiver is the computer's hands. Like the Sensor it is a thin plate mounted against a block face,
and it pops off if that block is removed. It reads nothing. It only outputs redstone.

## What it does

Right-click to set a channel. The Receiver reads the latest value on that channel and emits a redstone
signal on all sides, every tick:

| Channel value | Redstone output |
|---|---|
| a number | clamped to 0 through 15 |
| `true` | 15 |
| `false`, `nil`, nothing | 0 |
| a numeric string like `"7"` | parsed, clamped to 0 through 15 |
| anything else, such as a table | 0 |

```lua
emit("pump", true)   -- receiver on "pump" outputs 15
emit("pump", 7)      -- outputs 7
emit("pump", false)  -- outputs 0
```

> Avoid wiring a Receiver directly to a Sensor's channel. A Sensor publishes a table, which a Receiver
> reads as 0. The intended flow is Sensor to Computer to Receiver: the computer reads the sensor data,
> decides, and emits a number or boolean for the Receiver to convert into redstone.

> For plain wireless redstone of 0 through 15 with no logic, Create's own Redstone Link already does the
> job and is separate from this system. These channels are for rich data and computer logic.

# The Controller

The Controller is a handheld item that lets you, the player, drive channels directly from your keyboard
and mouse. It publishes to the same channels as everything else, so a key press can flip a Receiver,
feed a value into a running program, or trigger any logic listening on that channel. It is useful for
manual control and for testing a setup before writing code.

## Holding it

Hold the Controller in either hand. Two actions are bound to it:

- **Left-click** opens the configuration screen.
- **Right-click** toggles operate mode on and off.

## Configuring bindings

The configuration screen is a list of rows. Each row maps one input to one channel with a mode. Press
**Add binding** to create a row, then set:

- **Input:** click the input button, then press the key, mouse button, or scroll wheel you want. Press
  Escape to cancel.
- **Channel:** type the channel name. A dropdown suggests channels that already exist.
- **Mode:** click to cycle through the modes available for that input.

| Mode | Input | Effect |
|---|---|---|
| toggle | key or mouse button | Each press flips the channel between `false` and `true`. |
| hold | key or mouse button | Channel is `true` while the input is held, `false` when released. |
| analog | scroll wheel only | Channel is a number moved through a range by scrolling. |

For an analog row, set **min**, **max**, and **step**. Scrolling up and down moves the value through
`[min, max]` in `step` increments. Press **Apply** to save the bindings onto the item. You can define up
to 10 bindings.

## Operate mode

Right-click to start operating. While operating:

- Each bound input drives its channel. The moment you start, every binding publishes its resting value:
  `false` for hold, the remembered state for toggle, and `min` for analog.
- A bound input's normal game function is suppressed, so a bound W key no longer walks you forward.
- Every input you did not bind keeps working as usual. This partial passthrough is what separates the
  Controller from Create's Linked Controller, which locks you in place.
- A small heads-up display lists each binding and its live value.

Right-click again to stop. Switching away from the item also stops operating. Either way, held channels
are released and the suppressed keys are restored.

## How values land on channels

Toggle and hold publish booleans. Analog publishes numbers. These arrive on the channel exactly as if a
computer had called `emit`, so a Receiver converts them to redstone and a computer reads them with
`channel(name)`. Publishes are edge-triggered: a value is sent only when it actually changes.

```lua
-- a computer reading a Controller binding on channel "throttle" (analog 0..15)
while true do
  local v = channel("throttle") or 0
  emit("motor", v)            -- pass the player's scroll value to a Receiver
  sleep(0.1)
end
```

# Lua Basics

The computer runs Lua 5.1. This page teaches enough Lua to be productive even if you have never
programmed. If you already know Lua, skip to the [API Reference](#api-reference).

## Comments

```lua
-- this is a comment; everything after -- on the line is ignored
print("hi")  -- comments can trail code too
```

## Variables and types

Use `local` to make a variable. Lua values have a few basic **types**:

```lua
local name   = "Steve"     -- string
local count  = 42          -- number (integers and decimals are both "number")
local ratio  = 3.14
local ready  = true        -- boolean: true / false
local nothing = nil        -- nil = "no value"
```

> Always use `local`. Without it, variables are global and can clash with other code.

## Strings

Join strings with `..` (two dots):

```lua
local who = "world"
print("hello " .. who)          -- hello world
print("count = " .. 5)          -- numbers auto-convert when concatenated
print(string.format("%.1f%%", 73.456))  -- 73.5%  (formatted)
```

Handy: `string.format`, `string.sub(s, i, j)`, `string.find`, `#s` (length), `tostring(x)`,
`tonumber(s)`.

## Numbers and math

```lua
local a = 10 + 3 * 2     -- 16 (normal precedence)
print(math.floor(3.9))   -- 3
print(math.max(4, 9, 2)) -- 9
print(math.sqrt(144))    -- 12
```

## Conditionals

```lua
local fuel = 30
if fuel <= 0 then
  print("empty")
elseif fuel < 50 then
  print("low")
else
  print("ok")
end
```

Comparisons: `==` equal, `~=` not equal, `<`, `>`, `<=`, `>=`. Combine with `and`, `or`, `not`.

> Truthiness: only `false` and `nil` count as false. In Lua, `0` and `""` are both true, which is a
> common trap.

## Loops

```lua
-- numeric for: 1,2,3,4,5
for i = 1, 5 do print(i) end

-- step by 2: 0,2,4,6,8,10
for i = 0, 10, 2 do print(i) end

-- forever (the usual shape for control programs)
while true do
  -- do work
  sleep(0.25)   -- ALWAYS sleep in a forever-loop
end
```

`break` exits a loop early.

## Tables (lists and dictionaries)

Tables are Lua's one data structure. They serve as both arrays and key/value maps. Arrays are
1-indexed.

```lua
-- list / array
local fruits = { "apple", "pear", "plum" }
print(fruits[1])     -- apple   (NOT fruits[0])
print(#fruits)       -- 3       (length)
table.insert(fruits, "kiwi")

-- dictionary / record
local player = { name = "Steve", hp = 20 }
print(player.name)   -- Steve
player.hp = player.hp - 5

-- loop a list
for i, f in ipairs(fruits) do print(i, f) end
-- loop a dictionary
for key, value in pairs(player) do print(key, value) end
```

Sensor readings arrive as tables exactly like these, for example `t.item_count` and
`t.items[1].count`.

## Functions

```lua
local function add(a, b)
  return a + b
end
print(add(2, 3))   -- 5
```

## Safe code (pcall)

Wrap risky code so a crash becomes a value you can handle instead of an ERROR state:

```lua
local ok, err = pcall(function()
  -- risky stuff here
end)
if not ok then print("caught: " .. err) end
```

# API Reference

Everything the computer can call. The computer talks to the world only through these functions. There
is no `scan`, `redstone`, `setOutput`, or `moveItems`; that work belongs to the Sensor and Receiver
blocks.

## Function index

| Function | Returns | Purpose |
|---|---|---|
| `print(...)` | none | Write a line to the Console. Multiple arguments allowed. |
| `getLocation()` | `{x,y,z}` | This computer's own world coordinates, like a built-in GPS receiver. |
| `emit(channel, value)` | none | Publish any value (number, string, boolean, or table) on a named channel. |
| `channel(name)` | value or nil | The latest value on a channel, or `nil` if none. |
| `channels()` | table | Array of all active channel names. |
| `disk.set(key, value)` | none | Persistent storage write. A `nil` value deletes the key. |
| `disk.get(key)` | value or nil | Persistent storage read. |
| `disk.delete(key)` | none | Remove one key. |
| `disk.list()` | table | Array of stored key names. |
| `disk.clear()` | none | Wipe the whole disk. |
| `sleep(seconds)` | none | Idle without using clock budget (minimum one tick, 0.05s). |

## Output

### `print(...)`
Writes a line to the **Console** tab. Accepts multiple arguments (separated by tabs, like standard Lua).
Use it constantly while debugging.

## Location (built-in GPS)

### `getLocation()` returns `{ x=, y=, z= }`
Returns this computer's own block coordinates. A stationary computer reports a fixed position. Broadcast
it on a channel and other computers can navigate relative to it (see
[Patterns and Recipes](#patterns-and-recipes)).

## Wireless channels: the computer's I/O

### `emit(channelName, value)`
Publishes a value on a channel. The value can be a number, string, boolean, or a nested table. This is
how a computer drives Receivers and messages other computers. Emitting `nil` clears the channel.

### `channel(channelName)` returns a value or nil
Reads the latest value on a channel, or `nil` if nothing has been published. The type is whatever was
published, often a table when the source is a Sensor.

### `channels()` returns a table
Returns an array of all channel names that currently hold a value.

## Persistent storage (disk)

A key/value store on the computer that survives reboots, power loss, and world reloads, unlike RAM
variables. It holds up to 1024 keys. Values may be numbers, strings, booleans, or tables.

```lua
local boots = disk.get("boots") or 0
disk.set("boots", boots + 1)
print("booted " .. (boots + 1) .. " times")   -- counts up across reboots
```

- `disk.set(key, value)` stores a value, or deletes the key when given `nil`.
- `disk.get(key)` reads a value, or `nil` if the key is unset.
- `disk.delete(key)` removes a key.
- `disk.list()` returns an array of stored keys.
- `disk.clear()` wipes everything.

> `disk.*` calls do a quick round-trip to the server thread, so they take about one game tick each.
> Read/write in bulk outside tight inner loops where you can.

## Timing

### `sleep(seconds)`
Pauses the program without burning clock budget. Fractions are allowed but the minimum real wait is one
game tick (**0.05s**); `sleep(0.02)` still waits one tick. Put a `sleep` in every forever-loop.

## The Lua sandbox

You get Lua 5.1 with the standard libraries that make sense in a game.

Available: base functions (`print`, `type`, `tostring`, `tonumber`, `pairs`, `ipairs`, `next`,
`select`, `error`, `assert`, `pcall`, `xpcall`, `setmetatable`, `getmetatable`, `rawget`, `rawset`,
`unpack`, `#`, and so on), `math.*`, `string.*`, `table.*`, and `os.time`, `os.clock`, `os.date`.

Disabled for safety (these are `nil`): `io`, file access, `os.execute`, `os.exit`, `require`,
`package`, `load`, `loadstring`, `dofile`, `loadfile`, `debug`, `collectgarbage`, and any Java access
(`luajava`).

# Channels

Channels are the wireless backbone that ties everything together.

## What they are

A channel is a server-wide named mailbox holding a single latest value. Computers, Sensors, Receivers,
and Controllers all share the same set of channels by name. Pick any name you like, such as `"tank"`,
`"alarm"`, or `"base-coords"`.

- A computer's `emit(name, value)`, a Sensor, or a Controller writes the latest value.
- A computer's `channel(name)` or a Receiver reads the latest value.

## Rich values beyond 0 to 15

Vanilla and Create redstone carry a single number from 0 to 15. A channel can carry a number, string,
boolean, or a whole nested table. That is what lets a Sensor hand the computer a full inventory listing,
and lets computers send each other structured messages.

```lua
emit("status", { online = true, items = 42, name = "sorter-3" })
-- elsewhere:
local s = channel("status")
if s and s.online then print(s.name .. " has " .. s.items) end
```

## Latest value only

A channel remembers only the most recent value. If you publish faster than someone reads, earlier
values are overwritten. For control logic this is the behaviour you want, since you care about the
current state.

## Lifetime

Channels are runtime state. They are not saved and are cleared when the server stops. Sensors and
looping computers re-publish constantly, so values reappear as soon as things are running again. For
data that must persist, use the computer's [disk](#api-reference).

# Reading Any Mod or Addon

The Sensor reads blocks generically, with no mod-specific code. That means you can read machines from
Create addons and from completely unrelated mods.

## Three universal layers

When a Sensor scans a block, it gathers data from several providers. Three of them work for any mod:

1. **Capabilities.** If a block exposes the standard NeoForge capabilities, you get them with no special
   handling:
   - Item inventory gives `items`, `item_count`, and `slots`.
   - Fluid tanks give `tanks`.
   - Forge Energy gives `energy` and `energy_capacity`.

   Practically every modded machine with an inventory, tank, or energy buffer exposes these.

2. **Blockstate.** Every block's visible state properties appear under `state`, such as `state.powered`,
   `state.lit`, `state.facing`, `state.level`, and custom addon properties. The `analog_output` field
   also appears if the block emits a comparator signal.

3. **NBT**, the universal fallback. This is the important one.

## Custom mod data lives in the NBT

Almost every modded block that holds any state stores it in its block entity NBT, which is how
Minecraft saves it to disk. The Sensor exposes that entire NBT as a nested table under `nbt`. Even if a
mod offers no API, no capability, and no documentation, you can still read its state, because whatever
it persists is sitting right there in `nbt`.

```lua
-- read a field a Create addon stores in NBT (example: a steering wheel's angle)
local t = channel("device")
if t and t.nbt then
  print("angle = " .. tostring(t.nbt.steering_angle))
end
```

## How to discover an unknown block's fields

You never have to guess:

1. **Use the Sensor GUI.** Mount the Sensor on the block and open it. The live tree shows every field,
   including the full `nbt` subtree. Click the arrow to expand `nbt` and read off the exact key names
   and their current values.
2. **Or dump it in Lua** with `pairs`:

```lua
local t = channel("device")
if t and t.nbt then
  for key, value in pairs(t.nbt) do
    print(key .. " = " .. tostring(value))
  end
end
```

## Things to know about `nbt`

- The key names and structure are defined by each mod and are not standardized. Inspect them with the
  GUI tree or `pairs` to learn them. Once you know a field name, read it each scan.
- Numbers come through as Lua numbers, nested compounds become tables, and lists become arrays.
- It reflects what the block persists to disk. Purely visual or client-only effects may not be in NBT,
  but anything the machine actually saves (progress, contents, modes, fuel, angles, and so on) is.
- For common data such as items, fluids, and energy, prefer the dedicated fields `items`, `tanks`, and
  `energy`, which are cleaner. Reach into `nbt` for the mod-specific extras.

> Between capabilities, blockstate, and NBT, you can read the state of virtually any block from any mod
> or Create addon, with no integration code required.

# Patterns and Recipes

Reusable shapes for real programs. They assume Sensors and Receivers are set to the named channels.

## The standard control loop (read, decide, act)

```lua
while true do
  local t = channel("furnace")            -- a sensor publishes here
  local fuel = (t and t.energy) or 0
  if fuel < 1000 then emit("refuel", true)
  else emit("refuel", false) end          -- a receiver on "refuel" runs a machine
  sleep(0.25)
end
```

## Edge detection (act once when a value changes)

```lua
local was = false
while true do
  local on = (channel("lever") == true)
  if on and not was then print("just turned on!") end
  was = on
  sleep(0.1)
end
```

## Hysteresis (no flicker around a threshold)

```lua
local pumping = false
while true do
  local t = channel("tank")
  local amt = (t and t.tanks and t.tanks[1]) and t.tanks[1].amount or 0
  if amt < 1000 then pumping = true end   -- turn on when low
  if amt > 9000 then pumping = false end  -- turn off when high
  emit("pump", pumping)
  sleep(0.2)
end
```

## Persistent counter (survives reboot)

```lua
local n = disk.get("runs") or 0
disk.set("runs", n + 1)
print("run #" .. (n + 1))
```

## GPS: broadcast a position, navigate toward it

```lua
-- BASE computer (stationary): announce its location forever
while true do emit("base", getLocation()); sleep(1) end
```

```lua
-- TRAVELLER computer: distance to the base
local me = getLocation()
local dest = channel("base")
if dest then
  local dx, dz = dest.x - me.x, dest.z - me.z
  print(string.format("distance %.1f blocks", math.sqrt(dx*dx + dz*dz)))
end
```

## Computer-to-computer messaging

```lua
-- computer A
emit("inventory", { online = true, items = 42 })

-- computer B
local s = channel("inventory")
if s and s.online then print("A has " .. s.items .. " items") end
```

# Worked Examples

Complete, copy-pasteable programs.

## Wireless auto-restocker

A Sensor on a chest publishes to channel `stock`. When the chest gets low, the computer turns on channel
`refill`, which a Receiver uses to drive a Create machine that tops it up.

```lua
print("auto-restocker online")
while true do
  local s = channel("stock")              -- whole readings table from the sensor
  local count = (s and s.item_count) or 0
  if count < 10 then
    emit("refill", 15)                    -- low -> signal on
    print("LOW (" .. count .. ") refilling")
  else
    emit("refill", 0)                     -- enough -> signal off
  end
  sleep(0.5)
end
```

## Tank guard with status display

A Sensor on a tank publishes to channel `boiler`. The computer keeps the tank between 20% and 80% and
reports status to another computer over channel `report`.

```lua
local function pct(tk) return tk.capacity > 0 and (tk.amount / tk.capacity * 100) or 0 end

local filling = false
while true do
  local t = channel("boiler")
  local tk = t and t.tanks and t.tanks[1]
  if tk then
    local p = pct(tk)
    if p < 20 then filling = true end
    if p > 80 then filling = false end
    emit("valve", filling)                 -- receiver opens a valve
    emit("report", { fluid = tk.fluid, percent = math.floor(p), filling = filling })
    print(string.format("%s %d%% %s", tk.fluid, p, filling and "(filling)" or ""))
  end
  sleep(0.5)
end
```

## Overstress alarm

A Sensor on any Create kinetic block publishes to channel `drive`. Flash a lamp (a Receiver on
`alarm`) when the network is overstressed.

```lua
while true do
  local d = channel("drive")
  if d and d.overstressed then
    emit("alarm", 15); sleep(0.3)
    emit("alarm", 0);  sleep(0.3)
  else
    emit("alarm", 0); sleep(0.5)
  end
end
```

# Troubleshooting and FAQ

## My program does nothing and the state is OFF
The computer has no power. Drive its central cogwheel (mesh a cogwheel, or run a shaft into its
horizontal axis), or supply Forge Energy. Check the state shown on the screen.

## It shows FINISHED immediately
Your program ran to the end with no loop. Wrap the logic in `while true do ... sleep(...) end`.

## It shows ERROR
Read the message in the Console. It includes the line number. The most common causes are:
- Indexing nil: `t.foo.bar` when `t.foo` is `nil`. Guard with `if t.foo then`.
- Concatenating nil: `"x=" .. t.foo` when `t.foo` is `nil`. Use `tostring(t.foo)`.

## It keeps running after I remove power
It should not. The computer powers off within one tick of losing its supply. If it stays on, something
is still feeding it: a shaft or cogwheel is still turning it, or an FE source is still connected. Check
both power inputs.

## `channel("x")` is nil
Nothing is publishing on that channel yet. Check that the Sensor's channel name matches exactly, that
the Sensor is placed and mounted in the world, and that the publisher is running.

## My Receiver always outputs 0
It is probably wired straight to a Sensor's channel, which carries a table. Route it through the
computer: read the sensor, decide, and `emit` a number or boolean on the Receiver's channel.

## A field I expected is not there
Fields depend on the block. Open the Sensor GUI and look at the live tree to see exactly what that block
exposes. For mod-specific values, expand the `nbt` subtree. See
[Reading Any Mod or Addon](#reading-any-mod-or-addon).

## My variables reset
That is RAM, which is wiped on power loss by design. Use `disk.*` for anything that must persist. Your
code persists in flash.

## Gotchas checklist
- Tables are 1-indexed: use `t[1]`, not `t[0]`.
- `0` and `""` are truthy. Only `nil` and `false` are falsey.
- Always `sleep` inside forever-loops.
- A Sensor always publishes a table, so index into it.
- Use `local` for variables.

# Cheat Sheet

```
print(...)                        write a line to the Console
getLocation()                     -> {x,y,z}  this computer's coordinates
emit(name, value)                 publish any value on a channel
channel(name)                     -> latest value or nil
channels()                        -> array of active channel names
disk.set(key, value)              persistent write (nil = delete)
disk.get(key)                     -> stored value or nil
disk.delete(key)                  remove a key
disk.list()                       -> array of stored keys
disk.clear()                      wipe the disk
sleep(seconds)                    idle (minimum 0.05s)

Lua 5.1: math.* string.* table.* os.time/clock/date, pcall, pairs, ipairs ...
Disabled: io, require, package, load/loadstring, debug, os.execute, collectgarbage
```

```
DATA FLOW
  world -> [Sensor] -> channel -> [Computer] -> channel -> [Receiver] -> world

BLOCKS & ITEMS
  Computer    full block, powered by a cogwheel through its centre (or FE); flash Lua onto it
  Sensor      thin plate on a block face; publishes that block's full reading table
  Receiver    thin plate; turns a channel value into redstone (num->0-15, true->15)
  Controller  handheld; binds keys/mouse/scroll to channels (left-click config, right-click operate)

SENSOR FIELDS (depend on block)
  block  is_air  has_block_entity
  rpm  generated_rpm  overstressed  is_kinetic        (Create kinetic)
  items[]  item_count  slots                          (inventories)
  tanks[]                                              (fluids)
  energy  energy_capacity                              (Forge Energy)
  state{}  analog_output                               (blockstate / comparator)
  nbt{}    <- everything a block entity saves; read any mod's state here
```
