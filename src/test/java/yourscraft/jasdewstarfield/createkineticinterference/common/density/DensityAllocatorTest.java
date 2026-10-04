package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 数学夹具与设计解析积分独立对照；GameTest 另查 Create 的 float 与生命周期。 */
class DensityAllocatorTest {
    private static final double RHO = 8192 / (Math.PI * 16 * 16);
    private SourceSnapshot source(long id, double x, double z, double p) {
        return new SourceSnapshot(id, "water", x, z, p, 16);
    }
    private DensityAllocator.Result solve(List<SourceSnapshot> sources, double step) {
        return DensityAllocator.solve(sources, step, 8, (type, x, z) -> RHO);
    }
    private double analytic(double ratio) {
        double result = 0;
        // 独立径向积分：平坦中心与 smoothstep 外圈，节点饱和用显式分段公式对照。
        for (int i = 0; i < 100000; i++) {
            double r = (i + 0.5) / 100000, t = Math.max(0, (r - 0.9) / 0.1);
            double w = 1 - t * t * (3 - 2 * t), u = ratio * w / 0.903;
            double allocated = u <= 0.875 ? u : u >= 1.125 ? 1 : u - 2 * Math.pow(u - 0.875, 2);
            result += 2 * r * allocated / (ratio * 100000);
        }
        return result;
    }

    @Test void workstationBalanceHasFullOutputRegionAndBiomeAdvantage() {
        // 同位置实际分配核对初期设备、小产线与过量堆叠，覆盖新曲线的玩法目标。
        for(double capacity:new double[]{4096,6144}) {
            for(double ratio:new double[]{0.125,0.5,0.75}) {
                double demand=capacity*ratio;
                double out=DensityAllocator.solve(List.of(source(1,0.5,0.5,demand)),2,8,
                        (t,x,z)->capacity/(Math.PI*256)).outputs().get(1L);
                assertEquals(demand,out,demand*1e-9);
            }
        }
        var two=List.of(source(1,0.5,0.5,4096),source(2,1.5,0.5,4096));
        double ordinary=DensityAllocator.solve(two,2,8,(t,x,z)->6144/(Math.PI*256)).outputs().values().stream().mapToDouble(Double::doubleValue).sum();
        double rich=DensityAllocator.solve(two,2,8,(t,x,z)->12288/(Math.PI*256)).outputs().values().stream().mapToDouble(Double::doubleValue).sum();
        assertTrue(ordinary/8192>0.68 && ordinary/8192<0.75);
        assertEquals(8192,rich,8192e-9);
        assertTrue(rich>ordinary*1.4);
        for(double power:new double[]{2,4,8}) {
            double previous=0;
            for(int i=1;i<=1000;i++) {
                double demand=i/100d,output=demand*DensityKernel.fulfillment(demand,1,power);
                assertTrue(output>=previous-1e-12 && output<=1+1e-12);
                previous=output;
            }
        }
    }

    @Test void curveConvergenceAndNodeConservation() {
        for (int n : new int[]{1,2,4,8,16,32,64,128}) {
            var sources = new ArrayList<SourceSnapshot>();
            for (int i = 0; i < n; i++) sources.add(source(i, 0.5, 0.5, 1024));
            double reference = analytic(n / 8d) * 1024;
            var result = solve(sources, 2);
            assertEquals(reference, result.outputs().get(0L), reference * 0.01);
            assertEquals(solve(sources, 0.5).outputs().get(0L), result.outputs().get(0L), reference * 0.01);
            assertEquals(solve(sources, 1).outputs().get(0L), result.outputs().get(0L), reference * 0.01);
            result.nodes().values().forEach(node -> assertTrue(node.demand() * node.fulfillment() <= node.supply() * (1 + 1e-9)));
        }
    }

    @Test void normalizedDemandAndMixedPowerShares() {
        var sources = List.of(source(1, -0.5, -0.5, 1024), source(2, -0.5, -0.5, 4096));
        var result = solve(sources, 2);
        assertEquals(result.outputs().get(1L) * 4, result.outputs().get(2L), 1e-9);
        double demand = result.nodes().values().stream().mapToDouble(n -> n.demand() * 4).sum();
        assertEquals(5120, demand, 5120e-9);
        result.outputs().forEach((id, output) -> assertTrue(output >= 0 && output <= (id == 1 ? 1024 : 4096)));
    }

    @Test void translationAndMonotonicity() {
        double previousTotal = 0, previousEfficiency = 1;
        for (int n = 1; n <= 32; n++) {
            var list = new ArrayList<SourceSnapshot>();
            for (int i = 0; i < n; i++) list.add(source(i, -16.5, -0.5, 1024));
            var result = solve(list, 2);
            double total = result.outputs().values().stream().mapToDouble(Double::doubleValue).sum();
            double efficiency = result.outputs().get(0L) / 1024;
            assertTrue(total >= previousTotal && efficiency <= previousEfficiency);
            previousTotal = total; previousEfficiency = efficiency;
            var moved = list.stream().map(s -> source(s.id(), s.x()+1, s.z()+1, s.potential())).toList();
            assertEquals(result.outputs().get(0L), solve(moved, 2).outputs().get(0L), 1024 * 0.005);
        }
    }

