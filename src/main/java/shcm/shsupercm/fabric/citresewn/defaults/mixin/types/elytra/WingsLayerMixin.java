package shcm.shsupercm.fabric.citresewn.defaults.mixin.types.elytra;

import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import shcm.shsupercm.fabric.citresewn.cit.CIT;
import shcm.shsupercm.fabric.citresewn.cit.CITContext;
import shcm.shsupercm.fabric.citresewn.defaults.cit.types.TypeElytra;

/**
 * Unlike armor, {@link WingsLayer#submit} already threads an explicit {@code Identifier} texture
 * override through to {@code EquipmentLayerRenderer#renderLayers} for the elytra's WINGS layer
 * (vanilla uses it to substitute the wearer's own skin-defined elytra/cape texture) — so CIT only
 * needs to redirect the private {@code getPlayerElytraTexture} call that supplies that override,
 * rather than cancelling and resubmitting the whole render like the armor layer needs to.
 */
@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {
    @Invoker("getPlayerElytraTexture")
    private static Identifier citresewn$getPlayerElytraTexture(HumanoidRenderState state) {
        throw new AssertionError();
    }

    @Redirect(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/WingsLayer;getPlayerElytraTexture(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)Lnet/minecraft/resources/Identifier;"))
    private static Identifier citresewn$elytraTexture(HumanoidRenderState state) {
        CIT<TypeElytra> cit = TypeElytra.CONTAINER.getCIT(new CITContext(state.chestEquipment, null, null));
        if (cit != null && cit.type.texture != null)
            return cit.type.texture;

        return citresewn$getPlayerElytraTexture(state);
    }
}
