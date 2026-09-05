package shcm.shsupercm.fabric.citresewn.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config integration to CIT Resewn's config.
 * 本家(SHsuperCM/CITResewn)はcore/defaultsが別Modのため、defaults側の設定へ画面遷移する
 * DEFAULTS_CONFIG_ENTRYPOINT間接呼び出しがあったが、このフォークは1つのModにまとまっている
 * ため、その仕組みは持ち込んでいない(broken_pathsトグルも、対応するmixin一式を移植していない
 * ためこの画面には出していない。CITResewnConfig#broken_paths自体は本家互換のため残してある)。
 * @see CITResewnConfig
 */
public class CITResewnConfigScreenFactory {
    /**
     * Creates a Cloth Config screen for the current active config instance.
     * @param parent parent to return to from the config screen
     * @return the config screen
     * @throws NoClassDefFoundError if Cloth Config is not present
     */
    public static Screen create(Screen parent) {
        CITResewnConfig currentConfig = CITResewnConfig.INSTANCE, defaultConfig = new CITResewnConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.citresewn_continued.title"))
                .setSavingRunnable(currentConfig::write);

        ConfigCategory category = builder.getOrCreateCategory(Component.empty());
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        category.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.citresewn_continued.enabled.title"), currentConfig.enabled)
                .setTooltip(Component.translatable("config.citresewn_continued.enabled.tooltip"))
                .setSaveConsumer(newConfig -> {
                    if (currentConfig.enabled != newConfig) {
                        currentConfig.enabled = newConfig;
                        Minecraft.getInstance().reloadResourcePacks();
                    }
                })
                .setDefaultValue(defaultConfig.enabled)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.citresewn_continued.mute_errors.title"), currentConfig.mute_errors)
                .setTooltip(Component.translatable("config.citresewn_continued.mute_errors.tooltip"))
                .setSaveConsumer(newConfig -> currentConfig.mute_errors = newConfig)
                .setDefaultValue(defaultConfig.mute_errors)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.citresewn_continued.mute_warns.title"), currentConfig.mute_warns)
                .setTooltip(Component.translatable("config.citresewn_continued.mute_warns.tooltip"))
                .setSaveConsumer(newConfig -> currentConfig.mute_warns = newConfig)
                .setDefaultValue(defaultConfig.mute_warns)
                .build());

        category.addEntry(entryBuilder.startIntSlider(Component.translatable("config.citresewn_continued.cache_ms.title"), currentConfig.cache_ms / 50, 0, 5 * 20)
                .setTooltip(Component.translatable("config.citresewn_continued.cache_ms.tooltip"))
                .setSaveConsumer(newConfig -> currentConfig.cache_ms = newConfig * 50)
                .setDefaultValue(defaultConfig.cache_ms / 50)
                .setTextGetter(ticks -> {
                    if (ticks <= 1)
                        return Component.translatable("config.citresewn_continued.cache_ms.ticks." + ticks).withStyle(ChatFormatting.AQUA);

                    ChatFormatting color = ChatFormatting.DARK_RED;

                    if (ticks <= 40) color = ChatFormatting.RED;
                    if (ticks <= 20) color = ChatFormatting.GOLD;
                    if (ticks <= 10) color = ChatFormatting.DARK_GREEN;
                    if (ticks <= 5) color = ChatFormatting.GREEN;

                    return Component.translatable("config.citresewn_continued.cache_ms.ticks.any", ticks).withStyle(color);
                })
                .build());

        return builder.build();
    }
}
