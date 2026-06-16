# Badlion Client 3.0.0

Open-source Badlion Client for **Minecraft 1.8.9**, packaged as a normal jar mod so you can run it on any launcher — **PrismLauncher**, MultiMC, vanilla, you name it. No Badlion launcher required.

> Bundled: Badlion mods (cape, scoreboard, animations, anti-cheat, HUD, performance) **+ OptiFine 1.8.8 HD U I7** (FPS, shaders support, graphics options). One jar, one drop, done.

---

## Download

Grab the latest `badlion.jar` from the [Releases page](https://github.com/SirHumza/Badlion-3.0.0/releases).

Two artifacts are published per release:

| File | Use it for |
|---|---|
| `badlion.jar`     | **PrismLauncher / MultiMC** — drop into the *Jar Mods* tab of a 1.8.9 instance. |
| `badlion-all.jar` | **Standalone** — runnable with `java -jar`, all dependencies bundled. |

---

## PrismLauncher install (recommended)

1. Open PrismLauncher → **Add Instance** → **Vanilla** → pick version **1.8.9** → name it `Badlion`.
2. Right-click the instance → **Edit Instance**.
3. Open the **Version** tab → click **Add to Minecraft.jar** (older Prism versions call this **Jar Mods**).
4. Select the `badlion.jar` you downloaded.
5. Close, hit **Play**.

That's it. Badlion + OptiFine 1.8.8 are both inside the one jar. The Badlion main menu replaces vanilla and OptiFine's video settings appear automatically.

### Optional tweaks

- **More RAM:** *Edit Instance → Settings → Java → Memory* → set max to `4096 MiB` or higher.
- **Custom username (offline mode):** *Settings → User Accounts → Add Offline Account*.

---

## Standalone run (no launcher)

```bash
java -Djava.library.path=natives -jar badlion-all.jar --username Pace
```

You need:
- Java 8 (Temurin works)
- A `natives/` folder next to the jar containing LWJGL natives for your OS (Prism handles this for you; standalone, run `./gradlew unzipNatives` once from a source checkout to populate `jars/natives/`).

---

## Build from source

```bash
git clone https://github.com/SirHumza/Badlion-3.0.0.git
cd Badlion-3.0.0
./gradlew build
```

OptiFine 1.8.8 (`libs/optifine.jar`) is checked in alongside the source, so the build always merges it into the output jars. Output lands in `build/libs/`:
- `badlion.jar`      — thin (PrismLauncher Jar Mod), Badlion + OptiFine
- `badlion-all.jar`  — fat (standalone), Badlion + OptiFine + deps

### Dev-loop tasks

| Task | What it does |
|---|---|
| `./gradlew startGame` | Launch the client locally with a random username. |
| `./gradlew startGame --args='--username Pace'` | Launch with a specific username. |
| `./gradlew unzipNatives` | Extract LWJGL natives into `jars/natives/`. |
| `./gradlew copyAssets`  | Copy `assets/` from your local `.minecraft/` into `jars/`. |

Requires **Java 8**.

---

## How it works

Badlion ships as a [LaunchWrapper](https://github.com/Mojang/LegacyLauncher) tweaker:

```
PrismLauncher → LaunchClassLoader
              → BadlionTweaker (registers BadlionTransformer)
              → BadlionTransformer (patches Minecraft bytecode)
              → net.minecraft.client.main.Main
```

When you use the Jar Mod path, Prism overlays `badlion.jar` on top of vanilla `minecraft.jar`, so Badlion's modified classes win at classload time and the transformer hooks fire normally.

---

## Project layout

```
src/main/java/
├── Start.java                 -- standalone entry point (used by `startGame`)
├── net/minecraft/             -- decompiled MC 1.8.9
├── net/minecraft/launchwrapper/ -- bundled LaunchWrapper
├── net/minecraftforge/        -- event bus only (no full Forge)
├── optifine/                  -- bundled OptiFine 1.8.8 patches
├── shadersmod/                -- ShadersMod (client + common)
└── net/badlion/client/
    ├── Wrapper.java           -- runtime singleton (mods, cape, anti-cheat)
    ├── tweaker/               -- BadlionTweaker + BadlionTransformer (entry)
    ├── manager/               -- ModProfileManager, CapeManager, AccountManager
    ├── mods/                  -- individual mods (HUD, movement, render, misc)
    ├── gui/                   -- main menu, slideout, font renderer
    ├── events/                -- EventBus + EventType
    ├── config/                -- config readers
    ├── thread/                -- async workers (cape lookup)
    └── util/                  -- network, color, texture, hash helpers
```

`net/badlion/client/` is the part to read first if you want to understand or modify behaviour. Everything else is vanilla MC / OptiFine / shaders.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `No lwjgl64 in java.library.path` | Use `./gradlew startGame` (it sets the lib path), or pass `-Djava.library.path=jars/natives` manually. |
| Game launches but Badlion UI is missing | Confirm `badlion.jar` is in the *Jar Mods* tab, not the *Mods* (Forge) tab. |
| Crash on launch with `ClassNotFoundException: net.minecraft.launchwrapper.*` | You're running standalone without LaunchWrapper on the classpath — use `badlion-all.jar`, not `badlion.jar`. |
| Online auth fails | The launcher must pass a real `--accessToken`; PrismLauncher does this for online accounts. Offline accounts skip auth. |

---

## Credits

- Original Badlion Client team (closed source)
- Decompile / port: [@SirHumza](https://github.com/SirHumza)
- OptiFine: sp614x
- ShadersMod: karyonix
- Minecraft: Mojang
