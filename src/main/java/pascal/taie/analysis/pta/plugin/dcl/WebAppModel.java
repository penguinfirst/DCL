package pascal.taie.analysis.pta.plugin.dcl;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.Descriptor;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.util.AnalysisModelPlugin;
import pascal.taie.analysis.pta.plugin.util.InvokeHandler;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.ClassLiteral;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Maps;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WebAppModel extends AnalysisModelPlugin {
    private static final Descriptor WEB_OBJ_DESC = () -> "WebAppObj";

    public static Map<Obj, String> webMap = Maps.newMap();

    protected WebAppModel(Solver solver) {
        super(solver);
    }

    @Override
    public void onStart() {
        super.onStart();
        JMethod method = hierarchy.getMethod("<org.eclipse.jetty.servlet.BaseHolder: void setClassName(java.lang.String)>");
        assert method != null;

        Var nameVar = method.getIR().getParam(0);
        CSVar csNameVar = csManager.getCSVar(emptyContext, nameVar);
        Set<String> xmlSet = preWebXmlAnalysis();
        for (String cName : xmlSet) {
            JClass stringClass = hierarchy.getClass("java.lang.String");
            Type stringType = stringClass.getType();
            Obj obj = heapModel.getMockObj(WEB_OBJ_DESC, cName, stringType, method);
            solver.addPointsTo(csNameVar, obj);
        }
    }

    public Set<String> preWebXmlAnalysis() {
        Set<String> set = new HashSet<>();
        set.add("jenkins.util.SystemProperties$Listener");
        set.add("hudson.WebAppMain");
        set.add("jenkins.JenkinsHttpSessionListener");
        return set;
    }
}
