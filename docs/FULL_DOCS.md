# Computer Mod

Computer Mod is an addon for the Create tech mod (NeoForge 1.21.1). It adds a real, programmable
computer to Minecraft. You write a small program for it in a language called Lua, and the computer runs
that program to watch your machines and make decisions automatically.

It comes with four parts:

- A **Computer**: the brain that runs your program.
- A **Sensor**: reads a block (a chest, a tank, a Create machine, almost anything) and reports what it
  sees.
- A **Receiver**: turns a decision from your program into a redstone signal that drives machines.
- A **Controller**: a handheld item that lets you, the player, drive the same system by hand.

You do not need to know how to code to use this mod. The whole system is designed so you can describe
what you want in plain English to an AI like ChatGPT or Claude, paste in the program it writes, and run
it. See [Coding with AI](#coding-with-ai). If you do want to learn, [Lua Basics](#lua-basics) starts
from zero.

## What you can build

- An alarm that flashes a lamp when a chest is nearly full or a tank runs low.
- A farm or factory that turns machines on and off based on how much they have produced.
- A smart vehicle or contraption you steer with the keyboard, like a plane, train, or rover.
- A dashboard where one computer collects readings from many machines and reports a summary.
- A network of computers spread across your base that message each other.

## The model

The computer is purely a brain. It runs Lua, does math, stores data, and talks over named channels. It
deliberately does not reach out and touch the blocks around it. The other parts handle the physical
world:

```
  world  ->  [Sensor]  ->  channel  ->  [Computer]  ->  channel  ->  [Receiver]  ->  world
            reads a block   wireless     your Lua code   wireless     emits redstone
```

A **channel** is just a named wireless slot that carries a value. Sensors and the Controller write to
channels, the computer reads and writes them, and Receivers read them. Everything in the mod is built
on this one idea. The next page, [How It Works](#how-it-works), explains why it is designed this way,
because understanding the model makes everything else obvious.

## Works with other mods

The Sensor reads blocks generically, with no mod-specific code. It pulls standard NeoForge data (item
inventories, fluid tanks, Forge Energy), every blockstate property, and the block's full saved data
(NBT). Because almost every modded machine stores its state in NBT, you can read practically any block
from any mod, even one with no documented API. See [Reading Any Mod or Addon](#reading-any-mod-or-addon).

## Where to next

- Want to understand the idea first: read [How It Works](#how-it-works).
- Want to build something now: jump to [Getting Started](#getting-started).
- Not a programmer: read [Coding with AI](#coding-with-ai).
- Want the full function list: see [API Reference](#api-reference).

# How It Works

This page explains the ideas behind the mod. None of it is required to build your first machine, but
once these four ideas click, everything else in this documentation becomes obvious. Each section
explains how a part behaves and why it was designed that way.

## Idea 1: brain, senses, and hands are separate

A single all-in-one "smart block" would have to know about every other mod to be useful. Create alone
has hundreds of machines, and there are thousands of mods. That approach cannot scale.

Instead this mod splits the job into three roles, like a living thing:

- **Senses** (the Sensor) read the world and report raw facts.
- A **brain** (the Computer) thinks about those facts and decides what to do.
- **Hands** (the Receiver) carry out the decision as a redstone signal.

> [!WHY]
> Splitting the roles is what lets the computer stay simple and universal. The computer never needs to
> know what a chest or a pump or a modded reactor is. It only ever sees plain values like numbers and
> text on channels. All the messy work of understanding a specific block is done once, generically, by
> the Sensor. Add a brand new mod tomorrow and the same computer can already read it.

## Idea 2: channels are a shared noticeboard

The parts never connect to each other with wires. They communicate through **channels**. A channel is
a named slot on a server-wide noticeboard. Anyone can pin the latest value to a name, and anyone can
read the latest value off that name.

```
  Sensor writes ->  [ "tank" = { fluid="water", amount=6000 } ]  <- Computer reads
  Computer writes-> [ "pump" = true ]                            <- Receiver reads
```

A channel holds only the most recent value. If something writes to a channel twice, the new value
replaces the old one. There is no queue and no history.

> [!WHY]
> A noticeboard decouples the parts. The Sensor does not need to know who is listening, and the
> computer does not need to know where its commands end up. You can add a second Receiver on the same
> channel and it just works. Keeping only the latest value is exactly what control logic wants: when
> you ask "is the tank full right now?", you want the current answer, not a backlog of old readings.
> Channels can also carry rich values (whole tables of data), which is far more than the single 0 to 15
> number that normal redstone can send. See [Channels](#channels) for the details.

## Idea 3: the computer is a microcontroller

The computer behaves like a real microcontroller, the kind of tiny chip inside a thermostat or a
washing machine. That comparison drives three rules that surprise people at first:

- **You flash a program onto it.** Flashing burns your code permanently into the chip, like firmware.
  It stays there forever, even through power cuts and world reloads, until you flash something else.
- **Power only decides whether it is running, not what it runs.** Give it power and it boots up and
  runs the flashed program from the top. Cut the power and it stops instantly.
- **It forgets everything when it loses power.** Any variables your program created while running are
  wiped the moment power is lost, just like the working memory of a real chip. The program itself is
  not lost, only its temporary thoughts.

> [!WHY]
> This split between permanent code and temporary memory is what makes the computer predictable. Every
> time it powers on, it starts from a known, clean state and runs the same program the same way. There
> is no half-finished state left over from last time to cause mystery bugs. If you need something to
> survive a power cut on purpose, you save it to the [disk](#api-reference), which is separate, permanent
> storage. The three kinds of memory are explained on [The Computer](#the-computer) page.

## Idea 4: your program runs on its own clock

When your program loops, it does not run at Minecraft's speed of 20 steps per second. It runs on its
own much faster clock, on its own thread, capped at a fixed number of Lua instructions per game tick
(200,000 by default, about four million per second). Minecraft never waits for your program, and your
program never freezes the game, no matter how busy its loop is.

> [!WHY]
> Running on a separate clock is what lets you write a normal `while true do ... end` loop without
> lagging the server. The computer is throttled automatically, so a runaway loop can never hang
> Minecraft. The one thing to remember is that reading and acting on the world happens in step with the
> game, once per tick, so there is no point spinning faster than that. You pace your loops with
> `sleep`, which the next pages explain. The full timing model is on [The Computer](#the-computer).

## Putting it together

Every project in this mod is the same shape: a Sensor (or a Controller) puts facts on a channel, the
computer reads them and decides, and a Receiver turns the decision into action. Once you see that
shape, read [Getting Started](#getting-started) to build it for real.

# Getting Started

This page gets a working computer running in about five minutes. If you have read
[How It Works](#how-it-works), the steps will make sense. If not, you can still follow along and the
pieces will fit together by the end.

All four items are in the creative tab named Computer Mod: the Computer, Sensor, Receiver, and
Controller.

## 1. Place and power the computer

The Computer is a full block with a cogwheel running horizontally through its centre, like Create's
Encased Cogwheel. Place it, then power it through that cogwheel. It accepts either of Create's two
power types:

- **Rotation (recommended, free):** drive the central cogwheel. Mesh another cogwheel with it from the
  side, or run a shaft into either end of its horizontal axis. Any non-zero RPM works while the network
  is not overstressed.
- **Electricity (Forge Energy):** connect an FE cable or generator from a mod that provides one.

When powered, the computer boots. When power is removed, it halts instantly. See
[The Computer](#the-computer) for the full power model.

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

> [!TRY]
> Place a computer, power its cogwheel, open it, type the `print` line above, and press Flash. Open the
> Console tab. You should see `Hello from my computer!`. Now break the cogwheel's power and the state
> changes to OFF. Restore power and it prints again. That is a full boot.

> [!NOTE]
> A program that runs top to bottom and ends shows the state FINISHED. It is done, not broken. To keep
> working, a program needs a loop, shown next.

## 4. Make it run continuously

Most useful programs loop forever. Always put a `sleep` inside a forever-loop so it runs at a sensible
pace instead of as fast as possible:

```lua
print("counting...")
local n = 0
while true do
  n = n + 1
  print("tick " .. n)
  sleep(1)        -- wait 1 second between counts
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

> [!TRY]
> Build the three blocks above with a redstone lamp next to the Receiver. Put one item in the chest:
> the lamp turns on. Empty the chest: the lamp turns off. You just built the full loop, Sensor to
> channel to Computer to channel to Receiver.

That shape, read a channel then decide then emit, is the heart of almost every program in this mod. The
rest of this site builds on it. If you would rather have an AI write these programs for you, read
[Coding with AI](#coding-with-ai) next.

# Coding with AI

You do not have to learn Lua to use this mod. This documentation is written so that an AI assistant
like ChatGPT, Claude, or Gemini can read it and write correct programs for you. This page shows the
workflow.

## Why this works

A general AI does not know about this mod, so on its own it would invent functions that do not exist.
This site solves that by handing the AI the exact, complete list of everything the computer can do.
Given that list, the AI writes programs using only real functions.

## Step 1: give the AI the documentation

At the top of this page is a **Copy all docs** button. Click it. That copies this entire site as plain
text. Paste it into a new chat with your AI as the first message, with a line like:

```
Here is the full documentation for a Minecraft mod called Computer Mod. Read it.
I will then ask you to write Lua programs for it. Only use functions that appear
in this documentation. Do not invent any others.
```

## Step 2: describe your setup, then ask

The AI cannot see your world, so tell it what you have built and what each channel is. Be concrete
about block names, channel names, and what you want to happen.

> [!TIP]
> A good request names the channels and the goal. For example:
>
> "I have a Sensor on a fluid tank publishing to the channel `boiler`. I have a Receiver on the channel
> `pump` that runs a water pump. Write a program that keeps the tank between 20% and 80% full."

The AI will reply with a block of Lua. It can explain it line by line if you ask.

## Step 3: flash it and run it

Copy the AI's program, open the computer, paste it into the Code tab, and press Flash. Make sure the
computer is powered. Watch the Console tab and your machines.

## Step 4: when something is wrong, paste it back

If the computer shows ERROR, open the Console, copy the error message, and paste it back to the AI:

```
The computer shows this error: [paste the red error line here]. Please fix the program.
```

If it runs but does the wrong thing, describe what you see: "the pump never turns off" or "the lamp
flickers". The AI will adjust the code. This back and forth is normal and is how real programmers work
too.

## Tips for good results

- **Discover field names first.** Different blocks expose different data. Put a Sensor on the block, open
  its screen, and read the live list of fields (see [The Sensor](#the-sensor)). Tell the AI the exact
  field names you see, such as `item_count` or `tanks`.
- **Ask for comments.** Request that the AI add short comments so you can follow what each part does.
- **Change one thing at a time.** If you want a tweak, ask for that one change rather than describing the
  whole program again.
- **Keep the docs in the chat.** Stay in the same conversation so the AI still remembers the function
  list. If you start a fresh chat, paste the docs again.

> [!NOTE]
> The AI writes the program, but you still place the blocks, set the channel names, and supply power.
> Think of the AI as the programmer and yourself as the engineer wiring up the machine.

# The Computer

The computer is a full block that runs your Lua program. It behaves like a microcontroller, the small
chip found inside everyday devices. That single comparison, introduced on [How It Works](#how-it-works),
explains everything on this page: how flashing works, why power matters, what it remembers, and how fast
it runs.

## Flashing: your code is firmware

Open the computer (right-click) and you get two tabs. **Code** is a text editor with syntax
highlighting and line numbers. **Console** shows whatever your program prints, and any errors. You write
a program in the Code tab and press **Flash**.

Flashing copies your program into the block itself. Think of it as burning firmware onto a chip:

- The program is stored in the block and saved with the world. It survives the computer losing power,
  the chunk unloading, and you quitting and reloading the game.
- It stays exactly as flashed until you flash a different program over it. Power has no effect on it.
- Flashing while the computer is powered reboots it immediately with the new code, so you see your
  changes right away.

> [!WHY]
> Code and power are kept separate on purpose. A real device does not forget its firmware every time
> you unplug it, and neither does this. Flashing is a deliberate "save my program" action, while power
> is just an on and off switch. This is why you can build a machine, flash it once, and trust it to run
> the same way every time it powers on.

## Three kinds of memory

People are often surprised that a computer "forgets" things, so it is worth being precise. The computer
has three separate stores, and they behave differently:

| Store | What it holds | Survives power loss? | How you use it |
|---|---|---|---|
| Flash | Your program (the code) | Yes | Press Flash |
| Disk | Data you choose to save | Yes | `disk.set` / `disk.get` (see [API Reference](#api-reference)) |
| RAM | The variables your program makes while running | No | Normal `local` variables |

RAM is wiped every time power is lost. That is by design and matches real hardware: working memory is
volatile. If a value must outlive a power cut, such as a counter or a saved setting, write it to the
disk. The disk is permanent storage you control, holding up to 1024 named values.

> [!TIP]
> Rule of thumb: code goes in flash automatically when you press Flash. Anything you want to remember
> across reboots goes in the disk. Everything else lives in RAM and resets on each boot, which is usually
> exactly what you want.

## Power

The computer is the only block here that needs power. It runs while it has either of Create's two power
types:

| Source | How to supply it | Notes |
|---|---|---|
| Rotation (Create) | Drive the central cogwheel: mesh a cogwheel from the side, or run a shaft into either end of its horizontal axis | Recommended and free to run. Needs non-zero RPM and a network that is not overstressed. Adds a small stress load. |
| Forge Energy (FE) | Feed FE from a cable or generator from a mod that provides one | Consumes a fixed amount of FE per tick, and only when there is no rotation. Rotation is preferred and free. |

The computer is powered when the cogwheel is turning or FE is actively flowing in. If neither is true,
it is OFF.

## Power loss is immediate

The computer runs only while power is actually being supplied, exactly like a chip losing its supply
voltage. It keeps a tiny internal FE buffer to smooth the incoming current, but that buffer is not a
battery and cannot run the computer on its own. Stop the cogwheel or disconnect the FE source and the
computer powers down within a single game tick. It never coasts on stored charge.

> [!WHY]
> An immediate, clean cut-off is what makes power a reliable on and off switch. If the computer kept
> running for a while on leftover charge, you could never use a redstone-controlled clutch or a lever as
> a real "stop" button. Because shutdown is instant and wipes RAM, cutting power is also the simplest way
> to force a fresh restart.

## The boot and halt cycle

Each time power arrives or leaves, a clear cycle runs:

- **Power on (boot):** the computer starts a fresh copy of your flashed program. RAM is empty and the
  Console is cleared. Execution begins at the first line.
- **While powered (running):** your program runs on its own clock (described below).
- **Power off (halt):** the program stops at once and RAM is discarded. The last Console output is
  frozen on screen so you can still read it. The next boot clears that frozen output.

## Run states

The screen always shows one of four states:

- **OFF:** no power, not running.
- **RUNNING:** your program is executing, usually a forever-loop.
- **FINISHED:** the program reached its end with no error. A program with no loop finishes in an instant.
  This is success, not a fault.
- **ERROR:** the program hit a mistake and stopped. The red error message, including the line number, is
  printed to the Console. See [Troubleshooting and FAQ](#troubleshooting-and-faq).

## How fast your program runs

This is the part that most affects how you write programs, so it is worth understanding.

Your program runs on its own background thread, on a fixed clock. The clock allows a set number of Lua
instructions per game tick, 200,000 by default. A game tick is one twentieth of a second, so that is
about four million instructions per second. Minecraft runs at 20 ticks per second no matter what, and
your computer's clock is granted once per tick.

What this means in practice:

- **Pure calculation is fast.** Math, loops over tables, and building text all run at clock speed. You do
  not need to spread work across ticks by hand.
- **The game never waits for you, and you never freeze the game.** If your loop is busy, the clock simply
  throttles it. A runaway `while true do end` with no `sleep` cannot hang the server.
- **Touching the world happens once per tick.** Sensors update their channels once per tick, and a
  Receiver reads its channel once per tick. Reading a channel in Lua is cheap and instant. Disk
  operations (`disk.get`, `disk.set`, and so on) each cost about one tick, because they reach back into
  the saved world. The computer handles up to 64 such disk operations per tick.

### Why you should always sleep in a loop

```lua
while true do
  -- check something and act
  sleep(0.25)        -- pause a quarter second each pass
end
```

> [!WHY]
> Without a `sleep`, a forever-loop runs as fast as the clock allows, burning the computer's whole
> instruction budget every tick to repeat work that only changes once per tick anyway. It will not crash
> anything, but it wastes performance for no benefit, since the sensors it reads do not update faster
> than once per tick. `sleep` hands the rest of the time back, so the computer uses almost no
> performance while idle and your logic runs at a sensible, steady pace. A quarter to a half second is a
> good default for most control loops. Use a shorter sleep only when you genuinely need a faster
> reaction.

`sleep(seconds)` always waits at least one tick (0.05 seconds), because that is the finest step the game
has. `sleep(0.01)` still waits one full tick.

## Limits

- The Console keeps the most recent 200 lines. Older lines scroll off.
- The disk holds up to 1024 named values.
- The instruction budget per tick is set by config (below).

## Configuration

Server config file `computermod-common.toml` (NeoForge):

| Key | Default | Meaning |
|---|---|---|
| `maxOpsPerTick` | 200000 | Lua instructions per game tick (times 20 for the per second rate). Higher means faster computation but more server load per running computer. |
| `minRpm` | 1.0 | Minimum RPM for a kinetically powered computer to run. |
| `feCapacity` | 100000 | Size of the internal FE smoothing buffer, in FE. |
| `fePerTick` | 20 | FE consumed per tick while running on electricity. Ignored while powered by rotation. |

# The Sensor

The Sensor is a thin plate that mounts flat against a block, like a button or Create's Redstone Link.
Whatever block it is attached to is the block it reads.

## Placing it

Aim at the face of a block and place the Sensor. It snaps onto that face. If the block it is mounted to
is removed, the Sensor pops off and drops. It mounts on any side, including walls, floor, and ceiling.

## What it does

The Sensor scans the block it is attached to every tick and publishes one big table of readings to its
channel. It publishes the instant anything changes: add an item, change a fluid level, or flip a
blockstate, and the new readings go out that same tick with no delay. If nothing changed, it stays
quiet, because the channel already holds the latest value. Right-click the Sensor to set its channel
name, which is its only setting.

> [!WHY]
> The Sensor reads every block the same generic way, with no special code for any particular mod. It
> gathers standard data that almost all blocks expose (inventories, tanks, energy, blockstate) plus the
> block's entire saved data. This is the trick that lets one simple block read a chest, a Create machine,
> and a reactor from some mod you have never heard of, all identically. The cost is that you get a table
> of many fields rather than a single number, so your program picks out the field it cares about.

Because it always publishes the whole table, `channel("name")` from a Sensor is always a Lua table. You
index into it, for example `channel("name").item_count`. A Sensor never publishes a bare value.

## The live readings panel

The Sensor's screen shows, live, exactly what it currently sees, as a collapsible tree that refreshes a
couple of times a second. Fields that contain more data, such as `state`, `items`, and `nbt`, show an
arrow. Click it to expand and read the nested values inside. Scroll with the mouse wheel.

> [!TRY]
> Before writing any program, mount a Sensor on the block you want to read and open its screen. Browse
> the tree to learn the exact field names that block offers. These names are what you use in code, and
> what you tell an AI when [coding with AI](#coding-with-ai). Different blocks expose different fields,
> so this is the fastest way to find out what is available.

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

Right-click to set a channel. Every tick the Receiver reads the latest value on that channel, converts
it to a redstone strength from 0 to 15, and emits that signal on all sides. Its built-in LED lights up
and glows brighter as the signal rises, so you can see the output at a glance.

The conversion is simple:

| Channel value | Redstone output |
|---|---|
| a number | clamped to 0 through 15 |
| `true` | 15 |
| `false`, `nil`, or nothing | 0 |
| a numeric string like `"7"` | parsed, then clamped to 0 through 15 |
| anything else, such as a table | 0 |

```lua
emit("pump", true)   -- a Receiver on "pump" outputs 15
emit("pump", 7)      -- outputs 7
emit("pump", false)  -- outputs 0
```

> [!TRY]
> Place a Receiver, set its channel to `test`, and put a redstone lamp beside it. Flash a computer with
> `emit("test", 15)`. The lamp lights. Change it to `emit("test", 0)` and reflash: the lamp goes dark.
> No wires between the computer and the Receiver, only the shared channel name.

> [!NOTE]
> Do not point a Receiver at a Sensor's channel directly. A Sensor publishes a table, and a Receiver
> reads any table as 0. The intended path is Sensor to Computer to Receiver: the computer reads the rich
> sensor data, decides, and emits a plain number or boolean that the Receiver can turn into redstone.

> [!TIP]
> If all you want is plain wireless redstone of 0 to 15 with no logic, Create's own Redstone Link
> already does that and is separate from this mod. These channels exist for rich data and computer
> decisions.

# The Controller

The Controller is a handheld item that lets you, the player, write to channels by hand using your
keyboard and mouse. Where a Sensor turns a block into channel values automatically, the Controller turns
your key presses and scroll wheel into channel values directly. Because it writes to the same channels
as everything else, anything that reads a channel (a Receiver, or a running program) responds to your
input live.

## What it is for

- **Driving vehicles and contraptions.** Bind keys to the channels that control a Create contraption and
  steer it in real time: a plane, a train, a rover, an elevator, a crane. Your keys move the channels,
  Receivers turn those into redstone, and the contraption responds.
- **Manual override.** Keep a key bound to the same channel a program drives, so you can take control of
  a machine by hand when you want to.
- **Testing without code.** Flip a channel on and off yourself to check that a Receiver, lamp, or machine
  is wired correctly, before you write any program at all.
- **Live tuning.** Use the scroll wheel to feed a changing number (a speed, a height, a threshold) into a
  running program and watch the effect immediately.

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
- A bound input's normal game function is suppressed, so a bound W key no longer walks you forward, it
  only drives its channel.
- Every input you did not bind keeps working as normal.
- A small display in the corner lists each binding and its live value.

Right-click again to stop. Switching away from the item also stops operating. Either way, held channels
are released and your suppressed keys go back to normal.

> [!WHY]
> Only the keys you bind are taken over, and everything else still works. This partial takeover is what
> makes the Controller good for vehicles: you can bind W, A, S, D to steer your contraption while still
> looking around with the mouse, opening your inventory, and using hotbar items normally. The moment you
> stop operating, every key returns to its usual job, so you are never locked in.

## How your input reaches machines

Toggle and hold bindings publish `true` or `false`. Analog bindings publish a number. These land on the
channel exactly as if a computer had called `emit`, so:

- A **Receiver** on that channel turns your input into redstone immediately.
- A **computer** can read your input with `channel(name)` and use it in its logic.

The Controller only sends a value when it actually changes, which keeps it efficient.

> [!TRY]
> Bind one key in **hold** mode to a channel called `horn`. Place a Receiver on `horn` next to a note
> block or lamp. Right-click to operate, then hold the key: the Receiver fires while held and stops when
> you let go. You just made a manual button with no redstone wiring.

### Example: a throttle a program reads

Bind the scroll wheel in analog mode to `throttle` with min 0, max 15, step 1. Now your scroll wheel is
a dial from 0 to 15. A computer can read that dial and drive a motor with it:

```lua
-- read the player's scroll dial and pass it straight to a Receiver on "motor"
while true do
  local v = channel("throttle") or 0
  emit("motor", v)            -- 0 = stopped, 15 = full speed
  sleep(0.1)
end
```

Point the `motor` Receiver at a Create speed controller or gearshift and you have a hand-throttled
machine. Put the computer in the middle and you can add limits, smoothing, or safety cut-offs in code
while you still steer by hand.

# Lua Basics

The computer runs Lua 5.1. This page teaches enough Lua to be productive even if you have never
programmed. If you already know Lua, skip to the [API Reference](#api-reference). If you would rather not
learn to code at all, [Coding with AI](#coding-with-ai) shows how to have an assistant write programs for
you, and you can still skim this page to read what it produces.

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

> [!NOTE]
> Some calls are instant and some take a tick. Channel calls (`emit`, `channel`, `channels`),
> `getLocation`, and `print` are instant, so you can use them freely. Disk calls each take about one
> game tick, because they reach into saved storage. This matters only in tight loops; see
> [how fast your program runs](#the-computer).

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

> [!NOTE]
> Each `disk` call takes about one game tick, because it reaches into the world's saved data. Read what
> you need once near the top of a loop rather than calling `disk.get` many times per pass. The computer
> handles up to 64 disk operations per tick.

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

> [!WHY]
> Nothing is physically wired to anything. A writer just pins a value under a name, and any number of
> readers pick it up by that same name. This keeps every part independent: you can move a Receiver across
> the base, add a second one, or swap which computer drives a channel, all without touching the others.
> The name is the only connection.

> [!TIP]
> Channel names are shared across the whole server, so two machines using the name `door` will share one
> value. Give channels clear, specific names (`north-gate`, `boiler-pump`, `farm-3-alarm`) so different
> projects do not collide by accident.

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

## Hand-steered vehicle with a safety limit

This ties the whole mod together: a [Controller](#the-controller) for your hands, a computer for the
logic, and Receivers for the machines. Bind the scroll wheel to `throttle` (analog, 0 to 15) and a key
in hold mode to `brake`. The computer drives a `motor` Receiver, but caps the speed and forces a stop
when you hold the brake.

```lua
local MAX = 10                          -- never exceed this, even at full scroll

while true do
  local throttle = channel("throttle") or 0
  local braking  = (channel("brake") == true)

  local speed = throttle
  if speed > MAX then speed = MAX end   -- safety limit
  if braking then speed = 0 end         -- brake overrides everything

  emit("motor", speed)                  -- a Receiver on "motor" runs the drive
  sleep(0.1)
end
```

You steer by hand, but the code guarantees the contraption never runs away and always stops on the
brake. The same shape works for a plane's throttle, a crane's lift, or an elevator's height.

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

## Will a `while true` loop lag the game?
No. Your program runs on its own throttled clock on a background thread, so it cannot freeze Minecraft
even with no `sleep`. A `sleep` is still strongly recommended, because it stops the loop wasting
performance repeating work that only changes once per tick. See
[how fast your program runs](#the-computer).

## My loop reacts too slowly or too fast
The reaction speed is set by your `sleep`. A smaller value reacts faster but uses more performance.
For most machines, `sleep(0.25)` or `sleep(0.5)` is plenty. Reading the world only updates once per
tick (0.05s), so sleeping less than that gains nothing.

## Gotchas checklist
- Tables are 1-indexed: use `t[1]`, not `t[0]`.
- `0` and `""` are truthy. Only `nil` and `false` are falsey.
- Always `sleep` inside forever-loops.
- A Sensor always publishes a table, so index into it.
- Use `local` for variables.

# Glossary

Plain-English definitions for the terms used throughout this site. If a word in another page is unclear,
it is probably here.

## Mod terms

| Term | Meaning |
|---|---|
| Computer | The block that runs your program. The brain. |
| Sensor | A plate that reads a block and reports what it sees on a channel. The senses. |
| Receiver | A plate that turns a channel value into a redstone signal. The hands. |
| Controller | A handheld item that lets you write to channels with your keyboard and mouse. |
| Channel | A named wireless slot that holds one value. Parts share data by reading and writing channels. |
| Flash | To save your program permanently into a computer by pressing the Flash button. |
| Boot | What a computer does when it gains power: start your program fresh from the top. |
| Disk | Permanent storage inside a computer for values you want to keep across reboots. |
| RAM | A computer's temporary memory while running. Wiped on power loss. |
| Tick | One step of the game clock, one twentieth of a second. The game runs at 20 ticks per second. |
| RPM | Rotations per minute, the speed of a Create rotation. Powers the computer. |
| Redstone | Minecraft's built-in wiring signal, a strength from 0 (off) to 15 (full). |

## Coding terms

| Term | Meaning |
|---|---|
| Program | The list of instructions you write for the computer to follow. |
| Lua | The programming language the computer uses. Simple and beginner-friendly. |
| Variable | A named box that holds a value, like `count = 5`. |
| `local` | A keyword that makes a variable private to your program. Always use it. |
| Number | A numeric value, such as `42` or `3.14`. |
| String | Text, written in quotes, such as `"hello"`. |
| Boolean | A true or false value. |
| `nil` | The absence of a value, meaning "nothing here". |
| Table | A collection of values, used for both lists and labelled records. Sensor readings are tables. |
| Function | A named action you can run, such as `print(...)` or `sleep(...)`. |
| Loop | Code that repeats. `while true do ... end` repeats forever. |
| Comment | A note in the code starting with `--`, ignored by the computer. |

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

```
CONTROLLER (handheld)
  left-click   open config        right-click   start/stop operating
  toggle  key flips channel true/false
  hold    channel true while key held
  analog  scroll wheel moves a number through [min, max] by step

TIMING
  program runs on its own clock: 200,000 instructions/tick (~4,000,000/sec)
  a tight loop never lags the game; always sleep() to save performance
  channel/emit/getLocation/print = instant   disk.* = ~1 tick each
  sleep(seconds) waits >= 1 tick (0.05s)

MEMORY
  flash  your code        permanent   (Flash button)
  disk   saved values     permanent   (disk.set / disk.get, up to 1024 keys)
  RAM    live variables   wiped on power loss
```
