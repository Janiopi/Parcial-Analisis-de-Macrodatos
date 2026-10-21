package pe.edu.cc531;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Random;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

/** One-hidden-layer MLP trained by full-batch gradient descent, one MR job per epoch. */
public final class Mlp {
    private Mlp() {}
    static final int INPUT=68, HIDDEN=16, OUT=HIDDEN*(INPUT+1), SIZE=OUT+HIDDEN+1;
    static final double TARGET_SCALE=Math.log1p(161.0), RATE=0.1;
    static double[] features(Data.Row r) {
        double[] x=new double[INPUT];
        x[0]=(r.lat()+11)/7; x[1]=(r.lon()+75)/7;
        x[2]=(r.occurrence().getMonthValue()-6.5)/5.5;
        x[3]=(r.occurrence().getDayOfYear()-183)/183.0;
        x[4+Math.floorMod(r.event().hashCode(),32)]=1;
        x[36+Math.floorMod(r.department().hashCode(),32)]=1;
        return x;
    }
    static boolean test(int id) {
        int x=id^42;x^=x>>>16;x*=0x7feb352d;x^=x>>>15;x*=0x846ca68b;x^=x>>>16;
        return Math.floorMod(x,5)==0;
    }
    static Double target(Data.Row r,boolean regression) {
        if(!regression) {
            if("INTERRUMPIDO".equals(r.initialState()))return 1.0;
            if("RESTRINGIDO".equals(r.initialState()))return 0.0;
            return null;
        }
        try {
            java.time.LocalDate close=r.closure();
            if(close==null||close.isAfter(Data.CUT)||close.isBefore(r.occurrence()))return null;
            return Math.log1p(ChronoUnit.DAYS.between(r.occurrence(),close))/TARGET_SCALE;
        }catch(RuntimeException ex){return null;}
    }
    static double original(double y,boolean regression){return regression?Math.expm1(Math.max(-2,Math.min(2,y))*TARGET_SCALE):y;}
    static double sigmoid(double z){return z>=0?1/(1+Math.exp(-z)):Math.exp(z)/(1+Math.exp(z));}
    static double forward(double[] w,double[] x,boolean reg,double[] hidden){
        for(int h=0;h<HIDDEN;h++){
            int off=h*(INPUT+1);double z=w[off+INPUT];for(int i=0;i<INPUT;i++)z+=w[off+i]*x[i];hidden[h]=Math.tanh(z);
        }
        double z=w[OUT+HIDDEN];for(int h=0;h<HIDDEN;h++)z+=w[OUT+h]*hidden[h];
        return reg?z:sigmoid(z);
    }
    static double accumulate(double[] w,double[] x,double y,boolean reg,double[] grad,double positiveWeight){
        double[] hidden=new double[HIDDEN];double pred=forward(w,x,reg,hidden),weight=(!reg&&y>=0.5)?positiveWeight:1.0,delta=(pred-y)*weight;
        for(int h=0;h<HIDDEN;h++){
            grad[OUT+h]+=delta*hidden[h];
            double d=delta*w[OUT+h]*(1-hidden[h]*hidden[h]);int off=h*(INPUT+1);
            for(int i=0;i<INPUT;i++)grad[off+i]+=d*x[i];grad[off+INPUT]+=d;
        }
        grad[OUT+HIDDEN]+=delta;
        if(reg)return 0.5*delta*delta;
        double p=Math.max(1e-12,Math.min(1-1e-12,pred));return -weight*(y*Math.log(p)+(1-y)*Math.log(1-p));
    }
    static double[] model(Configuration conf) throws Exception {
        double[][] holder=new double[1][];
        Jobs.eachLine(conf,new Path(conf.get("cc531.model.path")),line->{
            if(!line.startsWith("MODEL\t"))return;
            String[] a=line.substring(6).split(",");if(a.length!=SIZE)throw new IllegalStateException("Model length: "+a.length);
            holder[0]=new double[SIZE];for(int i=0;i<SIZE;i++)holder[0][i]=Double.parseDouble(a[i]);
        });
        if(holder[0]==null)throw new IllegalStateException("No model found");return holder[0];
    }
    static String encode(double[] x){StringBuilder b=new StringBuilder();for(int i=0;i<x.length;i++){if(i>0)b.append(',');b.append(Double.toString(x[i]));}return b.toString();}
    static boolean reg(Configuration conf){return conf.getBoolean("cc531.regression",false);}
    public static class CountMap extends Mapper<LongWritable,Text,Text,Text> {
        @Override protected void map(LongWritable k,Text v,Context c)throws IOException,InterruptedException {
            Data.Row r=Data.safeParse(v,c);if(r==null||test(r.id()))return;
            Double y=target(r,false);if(y!=null)c.write(new Text("counts"),new Text(y>=0.5?"1\t0":"0\t1"));
        }
    }
    public static class CountReduce extends Reducer<Text,Text,Text,Text> {
        @Override protected void reduce(Text k,Iterable<Text> vs,Context c)throws IOException,InterruptedException {
            long positive=0,negative=0;for(Text v:vs){String[] p=v.toString().split("\t");positive+=Long.parseLong(p[0]);negative+=Long.parseLong(p[1]);}
            c.write(k,new Text(positive+"\t"+negative));
        }
    }
    public static class GradientMap extends Mapper<LongWritable,Text,Text,Text> {
        private double[] w,grad;private boolean reg;private long n;private double loss,positiveWeight;
        @Override protected void setup(Context c)throws IOException{try{w=model(c.getConfiguration());}catch(Exception e){throw new IOException(e);}grad=new double[SIZE];reg=reg(c.getConfiguration());positiveWeight=c.getConfiguration().getDouble("cc531.positive.weight",1.0);}
        @Override protected void map(LongWritable k,Text v,Context c)throws IOException,InterruptedException{
            Data.Row r=Data.safeParse(v,c);if(r==null||test(r.id()))return;
            Double y=target(r,reg);if(y==null)return;
            loss+=accumulate(w,features(r),y,reg,grad,positiveWeight);n++;
        }
        @Override protected void cleanup(Context c)throws IOException,InterruptedException{
            if(n>0)c.write(new Text("gradient"),new Text(n+"\t"+loss+"\t"+encode(grad)));
        }
    }
    public static class GradientReduce extends Reducer<Text,Text,Text,Text> {
        private double[] w;
        @Override protected void setup(Context c)throws IOException{try{w=model(c.getConfiguration());}catch(Exception e){throw new IOException(e);}}
        @Override protected void reduce(Text k,Iterable<Text> vs,Context c)throws IOException,InterruptedException{
            double[] g=new double[SIZE];long n=0;double loss=0;
            for(Text v:vs){String[] p=v.toString().split("\t");n+=Long.parseLong(p[0]);loss+=Double.parseDouble(p[1]);String[] a=p[2].split(",");
                for(int i=0;i<SIZE;i++)g[i]+=Double.parseDouble(a[i]);}
            if(n==0)throw new IOException("No training rows");
            for(int i=0;i<SIZE;i++)w[i]-=RATE*g[i]/n;
            c.write(new Text("LOSS"),new Text(n+"\t"+String.format(Locale.ROOT,"%.10f",loss/n)));
            c.write(new Text("MODEL"),new Text(encode(w)));
        }
    }
    public static class EvalMap extends Mapper<LongWritable,Text,Text,Text> {
        private double[] w;private boolean reg;
        @Override protected void setup(Context c)throws IOException{try{w=model(c.getConfiguration());}catch(Exception e){throw new IOException(e);}reg=reg(c.getConfiguration());}
        @Override protected void map(LongWritable k,Text v,Context c)throws IOException,InterruptedException{
            Data.Row r=Data.safeParse(v,c);if(r==null)return;Double y=target(r,reg);if(y==null)return;
            double p=forward(w,features(r),reg,new double[HIDDEN]);double loss,mae=0,mse=0,tp=0,fp=0,fn=0,correct=0;
            if(reg){double error=original(p,true)-original(y,true);mae=Math.abs(error);mse=error*error;loss=0.5*(p-y)*(p-y);}
            else {double clipped=Math.max(1e-12,Math.min(1-1e-12,p));loss=-(y*Math.log(clipped)+(1-y)*Math.log(1-clipped));
                boolean predicted=p>=0.5,actual=y>=0.5;correct=predicted==actual?1:0;
                tp=predicted&&actual?1:0;fp=predicted&&!actual?1:0;fn=!predicted&&actual?1:0;}
            c.write(new Text(test(r.id())?"test":"train"),new Text("1\t"+loss+"\t"+mae+"\t"+mse+"\t"+correct+"\t"+tp+"\t"+fp+"\t"+fn));
        }
    }
    public static class EvalReduce extends Reducer<Text,Text,Text,Text> {
        private boolean reg;
        @Override protected void setup(Context c){reg=reg(c.getConfiguration());}
        @Override protected void reduce(Text k,Iterable<Text> vs,Context c)throws IOException,InterruptedException{
            double[] s=new double[8];for(Text v:vs){String[] p=v.toString().split("\t");for(int i=0;i<8;i++)s[i]+=Double.parseDouble(p[i]);}
            double n=s[0];if(reg)c.write(k,new Text(String.format(Locale.ROOT,"n=%.0f\tloss=%.6f\tMAE_days=%.6f\tRMSE_days=%.6f",n,s[1]/n,s[2]/n,Math.sqrt(s[3]/n))));
            else {double precision=s[5]+s[6]==0?0:s[5]/(s[5]+s[6]),recall=s[5]+s[7]==0?0:s[5]/(s[5]+s[7]);
                double f1=precision+recall==0?0:2*precision*recall/(precision+recall);
                c.write(k,new Text(String.format(Locale.ROOT,"n=%.0f\tloss=%.6f\taccuracy=%.6f\tprecision=%.6f\trecall=%.6f\tF1=%.6f",n,s[1]/n,s[4]/n,precision,recall,f1)));}
        }
    }
    public static class PredictionMap extends Mapper<LongWritable,Text,Text,Text> {
        private double[] w;private boolean reg;
        @Override protected void setup(Context c)throws IOException{try{w=model(c.getConfiguration());}catch(Exception e){throw new IOException(e);}reg=reg(c.getConfiguration());}
        @Override protected void map(LongWritable k,Text v,Context c)throws IOException,InterruptedException{
            Data.Row r=Data.safeParse(v,c);if(r==null)return;Double y=target(r,reg);if(y==null)return;
            double p=forward(w,features(r),reg,new double[HIDDEN]);
            c.write(new Text(""+r.id()),new Text((test(r.id())?"test":"train")+"\t"+String.format(Locale.ROOT,"%.6f\t%.6f",original(y,reg),original(p,reg))));
        }
    }
    public static void run(Configuration base,Path input,Path output,boolean regression,int epochs)throws Exception{
        if(epochs<1||epochs>500)throw new IllegalArgumentException("epochs must be 1..500");Jobs.absent(base,output);
        Configuration training=new Configuration(base);
        if(!regression){
            Path counts=new Path(output,"class-counts");Job count=Jobs.raw(training,"MLP training class counts",input,counts,1);
            count.setMapperClass(CountMap.class);count.setReducerClass(CountReduce.class);Jobs.run(count);
            final long[] totals={0,0};Jobs.eachLine(base,counts,line->{String[] p=line.split("\t");totals[0]=Long.parseLong(p[1]);totals[1]=Long.parseLong(p[2]);});
            if(totals[0]==0||totals[1]==0)throw new IllegalStateException("Both classes required in training");
            training.setDouble("cc531.positive.weight",(double)totals[1]/totals[0]);
        }
        FileSystem fs=output.getFileSystem(base);Path init=new Path(output,"model-init");fs.mkdirs(init);
        Random rng=new Random(42);double[] weights=new double[SIZE];
        for(int h=0;h<HIDDEN;h++)for(int i=0;i<INPUT;i++)weights[h*(INPUT+1)+i]=rng.nextGaussian()*0.05;
        for(int h=0;h<HIDDEN;h++)weights[OUT+h]=rng.nextGaussian()*0.05;
        try(BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(fs.create(new Path(init,"part-r-00000")),StandardCharsets.UTF_8))){writer.write("MODEL\t"+encode(weights)+"\n");}
        Path prev=init;
        for(int epoch=1;epoch<=epochs;epoch++){
            Configuration conf=new Configuration(training);conf.setBoolean("cc531.regression",regression);conf.set("cc531.model.path",prev.toString());
            Path next=new Path(output,String.format(Locale.ROOT,"epoch-%03d",epoch));
            Job job=Jobs.raw(conf,(regression?"regression":"classification")+" gradient epoch "+epoch,input,next,1);
            job.setMapperClass(GradientMap.class);job.setReducerClass(GradientReduce.class);Jobs.run(job);prev=next;
        }
        Configuration end=new Configuration(base);end.setBoolean("cc531.regression",regression);end.set("cc531.model.path",prev.toString());
        Job eval=Jobs.raw(end,"MLP evaluation",input,new Path(output,"metrics"),1);eval.setMapperClass(EvalMap.class);eval.setReducerClass(EvalReduce.class);Jobs.run(eval);
        Job pred=Jobs.raw(end,"MLP predictions",input,new Path(output,"predictions"),0);pred.setMapperClass(PredictionMap.class);Jobs.run(pred);
    }
}
