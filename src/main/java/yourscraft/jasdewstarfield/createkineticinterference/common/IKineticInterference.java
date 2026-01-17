package yourscraft.jasdewstarfield.createkineticinterference.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Set;

public interface IKineticInterference {

    // --- 配置参数 ---
    float getEfficiencyFactor();
    void setEfficiencyFactor(float factor);
    DistanceType getDistanceType();

    // --- 核心数据存取 ---
    int getNearbyCount();
    void setNearbyCount(int count);

    boolean isTracked();
    void setTracked(boolean tracked);

    Set<BlockPos> getInterferenceSources();
    void setInterferenceSources(Set<BlockPos> sources);

    // --- 上下文获取 (由 BlockEntity 提供) ---
    Level getLevel();
    BlockPos getBlockPos();

    // --- 配置参数 (由具体实现提供) ---
    double getInterferenceRadius();
    double getInterferenceFactor();
    Set<BlockPos> getActivePeers();
    void trackSelf();
    void untrackSelf();
}
