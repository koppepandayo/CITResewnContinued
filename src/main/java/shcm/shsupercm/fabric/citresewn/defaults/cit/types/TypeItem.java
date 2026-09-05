package shcm.shsupercm.fabric.citresewn.defaults.cit.types;

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

import java.io.Reader;
import java.util.*;

/**
 * Scoped-down port: the original supported arbitrary custom {@code model=} JSON models with
 * per-sub-item override conditions and per-layer texture overrides, all built on
 * {@code JsonUnbakedModel}/{@code ModelOverrideList}/{@code BakedModel} — none of which exist
 * anymore (26.2's item model system is {@code ItemModel}/{@code SelectItemModel}/
 * {@code ConditionalItemModel}, entirely component-driven, with no equivalent low-level baking
 * entry point). Per-condition sub-item model overrides (e.g. bow pulling stages) remain out of
 * scope; a plain {@code model=} (one static custom model per CIT rule, the overwhelming majority
 * of real-world usage) is parsed here via {@link CuboidModel#fromStream} — the same public
 * vanilla entry point real resourcepack `models/*.json` files are loaded through — and baked as
 * an extra {@code ItemStackRenderState} layer by {@code ItemModelResolverMixin}, the same
 * "cancel nothing, append a layer" strategy already used for {@link #texture}/{@code tile}.
 */
public class TypeItem extends CITType {
    /** Registered as a "citresewn:type" entrypoint in fabric.mod.json. */
    public static final Container CONTAINER = new Container();

    private final List<Item> items = new ArrayList<>();

    public Identifier texture;
    public CuboidModel model;

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
        if (modelProp != null) {
            Identifier modelId = resolveAsset(properties.identifier, modelProp, "models", ".json", resourceManager);
            if (modelId == null)
                throw new CITParsingException("Could not resolve a replacement model", properties, modelProp.position());
            Optional<Resource> modelResource = resourceManager.getResource(modelId);
            if (modelResource.isEmpty())
                throw new CITParsingException("Could not resolve a replacement model", properties, modelProp.position());
            try (Reader reader = modelResource.get().openAsReader()) {
                model = CuboidModel.fromStream(reader);
            } catch (Exception e) {
                throw new CITParsingException("Could not parse replacement model: " + e.getMessage(), properties, modelProp.position());
            }
        }

        PropertyValue textureProp = properties.getLastWithoutMetadata("citresewn", "texture", "tile");
        if (textureProp != null) {
            texture = resolveAsset(properties.identifier, textureProp, "textures", ".png", resourceManager);
            if (texture == null)
                throw new CITParsingException("Could not resolve a replacement texture", properties, -1);
        }

        if (texture == null && model == null)
            throw new CITParsingException("Could not resolve a replacement texture", properties, -1);
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
