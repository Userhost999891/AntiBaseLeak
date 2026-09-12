# AntiBaseLeak

A client-side Fabric mod for **1.21.4** and **1.21.11** that makes it much harder to give away your base coordinates through screenshots and recordings.

Three things in Minecraft are a deterministic function of coordinates, so whenever they are visible in an image they can be matched back against the generated world:

1. **the bedrock pattern** (Nether ceiling, world floor),
2. **block model rotations and variants** (stone, netherrack, dirt…),
3. **plant model offsets** (grass, flowers).

On top of that there is the **F3** screen, which simply prints your coordinates. The mod covers all four.

---

## What it does

### 1. Bedrock masking
Bedrock is replaced with an ordinary block, so its pattern never reaches the screen in the first place.

The replacement is chosen **purely from the surrounding terrain** (never from the shape of the bedrock itself), and only a full, opaque cube with no block entity qualifies — so lighting, the terrain silhouette and collisions all stay exactly as they were.

Replacement modes:

| Mode | What it does | When to use |
|---|---|---|
| `RENDER` | swaps the data on its way to the chunk builder; the client world is untouched and the switch is instant | vanilla renderer |
| `WORLD` | swaps blocks in the client-side copy of the world right after a chunk loads; works with **every** renderer and also removes bedrock from the F3 "Targeted Block" line | Sodium / Iris / Nvidium |
| `AUTO` | `WORLD` when Sodium or a relative is detected, `RENDER` otherwise | default |

> `WORLD` mode is reversible — the mod records a bit map of bedrock positions for every section it touches (512 bytes per section), so turning the option off restores the original blocks without relogging. Single block updates that arrive from the server after a chunk was masked are masked too, otherwise a failed mining attempt would expose one block.
>
> One practical note: in `WORLD` mode the client genuinely sees netherrack there, so bedrock stops being recognisable — on the Nether ceiling you cannot tell it apart from an ordinary block until you try to mine it.

What to replace it with:

| Mode | Result |
|---|---|
| `NEAREST` | texture of the closest ordinary block — the most natural look (default) |
| `SECTION` | one block, the most common in the whole 16×16×16 section — no structure left at all |
| `FIXED` | always the same chosen block — maximum uniformity |

`NEAREST` is enough to hide the pattern. If you want nothing position-dependent on screen whatsoever, pick `SECTION` or `FIXED`.

### 2. Disabling model randomness
- **Rotations and variants** — the randomisation seed (`getRenderingSeed`) is pinned to a constant, so every block picks the same variant and the rotation pattern of stone and netherrack disappears.
- **Plant offsets** (`getModelOffset`) — grass and flowers stop being shifted according to XYZ.

The hook sits on the shared block state method, so third-party renderers are covered as well (Sodium calls it too).

### 3. F3 censoring
Finished lines are filtered right before they are drawn — a single place every line passes through, including lines added by other mods (Sodium, Iris, clients). Each category has its own switch:

- player position (`XYZ`, `Block`, `Chunk`, `Chunk-relative`)
- targeted block / fluid / entity coordinates
- biome
- dimension
- server / IP address
- local difficulty (off by default)
- **other lines with coordinates** — a heuristic that catches mod-added lines when they look like coordinates

Three censor styles:

| Style | Effect |
|---|---|
| `MASK` | keeps the label, hides the value behind characters: `XYZ: ### / ### / ###` |
| `HIDE` | drops the whole line |
| `BLUR` | keeps the line and **blurs it on screen with a shader** |

**How `BLUR` works.** The text is drawn normally, and at the end of the frame — when the world and the whole GUI are already in the framebuffer, but it has not been presented yet — the mod copies the screen into its own framebuffer, runs it through the vanilla `minecraft:blur` post effect (the very shader that blurs the pause menu background) and puts the blurred texture back on screen, **scissored down to the rectangles holding sensitive values**. The label (`XYZ:`, `Biome:`) stays readable; only the value is blurred.

A later moment is not an option: GUI drawing has been deferred since 1.21.9, so when the text is "drawn" it is not in the framebuffer yet.

Because the blur lands in the framebuffer **before it is presented**, a screenshot (F2), OBS and any other screen capture pick it up just the same — this is not an overlay drawn for your eyes only.

Strength is controlled by the *Blur strength* option (1–4 shader passes). If the blur fails for any reason (missing post effect, driver error), the mod **does not leave the coordinates exposed** — it falls back to masking them with text and logs the error.

One honest caveat: with `BLUR` the width of the blurred rectangle still roughly gives away how many characters the value had. If that is too much for you, use `MASK` — `###` is always the same length.

