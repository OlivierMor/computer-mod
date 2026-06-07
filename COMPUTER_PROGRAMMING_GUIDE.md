# Computer Mod — Programming Guide (LLM context file)

> **How to use this file:** paste the whole thing into an LLM, then ask it to write a program
> (e.g. "turn on a lamp when a tank is more than half full"). This document fully describes the
> in-game computer, its Lua dialect, every available function, and how it talks to the world. The
> LLM should ONLY use the functions and fields documented here — anything else does not exist.

---

## 0. Core idea (read this first)

The **Computer** is a pure "brain". It runs Lua, does math, stores data, knows its own location, and
talks over **wireless channels**. It does **NOT** directly read or touch neighbouring blocks. All
physical input and output happens through two other blocks:

- **Sensor block** → reads a block it's attached to and **publishes** its readings on a channel.
  (This is how the computer gets *input* from the world.)
- **Receiver block** → reads a channel and **emits redstone**. (This is how the computer *acts* on
  the world — drive machines, lamps, Create gearshifts, etc., with redstone.)

So the data flow is: **world → Sensor → channel → Computer → channel → Receiver → world.**
Computers can also message each other directly over channels.

### Complete function index (every global on the computer)

| Function | Returns | Purpose |
|---|---|---|
| `print(...)` | — | write a line to the computer's Console |
| `getLocation()` | `{x,y,z}` | this computer's own coordinates (GPS) |
| `emit(channel, value)` | — | publish any value on a named channel |
| `channel(name)` | value/nil | latest value on a channel |
| `channels()` | table | array of active channel names |
| `disk.set(key, value)` | — | persistent storage write (survives reboot) |
| `disk.get(key)` | value/nil | persistent storage read |
| `disk.delete(key)` | — | remove a stored key |
| `disk.list()` | table | array of stored keys |
| `disk.clear()` | — | wipe the disk |
| `sleep(seconds)` | — | idle (use inside loops; min 0.05s) |

Plus the standard Lua 5.1 library (`math`, `string`, `table`, base functions, `os.time/clock/date`).
There is **no** `scan`, `redstone`, `setOutput`, `moveItems`, etc. on the computer — that's the
Sensor/Receiver blocks' job.

---

## 1. The computer block

A microcontroller block for the Minecraft mod **Create** (NeoForge 1.21.1):

- You **flash** it with a Lua program (stored permanently in the block).
- When **powered**, it **boots** and runs the program from the top.
- When it loses power, it **halts** and variables (RAM) are wiped. Re-powering boots it fresh.
- Power: a spinning Create shaft on the **bottom** face, and/or Forge Energy (FE). Either works.
- The program runs **continuously on its own thread** — `while true do ... end` loops are fine.
- Run states (status bar): `OFF`, `RUNNING`, `FINISHED` (program returned), `ERROR` (crashed).

---

## 2. The language

**Lua 5.1** (via LuaJ) in a sandbox.

Available: base functions (`print`, `type`, `tostring`, `tonumber`, `pairs`, `ipairs`, `select`,
`error`, `assert`, `pcall`, `setmetatable`, `#`, …), `math.*`, `string.*`, `table.*`, and
`os.time/os.clock/os.date`.

NOT available (sandbox): `io`, files, `os.execute/exit`, `require`, `package`, `load`/`loadstring`,
`debug`, `collectgarbage`, Java access. Don't use these.

Lua reminders: tables are 1-indexed; `..` concatenates; only `nil`/`false` are falsey (`0` is true);
use `local`.

---

## 3. Execution & performance

- The program runs on a worker thread at a **clock speed** (~4M Lua instructions/sec, configurable).
  Pure computation runs at full speed.
- **Instant:** `print`, `getLocation`, `emit`, `channel`, `channels`, and all pure Lua.
- **~1 game tick each:** the `disk.*` functions (they touch saved data on the server thread).
- `sleep(seconds)` pauses without using clock budget; the minimum is one tick (0.05s). Put a `sleep`
  in every forever-loop so it paces itself:
  ```lua
  while true do
    -- read channels, decide, emit
    sleep(0.25)
  end
  ```
- A program with no loop runs once and shows `FINISHED`.

