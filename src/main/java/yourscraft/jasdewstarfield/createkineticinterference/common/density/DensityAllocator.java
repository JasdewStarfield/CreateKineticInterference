package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import java.util.*;

/** 所有设备共享世界网格；先归一化各源，再按节点共同分配，保证离散供给上限。 */
public final class DensityAllocator {
    public record Node(String type, int x, int z) {}
    public record NodeAllocation(double demand, double supply, double fulfillment) {}
    public record Result(Map<Long, Double> outputs, Map<Node, NodeAllocation> nodes, long contributions) {}
    @FunctionalInterface public interface Supply { double at(String type, double x, double z); }
    private record Contribution(long id, double density) {}

    public static Result solve(Collection<SourceSnapshot> input, double step, double power, Supply supply) {
        Job job = new Job(input,step,power,supply);
        while (!job.advance(Long.MAX_VALUE)) { /* 同步参考与分帧计算共用同一路径。 */ }
        return job.result();
    }

    /** 每次推进一源或一节点；结束前不暴露部分结果，便于服务端原子提交。 */
    public static final class Job implements DensityCalculation {
        private final List<SourceSnapshot> sources;
        private final double step, power, area;
        private final Supply supply;
        private final Map<Node,List<Contribution>> grid = new TreeMap<>(Comparator.comparing(Node::type)
                .thenComparingInt(Node::x).thenComparingInt(Node::z));
        private final Map<Long,Double> outputs = new LinkedHashMap<>();
        private final Map<Node,NodeAllocation> diagnostics = new HashMap<>();
        private Iterator<Map.Entry<Node,List<Contribution>>> nodes;
        private int sourceIndex;
        private long contributions;
        private boolean complete;
        public Job(Collection<SourceSnapshot> input,double step,double power,Supply supply) {
            if (!Double.isFinite(step) || step <= 0) throw new IllegalArgumentException("Invalid integration step");
            DensityKernel.fulfillment(0,0,power);
            this.sources = input.stream().sorted(Comparator.comparingLong(SourceSnapshot::id)).toList();
            this.step = step; this.power = power; this.supply = supply; this.area = step*step;
        }
        public boolean advance(long budgetNanos) {
            if (complete) return true;
            if (budgetNanos <= 0) throw new IllegalArgumentException("Invalid work budget");
            long start = System.nanoTime();
            do {
                if (sourceIndex < sources.size()) project(sources.get(sourceIndex++));
                else {
                    if (nodes == null) nodes = grid.entrySet().iterator();
                    if (!nodes.hasNext()) { complete = true; return true; }
                    allocate(nodes.next());
                }
            } while (System.nanoTime()-start < budgetNanos);
            return false;
        }
        private void project(SourceSnapshot source) {
            if (outputs.put(source.id(), 0d) != null) throw new IllegalArgumentException("Duplicate source id");
            if (step > source.radius() / 4 || Math.pow(2 * source.radius() / step + 2, 2) > 65536)
                throw new IllegalArgumentException("Integration step must be <= R/4 and <= 65536 samples/source");
            if (source.potential() == 0) return;
            // 固定世界节点，不随设备平移；归一化因子把每源离散需求严格还原为 P。
            Map<Node, Double> weights = new LinkedHashMap<>();
            double normalization = 0;
            int minX = (int) Math.ceil((source.x() - source.radius()) / step);
            int maxX = (int) Math.floor((source.x() + source.radius()) / step);
            int minZ = (int) Math.ceil((source.z() - source.radius()) / step);
            int maxZ = (int) Math.floor((source.z() + source.radius()) / step);
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                double weight = DensityKernel.weight(x * step - source.x(), z * step - source.z(), source.radius());
                if (weight == 0) continue;
                weights.put(new Node(source.type(), x, z), weight);
                normalization += weight * area;
            }
            if (!(normalization > 0)) throw new IllegalArgumentException("Empty integration support");
            for (var entry : weights.entrySet()) {
                grid.computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
                        .add(new Contribution(source.id(), source.potential() * entry.getValue() / normalization));
                contributions++;
            }
        }
        private void allocate(Map.Entry<Node,List<Contribution>> nodeEntry) {
            var node = nodeEntry.getKey(); var entries = nodeEntry.getValue();
            double demand = entries.stream().mapToDouble(Contribution::density).sum();
            double rho = supply.at(node.type(), node.x() * step, node.z() * step);
            double fulfillment = DensityKernel.fulfillment(demand, rho, power);
            diagnostics.put(node, new NodeAllocation(demand, rho, fulfillment));
            for (var entry : entries) outputs.merge(entry.id(), entry.density() * fulfillment * area, Double::sum);
        }
        public int projectedSources() { return sourceIndex; }
        public int allocatedNodes() { return diagnostics.size(); }
        public long contributions() { return contributions; }
        public Result result() {
            if (!complete) throw new IllegalStateException("Incomplete batch");
            // 完成后不再修改，包装视图避免提交瞬间复制大型网格。
            return new Result(Collections.unmodifiableMap(outputs),Collections.unmodifiableMap(diagnostics),contributions);
        }
        /** 完成后移交节点缓存的所有权；调度器可原地应用下一批差分。 */
        Map<Node,NodeAllocation> adoptNodes() { result(); return diagnostics; }
    }
}
