# Changelog

## 1.0.1

- **Plot detection without code**: plots are now also recognised by the stone outline on the codespace floor, so the
  plot filter works on plots without any code when you join them from spawn.
- **No flicker at the plot border**: walking into the plot border no longer shows everything around you for a
  moment.

## 1.0.0

First public release.

- **Fast code chests**: chests on code blocks are drawn as a static block model in the chunk mesh instead of as
  block entities. Other chests stay vanilla.
- **Code sign text distance**: the text of code signs is only drawn within a set distance (32 blocks by
  default, configurable in Mod Menu or with `/dfoptimizer signtext`). The signs themselves stay visible.
- **Plot filter**: only your own plot is rendered on DiamondFire. Neighbouring plots, their codespaces, entities
  and particles are hidden, and your own codespace only shows in dev mode.
- **Fewer client ticks**: code chests and code signs no longer tick on the client.
- **Plot detection**: uses CodeClient when installed. Without it, the mod reads the world border or recognises
  the codespace strip, and remembers plots per node.
- **Settings**: Mod Menu settings screen and the `/dfoptimizer` command.
- **Compatibility**: works with and without Sodium, and with Iris.
