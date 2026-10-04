package yourscraft.jasdewstarfield.createkineticinterference.common.density;

/** 有限二维覆盖核；返回值单位为每平方格，圆周处值和斜率均为零。 */
public final class DensityKernel {
    private DensityKernel() {}

    public static double weight(double dx, double dz, double radius) {
        double v = 1 - (dx * dx + dz * dz) / (radius * radius);
        return v <= 0 ? 0 : 3 * v * v / (Math.PI * radius * radius);
    }

    /** 大需求使用倒数形式，避免幂溢出；零供给不给任何保底产出。 */
    public static double fulfillment(double demand, double supply, double power) {
        if (!Double.isFinite(demand) || !Double.isFinite(supply) || demand < 0 || supply < 0
                || !Double.isFinite(power) || power < 2 || power > 8)
            throw new IllegalArgumentException("Invalid density or soft-cap power");
        if (demand == 0) return 1;
        if (supply == 0) return 0;
        if (demand <= supply) return Math.pow(1 + Math.pow(demand / supply, power), -1 / power);
        double inverse = supply / demand;
        return inverse * Math.pow(1 + Math.pow(inverse, power), -1 / power);
    }
}
