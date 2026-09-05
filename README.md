# CIT Resewn: Continued

Unofficial continuation of [SHsuperCM's CIT Resewn](https://github.com/SHsuperCM/CITResewn) (archived, last release targeted 1.21.1), ported to Minecraft 26.2 / Fabric. Re-implements MCPatcher/OptiFine's CIT (Custom Item Textures) resourcepack format against 26.2's item model system.

Only targets Minecraft 26.2 on Fabric. If you need other versions or Forge/NeoForge, see other CIT Resewn continuations instead.

## What works

- `texture=` / `tile=` CIT rules (the overwhelming majority of real-world CIT packs), including ones with no separate base texture defined for the target item — some other 26.x ports of CIT Resewn don't support this case yet.
- Plain `model=` CIT rules — a full custom 3D model replacing the item entirely (e.g. commemorative "doll"-style collectibles), rendered with correct per-face shading and the model's own display transforms.
- Armor, elytra, and enchantment glint CITs.
- Animated CIT spritesheets (the OptiFine `frametime`/multi-frame convention).
- Legacy `nbt.display.Name` / `nbt.display.Lore.N` conditions from pre-1.21 packs, translated to the current component-based item data automatically.

## Known limitations

- **Conditional/keyed sub-item model overrides are not supported** (e.g. a bow's `model.pulling_0=`, `model.pulling_1=` draw-stage variants). Only a single, unconditional `model=` per CIT rule works. A rule using this is rejected with a warning and otherwise ignored.
- **"Broken paths" support is not implemented.** The config option exists for compatibility with the original mod's config file but has no effect — resourcepacks with illegal (non-namespace-safe) asset paths won't load any differently than in vanilla.
- ModMenu and Cloth Config are optional — without them, the config screen just isn't available (nothing to configure yet beyond enable/mute-logging/cache interval).

## Requirements

- Minecraft 26.2, Fabric Loader, Fabric API.
- Optional: [ModMenu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config) for the in-game config screen.

## Building

```
./gradlew build
```

The output jar is written to `build/libs/`.

## License

MIT — see [LICENSE](LICENSE). Original work Copyright (c) 2021 SHsuperCM.
