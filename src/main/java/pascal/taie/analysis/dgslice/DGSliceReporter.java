package pascal.taie.analysis.dgslice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.dgslice.slicegraph.*;
import pascal.taie.analysis.dgslice.solver.SliceSolver;
import pascal.taie.analysis.dgslice.solver.TraceContext;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

public class DGSliceReporter {
    /* Logging and Slicing */
    private static final Logger logger = LogManager.getLogger(DGSliceReporter.class);

    public SliceSolver solver;
    public CIDGSG<SGNode> sliceGraph;
    public MergedSliceGraph<MSGNode> mergedSliceGraph;

    /* Singleton */
    private static DGSliceReporter reporter;

    protected DGSliceReporter(CIDGSG<SGNode> sliceGraph,
                              MergedSliceGraph<MSGNode> mergedSliceGraph, SliceSolver solver) {
        this.sliceGraph = sliceGraph;
        this.mergedSliceGraph = mergedSliceGraph;
        this.solver = solver;

        NotInCWNode2MethodSig = Maps.newMultiMap();
    }

    public static void initializeReporter(CIDGSG<SGNode> sliceGraph,
                                          MergedSliceGraph<MSGNode> mergedSliceGraph, SliceSolver solver) {
        if (reporter == null) {
            reporter = new DGSliceReporter(sliceGraph, mergedSliceGraph, solver);
        }
    }

    public static DGSliceReporter get() {
        if (reporter == null) {
            logger.info("Error: Get DGSliceReporter Before Initialization");
            return null;
        }
        return reporter;
    }

    /* PTA Failed Reason */

    // 1.Class not in closed world
    // When slicing x = y.f, there is def
    public MultiMap<SGNode, String> NotInCWNode2MethodSig;

    public void CollectNotInCW(TraceContext context, SGNode sgNode) {
        for (var se : context.stack) {
            if (se.method == null)
                NotInCWNode2MethodSig.put(sgNode, se.methodSig);
        }
    }

    /* DGSlice Graph Dump */
    public void trivialDump() {
        logger.info("********** SliceGraph Entries: **********");
        int index = 0;
        for (SGNode sgNode : sliceGraph.getEntry()) {
            logger.info("Entry {} -> [stmt : {}] [varLF : {}]",
                    index, sgNode.stmt, sgNode.varLF);
            index++;
        }

        logger.info("********** SliceGraph Nodes: **********");
        logger.info("Total Node Number: {}", sliceGraph.getNodes().size());
        Map<Stmt, Integer> stmt2num = Maps.newMap();
        for (SGNode sgNode : sliceGraph.getNodes()) {
            stmt2num.put(sgNode.getStmt(), stmt2num.getOrDefault(sgNode.getStmt(), 0) + 1);
        }
        for (var pair: stmt2num.entrySet()) {
            logger.info("{} -> {}", pair.getKey(), pair.getValue());
        }
    }

    public void dump() {
        mergedSliceGraph.mergeGraph();

        logger.info("Slicing Finished: Dump Slice Graph to {}/slice_unreach.dot", GlobalOptions.targetTraceFileDir);
        try (PrintWriter out = new PrintWriter(new File(GlobalOptions.targetTraceFileDir, "slice_unreach.dot"))) {
            out.println("digraph DGSliceGraph {");
            Map<String, List<MSGNode>> methodMap = mergedSliceGraph.methodSig2msgNodes;

            // Sort and print subgraphs
            for (Map.Entry<String, List<MSGNode>> entry : methodMap.entrySet()) {
                List<MSGNode> nodes = entry.getValue();

                out.println("subgraph cluster_" + Math.abs(entry.getKey().hashCode()) + " {");
                out.println("label = \"" + entry.getKey() + "\";");

                for (MSGNode node : nodes) {
                    String nodeName = "\"" + sanitize(node.getStmt().toString()) + "_" + node.line + "\"";
                    out.println(nodeName + " [label=\"" +
                            sanitize(node.getStmt().toString()) + ": line " + node.line + "\"];");
                }
                out.println("}");
            }

            // Print edges
            for (MSGNode node : mergedSliceGraph.getNodes()) {
                for (MSGEdge<MSGNode> edge : mergedSliceGraph.getOutEdgesOf(node)) {
                    MSGNode src = edge.source();
                    MSGNode tgt = edge.target();
                    String srcName = "\"" + sanitize(src.getStmt().toString()) + "_" + src.line + "\"";
                    String tgtName = "\"" + sanitize(tgt.getStmt().toString()) + "_" + tgt.line + "\"";
                    out.println(srcName + " -> " + tgtName + " [label=\"" + edge.getKind() + "\"];");
                }
            }

            out.println("}");
        } catch (FileNotFoundException e) {
            System.out.println("Error writing to file: " + e.getMessage());
        }
    }

    private String sanitize(String input) {
        return input.replace("\"", "\\\"").replace("\n", "\\l");
    }

    private int getLine(SGNode sgNode) {
        return solver.getToSliceNodeBySGNode(sgNode).stmt.getIndex();
    }

    private JMethod getContainer(SGNode sgNode) {
        return solver.getToSliceNodeBySGNode(sgNode).context.currentMethod;
    }

    private String getContainerSig(SGNode sgNode) {
        return getContainer(sgNode).getSignature();
    }
}
