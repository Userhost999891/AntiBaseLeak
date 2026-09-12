## Your screenshots know where your base is

Three things you see in Minecraft are a deterministic function of coordinates. Anyone with the world seed can match them against a generated world and work out where the picture was taken:

- **the bedrock pattern** — the Nether ceiling and the bottom of the world are a fingerprint of the exact spot,
- **block rotations and variants** — stone, netherrack and dirt pick their texture variant from a seed derived from XYZ,
- **plant offsets** — grass and flowers are nudged sideways by the same function.

And then there is **F3**, which simply prints the coordinates for anyone watching.

AntiBaseLeak closes all four. It is client-side only: it changes what *you* see, never what the server receives.

---

## What it does

### Bedrock masking

Bedrock is replaced with an ordinary block, so the pattern never reaches the screen. The replacement is chosen **only from the terrain around it** — never from the shape of the bedrock itself — and only a full, opaque cube without a block entity qualifies, so lighting, the silhouette of the terrain and collisions stay exactly as they were.

| Mode | What it does |
|---|---|
| `AUTO` | picks the right one for your setup (default) |
| `RENDER` | swaps the data on its way to the chunk builder; the world itself is untouched |
| `WORLD` | swaps blocks in the client-side copy of the world; works with **any** renderer, including Sodium |

`WORLD` mode is reversible — turning the option off puts the bedrock back without relogging.

And what it becomes:

| Mode | Result |
|---|---|
| `NEAREST` | texture of the closest ordinary block — looks natural (default) |
| `SECTION` | one block for the whole 16×16×16 section — no structure left at all |
| `FIXED` | always the same block you choose — maximum uniformity |

### No position-based model randomness

Block rotations and plant offsets stop depending on coordinates, so the rotation pattern of a netherrack wall stops being a map reference. This covers third-party renderers too.

### F3 censoring

Every line is filtered right before it is drawn — including lines added by other mods — with a separate switch for player position, targeted block, fluid and entity, biome, dimension, server address, local difficulty and a catch-all for anything else that looks like coordinates.

| Style | What you get |
|---|---|
| `MASK` | `XYZ: ### / ### / ###` — the label stays, the value does not |
| `HIDE` | the line disappears entirely |
| `BLUR` | the value is **blurred on screen by a shader** |

`BLUR` runs the game's own blur post effect over the finished frame, before it is presented — so a screenshot, OBS or any other capture picks up the blur exactly as you see it. It is not an overlay drawn for your eyes only. If the blur ever fails, the mod falls back to masking with text instead of leaving the coordinates exposed.

### Chat censoring (optional, off by default)

Masks numbers in messages that look like coordinates — death messages, `/home` listings, coordinates shared by friends.

---

## Settings

**F7** opens the menu. Every option can be switched off separately, and changes are saved immediately.

At the bottom there is a **Hook status** section that shows whether each part of the mod has actually run. In a mod that exists to prevent leaks, a silent failure after a game update would be the worst possible outcome, so you can verify it instead of trusting it.

---

## Compatibility

- **Fabric Loader 0.16+** and **Fabric API**
- **Client-side only** — nothing to install on the server, and it works on servers that have no idea it exists
- **Sodium, Iris, Nvidium and friends** are detected automatically; the mod switches to the masking mode that works with them
- Supported: **1.21.2 – 1.21.4** and **1.21.9 – 1.21.11**

No packets are modified and nothing is sent to the server. The game state the server sees is identical with or without the mod.

---

## What it does not protect against

To be clear about the limits:

- **maps** in your hand or in item frames still show the terrain around them
- **compasses** still point at spawn or their lodestone
- **scoreboards, action bar and server tab lists** — some servers print coordinates there; only F3 and, optionally, chat are covered
- **distinctive terrain and structures** still narrow down the area
- **window title, Discord status or the world name** visible outside the game

It closes the four most dangerous channels, but it is not a substitute for thinking before you post footage.

---

## Source

Code and issue tracker: [github.com/Userhost999891/AntiBaseLeak](https://github.com/Userhost999891/AntiBaseLeak) — MIT licensed.