    @Test void zeroExtremeIsolationAndDeterminism() {
        var sources = new ArrayList<>(List.of(source(1, 0.5, 0.5, 1024e6), source(2, 0.5, 0.5, 0),
                new SourceSnapshot(3, "wind", 0.5, 0.5, 1024, 16), source(4, 80.5, 0.5, 1024)));
        var result = solve(sources, 2);
        assertEquals(0, result.outputs().get(2L));
        assertEquals(result.outputs().get(3L), result.outputs().get(4L));
        assertTrue(result.outputs().get(1L) <= result.nodes().entrySet().stream()
                .filter(e -> e.getKey().type().equals("water") && e.getKey().x() < 16)
                .mapToDouble(e -> e.getValue().supply() * 4).sum());
        Collections.reverse(sources);
        assertEquals(result, solve(sources, 2));
        assertEquals(0, DensityAllocator.solve(sources, 2, 8, (t,x,z) -> 0).outputs().get(1L));
        assertEquals(0, DensityKernel.fulfillment(Double.MAX_VALUE, Double.MIN_VALUE, 4));
        assertThrows(IllegalArgumentException.class, () -> solve(sources, 5));
        assertThrows(IllegalArgumentException.class, () -> DensityKernel.fulfillment(1, Double.NaN, 4));
    }

    @Test void slicedBatchAndSpatialBoundaries() {
        var sources=List.of(source(1,-32.5,-0.5,1024),source(2,-1.5,-0.5,2048),source(3,0.5,-0.5,4096));
        var job=new DensityAllocator.Job(sources,2,8,(t,x,z)->RHO);
        assertThrows(IllegalStateException.class,job::result);
        int slices=0;while(!job.advance(1))slices++;
        assertTrue(slices>1);assertEquals(solve(sources,2),job.result());
        var index=new SourceSpatialIndex();sources.forEach(index::put);
        assertEquals(List.of(2L),index.overlapping(sources.get(0),16).stream().map(SourceSnapshot::id).toList());
        assertEquals(List.of(1L),index.covering("water",-32.5,-0.5,16).stream().map(SourceSnapshot::id).toList());
        index.remove(sources.get(1));assertTrue(index.overlapping(sources.get(0),16).isEmpty());
    }

    @Test void continuousMovementAndHighDensityAreaLimit() {
        double previous=-1;
        for(int i=0;i<=100;i++) {
            double x=31.5+i*0.01;
            double output=solve(List.of(source(1,0.5,0.5,8192),source(2,x,0.5,8192)),2).outputs().get(1L);
            if(previous>=0)assertTrue(Math.abs(output-previous)<8192*0.001);
            previous=output;
        }
        var result=solve(List.of(source(1,0.5,0.5,1024e9)),2);
        double limit=result.nodes().values().stream().mapToDouble(n->n.supply()*4).sum();
        assertTrue(result.outputs().get(1L)<=limit*(1+1e-9));
        assertTrue(result.outputs().get(1L)>limit*0.99);
        for(double radius:new double[]{2,4,32,64}) {
            var s=new SourceSnapshot(1,"water",-0.5,0.5,1024,radius);
            var r=DensityAllocator.solve(List.of(s),radius/8,8,(t,x,z)->1e9);
            assertEquals(1024,r.outputs().get(1L),1024e-9);
        }
    }

    @Test void nodeDifferencesMatchFullRebuildAcrossChanges() {
        var random=new Random(731);
        Map<Long,SourceSnapshot> previous=new HashMap<>();
        for(int i=0;i<60;i++)previous.put((long)i,source(i,random.nextInt(80)-40+0.5,random.nextInt(80)-40+0.5,256+random.nextInt(1024)));
        var baseline=solve(new ArrayList<>(previous.values()),2);
        Map<DensityAllocator.Node,DensityAllocator.NodeAllocation> nodes=new HashMap<>(baseline.nodes());
        Map<Long,Double> outputs=new HashMap<>(baseline.outputs());
        for(int iteration=0;iteration<20;iteration++) {
            Map<Long,SourceSnapshot> current=new HashMap<>(previous);
            long id=iteration;current.remove(id);
            if(iteration%2==0)current.put(id,source(id,random.nextInt(80)-40+0.5,random.nextInt(80)-40+0.5,512));
            var job=new IncrementalDensityJob(previous,current,nodes,outputs,2,8,(t,x,z)->RHO);
            while(!job.advance(1000)) { /* 验证多预算片结束后的差分结果。 */ }
            var delta=job.result();outputs.keySet().retainAll(current.keySet());outputs.putAll(delta.outputs());nodes.putAll(delta.nodes());
            var full=solve(new ArrayList<>(current.values()),2);
            for(var entry:full.outputs().entrySet())assertEquals(entry.getValue(),outputs.get(entry.getKey()),Math.max(1,entry.getValue())*1e-9);
            for(var entry:full.nodes().entrySet()) {
                var actual=nodes.get(entry.getKey());assertNotNull(actual);
                assertEquals(entry.getValue().demand(),actual.demand(),Math.max(1,entry.getValue().demand())*1e-9);
            }
            previous=current;
        }
    }
}
