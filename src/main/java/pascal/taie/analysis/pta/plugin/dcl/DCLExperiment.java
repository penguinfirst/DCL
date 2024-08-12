package pascal.taie.analysis.pta.plugin.dcl;

import java.util.ArrayList;
import java.util.List;

public class DCLExperiment {

    public static void main(String[] args) {
        DCLExperimentStats.init(1, true, false, "/home/zcx/DCL-Benchmark/");

        startTaie(args);
    }

    public static void startTaie(String[] args) {
        try {
            String cp = DCLExperimentStats.getClasspath();
            String mainClassName = DCLExperimentStats.getMainClassName();
            String inputClasses = DCLExperimentStats.getInputClasses();
            String resultDir = DCLExperimentStats.cpPrefix + "result/";

            if (inputClasses.equals(""))
                inputClasses = mainClassName;

            //System.out.println(DCLExperimentStats.projectName + " classpath -> " + cp + "\n");
            System.out.println(DCLExperimentStats.projectName + " mainClassName -> " + mainClassName + "\n");

            String dgsliceArg = String.join(";",
                    "class:org.eclipse.jetty.server.handler.ContextHandler$StaticContext",
                    "method:<org.eclipse.jetty.server.handler.ContextHandler$StaticContext# java.lang.Object createInstance(java.lang.Class)>",
                    "line:2900",
                    "type:java.lang.Class",
                    "callee:<java.lang.Class# java.lang.reflect.Constructor getDeclaredConstructor(java.lang.Class[])>",
                    "traceDir:" + resultDir,
                    "dump:true",
                    "depth:9"
            );

            String[] arguments = {
                    //class paths of analysis
                    "-cp", cp,

                    //class contain main method
                    "-m", mainClassName,

                    "--input-classes", inputClasses,

                    //"-java", "8",
                    "-pp",

                    //skip reference classes that are not found in class paths
                    "-ap",

                    //"-ire",

                    //analysis approach
                    "-a", "pta=cs:ci;only-app:false;reflection-inference:solar;handle-invokedynamic:true;",
                    //time-limit:2400
                    //reflection-inference:solar
                    //reflection-inference:string-constant
                    //handle-invokedynamic:true

                    "-a", "cg=dump-methods:true;dump-call-edges:true",
                    //"-a", "icfg=dump:true",
                    //"-a", "ir-dumper",
                    //"-a", "pta=cs:ci;only-app:false;dump-ci:false;dump-yaml:false;reflection-log:" + refLog,
                    //"-a", "cg=dump-methods:false;dump-call-edges:false",

                    "-a", "dgslice=" + dgsliceArg,

                    //"-scope=REACHABLE"

            };
            pascal.taie.Main.main(arguments);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
