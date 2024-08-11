package pascal.taie.analysis.dgslice.solver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.DefinitionStmt;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.StoreArray;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ArrayType;
import pascal.taie.language.type.NullType;
import pascal.taie.language.type.Type;

import java.util.List;

public class SliceUtil {
    private static final Logger logger = LogManager.getLogger(SliceUtil.class);

    private static final ClassHierarchy hierarchy = World.get().getClassHierarchy();

    // Is-JDK Utility Functions
    // Is-JDK-Method
    public static boolean isJDKMethod(String sig) {
        return sig.startsWith("<java.")
                || sig.startsWith("<sun.")
                || sig.startsWith("<com.java.")
                || sig.startsWith("<com.sun.")
                || sig.startsWith("<jdk.");
    }

    public static boolean isJDKReflectMethod(String sig) {
        return sig.startsWith("<java.lang.reflect.")
                || sig.startsWith("<jdk.internal.reflect.");
    }

    // Is-JDK-Type
    public static boolean isJDKType(Type type) {
        String sig = type.getName();
        return sig.startsWith("java.")
                || sig.startsWith("sun.")
                || sig.startsWith("com.java.")
                || sig.startsWith("com.sun.")
                || sig.startsWith("jdk.");
    }

    public static Stmt locateInvokeStmt(String className, String methodSig, String calleeSig, int line) {
        JClass clz = hierarchy.getClass(className);
        assert clz != null;

        JMethod method = hierarchy.getMethod(methodSig);
        assert method != null;

        List<Stmt> targetList = method.getIR().stmts().filter(stmt -> {
            if (stmt.getLineNumber() == line && stmt instanceof Invoke invoke){
                return !(invoke.getInvokeExp() instanceof InvokeDynamic)
                        && mayOverride(invoke.getInvokeExp().getMethodRef().resolveNullable(), calleeSig);
            }
            return false;
        }).toList();

        if (targetList.size() != 1) {
            logger.info("Strange: {} to {} can not locate target in line {}",
                    methodSig, calleeSig, line);
            return null;
        }

        return targetList.get(0);
    }

    public static Stmt locateInstanceInvokeStmt(String className, String methodSig, String calleeSig, int line) {
        JClass clz = hierarchy.getClass(className);
        assert clz != null;

        JMethod method = hierarchy.getMethod(methodSig);
        assert method != null;

        List<Stmt> targetList = method.getIR().stmts().filter(stmt -> {
            if (stmt.getLineNumber() == line && stmt instanceof Invoke invoke){
                return (invoke.getInvokeExp() instanceof InvokeInstanceExp) &&
                        mayOverride(invoke.getInvokeExp().getMethodRef().resolveNullable(), calleeSig);
            }
            return false;
        }).toList();

        if (targetList.size() != 1) {
            logger.info("Strange: {} to {} can not locate target in line {}",
                    methodSig, calleeSig, line);
            return null;
        }

        return targetList.get(0);
    }

    public static StoreArray findReflectionArgsDefAtIndex(Invoke invoke, int ith) {
        List<Stmt> stmts = invoke.getContainer().getIR().getStmts();
        Var args = invoke.getInvokeExp().getArg(1);
        Type[] argTypes = null;
        if (args.isConst()) {
            // if args is constant, it must be null, and for such case,
            // no argument is given.
            return null;
        } else {
            assert args.getType() instanceof ArrayType;
            DefinitionStmt<?, ?> argDef = null;
            for (int i = invoke.getIndex() - 1; i >= 0; --i) {
                Stmt stmt = stmts.get(i);
                if (stmt instanceof DefinitionStmt<?, ?> defStmt) {
                    LValue lValue = defStmt.getLValue();
                    if (args.equals(lValue)) { // found definition of args
                        if (argDef == null) {
                            argDef = defStmt;
                            int length = getArrayLength(defStmt.getRValue());
                            if (length != -1) { // found args = new Object[length];
                                argTypes = new Type[length];
                            } else { // args is defined by other ways, give up
                                break;
                            }
                        } else { // found multiple definitions of args, give up
                            argTypes = null;
                            break;
                        }
                    }
                }
            }
            if (argTypes != null) {
                // creation of args is analyzable, collect argument types
                for (int i = argDef.getIndex(); i < stmts.size(); ++i) {
                    Stmt stmt = stmts.get(i);
                    if (stmt instanceof StoreArray storeArray) {
                        ArrayAccess arrayAccess = storeArray.getArrayAccess();
                        if (arrayAccess.getBase().equals(args)) {
                            // args[*] = ...;
                            Var index = arrayAccess.getIndex();
                            if (index.isConst()) { // index is constant
                                int iIndex = ((IntLiteral) index.getConstValue()).getValue();
                                if (argTypes[iIndex] == null) {
                                    argTypes[iIndex] = storeArray.getRValue().getType();
                                    if (iIndex == ith) {
                                        return storeArray;
                                    }
                                } else { // found multiple definitions
                                    // on the same array index, give up
                                    argTypes = null;
                                    break;
                                }
                            } else { // index is not constant, give up
                                argTypes = null;
                                break;
                            }
                        }
                    }
                }
                if (argTypes != null) {
                    // fill NullType for non-assigned array indexes
                    for (int i = 0; i < argTypes.length; ++i) {
                        if (argTypes[i] == null) {
                            argTypes[i] = NullType.NULL;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static int getArrayLength(RValue rValue) {
        if (rValue instanceof NewArray newArray) {
            Var length = newArray.getLength();
            if (length.isConst()
                    && length.getConstValue() instanceof IntLiteral intLiteral) {
                // args = new Object[const];
                return intLiteral.getValue();
            }
        }
        if (rValue instanceof NullLiteral) {
            // args = null;
            return 0;
        }
        if (rValue instanceof CastExp castExp && castExp.getValue().isConst()) {
            // args = (Object[]) null;
            return 0;
        }
        return -1;
    }

    // Method Override Utility
    public static boolean mayOverride(JMethod superMethod, JMethod subMethod) {
        return superMethod != null
                && hierarchy.isSubclass(superMethod.getDeclaringClass(), subMethod.getDeclaringClass())
                && superMethod.getSubsignature().equals(subMethod.getSubsignature());
    }

    public static boolean mayOverride(JMethod superMethod, String subMethodSig) {
        JMethod subMethod = hierarchy.getMethod(subMethodSig);
        return subMethod != null
                && mayOverride(superMethod, subMethod);
    }
}
