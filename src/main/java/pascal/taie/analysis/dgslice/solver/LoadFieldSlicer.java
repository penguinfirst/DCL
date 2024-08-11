package pascal.taie.analysis.dgslice.solver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.dgslice.slicegraph.CIDGSG;
import pascal.taie.analysis.dgslice.slicegraph.DGSGEdge;
import pascal.taie.analysis.dgslice.slicegraph.SGNode;
import pascal.taie.ir.stmt.LoadField;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.StoreField;
import pascal.taie.language.classes.JField;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * LoadFieldSlicer slice a field to find all its defs in dynamic trace
 * LoadField : x = y.f
 */
public class LoadFieldSlicer extends StmtSlicer {
    private static final Logger logger = LogManager.getLogger(LoadFieldSlicer.class);
    private static LoadFieldSlicer slicer;

    protected LoadFieldSlicer(CIDGSG<SGNode> sliceGraph, SliceSolver solver) {
        super(sliceGraph, solver);
    }

    public static void initializeSlicer(CIDGSG<SGNode> sliceGraph, SliceSolver solver) {
        if (slicer == null) {
            slicer = new LoadFieldSlicer(sliceGraph, solver);
        }
    }

    public static LoadFieldSlicer get() {
        if (slicer == null) {
            logger.info("Error: Get LoadFieldSlicer Before Initialization");
            return null;
        }
        return slicer;
    }

    /**
     * LoadField : x = y.f
     * @DefLookingFor y.f
     */
    @Override
    public void doSlice(ToSliceNode tsNode) {
        validateLoadField(tsNode.stmt);

        SGNode target = solver.getOrMockSGNode(tsNode, this);

        if (reachDepthLimit(target)) {
            sliceGraph.addEntry(target);
            target.hasReachDepthLimit = true;
            return;
        }

        // 1.Find all field store for y.f
        LoadField loadField = (LoadField) tsNode.stmt;
        JField field = loadField.getFieldAccess().getFieldRef().resolveNullable();

        if (field == null) {
            sliceGraph.addEntry(target);
            target.hasNotResolvedField = true;
            logger.info("Error: Field in stmt {} can not be resolved", tsNode.stmt);
            return;
        }

        Set<StoreField> allStores = contextLocator.getStores(field);

        if (allStores == null) {
            // TO DO : Reflection Set
            logger.info("Error: No field stores or field set in the reflection for field {}", field.toString());
            return;
        }

        // 2.Filter dynamic reached field store
        Set<StoreField> dynStores = allStores.stream().filter(sf -> contextLocator.dynamicReach(sf)).collect(Collectors.toSet());
        if (dynStores.isEmpty()) {
            // TO DO : Reflection
            logger.info("Error: No [dynamic reached] field stores or field set in the reflection for field {}",
                    field.toString());
            return;
        }

        // 3.1.Generate TraceContext set for each StoreField stmt
        // 3.2.Create ToSliceNode and SGNode for IntaSideEffectStmtSlicer
        // 3.3.Create DGSGEdge and add to worklist
        for (StoreField sf : dynStores) {
            Set<TraceContext> contextSet = contextLocator.generateAllContext(sf, target);
            for (TraceContext context : contextSet) {
                ToSliceNode sfTsNode = solver.getOrMockToSliceNode(
                        sf, sf.getFieldAccess().getType(), context);
                SGNode source = solver.getOrMockSGNode(sfTsNode, IntraSideEffectStmtSlicer.get());
                sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.DEFINE, source, target));
                solver.addToWorkList(sfTsNode, IntraSideEffectStmtSlicer.get());
            }
        }
    }

    /* Validation Utility Functions */
    private void validateLoadField(Stmt stmt) {
        logger.info("LoadFieldSlicer Current Slicing -> {}", stmt);
        assert stmt instanceof LoadField
                : "LoadFieldSlicer only slice LoadField stmt";
    }
}
