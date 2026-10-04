package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import java.util.*;

/** 只重建变更覆盖节点；在每节点扣除旧份额再加新份额，不沿邻接链扩张范围。 */
public final class IncrementalDensityJob implements DensityCalculation {
    private final Map<Long,SourceSnapshot> oldSources,newSources;
    private final SourceSpatialIndex oldIndex=new SourceSpatialIndex(),newIndex=new SourceSpatialIndex();
    private final Map<DensityAllocator.Node,DensityAllocator.NodeAllocation> previousNodes;
    private final Map<Long,Double> previousOutputs;
    private final Map<DensityAllocator.Node,DensityAllocator.NodeAllocation> changedNodes=new HashMap<>();
    private final Map<Long,Double> outputs=new HashMap<>();
    private final Map<SourceSnapshot,Double> norms=new IdentityHashMap<>();
    private final Set<DensityAllocator.Node> dirty=new TreeSet<>(Comparator.comparing(DensityAllocator.Node::type)
            .thenComparingInt(DensityAllocator.Node::x).thenComparingInt(DensityAllocator.Node::z));
    private final double step,power,area,maxRadius;
    private final DensityAllocator.Supply supply;
    private Iterator<DensityAllocator.Node> iterator;
    private boolean complete;
    private long contributions;

    public IncrementalDensityJob(Map<Long,SourceSnapshot> oldSources,Map<Long,SourceSnapshot> newSources,
            Map<DensityAllocator.Node,DensityAllocator.NodeAllocation> previousNodes,Map<Long,Double> previousOutputs,
            double step,double power,DensityAllocator.Supply supply) {
        this.oldSources=Map.copyOf(oldSources);
        Map<Long,SourceSnapshot> canonical=new HashMap<>();
        // 未变化的源复用快照对象，归一化只算一次，节点内用身份缓存避免反复散列所有字段。
        newSources.forEach((id,source) -> canonical.put(id,source.equals(oldSources.get(id))?oldSources.get(id):source));
        this.newSources=Map.copyOf(canonical);
        this.previousNodes=previousNodes;this.previousOutputs=previousOutputs;this.step=step;this.power=power;
        this.area=step*step;this.supply=supply;
        this.oldSources.values().forEach(oldIndex::put);this.newSources.values().forEach(newIndex::put);
        this.maxRadius=java.util.stream.Stream.concat(oldSources.values().stream(),newSources.values().stream())
                .mapToDouble(SourceSnapshot::radius).max().orElse(16);
        Set<Long> ids=new HashSet<>(oldSources.keySet());ids.addAll(newSources.keySet());
        for(long id:ids)if(!Objects.equals(oldSources.get(id),newSources.get(id))) {
            if(oldSources.containsKey(id)) {
                mark(oldSources.get(id));
                oldIndex.overlapping(oldSources.get(id),maxRadius).forEach(peer -> outputs.putIfAbsent(peer.id(),previousOutputs.getOrDefault(peer.id(),0d)));
            }
            if(newSources.containsKey(id)) {
                mark(newSources.get(id));
                newIndex.overlapping(newSources.get(id),maxRadius).forEach(peer -> outputs.putIfAbsent(peer.id(),previousOutputs.getOrDefault(peer.id(),0d)));
            }
        }
    }
    private void mark(SourceSnapshot source) {
        for(int x=(int)Math.ceil((source.x()-source.radius())/step);x<=(int)Math.floor((source.x()+source.radius())/step);x++)
            for(int z=(int)Math.ceil((source.z()-source.radius())/step);z<=(int)Math.floor((source.z()+source.radius())/step);z++)
                if(DensityKernel.weight(x*step-source.x(),z*step-source.z(),source.radius())>0)
                    dirty.add(new DensityAllocator.Node(source.type(),x,z));
    }
    private double q(SourceSnapshot source,double x,double z) {
        double norm=norms.computeIfAbsent(source,key -> {
            double sum=0;
            for(int ix=(int)Math.ceil((key.x()-key.radius())/step);ix<=(int)Math.floor((key.x()+key.radius())/step);ix++)
                for(int iz=(int)Math.ceil((key.z()-key.radius())/step);iz<=(int)Math.floor((key.z()+key.radius())/step);iz++)
                    sum+=DensityKernel.weight(ix*step-key.x(),iz*step-key.z(),key.radius())*area;
            if(!(sum>0))throw new IllegalArgumentException("Empty integration support");return sum;
        });
        return source.potential()*DensityKernel.weight(x-source.x(),z-source.z(),source.radius())/norm;
    }
    @Override public boolean advance(long budgetNanos) {
        if(complete)return true;
        if(budgetNanos<=0)throw new IllegalArgumentException("Invalid work budget");
        if(iterator==null)iterator=dirty.iterator();
        long start=System.nanoTime();
        do {
            if(!iterator.hasNext()){complete=true;return true;}
            var node=iterator.next();double x=node.x()*step,z=node.z()*step;
            var old=previousNodes.get(node);
            if(old!=null)for(var source:oldIndex.covering(node.type(),x,z,maxRadius)) {
                outputs.putIfAbsent(source.id(),previousOutputs.getOrDefault(source.id(),0d));
                outputs.merge(source.id(),-q(source,x,z)*old.fulfillment()*area,Double::sum);contributions++;
            }
            var candidates=newIndex.covering(node.type(),x,z,maxRadius);
            double demand=0;for(var source:candidates)demand+=q(source,x,z);
            double rho=supply.at(node.type(),x,z),a=DensityKernel.fulfillment(demand,rho,power);
            changedNodes.put(node,new DensityAllocator.NodeAllocation(demand,rho,a));
            for(var source:candidates) {
                outputs.putIfAbsent(source.id(),previousOutputs.getOrDefault(source.id(),0d));
                outputs.merge(source.id(),q(source,x,z)*a*area,Double::sum);contributions++;
            }
        }while(System.nanoTime()-start<budgetNanos);
        return false;
    }
    @Override public DensityAllocator.Result result() {
        if(!complete)throw new IllegalStateException("Incomplete batch");
        // 移除设备的差分可出现舍入残差；只保留新快照仍有需求的源。
        Map<Long,Double> active=new HashMap<>();
        outputs.forEach((id,output) -> {if(newSources.containsKey(id))active.put(id,Math.max(0,Math.min(newSources.get(id).potential(),output)));});
        return new DensityAllocator.Result(Collections.unmodifiableMap(active),Collections.unmodifiableMap(changedNodes),contributions);
    }
    @Override public long contributions(){return contributions;}
    @Override public int allocatedNodes(){return changedNodes.size();}
}
