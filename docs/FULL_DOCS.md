# Computer Mod Wiki

**Computer Mod** is an addon for the Minecraft tech mod **Create** (NeoForge 1.21.1) that adds a
programmable **computer** you write real **Lua** code on, plus two small wall-mounted blocks — a
**Sensor** (input) and a **Receiver** (output) — that connect the computer to the physical world over
**wireless channels**.

This wiki explains the whole mod **from the ground up**: how to program in Lua even if you have never
coded before, every block, every function, and how to read **any block from any other mod or Create
addon**. Nothing is hidden.

## The big idea

The computer is a pure **brain**. It runs Lua, does math, remembers things, and talks over wireless
channels — but it does **not** reach out and touch the blocks next to it. Instead:

- A **Sensor** is stuck to a block and **publishes everything it sees** about that block onto a channel.
- The **computer** reads channels, makes decisions, and **emits** values onto channels.
- A **Receiver** turns a channel value into a **redstone signal** to drive machines, lamps, Create
  gearshifts, pistons — anything redstone can.

```
   world  ──►  [Sensor]  ──►  channel  ──►  [Computer]  ──►  channel  ──►  [Receiver]  ──►  world
              reads a block   (wireless)    your Lua code    (wireless)    emits redstone
```

Computers can also message each other over channels, and store data that survives reboots.

## Works with ANY mod or addon

