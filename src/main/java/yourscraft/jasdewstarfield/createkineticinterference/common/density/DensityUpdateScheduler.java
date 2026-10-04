package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlockEntity;
import com.simibubi.create.content.kinetics.KineticNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import yourscraft.jasdewstarfield.createkineticinterference.common.*;

import java.util.*;

/** 每维度去重调度，按不可变快照分帧求解；全部倍率提交后再批量刷新 Create 网络。 */
public final class DensityUpdateScheduler {
    private static final Map<ServerLevel,DensityUpdateScheduler> SERVICES = new IdentityHashMap<>();
    private final ServerLevel level;
    private final InterferenceNetworkData data;
    private final DensitySettings settings;
    private final Map<BlockPos,KineticBlockEntity> loaded = new HashMap<>();
    private final Map<Long,Double> committed = new HashMap<>();
    private final SourceSpatialIndex index = new SourceSpatialIndex();
    private final Map<Long,SourceSnapshot> indexed = new HashMap<>();
    private final Map<Long,Double> normalizationCache = new HashMap<>();
    private DensityFieldService field;
    private DensityCalculation job;
    private Map<DensityAllocator.Node,DensityAllocator.NodeAllocation> nodeState = new HashMap<>();
    private Map<Long,SourceSnapshot> committedSnapshots = Map.of();
    private boolean forceFull = true, fullJob;
    private DensityAllocator.Result readyResult;
    private Iterator<Long> preparationIds;
    private final Map<Long,Preparation> preparations = new HashMap<>();
    private final Set<Long> newlyLoaded = new HashSet<>();
    private final Set<KineticBlockEntity> pendingSync = new LinkedHashSet<>();
    private long loadRevision;
    private record Preparation(float efficiency,int count,Set<BlockPos> highlights,DensityDiagnostics diagnostics) {}
    private DensityAllocator.Result lastResult;
    private long revision = -1, profileVersion = -1, jobRevision, jobProfileVersion, lastChangeTick, pendingSince = -1;
    private long lastWorkNanos, maxWorkNanos, invalidations, batches, lastDelay;
    private boolean committing;
    private long lastValidationTick = Long.MIN_VALUE;

