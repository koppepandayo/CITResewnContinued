package shcm.shsupercm.fabric.citresewn.defaults.cit.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import shcm.shsupercm.fabric.citresewn.api.CITTypeContainer;
import shcm.shsupercm.fabric.citresewn.cit.*;
import shcm.shsupercm.fabric.citresewn.defaults.cit.conditions.ConditionItems;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyGroup;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyKey;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyValue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;

/**
 * Scoped-down port: the original supported arbitrary custom {@code model=} JSON models with
 * per-sub-item override conditions and per-layer texture overrides, all built on
 * {@code JsonUnbakedModel}/{@code ModelOverrideList}/{@code BakedModel} — none of which exist
 * anymore (26.2's item model system is {@code ItemModel}/{@code SelectItemModel}/
 * {@code ConditionalItemModel}, entirely component-driven, with no equivalent low-level baking
 * entry point). Per-condition sub-item *model* overrides remain out of scope; a plain
 * {@code model=} (one static custom model per CIT rule, the overwhelming majority of real-world
 * usage) is parsed here via {@link CuboidModel#fromStream} — the same public vanilla entry point
 * real resourcepack `models/*.json` files are loaded through — and baked as an extra
 * {@code ItemStackRenderState} layer by {@code ItemModelResolverMixin}, the same "cancel nothing,
 * append a layer" strategy already used for {@link #texture}/{@code tile}.
 * <p>
 * Per-condition sub-item *texture* overrides ({@code texture.<key>=}, e.g. a bow's
 * {@code texture.bow_pulling_0=}) are a separate, much more common OptiFine convention — every
 * {@link PropertyValue#keyMetadata()}-tagged {@code texture}/{@code tile} entry lands in
 * {@link #keyedTextures}, keyed by the same suffix names vanilla's own item model overrides use
 * (e.g. "bow", "bow_pulling_1", "broken_elytra"). {@code ItemModelResolverMixin} picks which key
 * applies to the current render by mirroring vanilla's own bow/elytra override conditions
 * (see its {@code citresewn$resolveKeyedTextureKey}) rather than reimplementing the entire
 * item-properties dispatch system generically.
 */
public class TypeItem extends CITType {
    /** Registered as a "citresewn:type" entrypoint in fabric.mod.json. */
    public static final Container CONTAINER = new Container();

    private final List<Item> items = new ArrayList<>();

    public Identifier texture;
    public CuboidModel model;
    public final Map<String, Identifier> keyedTextures = new LinkedHashMap<>();

    @Override
    public Set<PropertyKey> typeProperties() {
        return Set.of(PropertyKey.of("model"), PropertyKey.of("texture"), PropertyKey.of("tile"));
    }

    @Override
    public void load(List<CITCondition> conditions, PropertyGroup properties, ResourceManager resourceManager) throws CITParsingException {
        for (CITCondition condition : conditions)
            if (condition instanceof ConditionItems conditionItems)
                items.addAll(Arrays.asList(conditionItems.items));

        if (items.isEmpty())
            throw new CITParsingException("Not targeting any item type", properties, -1);

        PropertyValue modelProp = properties.getLastWithoutMetadata("citresewn", "model");
        PropertyValue textureProp = properties.getLastWithoutMetadata("citresewn", "texture", "tile");

        if (modelProp != null) {
            Identifier modelId = resolveAsset(properties.identifier, modelProp, "models", ".json", resourceManager);
            if (modelId == null)
                throw new CITParsingException("Could not resolve a replacement model", properties, modelProp.position());
            Optional<Resource> modelResource = resourceManager.getResource(modelId);
            if (modelResource.isEmpty())
                throw new CITParsingException("Could not resolve a replacement model", properties, modelProp.position());
            try (Reader reader = modelResource.get().openAsReader()) {
                model = parseModelResolvingTextures(modelId, reader, resourceManager);
            } catch (Exception e) {
                throw new CITParsingException("Could not parse replacement model: " + e.getMessage(), properties, modelProp.position());
            }
        } else if (textureProp == null) {
            // Neither model= nor texture=/tile= was given at all - OptiFine's implicit "same name as
            // this properties file" convention (see resolveAsset's null-path branch), tried model
            // (.json) first then texture (.png), mirroring upstream's own fallback order exactly.
            // Several real-world CITs in this pack (e.g. point_shop.properties + point_shop.png,
            // 127.properties + 127.json/127.png) rely entirely on this and previously always failed
            // to load with "Could not resolve a replacement texture", since this branch was never
            // even attempted without an explicit property line.
            Identifier implicitModelId = resolveAsset(properties.identifier, (PropertyValue) null, "models", ".json", resourceManager);
            if (implicitModelId != null) {
                try (Reader reader = resourceManager.getResource(implicitModelId).get().openAsReader()) {
                    model = parseModelResolvingTextures(implicitModelId, reader, resourceManager);
                } catch (Exception e) {
                    throw new CITParsingException("Could not parse replacement model: " + e.getMessage(), properties, -1);
                }
            } else {
                texture = resolveAsset(properties.identifier, (PropertyValue) null, "textures", ".png", resourceManager);
            }
        }

        if (textureProp != null) {
            texture = resolveAsset(properties.identifier, textureProp, "textures", ".png", resourceManager);
            if (texture == null)
                throw new CITParsingException("Could not resolve a replacement texture", properties, -1);
        }

        for (PropertyValue value : properties.get("citresewn", "texture", "tile")) {
            if (value.keyMetadata() == null)
                continue;
            Identifier keyedTexture = resolveAsset(properties.identifier, value, "textures", ".png", resourceManager);
            if (keyedTexture == null)
                throw new CITParsingException("Could not resolve a replacement texture", properties, value.position());
            keyedTextures.put(value.keyMetadata(), keyedTexture);
        }

        if (texture == null && model == null && keyedTextures.isEmpty())
            throw new CITParsingException("Could not resolve a replacement texture", properties, -1);
    }

