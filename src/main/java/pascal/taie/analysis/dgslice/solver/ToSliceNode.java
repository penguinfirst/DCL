package pascal.taie.analysis.dgslice.solver;

import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.type.Type;

public class ToSliceNode {
    public Stmt stmt;
    public Type expectedType;

    // Expected Const Literal for java.lang.Class
    public String expectedClass = "";

    public TraceContext context;

    // Index for slice invoke stmt
    public int index = -1;

    public ToSliceNode(Stmt stmt, Type expectedType, TraceContext context) {
        this.stmt = stmt;
        this.expectedType = expectedType;
        this.context = context;
    }

    public ToSliceNode(Stmt stmt, Type expectedType, TraceContext context, int index) {
        this.stmt = stmt;
        this.expectedType = expectedType;
        this.context = context;
        this.index = index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ToSliceNode that = (ToSliceNode) o;

        boolean basicEqual = this.stmt.equals(that.stmt)
                                && this.expectedType.equals(that.expectedType)
                                && this.context.equals(that.context)
                                && this.index == that.index;

        if (this.expectedType.getName().equals("java.lang.Class")) {
            return basicEqual && this.expectedClass.equals(that.expectedClass);
        }

        return basicEqual;
    }
}
