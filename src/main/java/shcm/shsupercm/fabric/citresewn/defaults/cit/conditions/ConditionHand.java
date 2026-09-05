package shcm.shsupercm.fabric.citresewn.defaults.cit.conditions;

import shcm.shsupercm.fabric.citresewn.api.CITConditionContainer;
import shcm.shsupercm.fabric.citresewn.cit.CITContext;
import shcm.shsupercm.fabric.citresewn.cit.builtin.conditions.EnumCondition;

public class ConditionHand extends EnumCondition<ConditionHand.Hand> {
    /** Registered as a "citresewn:condition" entrypoint in fabric.mod.json. */
    public static final CITConditionContainer<ConditionHand> CONTAINER = new CITConditionContainer<>(ConditionHand.class, ConditionHand::new,
            "hand");

    public ConditionHand() {
        super(ConditionHand.Hand::values);
    }

    @Override
    protected Hand getValue(CITContext context) {
        return context.entity != null && context.entity.getOffhandItem() == context.stack ? Hand.OFFHAND : Hand.MAINHAND;
    }

    @Override
    public boolean test(CITContext context) {
        return this.value == Hand.ANY || this.value == getValue(context);
    }

    protected enum Hand implements EnumCondition.Aliased {
        MAINHAND("main", "mainhand", "main_hand"),
        OFFHAND("off", "offhand", "off_hand"),
        ANY("any", "either", "*");

        private final String[] aliases;

        Hand(String... aliases) {
            this.aliases = aliases;
        }

        @Override
        public String[] getAliases() {
            return this.aliases;
        }
    }
}
