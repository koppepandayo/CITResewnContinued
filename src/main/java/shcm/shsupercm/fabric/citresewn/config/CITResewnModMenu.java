package shcm.shsupercm.fabric.citresewn.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;

/**
 * Mod Menu config button integration. fabric.mod.jsonの"modmenu"エントリーポイントから
 * 手動で参照している(本家が使っているfletching-tableプラグインの@Entrypoint自動生成は
 * このフォークでは使っていないため付けていない)。本家はCloth Config未導入時のフォールバックに
 * NoticeScreenを使っていたが、このMinecraftバージョンではNoticeScreen自体が撤去されているため
 * 同じ「メッセージ+閉じるボタンのみ」の画面であるAlertScreenで代替している。
 */
public class CITResewnModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (FabricLoader.getInstance().isModLoaded("cloth-config2"))
            return CITResewnConfigScreenFactory::create;

        return parent -> new AlertScreen(() -> Minecraft.getInstance().setScreenAndShow(parent), Component.literal("CIT Resewn: Continued"), Component.literal("CIT Resewn requires Cloth Config to be able to show the config."));
    }
}
