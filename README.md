# Computer Mod

A programmable Lua computer for [Create](https://github.com/Creators-of-Create/Create)
(NeoForge 1.21.1). It adds the autonomous half that Create is missing: machines that watch the world,
think, and act on their own.

```
world -> [Sensor] -> channel -> [Computer] -> channel -> [Receiver] -> world
```

## The parts

- **Computer**: a microcontroller block. Flash Lua files onto it (`main.lua` plus libraries loaded
  with `require`), power it with rotation or Forge Energy, and it boots and runs. Its screen is a
  small IDE with file tabs, find, undo, a console, and a live status bar.
- **Sensor**: a thin plate that reads whatever block it is mounted on, generically (inventories,
  tanks, energy, blockstate, and the block's full NBT), and publishes the readings on a wireless
  channel the instant they change. Its screen shows everything it sees as a live, searchable tree;
  click any value to copy the Lua expression that reads it.
- **Receiver**: a thin plate that turns the latest channel value into a redstone signal of 0 to 15.
  Its screen shows the live value and the resulting signal on a meter.
- **Controller**: a handheld item that binds your keys, mouse, and scroll wheel to channels, for
  driving contraptions by hand or overriding programs.

Because the Sensor reads blocks through standard capabilities, blockstate, and NBT, it works with
any correctly built mod or Create addon, with no integration code.

All three blocks keep their surrounding chunks loaded by default (a per-block setting with a
selectable area), so the systems you build keep running while you are on the other side of the world
or offline. No chunk-loader mod required.

## Documentation

The full documentation site lives in [`docs/`](docs/) (a single self-contained `index.html`, also
published via GitHub Pages). It covers the concepts, every block, every function, Lua from zero, and
an AI-assisted workflow for people who do not code.

For LLM users: [`COMPUTER_PROGRAMMING_GUIDE.md`](COMPUTER_PROGRAMMING_GUIDE.md) is a single context
file that teaches an AI assistant the entire API so it can write programs for you.

## Building

This is a standard NeoForge Gradle project:

```bash
./gradlew build        # build the mod jar into build/libs
./gradlew runClient    # launch a development client
```

Requires Java 21. Create and its dependencies are pulled in through the Gradle setup.
