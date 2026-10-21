package pe.edu.cc531;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class Main extends Configured implements Tool {
    private final String fixedQuery;

    public Main() { this.fixedQuery = null; }
    private Main(String fixedQuery) { this.fixedQuery = fixedQuery; }

    @Override public int run(String[] args) throws Exception {
        int offset = fixedQuery == null ? 1 : 0;
        if (args.length < offset + 2) {
            System.err.println(fixedQuery == null
                ? "Usage: hadoop jar cc531.jar pe.edu.cc531.Main <duration|interruption|dbscan|mlp-class|mlp-reg> <input.csv> <output-dir> [epochs]"
                : "Usage: hadoop jar cc531-" + fixedQuery + ".jar <input.csv> <output-dir> [epochs]");
            return 2;
        }
        Path input=new Path(args[offset]), output=new Path(args[offset+1]);
        String query = fixedQuery == null ? args[0] : fixedQuery;
        int epochs = args.length > offset + 2 ? Integer.parseInt(args[offset+2]) : 40;
        switch(query) {
            case "duration": Queries.run(getConf(),input,output,true); return 0;
            case "interruption": Queries.run(getConf(),input,output,false); return 0;
            case "dbscan": Dbscan.run(getConf(),input,output); return 0;
            case "mlp-class": Mlp.run(getConf(),input,output,false,epochs); return 0;
            case "mlp-reg": Mlp.run(getConf(),input,output,true,epochs); return 0;
            default: throw new IllegalArgumentException("Unknown command: "+query);
        }
    }
    public static void main(String[] args) throws Exception { System.exit(ToolRunner.run(new Main(),args)); }

    private static void launch(String query, String[] args) throws Exception {
        System.exit(ToolRunner.run(new Main(query), args));
    }
    public static final class Duration {
        public static void main(String[] args) throws Exception { launch("duration", args); }
    }
    public static final class Interruption {
        public static void main(String[] args) throws Exception { launch("interruption", args); }
    }
    public static final class DbscanJar {
        public static void main(String[] args) throws Exception { launch("dbscan", args); }
    }
    public static final class MlpClass {
        public static void main(String[] args) throws Exception { launch("mlp-class", args); }
    }
    public static final class MlpReg {
        public static void main(String[] args) throws Exception { launch("mlp-reg", args); }
    }
}
