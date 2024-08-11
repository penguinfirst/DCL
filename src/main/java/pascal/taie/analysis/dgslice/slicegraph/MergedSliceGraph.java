package pascal.taie.analysis.dgslice.slicegraph;

import pascal.taie.analysis.dgslice.solver.SliceSolver;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;

import javax.annotation.Nullable;
import java.util.*;

public class MergedSliceGraph<N extends MSGNode> extends AbstractDGSG<N> {
    private final MultiMap<N, MSGEdge<N>> inEdges;
    private final MultiMap<N, MSGEdge<N>> outEdges;


    public Map<String, List<Set<SGNode>>> methodSig2sgNodes;
    public Map<JMethod, List<Set<SGNode>>> method2sgNodes;
    public Map<String, List<MSGNode>> methodSig2msgNodes;
    public Map<JMethod, List<MSGNode>> method2msgNodes;

    public final Map<MSGNode, Set<SGNode>> msgToSgMap = new HashMap<>();
    public final Map<SGNode, MSGNode> sgToMsgMap = new HashMap<>();

    private final CIDGSG<SGNode> sliceGraph;
    private final SliceSolver solver;

    public MergedSliceGraph(CIDGSG<SGNode> sliceGraph, SliceSolver solver) {
        this.sliceGraph = sliceGraph;
        this.solver = solver;

        methodSig2sgNodes = Maps.newMap();
        methodSig2msgNodes = Maps.newMap();
        inEdges = Maps.newMultiMap();
        outEdges = Maps.newMultiMap();
    }

    public void mergeGraph() {
        // 1. merge nodes: all SGNodes with same stmt merge to one MSGNode
        mergeNodes();

        // 2. merge edges: for each edge, msgToSgMap(edge.source()) -> msgToSgMap(edge.target())
        mergeEdges();
    }

    // 3. merge edges
    private void mergeEdges() {
        for (SGNode sgNode : sliceGraph.getNodes()) {
            var edgeSet = sliceGraph.getOutEdgesOf(sgNode);
            for (var sEdge: edgeSet) {
                var source = sgToMsgMap.get(sEdge.source());
                var target = sgToMsgMap.get(sEdge.target());
                if (!source.equals(target)) {
                    var mEdge = new MSGEdge<>((N) source, (N) target);
                    mEdge.mergeEdge(sEdge);
                    this.addEdge(mEdge);
                }
            }
        }
    }

    // 2. merge same stmt nodes to one Merged Slice Node
    private void mergeNodes() {
        clusterSGNode();
        for (Map.Entry<String, List<Set<SGNode>>> entry : methodSig2sgNodes.entrySet()) {
            var allNodes = entry.getValue();
            for (var sgNodes : allNodes) {
                N msgNode = (N) new MSGNode();
                this.addNode(msgNode);
                for (SGNode sgNode : sgNodes) {
                    msgNode.merge(sgNode);
                    msgNode.line = getLine(sgNode);
                    msgToSgMap.computeIfAbsent(msgNode, k -> new HashSet<>()).add(sgNode);
                    sgToMsgMap.put(sgNode, msgNode);
                }
                methodSig2msgNodes.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(msgNode);
            }
        }
    }

    // 1. cluster sgNodes for same stmt
    private void clusterSGNode() {
        // Organize nodes by method signature and line number
        Map<String, Map<Stmt, Set<SGNode>>> nestedMap = new HashMap<>();

        for (SGNode node : sliceGraph.getNodes()) {
            String methodSig = getContainerSig(node);
            Stmt stmt = node.getStmt();

            nestedMap.computeIfAbsent(methodSig, k -> new HashMap<>())
                    .computeIfAbsent(stmt, k -> new HashSet<>())
                    .add(node);
        }

        for (Map.Entry<String, Map<Stmt, Set<SGNode>>> entry : nestedMap.entrySet()) {
            String methodSig = entry.getKey();
            List<Set<SGNode>> listOfSets = new ArrayList<>(entry.getValue().values());
            listOfSets.sort(Comparator.comparingInt(set -> {
                int line = -1;
                for (SGNode n : set) {
                    return getLine(n);
                }
                return line;
            }));
            methodSig2sgNodes.put(methodSig, listOfSets);
        }
    }

    // Utility Functions For SGNode Info
    private int getLine(SGNode sgNode) {
        return solver.getToSliceNodeBySGNode(sgNode).stmt.getIndex();
    }

    private JMethod getContainer(SGNode sgNode) {
        return solver.getToSliceNodeBySGNode(sgNode).context.currentMethod;
    }

    private String getContainerSig(SGNode sgNode) {
        return getContainer(sgNode).getSignature();
    }

    // Graph Utility
    public void addEdge(MSGEdge<N> edge) {
        if (getExistingEdge(edge) == null) {
            inEdges.put(edge.target(), edge);
            outEdges.put(edge.source(), edge);
        }
    }

    @Nullable
    private MSGEdge<N> getExistingEdge(MSGEdge<N> edge) {
        for (MSGEdge<N> outEdge : outEdges.get(edge.source())) {
            if (outEdge.target().equals(edge.target()))
                return outEdge;
        }
        return null;
    }

    @Override
    public Set<MSGEdge<N>> getInEdgesOf(N node) {
        return inEdges.get(node);
    }

    @Override
    public Set<MSGEdge<N>> getOutEdgesOf(N node) {
        return outEdges.get(node);
    }
}
