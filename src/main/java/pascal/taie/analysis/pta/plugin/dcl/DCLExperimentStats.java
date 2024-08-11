package pascal.taie.analysis.pta.plugin.dcl;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class DCLExperimentStats {
    // origin tai-e
    public static boolean enableDCLAnalysis = true;
    public static boolean enableDCLModel = true;

    // [Input] project information
    public static int projectID;
    public static boolean isLinux;
    public static boolean isApp;
    public static String benchPath;

    // [Inferred] project information
    public static String projectName;
    public static String SEP;

    // [Inferred] class path / main method / entries
    public static String cpPrefix;
    public static String classpath;
    public static String mainClassName;
    public static List<String> inputEntries;
    public static String inputClasses;



    /* -------------- Project Info -------------- */
    public static void init(int projectID, boolean isLinux, boolean isApp, String benchPath) {
        DCLExperimentStats.projectID = projectID;
        DCLExperimentStats.isLinux = isLinux;
        DCLExperimentStats.isApp = isApp;
        DCLExperimentStats.benchPath = benchPath;

        DCLExperimentStats.SEP = (isLinux) ? ":" : ";";
        switch(projectID) {
            case 1 -> projectName = "jenkins";
            case 2 -> projectName = "eclipse";
            default -> throw new RuntimeException("Error: Project " + projectID + " not support now.");
        }
        DCLExperimentStats.cpPrefix = benchPath + projectName + "/";
        DCLExperimentStats.classpath = readClassPath(cpPrefix + "info/classpath.txt");
        DCLExperimentStats.mainClassName = readMainClass(cpPrefix + "info/main.txt");
        DCLExperimentStats.inputEntries = readEntries(cpPrefix + "info/entries.txt");
        DCLExperimentStats.inputClasses = readInputClasses(cpPrefix + "info/input-classes.txt");
    }

    /* --------------- Class Path --------------- */
    public static String readClassPath(String path) {
        String cp = cpPrefix + "classpath:";
        try {
            FileInputStream f = new FileInputStream(path);
            Scanner scanner = new Scanner(f);
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if (line.contains("spring-web-5.3.33.jar")
                        || line.contains("spring-security-web-5.8.11.jar"))
                    continue;
                cp += cpPrefix + "classpath/" + line + SEP;
            }
            if (!cp.isEmpty()) {
                cp = cp.substring(0, cp.length() - 1);
            }
        } catch (Exception e) {
            System.err.println("Error: Failed to get project + " + projectName + " class path.");
            e.printStackTrace();
        }
        return cp;
    }

    public static String getClasspath() { return classpath; }

    /* --------------- Main Class --------------- */
    public static String readMainClass(String path) {
        String main = "";
        try {
            FileInputStream f = new FileInputStream(path);
            Scanner scanner = new Scanner(f);
            while (scanner.hasNext()) {
                main += scanner.nextLine() + SEP;
            }
            if (!main.isEmpty()) {
                main = main.substring(0, main.length() - 1);
            }
        } catch (Exception e) {
            System.err.println("Error: Failed to get project + " + projectName + " main class.");
            e.printStackTrace();
        }
        return main;
    }

    public static String getMainClassName() { return mainClassName; }

    /* -------------- Project Entry ------------- */
    private static List<String> readEntries(String path) {
        List<String> entries = new ArrayList<>();
        try {
            FileInputStream f = new FileInputStream(path);
            Scanner scanner = new Scanner(f);
            while (scanner.hasNext()) {
                entries.add(scanner.nextLine());
            }
        } catch (Exception e) {
            System.err.println("Error: Failed to get project + " + projectName + " entries.");
            e.printStackTrace();
        }
        return entries;
    }

    public static List<String> getEntries() { return inputEntries; }

    /* -------------- Input Classes ------------- */
    private static String readInputClasses(String path) {
        String inputClasses = "";
        try {
            FileInputStream f = new FileInputStream(path);
            Scanner scanner = new Scanner(f);
            while (scanner.hasNext()) {
                inputClasses += scanner.nextLine() + ",";
            }
            if (!inputClasses.isEmpty()) {
                inputClasses = inputClasses.substring(0, inputClasses.length() - 1);
            }
        } catch (Exception e) {
            System.err.println("Error: Failed to get project + " + projectName + " input classes.");
            e.printStackTrace();
        }
        return inputClasses;
    }

    public static String getInputClasses() { return inputClasses; }
    /* ------------ Utility Function ------------ */

}
