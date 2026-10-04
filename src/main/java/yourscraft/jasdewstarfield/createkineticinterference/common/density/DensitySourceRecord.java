package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import net.minecraft.core.BlockPos;

/** 持久化最后已知需求；卸载记录继续竞争，estimated 表示旧坐标使用配置估计。 */
public record DensitySourceRecord(BlockPos pos, String resourceType, String sourceKind,
                                  double rawPotentialSU, long lastValidatedGameTime, String validationState) {
    public DensitySourceRecord {
        pos = pos.immutable();
        if ((!resourceType.equals("wind") && !resourceType.equals("water"))
                || !Double.isFinite(rawPotentialSU) || rawPotentialSU < 0)
            throw new IllegalArgumentException("Invalid saved density source at " + pos);
    }
    public SourceSnapshot snapshot(double radius) {
        return new SourceSnapshot(pos.asLong(),resourceType,pos.getX()+0.5,pos.getZ()+0.5,rawPotentialSU,radius);
    }
    public boolean estimated() { return validationState.equals("estimated"); }
}
