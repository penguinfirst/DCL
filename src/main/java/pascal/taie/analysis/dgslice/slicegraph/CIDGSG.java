package pascal.taie.analysis.dgslice.slicegraph;

import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.graph.AbstractEdge;

import javax.annotation.Nullable;
import java.util.*;

public class CIDGSG<N extends SGNode> extends AbstractDGSG<N> {

    private final MultiMap<N, DGSGEdge<N>> inEdges;

    private final MultiMap<N, DGSGEdge<N>> outEdges;

    public CIDGSG() {
        inEdges = Maps.newMultiMap();
        outEdges = Maps.newMultiMap();
    }

    @Override
    public Set<N> getNodes() {
        return Collections.unmodifiableSet(super.getNodes());
    }

    public void addEdge(DGSGEdge<N> edge) {
        if (getExistingEdge(edge) == null) {
            inEdges.put(edge.target(), edge);
            outEdges.put(edge.source(), edge);
        }
    }

    @Nullable
    private DGSGEdge<N> getExistingEdge(DGSGEdge<N> edge) {
        for (DGSGEdge<N> outEdge : outEdges.get(edge.source())) {
            if (outEdge.target().equals(edge.target()))
                return outEdge;
        }
        return null;
    }

    @Override
    public Set<DGSGEdge<N>> getInEdgesOf(N node) {
        return inEdges.get(node);
    }

    @Override
    public Set<DGSGEdge<N>> getOutEdgesOf(N node) {
        return outEdges.get(node);
    }

}
