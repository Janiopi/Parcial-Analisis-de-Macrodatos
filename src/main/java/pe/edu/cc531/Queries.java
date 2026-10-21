package pe.edu.cc531;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

public final class Queries {
    private Queries() {}
    public static class FirstMap extends Mapper<LongWritable,Text,Text,Text> {
        private boolean duration;
        @Override protected void setup(Context c) { duration = c.getConfiguration().getBoolean("cc531.duration", false); }
        @Override protected void map(LongWritable k, Text v, Context c) throws java.io.IOException, InterruptedException {
            Data.Row r = Data.safeParse(v,c); if (r == null) return;
            if (duration) {
                java.time.LocalDate close;
                try { close = r.closure(); } catch (RuntimeException ex) { c.getCounter("input","invalid_date").increment(1); return; }
                if (close == null || close.isAfter(Data.CUT) || close.isBefore(r.occurrence())) return;
                long days = ChronoUnit.DAYS.between(r.occurrence(), close);
                c.write(new Text(r.department()+"\t"+r.event()), new Text(days+"\t1"));
            } else {
                c.write(new Text(r.road()+"\t"+r.occurrence().getMonthValue()),
                    new Text(("INTERRUMPIDO".equals(r.initialState()) ? 1 : 0)+"\t1"));
            }
        }
    }
    public static class Sum extends Reducer<Text,Text,Text,Text> {
        @Override protected void reduce(Text key, Iterable<Text> vals, Context c) throws java.io.IOException, InterruptedException {
            long sum=0,n=0;
            for (Text v:vals) { String[] x=v.toString().split("\t"); sum+=Long.parseLong(x[0]); n+=Long.parseLong(x[1]); }
            c.write(key,new Text(sum+"\t"+n));
        }
    }
    public static class SecondMap extends Mapper<LongWritable,Text,Text,Text> {
        @Override protected void map(LongWritable k, Text v, Context c) throws java.io.IOException, InterruptedException {
            String[] x=v.toString().split("\t"); if (x.length!=4) return;
            c.write(new Text(x[0]),new Text(x[2]+"\t"+x[3]));
        }
    }
    public static class RankMap extends Mapper<LongWritable,Text,Text,Text> {
        @Override protected void map(LongWritable k, Text v, Context c) throws java.io.IOException, InterruptedException {
            String[] x=v.toString().split("\t"); if(x.length!=3)return;
            c.write(new Text("all"),new Text(x[0]+"\t"+x[1]+"\t"+x[2]));
        }
    }
    public static class Rank extends Reducer<Text,Text,Text,Text> {
        private boolean duration;
        @Override protected void setup(Context c) { duration=c.getConfiguration().getBoolean("cc531.duration",false); }
        @Override protected void reduce(Text key, Iterable<Text> vals, Context c) throws java.io.IOException, InterruptedException {
            List<String[]> all=new ArrayList<>();
            for(Text v:vals) { String[] x=v.toString().split("\t"); long n=Long.parseLong(x[2]);
                if(duration || n>=10) all.add(x); }
            Collections.sort(all,(a,b)->{ int q=Double.compare(Double.parseDouble(b[1])/Double.parseDouble(b[2]),
                Double.parseDouble(a[1])/Double.parseDouble(a[2])); return q==0?a[0].compareTo(b[0]):q; });
            int rank=1; for(String[] x:all) { if(rank>10)break;
                double metric=Double.parseDouble(x[1])/Double.parseDouble(x[2])*(duration?1:100);
                c.write(new Text(String.valueOf(rank++)),new Text(x[0]+"\t"+x[2]+"\t"+String.format(java.util.Locale.ROOT,"%.4f",metric)));
            }
        }
    }
    public static void run(Configuration base, Path input, Path output, boolean duration) throws Exception {
        Jobs.absent(base,output);
        Configuration conf=new Configuration(base); conf.setBoolean("cc531.duration",duration);
        String prefix=duration?"duration":"interruption";
        Path p1=new Path(output,"stage1"), p2=new Path(output,"stage2"), p3=new Path(output,"result");
        Job a=Jobs.raw(conf,prefix+" 1/3 partial",input,p1,3); a.setMapperClass(FirstMap.class); a.setReducerClass(Sum.class); Jobs.run(a);
        Job b=Jobs.text(conf,prefix+" 2/3 total",p1,p2,3); b.setMapperClass(SecondMap.class); b.setReducerClass(Sum.class); Jobs.run(b);
        Job c=Jobs.text(conf,prefix+" 3/3 top ten",p2,p3,1); c.setMapperClass(RankMap.class); c.setReducerClass(Rank.class); Jobs.run(c);
    }
}
