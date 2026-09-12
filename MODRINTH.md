Your screenshots can give your base away. The bedrock pattern, block rotations and plant offsets are all calculated from coordinates — with the world seed, anyone can match them back and find you. And F3 simply prints the coordinates outright.

This mod closes all four. **Client-side only** — nothing is sent to the server, nothing is installed on it.

## 🪨 Anti bedrock leak

Bedrock is replaced with an ordinary block picked from the terrain around it, so the pattern never reaches your screen. Lighting, collisions and the look of the terrain stay the same. Works with Sodium.

## 🔄 Removed block rotation

Stone, netherrack, dirt and plants stop choosing their texture variant and offset from XYZ — a netherrack wall is no longer a map reference.

## 🔍 Blur your coordinates

F3 lines can be masked with `###`, hidden completely, or **blurred by a real shader**. The blur goes into the frame before it is shown, so screenshots and OBS capture it too. Every category has its own switch: position, targeted block, biome, dimension, server address and more.

## ⚙️ Custom menu GUI

Press **F7**. Every option has its own toggle and saves instantly. At the bottom, a hook status list shows whether each part of the mod has actually run — so you can check it instead of trusting it.

## 💬 Chat censoring

Masks coordinates in chat: death messages, `/home` lists, coords shared by friends. Off by default.

---

## Works with

- Fabric Loader 0.16+ and Fabric API
- Minecraft **1.21.2 – 1.21.4** and **1.21.9 – 1.21.11**
- Sodium, Iris and Nvidium — detected automatically
- Any server, including ones that know nothing about the mod

## Does not cover

Maps, compasses, server scoreboards and distinctive terrain can still give hints. This closes the four biggest leaks, not every one.

---

[Source code on GitHub](https://github.com/Userhost999891/AntiBaseLeak) · MIT
