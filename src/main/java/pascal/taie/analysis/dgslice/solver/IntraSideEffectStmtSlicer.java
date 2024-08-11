package pascal.taie.analysis.dgslice.solver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.defuse.DefUse;
import pascal.taie.analysis.defuse.DefUseAnalysis;
import pascal.taie.analysis.dgslice.slicegraph.CIDGSG;
import pascal.taie.analysis.dgslice.slicegraph.SGNode;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.JMethod;

import java.util.Set;

/**
 * IntraSideEffectStmtSlicer slice a var of an assignment or invoke
 * which produce intra-side-effect on def-use
 * Copy : x = y |
 * Cast : x = (A) y |
 * StoreArray : a[i] = y |
 * StoreField : x.f = y |
 * LoadField : x = y.f |
 * LoadArray : x = a[i] |
 * Return : return y |
 * Invoke : x = y.m(a1, ..., an)
 */
public class IntraSideEffectStmtSlicer extends StmtSlicer {
    private static final Logger logger = LogManager.getLogger(IntraSideEffectStmtSlicer.class);
    private static IntraSideEffectStmtSlicer slicer;

    protected IntraSideEffectStmtSlicer(CIDGSG<SGNode> sliceGraph, SliceSolver solver) {
        super(sliceGraph, solver);
    }

    public static void initializeSlicer(CIDGSG<SGNode> sliceGraph, SliceSolver solver) {
        if (slicer == null) {
            slicer = new IntraSideEffectStmtSlicer(sliceGraph, solver);
        }
    }

    public static IntraSideEffectStmtSlicer get() {
        if (slicer == null) {
            logger.info("Error: Get IntraSideEffectStmtSlicer Before Initialization");
            return null;
        }
        return slicer;
    }

    /**
     * Copy : x = y |
     * Cast : x = (A) y |
     * StoreArray : a[i] = y |
     * StoreField : x.f = y |
     * LoadField : x = y.f |
     * LoadArray : x = a[i] |
     * Return : return y |
     * Invoke : x = y.m(a1, ..., an)
     * @DefLookingFor y | a | a1 | ... | an
     */
    @Override
    public void doSlice(ToSliceNode tsNode) {
        validateIntraSideEffectStmt(tsNode.stmt);

        JMethod method = tsNode.context.currentMethod;
        SGNode node = solver.getOrMockSGNode(tsNode, this);

        if (reachDepthLimit(node)) {
            sliceGraph.addEntry(node);
            node.hasReachDepthLimit = true;
            return;
        }

        if (validateLookingFor(node.varLF, tsNode.expectedType)
                && validateLookingForSpecial(node.varLF, tsNode)) {
            node.hasExpectedType = true;
            sliceGraph.addEntry(node);
            return;
        }

        DefUse defuse = method.getIR().getResult(DefUseAnalysis.ID);
        Set<Stmt> defList = defuse.getDefs(tsNode.stmt, node.varLF);
        if (defList.isEmpty()) {
            if (checkVarIfArguments(node.varLF, method)) {
                jumpOutToPrecedingStack(tsNode, node);
            } else if (node.varLF.isConst()){
                logger.info("Good: varLF {} is constant of [value : {}] and [literal class : {}]",
                        node.varLF.toString(), node.varLF.getConstValue(), node.varLF.getConstValue().getClass());
                sliceGraph.addEntry(node);
            } else {
                logger.info("Error: {} in stmt {} has no def and also not the parameter of method {} ",
                        node.varLF, tsNode.stmt, method.getSignature());
            }
        } else {
            for (Stmt defStmt : defuse.getDefs(tsNode.stmt, node.varLF)) {
                switchSlicer(defStmt, tsNode, node);
            }
        }
    }

    /* Validation Utility Functions */
    private void validateIntraSideEffectStmt(Stmt stmt) {
        logger.info("IntraSideEffectStmtSlicer Current Slicing -> {}", stmt);
        assert stmt instanceof Copy
                || stmt instanceof StoreArray
                || stmt instanceof StoreField
                || stmt instanceof Cast
                || stmt instanceof Invoke
                || stmt instanceof LoadField
                || stmt instanceof LoadArray
                || stmt instanceof Return
                : "IntraSideEffectStmtSlicer only slice intra-def-use effect stmt";
    }
}
