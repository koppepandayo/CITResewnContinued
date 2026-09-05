package shcm.shsupercm.fabric.citresewn.defaults.cit.conditions;

import shcm.shsupercm.fabric.citresewn.api.CITConditionContainer;
import shcm.shsupercm.fabric.citresewn.cit.CITContext;
import shcm.shsupercm.fabric.citresewn.cit.builtin.conditions.IntegerCondition;

public class ConditionStackSize extends IntegerCondition {
    /** Registered as a "citresewn:condition" entrypoint in fabric.mod.json. */
    public static final CITConditionContainer<ConditionStackSize> CONTAINER = new CITConditionContainer<>(ConditionStackSize.class, ConditionStackSize::new,
            "stack_size", "stackSize", "amount");

    public ConditionStackSize() {
        super(true, false, false);
    }

    @Override
    protected int getValue(CITContext context) {
        return context.stack.getCount();
    }
}
