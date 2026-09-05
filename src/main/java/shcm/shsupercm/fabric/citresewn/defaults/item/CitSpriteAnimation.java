package shcm.shsupercm.fabric.citresewn.defaults.item;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-texture frame-cycling info for CIT item spritesheets whose {@code .mcmeta} ships an explicit
 * empty {@code "frames": []} - a widely-used OptiFine CIT convention meaning "don't let *vanilla*
 * auto-animate this; the CIT icon still visibly cycles through the sheet's frames, just driven by
 * the same {@code frametime} value through the CIT renderer's own ticking instead of vanilla's
 * atlas-animation system" (confirmed: {@code SpriteContents.createAnimatedTexture} treats an
 * explicit-but-empty frame list as zero frames, i.e. permanently frame 0 - there is no vanilla
 * animation state to piggyback on for these).
 */
public final class CitSpriteAnimation {
    private static final Map<Identifier, CitSpriteAnimation> BY_TEXTURE = new ConcurrentHashMap<>();

    public final int frameHeightPx;
    public final int frameCount;
    public final int frameTimeTicks;

    private CitSpriteAnimation(int frameHeightPx, int frameCount, int frameTimeTicks) {
        this.frameHeightPx = frameHeightPx;
        this.frameCount = frameCount;
        this.frameTimeTicks = frameTimeTicks;
    }

    public static void register(Identifier texture, int frameHeightPx, int frameCount, int frameTimeTicks) {
        if (frameHeightPx <= 0 || frameCount <= 1) {
            BY_TEXTURE.remove(texture);
            return;
        }
        BY_TEXTURE.put(texture, new CitSpriteAnimation(frameHeightPx, frameCount, Math.max(1, frameTimeTicks)));
    }

    public static CitSpriteAnimation get(Identifier texture) {
        return BY_TEXTURE.get(texture);
    }

    public int currentFrame(long gameTicks) {
        return (int) ((gameTicks / frameTimeTicks) % frameCount);
    }
}
