package shcm.shsupercm.fabric.citresewn.defaults.cit.conditions;

import com.mojang.serialization.DataResult;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import shcm.shsupercm.fabric.citresewn.CITResewn;
import shcm.shsupercm.fabric.citresewn.api.CITConditionContainer;
import shcm.shsupercm.fabric.citresewn.cit.CITCondition;
import shcm.shsupercm.fabric.citresewn.cit.CITContext;
import shcm.shsupercm.fabric.citresewn.cit.CITParsingException;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyGroup;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyKey;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyValue;

@SuppressWarnings("unchecked")
public class ConditionComponents extends CITCondition {
    /** Registered as a "citresewn:condition" entrypoint in fabric.mod.json. */
    public static final CITConditionContainer<ConditionComponents> CONTAINER = new CITConditionContainer<>(ConditionComponents.class, ConditionComponents::new,
            "components", "component", "nbt");

    private DataComponentType<?> componentType;
    private String componentMetadata;
    private String matchValue;

    private ConditionNBT fallbackNBTCheck;

    @Override
    public void load(PropertyKey key, PropertyValue value, PropertyGroup properties) throws CITParsingException {
        String metadata = value.keyMetadata();
        if (key.path().equals("nbt")) {
            if (metadata.startsWith("display.Name")) {
                metadata = "minecraft:custom_name" + value.keyMetadata().substring("display.Name".length());
                CITResewn.logWarnLoading(properties.messageWithDescriptorOf("Using legacy nbt.display.Name", value.position()));
            } else if (metadata.startsWith("display.Lore")) {
                metadata = "minecraft:lore" + value.keyMetadata().substring("display.Lore".length());
                CITResewn.logWarnLoading(properties.messageWithDescriptorOf("Using legacy nbt.display.Lore", value.position()));
            } else if (metadata.startsWith("SkullOwner.Name")) {
                // Pre-1.21 player heads stored their owner as an NBT compound
                // (SkullOwner: {Name: "...", Id: [I;...], ...}); 26.2 replaced that entirely with the
                // minecraft:profile component (a ResolvableProfile, confirmed by decompiling it and
                // its Codec - a name-only profile like this always encodes its "name" as a plain
                // top-level string field). Only the "Name" sub-path is mapped since that's the only
                // one any real CIT pack (including this one) actually keys off of - matching a head
                // by a specific known skin owner name (e.g. "MHF_ArrowDown").
                metadata = "minecraft:profile.name" + value.keyMetadata().substring("SkullOwner.Name".length());
                CITResewn.logWarnLoading(properties.messageWithDescriptorOf("Using legacy nbt.SkullOwner.Name", value.position()));
            } else
                throw new CITParsingException("NBT condition is not supported since 1.21", properties, value.position());
        }

        metadata = metadata.replace("~", "minecraft:");

        String componentId = metadata.split("\\.")[0];

        Identifier componentIdentifier = Identifier.tryParse(componentId);
        if (componentIdentifier == null || (this.componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(componentIdentifier)) == null)
            throw new CITParsingException("Unknown component type \"" + componentId + "\"", properties, value.position());

        metadata = metadata.substring(componentId.length());
        if (metadata.startsWith("."))
            metadata = metadata.substring(1);
        this.componentMetadata = metadata;

        this.matchValue = value.value();

        this.fallbackNBTCheck = new ConditionNBT();
        String[] metadataNbtPath = metadata.split("\\.");
        if (metadataNbtPath.length == 1 && metadataNbtPath[0].isEmpty())
            metadataNbtPath = new String[0];
        this.fallbackNBTCheck.loadNbtCondition(value, properties, metadataNbtPath, this.matchValue);
    }

    @Override
    public boolean test(CITContext context) {
        Object stackComponent = context.stack.get(this.componentType);
        if (stackComponent != null) {
            if (stackComponent instanceof Component text) {
                if (this.fallbackNBTCheck.testString(null, text, context))
                    return true;
            }

            DataResult<Tag> encoded = ((DataComponentType<Object>) this.componentType).codec()
                    .encodeStart(context.world.registryAccess().createSerializationContext(NbtOps.INSTANCE), stackComponent);
            Tag fallbackComponentNBT = encoded.result().orElse(null);
            if (fallbackComponentNBT != null)
                return this.fallbackNBTCheck.testPath(fallbackComponentNBT, 0, context);
        }
        return false;
    }
}
