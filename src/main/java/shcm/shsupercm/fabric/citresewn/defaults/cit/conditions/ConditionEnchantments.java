package shcm.shsupercm.fabric.citresewn.defaults.cit.conditions;

import net.minecraft.resources.Identifier;
import shcm.shsupercm.fabric.citresewn.api.CITConditionContainer;
import shcm.shsupercm.fabric.citresewn.cit.CITCondition;
import shcm.shsupercm.fabric.citresewn.cit.CITContext;
import shcm.shsupercm.fabric.citresewn.cit.builtin.conditions.IdentifierCondition;
import shcm.shsupercm.fabric.citresewn.cit.builtin.conditions.ListCondition;

import java.util.Set;

public class ConditionEnchantments extends ListCondition<ConditionEnchantments.EnchantmentCondition> {
    /** Registered as a "citresewn:condition" entrypoint in fabric.mod.json. */
    public static final CITConditionContainer<ConditionEnchantments> CONTAINER = new CITConditionContainer<>(ConditionEnchantments.class, ConditionEnchantments::new,
            "enchantments", "enchantmentIDs");

    public ConditionEnchantments() {
        super(EnchantmentCondition.class, EnchantmentCondition::new);
    }

    public Identifier[] getEnchantments() {
        Identifier[] enchantments = new Identifier[this.conditions.length];

        for (int i = 0; i < this.conditions.length; i++)
            enchantments[i] = this.conditions[i].getValue(null);

        return enchantments;
    }

    @Override
    public Set<Class<? extends CITCondition>> siblingConditions() {
        return Set.of(ConditionEnchantmentLevels.class);
    }

    protected static class EnchantmentCondition extends IdentifierCondition {
        @Override
        public boolean test(CITContext context) {
            return context.enchantments().containsKey(this.value);
        }

        @Override
        protected Identifier getValue(CITContext context) {
            return this.value;
        }
    }
}
