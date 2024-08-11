package pascal.taie.analysis.dgslice.slicegraph;

import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Sets;
import pascal.taie.util.collection.Views;
import pascal.taie.util.graph.AbstractEdge;
import pascal.taie.util.graph.Edge;

import javax.annotation.Nullable;
import java.util.*;

public class AbstractDGSG<N extends AbstractSGNode> implements DGSG<N> {

    protected Set<N> entry;

    protected N target;

    protected final Set<N> nodes;

    public AbstractDGSG() {
        entry = Sets.newSet();
        nodes = Sets.newSet();
    }

    public boolean addEntry(N entry) {
        boolean succ = this.entry.add(entry);
        nodes.add(entry);
        return succ;
    }

    public void setTarget(N target) {
        assert this.target == null : "DGSG target should be set only once";
        this.target = target;
        nodes.add(target);
    }

    @Override
    public Set<N> getEntry() {
        return entry;
    }

    @Override
    public N getTarget() {
        return target;
    }

    @Override
    public boolean isEntry(N node) {
        return entry.contains(node);
    }

    @Override
    public boolean isTarget(N node) {
        return node == target;
    }

    public void addNode(N node) {
        if (target == null)
            target = node;
        nodes.add(node);
    }

    @Override
    public Set<N> getPredsOf(N node) {
        return Views.toMappedSet(getInEdgesOf(node), Edge::source);
    }

    @Override
    public Set<N> getSuccsOf(N node) {
        return Views.toMappedSet(getOutEdgesOf(node), Edge::target);
    }

    @Override
    public Set<N> getNodes() {
        return nodes;
    }

    // Utility Functions
    public int shortestPathLength(N source, N target) {
        if (source.equals(target)) {
            return 0;  // If the source and target are the same, the path length is 0.
        }

        Queue<N> queue = new LinkedList<>();
        Map<N, Integer> distance = new HashMap<>();
        Set<N> visited = new HashSet<>();

        queue.offer(source);
        visited.add(source);
        distance.put(source, 0);

        while (!queue.isEmpty()) {
            N current = queue.poll();
            int currentDistance = distance.get(current);

            // Get all out edges from the current node
            Set<Edge<N>> outEdges = (Set<Edge<N>>) getOutEdgesOf(current);
            for (Edge<N> edge : outEdges) {
                N neighbor = edge.target();

                if (!visited.contains(neighbor)) {
                    if (neighbor.equals(target)) {
                        return currentDistance + 1;  // Found the target, return the path length
                    }
                    visited.add(neighbor);
                    queue.offer(neighbor);
                    distance.put(neighbor, currentDistance + 1);
                }
            }
        }

        return -1;  // Target is unreachable
    }
}
