package shcm.shsupercm.fabric.citresewn.defaults.cit.types;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import shcm.shsupercm.fabric.citresewn.api.CITTypeContainer;
import shcm.shsupercm.fabric.citresewn.cit.*;
import shcm.shsupercm.fabric.citresewn.defaults.cit.conditions.ConditionItems;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyGroup;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyKey;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyValue;

import java.util.*;
import java.util.function.BiFunction;

/**
 * "Is this item worn armor" used to be a simple {@code instanceof ArmorItem} check; as of the
 * data-component rewrite armor-ness is instead expressed by an {@link Equippable} component whose
 * {@link Equippable#slot()} is one of the four {@link EquipmentSlot#isArmor() armor slots}
 * (HEAD/CHEST/LEGS/FEET) — there is no dedicated class anymore.
 */
public class TypeArmor extends CITType {
    /** Registered as a "citresewn:type" entrypoint in fabric.mod.json. */
    public static final Container CONTAINER = new Container();

    public final Map<String, Identifier> textures = new HashMap<>();

    @Override
    public Set<PropertyKey> typeProperties() {
        return Set.of(PropertyKey.of("texture"));
    }

    private static boolean isArmorItem(Item item) {
        try {
            Equippable equippable = item.components().get(DataComponents.EQUIPPABLE);
            return equippable != null && equippable.slot().isArmor();
        } catch (NullPointerException e) {
            // Item registry Holders aren't bound to their components yet during the very first
            // resource reload (before the game finishes bootstrapping) - treat as "unknown" rather
            // than rejecting the CIT outright. getRealTimeCIT() re-checks this safely once
            // components are bound, so this only weakens the load()-time validation pass.
            return true;
        }
    }

    @Override
    public void load(List<CITCondition> conditions, PropertyGroup properties, ResourceManager resourceManager) throws CITParsingException {
        boolean itemsConditionPresent = false;
        for (CITCondition condition : conditions)
            if (condition instanceof ConditionItems conditionItems)
                for (Item item : conditionItems.items)
                    if (isArmorItem(item))
                        itemsConditionPresent = true;
                    else
                        throw new CITParsingException("This type only accepts armor items for the items condition", properties, -1);

        if (!itemsConditionPresent)
            try {
                Identifier propertiesName = Identifier.tryParse(properties.stripName());
                if (!BuiltInRegistries.ITEM.containsKey(propertiesName))
                    throw new Exception();
                Item item = BuiltInRegistries.ITEM.getValue(propertiesName);
                if (!isArmorItem(item))
                    throw new Exception();
                conditions.add(new ConditionItems(item));
            } catch (Exception ignored) {
                throw new CITParsingException("Not targeting any item type", properties, -1);
            }

        for (PropertyValue propertyValue : properties.get("citresewn", "texture")) {
            Identifier identifier = resolveAsset(properties.identifier, propertyValue, "textures", ".png", resourceManager);
            if (identifier == null)
                throw new CITParsingException("Could not resolve texture", properties, propertyValue.position());

            textures.put(propertyValue.keyMetadata(), identifier);
        }
        if (textures.size() == 0)
            throw new CITParsingException("Texture not specified", properties, -1);
    }

    public static class Container extends CITTypeContainer<TypeArmor> {
        public Container() {
            super(TypeArmor.class, TypeArmor::new, "armor");
        }

        public final List<BiFunction<LivingEntity, EquipmentSlot, ItemStack>> getItemInSlotCompatRedirects = new ArrayList<>();

        public Set<CIT<TypeArmor>> loaded = new HashSet<>();
        public Map<Item, Set<CIT<TypeArmor>>> loadedTyped = new IdentityHashMap<>();

        @Override
        public void load(List<CIT<TypeArmor>> parsedCITs) {
            loaded.addAll(parsedCITs);
            for (CIT<TypeArmor> cit : parsedCITs)
                for (CITCondition condition : cit.conditions)
                    if (condition instanceof ConditionItems items)
                        for (Item item : items.items)
                            if (isArmorItem(item))
                                loadedTyped.computeIfAbsent(item, i -> new LinkedHashSet<>()).add(cit);
        }

        @Override
        public void dispose() {
            loaded.clear();
            loadedTyped.clear();
        }

        public CIT<TypeArmor> getCIT(CITContext context) {
            return ((CITCacheArmor) (Object) context.stack).citresewn$getCacheTypeArmor().get(context).get();
        }

        public CIT<TypeArmor> getRealTimeCIT(CITContext context) {
            if (!isArmorItem(context.stack.getItem()))
                return null;

            Set<CIT<TypeArmor>> loadedForItemType = loadedTyped.get(context.stack.getItem());
            if (loadedForItemType != null)
                for (CIT<TypeArmor> cit : loadedForItemType)
                    if (cit.test(context))
                        return cit;

            return null;
        }

        public ItemStack getVisualItemInSlot(LivingEntity entity, EquipmentSlot slot) {
            for (BiFunction<LivingEntity, EquipmentSlot, ItemStack> redirect : getItemInSlotCompatRedirects) {
                ItemStack stack = redirect.apply(entity, slot);
                if (stack != null)
                    return stack;
            }

            return entity.getItemBySlot(slot);
        }
    }

    public interface CITCacheArmor {
        CITCache.Single<TypeArmor> citresewn$getCacheTypeArmor();
    }
}
