package shcm.shsupercm.fabric.citresewn;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import shcm.shsupercm.fabric.citresewn.cit.ActiveCITs;

/**
 * Triggers the (re)loading of active CITs on every client resource reload.<br>
 * Registered via Fabric's {@code ResourceLoader} instead of a Mixin into the vanilla model loading
 * pipeline: as of the item model rewrite (~1.20.5+), model loading became a multi-staged
 * {@code CompletableFuture} pipeline inside {@code ModelManager} with no single stable injection
 * point left, whereas a plain reload listener is the version-stable, intended extension point for
 * "run once per resource reload with access to the resource manager" behavior.
 * @see ActiveCITs
 */
public class CITReloadListener implements ResourceManagerReloadListener {
    public static final CITReloadListener INSTANCE = new CITReloadListener();

    private CITReloadListener() {}

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("citresewn:reloading_cits");
        ActiveCITs.load(manager, profiler);
        profiler.pop();
    }

    @Override
    public String getName() {
        return "CIT Resewn: Continued (cits)";
    }
}
