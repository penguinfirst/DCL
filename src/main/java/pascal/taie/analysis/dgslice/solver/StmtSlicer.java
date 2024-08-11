package pascal.taie.analysis.dgslice.solver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.dgslice.DGSliceReporter;
import pascal.taie.analysis.dgslice.GlobalOptions;
import pascal.taie.analysis.dgslice.slicegraph.CIDGSG;
import pascal.taie.analysis.dgslice.slicegraph.DGSGEdge;
import pascal.taie.analysis.dgslice.slicegraph.SGNode;
import pascal.taie.analysis.pta.PointerAnalysis;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.core.heap.ConstantObj;
import pascal.taie.ir.exp.ClassLiteral;
import pascal.taie.ir.exp.FieldAccess;
import pascal.taie.ir.exp.InstanceFieldAccess;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;

public abstract class StmtSlicer {
    // Logger and Debugger
    private static final Logger logger = LogManager.getLogger(StmtSlicer.class);

    // World
    public ClassHierarchy hierarchy;
    public TypeSystem typeSystem;
    public PointerAnalysisResult pta;
    public StmtDynamicContextLocator contextLocator;

    // Slice Graph and Solver
    public CIDGSG<SGNode> sliceGraph;
    public SliceSolver solver;

    // Reporter and Dump
    public DGSliceReporter reporter;

    public StmtSlicer(CIDGSG<SGNode> sliceGraph, SliceSolver solver) {
        this.sliceGraph = sliceGraph;
        this.solver = solver;
        hierarchy = World.get().getClassHierarchy();
        typeSystem = World.get().getTypeSystem();
        pta = World.get().getResult(PointerAnalysis.ID);
        contextLocator = StmtDynamicContextLocator.get();
        reporter = DGSliceReporter.get();
    }

