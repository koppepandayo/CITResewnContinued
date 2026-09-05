package shcm.shsupercm.fabric.citresewn;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shcm.shsupercm.fabric.citresewn.config.CITResewnConfig;
import shcm.shsupercm.fabric.citresewn.cit.CITRegistry;
import shcm.shsupercm.fabric.citresewn.defaults.item.ItemMeshCache;

/**
 * Main initializer for CIT Resewn: Continued. Contains various internal utilities(just logging for now).
 */
public class CITResewn implements ClientModInitializer {
    public static final Logger LOG = LogManager.getLogger("CITResewnContinued");
    public static final java.util.Set<String> DEBUG_LOG_TEST_EXCEPTIONS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void onInitializeClient() {
        CITRegistry.registerAll();

        Identifier citsListenerId = Identifier.fromNamespaceAndPath("citresewn", "cits");
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(citsListenerId, CITReloadListener.INSTANCE);
        // CIT parsing must finish (and populate TypeItem's known-texture registry) before the item
        // atlas is stitched, so SpriteSourceListMixin can inject CIT item textures as real atlas
        // sprites while the sprite list is still being assembled - otherwise the atlas would
        // already be built by the time CIT textures are known, and there'd be nothing left to
        // inject them into.
        ResourceLoader.get(PackType.CLIENT_RESOURCES).addListenerOrdering(citsListenerId, ResourceReloaderKeys.Client.ATLAS);

        // ItemModelResolverMixin caches built BakedQuad meshes per texture, and each one embeds a
        // direct reference to the TextureAtlasSprite it was built from. That sprite gets closed and
        // replaced every time the item atlas is re-stitched, so the cache must be dropped *after* the
        // atlas listener finishes each reload - otherwise a mesh built before a reload keeps pointing
        // at a now-closed sprite (rendering garbage/stale-looking icons) instead of ever being rebuilt.
        Identifier meshCacheListenerId = Identifier.fromNamespaceAndPath("citresewn", "item_mesh_cache_invalidation");
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(meshCacheListenerId,
                (ResourceManagerReloadListener) manager -> ItemMeshCache.clear());
        ResourceLoader.get(PackType.CLIENT_RESOURCES).addListenerOrdering(ResourceReloaderKeys.Client.ATLAS, meshCacheListenerId);

        if (FabricLoader.getInstance().isModLoaded("fabric-command-api-v2"))
            CITResewnCommand.register();
    }

    /**
     * Logs an info line in CIT Resewn's name.
     * @param message log message
     */
    public static void info(String message) {
        LOG.info("[citresewn] " + message);
    }

    /**
     * Logs a warning line in CIT Resewn's name if enabled in config.
     * @see CITResewnConfig#mute_warns
     * @param message warn message
     */
    public static void logWarnLoading(String message) {
        if (CITResewnConfig.INSTANCE.mute_warns)
            return;
        LOG.warn("[citresewn] " + message);
    }

    /**
     * Logs an error line in CIT Resewn's name if enabled in config.
     * @see CITResewnConfig#mute_errors
     * @param message error message
     */
    public static void logErrorLoading(String message) {
        if (CITResewnConfig.INSTANCE.mute_errors)
            return;
        LOG.error("{citresewn} " + message);
    }
}
