package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import java.util.*;

/** XZ 分桶只筛选候选；实际竞争由二维圆相交判断，高度不改变供给范围。 */
public final class SourceSpatialIndex {
    private record Bucket(String type, int x, int z) {}
    private final Map<Bucket, Map<Long, SourceSnapshot>> buckets = new HashMap<>();
    private static final int SIZE = 32;
    private Bucket bucket(SourceSnapshot source) {
        return new Bucket(source.type(), (int) Math.floor(source.x() / SIZE), (int) Math.floor(source.z() / SIZE));
    }
    public void put(SourceSnapshot source) {
        buckets.computeIfAbsent(bucket(source), key -> new TreeMap<>()).put(source.id(), source);
    }
    public void remove(SourceSnapshot source) {
        var entries = buckets.get(bucket(source));
        if (entries != null) {
            entries.remove(source.id());
            if (entries.isEmpty()) buckets.remove(bucket(source));
        }
    }
    public List<SourceSnapshot> overlapping(SourceSnapshot center, double maximumRadius) {
        var result = new ArrayList<SourceSnapshot>();
        double range = center.radius() + maximumRadius;
        for (int x = (int) Math.floor((center.x()-range)/SIZE); x <= (int) Math.floor((center.x()+range)/SIZE); x++)
            for (int z = (int) Math.floor((center.z()-range)/SIZE); z <= (int) Math.floor((center.z()+range)/SIZE); z++) {
                var entries = buckets.get(new Bucket(center.type(), x, z));
                if (entries == null) continue;
                for (var other : entries.values()) {
                    double dx = other.x()-center.x(), dz = other.z()-center.z();
                    if (other.id() != center.id() && dx*dx+dz*dz < Math.pow(center.radius()+other.radius(), 2)) result.add(other);
                }
            }
        return result.stream().sorted(Comparator.comparingDouble((SourceSnapshot s) ->
                Math.hypot(s.x()-center.x(), s.z()-center.z())).thenComparingLong(SourceSnapshot::id)).toList();
    }
    public List<SourceSnapshot> covering(String type,double x,double z,double maximumRadius) {
        var result = new ArrayList<SourceSnapshot>();
        for (int bx=(int)Math.floor((x-maximumRadius)/SIZE);bx<=(int)Math.floor((x+maximumRadius)/SIZE);bx++)
            for (int bz=(int)Math.floor((z-maximumRadius)/SIZE);bz<=(int)Math.floor((z+maximumRadius)/SIZE);bz++) {
                var entries=buckets.get(new Bucket(type,bx,bz)); if (entries==null) continue;
                for (var source:entries.values()) {
                    double dx=source.x()-x,dz=source.z()-z;
                    if(dx*dx+dz*dz<source.radius()*source.radius())result.add(source);
                }
            }
        // 桶按坐标、桶内按 id 遍历，保证确定性，同时避免每个积分节点重复排序。
        return result;
    }
}