The Sensor reads blocks **generically** — it has no mod-specific code. It pulls universal NeoForge
data (item inventories, fluid tanks, Forge Energy), every blockstate property, and — most importantly
— the block entity's **entire NBT**. Because almost every modded machine stores its state in NBT, you
can **read the state of practically any block from any mod**, even one with no documented API. See
[Reading Any Mod or Addon](#reading-any-mod-or-addon).

## The "Copy for LLM" button

Click **📋 Copy all docs (Markdown)** at the top to copy this entire wiki as clean Markdown. Paste it
into ChatGPT, Claude, or any LLM, then ask it to write a program (e.g. *"turn on a lamp when a tank is
over half full"*). The doc tells the model exactly which functions exist, so it won't invent ones that
don't.

## Where to next?

- New to all of this? Start with **[Getting Started](#getting-started)**.
- Never coded? Read **[Lua Basics](#lua-basics)**.
- Want the function list? **[API Reference](#api-reference)**.
- Reading a specific machine? **[The Sensor](#the-sensor)** and **[Reading Any Mod or Addon](#reading-any-mod-or-addon)**.

# Getting Started

This page gets a working computer running in about five minutes.

## 1. Place and power the computer

The **Computer** is a full block. Place it down, then give it power on its **bottom** face — it runs on
either of Create's two power types:

- **Rotation (recommended, free):** point a spinning shaft into the **bottom** face. Any non-zero RPM
  works as long as the network isn't overstressed.
- **Electricity (Forge Energy):** connect an FE cable/source from a mod that provides it. The computer
  has an internal battery and sips a little FE each tick while running.

When powered, the computer **boots**; when it loses power, it **halts**.

## 2. Write a program

Right-click the computer to open its screen. There are two tabs:

- **Code** — a full text editor (cursor, selection, copy/paste, Lua syntax highlighting, line numbers).
- **Console** — where `print(...)` output and errors appear.

Type a first program in the Code tab:

```lua
print("Hello from my computer!")
```

## 3. Flash it

Press **Flash** to save the program into the computer permanently (it survives the world being closed).
If the computer is powered, flashing **reboots** it immediately. Switch to the **Console** tab and you'll
see your message.

> A program that just runs top-to-bottom and finishes shows the state **FINISHED**. To keep doing work,
> use a loop (next step).

## 4. Make it do something forever

Most useful programs loop. Always put a `sleep` in a forever-loop so it paces itself:

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

1. **Place a Sensor** against a block you care about (e.g. a chest). It snaps flat to that face like a
   button. Right-click it and set a **Channel** name, e.g. `chest`.
2. **Place a Receiver** somewhere its redstone can drive a machine/lamp. Right-click it and set a
   **Channel** name, e.g. `alarm`.
3. Flash this onto the computer:

```lua
while true do
  local c = channel("chest")              -- the sensor's readings (a table)
  local count = (c and c.item_count) or 0
  if count > 0 then emit("alarm", 15)     -- chest not empty -> redstone ON
  else emit("alarm", 0) end
  sleep(0.5)
end
```

That's the whole loop: **Sensor → channel → Computer → channel → Receiver**. Everything else in this
wiki is detail on top of these pieces.

# The Computer

The computer behaves like a **microcontroller**. You *flash* it with a program; it *runs* whenever it
has power; it *forgets* its variables when power is lost.

## Flashing (the program is permanent)

- Pressing **Flash** stores your Lua source into the block itself (saved with the world).
- Flashing while powered **reboots** the computer cleanly with the new code.
- The flashed code stays until you flash something else — breaking power does **not** erase it.

## Power

The computer accepts **either** power type; whichever is present runs it:

| Source | How | Notes |
|---|---|---|
| **Rotation (Create)** | Spinning shaft into the **bottom** face | Free to run. Needs non-zero RPM and a non-overstressed network. Imposes a small stress load. |
| **Forge Energy (FE)** | FE from a cable/generator into the block | Uses an internal FE battery; drains a little FE per tick, but **only when there's no rotation** (rotation is preferred and free). |

`isPowered = has rotation OR has stored FE`. If neither is present, the computer is **OFF**.

## Boot & halt behaviour

- **Power on (rising edge):** the computer boots a **fresh** runtime from the flashed code — variables
  (RAM) start empty and the Console is cleared. The program runs from the top.
- **Power off:** the computer **halts** immediately, **discards all variables (RAM)**, and *freezes* the
  last Console output on screen for you to read. The next boot clears it.
- **Program finishes:** if your code reaches the end (no loop), it stops at **FINISHED** until the next
  power cycle.

> **RAM vs disk vs flash.** *Flash* = your code (permanent). *Disk* = a key/value store you control that
> also persists (see [API Reference](#api-reference)). *RAM* = your variables while running — wiped on
> power loss. Put anything that must survive into `disk`.

## Run states

The screen shows one of four states:

- **OFF** — no power; not running.
- **RUNNING** — executing your program.
- **FINISHED** — the program returned/ended without error.
- **ERROR** — the program crashed; the message is printed to the Console.

## Performance

The program runs on its **own thread** at a fixed *clock speed* measured in Lua instructions per game
tick (default **200,000 per tick** = ~**4,000,000 per second**, configurable). Pure computation and a
`while true` loop run at full speed — you do **not** need to (and shouldn't) match the 20 ticks/second
game rate for logic. Only `sleep` and world round-trips are tick-bound. See
[Patterns &amp; Recipes](#patterns-recipes) for loop pacing.

## Configuration

Server config file `computermod-common.toml` (NeoForge):

| Key | Default | Meaning |
|---|---|---|
| `maxOpsPerTick` | 200000 | Lua instructions per game tick (×20 = per second). Higher = faster compute, more CPU per running computer. |
| `minRpm` | 1.0 | Minimum absolute RPM for a kinetically-powered computer to run. |
| `feCapacity` | 100000 | Internal Forge Energy buffer size (FE). |
| `fePerTick` | 20 | FE consumed per tick when running on electricity (ignored while kinetically powered). |

# The Sensor

The Sensor is a **thin plate** that mounts flat against a block, just like a button or Create's
Redstone Link. Whatever block it is stuck to is the block it **reads**.

## Placing it

Aim at the face of a block and place the Sensor — it snaps onto that face. If the block it's mounted to
is removed, the Sensor pops off and drops. It can mount on any side (walls, floor, ceiling).

## What it does

The Sensor scans the block it's attached to **every tick** and **publishes the complete readings table
the instant anything changes** — add an item, change a fluid level, flip a blockstate, and it transmits
that **same tick** with no polling delay. (If nothing changed, it stays quiet; the channel already holds
the latest value.) Right-click the Sensor to set the **Channel** name; that's the only setting.

> Because it always publishes the **whole table**, `channel("name")` from a sensor is always a Lua
> **table** — you index into it (e.g. `channel("name").item_count`). It never publishes a bare value.

## The live readings panel

The Sensor's GUI shows, **live**, exactly what it currently sees, as a **collapsible tree**. Container
values like `state`, `items`, and `nbt` show a `▸` arrow — click to expand and drill into their nested
fields. This lets you discover precisely which fields a given block exposes **before** you write any
code. Scroll with the mouse wheel.

## Reading fields

Which fields appear **depends on the block**. Always guard with `if t.field then ... end`. The full set
of possible fields:

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
| `analog_output` | number | block emits a comparator signal | Comparator output 0–15 (e.g. container fullness). |
| `nbt` | table | block has a block entity | The block entity's **entire saved data** as a nested table — the universal fallback. See [Reading Any Mod or Addon](#reading-any-mod-or-addon). |

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

The Receiver is the computer's hands. Like the Sensor it's a **thin plate** mounted against a block
face (it pops off if that block is removed), but it doesn't read anything — it just **outputs redstone**.

## What it does

Set a **Channel** (right-click). The Receiver reads the latest value on that channel and emits a
redstone signal on **all sides**, every tick:

| Channel value | Redstone output |
|---|---|
| a number | clamped to **0–15** |
| `true` | **15** |
| `false`, `nil`, nothing | **0** |
| a numeric string like `"7"` | parsed, clamped to **0–15** |
| anything else (e.g. a table) | **0** |

```lua
emit("pump", true)   -- receiver on "pump" outputs 15
emit("pump", 7)      -- outputs 7
emit("pump", false)  -- outputs 0
```

> **Don't wire a Receiver directly to a Sensor's channel.** A Sensor publishes a *table*, which a
> Receiver reads as 0. The intended flow is Sensor → **Computer decides** → `emit` a number/boolean →
> Receiver. The computer is what turns rich data into a redstone decision.

> For plain 0–15 wireless redstone with no logic, Create's own **Redstone Link** already works and is
> separate from this system. These channels are for **rich data** and computer logic.

# Lua Basics

The computer runs **Lua 5.1**. This page teaches enough Lua to be productive even if you've never
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

> **Truthiness:** only `false` and `nil` are "false". **`0` and `""` are TRUE** in Lua — a common trap.

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

Tables are Lua's one data structure — they're both arrays and key/value maps. **Arrays are
1-indexed.**

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

Sensor readings arrive as tables exactly like these — `t.item_count`, `t.items[1].count`, etc.

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

Everything the computer can call. The computer talks to the world **only** through these — there is no
`scan`, `redstone`, `setOutput`, `moveItems`, etc. (those belong to the Sensor/Receiver blocks).

## Function index

| Function | Returns | Purpose |
|---|---|---|
| `print(...)` | — | Write a line to the Console. Multiple arguments allowed. |
| `getLocation()` | `{x,y,z}` | This computer's own world coordinates (a built-in GPS receiver). |
| `emit(channel, value)` | — | Publish any value (number/string/boolean/table) on a named channel. |
| `channel(name)` | value / nil | The latest value on a channel, or `nil` if none. |
| `channels()` | table | Array of all active channel names. |
| `disk.set(key, value)` | — | Persistent storage write. `nil` value deletes the key. |
| `disk.get(key)` | value / nil | Persistent storage read. |
| `disk.delete(key)` | — | Remove one key. |
| `disk.list()` | table | Array of stored key names. |
| `disk.clear()` | — | Wipe the whole disk. |
| `sleep(seconds)` | — | Idle without using clock budget (minimum one tick = 0.05s). |

## Output

### `print(...)`
Writes a line to the **Console** tab. Accepts multiple arguments (separated by tabs, like standard Lua).
Use it constantly while debugging.

## Location (built-in GPS)

### `getLocation()` → `{ x=, y=, z= }`
Returns this computer's own block coordinates. A stationary computer reports a fixed position; broadcast
it on a channel and other computers can navigate relative to it (see
[Patterns &amp; Recipes](#patterns-recipes)).

## Wireless channels — the computer's I/O

### `emit(channelName, value)`
Publishes a value on a channel. The value can be a number, string, boolean, or a (possibly nested)
table. This is how a computer drives Receivers and messages other computers. Emitting `nil` clears the
channel.

### `channel(channelName)` → value / nil
Reads the **latest** value on a channel, or `nil` if nothing has been published. The type is whatever
was published — often a **table** when the source is a Sensor.

### `channels()` → table
Returns an array of all channel names that currently hold a value.

## Persistent storage (disk)

A key/value store on the computer that **survives reboots, power loss, and world reloads** — unlike RAM
variables. Up to **1024 keys**. Values may be numbers, strings, booleans, or tables.

```lua
local boots = disk.get("boots") or 0
disk.set("boots", boots + 1)
print("booted " .. (boots + 1) .. " times")   -- counts up across reboots
```

- `disk.set(key, value)` — store (or, with `nil`, delete) a value.
- `disk.get(key)` — read a value or `nil`.
- `disk.delete(key)` — remove a key.
- `disk.list()` — array of stored keys.
- `disk.clear()` — wipe everything.

> `disk.*` calls do a quick round-trip to the server thread, so they take about one game tick each.
> Read/write in bulk outside tight inner loops where you can.

## Timing

### `sleep(seconds)`
Pauses the program without burning clock budget. Fractions are allowed but the minimum real wait is one
game tick (**0.05s**); `sleep(0.02)` still waits one tick. Put a `sleep` in every forever-loop.

## The Lua sandbox

You get **Lua 5.1** with the standard libraries that make sense in a game:

**Available:** base functions (`print`, `type`, `tostring`, `tonumber`, `pairs`, `ipairs`, `next`,
`select`, `error`, `assert`, `pcall`, `xpcall`, `setmetatable`, `getmetatable`, `rawget`, `rawset`,
`unpack`, `#`, …), **`math.*`**, **`string.*`**, **`table.*`**, and **`os.time` / `os.clock` /
`os.date`**.

**Disabled for safety** (do not use — they are `nil`): `io`, file access, `os.execute`/`os.exit`,
`require`, `package`, `load` / `loadstring` / `dofile` / `loadfile`, `debug`, `collectgarbage`, and any
Java access (`luajava`).

# Channels

Channels are the wireless backbone that ties everything together.

## What they are

A channel is a **server-wide named mailbox** holding a single **latest value**. Computers, Sensors, and
Receivers all share the same set of channels by name. Pick any name you like (`"tank"`, `"alarm"`,
`"base-coords"`).

- **`emit(name, value)`** (computer) or a **Sensor** writes the latest value.
- **`channel(name)`** (computer) or a **Receiver** reads the latest value.

## Rich values, not just 0–15

Unlike vanilla/Create redstone (a single 0–15 number), a channel can carry a **number, string, boolean,
or a whole nested table**. That's what lets a Sensor hand the computer a full inventory listing, and
lets computers send each other structured messages.

```lua
emit("status", { online = true, items = 42, name = "sorter-3" })
-- elsewhere:
local s = channel("status")
if s and s.online then print(s.name .. " has " .. s.items) end
```

## Latest-value, not a queue

A channel remembers only the **most recent** value. If you publish faster than someone reads, earlier
values are simply overwritten. For control logic this is exactly what you want (you care about the
*current* state).

## Lifetime

Channels are **runtime state**: they are **not saved** and are **cleared when the server stops**.
Sensors and looping computers re-publish constantly, so values reappear as soon as things are running
again. For data that must persist, use the computer's [disk](#api-reference).

# Reading Any Mod or Addon

One of the most powerful things about this mod: the Sensor reads blocks **generically**, with **zero
mod-specific code**. That means you can read machines from Create addons and completely unrelated mods.

## Three universal layers

When a Sensor scans a block, it gathers data from up to five providers. Three of them work for *any*
mod:

1. **Capabilities** — if a block exposes the standard NeoForge capabilities, you get them with no
   special handling:
   - **Item inventory** → `items`, `item_count`, `slots`
   - **Fluid tanks** → `tanks`
   - **Forge Energy** → `energy`, `energy_capacity`

   Practically every modded machine with an inventory, tank, or energy buffer exposes these.

2. **Blockstate** — every block's visible state properties appear under `state` (e.g. `state.powered`,
   `state.lit`, `state.facing`, `state.level`, custom addon properties). Also `analog_output` if the
   block emits a comparator signal.

3. **NBT — the universal fallback.** This is the key one.

## Custom mod data is ALWAYS in the NBT

Almost every modded block that has any state stores it in its **block entity NBT** (that's how Minecraft
saves it to disk). The Sensor exposes that entire NBT as a nested table under **`nbt`**. So even if a
mod offers **no API, no capability, and no documentation**, you can still read its state — because
whatever it persists is sitting right there in `nbt`.

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
   including the full `nbt` subtree — click `▸` to expand `nbt` and read off the exact key names and
   their current values.
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

- The **key names and structure are defined by that mod**, not standardized — inspect with the GUI tree
  or `pairs` to learn them. Once you know a field name, just read it each scan.
- Numbers come through as Lua numbers; nested compounds become **tables**; lists become **arrays**.
- It reflects what the block **persists to disk**. Purely visual/client-only effects might not be in
  NBT, but anything the machine actually saves (progress, contents, modes, fuel, angles, etc.) is.
- For common things (items/fluids/energy) prefer the dedicated fields (`items`, `tanks`, `energy`) —
  they're cleaner. Reach into `nbt` for the mod-specific extras.

> **Bottom line:** between capabilities, blockstate, and NBT, you can read the state of virtually any
> block from any mod or Create addon — no integration code required.

# Patterns & Recipes

Reusable shapes for real programs. All of them assume Sensors/Receivers are set to the named channels.

## The standard control loop (read → decide → act)

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

Sensor on a tank → channel `boiler`. Computer keeps the tank between 20% and 80% and reports status to
another computer over channel `report`.

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

Sensor on any Create kinetic block → channel `drive`. Flash a lamp (Receiver on `alarm`) when the
network is overstressed.

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

# Troubleshooting & FAQ

## My program does nothing / state is OFF
The computer has no power. Put a spinning shaft into the **bottom** face, or supply Forge Energy. Check
the state shown on the screen.

## It shows FINISHED immediately
Your program ran to the end with no loop. Wrap the logic in `while true do ... sleep(...) end`.

## It shows ERROR
Read the message in the **Console** — it includes the line number. Most common causes:
- **Indexing nil:** `t.foo.bar` when `t.foo` is `nil`. Guard with `if t.foo then`.
- **Concatenating nil:** `"x=" .. t.foo` when `t.foo` is `nil`. Use `tostring(t.foo)`.

## `channel("x")` is nil
Nothing is publishing on that channel yet. Check the Sensor's channel name matches exactly, that the
Sensor is powered into the world (it's placed and mounted), and that the publisher is running.

## My Receiver always outputs 0
It's probably wired straight to a **Sensor's** channel, which carries a *table*. Route it through the
computer: read the sensor, decide, and `emit` a number/boolean on the Receiver's channel.

## A field I expected isn't there
Fields depend on the block. Open the **Sensor GUI** and look at the live tree to see exactly what that
block exposes. For mod-specific values, expand the `nbt` subtree — see
[Reading Any Mod or Addon](#reading-any-mod-or-addon).

## My variables reset
That's RAM — it's wiped on power loss by design. Use `disk.*` for anything that must persist; your code
persists in flash.

## Gotchas checklist
- Tables are **1-indexed** (`t[1]`, not `t[0]`).
- **`0` and `""` are truthy**; only `nil`/`false` are falsey.
- Always `sleep` inside forever-loops.
- A Sensor always publishes a **table** — index into it.
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

BLOCKS
  Computer  full block, powered from bottom (shaft) or FE; flash Lua onto it
  Sensor    thin plate on a block face; publishes that block's full reading table
  Receiver  thin plate; turns a channel value into redstone (num->0-15, true->15)

SENSOR FIELDS (depend on block)
  block  is_air  has_block_entity
  rpm  generated_rpm  overstressed  is_kinetic        (Create kinetic)
  items[]  item_count  slots                          (inventories)
  tanks[]                                              (fluids)
  energy  energy_capacity                              (Forge Energy)
  state{}  analog_output                               (blockstate / comparator)
  nbt{}    <- everything a block entity saves; read ANY mod's state here
```
