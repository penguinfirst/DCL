package pascal.taie.analysis.pta.plugin.dcl;

import pascal.taie.analysis.pta.core.solver.DeclaredParamProvider;
import pascal.taie.analysis.pta.core.solver.EntryPoint;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.CompositePlugin;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JMethod;

public class DCLAnalysis extends CompositePlugin {
    private Solver solver;
    private ClassHierarchy hierarchy;

    private DCLModel dclModel;
    @Override
    public void setSolver(Solver solver) {
        this.solver = solver;
        hierarchy = solver.getHierarchy();

        // Dynamic Class Loading Analysis
        if (DCLExperimentStats.enableDCLAnalysis) {
            addPlugin(new DCLEntryHandler(solver));
            if (DCLExperimentStats.enableDCLModel) {
                addPlugin(new DCLModel(solver));
                addPlugin(new ServiceLoaderModel(solver));
                addPlugin(new WebAppModel(solver));
            }
        }
    }
}
