package shcm.shsupercm.fabric.citresewn.defaults.mixin.types.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import shcm.shsupercm.fabric.citresewn.cit.CIT;
import shcm.shsupercm.fabric.citresewn.cit.CITContext;
import shcm.shsupercm.fabric.citresewn.defaults.cit.types.TypeArmor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Substitutes a synthetic {@link EquipmentClientInfo} (built from the CIT's textures) for the
 * asset vanilla would have resolved, then lets {@code EquipmentLayerRenderer.renderLayers} run
 * completely unmodified — dye/trim/glint compositing, the {@code queue.order(n)} submission
 * sequencing, all of it. Replaces an earlier "cancel HumanoidArmorLayer's per-piece render and
 * resubmit ourselves" approach that kept ending up invisible in-game for reasons that were never
 * fully pinned down (most likely: submitting outside {@code queue.order(n)}'s bookkeeping, or
 * some other undocumented step this method also does). Substituting the asset one call earlier
 * and reusing vanilla's own (already correct) rendering is far less fragile than re-deriving that
 * whole call chain by hand.
 * <p>
 * {@code EquipmentAssetManager.get(ResourceKey)} is a type-level, memoized-by-asset-id lookup
 * with no per-itemstack context, so it can't be redirected the way elytra's per-render-call
 * texture lookup was — the substitution has to happen here, one level up, where the actual
 * {@link ItemStack} being rendered is still an available parameter.
 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {
    @Redirect(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/EquipmentAssetManager;get(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/client/resources/model/EquipmentClientInfo;"))
    private EquipmentClientInfo citresewn$redirectAsset(net.minecraft.client.resources.model.EquipmentAssetManager assetManager, ResourceKey<EquipmentAsset> key,
                                                          EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> resourceKey, Model<?> model, Object state,
                                                          ItemStack stack, PoseStack poseStack, SubmitNodeCollector queue, int light, Identifier identifier, int color, int outlineColor) {
        CIT<TypeArmor> cit = TypeArmor.CONTAINER.getCIT(new CITContext(stack, null, null));
        if (cit == null) {
            EquipmentClientInfo legacy = citresewn$legacyArmorTextureBridge(key);
            return legacy != null ? legacy : assetManager.get(key);
        }

        // OptiFine armor.properties keys its "texture.<key>" entries after vanilla's own armor
        // texture filenames, which are "<material>_layer_1"/"<material>_layer_2" (e.g.
        // "diamond_layer_1") - not the bare "layer_1"/"layer_2" this originally assumed. Match by
        // suffix so it works regardless of material name; a bare "layer_1"/"layer_2" (no material
        // prefix) still matches too.
        Identifier layer1 = citresewn$findLayerTexture(cit.type.textures, "layer_1");
        Identifier layer2 = citresewn$findLayerTexture(cit.type.textures, "layer_2");
        if (layer1 == null)
            layer1 = cit.type.textures.get(null);
        if (layer2 == null)
            layer2 = layer1;
        if (layer1 == null && layer2 == null)
            return assetManager.get(key);

        Map<EquipmentClientInfo.LayerType, List<EquipmentClientInfo.Layer>> layers = new EnumMap<>(EquipmentClientInfo.LayerType.class);
        if (layer1 != null)
            layers.put(EquipmentClientInfo.LayerType.HUMANOID, List.of(new EquipmentClientInfo.Layer(layer1)));
        if (layer2 != null)
            layers.put(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, List.of(new EquipmentClientInfo.Layer(layer2)));

        return new EquipmentClientInfo(layers);
    }

    private static Identifier citresewn$findLayerTexture(Map<String, Identifier> textures, String suffix) {
        for (Map.Entry<String, Identifier> entry : textures.entrySet())
            if (entry.getKey() != null && entry.getKey().endsWith(suffix))
                return entry.getValue();
        return null;
    }

    /**
     * Bridges resourcepacks that still only ship the pre-equipment-rework
     * {@code textures/models/armor/<material>_layer_N.png} convention. Vanilla now only ever
     * resolves the new {@code textures/entity/equipment/<layerType>/<id>.png} convention
     * (confirmed by decompiling {@code EquipmentClientInfo.Layer#getTextureLocation}), so a pack
     * with only the old files present would otherwise render every plain vanilla-material piece
     * with the base game's own texture no matter what the pack ships - this has nothing to do
     * with CIT itself (no rule ever matches), it predates the whole equipment/EquipmentAsset
     * system this mixin substitutes into. The old and new material ids are identical for every
     * vanilla base material (confirmed against the game's own assets/minecraft/equipment/*.json -
     * "iron", "gold", "diamond", "chainmail", "netherite", "leather" are all unchanged), so no id
     * remapping is needed, only the path convention differs. Mirrors the CIT-match branch's own
     * "leggings layer falls back to the layer_1 texture if layer_2 is missing" behavior.
     */
    private static EquipmentClientInfo citresewn$legacyArmorTextureBridge(ResourceKey<EquipmentAsset> key) {
        Identifier materialId = key.identifier();
        Identifier layer1 = materialId.withPath(path -> "textures/models/armor/" + path + "_layer_1.png");
        Identifier layer2 = materialId.withPath(path -> "textures/models/armor/" + path + "_layer_2.png");
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        boolean has1 = resourceManager.getResource(layer1).isPresent();
        boolean has2 = resourceManager.getResource(layer2).isPresent();
        if (!has1 && !has2)
            return null;

        Map<EquipmentClientInfo.LayerType, List<EquipmentClientInfo.Layer>> layers = new EnumMap<>(EquipmentClientInfo.LayerType.class);
        if (has1)
            layers.put(EquipmentClientInfo.LayerType.HUMANOID, List.of(new EquipmentClientInfo.Layer(layer1)));
        Identifier leggingsLayer = has2 ? layer2 : (has1 ? layer1 : null);
        if (leggingsLayer != null)
            layers.put(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, List.of(new EquipmentClientInfo.Layer(leggingsLayer)));

        return new EquipmentClientInfo(layers);
    }
}