    public static boolean enabled(IKineticInterference source) {
        return source.getLevel() instanceof ServerLevel server && WorldModelData.get(server) == CalculationModel.DENSITY;
    }
    public static DensityUpdateScheduler get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level,DensityUpdateScheduler::new);
    }
    public static void unload(ServerLevel level) { SERVICES.remove(level); }
    public static void clear() { SERVICES.clear(); }
    private DensityUpdateScheduler(ServerLevel level) {
        this.level = level; this.data = InterferenceNetworkData.get(level); this.settings = DensitySettings.read();
        // 旧坐标没有 P，转入密度模型时保留估计，首次真实加载立即替换。
        migrate(data.getWindmills(),"wind"); migrate(data.getWaterWheels(),"water");
        rebuildField();
    }
    private void migrate(Set<BlockPos> positions,String type) {
        for (var pos : Set.copyOf(positions)) if (!data.getDensitySources().containsKey(pos))
            data.putDensitySource(new DensitySourceRecord(pos,type,"legacy",settings.estimates().get(type),0,"estimated"));
    }
    private void rebuildField() {
        Map<String,DensityProfiles.Profile> profiles = new HashMap<>();
        Map<String,Double> densities = new HashMap<>();
        for (String type : settings.radii().keySet()) {
            profiles.put(type,DensityProfiles.get(settings.profiles().get(type),type));
            densities.put(type,settings.capacities().get(type)/(Math.PI*Math.pow(settings.radii().get(type),2)));
        }
        field = new DensityFieldService(level,profiles,densities,settings.environmentStep(),settings.blend(),settings.sampleY());
        profileVersion = DensityProfiles.version();
    }
    public DensityFieldService field() { return field; }
    public DensitySettings settings() { return settings; }
    private long inputRevision() { return data.densityRevision()+loadRevision; }
    public boolean pending() { return !committing && (job != null || revision != inputRevision() || profileVersion != DensityProfiles.version()); }
    public void observe(KineticBlockEntity be) {
        if (committing || SourceCapacityAdapter.sampling() || be.isRemoved()) return;
        if (!(be instanceof IKineticInterference source)) return;
        var pos = be.getBlockPos();
        double raw = SourceCapacityAdapter.potential(be);
        boolean newEntity=loaded.put(pos,be)!=be;
        if(newEntity) { loadRevision++; newlyLoaded.add(pos.asLong()); }
        if (raw == 0) {
            removeRecord(pos); source.setTracked(false); source.setEfficiencyFactor(0);
            var old=((DensityDiagnostics.View)source).cki$getDiagnostics();
            if(newEntity || !old.enabled() || old.raw()!=0) {
                String type=be instanceof WindmillBearingBlockEntity?"wind":"water";
                ((DensityDiagnostics.View)source).cki$setDiagnostics(new DensityDiagnostics(true,type,0,0,
                        old.localDensity(),old.averageSupply(),settings.radii().get(type),field.sampleY(),0,true,profileVersion,old.baseDensity()));
                pendingSync.add(be);
            }
        } else {
            String type = be instanceof WindmillBearingBlockEntity ? "wind" : "water";
            String kind = be instanceof WindmillBearingBlockEntity ? "windmill" :
                    be instanceof LargeWaterWheelBlockEntity ? "large_waterwheel" : "waterwheel";
            data.putDensitySource(new DensitySourceRecord(pos,type,kind,raw,level.getGameTime(),"validated"));
            source.setTracked(true);
            // 提交前增产不突破原绝对 SU，新源没有已提交额度，输出为零。
            source.setEfficiencyFactor((float)Math.min(1,committed.getOrDefault(pos.asLong(),0d)/raw));
            var old=((DensityDiagnostics.View)source).cki$getDiagnostics();
            if (newEntity || !old.enabled() || old.raw()!=raw) {
                ((DensityDiagnostics.View)source).cki$setDiagnostics(new DensityDiagnostics(true,type,raw,
                        Math.min(raw,committed.getOrDefault(pos.asLong(),0d)),old.localDensity(),old.averageSupply(),
                        settings.radii().get(type),field.sampleY(),old.estimatedSources(),true,profileVersion,old.baseDensity()));
                // 容量 RETURN 钩子此刻尚未写回缩放缓存；发送安排在维度 tick 末尾。
                pendingSync.add(be);
            }
        }
    }
    private void removeRecord(BlockPos pos) {
        data.removeDensitySource(pos); data.removeWindmill(pos); data.removeWaterWheel(pos); committed.remove(pos.asLong());
    }
    public void removed(IKineticInterference source,boolean unloaded) {
        loaded.remove(source.getBlockPos());
        pendingSync.remove((KineticBlockEntity)source);
        if (!unloaded) removeRecord(source.getBlockPos());
    }
    /** 容量暴露前重新采样完整能力，避免转速或附属倍率变化沿用旧效率增产。 */
    public static float capacityFactor(IKineticInterference source) {
        if (!enabled(source)) return source.getEfficiencyFactor();
        var service = get((ServerLevel)source.getLevel());
        service.observe((KineticBlockEntity)source);
        return source.getEfficiencyFactor();
    }
    public static float boundNetworkCapacity(KineticBlockEntity be,float cachedSU) {
        var service=get((ServerLevel)be.getLevel());service.observe(be);
        var record=service.data.getDensitySources().get(be.getBlockPos());
        double allowance=record==null?0:Math.min(record.rawPotentialSU(),service.committedOutput(be.getBlockPos()));
        float limit=(float)allowance;
        if(limit>allowance)limit=Math.nextDown(limit);
        // 只限制最终值，继续保留 Create/附属网络里已经写入的更小系数。
        return Math.min(cachedSU,limit);
    }
    public void tick() {
        long start = System.nanoTime();
        var syncIterator=pendingSync.iterator();
        while(syncIterator.hasNext()) {
            var be=syncIterator.next();syncIterator.remove();if(!be.isRemoved())be.sendData();
            if(System.nanoTime()-start>=settings.budgetNanos())break;
        }
        if (profileVersion != DensityProfiles.version()) { rebuildField(); revision = -1; forceFull = true; }
        // 定期清理可验证的幽灵记录，保留未加载区块；从不为校验加载区块。
        if (level.getGameTime()%settings.interval()==0 && lastValidationTick!=level.getGameTime()) {
            lastValidationTick=level.getGameTime();
            for (var record : List.copyOf(data.getDensitySources().values())) {
                if (!level.hasChunkAt(record.pos())) continue;
                var be = level.getBlockEntity(record.pos());
                boolean correct = record.resourceType().equals("wind") ? be instanceof WindmillBearingBlockEntity : be instanceof WaterWheelBlockEntity;
                if (!correct) removeRecord(record.pos());
                else observe((KineticBlockEntity)be);
            }
        }
        if (job != null && (jobRevision != inputRevision() || jobProfileVersion != profileVersion)) {
            job = null; readyResult = null; preparationIds = null; preparations.clear();
            invalidations++; lastChangeTick = level.getGameTime();
        }
        if (pendingSince < 0 && pending()) pendingSince = level.getGameTime();
        // 维度 Post tick 自然合并本 tick 的所有登记；失效批次在下一次推进重新取快照。
        if (job == null && revision != inputRevision()) {
            var snapshots = data.getDensitySources().values().stream()
                    .map(record -> record.snapshot(settings.radii().get(record.resourceType()))).toList();
            // 索引只查竞争者；计算仍包含全部加载/卸载需求，不截断候选源。
            for (var old : indexed.values()) index.remove(old);
            indexed.clear(); normalizationCache.clear();
            for (var snapshot : snapshots) { index.put(snapshot); indexed.put(snapshot.id(),snapshot); }
            jobRevision = inputRevision(); jobProfileVersion = profileVersion;
            long changes=committedSnapshots.values().stream().filter(old -> !old.equals(indexed.get(old.id()))).count()
                    +indexed.keySet().stream().filter(id -> !committedSnapshots.containsKey(id)).count();
            // 大批变更直接分帧全量重建，避免在一个 tick 建立巨大的差分脏节点并集。
            // 停转后同一 tick 恢复可能得到相同 P，但停转已撤销额度，仍须重新分配。
            boolean revokedAllowance=indexed.keySet().stream().anyMatch(id -> committedSnapshots.containsKey(id)&&!committed.containsKey(id));
            fullJob = forceFull || changes>8 || revokedAllowance;
            job = fullJob ? new DensityAllocator.Job(snapshots,settings.step(),settings.power(),field::at)
                    : new IncrementalDensityJob(committedSnapshots,indexed,nodeState,committed,settings.step(),settings.power(),field::at);
            if(fullJob)for(var be:loaded.values())if(!be.isRemoved() && ((DensityDiagnostics.View)be).cki$getDiagnostics().enabled())pendingSync.add(be);
        }
        if (job != null && (readyResult != null || job.advance(settings.budgetNanos()))) {
            if(readyResult==null) {
                var solved=job.result(); var outputs=new HashMap<>(solved.outputs());
                for(long id:newlyLoaded)if(indexed.containsKey(id))outputs.putIfAbsent(id,committed.getOrDefault(id,0d));
                readyResult=new DensityAllocator.Result(outputs,solved.nodes(),solved.contributions());
                preparationIds=outputs.keySet().iterator();
            }
            long preparationStart=System.nanoTime();
            while(preparationIds.hasNext()) {
                long id=preparationIds.next();var be=loaded.get(BlockPos.of(id));
                if(be!=null&&!be.isRemoved())prepare(id,readyResult);
                if(System.nanoTime()-preparationStart>=settings.budgetNanos())break;
            }
            if(preparationIds.hasNext()) { lastWorkNanos=System.nanoTime()-start;maxWorkNanos=Math.max(maxWorkNanos,lastWorkNanos);return; }
            lastResult = readyResult;
            if (fullJob) nodeState = ((DensityAllocator.Job)job).adoptNodes();
            else lastResult.nodes().forEach((node,value) -> { if(value.demand()==0)nodeState.remove(node);else nodeState.put(node,value); });
            commit(lastResult); readyResult=null; preparationIds=null; preparations.clear(); newlyLoaded.clear();
            committedSnapshots = Map.copyOf(indexed); forceFull = false;
            revision = jobRevision; job = null; batches++;
            lastDelay = pendingSince < 0 ? 0 : level.getGameTime()-pendingSince; pendingSince = -1;
        }
        lastWorkNanos = System.nanoTime()-start; maxWorkNanos = Math.max(maxWorkNanos,lastWorkNanos);
    }
    private void prepare(long id,DensityAllocator.Result result) {
        var record=data.getDensitySources().get(BlockPos.of(id));if(record==null)return;
        var snapshot=indexed.get(id);var peers=index.overlapping(snapshot,settings.radii().get(record.resourceType()));
        int estimates=(int)peers.stream().filter(peer -> data.getDensitySources().get(BlockPos.of(peer.id())).estimated()).count();
        var highlights=new LinkedHashSet<BlockPos>();peers.stream().limit(64).forEach(peer -> highlights.add(BlockPos.of(peer.id())));
        double output=result.outputs().getOrDefault(id,0d);
        // 基础供给密度随同服务端配置同步，客户端比较不依赖本地配置。
        var diagnostics=new DensityDiagnostics(true,record.resourceType(),record.rawPotentialSU(),output,
                field.at(record.resourceType(),snapshot.x(),snapshot.z()),meanSupply(snapshot,result),snapshot.radius(),
                field.sampleY(),estimates,false,jobProfileVersion,
                settings.capacities().get(record.resourceType())/(Math.PI*snapshot.radius()*snapshot.radius()));
        preparations.put(id,new Preparation((float)Math.min(1,output/record.rawPotentialSU()),peers.size(),highlights,diagnostics));
    }
    private void commit(DensityAllocator.Result result) {
        committing = true;
        try {
            if(fullJob)committed.clear();
            committed.putAll(result.outputs());
            Set<KineticNetwork> networks = new HashSet<>();
            Set<KineticBlockEntity> changed=new HashSet<>();
            for (var entry : loaded.entrySet()) {
                var be = entry.getValue();
                if (be.isRemoved()) continue;
                var source = (IKineticInterference)be;
                var record = data.getDensitySources().get(entry.getKey());
                if (record == null) {
                    var old=((DensityDiagnostics.View)source).cki$getDiagnostics();
                    if(old.pending()||old.raw()!=0||source.getNearbyCount()!=0||old.enabled()&&old.version()!=profileVersion)changed.add(be);
                    source.setEfficiencyFactor(0); source.setNearbyCount(0); source.setInterferenceSources(Set.of());
                    // 停转源不参与需求，保留诊断以便恢复；客户端隐藏效率与当地条件。
                    ((DensityDiagnostics.View)source).cki$setDiagnostics(old.enabled()
                            ? new DensityDiagnostics(true,old.type(),0,0,old.localDensity(),old.averageSupply(),
                                    old.radius(),old.sampleY(),0,false,profileVersion,old.baseDensity()):DensityDiagnostics.EMPTY);
                    continue;
                }
                if(!result.outputs().containsKey(entry.getKey().asLong()))continue;
                var prepared=preparations.get(entry.getKey().asLong());if(prepared==null)continue;
                source.setNearbyCount(prepared.count());source.setInterferenceSources(prepared.highlights());
                source.setEfficiencyFactor(prepared.efficiency());((DensityDiagnostics.View)source).cki$setDiagnostics(prepared.diagnostics());
                changed.add(be);
            }
            // 全部倍率先提交。网络系数全写入后每网络统一重算，避免回调读取混合批次。
            for (var be : changed) if (!be.isRemoved()) {
                if (be.hasNetwork()) {
                    var network = be.getOrCreateNetwork();
                    if (network.sources.containsKey(be)) network.sources.put(be,be.calculateAddedStressCapacity());
                    networks.add(network);
                }
                be.setChanged();
            }
            networks.forEach(KineticNetwork::updateCapacity);
            // 网络中的实际 SU 已统一刷新，包里的 Create 网络字段与 CKI 诊断属于同一批次。
            for(var be:changed)if(!be.isRemoved()){pendingSync.remove(be);be.sendData();}
        } finally { committing = false; }
    }
    private double meanSupply(SourceSnapshot source,DensityAllocator.Result result) {
        double sum = 0; int count = 0;
        double step = settings.step();
        for (int x=(int)Math.ceil((source.x()-source.radius())/step); x<=(int)Math.floor((source.x()+source.radius())/step); x++)
            for (int z=(int)Math.ceil((source.z()-source.radius())/step); z<=(int)Math.floor((source.z()+source.radius())/step); z++) {
                if (DensityKernel.weight(x*step-source.x(),z*step-source.z(),source.radius())==0) continue;
                var key=new DensityAllocator.Node(source.type(),x,z);
                var node = result.nodes().get(key);
                if(node==null&&!fullJob)node=nodeState.get(key);
                if (node != null) { sum+=node.supply(); count++; }
            }
        return count == 0 ? 0 : sum/count;
    }
    public DensityAllocator.NodeAllocation sample(String type,double x,double z) {
        // 诊断 D 直接取当前需求快照，保持 rho/D/a 三层在同一世界位置可解释。
        double demand = 0, area=settings.step()*settings.step();
        for (var snapshot : index.covering(type,x,z,settings.radii().get(type))) {
            double weight=DensityKernel.weight(x-snapshot.x(),z-snapshot.z(),snapshot.radius());
            if (weight==0) continue;
            double norm=normalizationCache.computeIfAbsent(snapshot.id(),id -> {
                double normalization=0;
                for (int ix=(int)Math.ceil((snapshot.x()-snapshot.radius())/settings.step());ix<=(int)Math.floor((snapshot.x()+snapshot.radius())/settings.step());ix++)
                    for (int iz=(int)Math.ceil((snapshot.z()-snapshot.radius())/settings.step());iz<=(int)Math.floor((snapshot.z()+snapshot.radius())/settings.step());iz++)
                        normalization+=DensityKernel.weight(ix*settings.step()-snapshot.x(),iz*settings.step()-snapshot.z(),snapshot.radius())*area;
                return normalization;
            });
            demand+=snapshot.potential()*weight/norm;
        }
        double rho=field.at(type,x,z);
        return new DensityAllocator.NodeAllocation(demand,rho,DensityKernel.fulfillment(demand,rho,settings.power()));
    }
    public int sampleCandidates(String type,double x,double z) { return index.covering(type,x,z,settings.radii().get(type)).size(); }
    public double committedOutput(BlockPos pos) { return committed.getOrDefault(pos.asLong(),0d); }
    public String sourceCounts(DensitySourceRecord record) {
        var peers=index.overlapping(record.snapshot(settings.radii().get(record.resourceType())),settings.radii().get(record.resourceType()));
        long loadedCount=peers.stream().filter(peer -> loaded.containsKey(BlockPos.of(peer.id()))).count();
        long estimated=peers.stream().filter(peer -> data.getDensitySources().get(BlockPos.of(peer.id())).estimated()).count();
        return "competitors="+peers.size()+" loadedPeers="+loadedCount+" snapshotPeers="+(peers.size()-loadedCount)+" estimatedPeers="+estimated;
    }
    public long lastWorkNanos() { return lastWorkNanos; }
    public int nodeCount() { return nodeState.size(); }
    public String stats() {
        return "model=DENSITY sources="+data.getDensitySources().size()+" estimated="+data.getDensitySources().values().stream().filter(DensitySourceRecord::estimated).count()
                +" pending="+pending()+" ageTicks="+(pendingSince<0?0:level.getGameTime()-pendingSince)
                +" lastDelayTicks="+lastDelay+" workMs="+lastWorkNanos/1e6+" maxWorkMs="+maxWorkNanos/1e6
                +" nodes="+nodeState.size()+" contributions="+(job==null?(lastResult==null?0:lastResult.contributions()):job.contributions())
                +" progressSources="+(job instanceof DensityAllocator.Job full?full.projectedSources():indexed.size())
                +" progressNodes="+(job==null?0:job.allocatedNodes())+" pendingPackets="+pendingSync.size()
                +" batches="+batches+" invalidations="+invalidations+" profileVersion="+profileVersion+" sampleY="+field.sampleY();
    }
}
