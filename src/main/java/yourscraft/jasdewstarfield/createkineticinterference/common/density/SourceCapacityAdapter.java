package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

/** 原始采样只跳过 CKI，保留附属 super 返回后的倍率；缓存通过 finally 恢复。 */
public final class SourceCapacityAdapter {
    public interface CapacityCache {
        float cki$getLastCapacity();
        void cki$setLastCapacity(float value);
    }
    private static final ThreadLocal<Integer> RAW_DEPTH = ThreadLocal.withInitial(() -> 0);
    public static boolean sampling() { return RAW_DEPTH.get() > 0; }

    public static double potential(KineticBlockEntity source) {
        var cache = (CapacityCache) source;
        float saved = cache.cki$getLastCapacity();
        int depth = RAW_DEPTH.get();
        RAW_DEPTH.set(depth + 1);
        try {
            double potential = (double) source.calculateAddedStressCapacity() * Math.abs(source.getGeneratedSpeed());
            if (!Double.isFinite(potential) || potential < 0)
                throw new IllegalStateException("Invalid raw potential at " + source.getBlockPos());
            return potential;
        } finally {
            cache.cki$setLastCapacity(saved);
            if (depth == 0) RAW_DEPTH.remove(); else RAW_DEPTH.set(depth);
        }
    }
}
