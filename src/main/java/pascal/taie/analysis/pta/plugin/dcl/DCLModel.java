package pascal.taie.analysis.pta.plugin.dcl;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.heap.Descriptor;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.util.AnalysisModelPlugin;
import pascal.taie.analysis.pta.plugin.util.InvokeHandler;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.ClassLiteral;
import pascal.taie.ir.exp.StringLiteral;
import pascal.taie.ir.stmt.Invoke;

public class DCLModel extends AnalysisModelPlugin {
    private static final Descriptor DCL_OBJ_DESC = () -> "DynamicLoadingObj";

    protected DCLModel(Solver solver) {
        super(solver);
    }

    @InvokeHandler(signature = "<java.lang.ClassLoader: java.lang.Class loadClass(java.lang.String)>", argIndexes = {0})
    public void loadClassByString(Context context, Invoke invoke, PointsToSet pts) {
        if (invoke.getContainer().getSignature().equals(
                "<org.eclipse.jetty.util.Loader: java.lang.Class loadClass(java.lang.String)>")) {
            for (var csObj : pts.getObjects()) {
                var obj = csObj.getObject();
                if (obj.getAllocation() instanceof String cName) {
                    var loadClz = hierarchy.getClass(cName);
                    if (loadClz != null) {
                        solver.initializeClass(loadClz);
                        ClassLiteral classLiteral = ClassLiteral.get(loadClz.getType());
                        Obj classObj = heapModel.getConstantObj(classLiteral);
                        CSObj csClassObj = csManager.getCSObj(context, classObj);
                        solver.addVarPointsTo(context, invoke.getResult(), csClassObj);
                        System.err.println("WebApp Model: " + cName + " has been returned");
                    } else {
                        System.err.println("WebApp Model Warning: " + cName + " not found!");
                    }
                }
            }
        }

        var pName = invoke.getInvokeExp().getArg(0);
        if (pName.isConst()) {
            var cName = pName.getConstValue();
            if (cName instanceof StringLiteral sName) {
                var name = sName.getString();
                var loadClz = hierarchy.getClass(name);
                if (loadClz != null) {
                    solver.initializeClass(loadClz);
                    ClassLiteral classLiteral = ClassLiteral.get(loadClz.getType());
                    Obj obj = heapModel.getConstantObj(classLiteral);
                    CSObj csObj = csManager.getCSObj(context, obj);
                    solver.addVarPointsTo(context, invoke.getResult(), csObj);
                } else {
                    System.err.println("WebApp Model Warning: " + name + " not found!");
                }
            }
        }
    }
}
