package yourscraft.jasdewstarfield.createkineticinterference.common.density;

/** 不依赖 Minecraft 的不可变需求快照；id 对应位置，坐标为方块中心，potential 单位 SU。 */
public record SourceSnapshot(long id, String type, double x, double z, double potential, double radius) {
    public SourceSnapshot {
        if (type == null || !Double.isFinite(x) || !Double.isFinite(z)
                || !Double.isFinite(potential) || potential < 0 || !Double.isFinite(radius) || radius <= 0)
            throw new IllegalArgumentException("Invalid source snapshot");
    }
}
