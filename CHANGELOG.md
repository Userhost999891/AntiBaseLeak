# 1.0.0

First release.

- **Bedrock masking** - bedrock is replaced with an ordinary block picked from the
  surrounding terrain, in `RENDER` mode (vanilla renderer) or `WORLD` mode (works with
  Sodium and every other renderer, and is reversible without relogging).
- **Model randomness disabled** - constant rendering seed and zeroed model offsets, so
  block rotations and plant offsets no longer depend on coordinates.
- **F3 censoring** - a switch per category (position, targeted block/fluid/entity, biome,
  dimension, server, local difficulty, other coordinate-looking lines) and three styles:
  `MASK`, `HIDE` and `BLUR`, the last one running the vanilla blur shader over the
  finished frame so screenshots and screen capture are covered too.
- **Optional chat censoring** of numbers that look like coordinates.
- **F7 menu** with a hook status section showing whether each hook has actually fired.
- **Notification on join** with the menu key hint.
