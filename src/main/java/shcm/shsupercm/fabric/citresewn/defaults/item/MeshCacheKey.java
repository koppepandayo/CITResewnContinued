package shcm.shsupercm.fabric.citresewn.defaults.item;

import net.minecraft.resources.Identifier;

/**
 * Cache key for {@link ItemMeshCache}: a mesh is only reusable across renders while showing the
 * same animation frame of the same texture, so the frame index has to be part of the key - keying
 * on texture alone would freeze every animated CIT icon on whatever frame first built its mesh.
 */
public record MeshCacheKey(Identifier texture, int frame) {
}
