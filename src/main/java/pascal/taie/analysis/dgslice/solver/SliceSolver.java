package pascal.taie.analysis.dgslice.solver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.dgslice.GlobalOptions;
import pascal.taie.analysis.dgslice.slicegraph.CIDGSG;
import pascal.taie.analysis.dgslice.slicegraph.SGNode;
import pascal.taie.ir.exp.InstanceFieldAccess;
import pascal.taie.ir.exp.InvokeInstanceExp;
import pascal.taie.ir.exp.Literal;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.Pair;

import java.util.*;

public class SliceSolver {
    private static final Logger logger = LogManager.getLogger(SliceSolver.class);
    private final Queue<Pair<ToSliceNode, StmtSlicer>> worklist = new ArrayDeque<>();
    private final CIDGSG<SGNode> sliceGraph;
    private final Set<ToSliceNode> tsNodeSet = new HashSet<>();
    private final Set<Pair<ToSliceNode, StmtSlicer>> hasSliced = new HashSet<>();
    public final Map<Pair<ToSliceNode, StmtSlicer>, SGNode> allNodeMap = Maps.newMap();

    public SliceSolver(CIDGSG<SGNode> sliceGraph) {
        this.sliceGraph = sliceGraph;
    }

    public void solve() {
        initialize();
        analyze();
    }

    public void initialize() {
        // Slicer Initialization
        IntraSideEffectStmtSlicer.initializeSlicer(sliceGraph, this);
        LoadFieldSlicer.initializeSlicer(sliceGraph, this);
        NaiveLoadArraySlicer.initializeSlicer(sliceGraph, this);
        InvokeSlicer.initializeSlicer(sliceGraph, this);

        // Field Access Init
        StmtDynamicContextLocator.get().countFieldAccesses();

        // Add First ToSliceNode
        worklist.add(new Pair<>(getInitialToSliceNode(), IntraSideEffectStmtSlicer.get()));
    }

    public void analyze() {
        while (!worklist.isEmpty()) {
            Pair<ToSliceNode, StmtSlicer> pair  = worklist.poll();
            if (hasSliced.contains(pair)) {
                logger.info("Strange: {} will be sliced at least twice by {}",
                        pair.first().stmt, pair.second().getClass());
                continue;
            } else
                hasSliced.add(pair);
            pair.second().doSlice(pair.first());
        }
    }

    /* Worklist API */
    public void addToWorkList(ToSliceNode tsNode, StmtSlicer slicer) {
        worklist.add(new Pair<>(tsNode, slicer));
    }

    /* Mock ToSliceNode API */
    public ToSliceNode getOrMockToSliceNode(Stmt stmt, Type expectedType, TraceContext context) {
        assert stmt != null;
        ToSliceNode tsNode = new ToSliceNode(stmt, expectedType, context);
        for (var node : tsNodeSet) {
            if (node.equals(tsNode))
                return node;
        }
        tsNodeSet.add(tsNode);
        return tsNode;
    }

    public ToSliceNode getOrMockToSliceNode(Stmt stmt, Type expectedType, TraceContext context, int index) {
        assert stmt != null;
        ToSliceNode tsNode = new ToSliceNode(stmt, expectedType, context, index);
        for (var node : tsNodeSet) {
            if (node.equals(tsNode))
                return node;
        }
        tsNodeSet.add(tsNode);
        return tsNode;
    }

    public ToSliceNode getInitialToSliceNode() {
        return getOrMockToSliceNode(SliceUtil.locateInstanceInvokeStmt(GlobalOptions.className,
                                                GlobalOptions.methodSig,
                                                GlobalOptions.calleeSig,
                                                GlobalOptions.line),
                GlobalOptions.expectedType,
                GlobalOptions.context);
    }

    /* Mock SGNode API */
    public SGNode getMockSGNodeLiteral(ToSliceNode tsNode, Literal literal) {
        var pair = new Pair<>(tsNode, (StmtSlicer) null);
        if (allNodeMap.containsKey(pair))
            return allNodeMap.get(pair);

        // New SGNode
        SGNode node = new SGNode(tsNode.stmt, null, literal.getType());
        node.literal = literal;
        node.hasLiteral = true;
        sliceGraph.addNode(node);
        allNodeMap.put(pair, node);

        return node;
    }

