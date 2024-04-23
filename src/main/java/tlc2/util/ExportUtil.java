package tlc2.util;

import com.alibaba.fastjson2.JSONWriter;
import tlc2.TLCGlobals;
import tlc2.diploma.graph.ConcreteAction;
import tlc2.module.TLCPE;
import tlc2.tool.Action;
import tlc2.tool.TLCState;
import tlc2.tool.impl.Tool;
import tlc2.value.IValue;
import util.UniqueString;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ExportUtil {
    private ExportUtil() {
    }

    public static Tool getTool() {
        return (Tool) (TLCGlobals.mainChecker != null ? TLCGlobals.mainChecker.tool : TLCGlobals.simulator.getTool());
    }

    @SuppressWarnings("unchecked")
    public static Map<String, IValue> getSpecConstantValues() {
        Vect<Object> constVect = getTool().getModelConfig().getConstants();
        Object[] constArray = new Vect[constVect.size()];
        constVect.copyInto(constArray);
        Map<String, IValue> constMap = new HashMap<>();
        for (Object obj : constArray) {
            Vect<Object> vect = (Vect<Object>) obj;
            constMap.put((String) vect.elementAt(0), (IValue) vect.elementAt(1));
        }
        return constMap;
    }

    public static void writeTlaModule(JSONWriter jsonWriter) {
        jsonWriter.writeName("tla_module");
        jsonWriter.writeColon();
        String rootFile = getTool().getRootFile();
        jsonWriter.writeString(Path.of(rootFile).getFileName().toString().replaceAll(".tla$", ""));
    }

    public static void writeTlaConstants(JSONWriter jsonWriter) {
        jsonWriter.writeName("tla_constants");
        jsonWriter.writeColon();
        jsonWriter.startObject();
        for (Map.Entry<String, IValue> entry : ExportUtil.getSpecConstantValues().entrySet()) {
            jsonWriter.writeName(entry.getKey());
            jsonWriter.writeColon();
            jsonWriter.writeAny(FastJsonSerializer.serialize(entry.getValue()));
        }
        jsonWriter.endObject();
    }

    public static void writeState(JSONWriter jsonWriter, TLCState state) {
        jsonWriter.startObject();
        Map<UniqueString, IValue> stateVals = state.getVals();
        for (Map.Entry<UniqueString, IValue> entry : stateVals.entrySet()) {
            jsonWriter.writeName(entry.getKey().toString());
            jsonWriter.writeColon();
            jsonWriter.writeAny(FastJsonSerializer.serialize(entry.getValue()));
        }
        jsonWriter.endObject();
    }

    public static void writeAction(JSONWriter jsonWriter, TLCState from, TLCState to, Action action) {
        ConcreteAction concreteAction;
        if (TLCPE.exportedActions.get() != null) {
            concreteAction = TLCPE.exportedActions.get();
            TLCPE.exportedActions.set(null);
        } else {
            concreteAction = ConcreteAction.from(from, to, action);
        }
        jsonWriter.startArray();
        jsonWriter.writeString(concreteAction.getName());
        for (IValue val : concreteAction.getArgs()) {
            jsonWriter.writeComma();
            jsonWriter.writeAny(FastJsonSerializer.serialize(val));
        }
        jsonWriter.endArray();
    }
}
