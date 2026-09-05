package shcm.shsupercm.fabric.citresewn.config;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import net.fabricmc.loader.api.FabricLoader;
import shcm.shsupercm.fabric.citresewn.CITResewn;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;

/**
 * Contains runtime representation of CIT Resewn's config, encoded using GSON.
 */
public class CITResewnConfig {
    /**
     * Whether CIT Resewn should work or not.<br>
     * Requires a restart.
     */
    public boolean enabled = true;
    /**
     * Mutes pack loading errors from logs.
     */
    public boolean mute_errors = false;
    /**
     * Mutes pack loading warnings from logs.
     */
    public boolean mute_warns = false;
    /**
     * Invalidating interval for CITs' caches in milliseconds. Set to 0 to disable caching.
     */
    public int cache_ms = 50;
    /**
     * Should broken paths be allowed in resourcepacks. Requires a restart.
     * @see BrokenPaths
     */
    public boolean broken_paths = false;

    /**
     * CIT Resewn's config storage file.
     */
    private static final File FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "citresewn_continued.json");

    /**
     * Active instance of the current config.
     */
    public static final CITResewnConfig INSTANCE = read();

    /**
     * Reads the stored config.
     * @see #FILE
     * @return the read config
     */
    public static CITResewnConfig read() {
        if (!FILE.exists())
            return new CITResewnConfig().write();

        try (Reader reader = new FileReader(FILE)) {
            return new Gson().fromJson(reader, CITResewnConfig.class);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Saves this config to file.
     * @see #FILE
     * @return this
     */
    public CITResewnConfig write() {
        Gson gson = new Gson();
        try (Writer writer = new FileWriter(FILE)) {
            JsonWriter jsonWriter = gson.newJsonWriter(writer);
            jsonWriter.setIndent("    ");
            gson.toJson(gson.toJsonTree(this, CITResewnConfig.class), jsonWriter);
        } catch (Exception e) {
            CITResewn.LOG.error("Couldn't save config");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return this;
    }
}
