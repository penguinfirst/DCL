package pascal.taie.analysis.dgslice;

import pascal.taie.analysis.ProgramAnalysis;
import pascal.taie.analysis.dgslice.slicegraph.*;
import pascal.taie.analysis.dgslice.solver.SliceSolver;
import pascal.taie.config.AnalysisConfig;

public class DGSlice extends ProgramAnalysis<DGSG<SGNode>> {

    public static final String ID = "dgslice";

    // Slice Engine
    public SliceSolver solver;

    // Output
    public CIDGSG<SGNode> sliceGraph;
    public MergedSliceGraph<MSGNode> mergedSliceGraph;

    public DGSlice(AnalysisConfig config) {
        super(config);

        // Process inputs and options
        GlobalOptions.processOptions(getOptions());

        // Initialize Slice Nodes
        sliceGraph = new CIDGSG<>();
        // Initialize Slice Engine
        solver = new SliceSolver(sliceGraph);

        // Initialize Output
        mergedSliceGraph = new MergedSliceGraph<>(sliceGraph, solver);

        // Initialize Slice Reporter
        DGSliceReporter.initializeReporter(sliceGraph, mergedSliceGraph, solver);
    }

    @Override
    public DGSG<SGNode> analyze() {
        solver.solve();

        if (GlobalOptions.dump) {
            DGSliceReporter.get().trivialDump();
            DGSliceReporter.get().dump();
        }

        return sliceGraph;
    }
}
