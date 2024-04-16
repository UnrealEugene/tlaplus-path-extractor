package tlc2.module;

import tlc2.diploma.graph.ConcreteAction;
import tlc2.output.EC;
import tlc2.overrides.TLAPlusOperator;
import tlc2.tool.EvalException;
import tlc2.util.IdThread;
import tlc2.value.ValueConstants;
import tlc2.value.Values;
import tlc2.value.impl.BoolValue;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.Value;

public class TLCPE implements ValueConstants {
    public static ThreadLocal<ConcreteAction> exportedActions;

    @TLAPlusOperator(identifier = "ExportAs", module = "TLCPE")
    public static Value ExportAs(Value actionNameVal, Value... args) {
        Thread th = Thread.currentThread();
        if (!(th instanceof IdThread)) {
            throw new EvalException(EC.GENERAL, "ExportAs is called during initial state evaluation");
        }
        if (exportedActions.get() != null) {
            throw new EvalException(EC.GENERAL, "ExportAs is called twice within single action");
        }
        if (!(actionNameVal instanceof StringValue)) {
            throw new EvalException(EC.TLC_MODULE_ARGUMENT_ERROR, new String[]{ "first", "ExportAs", "string literal",
                    Values.ppr(actionNameVal.toString()) });
        }
        String actionName = ((StringValue) actionNameVal).val.toString();
        Value[] normalizedArgs = new Value[args.length];
        for (int i = 0; i < args.length; i++) {
            normalizedArgs[i] = (Value) args[i].deepCopy();
            normalizedArgs[i].deepNormalize();
        }
        exportedActions.set(ConcreteAction.from(actionName, normalizedArgs));
        return BoolValue.ValTrue;
    }
}
