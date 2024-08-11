package pascal.taie.analysis.dgslice.slicegraph;

import pascal.taie.ir.exp.ArrayAccess;
import pascal.taie.ir.exp.FieldAccess;
import pascal.taie.ir.exp.Literal;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.type.Type;

public class SGNode extends AbstractSGNode{
    public Var varLF;
    public Type expectedType;

    public SGNode(Stmt stmt, Var varLF, Type expectedType) {
        this.stmt = stmt;
        this.varLF = varLF;
        this.expectedType = expectedType;
    }

    public SGNode(Stmt stmt, Var varLF, Type expectedType, boolean isFieldAccess, FieldAccess fieldLoadLF) {
        this.stmt = stmt;
        this.varLF = varLF;
        this.expectedType = expectedType;
        this.isFieldAccess = isFieldAccess;
        this.fieldLoadLF = fieldLoadLF;
    }

    public SGNode(Stmt stmt, Var varLF, Type expectedType, boolean isArrayAccess, ArrayAccess arrayAccessLF) {
        this.stmt = stmt;
        this.varLF = varLF;
        this.expectedType = expectedType;
        this.isArrayAccess = isArrayAccess;
        this.arrayAccessLF = arrayAccessLF;
    }
}