### 4. Chat censoring (optional, off by default)
Masks numbers in messages that look like coordinates (`you died at 123 64 -900`, `/home` listings, friends sharing coordinates). Off by default, because the replacement drops message formatting.

### 5. Notification on join
After you join a world, a panel slides in from the right edge with the menu key hint (`Click [F7] to open settings menu!`), stays for about 4 seconds and slides back out. The animation is driven by the system clock, so it is equally smooth at 20 and at 240 FPS and does not slow down when the server lags. The key it suggests comes from the current binding, so rebinding it in *Controls* updates the hint. It shows up **only** when joining a world — nothing else triggers it. The switch is in the F7 menu.

---

## Menu (F7)

**F7** opens a menu with every option — each one can be turned off separately and changes are written to `config/antibaseleak.json` immediately. The key can be rebound in *Options → Controls → Miscellaneous*.

At the bottom there is a **"Hook status"** section. Every hook records its first actual run there, so you can see whether the mod is really working — in a mod meant to prevent leaks, a silent failure after a game update would be the worst possible outcome.

---

## Building

Requires JDK 21.

```bash
./gradlew buildAll
```

Builds both versions and collects the finished jars in `build/libs/1.0.0/`:

```
antibaseleak-1.0.0+1.21.4.jar     -> Minecraft 1.21.2 - 1.21.4
antibaseleak-1.0.0+1.21.11.jar    -> Minecraft 1.21.9 - 1.21.11
```

A single version: `./gradlew :1.21.4:build` (jar in `versions/1.21.4/build/libs/`).

Switching the active branch (for the IDE):

```bash
./gradlew "Set active project to 1.21.4"
./gradlew "Set active project to 1.21.11"
```

Running the development client: `./gradlew :1.21.11:runClient`. Useful flags: `-Pabl.quickPlay="World name"` jumps straight into a world, `-Pabl.blurtest` blurs a fixed rectangle in the top-left corner so the effect can be checked without opening F3. The dev client starts with `-Dantibaseleak.selftest=true`, so every launch prints a self test result (`[selftest] ...`) into the log: it loads every class the mixins target and checks how the replacement block is chosen for the Nether ceiling, the world floor and a solid bedrock layer. After a Minecraft update that is the first thing worth looking at.

In-game requirements: Fabric Loader ≥ 0.16.0 and Fabric API.

Version ranges: the `1.21.4` branch declares compatibility with 1.21.2–1.21.4 and the `1.21.11` branch with 1.21.9–1.21.11 (those versions share the same API at every point the mod hooks into; 1.21.4 and 1.21.11 are the ones actually verified).

---

## What the mod does NOT cover

So there are no illusions about "impossible to leak":

- **Maps** (`filled_map`) in your hand, in an item frame or on a screenshot still show the surrounding terrain — the mod does not touch them.
- **Compass / lodestone compass** points towards spawn or its lodestone.
- **Scoreboards, action bar, titles and server tab lists** — some servers print coordinates there; only F3 and, optionally, chat are censored.
- **Structures and distinctive terrain** (a portal, a village, specific biomes) still narrow down the area.
- **World name / server address** visible outside the game (Discord, OBS, window title).
- Lighting and shader shadows are derived from the sun position, so the in-game time is still visible.

The mod closes the four most dangerous channels (bedrock, rotations, offsets, F3), but it is no substitute for thinking before publishing footage.

---

## Structure

```
src/main/java/dev/antibaseleak/
├─ AntiBaseLeakClient.java     entry point, F7 key, world events
├─ FeatureStatus.java          whether the hooks actually fired
├─ SelfTest.java               self test (-Dantibaseleak.selftest=true)
├─ config/                     settings and JSON persistence
├─ mask/BedrockMask.java       picking the replacement block (shared by both modes)
├─ mask/WorldMasker.java       WORLD mode and restoring bedrock
├─ censor/                     F3 and chat censoring rules
├─ render/BlurCensor.java      queue of rectangles to blur
├─ gui/AblConfigScreen.java    the F7 menu
├─ gui/JoinToast.java          the sliding notification
└─ mixin/                      7 hooks into the game

src/1.21.4/java/  |  src/1.21.11/java/
└─ render/ScreenBlur.java      the GPU half of the blur (one file per version)
```

The code is shared except in two places:

- `KeyBinding` and `mouseClicked` (1.21.9 moved input handling to a `Click` object) — handled with Stonecutter comments in `src/main`,
- the GPU half of the blur — a separate file per version in `src/1.21.4/java` and `src/1.21.11/java`, because 1.21.5 rewrote blaze3d (old `glBlitFramebuffer` versus the new `CommandEncoder`). Conditional blocks would be unreadable there, so the directories are attached to the source set in `build.gradle.kts`.

---

## License

MIT — see [LICENSE](LICENSE).
