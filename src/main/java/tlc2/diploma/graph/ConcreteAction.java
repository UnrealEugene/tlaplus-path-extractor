package tlc2.diploma.graph;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.list.mutable.FastList;
import tla2sany.semantic.ExprOrOpArgNode;
import tla2sany.semantic.FormalParamNode;
import tla2sany.semantic.OpApplNode;
import tlc2.TLCGlobals;
import tlc2.tool.Action;
import tlc2.tool.TLCState;
import tlc2.tool.impl.Tool;
import tlc2.value.IValue;
import tlc2.value.impl.LazyValue;
import tlc2.value.impl.StringValue;

import java.util.Arrays;
import java.util.List;

import static tla2sany.semantic.ASTConstants.UserDefinedOpKind;

public class ConcreteAction {
    private final String name;
    private final ImmutableList<IValue> args;

    private ConcreteAction(String name, List<IValue> args) {
        this.name = name;
        this.args = new FastList<>(args).toImmutable();
    }

    public static ConcreteAction from(String name, IValue... args) {
        return new ConcreteAction(name, Arrays.asList(args));
    }

    public static ConcreteAction from(TLCState from, TLCState to, Action action) {
        Tool tool = (Tool) TLCGlobals.mainChecker.tool;
        OpApplNode opApplNode = (OpApplNode) action.pred;
        List<IValue> args = new FastList<>();
        if (opApplNode.getOperator().getKind() == UserDefinedOpKind) {
            ExprOrOpArgNode[] nodeArgs = opApplNode.getArgs();
            for (ExprOrOpArgNode arg : nodeArgs) {
                Object val = tool.getVal(arg, action.con, false);
                if (val instanceof LazyValue) {
                    args.add(((LazyValue) val).eval(tool, from, to));
                } else if (val instanceof IValue) {
                    args.add((IValue) val);
                } else {
                    args.add(new StringValue(val.toString()));
                }
            }
        } else {
            FormalParamNode[] params = action.getOpDef().getParams();
            for (FormalParamNode param : params) {
                Object val = tool.lookup(param, action.con, false);
                if (val instanceof IValue) {
                    args.add((IValue) val);
                } else {
                    args.add(new StringValue(val.toString()));
                }
            }
        }
        return new ConcreteAction(action.getName().toString(), args);
    }

    public String getName() {
        return name;
    }

    public List<IValue> getArgs() {
        return args.castToList();
    }
}