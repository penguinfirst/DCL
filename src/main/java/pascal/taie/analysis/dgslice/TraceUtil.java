package pascal.taie.analysis.dgslice;

import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;

import java.util.List;

public class TraceUtil {

    public static MultiMap<String, String> dynM2Traces = Maps.newMultiMap();

    public static void storeRawTraces(List<String> traces) {
        for (String trace : traces) {
            dynM2Traces.put(trace.split("#")[0], trace);
        }
    }

}
