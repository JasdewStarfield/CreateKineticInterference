package yourscraft.jasdewstarfield.createkineticinterference.common.density;

/** 全量与节点差分批次共用预算接口，完成前均不暴露新输出。 */
public interface DensityCalculation {
    boolean advance(long budgetNanos);
    DensityAllocator.Result result();
    long contributions();
    int allocatedNodes();
}
