package pascal.taie.analysis.pta.plugin.dcl;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.heap.ConstantObj;
import pascal.taie.analysis.pta.core.heap.Descriptor;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.util.AnalysisModelPlugin;
import pascal.taie.analysis.pta.plugin.util.InvokeHandler;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.ClassLiteral;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.MapSetMultiMap;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ServiceLoaderModel extends AnalysisModelPlugin {

    private static final Descriptor SL_OBJ_DESC = () -> "ServiceLoaderObj";

    Map<JClass, Set<CSObj>> SL2Services;
    Set<Pair<Context, Invoke>> updateList;
    Set<Invoke> updateListNonContext;

    int mockNumber = 0;
    int updateNumber = 0;

    protected ServiceLoaderModel(Solver solver) {
        super(solver);
        SL2Services = new HashMap<>();
        updateList = new HashSet<>();
        updateListNonContext = new HashSet<>();
    }

    @Override
    public void onStart() {
        super.onStart();
        var jClass = solver.getHierarchy().getClass("java.lang.invoke.DirectMethodHandle$Holder");
        assert jClass != null;
        for (var method : jClass.getDeclaredMethods()) {
            solver.addIgnoredMethod(method);
        }

        var filterClass = solver.getHierarchy().getClass("org.eclipse.jetty.webapp.JettyWebXmlConfiguration");
        assert filterClass != null;
//        for (var method : filterClass.getDeclaredMethods()) {
//            if (!(method.getName().contains("<init>") || method.getName().contains("<clinit>") || method.getSignature().contains("configure")))
//                solver.addIgnoredMethod(method);
//        }

        filterClass = solver.getHierarchy().getClass("org.eclipse.jetty.xml.XmlConfiguration");
        assert filterClass != null;
        for (var method : filterClass.getDeclaredMethods()) {
            if (method.getSignature().contains("<org.eclipse.jetty.xml.XmlConfiguration: java.lang.Object configure(java.lang.Object)>"))
                solver.addIgnoredMethod(method);
        }
    }

    /* Utility Functions */
    private void informServiceUpdate() {
        for (var pair : updateList) {
            var context = pair.first();
            var invoke = pair.second();
            var ret = invoke.getResult();
            for (var set : SL2Services.values()) {
                for (var csObj : set) {
                    solver.addVarPointsTo(context, ret, csObj);
                }
            }
        }
        updateNumber++;
        System.err.println(updateNumber + " -> " + updateList.size());
    }

    private void mockServices(Context context, Invoke invoke, JClass subClz) {
//        if (subClz.getName().startsWith("org.eclipse.jetty.webapp.JettyWebXmlConfiguration"))
//            return;
        mockNumber++;
        System.err.println(subClz.getName() + " -> " + mockNumber);
        Obj obj = heapModel.getMockObj(SL_OBJ_DESC, invoke,
                subClz.getType(), invoke.getContainer());
        CSObj csObj = csManager.getCSObj(context, obj);

        boolean isNew = false;
        if (!SL2Services.containsKey(subClz)){
            Set<CSObj> set = new HashSet<>();
            isNew = set.add(csObj);
            SL2Services.put(subClz, set);
        } else {
            isNew = SL2Services.get(subClz).add(csObj);
        }

        if (isNew) {
            JMethod method = hierarchy.getMethod("<" + subClz.getName() + ": void <clinit>()>");
            if (method != null) {
                solver.addCSMethod(csManager.getCSMethod(context, method));
            }
            method = hierarchy.getMethod("<" + subClz.getName() + ": void <init>()>");
            if (method != null) {
                solver.addCSMethod(csManager.getCSMethod(context, method));
                var thisVar = method.getIR().getThis();
                solver.addVarPointsTo(context, thisVar, csObj);
            }
        }

        informServiceUpdate();
    }

    @InvokeHandler(signature = {"<java.util.ServiceLoader: java.util.ServiceLoader load(java.lang.Class)>",
            "<java.util.ServiceLoader: java.util.ServiceLoader load(java.lang.Class,java.lang.ClassLoader)>"},
            argIndexes = {0})
    public void ServiceLoaderLoad(Context context, Invoke invoke, PointsToSet pts) {
        var clzVar = invoke.getInvokeExp().getArg(0);
        if (clzVar.isConst()) {
            var clzName = ((ClassLiteral) clzVar.getConstValue()).getTypeValue().getName();
            var jClass = hierarchy.getClass(clzName);
            if (jClass != null) {
                hierarchy.getAllSubclassesOf(jClass).forEach(subClz -> {
                    if (!(subClz.isInterface()
                            || subClz.isAbstract()
                            || subClz.isPhantom())) {
                        mockServices(context, invoke, subClz);
                    }
                });
            }
        }
    }

    @InvokeHandler(signature = "<java.util.ServiceLoader$Provider: java.lang.Object get()>", argIndexes = {-1})
    public void ServiceProvideGet(Context context, Invoke invoke, PointsToSet pts) {
        updateList.add(new Pair<>(context, invoke));
        System.err.println(invoke.toString());
        informServiceUpdate();
    }
}
