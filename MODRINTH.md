Screenshots can reveal your base. Bedrock patterns, block rotations and plant offsets all come from coordinates, so with the world seed anyone can match them and find you. F3 shows the coordinates outright.

Client-side only. Nothing is sent to the server.

---

**Anti bedrock leak**

Bedrock is swapped for a normal block taken from the terrain around it, so the pattern never shows up. Works with Sodium.

---

**Removed block rotation**

Blocks and plants stop being rotated and shifted by coordinates, so a wall is no longer a map reference.

---

**Blur your coordinates**

F3 values can be masked, hidden, or **blurred by a real shader**. The blur goes into the frame, so screenshots and OBS get it too.

---

**Custom menu GUI**

Press **F7**. Every option has its own toggle, saved instantly, plus a status list showing what is actually running.

---

**Chat censoring**

Hides coordinates in chat messages. Off by default.

---

**Works with**

Fabric Loader 0.16+ and Fabric API · Minecraft 1.21.2–1.21.4 and 1.21.9–1.21.11 · Sodium, Iris, Nvidium · any server.

**Does not cover**

Maps, compasses, server scoreboards and distinctive terrain can still give hints.

---

[Source code on GitHub](https://github.com/Userhost999891/AntiBaseLeak) · MIT
