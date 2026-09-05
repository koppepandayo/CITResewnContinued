package shcm.shsupercm.fabric.citresewn.defaults.cit.types;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import shcm.shsupercm.fabric.citresewn.api.CITTypeContainer;
import shcm.shsupercm.fabric.citresewn.cit.*;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyGroup;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyKey;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyValue;

import java.util.List;
import java.util.Set;

/**
 * Parse-only stub. The original renders scrolling enchantment glint overlays (custom texture,
 * scroll speed/rotation, blend mode) via hand-built {@code RenderLayer}/{@code RenderPhase}
 * construction and manual {@code BufferBuilder} layering — none of which exists in 26.2's
 * submit-based rendering pipeline. Reimplementing the modern glint {@code RenderType}/
 * {@code TextureTransform} machinery blind, with zero confirmed usage in the target resource
 * pack (0 of its 337 CIT rules use {@code type=enchantment}), isn't worth the risk in this port.
 * This stub only parses/validates the properties so a pack containing enchantment CITs doesn't
 * fail to load entirely; it never actually renders a glint override.
 */
public class TypeEnchantment extends CITType {
    /** Registered as a "citresewn:type" entrypoint in fabric.mod.json. */
    public static final Container CONTAINER = new Container();

    @Override
    public Set<PropertyKey> typeProperties() {
        return Set.of(
                PropertyKey.of("texture"),
                PropertyKey.of("layer"),
                PropertyKey.of("speed"),
                PropertyKey.of("rotation"),
                PropertyKey.of("duration"),
                PropertyKey.of("r"),
                PropertyKey.of("g"),
                PropertyKey.of("b"),
                PropertyKey.of("a"),
                PropertyKey.of("useGlint"),
                PropertyKey.of("blur"),
                PropertyKey.of("blend"));
    }

    @Override
    public void load(List<CITCondition> conditions, PropertyGroup properties, ResourceManager resourceManager) throws CITParsingException {
        PropertyValue textureProp = properties.getLastWithoutMetadata("citresewn", "texture");
        Identifier texture = resolveAsset(properties.identifier, textureProp, "textures", ".png", resourceManager);
        if (texture == null)
            throw textureProp == null ? new CITParsingException("No texture specified", properties, -1) : new CITParsingException("Could not resolve texture", properties, textureProp.position());

        PropertyValue layerProp = properties.getLastWithoutMetadataOrDefault("0", "citresewn", "layer");
        try {
            Integer.parseInt(layerProp.value());
        } catch (Exception e) {
            throw new CITParsingException("Could not parse integer", properties, layerProp.position(), e);
        }

        for (String floatProperty : new String[]{"speed", "rotation", "duration", "r", "g", "b", "a"}) {
            PropertyValue value = properties.getLastWithoutMetadata("citresewn", floatProperty);
            if (value != null)
                try {
                    Float.parseFloat(value.value());
                } catch (Exception e) {
                    throw new CITParsingException("Could not parse float", properties, value.position(), e);
                }
        }

        PropertyValue useGlintProp = properties.getLastWithoutMetadata("citresewn", "useGlint");
        if (useGlintProp != null && !useGlintProp.value().equalsIgnoreCase("true") && !useGlintProp.value().equalsIgnoreCase("false"))
            throw new CITParsingException("Could not parse boolean", properties, useGlintProp.position());

        warn("Enchantment glint rendering is not supported in this port (rule parsed but will not render)", null, properties);
    }

    public static class Container extends CITTypeContainer<TypeEnchantment> {
        public Container() {
            super(TypeEnchantment.class, TypeEnchantment::new, "enchantment");
        }

        @Override
        public void load(List<CIT<TypeEnchantment>> parsedCITs) {
        }

        @Override
        public void dispose() {
        }
    }
}
