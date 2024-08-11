package pascal.taie.analysis.dgslice.slicegraph;

import pascal.taie.util.Hashes;
import pascal.taie.util.graph.AbstractEdge;

public class DGSGEdge<N> extends AbstractEdge<N> {

    public enum Kind {
        DEFINE,
        THIS_PASSING,
        CAST,
        CAST_OUT_JDK,
        ARGUMENT_PASSING,
        REFLECTION_THIS_PASSING,
        REFLECTION_ARGUMENT_PASSING,
        FIELD_BASE,
        FIELD_DEFINE,
        ARRAY_BASE,
        ARRAY_DEFINE,
        RETURN,
    }

    private final Kind kind;

    public DGSGEdge(Kind kind, N source, N target) {
        super(source, target);
        this.kind = kind;
    }

    public Kind getKind() { return kind; }

    @Override
    public N source() {
        return source;
    }

    @Override
    public N target() {
        return target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DGSGEdge<?> edge = (DGSGEdge<?>) o;
        return kind == edge.kind &&
                source.equals(edge.source) &&
                target.equals(edge.target);
    }

    @Override
    public int hashCode() {
        return Hashes.hash(kind, source, target);
    }

    @Override
    public String toString() {
        return "[" + kind + "]: " + source + " -> " + target;
    }
}