    /**
     * A {@code model=} file's own {@code "textures"} values use the same OptiFine CIT relative-path
     * convention as {@code texture=}/{@code tile=} (see {@link #resolveAsset}) - {@code "./foo"}, a
     * bare filename, etc, resolved relative to the <em>model file's own location</em> - not the
     * Mojang-style absolute/{@code textures/}-relative references a real resourcepack model JSON
     * normally uses. Vanilla's own {@link CuboidModel}/{@code TextureSlots} deserializer has no
     * notion of this convention and parses such a value into a nonsensical literal {@link Identifier}
     * (e.g. {@code "./arrow_back"} becomes {@code minecraft:./arrow_back}), which then never
     * resolves to any real atlas sprite anywhere downstream - confirmed by "Invalid segment '.' in
     * path" log spam and every such icon rendering as the atlas's missing-texture checkerboard. Every
     * texture value is rewritten to its correctly-resolved real resource path (matching exactly how
     * {@code texture=}/{@code tile=} resolves) before handing the JSON to vanilla's own parser. A
     * value starting with {@code #} is a vanilla texture-variable reference, not a path, and is left
     * untouched - {@code TextureSlots} resolves those internally on its own.
     */
    public static CuboidModel parseModelResolvingTextures(Identifier modelIdentifier, Reader reader, ResourceManager resourceManager) throws IOException {
        JsonElement root = JsonParser.parseReader(reader);
        if (root.isJsonObject()) {
            JsonObject rootObject = root.getAsJsonObject();
            JsonElement texturesElement = rootObject.get("textures");
            if (texturesElement != null && texturesElement.isJsonObject()) {
                JsonObject textures = texturesElement.getAsJsonObject();
                for (String key : new ArrayList<>(textures.keySet())) {
                    JsonElement value = textures.get(key);
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                        continue;
                    String rawValue = value.getAsString();
                    if (rawValue.startsWith("#"))
                        continue;
                    Identifier resolved = resolveAsset(modelIdentifier, rawValue, "textures", ".png", resourceManager);
                    if (resolved != null)
                        textures.addProperty(key, resolved.toString());
                }
            }
        }
        return CuboidModel.fromStream(new StringReader(root.toString()));
    }

    public static class Container extends CITTypeContainer<TypeItem> {
        public Container() {
            super(TypeItem.class, TypeItem::new, "item");
        }

        public Set<CIT<TypeItem>> loaded = new HashSet<>();
        public Map<Item, Set<CIT<TypeItem>>> loadedTyped = new IdentityHashMap<>();

        @Override
        public void load(List<CIT<TypeItem>> parsedCITs) {
            loaded.addAll(parsedCITs);
            for (CIT<TypeItem> cit : parsedCITs)
                for (CITCondition condition : cit.conditions)
                    if (condition instanceof ConditionItems items)
                        for (Item item : items.items)
                            if (item != null)
                                loadedTyped.computeIfAbsent(item, i -> new LinkedHashSet<>()).add(cit);
        }

        @Override
        public void dispose() {
            loaded.clear();
            loadedTyped.clear();
        }

        public CIT<TypeItem> getCIT(CITContext context) {
            return ((CITCacheItem) (Object) context.stack).citresewn$getCacheTypeItem().get(context).get();
        }

        public CIT<TypeItem> getRealTimeCIT(CITContext context) {
            Set<CIT<TypeItem>> loadedForItemType = loadedTyped.get(context.stack.getItem());
            if (loadedForItemType != null)
                for (CIT<TypeItem> cit : loadedForItemType)
                    if (cit.test(context))
                        return cit;

            return null;
        }
    }

    public interface CITCacheItem {
        CITCache.Single<TypeItem> citresewn$getCacheTypeItem();
    }
}
