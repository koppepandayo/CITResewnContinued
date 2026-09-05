package shcm.shsupercm.fabric.citresewn.defaults.mixin.types.item;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shcm.shsupercm.fabric.citresewn.CITResewn;
import shcm.shsupercm.fabric.citresewn.defaults.cit.types.TypeItem;
import shcm.shsupercm.fabric.citresewn.defaults.item.CitSpriteAnimation;
import shcm.shsupercm.fabric.citresewn.pack.PackParser;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Injects every CIT item PNG as a real, ordinary entry in the item atlas's sprite pool — mirroring
 * upstream CIT Resewn's own {@code AtlasLoaderMixin}/{@code AtlasSource}: a plain resourcepack
 * directory scan under each CIT root ({@code <root>/cit/**.png}), added directly to the atlas's
 * own source list, with no dependency on CIT's own parsed-rule state.
 * <p>
 * This port originally injected only the specific textures a parsed CIT rule referenced
 * ({@code TypeItem.Container.KNOWN_TEXTURES}), populated by CIT's own resource-reload listener.
 * That state lives in a *different* listener's "apply" phase, while the atlas's own sprite
 * collection runs in every listener's "prepare" phase - concurrently, before any "apply" runs -
 * so on a session's very first reload there was no ordering that could get CIT's textures
 * discovered before the atlas asked for them; only the second and later reloads (i.e. after
 * manually pressing F3+T) ever had anything to inject, confirmed by a log capturing that first,
 * pre-F3+T reload showing every single CIT item permanently falling back to the non-atlased path.
 * Scanning the resourcepacks directly here — the same thing the reference mod does — needs no
 * other listener to have already run, eliminating the race entirely.
 * <p>
 * Doing this gets every CIT item texture a genuine {@code TextureAtlasSprite}: correct
 * atlas-driven lighting (whatever makes vanilla items look right, which this port's non-atlased
 * BakedQuads never got), and free per-tick animation via {@code SpriteContents.AnimationState}
 * (no hand-rolled frame-cycling needed) since {@link SpriteResourceLoader#loadSprite} already
 * reads a texture's {@code .mcmeta} animation metadata the same way it does for every other
 * sprite in the atlas.
 */
@Mixin(SpriteSourceList.class)
public abstract class SpriteSourceListMixin {
    // SpriteSourceList itself keeps no record of which atlas it belongs to once constructed (only
    // its "sources" list survives) - confirmed by decompiling the class - yet the SAME class is
    // reused for every one of vanilla's ~13 atlases (blocks, items, particles, gui, banner patterns,
    // ...). Without capturing this here, list() had no way to tell "the items atlas" apart from any
    // other, so every CIT item texture was being blindly injected into *all* of them every reload -
    // confirmed by the log showing "Injected 258 CIT item texture(s)" fire 13 times per reload, once
    // per atlas, from concurrent worker threads. That almost certainly raced/corrupted the *real*
    // items-atlas stitch for these textures (each one's Resource was shared across every one of
    // those concurrent attempts), which is why the real atlas sprite could never be found afterward
    // no matter how many times a resource reload was retried.
    @Unique
    private Identifier citresewn$atlasId;

    // One converter per CIT root (mcpatcher/optifine/citresewn), each matching every ".png" under
    // "<root>/cit/" in any namespace - mirrors upstream's ResourceFinder(root + "/cit", ".png").
    @Unique
    private static final List<FileToIdConverter> citresewn$CIT_PNG_FINDERS = PackParser.ROOTS.stream()
            .map(root -> new FileToIdConverter(root + "/cit", ".png"))
            .toList();

    // Same idea, but for a CIT model=' *.json under "<root>/cit/" - a full custom model's own
    // textures routinely live under the ordinary "textures/" tree instead of the CIT root, so the
    // PNG scan above never finds them on its own. This re-parses
    // each model file independently of TypeItem's own parsed-CIT state, for the same reason the
    // PNG scan does its own filesystem walk instead of reading TypeItem.CONTAINER.loaded (see the
    // class Javadoc above): that state lives in a different listener's "apply" phase, which has no
    // ordering guarantee relative to this "prepare"-phase atlas scan on a session's first reload.
    @Unique
    private static final List<FileToIdConverter> citresewn$CIT_MODEL_JSON_FINDERS = PackParser.ROOTS.stream()
            .map(root -> new FileToIdConverter(root + "/cit", ".json"))
            .toList();

    @Inject(method = "load", at = @At("RETURN"))
    private static void citresewn$captureAtlasId(ResourceManager resourceManager, Identifier atlasId, CallbackInfoReturnable<SpriteSourceList> cir) {
        ((SpriteSourceListMixin) (Object) cir.getReturnValue()).citresewn$atlasId = atlasId;
    }

    @Inject(method = "list", at = @At("RETURN"), cancellable = true)
    private void citresewn$injectItemTextures(ResourceManager resourceManager, CallbackInfoReturnable<List<SpriteSource.Loader>> cir) {
        if (!AtlasIds.ITEMS.equals(citresewn$atlasId))
            return;

        List<SpriteSource.Loader> combined = new ArrayList<>(cir.getReturnValue());
        int added = 0;
        for (FileToIdConverter finder : citresewn$CIT_PNG_FINDERS) {
            for (Map.Entry<Identifier, Resource> entry : finder.listMatchingResources(resourceManager).entrySet()) {
                Identifier texture = entry.getKey();
                Resource resource = entry.getValue();
                combined.add((SpriteResourceLoader loader) -> loadCitSprite(loader, texture, resource));
                added++;
            }
        }
        added += citresewn$injectModelTextures(resourceManager, combined);
        if (added > 0) {
            CITResewn.LOG.info("[citresewn] Injected {} CIT item texture(s) into the item atlas", added);
            cir.setReturnValue(combined);
        }
    }

    /** @return how many additional textures (referenced by a `model=` CIT's own model file) were added. */
    private static int citresewn$injectModelTextures(ResourceManager resourceManager, List<SpriteSource.Loader> combined) {
        Set<Identifier> seen = new HashSet<>();
        int added = 0;
        for (FileToIdConverter modelFinder : citresewn$CIT_MODEL_JSON_FINDERS) {
            for (Map.Entry<Identifier, Resource> entry : modelFinder.listMatchingResources(resourceManager).entrySet()) {
                Identifier modelId = entry.getKey();
                Resource modelResource = entry.getValue();
                CuboidModel model;
                try (Reader reader = modelResource.openAsReader()) {
                    // Resolves each of the model's own "textures" values through the same CIT
                    // relative-path convention texture=/tile= uses (see
                    // TypeItem#parseModelResolvingTextures) - without this, vanilla's own parser
                    // turns a "./foo"-style value into a nonsensical literal Identifier that never
                    // matches any real sprite, and this scan (silently) never injects that texture.
                    model = TypeItem.parseModelResolvingTextures(modelId, reader, resourceManager);
                } catch (Exception e) {
                    // Not every "<root>/cit/**.json" is necessarily a citresewn model= target (could
                    // be some other tool's metadata file sharing the same tree) - skip quietly rather
                    // than warning on every unrelated JSON file.
                    continue;
                }

                TextureSlots resolved = new TextureSlots.Resolver().addLast(model.textureSlots()).resolve(() -> "citresewn model texture scan");
                for (String slotName : model.textureSlots().values().keySet()) {
                    Material material = resolved.getMaterial(slotName);
                    if (material == null || !seen.add(material.sprite()))
                        continue;

                    // material.sprite() is already the fully-resolved real resource path (see
                    // TypeItem#parseModelResolvingTextures) - the same convention TypeItem#texture
                    // uses - so it's looked up directly, with no further "textures/"/".png" wrapping.
                    Identifier texture = material.sprite();
                    Optional<Resource> textureResource = resourceManager.getResource(texture);
                    if (textureResource.isEmpty())
                        continue;

                    Resource resource = textureResource.get();
                    combined.add((SpriteResourceLoader loader) -> loadCitSprite(loader, texture, resource));
                    added++;
                }
            }
        }
        return added;
    }

    private static SpriteContents loadCitSprite(SpriteResourceLoader loader, Identifier texture, Resource resource) {
        try {
            Optional<AnimationMetadataSection> animationInfo = resource.metadata().getSection(AnimationMetadataSection.TYPE);
            if (animationInfo.isEmpty())
                return loader.loadSprite(texture, resource);

            SpriteContents contents = loader.loadSprite(texture, resource);
            if (contents == null || contents.isAnimated())
                return contents;

            // CIT texture packs routinely ship "frames": [] in a texture's .mcmeta to stop *vanilla*
            // from auto-animating a spritesheet - confirmed by decompiling SpriteContents.createAnimatedTexture:
            // an explicit-but-empty frame list collapses to zero frames, i.e. permanently frame 0, not
            // "auto-detect frames like when the key is absent". These sheets still visibly cycle in-game
            // though - that's the OptiFine CIT icon-animation convention, driven by the same mcmeta
            // "frametime" but ticked by CIT's own renderer instead of vanilla's (deliberately disabled)
            // atlas animation. TextureAtlas.uploadInitialContents() allocates a scratch texture sized to
            // the declared (too-small, one-frame) size but then uploads the full, uncropped sheet into it,
            // crashing the atlas stitch and taking the *entire* resourcepack down with it - so report the
            // sprite's true full pixel size as its own frame size instead (an honest, always-safe
            // allocate/upload match), and register the real per-frame height + frame count +
            // frametime so ItemModelResolverMixin can pick the right frame's UV row itself every tick.
            int frameHeight = contents.height();
            try (InputStream in = resource.open()) {
                NativeImage rawImage = NativeImage.read(in);
                int rawWidth = rawImage.getWidth();
                int rawHeight = rawImage.getHeight();
                if (contents.width() != rawWidth || frameHeight != rawHeight) {
                    int frameTimeTicks = animationInfo.get().defaultFrameTime();
                    CitSpriteAnimation.register(texture, frameHeight, rawHeight / frameHeight, frameTimeTicks);
                    contents.close();
                    return new SpriteContents(texture, new FrameSize(rawWidth, rawHeight), rawImage);
                }
                rawImage.close();
            }
            return contents;
        } catch (Exception e) {
            CITResewn.LOG.warn("[citresewn] Failed to load CIT item texture as an atlas sprite: " + texture, e);
            return null;
        }
    }
}