    public void switchSlicer(Stmt defStmt, ToSliceNode defToNode, SGNode target) {
        // TO DO : Support More Stmt
        if (defStmt instanceof Copy || defStmt instanceof Cast) {
            // Direct Assign Stmt

            ToSliceNode daTsNode = solver.getOrMockToSliceNode(defStmt,
                    defToNode.expectedType, defToNode.context);
            SGNode source = solver.getOrMockSGNode(daTsNode, IntraSideEffectStmtSlicer.get());
            if (defStmt instanceof Cast cast) {
                if (!SliceUtil.isJDKType(cast.getRValue().getCastType())
                    && SliceUtil.isJDKType(cast.getRValue().getValue().getType())) {
                    sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.CAST_OUT_JDK, source, target));
                } else {
                    sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.CAST, source, target));
                }
            } else
                sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.DEFINE, source, target));
            solver.addToWorkList(daTsNode, IntraSideEffectStmtSlicer.get());
        } else if (defStmt instanceof LoadField loadField) {
            // LoadField Stmt

            FieldAccess fieldAccess = loadField.getFieldAccess();
            if (fieldAccess instanceof InstanceFieldAccess ifa) {
                ToSliceNode lfBaseTsNode = solver.getOrMockToSliceNode(loadField,
                        ifa.getBase().getType(), defToNode.context);
                SGNode source = solver.getOrMockSGNode(lfBaseTsNode, IntraSideEffectStmtSlicer.get());
                sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.FIELD_BASE, source, target));
                solver.addToWorkList(lfBaseTsNode, IntraSideEffectStmtSlicer.get());
            }

            ToSliceNode lfTsNode = solver.getOrMockToSliceNode(loadField,
                    loadField.getFieldAccess().getType(), defToNode.context);
            SGNode source = solver.getOrMockSGNode(lfTsNode, LoadFieldSlicer.get());
            sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.FIELD_DEFINE, source, target));
            solver.addToWorkList(lfTsNode, LoadFieldSlicer.get());
        } else if (defStmt instanceof LoadArray loadArray) {
            // TO DO : Complex slicing for pts(a) is empty
            // LoadArray Stmt x = a[i]

            // 1.Slice a in x = a[i]
            ToSliceNode baseTsNode = solver.getOrMockToSliceNode(
                    loadArray, loadArray.getArrayAccess().getBase().getType(), defToNode.context);
            SGNode baseNode = solver.getOrMockSGNode(baseTsNode, IntraSideEffectStmtSlicer.get());
            sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.ARRAY_BASE, baseNode, target));
            solver.addToWorkList(baseTsNode, IntraSideEffectStmtSlicer.get());

            // 2.Slice a[i] for all [naive] def of a[i]
            ToSliceNode arrayTsNode = solver.getOrMockToSliceNode(
                    loadArray, defToNode.expectedType, defToNode.context);
            SGNode arrayNode = solver.getOrMockSGNode(arrayTsNode, NaiveLoadArraySlicer.get());
            sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.ARRAY_DEFINE, arrayNode, target));
            solver.addToWorkList(arrayTsNode, NaiveLoadArraySlicer.get());
        } else if (defStmt instanceof Invoke invoke) {
            // TO DO : JDK Model and Reflection Model
            // Invoke Stmt x = y.m(a1, a2, ..., an)
            // Looking for x

            ToSliceNode tsNode = solver.getOrMockToSliceNode(
                    invoke, defToNode.expectedType, defToNode.context);
            SGNode source = solver.getOrMockSGNode(tsNode, InvokeSlicer.get());
            sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.DEFINE, source, target));
            solver.addToWorkList(tsNode, InvokeSlicer.get());
        } else if (defStmt instanceof AssignLiteral assignLiteral) {
            // Assign Literal x = Literal must be ReferenceLiteral
            // Slice end here

            ToSliceNode litAssignNode = solver.getOrMockToSliceNode(
                    assignLiteral, assignLiteral.getRValue().getType(), defToNode.context);
            SGNode source = solver.getMockSGNodeLiteral(litAssignNode, assignLiteral.getRValue());
            sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.DEFINE, source, target));
            sliceGraph.addEntry(source);
        } else if (defStmt instanceof New) {
            logger.info("Error: Should not slice to New Stmt {} in method {}",
                    defStmt, defToNode.context.currentMethodSig);
        } else if (defStmt instanceof Throw
                    || defStmt instanceof Catch
                    || defStmt instanceof Monitor) {
            logger.info("Error: Not support Throw/Catch/Monitor for {} of type {}",
                    defStmt, defStmt.getClass());
        } else {
            logger.info("Error: Not support for {} of type {}",
                    defStmt, defStmt.getClass());
        }
    }

    public void jumpOutToPrecedingStack(ToSliceNode tsNode, SGNode sgNode) {
        if (tsNode.context.currentDepth == 0) {
            logger.info("Error: Reach the start of the trace {}, can not jump out",
                    tsNode.context.currentMethodSig);
            return;
        }

        // 1.Find index of varLF in the current method parameters or this
        int index = getArgIndex(sgNode.varLF, tsNode.context.currentMethod);

        // 2.Find preceding method and locate the invocation stmt
        StackElement preElement = tsNode.context.stack.get(tsNode.context.currentDepth - 1);
        JMethod preMethod = preElement.method;

        // 2-Special-1 Skip reflection part
        if (preMethod.getDeclaringClass().getName().equals("jdk.internal.reflect.NativeMethodAccessorImpl")) {
            int notReflectIndex = tsNode.context.currentDepth - 1;
            while (SliceUtil.isJDKReflectMethod(tsNode.context.stack.get(notReflectIndex).methodSig))
                notReflectIndex--;
            if (SliceUtil.isJDKMethod(tsNode.context.stack.get(notReflectIndex).methodSig)) {
                // TO DO : JDK Model
                logger.info("Error: Need to apply JDK model to {} from stmt {} back to",
                        tsNode.context.stack.get(notReflectIndex).methodSig, tsNode.stmt);
                return;
            }
            StackElement notJDKElement = tsNode.context.stack.get(notReflectIndex);
            JMethod notJDKMethod = notJDKElement.method;
            Invoke refInvoke = (Invoke) SliceUtil.locateInvokeStmt(
                    notJDKMethod.getDeclaringClass().getName(),
                    notJDKMethod.getSignature(),
                    "<java.lang.reflect.Method: java.lang.Object invoke(java.lang.Object,java.lang.Object[])>",
                    notJDKElement.line);

            if (refInvoke == null) {
                sliceGraph.addEntry(sgNode);
                sgNode.hasNotLocatedInvoke = true;
                return;
            }

            // Index == -1 : normal invoke slice this of method.invoke(this, args)
            if (index == -1) {
                TraceContext notJDKContext = TraceContext.getTraceContextAtIndex(tsNode.context, notReflectIndex);
                ToSliceNode notJDKSliceNode = solver.getOrMockToSliceNode(refInvoke, tsNode.expectedType, notJDKContext, 0);
                SGNode notJDKNode = solver.getOrMockSGNode(notJDKSliceNode, IntraSideEffectStmtSlicer.get());
                sliceGraph.addNode(notJDKNode);
                sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.REFLECTION_THIS_PASSING,
                        notJDKNode, sgNode));
                solver.addToWorkList(notJDKSliceNode, IntraSideEffectStmtSlicer.get());
                return;
            }

            // Index != -1 : should find which defStmt defines args[index] of method.invoke(this, args)
            StoreArray storeArray = SliceUtil.findReflectionArgsDefAtIndex(refInvoke, index);
            if (storeArray == null) {
                logger.info("Error: Cannot find array store to {}th of reflection invoke {}",
                        index, refInvoke);
                return;
            }

            TraceContext notJDKContext = TraceContext.getTraceContextAtIndex(tsNode.context, notReflectIndex);
            ToSliceNode notJDKSliceNode = solver.getOrMockToSliceNode(refInvoke, tsNode.expectedType, notJDKContext);
            SGNode notJDKNode = solver.getOrMockSGNode(notJDKSliceNode, IntraSideEffectStmtSlicer.get());
            sliceGraph.addNode(notJDKNode);
            sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.REFLECTION_ARGUMENT_PASSING,
                    notJDKNode, sgNode));
            solver.addToWorkList(notJDKSliceNode, IntraSideEffectStmtSlicer.get());
            return;
        }

        // 2-Special-2 Functional programming will allow jdk method to invoke to APP method
        if (SliceUtil.isJDKMethod(preMethod.getSignature())) {
            // TO DO : JDK Functional Model
            logger.info("Error: Need to apply JDK Functional model to {} from stmt {} back to",
                    preMethod.getSignature(), tsNode.stmt);
            return;
        }

        // 2.Find preceding method and locate the invocation stmt - Continue
        Invoke invoke = (Invoke) SliceUtil.locateInvokeStmt(
                            preMethod.getDeclaringClass().getName(),
                            preMethod.getSignature(),
                            tsNode.context.currentMethodSig,
                            preElement.line);

        if (invoke == null) {
            sliceGraph.addEntry(sgNode);
            sgNode.hasNotLocatedInvoke = true;
            return;
        }

        // 3.Construct traceContext and then ToSLiceNode
        TraceContext preContext = TraceContext.getPreStackContext(tsNode.context);
        ToSliceNode preTsNode = solver.getOrMockToSliceNode(
                invoke, tsNode.expectedType, preContext, index);

        // 4.Construct SGNode and SGEdge
        SGNode preNode = solver.getOrMockSGNode(preTsNode, IntraSideEffectStmtSlicer.get());
        sliceGraph.addNode(preNode);
        sliceGraph.addEdge(new DGSGEdge<>(DGSGEdge.Kind.ARGUMENT_PASSING,
                                          preNode, sgNode));

        // 5.Add new slice node to worklist
        solver.addToWorkList(preTsNode, IntraSideEffectStmtSlicer.get());
    }

    /* Check Utility Functions */
    // Get index if the looking for var is base or argument of its container method
    protected int getArgIndex(Var varLF, JMethod method) {
        if (method.getIR().getThis() == varLF)
            return -1;
        return method.getIR().getParams().indexOf(varLF);
    }

    // Check if the looking for var is base or argument of its container method
    protected boolean checkVarIfArguments(Var varLF, JMethod method) {
        if (method.getIR().getThis() == varLF || method.getIR().getParams().contains(varLF)) {
            return true;
        }
        return false;
    }

    /* Validation Utility Functions */
    protected boolean validateLookingFor(Var var, Type expectedType) {
        return !pta.getPointsToSet(var)
                    .stream()
                    .filter(obj -> typeSystem.isSubtype(expectedType, obj.getType()) )
                    .toList()
                    .isEmpty();
    }

    protected boolean validateLookingForClass(Var var, Type expectedType, String expectedClass) {
        if (expectedType.getName().equals("java.lang.Class")) {
            return pta.getPointsToSet(var)
                        .stream()
                        .filter(obj ->  {
                            if (obj instanceof ConstantObj constObj) {
                                if (constObj.getAllocation() instanceof ClassLiteral classLiteral) {
                                    return classLiteral.getTypeValue().getName().equals(expectedClass);
                                }
                            }
                            return false;
                        }).toList().isEmpty();
        }
        return true;
    }

    protected boolean validateLookingForSpecial(Var varLF, ToSliceNode tsNode) {
        if (tsNode.expectedType.getName().equals("java.lang.Class")) {
            return !pta.getPointsToSet(varLF)
                    .stream()
                    .filter(obj ->  {
                        if (obj instanceof ConstantObj constObj) {
                            if (constObj.getAllocation() instanceof ClassLiteral classLiteral) {
                                return classLiteral.getTypeValue().getName().equals("hudson.WebAppMain");
                            }
                        }
                        return false;
                    }).toList().isEmpty();
        }
        return true;
    }

    public boolean reachDepthLimit(SGNode sgNode) {
        if (GlobalOptions.depth == -1)
            return false;

        int pathLength = sliceGraph.shortestPathLength(sgNode, sliceGraph.getTarget());
        if (pathLength >= GlobalOptions.depth)
            return true;
        if (pathLength == -1)
            logger.info("Strange: Node {} can not reach target", sgNode.stmt);
        return false;
    }


    /* Interface For Different Stmt Slicer */
    public abstract void doSlice(ToSliceNode tsNode);
}
