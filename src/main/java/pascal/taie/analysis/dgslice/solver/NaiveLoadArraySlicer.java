package pascal.taie.analysis.dgslice.solver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.dgslice.slicegraph.CIDGSG;
import pascal.taie.analysis.dgslice.slicegraph.DGSGEdge;
import pascal.taie.analysis.dgslice.slicegraph.SGNode;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.LoadArray;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.StoreArray;

import java.util.Set;
import java.util.stream.Collectors;

public class NaiveLoadArraySlicer extends StmtSlicer {

    private static final Logger logger = LogManager.getLogger(NaiveLoadArraySlicer.class);
    private static NaiveLoadArraySlicer slicer;

    protected NaiveLoadArraySlicer(CIDGSG<SGNode> sliceGraph, SliceSolver solver) {
        super(sliceGraph, solver);
    }

    public static void initializeSlicer(CIDGSG<SGNode> sliceGraph, SliceSolver solver) {
        if (slicer == null) {
            slicer = new NaiveLoadArraySlicer(sliceGraph, solver);
        }
    }

    public static NaiveLoadArraySlicer get() {
        if (slicer == null) {
            logger.info("Error: Get NaiveLoadArraySlicer Before Initialization");
            return null;
        }
        return slicer;
    }

    @Override
    public void doSlice(ToSliceNode tsNode) {
        validateArrayLoadStmt(tsNode.stmt);

        LoadArray loadArray = (LoadArray) tsNode.stmt;

        SGNode sgNode = solver.getOrMockSGNode(tsNode, this);

        if (reachDepthLimit(sgNode)) {
            sliceGraph.addEntry(sgNode);
            sgNode.hasReachDepthLimit = true;
            return;
        }

        Var base = sgNode.arrayAccessLF.getBase();

        // 1.pts(base) is empty, naive approach
        if (pta.getPointsToSet(base).isEmpty()) {
            for (StoreArray storeArray : base.getStoreArrays()) {
                ToSliceNode arrayTsNode = solver.getOrMockToSliceNode(
                        storeArray, tsNode.expectedType, tsNode.context);
                SGNode arrayNode = solver.getOrMockSGNode(arrayTsNode, IntraSideEffectStmtSlicer.get());
                sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.DEFINE, arrayNode, sgNode));
                solver.addToWorkList(arrayTsNode, IntraSideEffectStmtSlicer.get());
            }
            return;
        }

        // 2.pts(base) is not empty
        Set<Var> mayAliasVars = pta.getVars()
                                        .stream()
                                        .filter(v -> !v.getStoreArrays().isEmpty()
                                                        && pta.mayAlias(v, base))
                                        .collect(Collectors.toSet());

        for (Var v : mayAliasVars) {
            for (StoreArray storeArray : v.getStoreArrays()) {
                if (contextLocator.dynamicReach(storeArray)) {
                    Set<TraceContext> contextSet = contextLocator.generateAllContext(storeArray, sgNode);
                    for (TraceContext context : contextSet) {
                        ToSliceNode arrayTsNode = solver.getOrMockToSliceNode(
                                storeArray, storeArray.getArrayAccess().getType(), context);
                        SGNode source = solver.getOrMockSGNode(arrayTsNode, IntraSideEffectStmtSlicer.get());
                        sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.DEFINE, source, sgNode));
                        solver.addToWorkList(arrayTsNode, IntraSideEffectStmtSlicer.get());
                    }
                }
            }
        }
    }

    /* Validation Utility Functions */
    private void validateArrayLoadStmt(Stmt stmt) {
        logger.info("NaiveLoadArraySlicer Current Slicing -> {}", stmt);
        assert stmt instanceof LoadArray
                : "NaiveLoadArraySlicer only slice LoadArray stmt";
    }
}
