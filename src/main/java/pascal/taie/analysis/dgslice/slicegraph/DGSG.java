package pascal.taie.analysis.dgslice.slicegraph;

import pascal.taie.util.graph.AbstractEdge;
import pascal.taie.util.graph.Graph;

import java.util.Set;

public interface DGSG<N extends AbstractSGNode> extends Graph<N>  {
    /**
     * @return the entry node set of this DGSG.
     */
    Set<N> getEntry();

    /**
     * @return the target node of this DGSG.
     */
    N getTarget();

    /**
     * @return true if the given node is in the entry set of this DGSG, otherwise false.
     */
    boolean isEntry(N node);

    /**
     * @return true if the given node is the target of this DGSG, otherwise false.
     */
    boolean isTarget(N node);
}
