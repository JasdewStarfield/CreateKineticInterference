package yourscraft.jasdewstarfield.createkineticinterference.common.density;

/** 有限二维覆盖核；返回值单位为每平方格，圆周处值和斜率均为零。 */
public final class DensityKernel {
    private DensityKernel() {}

    public static double weight(double dx, double dz, double radius) {
        double distanceSquared = dx * dx + dz * dz, radiusSquared = radius * radius;
        if (distanceSquared >= radiusSquared) return 0;
        // 高频节点查询在平坦中心直接返回，仅边缘需要开方。
        double centerWeight = 1 / (0.903 * Math.PI * radiusSquared);
        if (distanceSquared <= 0.81 * radiusSquared) return centerWeight;
        double r = Math.sqrt(distanceSquared / radiusSquared);
        // 90% 半径内均匀采集，外圈用 smoothstep 收边；0.903 是连续圆积分的面积系数。
        double t = Math.max(0, (r - 0.9) / 0.1);
        return (1 - t * t * (3 - 2 * t)) * centerWeight;
    }

    /** 群系卷积保留宽缓旧核，使采集核平衡调整不改变环境平滑范围。 */
    public static double environmentWeight(double dx, double dz, double radius) {
        double v = 1 - (dx * dx + dz * dz) / (radius * radius);
        return v <= 0 ? 0 : 3 * v * v / (Math.PI * radius * radius);
    }

    /** 低需求满效，供给上限附近用 C1 连续抛物线收敛；饱和后分配严格等于供给。 */
    public static double fulfillment(double demand, double supply, double power) {
        if (!Double.isFinite(demand) || !Double.isFinite(supply) || demand < 0 || supply < 0
                || !Double.isFinite(power) || power < 2 || power > 8)
            throw new IllegalArgumentException("Invalid density or soft-cap power");
        if (demand == 0) return 1;
        if (supply == 0) return 0;
        double inverse = supply / demand;
        double width = 2 / power, start = 1 - 1 / power, end = 1 + 1 / power;
        // 先比较密度本身，极大 demand/supply 的比值不会进入过渡段而溢出。
        if (inverse >= 1 / start) return 1;
        if (inverse <= 1 / end) return inverse;
        double ratio = demand / supply, t = (ratio - start) / width;
        return (ratio - width * t * t / 2) / ratio;
    }
}
