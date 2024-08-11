package pascal.taie.analysis.dgslice;

public class DGSTest {
    public static void main(String[] args) {
        try {
            String cp = "/home/zcx/IdeaProjects/SliceTest/out/production/SliceTest";

            String dgsliceArg = String.join(";",
                    "class:my.RelatedCase",
                    "method:<my.RelatedCase# void foo()>",
                    "line:11",
                    "type:my.FirstService",
                    "callee:<my.MyService# void performService()>",
                    "traceDir:/home/zcx/playground/dgslice",
                    "dump:true",
                    "depth:-1"
            );

            String[] arguments = {
                    "-cp", cp,
                    "-m", "my.RelatedCase",
                    "-pp",
                    "-ap",
                    "-a", "pta=cs:ci;only-app:true",
                    "-a", "ir-dumper",
                    "-a", "dgslice=" + dgsliceArg

            };
            pascal.taie.Main.main(arguments);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