---

## 4. API reference

### Output
- `print(...)` — write a line to the Console tab. Multiple args allowed.

### Location (built-in GPS)
- `getLocation()` → `{ x=, y=, z= }`, this computer's world coordinates. Stationary computers report
  a fixed position; broadcast it on a channel so others can navigate to it.

### Wireless channels — the computer's I/O
- `emit(channelName, value)` — publish a value (number, string, boolean, or table) on a named
  channel. This is how a computer drives Receivers and messages other computers.
- `channel(channelName)` → the latest value on that channel, or `nil` if none. The value's type is
  whatever was published (often a table from a Sensor — see §5).
- `channels()` → array (table) of all active channel names.

### Persistent storage (disk / EEPROM) — survives reboot, power loss, world reload
- `disk.set(key, value)` — store a value (number/string/boolean/table). `nil` deletes the key.
- `disk.get(key)` → the stored value, or `nil`.
- `disk.delete(key)` — remove a key.
- `disk.list()` → array of stored key names.
- `disk.clear()` — wipe the disk.
```lua
local boots = disk.get("boots") or 0
disk.set("boots", boots + 1)
print("booted " .. (boots + 1) .. " times")   -- counts up across reboots
```

### Timing
- `sleep(seconds)` — idle (fractions allowed; minimum one tick = 0.05s).

---

## 5. Sensor readings (the data a Sensor publishes)

A **Sensor** reads the block it faces and publishes a **table** of all its readings. The computer
receives that table via `channel(name)`. **Which keys exist depends on the block** — always guard
with `if t.field then ... end`. The Sensor's GUI shows, live, exactly what it currently sees as a
collapsible tree — click a `▸` row (e.g. `state`, `items`, `nbt`) to drill into its nested fields.

| Field | Type | Meaning |
|---|---|---|
| `block` | string | block id, e.g. `"minecraft:chest"` |
| `is_air` | boolean | true if empty space |
| `has_block_entity` | boolean | true if it has a block entity |
| `rpm` | number | Create rotation speed (kinetic blocks) |
| `generated_rpm` | number | speed it generates if a source |
| `overstressed` | boolean | kinetic network overstressed |
| `item_count` | number | total items (inventories) |
| `slots` | number | number of inventory slots |
| `items` | array | `{ slot, item="<id>", count }` per non-empty slot |
| `tanks` | array | `{ fluid="<id>", amount=<mB>, capacity }` per tank |
| `energy` / `energy_capacity` | number | stored / max Forge Energy |
| `state` | table | blockstate properties, e.g. `state.powered`, `state.facing` |
| `analog_output` | number | comparator output 0–15 (e.g. container fullness) |
| `nbt` | table | the block's full saved data — exposes anything an addon stores even with no labelled field. Explore with `pairs`. |

```lua
-- a Sensor is publishing the whole table on channel "tank"
local t = channel("tank")
if t and t.tanks and t.tanks[1] then
  print("fluid: " .. t.tanks[1].amount .. " mB")
end
```

---

## 6. Channels, Sensor & Receiver in detail

### Channel bus
Server-wide named mailboxes carrying **rich values** (numbers/strings/booleans/tables). Computers,
Sensors and Receivers all share it. `emit` to write, `channel(name)` to read latest.

### Sensor block (input)
Place it **facing a block** (placed against the block you want to read). Right-click to set the
**Channel** it publishes on. It always publishes the **whole readings table**, so `channel("c")` is
a table — index into it (e.g. `channel("c").item_count`). The GUI lists every field it currently
sees with its live value, so you know exactly what's available before writing code.

> To turn a sensor reading into redstone, read it in the computer, decide, and `emit` a number/bool
> on another channel that a **Receiver** listens to. Wiring a Receiver straight to a sensor channel
> gives it a table, which reads as 0.

### Receiver block (output)
Right-click to set a **Channel**. It emits redstone on all sides from the latest value:
number → clamped 0–15, `true` → 15, `false`/`nil` → 0.
```lua
emit("pump", true)   -- receiver outputs 15
emit("pump", 7)      -- receiver outputs 7
emit("pump", false)  -- receiver outputs 0
```

