package pe.edu.cc531;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.NLineInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

public final class Jobs {
    private Jobs() {}
    public static Job raw(Configuration conf, String name, Path input, Path output, int reducers) throws Exception {
        Job job = Job.getInstance(conf, name);
        job.setJarByClass(Main.class);
        job.setInputFormatClass(NLineInputFormat.class);
        NLineInputFormat.setNumLinesPerSplit(job, conf.getInt("cc531.lines.per.map", 100));
        job.setOutputFormatClass(TextOutputFormat.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(reducers);
        FileInputFormat.addInputPath(job, input);
        FileOutputFormat.setOutputPath(job, output);
        return job;
    }
    public static Job text(Configuration conf, String name, Path input, Path output, int reducers) throws Exception {
        Job job = Job.getInstance(conf, name);
        job.setJarByClass(Main.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(reducers);
        FileInputFormat.addInputPath(job, input);
        FileOutputFormat.setOutputPath(job, output);
        return job;
    }
    public static void run(Job job) throws Exception {
        if (!job.waitForCompletion(true)) throw new IllegalStateException("Failed: " + job.getJobName());
    }
    public static void absent(Configuration conf, Path path) throws Exception {
        if (path.getFileSystem(conf).exists(path)) throw new IllegalArgumentException("Output exists: " + path);
    }
    public static interface LineConsumer { void accept(String line) throws Exception; }
    public static void eachLine(Configuration conf, Path dir, LineConsumer consumer) throws Exception {
        FileSystem fs = dir.getFileSystem(conf);
        for (FileStatus status : fs.listStatus(dir)) {
            if (!status.getPath().getName().startsWith("part-")) continue;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(fs.open(status.getPath()), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) consumer.accept(line);
            }
        }
    }
}
