package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** 固定高度的群系源查询不访问区块；卷积后双线性插值使供给跨群系边界连续。 */
public final class DensityFieldService {
    private record Node(String type, int x, int z) {}
    private final ServerLevel level;
    private final Map<Node, Double> smoothed = boundedCache();
    private final Map<Node, Double> raw = boundedCache();
    private static Map<Node,Double> boundedCache() {
        // 历史建设范围不让环境节点永久常驻；被淘汰的节点可无区块加载地重新查询。
        return new LinkedHashMap<>(1024,0.75f,true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Node,Double> entry) { return size()>65536; }
        };
    }
    private final Map<String, DensityProfiles.Profile> profiles;
    private final Map<String, Double> density;
    private final double step, blend;
    private final int y;

    public DensityFieldService(ServerLevel level, Map<String, DensityProfiles.Profile> profiles,
                               Map<String, Double> density, double step, double blend, int sampleY) {
        this.level = level; this.profiles = Map.copyOf(profiles); this.density = Map.copyOf(density);
        this.step = step; this.blend = blend;
        this.y = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight()-1, sampleY));
    }
    public int sampleY() { return y; }
    public int cachedNodes() { return smoothed.size(); }
    public double at(String type, double x, double z) {
        int ix = (int) Math.floor(x / step), iz = (int) Math.floor(z / step);
        double fx = x / step - ix, fz = z / step - iz;
        return density.get(type) * profiles.get(type).dimensions().getOrDefault(level.dimension().location(),1d)
                * ((1-fz)*((1-fx)*smooth(type,ix,iz)+fx*smooth(type,ix+1,iz))
                + fz*((1-fx)*smooth(type,ix,iz+1)+fx*smooth(type,ix+1,iz+1)));
    }
    private double smooth(String type, int x, int z) {
        return smoothed.computeIfAbsent(new Node(type,x,z), node -> {
            if (blend == 0) return raw(type,x,z);
            int reach = (int) Math.ceil(blend / step);
            double sum = 0, weights = 0;
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
                double weight = DensityKernel.environmentWeight(dx*step,dz*step,blend);
                if (weight == 0) continue;
                sum += raw(type,x+dx,z+dz)*weight; weights += weight;
            }
            return sum / weights;
        });
    }
    private double raw(String type, int x, int z) {
        return raw.computeIfAbsent(new Node(type,x,z), node -> {
            var chunks = level.getChunkSource();
            // 直接问噪声群系源，避免 getBiome 的区块获取路径。固定列不随设备 Y 改变。
            var biome = chunks.getGenerator().getBiomeSource().getNoiseBiome(
                    QuartPos.fromBlock((int) Math.round(x*step)), QuartPos.fromBlock(y),
                    QuartPos.fromBlock((int) Math.round(z*step)), chunks.randomState().sampler());
            return profiles.get(type).multiplier(biome);
        });
    }
}
