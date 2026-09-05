package shcm.shsupercm.fabric.citresewn.defaults.mixin.types.item;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Quadrant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.cuboid.ItemModelGenerator;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shcm.shsupercm.fabric.citresewn.CITResewn;
import shcm.shsupercm.fabric.citresewn.cit.CIT;
import shcm.shsupercm.fabric.citresewn.cit.CITContext;
import shcm.shsupercm.fabric.citresewn.defaults.cit.types.TypeItem;
import shcm.shsupercm.fabric.citresewn.defaults.item.CitSpriteAnimation;
import shcm.shsupercm.fabric.citresewn.defaults.item.ItemMeshCache;
import shcm.shsupercm.fabric.citresewn.defaults.item.MeshCacheKey;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item CITs are rendered as a hand-baked quad mesh replacing whatever vanilla resolved, since
 * there is no general per-itemstack texture override hook left in the new item model system —
 * mirrors the armor/elytra "cancel vanilla, resubmit manually" strategy.
 * <p>
 * {@link ItemModelResolver#appendItemLayers} is the single choke point all item rendering
 * contexts (hand, GUI, ground, item frame) funnel through — confirmed by disassembly of
 * {@code updateForLiving}/{@code updateForNonLiving}/{@code updateForTopItem}, which all end up
 * calling it — so injecting only here covers every case without a mixin per render path.
 * <p>
 * The mesh mirrors what vanilla's own flat-item baking ({@code ItemModelGenerator}) produces: a
 * front/back quad plus a thin extruded rim following the texture's actual alpha silhouette. This
 * used to be built entirely by hand against a non-atlased texture (no real
 * {@code TextureAtlasSprite}), which is what caused a long tail of symptoms this session - wrong
 * colors/brightness, no animation, visible seams between rim quads - none of which vanilla's own
 * items have, because they always render through a real atlas sprite.
 * {@link SpriteSourceListMixin} now injects every CIT item texture into the real item atlas at
 * stitch time, so this mesh is built against a genuine {@link TextureAtlasSprite} whenever that
 * injection succeeded (falls back to the old non-atlased approach otherwise, so a texture that
 * somehow wasn't injected still renders as something rather than nothing).
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    private static final float Z_FRONT = 7.5f / 16f;
    private static final float Z_BACK = 8.5f / 16f;

    // Both interfaces are all-default-methods / single-abstract-method with no real state needed
    // for a one-shot, non-block, non-rotated bake - a no-op passthrough is the entire contract.
    private static final ModelState NO_TRANSFORM_STATE = new ModelState() {};
    private static final ModelBaker.Interner PASSTHROUGH_INTERNER = new ModelBaker.Interner() {
        @Override
        public Vector3fc vector(Vector3fc vector) {
            return vector;
        }

        @Override
        public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) {
            return material;
        }
    };

    // FaceBakery.bakeQuad(ModelBaker, ...) only ever reads modelBaker.interner() - everything else
    // is for resolving sub-models/materials by reference, which this call site never needs since it
    // already holds a resolved TextureAtlasSprite directly.
    private static final ModelBaker INTERNER_ONLY_MODEL_BAKER = new ModelBaker() {
        @Override
        public ResolvedModel getModel(Identifier location) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BlockStateModelPart missingBlockModelPart() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MaterialBaker materials() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelBaker.Interner interner() {
            return PASSTHROUGH_INTERNER;
        }

        @Override
        public <T> T compute(ModelBaker.SharedOperationKey<T> key) {
            return key.compute(this);
        }
    };

    // Standard vanilla "item/generated" display transforms (assets/minecraft/models/item/generated.json) -
    // without these, held/dropped/GUI items render dead flat instead of vanilla's angled-icon look.
    // ItemTransform#apply(leftHand, pose) mirrors these for the left-hand contexts on its own.
    private static final ItemTransform TRANSFORM_GUI = new ItemTransform(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(1, 1, 1));
    private static final ItemTransform TRANSFORM_GROUND = new ItemTransform(new Vector3f(0, 0, 0), new Vector3f(0, 3f / 16f, 0), new Vector3f(0.25f, 0.25f, 0.25f));
    private static final ItemTransform TRANSFORM_FIXED = new ItemTransform(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(1, 1, 1));
    private static final ItemTransform TRANSFORM_THIRD_PERSON = new ItemTransform(new Vector3f(0, 0, 0), new Vector3f(0, 3f / 16f, 1f / 16f), new Vector3f(0.55f, 0.55f, 0.55f));
    private static final ItemTransform TRANSFORM_FIRST_PERSON = new ItemTransform(new Vector3f(0, -90, 25), new Vector3f(1.13f / 16f, 3.2f / 16f, 1.13f / 16f), new Vector3f(0.68f, 0.68f, 0.68f));

    private static ItemTransform citresewn$transformFor(ItemDisplayContext context) {
        return switch (context) {
            case GUI -> TRANSFORM_GUI;
            case GROUND -> TRANSFORM_GROUND;
            case FIXED, HEAD, ON_SHELF -> TRANSFORM_FIXED;
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> TRANSFORM_THIRD_PERSON;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> TRANSFORM_FIRST_PERSON;
            default -> ItemTransform.NO_TRANSFORM;
        };
    }

    @Inject(method = "appendItemLayers", at = @At("TAIL"))
    private void citresewn$item(ItemStackRenderState state, ItemStack stack, ItemDisplayContext displayContext,
                                 Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        CIT<TypeItem> cit = TypeItem.CONTAINER.getCIT(new CITContext(stack, null, null));
        if (cit == null)
            return;

        // GuiItemAtlas (the inventory-icon cache) keys its slot cache on
        // TrackingItemStackRenderState#getModelIdentity() - a list built purely from
        // appendModelIdentityElement() calls, never on the actual stack/NBT. Vanilla's own
        // resolution already appended whatever identity elements the item's *default* model
        // has (e.g. plain "bedrock") before this TAIL injection runs, so without contributing
        // something here, every CIT rule that reskins the same base item (all of these test
        // dolls are minecraft:bedrock) shares one identity - the icon atlas treats them as the
        // same icon and only ever bakes/caches the first one it sees, no matter how different
        // their held-item rendering is (that path isn't cached this way, which is why it never
        // showed this bug). cit.type is a distinct TypeItem instance per parsed CIT rule, so
        // using it directly disambiguates every rule from every other one, texture or model.
        state.appendModelIdentityElement(cit.type);

        // A rule with texture.<key>= entries (e.g. a bow's texture.bow_pulling_0=) picks its
        // texture the same way vanilla's own bow/elytra item models do - see
        // citresewn$resolveKeyedTextureKey - falling back to the plain texture=/tile= (if any)
        // when no keyed entry matches the current key (e.g. a bow CIT with no texture.bow=
        // override for the resting pose keeps showing vanilla's own resting bow otherwise, but
        // here it just means "no CIT texture for this state," same as any other unmatched rule).
        Identifier effectiveTexture = cit.type.texture;
        if (!cit.type.keyedTextures.isEmpty()) {
            String key = citresewn$resolveKeyedTextureKey(stack, owner);
            Identifier keyed = key != null ? cit.type.keyedTextures.get(key) : null;
            if (keyed != null)
                effectiveTexture = keyed;
        }

        // A model= with no own geometry (relies entirely on "parent": "item/generated" - see
        // citresewn$modelMeshFor) is really just a flat texture-swap icon wearing a model= skin -
        // the overwhelming majority of this pack's model= rules (every stick_menu icon) are exactly
        // this. Routing it through the SAME path as a plain texture=/tile= CIT (rather than the
        // dedicated 3D-model baking below) is what gives it CIT spritesheet animation support for
        // free: citresewn$modelMeshFor bakes once and caches forever with no notion of "current
        // frame" at all, so an animated spinning-head icon authored as model= previously always
        // froze on whatever frame happened to be baked first.
        boolean flatModel = cit.type.model != null && cit.type.model.geometry() == null;
        if (flatModel)
            effectiveTexture = citresewn$modelFlatTexture(cit.type.model);

        List<BakedQuad> mesh;
        if (cit.type.model != null && !flatModel)
            mesh = citresewn$modelMeshFor(cit.type.model);
        else if (effectiveTexture != null)
            mesh = citresewn$meshFor(effectiveTexture);
        else
            mesh = List.of();
        if (mesh.isEmpty())
            return;

        state.clear();
        state.setOversizedInGui(false);
        // GuiItemAtlas snapshots each item's inventory icon to an offscreen texture and only ever
        // redraws it while ItemStackRenderState.isAnimated() is true (confirmed by decompiling
        // GuiItemAtlas#getOrUpdate/drawToSlot - an EMPTY slot draws once and then stays READY
        // forever unless marked animated, in which case it's always treated as STALE and redrawn).
        // Vanilla's own CuboidItemModelWrapper#update calls output.setAnimated() unconditionally
        // whenever item.hasFoil() is true, in addition to CIT spritesheet cycling - the enchant
        // glint shimmer is itself an animated effect, so a foiled item never marked animated gets
        // its GUI icon snapshotted once and never redrawn, which reads as "no glint in the GUI".
        // A CIT spritesheet whose own .mcmeta ships a real (non-empty) "frames" list - unlike the
        // usual "frames": [] CIT convention that deliberately disables vanilla's own animation - is
        // instead loaded as a genuinely vanilla-animated TextureAtlasSprite by SpriteSourceListMixin
        // (its own animation ticks the atlas's pixel data automatically, no per-frame mesh rebaking
        // needed), so CitSpriteAnimation never gets registered for it at all - only checking that
        // registry left every such icon (e.g. gacha.png, whose mcmeta lists real frame indices)
        // permanently frozen on whatever frame happened to be visible at snapshot time.
        boolean vanillaAnimatedSprite = effectiveTexture != null && citresewn$isRealSpriteAnimated(effectiveTexture);
        if ((effectiveTexture != null && CitSpriteAnimation.get(effectiveTexture) != null) || vanillaAnimatedSprite || stack.hasFoil())
            state.setAnimated();
        // A model= CIT's own "display" block (thirdperson/gui/head/... transforms authored
        // alongside its geometry) is respected instead of the generic flat-icon transforms below,
        // the same way vanilla honors a real model's own display block - without this a custom
        // 3D model (rather than a flat texture swap) would render at the wrong size/rotation in
        // every context but never at the size/pose its author actually tuned it for. transforms()
        // is null exactly when geometry() is null for the same reason (e.g. "parent":
        // "item/generated" with no own "display" block, like every stick_menu icon) - falls back
        // to the same standard flat-icon transforms used for a plain texture=/tile= CIT.
        ItemStackRenderState.LayerRenderState layer = state.newLayer();
        layer.clear();
        ItemTransform itemTransform = cit.type.model != null && cit.type.model.transforms() != null
                ? cit.type.model.transforms().getTransform(displayContext)
                : citresewn$transformFor(displayContext);
        layer.setItemTransform(itemTransform);
        layer.setFoilType(stack.hasFoil() ? ItemStackRenderState.FoilType.STANDARD : ItemStackRenderState.FoilType.NONE);
        // Defaults to false (flat, uniformly-lit "2D icon" rig) - correct for the flat texture-swap
        // path, but a real multi-face model= (one with its own "elements") needs the same
        // per-face-normal shading a block gets (GuiItemAtlas#drawToSlot picks ITEMS_3D vs
        // ITEMS_FLAT lighting from exactly this flag), or every face bakes at full brightness with
        // no shading between faces - reads as washed-out/too bright compared to how a real 3D item
        // model is supposed to look. A model= with no own geometry (falls back to
        // ItemModelGenerator - see citresewn$modelMeshFor) is just a flat icon under the hood,
        // exactly like a plain texture=/tile= CIT, and must keep flat lighting too - forcing block
        // lighting on it instead read as unevenly dark/shaded compared to every other flat icon.
        layer.setUsesBlockLight(cit.type.model != null && cit.type.model.geometry() != null);
        layer.prepareQuadList().addAll(mesh);
    }

    /**
     * Picks which {@code texture.<key>=} entry (see {@link TypeItem#keyedTextures}) applies to the
     * current render, by mirroring vanilla's own item model conditions for the handful of vanilla
     * items real CIT packs actually key textures by name for - confirmed against this version's
     * own {@code assets/minecraft/items/bow.json}/{@code elytra.json} and their backing
     * {@code IsUsingItem}/{@code UseDuration}/{@code Broken} property classes, rather than
     * reimplementing the entire (much larger) generic item-properties dispatch system:
     * <ul>
     *   <li>bow/crossbow: "bow" at rest; while drawing, {@code (useDuration ticks) * 0.05} ranged
     *       against vanilla's own 0.65/0.9 thresholds picks "bow_pulling_0/1/2" - the same
     *       {@code minecraft:using_item} + {@code minecraft:use_duration} condition/range-dispatch
     *       bow.json itself uses.</li>
     *   <li>elytra: "broken_elytra" once {@link ItemStack#nextDamageWillBreak()} (vanilla's own
     *       {@code minecraft:broken} condition), otherwise no key (falls through to whatever the
     *       rule's plain {@code texture=} would show, if any - most "broken" CITs like this pack's
     *       don't define one, so nothing at all outside that state, same as not matching).</li>
     * </ul>
     * Returns null for any other item - a rule keying textures by name on some other item simply
     * never finds a match and falls back to its plain {@code texture=}/{@code tile=}, if any.
     */
    private static String citresewn$resolveKeyedTextureKey(ItemStack stack, ItemOwner owner) {
        if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) {
            LivingEntity entity = owner == null ? null : owner.asLivingEntity();
            boolean usingThisStack = entity != null && entity.isUsingItem() && entity.getUseItem() == stack;
            if (!usingThisStack)
                return "bow";

            int ticksInUse = stack.getUseDuration(entity) - entity.getUseItemRemainingTicks();
            float pull = ticksInUse * 0.05f;
            if (pull >= 0.9f)
                return "bow_pulling_2";
            if (pull >= 0.65f)
                return "bow_pulling_1";
            return "bow_pulling_0";
        }

        if (stack.is(Items.ELYTRA))
            return stack.nextDamageWillBreak() ? "broken_elytra" : null;

        return null;
    }

    /**
     * A CIT icon's mcmeta-driven "frametime" cycling (see {@link CitSpriteAnimation}) isn't vanilla
     * per-tick animation, so this doesn't need real game ticks - wall-clock time divided into
     * 50ms steps matches the "ticks" convention the mcmeta's frametime is written against closely
     * enough, and works uniformly in every render context (hand, GUI, ground) without needing a
     * {@code Level} reference that isn't always available where meshes get built/cached.
     */
    private static long citresewn$animationTicks() {
        return System.currentTimeMillis() / 50L;
    }

    /**
     * Deliberately not a simple cache-or-build: a mesh built via the non-atlased fallback (because
     * {@link #citresewn$getRealSprite} came up empty at this exact moment, e.g. right at
     * resourcepack load before the item atlas finished stitching) must never be written into
     * {@link ItemMeshCache}, or that dark, non-atlas-lit result would stick permanently until the
     * next resource reload even after the real sprite becomes resolvable moments later - only a
     * mesh built from a genuine {@link TextureAtlasSprite} gets cached.
     */
    private static final Set<Identifier> citresewn$warnedMissingSprite = ConcurrentHashMap.newKeySet();

    private static List<BakedQuad> citresewn$meshFor(Identifier texture) {
        CitSpriteAnimation animation = CitSpriteAnimation.get(texture);
        int frame = animation != null ? animation.currentFrame(citresewn$animationTicks()) : 0;
        MeshCacheKey key = new MeshCacheKey(texture, frame);

        List<BakedQuad> cached = ItemMeshCache.get(key);
        if (cached != null)
            return cached;

        TextureAtlasSprite sprite = citresewn$getRealSprite(texture);
        if (sprite == null) {
            // Never cached (see above), so this is reached on every render call while unresolved -
            // warn once per texture instead of flooding the log every frame.
            if (citresewn$warnedMissingSprite.add(texture))
                CITResewn.LOG.warn("[citresewn] No real atlas sprite found for {} - using non-atlased fallback mesh (no animation, no atlas lighting)", texture);
            return citresewn$buildMeshFallback(texture);
        }

        try {
            // Only a CIT spritesheet's per-frame cycling (no vanilla equivalent - see
            // CitSpriteAnimation) is hand-built; every ordinary (non-animated) CIT icon is baked by
            // vanilla's own ItemModelGenerator, exactly like any real tool/armor/item icon in the
            // game, instead of reimplementing that algorithm here.
            List<BakedQuad> mesh = animation != null
                    ? citresewn$buildAnimatedFrameMesh(sprite, animation, frame)
                    : citresewn$buildVanillaGeneratedMesh(sprite);
            CITResewn.LOG.info("[citresewn] Built mesh for {} frame {} from real atlas sprite (sheet {}x{}, cit-animated={})",
                    texture, frame, sprite.contents().width(), sprite.contents().height(), animation != null);
            ItemMeshCache.put(key, mesh);
            return mesh;
        } catch (Exception e) {
            CITResewn.LOG.warn("[citresewn] Failed building mesh from real atlas sprite, falling back for " + texture, e);
            return citresewn$buildMeshFallback(texture);
        }
    }

    private static TextureAtlas citresewn$itemsAtlas() {
        var boundTexture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_ITEMS);
        return boundTexture instanceof TextureAtlas atlas ? atlas : null;
    }

    /**
     * {@code SpriteSourceListMixin} injects every CIT item texture into the real item atlas while
     * it's being stitched. If that succeeded, the texture's identifier resolves to a genuine
     * sprite here - same atlas, same lighting, same animation ticking as any vanilla item.
     */
    private static TextureAtlasSprite citresewn$getRealSprite(Identifier texture) {
        try {
            TextureAtlas atlas = citresewn$itemsAtlas();
            if (atlas == null)
                return null;
            TextureAtlasSprite sprite = atlas.getSprite(texture);
            if (sprite == null || sprite == atlas.missingSprite())
                return null;
            return sprite;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * True for a CIT sprite whose own .mcmeta ships a real (non-empty) "frames" list - i.e. one
     * {@link SpriteSourceListMixin#loadCitSprite} let load as an ordinary, genuinely
     * vanilla-animated {@link TextureAtlasSprite} instead of the usual CIT "frames": []
     * (deliberately-disabled) convention {@link CitSpriteAnimation} tracks by hand. Such a sprite's
     * pixel data is ticked automatically by vanilla itself - no per-frame mesh rebaking needed -
     * but {@code ItemStackRenderState.setAnimated()} still needs to know about it, or
     * GuiItemAtlas's offscreen icon snapshot never gets asked to redraw and stays frozen on
     * whichever frame happened to be visible the moment it was first cached.
     */
    private static boolean citresewn$isRealSpriteAnimated(Identifier texture) {
        TextureAtlasSprite sprite = citresewn$getRealSprite(texture);
        return sprite != null && sprite.contents().isAnimated();
    }

    /**
     * Bakes a plain, non-animated CIT icon through vanilla's real {@link ItemModelGenerator} -
     * the same {@code FaceBakery.bakeQuad}-based front/back-plus-silhouette-rim algorithm, correct
     * winding, correct UVs, correct lighting, that every ordinary flat item in the game uses -
     * instead of reimplementing it by hand. {@code ItemModelGenerator.geometry()} exposes a public
     * reference to its own (otherwise-private) baking method, so this only needs to supply the
     * handful of small, mostly-default collaborators that method expects: a {@link TextureSlots}
     * pointing "layer0" at this CIT texture, a {@link MaterialBaker} that resolves that reference
     * through the real sprite lookup this class already has, and a minimal, no-op-everywhere-else
     * {@link ModelBaker} to drive it (block/sub-model lookups are unreachable here - a generated
     * item icon has neither).
     */
    private static List<BakedQuad> citresewn$buildVanillaGeneratedMesh(TextureAtlasSprite sprite) {
        Identifier texture = sprite.contents().name();
        ModelDebugName debugName = () -> "citresewn item icon: " + texture;

        TextureSlots.Data.Builder textures = new TextureSlots.Data.Builder();
        textures.addTexture("layer0", new Material(texture, false));
        return citresewn$bakeGeometry(new ItemModelGenerator().geometry(), textures.build(), debugName);
    }

    /**
     * A CIT {@code model=} rule (see {@link TypeItem#model}) with its own "elements" is a genuine
     * multi-element custom model - unlike the flat texture-swap path, its geometry is baked
     * as-authored via {@link CuboidModel#geometry()} instead of reimplementing
     * {@link ItemModelGenerator}'s front/back-plus-rim algorithm. Only ever called for a model
     * with real geometry of its own - see {@code citresewn$item}'s {@code flatModel} check, which
     * routes a model with no "elements" (relies entirely on "parent": "item/generated" for shape,
     * e.g. every stick_menu icon) through {@link #citresewn$modelFlatTexture}/
     * {@link #citresewn$meshFor} instead, the same path a plain {@code texture=}/{@code tile=} CIT
     * uses - that path (unlike this one) supports CIT spritesheet frame animation. One model
     * instance never changes shape/textures across renders, so the baked mesh is cached by the
     * {@link CuboidModel} instance itself (a record, so this is also correct if the same parsed
     * model were ever reused - equals/hashCode are structural, not identity).
     */
    private static final Map<CuboidModel, List<BakedQuad>> citresewn$modelMeshCache = new ConcurrentHashMap<>();

    private static List<BakedQuad> citresewn$modelMeshFor(CuboidModel model) {
        return citresewn$modelMeshCache.computeIfAbsent(model, m -> {
            ModelDebugName debugName = () -> "citresewn item model: " + m.parent();
            return citresewn$bakeGeometry(m.geometry(), m.textureSlots(), debugName);
        });
    }

    /**
     * Resolves the "layer0" material of a flat (no own "elements") {@code model=} - the same slot
     * name {@link #citresewn$buildVanillaGeneratedMesh} feeds vanilla's own {@link ItemModelGenerator}
     * for a plain {@code texture=}/{@code tile=} icon - so its texture can be routed through the
     * exact same (animation-aware) flat-icon path instead of the static, cache-forever
     * {@link #citresewn$modelMeshFor}.
     */
    private static Identifier citresewn$modelFlatTexture(CuboidModel model) {
        TextureSlots resolved = new TextureSlots.Resolver().addLast(model.textureSlots()).resolve(() -> "citresewn flat model= texture");
        Material material = resolved.getMaterial("layer0");
        return material != null ? material.sprite() : null;
    }

    /**
     * Shared by both the flat texture-swap icon ({@link #citresewn$buildVanillaGeneratedMesh}) and
     * a full custom {@code model=} ({@link #citresewn$modelMeshFor}): resolves every texture slot
     * through the real item atlas (see {@link #citresewn$getRealSprite}) and bakes the given
     * geometry against it, exactly like vanilla's own model baker does for real block/item models.
     */
    private static List<BakedQuad> citresewn$bakeGeometry(UnbakedGeometry geometry, TextureSlots.Data textures, ModelDebugName debugName) {
        TextureSlots textureSlots = new TextureSlots.Resolver().addLast(textures).resolve(debugName);

        MaterialBaker materialBaker = new MaterialBaker(citresewn$itemsAtlas().missingSprite()) {
            @Override
            protected Material.Baked bake(Material material) {
                TextureAtlasSprite resolved = citresewn$getRealSprite(material.sprite());
                return resolved != null ? new Material.Baked(resolved, material.forceTranslucent()) : null;
            }
        };
        ModelBaker modelBaker = new ModelBaker() {
            @Override
            public ResolvedModel getModel(Identifier location) {
                throw new UnsupportedOperationException("citresewn baked items reference no sub-models");
            }

            @Override
            public BlockStateModelPart missingBlockModelPart() {
                throw new UnsupportedOperationException("citresewn baked items are not block models");
            }

            @Override
            public MaterialBaker materials() {
                return materialBaker;
            }

            @Override
            public ModelBaker.Interner interner() {
                return PASSTHROUGH_INTERNER;
            }

            @Override
            public <T> T compute(ModelBaker.SharedOperationKey<T> key) {
                return key.compute(this);
            }
        };

        QuadCollection quadCollection = geometry.bake(textureSlots, modelBaker, NO_TRANSFORM_STATE, debugName);
        return new ArrayList<>(quadCollection.getAll());
    }

    /**
     * CIT spritesheet frame-cycling (see {@link CitSpriteAnimation}) has no vanilla equivalent -
     * vanilla's own sprite-animation system is deliberately disabled for these ("frames": []), and
     * {@link ItemModelGenerator} always bakes a sprite's *entire* declared size as one icon, with
     * no notion of "the current frame of a taller sheet". So only this path is still hand-built:
     * a front/back quad plus a thin rim quad along every alpha-boundary edge of every opaque
     * pixel of the *current* frame, mirroring {@code ItemModelGenerator}'s own algorithm (down to
     * matching its exact left/right {@link Direction} convention) but reading pixels/UVs from the
     * current frame's row of the real, full sheet instead of the sheet's declared (single-frame)
     * extent.
     */
    private static List<BakedQuad> citresewn$buildAnimatedFrameMesh(TextureAtlasSprite sprite, CitSpriteAnimation animation, int frame) {
        SpriteContents contents = sprite.contents();
        int rawW = contents.width(), rawH = contents.height();
        // "Logical" icon size (w×h) is one frame of the sheet: CitSpriteAnimation's declared
        // per-frame height, not the sprite's full (multi-frame-tall) raw height. frameOffsetY is
        // that frame's row offset into the real underlying sheet, used only when sampling pixels/UVs
        // - never for the 0..16 mesh geometry itself, which stays frame-relative like any normal
        // (single-frame) vanilla item icon.
        int w = rawW;
        int h = animation.frameHeightPx;
        int frameOffsetY = frame * animation.frameHeightPx;
        float vScale16 = 16f / rawH;

        Material.Baked material = new Material.Baked(sprite, false);
        List<BakedQuad> quads = new ArrayList<>();

        Vector3f from = new Vector3f(0f, 0f, 7.5f);
        Vector3f to = new Vector3f(16f, 16f, 8.5f);
        CuboidFace.UVs southUvs = new CuboidFace.UVs(0f, frameOffsetY * vScale16, 16f, (frameOffsetY + h) * vScale16);
        CuboidFace.UVs northUvs = new CuboidFace.UVs(16f, frameOffsetY * vScale16, 0f, (frameOffsetY + h) * vScale16);
        quads.add(citresewn$bakeQuad(from, to, southUvs, material, Direction.SOUTH));
        quads.add(citresewn$bakeQuad(from, to, northUvs, material, Direction.NORTH));

        float xScale = 16f / w, yScale = 16f / h;
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                if (!citresewn$opaque(contents, w, h, frameOffsetY, px, py))
                    continue;

                if (!citresewn$opaque(contents, w, h, frameOffsetY, px, py - 1))
                    quads.add(citresewn$bakeSideFace(material, xScale, yScale, vScale16, frameOffsetY, px, py, Direction.UP));
                if (!citresewn$opaque(contents, w, h, frameOffsetY, px, py + 1))
                    quads.add(citresewn$bakeSideFace(material, xScale, yScale, vScale16, frameOffsetY, px, py, Direction.DOWN));
                // Vanilla's own ItemModelGenerator.SideDirection maps LEFT (checked via the x-1
                // neighbor, exactly like this) to Direction.EAST, not WEST - confirmed by decompiling
                // it. Direction picks the quad's normal for lighting purposes, so having this
                // backwards lit every left/right rim quad as if it faced the opposite way.
                if (!citresewn$opaque(contents, w, h, frameOffsetY, px - 1, py))
                    quads.add(citresewn$bakeSideFace(material, xScale, yScale, vScale16, frameOffsetY, px, py, Direction.EAST));
                if (!citresewn$opaque(contents, w, h, frameOffsetY, px + 1, py))
                    quads.add(citresewn$bakeSideFace(material, xScale, yScale, vScale16, frameOffsetY, px, py, Direction.WEST));
            }
        }

        return quads;
    }

    /**
     * Bakes one quad through vanilla's real {@link FaceBakery#bakeQuad} instead of constructing a
     * {@link BakedQuad} by hand. This is what {@link ItemModelGenerator} itself does for every quad
     * it produces, and it matters for more than just geometry: it also runs vanilla's own winding
     * normalization ({@code recalculateWinding}, based on the quad's actual computed bounding box)
     * and computes a real per-quad {@link com.mojang.blaze3d.platform.Transparency}-derived
     * {@code MaterialInfo} (translucent vs. cutout render type, correct {@code ChunkSectionLayer}).
     * The hand-rolled {@code BakedQuad}/{@code MaterialInfo} construction this replaced always used
     * a hardcoded cutout render type and left winding unnormalized - the former made any genuinely
     * semi-transparent CIT pixels render wrong, and the latter is the most likely cause of the
     * animated-frame path still looking dark after every other lighting-affecting bug was fixed.
     */
    private static BakedQuad citresewn$bakeQuad(Vector3f from, Vector3f to, CuboidFace.UVs uvs, Material.Baked material, Direction facing) {
        CuboidFace face = new CuboidFace(null, -1, "", uvs, Quadrant.R0);
        return FaceBakery.bakeQuad(INTERNER_ONLY_MODEL_BAKER, from, to, face, material, facing, NO_TRANSFORM_STATE, null, true, 0);
    }

    /**
     * Mirrors {@code ItemModelGenerator.bakeSideFaces}'s per-quad geometry/UV construction exactly
     * (translated from its private {@code SideDirection} enum to plain {@link Direction} - LEFT is
     * always {@code Direction.EAST} and RIGHT is always {@code Direction.WEST} there), with one
     * addition: the V coordinate is computed against the frame's row in the *real* sheet
     * ({@code frameOffsetY + v}, scaled by {@code vScale16 = 16/rawH}) instead of directly against
     * {@code h}, since {@code h} here is one frame's height, not the sprite's own full declared
     * height like it always is in vanilla's single-frame case.
     */
    private static BakedQuad citresewn$bakeSideFace(Material.Baked material, float xScale, float yScale, float vScale16,
                                                      int frameOffsetY, int x, int y, Direction direction) {
        boolean horizontal = direction == Direction.UP || direction == Direction.DOWN;
        float u0 = x + 0.1f, u1 = x + 1.0f - 0.1f;
        float v0, v1;
        if (horizontal) {
            v0 = y + 0.1f;
            v1 = y + 1.0f - 0.1f;
        } else {
            v0 = y + 1.0f - 0.1f;
            v1 = y + 0.1f;
        }

        float startX = x, startY = y, endX = x, endY = y;
        switch (direction) {
            case UP -> endX++;
            case DOWN -> { endX++; startY++; endY++; }
            case EAST -> endY++;
            case WEST -> { startX++; endX++; endY++; }
            default -> throw new IllegalStateException("unexpected side direction " + direction);
        }
        startX *= xScale; endX *= xScale; startY *= yScale; endY *= yScale;
        startY = 16f - startY;
        endY = 16f - endY;

        Vector3f from = new Vector3f(), to = new Vector3f();
        switch (direction) {
            case UP -> { from.set(startX, startY, 7.5f); to.set(endX, startY, 8.5f); }
            case DOWN -> { from.set(startX, endY, 7.5f); to.set(endX, endY, 8.5f); }
            case EAST -> { from.set(startX, startY, 7.5f); to.set(startX, endY, 8.5f); }
            case WEST -> { from.set(endX, startY, 7.5f); to.set(endX, endY, 8.5f); }
            default -> throw new IllegalStateException("unexpected side direction " + direction);
        }

        CuboidFace.UVs uvs = new CuboidFace.UVs(u0 * xScale, (frameOffsetY + v0) * vScale16, u1 * xScale, (frameOffsetY + v1) * vScale16);
        return citresewn$bakeQuad(from, to, uvs, material, direction);
    }

    /**
     * {@code x}/{@code y} are frame-relative (0..w, 0..h of one logical icon frame), while
     * {@code frameOffsetY} shifts the actual pixel lookup down to the correct row of the real
     * (possibly multi-frame-tall) sheet. Bounds-checking against the frame-relative range - not the
     * sheet's real height - matters here: without it, checking the row just past the bottom of one
     * frame would read the *next* frame's first row instead of correctly treating that edge as
     * transparent, silently stitching adjacent animation frames together into the rim mesh.
     */
    private static boolean citresewn$opaque(SpriteContents contents, int w, int h, int frameOffsetY, int x, int y) {
        if (x < 0 || x >= w || y < 0 || y >= h)
            return false;
        try {
            // SpriteContents#isTransparent takes (frame, x, y) - not (x, y, frame). The sprite is
            // never reported as vanilla-animated here (see CitSpriteAnimation), so the "frame" argument
            // is always ignored internally and only the raw (x, y) lookup matters - frameOffsetY does
            // the real per-frame row selection instead.
            return !contents.isTransparent(0, x, frameOffsetY + y);
        } catch (Exception e) {
            return false;
        }
    }

    private static void citresewn$addQuad(List<BakedQuad> quads, BakedQuad.MaterialInfo materialInfo,
                                           Vector3f v0, Vector3f v1, Vector3f v2, Vector3f v3,
                                           long uv0, long uv1, long uv2, long uv3, Direction direction) {
        quads.add(new BakedQuad(v0, v1, v2, v3, uv0, uv1, uv2, uv3, direction, materialInfo));
    }

    // --- Fallback path: used only if a texture somehow wasn't injected into the real atlas
    // (e.g. SpriteSourceListMixin's injection didn't run in time for this one, or failed to read
    // it). Non-atlased, hand-built exactly as before this session's atlas-integration work -
    // still has the known issues (no real animation, no atlas-driven lighting), but means a CIT
    // item texture that hits this path renders as *something* instead of nothing.

    private static List<BakedQuad> citresewn$buildMeshFallback(Identifier texture) {
        RenderType renderType = RenderTypes.itemCutout(texture);
        BakedQuad.MaterialInfo materialInfo = new BakedQuad.MaterialInfo(null, null, renderType, -1, false, 0);
        List<BakedQuad> quads = new ArrayList<>();

        long uv00 = UVPair.pack(0f, 0f), uv10 = UVPair.pack(1f, 0f), uv11 = UVPair.pack(1f, 1f), uv01 = UVPair.pack(0f, 1f);
        citresewn$addQuad(quads, materialInfo,
                new Vector3f(0, 1, Z_FRONT), new Vector3f(1, 1, Z_FRONT), new Vector3f(1, 0, Z_FRONT), new Vector3f(0, 0, Z_FRONT),
                uv00, uv10, uv11, uv01,
                Direction.SOUTH);
        citresewn$addQuad(quads, materialInfo,
                new Vector3f(1, 1, Z_BACK), new Vector3f(0, 1, Z_BACK), new Vector3f(0, 0, Z_BACK), new Vector3f(1, 0, Z_BACK),
                uv10, uv00, uv01, uv11,
                Direction.NORTH);

        try (InputStream is = Minecraft.getInstance().getResourceManager().open(texture);
             NativeImage image = NativeImage.read(is)) {
            int w = image.getWidth();
            int h = image.getHeight();
            if (Minecraft.getInstance().getResourceManager().getResource(texture.withPath(p -> p + ".mcmeta")).isPresent())
                h = Math.min(h, w);

            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    if (!citresewn$opaqueFallback(image, w, h, px, py))
                        continue;

                    // No position-space inset here (unlike an earlier version of this method) -
                    // vanilla's own ItemModelGenerator never insets rim-quad *positions*, only their
                    // UVs by a small texel margin. Insetting positions instead left a visible gap
                    // between every adjacent rim quad, seen as fine grid lines cutting through the
                    // extruded silhouette (e.g. between the "pixels" of a sword blade).
                    float x0 = px / (float) w, x1 = (px + 1) / (float) w;
                    float yTop = 1f - py / (float) h, yBottom = 1f - (py + 1) / (float) h;
                    float uCenter = (px + 0.5f) / w, vCenter = (py + 0.5f) / h;
                    long uvSelf = UVPair.pack(uCenter, vCenter);

                    if (!citresewn$opaqueFallback(image, w, h, px, py - 1)) {
                        citresewn$addQuad(quads, materialInfo,
                                new Vector3f(x0, yTop, Z_FRONT), new Vector3f(x0, yTop, Z_BACK), new Vector3f(x1, yTop, Z_BACK), new Vector3f(x1, yTop, Z_FRONT),
                                uvSelf, uvSelf, uvSelf, uvSelf,
                                Direction.UP);
                    }
                    if (!citresewn$opaqueFallback(image, w, h, px, py + 1)) {
                        citresewn$addQuad(quads, materialInfo,
                                new Vector3f(x0, yBottom, Z_BACK), new Vector3f(x0, yBottom, Z_FRONT), new Vector3f(x1, yBottom, Z_FRONT), new Vector3f(x1, yBottom, Z_BACK),
                                uvSelf, uvSelf, uvSelf, uvSelf,
                                Direction.DOWN);
                    }
                    if (!citresewn$opaqueFallback(image, w, h, px - 1, py)) {
                        // Same EAST/WEST convention as ItemModelGenerator.SideDirection - see the
                        // matching comment in citresewn$buildAnimatedFrameMesh.
                        citresewn$addQuad(quads, materialInfo,
                                new Vector3f(x0, yBottom, Z_FRONT), new Vector3f(x0, yBottom, Z_BACK), new Vector3f(x0, yTop, Z_BACK), new Vector3f(x0, yTop, Z_FRONT),
                                uvSelf, uvSelf, uvSelf, uvSelf,
                                Direction.EAST);
                    }
                    if (!citresewn$opaqueFallback(image, w, h, px + 1, py)) {
                        citresewn$addQuad(quads, materialInfo,
                                new Vector3f(x1, yBottom, Z_BACK), new Vector3f(x1, yBottom, Z_FRONT), new Vector3f(x1, yTop, Z_FRONT), new Vector3f(x1, yTop, Z_BACK),
                                uvSelf, uvSelf, uvSelf, uvSelf,
                                Direction.WEST);
                    }
                }
            }
        } catch (Exception e) {
            CITResewn.LOG.warn("[citresewn] Could not read texture for fallback silhouette extrusion: " + texture, e);
        }

        return quads;
    }

    private static boolean citresewn$opaqueFallback(NativeImage image, int w, int h, int x, int y) {
        if (x < 0 || x >= w || y < 0 || y >= h)
            return false;
        return ARGB.alpha(image.getPixel(x, y)) != 0;
    }
}
