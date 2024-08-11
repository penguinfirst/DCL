package pascal.taie.analysis.dgslice.slicegraph;

import pascal.taie.ir.stmt.*;
import pascal.taie.util.Hashes;
import pascal.taie.util.graph.AbstractEdge;

import static pascal.taie.analysis.dgslice.slicegraph.DGSGEdge.Kind.DEFINE;

public class MSGEdge<N extends MSGNode> extends AbstractEdge<N> {
    public enum Kind {
        TOP,
        DEFINE,
        ARGUMENT_PASSING,
        REFLECTION_THIS_PASSING,
        REFLECTION_ARGUMENT_PASSING,
        FIELD_BASE,
        FIELD_DEFINE,
        ARRAY_BASE,
        ARRAY_DEFINE,
        RETURN,
    }

    private Kind kind;

    public MSGEdge(N source, N target) {
        super(source, target);
    }
    public MSGEdge(Kind kind, N source, N target) {
        super(source, target);
        this.kind = kind;
    }

    // merge edges
    // TO DO : source and target node property
    // e.g. which var should the edge start with
    public void mergeEdge(DGSGEdge<SGNode> sEdge) {
        kind = switch (sEdge.getKind()) {
            case RETURN -> Kind.RETURN;
            case ARGUMENT_PASSING -> Kind.ARGUMENT_PASSING;
            case REFLECTION_THIS_PASSING -> Kind.REFLECTION_THIS_PASSING;
            case REFLECTION_ARGUMENT_PASSING -> Kind.REFLECTION_ARGUMENT_PASSING;
            case CAST, CAST_OUT_JDK, FIELD_BASE, FIELD_DEFINE, ARRAY_BASE, ARRAY_DEFINE -> Kind.DEFINE;
            default -> Kind.TOP;
        };

        Stmt sourceStmt = source.getStmt();
        if (sourceStmt instanceof StoreArray)
            kind = Kind.ARRAY_DEFINE;
        else if (sourceStmt instanceof StoreField)
            kind = Kind.FIELD_DEFINE;
        else if (sEdge.getKind() == DEFINE) {
            SGNode sgTarget = sEdge.target();

            if (sgTarget.isArrayAccess)
                kind = Kind.ARRAY_BASE;
            else if (sgTarget.isFieldAccess)
                kind = Kind.FIELD_BASE;
            else
                kind = Kind.DEFINE;
        }
    }


    // Utility Functions
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
        MSGEdge<?> edge = (MSGEdge<?>) o;
        return kind == edge.kind &&
                source.getStmt().equals(edge.source.getStmt()) &&
                target.getStmt().equals(edge.target.getStmt());
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
