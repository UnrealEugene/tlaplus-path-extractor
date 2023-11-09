package tlc2.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import tlc2.value.IValue;
import tlc2.value.impl.BoolValue;
import tlc2.value.impl.FcnLambdaValue;
import tlc2.value.impl.FcnRcdValue;
import tlc2.value.impl.IntValue;
import tlc2.value.impl.IntervalValue;
import tlc2.value.impl.ModelValue;
import tlc2.value.impl.RecordValue;
import tlc2.value.impl.SetEnumValue;
import tlc2.value.impl.SetOfFcnsValue;
import tlc2.value.impl.SetOfRcdsValue;
import tlc2.value.impl.SetOfTuplesValue;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.SubsetValue;
import tlc2.value.impl.TupleValue;
import tlc2.value.impl.Value;
import util.UniqueString;

public class FastJsonSerializer {
    private FastJsonSerializer() {
    }

    public static Object serialize(IValue value) {
        if (value == null) {
            return null;
        }
        try {
            return getNode(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Object getNode(IValue value) throws IOException {
        if (value instanceof RecordValue) {
            return getObjectNode((RecordValue) value);
        } else if (value instanceof TupleValue) {
            return getArrayNode((TupleValue) value);
        } else if (value instanceof StringValue) {
            return ((StringValue) value).val.toString();
        } else if (value instanceof ModelValue) {
            return ((ModelValue) value).val.toString();
        } else if (value instanceof IntValue) {
            return ((IntValue) value).val;
        } else if (value instanceof BoolValue) {
            return ((BoolValue) value).val;
        } else if (value instanceof FcnRcdValue) {
            return getObjectNode((FcnRcdValue) value);
        } else if (value instanceof FcnLambdaValue) {
            return getObjectNode((FcnRcdValue) ((FcnLambdaValue) value).toFcnRcd());
        } else if (value instanceof SetEnumValue) {
            return getArrayNode((SetEnumValue) value);
        } else if (value instanceof SetOfRcdsValue) {
            return getArrayNode((SetEnumValue) ((SetOfRcdsValue) value).toSetEnum());
        } else if (value instanceof SetOfTuplesValue) {
            return getArrayNode((SetEnumValue) ((SetOfTuplesValue) value).toSetEnum());
        } else if (value instanceof SetOfFcnsValue) {
            return getArrayNode((SetEnumValue) ((SetOfFcnsValue) value).toSetEnum());
        } else if (value instanceof SubsetValue) {
            return getArrayNode((SetEnumValue) ((SubsetValue) value).toSetEnum());
        } else if (value instanceof IntervalValue) {
            return getArrayNode((SetEnumValue) ((IntervalValue) value).toSetEnum());
        } else {
            throw new IOException("Cannot convert value: unsupported value type " + value.getClass().getName());
        }
    }

    private static boolean isValidSequence(FcnRcdValue value) {
        final Value[] domain = value.getDomainAsValues();
        for (Value d : domain) {
            if (!(d instanceof IntValue)) {
                return false;
            }
        }
        value.normalize();
        for (int i = 0; i < domain.length; i++) {
            // TODO: fix this hack
            if (((IntValue) domain[i]).val != (i + 1) && ((IntValue) domain[i]).val != i) {
                return false;
            }
        }
        return true;
    }

    private static Object getObjectNode(FcnRcdValue value) throws IOException {
        if (isValidSequence(value)) {
            return getArrayNode(value);
        }

        final Value[] domain = value.getDomainAsValues();
        JSONObject jsonObject = new JSONObject();
        for (int i = 0; i < domain.length; i++) {
            Value domainValue = domain[i];
            if (domainValue instanceof StringValue) {
                jsonObject.put(((StringValue) domainValue).val.toString(), getNode(value.values[i]));
            } else {
                jsonObject.put(domainValue.toString(), getNode(value.values[i]));
            }
        }
        return jsonObject;
    }

    private static Object getObjectNode(RecordValue value) throws IOException {
        JSONObject jsonObject = new JSONObject();
        for (int i = 0; i < value.names.length; i++) {
            jsonObject.put(value.names[i].toString(), getNode(value.values[i]));
        }
        return jsonObject;
    }

    private static Object getArrayNode(TupleValue value) throws IOException {
        JSONArray jsonArray = new JSONArray(value.elems.length);
        for (Value elem : value.elems) {
            jsonArray.add(getNode(elem));
        }
        return jsonArray;
    }

    private static Object getArrayNode(FcnRcdValue value) throws IOException {
        if (!isValidSequence(value)) {
            return getObjectNode(value);
        }

        value.normalize();
        JSONArray jsonArray = new JSONArray(value.values.length);
        for (Value item : value.values) {
            jsonArray.add(getNode(item));
        }
        return jsonArray;
    }

    private static Object getArrayNode(SetEnumValue value) throws IOException {
        value.normalize();
        Value[] values = value.elems.toArray();
        JSONArray jsonArray = new JSONArray(values.length);
        for (Value item : values) {
            jsonArray.add(getNode(item));
        }
        return jsonArray;
    }

    private static Value getValue(Object node) throws IOException {
        if (node instanceof JSONArray) {
            return getTupleValue(node);
        } else if (node instanceof JSONObject) {
            return getRecordValue(node);
        } else if (node instanceof Integer) {
            return IntValue.gen((int) node);
        } else if (node instanceof Boolean) {
            return new BoolValue((boolean) node);
        } else if (node instanceof String) {
            return new StringValue((String) node);
        } else if (node == null) {
            return null;
        }
        throw new IOException("Cannot convert value: unsupported JSON value " + node);
    }

    private static TupleValue getTupleValue(Object node) throws IOException {
        List<Value> values = new ArrayList<>();
        JSONArray jsonArray = (JSONArray) node;
        for (Object obj : jsonArray) {
            values.add(getValue(obj));
        }
        return new TupleValue(values.toArray(new Value[0]));
    }

    private static RecordValue getRecordValue(Object node) throws IOException {
        List<UniqueString> keys = new ArrayList<>();
        List<Value> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : ((JSONObject) node).entrySet()) {
            keys.add(UniqueString.uniqueStringOf(entry.getKey()));
            values.add(getValue(entry.getValue()));
        }
        return new RecordValue(keys.toArray(new UniqueString[0]), values.toArray(new Value[0]), false);
    }
}