> For plain 0–15 wireless redstone you can also use Create's own **Redstone Link** — it's separate
> from this system. Our channels are for rich data and computer logic.

---

## 7. Patterns

**Read a sensor, drive a receiver (the standard control loop):**
```lua
while true do
  local t = channel("furnace")          -- sensor publishes furnace readings here
  local fuel = (t and t.energy) or 0
  if fuel < 1000 then emit("refuel", true)
  else emit("refuel", false) end        -- receiver on "refuel" runs a machine
  sleep(0.25)
end
```

**Edge detection (act once when a value crosses):**
```lua
local was = false
while true do
  local on = (channel("lever") == true)
  if on and not was then print("turned on!") end
  was = on
  sleep(0.1)
end
```

**Hysteresis (no flicker):**
```lua
local pumping = false
while true do
  local t = channel("tank")
  local amt = (t and t.tanks and t.tanks[1]) and t.tanks[1].amount or 0
  if amt < 1000 then pumping = true end
  if amt > 9000 then pumping = false end
  emit("pump", pumping)
  sleep(0.2)
end
```

**Persistent counter (survives reboot):**
```lua
local n = disk.get("runs") or 0
disk.set("runs", n + 1)
print("run #" .. (n + 1))
```

**GPS / navigation (broadcast a position, navigate toward it):**
```lua
-- BASE computer (stationary): announce its location forever
while true do emit("base", getLocation()); sleep(1) end
```
```lua
-- TRAVELLER computer: distance + bearing to the base
local me = getLocation()
local dest = channel("base")
if dest then
  local dx, dz = dest.x - me.x, dest.z - me.z
  print(string.format("dist %.1f", math.sqrt(dx*dx + dz*dz)))
end
```

**Computer-to-computer messaging:**
```lua
-- computer A
emit("status", { online = true, items = 42 })
-- computer B
local s = channel("status")
if s and s.online then print("A has " .. s.items .. " items") end
```

---

## 8. Errors & debugging

- A crash sets state `ERROR` and prints `error: computer:<line> <message>` to the Console.
- Common causes:
  - Indexing nil: `t.foo.bar` when `t.foo` is nil → guard with `if t.foo then`.
  - Concatenating nil: `"x=" .. t.foo` when nil → use `tostring(t.foo)`.
- `print` liberally and watch the Console. Wrap risky code in `pcall`:
  ```lua
  local ok, err = pcall(function() ... end)
  if not ok then print("caught: " .. err) end
  ```

---

## 9. FAQ / gotchas

- The computer **cannot** read or output to adjacent blocks directly. Use a **Sensor** for input and
  a **Receiver** for output, connected by channels.
- Sensor readings differ per block — **check fields exist** before using them.
- A Sensor always publishes the whole table; `channel(name)` from a sensor is always a table.
- `0` is truthy in Lua; only `nil`/`false` are falsey.
- Variables reset on power loss (RAM); use `disk` for data that must persist; code persists (flash).
- Put `sleep` in every forever-loop.
- A program with no loop runs once and stops (`FINISHED`).

---

## 10. Complete worked example — wireless auto-restocker

Sensor on a chest publishes its readings on channel `stock`. The computer turns on a wireless signal
when the chest is low; a Receiver on channel `refill` drives a machine.

```lua
print("auto-restocker online")
while true do
  local s = channel("stock")             -- whole readings table from the sensor
  local count = (s and s.item_count) or 0
  if count < 10 then
    emit("refill", 15)                   -- low → signal on
    print("LOW (" .. count .. ") refilling")
  else
    emit("refill", 0)                    -- enough → signal off
  end
  sleep(0.5)
end
```

---

### Cheat sheet
```
print(...)                       console output
getLocation()                    -> {x,y,z} own coordinates (GPS)
emit(name, value)                publish any value on a channel
channel(name)                    -> latest value or nil
channels()                       -> array of active channel names
disk.set(key,value) disk.get(key) disk.delete(key) disk.list() disk.clear()
sleep(seconds)                   idle (min 0.05s)

Sensor block  = input  (reads a block -> publishes on a channel)
Receiver block = output (channel value -> redstone)
Computer talks ONLY via channels, disk, print, getLocation.
```
