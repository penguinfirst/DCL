package pascal.taie.analysis.dgslice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.dgslice.solver.TraceContext;
import pascal.taie.config.AnalysisOptions;
import pascal.taie.language.type.Type;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GlobalOptions {
    private static final Logger logger = LogManager.getLogger(GlobalOptions.class);

    // Input - Slice Target
    public static String className;
    public static String methodSig;
    public static int line;
    public static String calleeSig;
    public static Type expectedType;

    // Input - Slice Target Trace
    public static String targetTraceFileDir;
    public static TraceContext context;

    // Input - Dump & Depth
    public static boolean dump = false;
    public static int depth = -1;

    public static void processOptions(AnalysisOptions options) {
        // Input - Target
        className = options.getString("class");
        methodSig = options.getString("method").replace('#', ':');
        line = options.getInt("line");
        calleeSig = options.getString("callee").replace('#', ':');
        String type = options.getString("type");
        expectedType = World.get().getTypeSystem().getType(type);
        logger.info("class : {}\nmethod : {}\nline : {}\ntype : {}", className, methodSig, line, expectedType);

        // Input - Target Trace
        targetTraceFileDir = options.getString("traceDir");
        File targetTraceFile = new File(targetTraceFileDir, "targetTrace.txt");
        String targetTrace = readFileOneLine(targetTraceFile);
        assert targetTrace != null;
        context = TraceContext.getTraceContext(targetTrace);
        assert context.validate();

        // Input - All Trace
        File allTraceFile = new File(targetTraceFileDir, "allTrace.txt");
        List<String> traces = readFileLines(allTraceFile);
        assert !traces.isEmpty();
        TraceUtil.storeRawTraces(traces);

        // TO DO : More readable dump
        dump = options.getBoolean("dump");
        depth = options.getInt("depth");
    }

    public static String readFileOneLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<String> readFileLines(File file) {
        List<String> content = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null && !line.equals("\n")) {
                content.add(line);
            }
            return content;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
