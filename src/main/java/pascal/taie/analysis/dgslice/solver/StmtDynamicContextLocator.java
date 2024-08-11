package pascal.taie.analysis.dgslice.solver;

import pascal.taie.World;
import pascal.taie.analysis.dgslice.DGSliceReporter;
import pascal.taie.analysis.dgslice.TraceUtil;
import pascal.taie.analysis.dgslice.slicegraph.SGNode;
import pascal.taie.ir.stmt.StoreArray;
import pascal.taie.ir.stmt.StoreField;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.TypeSystem;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Sets;

import java.util.Map;
import java.util.Set;

public class StmtDynamicContextLocator {
    private ClassHierarchy hierarchy;
    private TypeSystem typeSystem;
    private DGSliceReporter reporter;

    // Field Access Map
    public MultiMap<JField, StoreField> Field2StoreField;
    public Map<StoreField, JMethod> StoreField2Method;
    public Map<StoreArray, JMethod> StoreArray2Method;

    private static StmtDynamicContextLocator accessLocator;
    protected StmtDynamicContextLocator() {
        hierarchy = World.get().getClassHierarchy();
        typeSystem = World.get().getTypeSystem();
        reporter = DGSliceReporter.get();

        Field2StoreField = Maps.newMultiMap();
        StoreField2Method = Maps.newMap();
        StoreArray2Method = Maps.newMap();
    }

    public static StmtDynamicContextLocator get() {
        if (accessLocator == null) {
            accessLocator = new StmtDynamicContextLocator();
        }
        return accessLocator;
    }

    // Get Field and StoreField API
    public Set<StoreField> getStores(JField field) {
        return Field2StoreField.get(field);
    }

    // Analyze Field Store and Array Store
    public void countFieldAccesses() {
        hierarchy.allClasses()
                .filter(jClass -> !jClass.isInterface() && jClass.isApplication())
                .forEach(jClass -> {
                    jClass.getDeclaredMethods().stream()
                            .filter(method -> !method.isAbstract() && method.getIR() != null)
                            .forEach(method ->
                                    method.getIR().getStmts().stream()
                                            .filter(stmt -> stmt instanceof StoreField
                                                            || stmt instanceof StoreArray)
                                            .forEach(stmt -> {
                                                if (stmt instanceof StoreField) {
                                                    var field = ((StoreField) stmt).getFieldRef().resolveNullable();
                                                    if (field != null) {
                                                        Field2StoreField.put(field, (StoreField) stmt);
                                                        StoreField2Method.put((StoreField) stmt, method);
                                                    }
                                                } else
                                                    StoreArray2Method.put((StoreArray) stmt, method);
                                            }));
                });
    }

    public boolean dynamicReach(StoreField sf) {
        return TraceUtil.dynM2Traces.containsKey(StoreField2Method.get(sf).getSignature());
    }

    public boolean dynamicReach(StoreArray sa) {
        return StoreArray2Method.get(sa) != null &&
                TraceUtil.dynM2Traces.containsKey(StoreArray2Method.get(sa).getSignature());
    }

    public boolean dynamicReach(JMethod method) {
        return TraceUtil.dynM2Traces.containsKey(method.getSignature());
    }

    public Set<TraceContext> generateAllContext(StoreField sf, SGNode sgNode) {
        Set<TraceContext> set = Sets.newSet();

        JMethod method = StoreField2Method.get(sf);
        for (String trace : TraceUtil.dynM2Traces.get(method.getSignature())) {
            TraceContext context = TraceContext.getCurrentTraceContext(trace);
            if (context.validate()) {
                set.add(context);
            } else {
                reporter.CollectNotInCW(context, sgNode);
            }
        }

        return set;
    }

    public Set<TraceContext> generateAllContext(StoreArray sa, SGNode sgNode) {
        Set<TraceContext> set = Sets.newSet();

        JMethod method = StoreArray2Method.get(sa);
        for (String trace : TraceUtil.dynM2Traces.get(method.getSignature())) {
            TraceContext context = TraceContext.getCurrentTraceContext(trace);
            if (context.validate()) {
                set.add(context);
            } else {
                reporter.CollectNotInCW(context, sgNode);
            }
        }

        return set;
    }

    public Set<TraceContext> generateAllContext(JMethod method, SGNode sgNode) {
        Set<TraceContext> set = Sets.newSet();

        for (String trace : TraceUtil.dynM2Traces.get(method.getSignature())) {
            TraceContext context = TraceContext.getCurrentTraceContext(trace);
            if (context.validate()) {
                set.add(context);
            } else {
                reporter.CollectNotInCW(context, sgNode);
            }
        }

        return set;
    }
}
