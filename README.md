# Badlion Client (1.8.9)

The old Badlion Client — packaged so you can play it without the Badlion launcher. Drop one jar into PrismLauncher (or MultiMC, vanilla, whatever you use) on a 1.8.9 instance and you're in.

This is **Badlion 2.0.0-v-beta** — the last open-source build of the client, the one with capes, scoreboard, ping, FPS, keystrokes, all the HUD stuff. **OptiFine 1.8.8** is bundled inside the same jar, so shaders + the FPS bump come along for free.

## Get it

[**Download `badlion.jar` from Releases**](https://github.com/SirHumza/Badlion-3.0.0/releases/latest)

You'll see two files there:

- `badlion.jar` — what you actually want. Drop this into PrismLauncher's *Jar Mods* tab.
- `badlion-all.jar` — same thing but with every Java dep crammed in too. Only useful if you want to run the client without a launcher.

## Setting it up in PrismLauncher

1. **Add Instance** → **Vanilla** → pick **1.8.9** → name it whatever (`Badlion` works).
2. Right-click the instance → **Edit Instance**.
3. Open the **Version** tab. Click **Add to Minecraft.jar** (older Prism builds call this *Jar Mods*).
4. Pick the `badlion.jar` you downloaded.
5. Save, hit **Play**.

That's it. You should see the Badlion main menu instead of the vanilla one. OptiFine's video settings show up under Options → Video Settings automatically.

> Heads up: keep an eye on RAM. Edit Instance → Settings → Java → Memory, set max to at least 3 or 4 GB. The jar carries every Badlion cosmetic texture, so allocating tiny RAM = stutter.

## Running it standalone

If you don't want a launcher at all:

```bash
java -Djava.library.path=natives -jar badlion-all.jar --username YourName
```

You need a `natives/` folder next to the jar with LWJGL natives for your OS. PrismLauncher handles this for you; standalone you'll have to grab them yourself or run `./gradlew unzipNatives` from a source checkout.

## Building from source

You need Java 8 (Temurin works fine).

```bash
git clone https://github.com/SirHumza/Badlion-3.0.0.git
cd Badlion-3.0.0
./gradlew build
```

Outputs land in `build/libs/`. OptiFine 1.8.8 is checked in at `libs/optifine.jar`, so the build is fully self-contained — no internet, no extra steps.

Other useful tasks:

| Task | What it does |
|---|---|
| `./gradlew startGame` | Launch the client locally for dev. |
| `./gradlew startGame --args='--username Pace'` | Same, but with a specific name. |

## What's actually in this repo

```
src/main/java/
├── Start.java                     -- standalone entry point
├── net/minecraft/                 -- decompiled MC 1.8.9
├── net/minecraft/launchwrapper/   -- LaunchWrapper (used by the tweaker)
├── net/minecraftforge/            -- just Forge's event bus, not full Forge
└── net/badlion/client/            -- the actual Badlion code
    ├── Wrapper.java               -- the runtime singleton (mods, capes, anti-cheat heartbeat)
    ├── tweaker/                   -- BadlionTweaker + bytecode transformer (entry point)
    ├── manager/                   -- mod profiles, capes, accounts
    ├── mods/                      -- every individual mod (HUD, FOV, FPS, keystrokes, etc.)
    ├── gui/                       -- main menu, slideout, font renderer
    ├── events/                    -- internal event bus
    ├── config/                    -- config file readers
    ├── thread/                    -- async workers (cape lookups)
    └── util/                      -- helpers (network, color, hashing)

libs/optifine.jar                  -- OptiFine 1.8.8 HD U I7, merged into output jars at build time
```

If you want to read or change behavior, `net/badlion/client/` is the place to start. Everything else is vanilla MC, OptiFine, or LaunchWrapper.

## How it actually works (briefly)

PrismLauncher overlays `badlion.jar` onto vanilla `minecraft.jar` at launch. Both Badlion's modified MC classes *and* OptiFine's modified MC classes win at classload time. Then `BadlionTweaker` (a LaunchWrapper tweaker) registers a bytecode transformer that wires up Badlion's runtime hooks (cape rendering, scoreboard injection, anti-cheat ping, etc.). OptiFine boots the same way through its own tweaker.

## Things that might trip you up

| Problem | Fix |
|---|---|
| `No lwjgl64 in java.library.path` | Use `./gradlew startGame` (it sets the library path), or pass `-Djava.library.path=jars/natives` yourself. |
| Game launches, no Badlion UI | Make sure `badlion.jar` is in the *Jar Mods* tab, not the *Mods* (Forge) tab. They're different. |
| Crashes on launch with `ClassNotFoundException: net.minecraft.launchwrapper.*` | You're running the thin jar standalone. Use `badlion-all.jar`, or run inside a launcher. |
| Online mode auth fails | The launcher has to pass a real `--accessToken`. Online accounts in Prism handle this automatically. Offline accounts skip auth. |

## Credit

- Original Badlion Client team — closed source these days, this is from a leaked dump of v2.0.0-v-beta.
- Decompile + repackage: [@SirHumza](https://github.com/SirHumza)
- OptiFine: sp614x
- Minecraft: Mojang
