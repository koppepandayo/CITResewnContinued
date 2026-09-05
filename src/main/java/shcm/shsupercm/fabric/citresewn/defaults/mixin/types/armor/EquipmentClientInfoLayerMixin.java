package shcm.shsupercm.fabric.citresewn.defaults.mixin.types.armor;

import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link EquipmentClientInfo.Layer#getTextureLocation} always expands its stored {@code textureId}
 * into {@code textures/entity/equipment/<layerType>/<id>.png} — vanilla only ever stores a bare
 * material name there (e.g. "diamond"), never a full path. CIT textures resolved by
 * {@link EquipmentLayerRendererMixin} are already complete, ready-to-use identifiers (e.g.
 * {@code minecraft:optifine/cit/armors/apollo/diamond_layer_1.png}), so running them through that
 * expansion doubled the ".png" and nested them under the wrong folder, producing a missing
 * texture. A real vanilla equipment asset id never ends in ".png", so that's a reliable,
 * collision-free marker for "this is already a complete path - use it as-is".
 */
@Mixin(EquipmentClientInfo.Layer.class)
public abstract class EquipmentClientInfoLayerMixin {
    @Inject(method = "getTextureLocation", at = @At("HEAD"), cancellable = true)
    private void citresewn$skipExpansionForCITTextures(EquipmentClientInfo.LayerType layerType, CallbackInfoReturnable<Identifier> cir) {
        Identifier textureId = ((EquipmentClientInfo.Layer) (Object) this).textureId();
        if (textureId.getPath().endsWith(".png"))
            cir.setReturnValue(textureId);
    }
}
