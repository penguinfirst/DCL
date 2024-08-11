package pascal.taie.analysis.dgslice.solver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.dgslice.slicegraph.CIDGSG;
import pascal.taie.analysis.dgslice.slicegraph.DGSGEdge;
import pascal.taie.analysis.dgslice.slicegraph.SGNode;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.ir.exp.InvokeInstanceExp;
import pascal.taie.ir.proginfo.MemberRef;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Return;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.AnalysisException;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.TwoKeyMap;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class InvokeSlicer extends StmtSlicer {
    private static final Logger logger = LogManager.getLogger(InvokeSlicer.class);
    private static InvokeSlicer slicer;

    private TwoKeyMap<JClass, MemberRef, Set<JMethod>> resolveTable;

    protected InvokeSlicer(CIDGSG<SGNode> sliceGraph, SliceSolver solver) {
        super(sliceGraph, solver);

        resolveTable = Maps.newTwoKeyMap();
    }

    public static void initializeSlicer(CIDGSG<SGNode> sliceGraph, SliceSolver solver) {
        if (slicer == null) {
            slicer = new InvokeSlicer(sliceGraph, solver);
        }
    }

    public static InvokeSlicer get() {
        if (slicer == null) {
            logger.info("Error: Get InvokeSlicer Before Initialization");
            return null;
        }
        return slicer;
    }

    @Override
    public void doSlice(ToSliceNode tsNode) {
        validateInvokeStmt(tsNode.stmt);

        Invoke invoke = (Invoke) tsNode.stmt;
        SGNode sgNode = solver.getOrMockSGNode(tsNode, this);

        if (reachDepthLimit(sgNode)) {
            sliceGraph.addEntry(sgNode);
            sgNode.hasReachDepthLimit = true;
            return;
        }

        // TO DO : JDK Model
        // 1.Directly apply JDK model to jdk call site
        Set<JMethod> targetMethodSet = resolveCHACalleesOf(invoke);

        // JDK Model
        Set<JMethod> jdkTargetMethodSet = targetMethodSet.stream()
                .filter(m -> !m.isApplication())
                .collect(Collectors.toSet());

        if (!jdkTargetMethodSet.isEmpty()) {
            for (JMethod method : jdkTargetMethodSet) {
                logger.info("Error: Not support for JDK method {}", method.getSignature());
            }
        }

        // 2.Handle application part
        Set<JMethod> appTargetMethodSet = targetMethodSet.stream()
                .filter(m -> m.isApplication() && contextLocator.dynamicReach(m))
                .collect(Collectors.toSet());

        // Empty set means slice end and maybe a wrong slice path
        if (appTargetMethodSet.isEmpty()) {
            sgNode.hasNotFoundInDynamic = true;
            sliceGraph.addEntry(sgNode);
        } else {
            // 2.1.Recv pointer slice for instance invoke
            // InstanceInvoke x = y.m(a1, a2, ..., an)
            // StaticInvoke x = S.m(a1, a2, ..., an)
            // If instance invoke, do an intra-slice to receiver pointer y
            if (invoke.getInvokeExp() instanceof InvokeInstanceExp invokeInstanceExp) {
                ToSliceNode recvTsNode = solver.getOrMockToSliceNode(
                        invoke, invokeInstanceExp.getBase().getType(), tsNode.context, -1);
                SGNode recvNode = solver.getOrMockSGNode(recvTsNode, IntraSideEffectStmtSlicer.get());
                sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.THIS_PASSING, recvNode, sgNode));
                solver.addToWorkList(recvTsNode, IntraSideEffectStmtSlicer.get());
            }

            // 2.2.Application method return Stmt slice
            for (JMethod method : appTargetMethodSet) {
                // if (!SliceUtil.isJDKMethod(method.getSignature()))
                if (method.isApplication()) {
                    Set<TraceContext> traceContexts = contextLocator.generateAllContext(method, sgNode);
                    Set<Stmt> retStmts = getRetStmts(method);
                    composeSliceNodes(retStmts, traceContexts, sgNode);
                }
            }
        }
    }

    private void composeSliceNodes(Set<Stmt> retStmts, Set<TraceContext> traceContexts, SGNode target) {
        for (Stmt ret : retStmts) {
            for (TraceContext context : traceContexts) {
                ToSliceNode retTsNode = solver.getOrMockToSliceNode(ret, target.expectedType, context);
                SGNode source = solver.getOrMockSGNode(retTsNode, IntraSideEffectStmtSlicer.get());
                sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.RETURN, source, target));
                solver.addToWorkList(retTsNode, IntraSideEffectStmtSlicer.get());
            }
        }
    }

    private Set<Stmt> getRetStmts(JMethod method) {
        return method.getIR().getStmts()
                .stream()
                .filter(stmt -> stmt instanceof Return)
                .collect(Collectors.toSet());
    }

    private Set<JMethod> resolveCHACalleesOf(Invoke callSite) {
        CallKind kind = CallGraphs.getCallKind(callSite);
        return switch (kind) {
            case INTERFACE, VIRTUAL -> {
                MethodRef methodRef = callSite.getMethodRef();
                JClass cls = methodRef.getDeclaringClass();
                Set<JMethod> callees = resolveTable.get(cls, methodRef);
                if (callees == null) {
                    callees = hierarchy.getAllSubclassesOf(cls)
                            .stream()
                            .filter(Predicate.not(JClass::isAbstract))
                            .map(c -> hierarchy.dispatch(c, methodRef))
                            .filter(Objects::nonNull) // filter out null callees
                            .collect(Collectors.toUnmodifiableSet());
                    resolveTable.put(cls, methodRef, callees);
                }
                yield callees;
            }
            case SPECIAL, STATIC -> Set.of(callSite.getMethodRef().resolve());
            case DYNAMIC -> {
                logger.debug("CHA cannot resolve invokedynamic " + callSite);
                yield Set.of();
            }
            default -> throw new AnalysisException(
                    "Failed to resolve call site: " + callSite);
        };
    }

    /* Validation Utility Functions */
    private void validateInvokeStmt(Stmt stmt) {
        logger.info("InvokeSlicer Current Slicing -> {}", stmt);
        assert stmt instanceof Invoke
                : "InvokeSlicer only slice invoke stmt";
    }
}
