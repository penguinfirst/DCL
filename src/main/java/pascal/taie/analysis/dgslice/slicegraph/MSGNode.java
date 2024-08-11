package pascal.taie.analysis.dgslice.slicegraph;

import pascal.taie.ir.exp.Literal;
import pascal.taie.ir.exp.Var;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Maps;

import java.util.Map;

public class MSGNode extends AbstractSGNode {

    public Map<Var, Type> varLFMap;

    public int line;

    public MSGNode() {
        varLFMap = Maps.newMap();
    }

    // handle node
    public void merge(SGNode sgNode) {
        copyCommonInfo(sgNode);
        varLFMap.put(sgNode.varLF, sgNode.expectedType);
    }

}
