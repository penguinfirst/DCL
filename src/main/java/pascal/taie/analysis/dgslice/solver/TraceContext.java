package pascal.taie.analysis.dgslice.solver;

import pascal.taie.World;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JMethod;

import java.util.ArrayList;
import java.util.List;

public class TraceContext {
    public static final ClassHierarchy hierarchy = World.get().getClassHierarchy();

    // Current Method & Signature
    public String currentMethodSig;
    public JMethod currentMethod;

    // Stack Info
    public List<StackElement> stack;
    public int currentDepth;


    public int stackHash() {
        int hash = 1;
        for (StackElement se : stack) {
            hash = 31 * hash + se.methodSig.hashCode();
        }
        return hash;
    }

    public boolean validate() {
        for (var se : stack) {
            if (se.method == null)
                return false;
        }
        return true;
    }

    public static TraceContext getTraceContext(String trace) {
        TraceContext context = new TraceContext();

        String[] methodAndStack = trace.split("\\|");

        context.stack = new ArrayList<>();
        String stackPart = methodAndStack[1];
        String[] stackElementStrs = stackPart.split("->");
        for (int i = 0; i < stackElementStrs.length - 1; i++) {
            String[] methodAndLine = stackElementStrs[i].split(">\\[");
            StackElement stackElement = new StackElement();
            stackElement.methodSig = methodAndLine[0] + ">";
            stackElement.method = hierarchy.getMethod(stackElement.methodSig);
            stackElement.line = Integer.parseInt(methodAndLine[1].split(":")[1].split("]")[0]);
            context.stack.add(i, stackElement);
        }
        context.currentDepth = stackElementStrs.length - 2;
        context.currentMethodSig = context.stack.get(context.currentDepth).methodSig;
        context.currentMethod = context.stack.get(context.currentDepth).method;

        return context;
    }

    public static TraceContext getCurrentTraceContext(String trace) {
        TraceContext context = new TraceContext();

        String[] methodAndStack = trace.split("\\|");

        context.stack = new ArrayList<>();
        String stackPart = methodAndStack[1];
        String[] stackElementStrs = stackPart.split("->");
        for (int i = 0; i < stackElementStrs.length; i++) {
            String[] methodAndLine = stackElementStrs[i].split(">\\[");
            StackElement stackElement = new StackElement();
            stackElement.methodSig = methodAndLine[0] + ">";
            stackElement.method = hierarchy.getMethod(stackElement.methodSig);
            stackElement.line = Integer.parseInt(methodAndLine[1].split(":")[1].split("]")[0]);
            context.stack.add(i, stackElement);
        }
        context.currentDepth = stackElementStrs.length - 1;
        context.currentMethodSig = context.stack.get(context.currentDepth).methodSig;
        context.currentMethod = context.stack.get(context.currentDepth).method;

        return context;
    }

    public static TraceContext getTraceContextAtIndex(TraceContext context, int index) {
        TraceContext atIndexContext = new TraceContext();
        atIndexContext.stack = new ArrayList<>();
        for (int i = 0; i < index + 1; i++) {
            atIndexContext.stack.add(context.stack.get(i));
        }
        atIndexContext.currentDepth = index;
        atIndexContext.currentMethod = atIndexContext.stack.get(atIndexContext.currentDepth).method;
        atIndexContext.currentMethodSig = atIndexContext.currentMethod.getSignature();
        return atIndexContext;
    }

    public static TraceContext getPreStackContext(TraceContext context) {
        return getTraceContextAtIndex(context, context.currentDepth - 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TraceContext that = (TraceContext) o;
        // TO DO : Context Equal
        if (that.stackHash() == stackHash())
            return true;
        return false;
    }
}