    public SGNode getOrMockSGNode(ToSliceNode tsNode, IntraSideEffectStmtSlicer slicer) {
        var pair = new Pair<>(tsNode, (StmtSlicer) slicer);
        if (allNodeMap.containsKey(pair))
            return allNodeMap.get(pair);

        Type expectedType = tsNode.expectedType;

        Var varLF = null;
        if (tsNode.stmt instanceof Copy copy) {
            varLF = copy.getRValue();
        } else if (tsNode.stmt instanceof Cast cast) {
            varLF = cast.getRValue().getValue();
        } else if (tsNode.stmt instanceof StoreArray storeArray) {
            varLF = storeArray.getRValue();
        } else if (tsNode.stmt instanceof StoreField storeField) {
            varLF = storeField.getRValue();
        } else if (tsNode.stmt instanceof Invoke invoke) {
            if (tsNode.index == -1) {
                varLF = ((InvokeInstanceExp) invoke.getInvokeExp()).getBase();
            } else {
                varLF = invoke.getInvokeExp().getArg(tsNode.index);
            }
        } else if (tsNode.stmt instanceof LoadField loadField) {
            InstanceFieldAccess ifa = (InstanceFieldAccess) loadField.getFieldAccess();
            varLF = ifa.getBase();
        } else if (tsNode.stmt instanceof LoadArray loadArray) {
            varLF = loadArray.getArrayAccess().getBase();
        } else if (tsNode.stmt instanceof Return retStmt) {
            varLF = retStmt.getValue();
        } else {
            logger.info("Error: {} is not IntraSideEffectStmt", tsNode.stmt);
        }

        // New SGNode
        SGNode node = new SGNode(tsNode.stmt, varLF, expectedType);
        sliceGraph.addNode(node);
        allNodeMap.put(pair, node);

        return node;
    }

    public SGNode getOrMockSGNode(ToSliceNode tsNode, LoadFieldSlicer slicer) {
        var pair = new Pair<>(tsNode, (StmtSlicer) slicer);
        if (allNodeMap.containsKey(pair))
            return allNodeMap.get(pair);

        LoadField loadField = (LoadField) tsNode.stmt;
        Type expectedType = loadField.getFieldAccess().getType();

        // New SGNode
        SGNode node = new SGNode(tsNode.stmt, null, expectedType, true, loadField.getFieldAccess());
        sliceGraph.addNode(node);
        allNodeMap.put(pair, node);

        return node;
    }

    public SGNode getOrMockSGNode(ToSliceNode tsNode, NaiveLoadArraySlicer slicer) {
        var pair = new Pair<>(tsNode, (StmtSlicer) slicer);
        if (allNodeMap.containsKey(pair))
            return allNodeMap.get(pair);

        LoadArray loadArray = (LoadArray) tsNode.stmt;

        // New SGNode
        SGNode node = new SGNode(tsNode.stmt, null, tsNode.expectedType, true, loadArray.getArrayAccess());
        sliceGraph.addNode(node);
        allNodeMap.put(pair, node);

        return node;
    }

    public SGNode getOrMockSGNode(ToSliceNode tsNode, InvokeSlicer slicer) {
        var pair = new Pair<>(tsNode, (StmtSlicer) slicer);
        if (allNodeMap.containsKey(pair))
            return allNodeMap.get(pair);

        Invoke invoke = (Invoke) tsNode.stmt;
        Var varLF = invoke.getResult();

        // New SGNode
        SGNode node = new SGNode(tsNode.stmt, varLF, tsNode.expectedType);
        sliceGraph.addNode(node);
        allNodeMap.put(pair, node);

        return node;
    }

    /* Other Utility Functions */
    public ToSliceNode getToSliceNodeBySGNode(SGNode sgNode) {
        for (var map : allNodeMap.entrySet()) {
            if (map.getValue() == sgNode) {
                return map.getKey().first();
            }
        }
        assert false;
        return null;
    }
}
