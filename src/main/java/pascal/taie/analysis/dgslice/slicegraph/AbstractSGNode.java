package pascal.taie.analysis.dgslice.slicegraph;

import pascal.taie.ir.exp.ArrayAccess;
import pascal.taie.ir.exp.FieldAccess;
import pascal.taie.ir.exp.Literal;
import pascal.taie.ir.stmt.Stmt;

public abstract class AbstractSGNode {
    public Stmt stmt;

    // Field Load -> y = p.f
    public boolean isFieldAccess = false;
    public FieldAccess fieldLoadLF = null;

    // Array Load -> y = a[i]
    public boolean isArrayAccess = false;
    public ArrayAccess arrayAccessLF = null;

    // Slice Ending Flags
    // 1.hasLiteral
    // reach x = Literal
    public boolean hasLiteral = false;
    public Literal literal = null;

    // 2.hasExpectedType
    // obj allocation is found successfully and can be traced through PFG
    public boolean hasExpectedType = false;

    // 3.not in dynamic
    public boolean hasNotFoundInDynamic = false;

    // 4.invoke can not be located
    // maybe invalid dynamic trace or other unknown error
    public boolean hasNotLocatedInvoke = false;

    // 5.field can not be resolved
    public boolean hasNotResolvedField = false;

    // 6.reach depth limit
    public boolean hasReachDepthLimit = false;

    public Stmt getStmt() {
        return stmt;
    }

    public void setStmt(Stmt stmt) {
        this.stmt = stmt;
    }

    public void copyCommonInfo(AbstractSGNode node) {
        if (node == null) {
            throw new IllegalArgumentException("The input node cannot be null");
        }

        this.stmt = node.stmt;

        this.isFieldAccess = node.isFieldAccess;
        this.fieldLoadLF = node.fieldLoadLF;

        this.isArrayAccess = node.isArrayAccess;
        this.arrayAccessLF = node.arrayAccessLF;

        this.hasLiteral = node.hasLiteral;
        this.literal = node.literal;

        this.hasExpectedType = node.hasExpectedType;
        this.hasNotFoundInDynamic = node.hasNotFoundInDynamic;
        this.hasNotLocatedInvoke = node.hasNotLocatedInvoke;
        this.hasNotResolvedField = node.hasNotResolvedField;
        this.hasReachDepthLimit = node.hasReachDepthLimit;
    }
}
