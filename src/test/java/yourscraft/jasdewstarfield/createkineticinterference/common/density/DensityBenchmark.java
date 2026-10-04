package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import java.util.*;

/** 独立可复跑的纯求解压力夹具，记录分帧工作、队列长度、贡献条目与堆内存。 */
public final class DensityBenchmark {
    public static void main(String[] args) {
        System.out.println("java="+System.getProperty("java.version")+" vm="+System.getProperty("java.vm.name")
                +" cpu="+System.getenv("PROCESSOR_IDENTIFIER")+" processors="+Runtime.getRuntime().availableProcessors()
                +" maxHeap="+Runtime.getRuntime().maxMemory()+" budgetMs=1 environment=uniform otherMods=none");
        for(int i=0;i<3;i++)run(1000,false,"warmup");
        for(int count:new int[]{1000,10000})for(boolean scattered:new boolean[]{false,true}) {
            var sources=run(count,scattered,"initial");
            var baseline=runSources(sources,count,scattered,"incremental-baseline");
            Map<Long,SourceSnapshot> previous=new HashMap<>();sources.forEach(source->previous.put(source.id(),source));
            Map<Long,SourceSnapshot> current=new HashMap<>(previous);var first=sources.getFirst();
            current.put(first.id(),new SourceSnapshot(first.id(),first.type(),first.x(),first.z(),2048,first.radius()));
            long setup=System.nanoTime();var incremental=new IncrementalDensityJob(previous,current,baseline.nodes(),baseline.outputs(),2,4,(t,x,z)->8192/(Math.PI*256));
            long setupNanos=System.nanoTime()-setup;List<Long> times=new ArrayList<>();boolean done;
            do {long slice=System.nanoTime();done=incremental.advance(1000000);times.add(System.nanoTime()-slice);}while(!done);
            times.sort(Long::compare);
            System.out.printf(Locale.ROOT,"stage=single-change count=%d layout=%s setupMs=%.3f workP95Ms=%.3f workMaxMs=%.3f slices=%d dirtyNodes=%d contributions=%d%n",
                    count,scattered?"scattered":"dense",setupNanos/1e6,times.get((int)Math.ceil(times.size()*0.95)-1)/1e6,
                    times.getLast()/1e6,times.size(),incremental.result().nodes().size(),incremental.contributions());
            runSources(sources.subList(0,sources.size()/2),count,scattered,"batch-removal");
            runSources(sources,count,scattered,"rule-reload");
        }
    }
    private static List<SourceSnapshot> run(int count,boolean scattered,String stage) {
        var sources=new ArrayList<SourceSnapshot>();
        int side=(int)Math.ceil(Math.sqrt(count));
        for(int i=0;i<count;i++)sources.add(new SourceSnapshot(i,"water",(i%side)*(scattered?80:1)+0.5,
                (i/side)*(scattered?80:1)+0.5,1024,16));
        runSources(sources,count,scattered,stage);return sources;
    }
    private static DensityAllocator.Result runSources(List<SourceSnapshot> sources,int count,boolean scattered,String stage) {
        System.gc();var runtime=Runtime.getRuntime();long before=runtime.totalMemory()-runtime.freeMemory();
        long start=System.nanoTime();var job=new DensityAllocator.Job(sources,2,4,(t,x,z)->8192/(Math.PI*256));
        List<Long> frames=new ArrayList<>();boolean done;
        do {long frame=System.nanoTime();done=job.advance(1000000);frames.add(System.nanoTime()-frame);}while(!done);
        double milliseconds=(System.nanoTime()-start)/1e6;var result=job.result();
        frames.sort(Long::compare);double p95=frames.get((int)Math.ceil(frames.size()*0.95)-1)/1e6;
        long after=runtime.totalMemory()-runtime.freeMemory();
        System.out.printf(Locale.ROOT,"stage=%s count=%d layout=%s solveMs=%.3f workP95Ms=%.3f workMaxMs=%.3f slices=%d nodes=%d contributions=%d heapDeltaMiB=%.2f%n",
                stage,count,scattered?"scattered":"dense",milliseconds,p95,frames.getLast()/1e6,frames.size(),result.nodes().size(),
                result.contributions(),(after-before)/1048576d);
        return result;
    }
}
