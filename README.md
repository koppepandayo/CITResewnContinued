# CIT Resewn: Continued

Unofficial continuation of [SHsuperCM's CIT Resewn](https://github.com/SHsuperCM/CITResewn) (archived, last release targeted 1.21.1), ported to Minecraft 26.2 / Fabric. Re-implements MCPatcher/OptiFine's CIT (Custom Item Textures) resourcepack format against 26.2's item model system.

Only targets Minecraft 26.2 on Fabric. If you need other versions or Forge/NeoForge, see other CIT Resewn continuations instead.

## What works

- `texture=` / `tile=` CIT rules (the overwhelming majority of real-world CIT packs), including ones with no separate base texture defined for the target item — some other 26.x ports of CIT Resewn don't support this case yet.
- Plain `model=` CIT rules — a full custom 3D model replacing the item entirely (e.g. commemorative "doll"-style collectibles), rendered with correct per-face shading and the model's own display transforms.
- Keyed `texture.<key>=` overrides for bow/crossbow draw stages (`bow`, `bow_pulling_0/1/2`) and a broken elytra (`broken_elytra`) — resolved by mirroring vanilla's own item model conditions for those two items specifically, not a generic dispatch system (see below).
- Armor and elytra texture CITs. Enchantment glint on any CIT-reskinned item follows vanilla's own logic (a genuinely enchanted item shows the normal shimmer over the custom icon) — see Known limitations for the one enchantment-related feature that isn't ported.
- OptiFine's implicit "same file name as the `.properties`" asset convention — a rule with no `texture=`/`tile=`/`model=` line at all falls back to a same-named `.json` (tried first) or `.png` next to it.
- Animated CIT spritesheets (the OptiFine `frametime`/multi-frame convention), including ones with a non-empty `.mcmeta` `frames` list that vanilla itself ends up animating.
- Legacy `nbt.display.Name` / `nbt.display.Lore.N` / `nbt.SkullOwner.Name` conditions from pre-1.21 packs, translated to the current component-based item data automatically.

## Known limitations

- **Keyed texture overrides only resolve for bow/crossbow and elytra.** The `texture.<key>=` mechanism itself is generic, but picking the right key requires knowing that particular vanilla item's own override conditions, which aren't reimplemented generically. A rule keying textures by name on any other item silently falls back to its plain `texture=`/`tile=` (if any) instead of ever matching a keyed entry.
- **Conditional/keyed sub-item *model* overrides are not supported** (e.g. a bow's `model.pulling_0=`, `model.pulling_1=` draw-stage variants as a full custom 3D model per stage, rather than just a texture). Only a single, unconditional `model=` per CIT rule works; any keyed `model.<key>=` entries are silently unused.
- **"Broken paths" support is not implemented.** The config option exists for compatibility with the original mod's config file but has no effect — resourcepacks with illegal (non-namespace-safe) asset paths won't load any differently than in vanilla.
- **No dedicated enchantment CIT type.** Upstream CIT Resewn has a separate `enchantment=` CIT type that replaces the *texture* of the glint shimmer itself with a custom image — that's not ported. Normal enchant glint visibility (on/off) is unaffected and works on every CIT-reskinned item.
- **A custom `model=` with its own real 3D geometry doesn't support CIT spritesheet animation.** Only a flat `model=` (one relying on `"parent": "item/generated"` for shape, the common case) gets animation, the same way a plain `texture=`/`tile=` icon does.
- ModMenu and Cloth Config are optional — without them, the config screen just isn't available (nothing to configure yet beyond enable/mute-logging/cache interval).

## AI assistance

Large parts of this port — mixin implementation, verifying exact vanilla behavior by decompiling the game jar, and bug fixes — were developed with AI assistance (Claude Code) under close human review, testing, and direction. If something looks off, please open an issue.

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
