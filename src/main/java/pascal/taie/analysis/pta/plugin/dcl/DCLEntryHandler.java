package pascal.taie.analysis.pta.plugin.dcl;

import pascal.taie.analysis.pta.core.solver.DeclaredParamProvider;
import pascal.taie.analysis.pta.core.solver.EntryPoint;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.plugin.util.AnalysisModelPlugin;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;

import java.io.FileWriter;
import java.io.OutputStream;
import java.util.stream.Collectors;

public class DCLEntryHandler extends AnalysisModelPlugin {
    protected DCLEntryHandler(Solver solver) {
        super(solver);
    }

    @Override
    public void onStart() {
        var entryList = DCLExperimentStats.getEntries();
        for (String methodSig : entryList) {
            JMethod entry = hierarchy.getMethod(methodSig);
            if (entry == null) {
                System.err.println("Cannot Find Entry Method : " + methodSig);
                continue;
            }
            if (methodSig.contains("void main(java.lang.String[])")) {
                solver.addEntryPoint(new EntryPoint(entry, new DeclaredParamProvider(entry, solver.getHeapModel(), 1)));
            } else
                solver.addEntryPoint(new EntryPoint(entry, new DeclaredParamProvider(entry, solver.getHeapModel())));
        }
    }

    @Override
    public void onFinish() {
        String outPath = DCLExperimentStats.cpPrefix + "result/ClosedWorld.txt";
        try {
            FileWriter fw = new FileWriter(outPath);
            for (var jClass : hierarchy.allClasses().toList()) {
                /*
                String name = jClass.getName();
                if (!(name.startsWith("java.")
                        || name.startsWith("sun.")
                        || name.startsWith("jdk.")
                        || name.startsWith("com.sun.")
                        || name.startsWith("javax.")
                        || name.startsWith("soot.dummy.InvokeDynamic"))) {
                    fw.write(name + "\n");
                }*/
                if (jClass.isApplication()){
                    fw.write(jClass.getName() + "\n");
                }
            }
            fw.close();
        } catch (Exception e) {
            System.err.println("Error: Failed to print Closed World.");
            e.printStackTrace();
        }

        String outPathRM = DCLExperimentStats.cpPrefix + "result/rm-static.txt";
        try {
            FileWriter fw = new FileWriter(outPathRM);
            for (var method : solver.getCallGraph().reachableMethods().toList()) {
                if (method.getMethod().isApplication()){
                    fw.write(method.getMethod().getSignature()+ "\n");
                }
            }
            fw.close();
        } catch (Exception e) {
            System.err.println("Error: Failed to print reachable methods.");
            e.printStackTrace();
        }

//        JClass clz = hierarchy.getClass("org.osgi.framework.BundleActivator");
//        System.err.println("**********BundleActivator**********");
//        hierarchy.getAllSubclassesOf(clz).forEach(jClass -> {
//            if (!jClass.isInterface()
//                    && !jClass.isAbstract())
//                System.err.println(jClass.getName());
//        });
//        System.err.println("**********BundleActivatorEND**********");ConfigurationProcessorFactory
    }
}
