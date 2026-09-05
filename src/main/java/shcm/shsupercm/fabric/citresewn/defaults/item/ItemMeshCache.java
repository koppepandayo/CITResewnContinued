package shcm.shsupercm.fabric.citresewn.defaults.item;

import net.minecraft.client.resources.model.geometry.BakedQuad;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plain (non-Mixin) holder for ItemModelResolverMixin's built-mesh cache. Must live outside the
 * "shcm.shsupercm.fabric.citresewn.defaults.mixin" package: Mixin reserves every class under a
 * mixins.json "package" for its own transform targets and refuses to let anything outside that
 * package reference a class inside it directly (IllegalClassLoadError), even a plain helper class
 * with no @Mixin annotation.
 * <p>
 * Keyed on {@link MeshCacheKey} (texture + animation frame), not just the texture, so an animated
 * CIT icon's cache naturally holds one mesh per distinct frame instead of freezing on whichever
 * frame first built it.
 */
public final class ItemMeshCache {
    private static final Map<MeshCacheKey, List<BakedQuad>> CACHE = new ConcurrentHashMap<>();

    private ItemMeshCache() {}

    /**
     * Deliberately not a {@code computeIfAbsent}: whether a built mesh is even worth caching
     * depends on *how* it was built (see {@code ItemModelResolverMixin#citresewn$meshFor}) - a
     * mesh built via the non-atlased fallback (because the real atlas sprite wasn't resolvable at
     * that exact moment, e.g. right at world/resourcepack load) must never be cached, or it would
     * permanently lock the icon onto that dark, non-atlas-lit result until the next resource
     * reload even after the real sprite becomes available moments later.
     */
    public static List<BakedQuad> get(MeshCacheKey key) {
        return CACHE.get(key);
    }

    public static void put(MeshCacheKey key, List<BakedQuad> mesh) {
        CACHE.put(key, mesh);
    }

    /**
     * Every cached {@link BakedQuad} embeds a direct reference to the {@code TextureAtlasSprite} it
     * was built from. {@code TextureAtlas.clearTextureData()} closes and discards every sprite of
     * the previous atlas on each resource reload, so a mesh built before a reload points at a
     * now-closed/dangling sprite afterward - reading garbage UVs into whatever now occupies that GPU
     * memory (the "static noise" / "shows a completely different texture" look). Must be called once
     * the reload that rebuilds the item atlas has fully finished, so the next render lazily rebuilds
     * against the new atlas instead of the stale cache.
     */
    public static void clear() {
        CACHE.clear();
    }
}
