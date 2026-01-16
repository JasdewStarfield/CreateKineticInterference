package yourscraft.jasdewstarfield.createkineticinterference.common;

import net.minecraft.core.BlockPos;

import java.util.Set;

public interface IWindmillInterference {
    /**
     * 获取当前风车的效率系数 (0.0 ~ 1.0)
     */
    float createKineticInterference$getEfficiencyFactor();

    /**
     * 设置当前风车的效率系数
     */
    void createKineticInterference$setEfficiencyFactor(float factor);

    /**
     * 获取当前风车受到的所有干扰源坐标
     */
    Set<BlockPos> createKineticInterference$getInterferenceSources();

    /**
     * 设置干扰源坐标
     */
    void createKineticInterference$setInterferenceSources(Set<BlockPos> sources);
}
