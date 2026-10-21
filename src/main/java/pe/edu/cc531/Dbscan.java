package pe.edu.cc531;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.NLineInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/** Exact Haversine DBSCAN; grid cells prune candidate pairs, labels propagate over core edges. */
public final class Dbscan {
    private Dbscan() {}
    private static final double EPS=20.0;
    private static final double LAT_CELL=EPS/111.2;
    private static final double LON_CELL=EPS/(111.2*Math.cos(Math.toRadians(18.1)));
    private static final int MIN_PTS=4;
    private static Map<Integer,Integer> loadLabels(Configuration conf) throws Exception {
        Map<Integer,Integer> labels=new HashMap<>(); String name=conf.get("cc531.labels.path");
        if(name!=null) Jobs.eachLine(conf,new Path(name),line->{ String[] p=line.split("\t"); labels.put(Integer.parseInt(p[0]),Integer.parseInt(p[1])); });
        return labels;
    }
    private static Set<Integer> loadCores(Configuration conf) throws Exception {
        Set<Integer> cores=new HashSet<>();
        Jobs.eachLine(conf,new Path(conf.get("cc531.core.path")),line->{String[] p=line.split("\t"); if(p.length>=4 && p[3].equals("1"))cores.add(Integer.parseInt(p[0]));});
        return cores;
    }
    public static class CellMap extends Mapper<LongWritable,Text,Text,Text> {
        @Override protected void map(LongWritable k,Text v,Context c) throws IOException,InterruptedException {
            Data.Row r=Data.safeParse(v,c); if(r==null)return;
            int y=(int)Math.floor(r.lat()/LAT_CELL), x=(int)Math.floor(r.lon()/LON_CELL);
            Text point=new Text(r.id()+"\t"+r.lat()+"\t"+r.lon());
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)
                c.write(new Text((x+dx)+","+(y+dy)),point);
        }
    }
    private static final class Point { int id; double lat,lon; Point(String s) {String[] p=s.split("\t");id=Integer.parseInt(p[0]);lat=Double.parseDouble(p[1]);lon=Double.parseDouble(p[2]);} }
    public static class Candidate extends Reducer<Text,Text,Text,Text> {
        @Override protected void reduce(Text key,Iterable<Text> vals,Context c) throws IOException,InterruptedException {
            Map<Integer,Point> unique=new HashMap<>();
            for(Text v:vals){Point p=new Point(v.toString());unique.put(p.id,p);}
            List<Point> p=new ArrayList<>(unique.values());
            for(int i=0;i<p.size();i++)for(int j=i+1;j<p.size();j++) {
                Point a=p.get(i),b=p.get(j);
                if(Data.haversine(a.lat,a.lon,b.lat,b.lon)<=EPS)
                    c.write(new Text(Math.min(a.id,b.id)+"\t"+Math.max(a.id,b.id)),new Text("1"));
            }
        }
    }
    public static class IdentityMap extends Mapper<LongWritable,Text,Text,Text> {
        @Override protected void map(LongWritable k,Text v,Context c) throws IOException,InterruptedException {
            String[] p=v.toString().split("\t"); if(p.length>=2)c.write(new Text(p[0]+"\t"+p[1]),new Text("1"));
        }
    }
    public static class Dedup extends Reducer<Text,Text,Text,Text> {
        @Override protected void reduce(Text k,Iterable<Text> v,Context c) throws IOException,InterruptedException {
            String[] p=k.toString().split("\t"); c.write(new Text(p[0]),new Text(p[1]));
        }
    }
    public static class EdgeMap extends Mapper<LongWritable,Text,Text,Text> {
        @Override protected void map(LongWritable k,Text v,Context c) throws IOException,InterruptedException {
            String[] p=v.toString().split("\t"); if(p.length<2)return;
            c.write(new Text(p[0]),new Text("E:"+p[1])); c.write(new Text(p[1]),new Text("E:"+p[0]));
        }
    }
    public static class PointMap extends Mapper<LongWritable,Text,Text,Text> {
        @Override protected void map(LongWritable k,Text v,Context c) throws IOException,InterruptedException {
            Data.Row r=Data.safeParse(v,c);if(r!=null)c.write(new Text(""+r.id()),new Text("P:"+r.lat()+"\t"+r.lon()));
        }
    }
    public static class CoreReduce extends Reducer<Text,Text,Text,Text> {
        @Override protected void reduce(Text k,Iterable<Text> vals,Context c) throws IOException,InterruptedException {
            String coords=null; Set<Integer> edges=new java.util.TreeSet<>();
            for(Text v:vals){String s=v.toString();if(s.startsWith("P:"))coords=s.substring(2);else if(s.startsWith("E:"))edges.add(Integer.parseInt(s.substring(2)));}
            if(coords==null)return;
            StringBuilder b=new StringBuilder();for(int id:edges){if(b.length()>0)b.append(',');b.append(id);}
            c.write(k,new Text(coords+"\t"+(edges.size()+1>=MIN_PTS?"1":"0")+"\t"+b));
        }
    }
    public static class LabelMap extends Mapper<LongWritable,Text,Text,Text> {
        private Map<Integer,Integer> labels;private Set<Integer> cores;
        @Override protected void setup(Context c) throws IOException { try {labels=loadLabels(c.getConfiguration());cores=loadCores(c.getConfiguration());}catch(Exception e){throw new IOException(e);} }
        @Override protected void map(LongWritable k,Text v,Context c) throws IOException,InterruptedException {
            String[] p=v.toString().split("\t",-1);if(p.length<5||!p[3].equals("1"))return;
            int id=Integer.parseInt(p[0]),label=labels.getOrDefault(id,id);
            c.write(new Text(""+id),new Text(""+label));
            if(!p[4].isEmpty())for(String neighbor:p[4].split(",")) {
                int n=Integer.parseInt(neighbor);if(cores.contains(n))c.write(new Text(neighbor),new Text(""+label));
            }
        }
    }
    public static class LabelReduce extends Reducer<Text,Text,Text,Text> {
        private Map<Integer,Integer> labels;
        @Override protected void setup(Context c) throws IOException {try{labels=loadLabels(c.getConfiguration());}catch(Exception e){throw new IOException(e);} }
        @Override protected void reduce(Text k,Iterable<Text> vals,Context c) throws IOException,InterruptedException {
            int id=Integer.parseInt(k.toString()),before=labels.getOrDefault(id,id),min=before;
            for(Text v:vals)min=Math.min(min,Integer.parseInt(v.toString()));
            if(min<before)c.getCounter("dbscan","changed_labels").increment(1);
            c.write(k,new Text(""+min));
        }
    }
    public static class AssignMap extends Mapper<LongWritable,Text,Text,Text> {
        private Map<Integer,Integer> labels;private Set<Integer> cores;
        @Override protected void setup(Context c) throws IOException {try{labels=loadLabels(c.getConfiguration());cores=loadCores(c.getConfiguration());}catch(Exception e){throw new IOException(e);} }
        @Override protected void map(LongWritable k,Text v,Context c) throws IOException,InterruptedException {
            String[] p=v.toString().split("\t",-1);if(p.length<5)return;
            int id=Integer.parseInt(p[0]),cluster=-1;String kind="noise";
            if(cores.contains(id)){cluster=labels.get(id);kind="core";}
            else if(!p[4].isEmpty())for(String n:p[4].split(",")){
                int neighbor=Integer.parseInt(n);if(cores.contains(neighbor)){
                    cluster=cluster<0?labels.get(neighbor):Math.min(cluster,labels.get(neighbor));kind="border";
                }
            }
            c.write(new Text(p[0]),new Text(p[1]+"\t"+p[2]+"\t"+cluster+"\t"+kind));
        }
    }
    public static class CentroidMap extends Mapper<LongWritable,Text,Text,Text> {
        @Override protected void map(LongWritable k,Text v,Context c) throws IOException,InterruptedException {
            String[] p=v.toString().split("\t");if(p.length<5||p[3].equals("-1"))return;
            c.write(new Text(p[3]),new Text(p[1]+"\t"+p[2]));
        }
    }
    public static class CentroidReduce extends Reducer<Text,Text,Text,Text> {
        @Override protected void reduce(Text k,Iterable<Text> vs,Context c) throws IOException,InterruptedException {
            double lat=0,lon=0;int n=0;for(Text v:vs){String[] p=v.toString().split("\t");lat+=Double.parseDouble(p[0]);lon+=Double.parseDouble(p[1]);n++;}
            c.write(k,new Text(n+"\t"+(lat/n)+"\t"+(lon/n)));
        }
    }
    private static Map<Integer,double[]> centroids(Configuration conf) throws Exception {
        Map<Integer,double[]> out=new HashMap<>();Jobs.eachLine(conf,new Path(conf.get("cc531.centroids.path")),line->{String[] p=line.split("\t");out.put(Integer.parseInt(p[0]),new double[]{Double.parseDouble(p[2]),Double.parseDouble(p[3])});});return out;
    }
    public static class ScatterMap extends Mapper<LongWritable,Text,Text,Text> {
        private Map<Integer,double[]> centroids;
        @Override protected void setup(Context c) throws IOException {try{centroids=centroids(c.getConfiguration());}catch(Exception e){throw new IOException(e);} }
        @Override protected void map(LongWritable k,Text v,Context c) throws IOException,InterruptedException {
            String[] p=v.toString().split("\t");if(p.length<5||p[3].equals("-1"))return;
            int id=Integer.parseInt(p[3]);double[] center=centroids.get(id);
            c.write(new Text(p[3]),new Text(""+Data.haversine(Double.parseDouble(p[1]),Double.parseDouble(p[2]),center[0],center[1])));
        }
    }
    public static class ScatterReduce extends Reducer<Text,Text,Text,Text> {
        @Override protected void reduce(Text k,Iterable<Text> vs,Context c) throws IOException,InterruptedException {
            double sum=0;int n=0;for(Text v:vs){sum+=Double.parseDouble(v.toString());n++;}c.write(k,new Text(""+(sum/n)));
        }
    }
    public static class IndexMap extends Mapper<LongWritable,Text,Text,Text> {
        @Override protected void map(LongWritable k,Text v,Context c) throws IOException,InterruptedException {c.write(new Text("DBI"),v);}
    }
    public static class IndexReduce extends Reducer<Text,Text,Text,Text> {
        private Map<Integer,double[]> centers;
        @Override protected void setup(Context c)throws IOException{try{centers=centroids(c.getConfiguration());}catch(Exception e){throw new IOException(e);} }
        @Override protected void reduce(Text k,Iterable<Text> vs,Context c)throws IOException,InterruptedException{
            Map<Integer,Double> scatter=new HashMap<>();for(Text v:vs){String[] p=v.toString().split("\t");scatter.put(Integer.parseInt(p[0]),Double.parseDouble(p[1]));}
            if(scatter.size()<2){c.write(k,new Text("NaN\t"+scatter.size()));return;}
            double total=0;for(int i:scatter.keySet()){
                double max=0;for(int j:scatter.keySet())if(i!=j){double d=Data.haversine(centers.get(i)[0],centers.get(i)[1],centers.get(j)[0],centers.get(j)[1]);if(d>0)max=Math.max(max,(scatter.get(i)+scatter.get(j))/d);}
                total+=max;
            }
            c.write(k,new Text(String.format(Locale.ROOT,"%.6f\t%d",total/scatter.size(),scatter.size())));
        }
    }
    public static void run(Configuration base,Path input,Path output)throws Exception{
        Jobs.absent(base,output);
        Path candidates=new Path(output,"candidates"),edges=new Path(output,"edges"),core=new Path(output,"core"),assign=new Path(output,"assignments");
        Job a=Jobs.raw(base,"dbscan candidate pairs",input,candidates,3);a.setMapperClass(CellMap.class);a.setReducerClass(Candidate.class);Jobs.run(a);
        Job b=Jobs.text(base,"dbscan unique edges",candidates,edges,3);b.setMapperClass(IdentityMap.class);b.setReducerClass(Dedup.class);Jobs.run(b);
        Job d=Job.getInstance(base,"dbscan core points");d.setJarByClass(Main.class);d.setOutputKeyClass(Text.class);d.setOutputValueClass(Text.class);d.setReducerClass(CoreReduce.class);d.setNumReduceTasks(3);
        MultipleInputs.addInputPath(d,edges,TextInputFormat.class,EdgeMap.class);MultipleInputs.addInputPath(d,input,NLineInputFormat.class,PointMap.class);NLineInputFormat.setNumLinesPerSplit(d,100);FileOutputFormat.setOutputPath(d,core);Jobs.run(d);
        Configuration lc=new Configuration(base);lc.set("cc531.core.path",core.toString());Path labels=null;int max=2000;
        for(int iter=0;iter<max;iter++){
            Configuration next=new Configuration(lc);if(labels!=null)next.set("cc531.labels.path",labels.toString());
            Path dest=new Path(output,String.format(Locale.ROOT,"labels-%04d",iter));
            Job job=Jobs.text(next,"dbscan propagate "+iter,core,dest,3);job.setMapperClass(LabelMap.class);job.setReducerClass(LabelReduce.class);Jobs.run(job);
            labels=dest;if(job.getCounters().findCounter("dbscan","changed_labels").getValue()==0)break;
            if(iter==max-1)throw new IllegalStateException("DBSCAN labels did not converge");
        }
        Configuration finalConf=new Configuration(lc);finalConf.set("cc531.labels.path",labels.toString());
        Job e=Jobs.text(finalConf,"dbscan assignments",core,assign,0);e.setMapperClass(AssignMap.class);Jobs.run(e);
        Path centers=new Path(output,"centroids"),scatter=new Path(output,"scatter"),index=new Path(output,"davies-bouldin");
        Job f=Jobs.text(base,"dbscan centroids",assign,centers,3);f.setMapperClass(CentroidMap.class);f.setReducerClass(CentroidReduce.class);Jobs.run(f);
        Configuration ic=new Configuration(base);ic.set("cc531.centroids.path",centers.toString());
        Job g=Jobs.text(ic,"dbscan scatter",assign,scatter,3);g.setMapperClass(ScatterMap.class);g.setReducerClass(ScatterReduce.class);Jobs.run(g);
        Job h=Jobs.text(ic,"dbscan Davies Bouldin",scatter,index,1);h.setMapperClass(IndexMap.class);h.setReducerClass(IndexReduce.class);Jobs.run(h);
    }
}
