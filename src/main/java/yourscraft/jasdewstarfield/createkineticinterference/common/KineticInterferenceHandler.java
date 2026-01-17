package yourscraft.jasdewstarfield.createkineticinterference.common;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
import java.util.Set;

public class KineticInterferenceHandler {

    // --- 数据同步逻辑 ---

    public static void write(IKineticInterference self, CompoundTag compound) {
        compound.putFloat("InterferenceEfficiency", self.getEfficiencyFactor());
        compound.putInt("InterferenceCount", self.getNearbyCount());

        Set<BlockPos> sources = self.getInterferenceSources();
        if (sources != null && !sources.isEmpty()) {
            compound.putLongArray("InterferenceSources", sources.stream().mapToLong(BlockPos::asLong).toArray());
        }
    }

    public static void read(IKineticInterference self, CompoundTag compound) {
        if (compound.contains("InterferenceEfficiency")) {
            self.setEfficiencyFactor(compound.getFloat("InterferenceEfficiency"));
        }
        if (compound.contains("InterferenceCount")) {
            self.setNearbyCount(compound.getInt("InterferenceCount"));
        }
        if (compound.contains("InterferenceSources")) {
            Set<BlockPos> sources = new HashSet<>();
            long[] packed = compound.getLongArray("InterferenceSources");
            for (long p : packed) sources.add(BlockPos.of(p));
            self.setInterferenceSources(sources);
        }
    }

    // --- 状态追踪逻辑 ---

    /**
     * 更新当前方块在全局管理器中的追踪状态
     * @param isStressNonZero 当前方块是否产生应力（即是否在运行）
     */
    public static void updateTrackingState(IKineticInterference self, boolean isStressNonZero) {
        if (self.getLevel() == null || self.getLevel().isClientSide()) return;

        if (isStressNonZero != self.isTracked()) {
            BlockPos pos = self.getBlockPos();
            if (isStressNonZero) {
                self.trackSelf();
            } else {
                self.untrackSelf();
            }
            self.setTracked(isStressNonZero);
        }
    }

    /**
     * 方块实体移除或卸载时的清理逻辑
     * @param isChunkUnloaded 是否因区块卸载导致（如果是区块卸载，则不从全局数据中移除）
     */
    public static void invalidate(IKineticInterference self, boolean isChunkUnloaded) {
        if (self.getLevel() != null && !self.getLevel().isClientSide() && !isChunkUnloaded) {
            self.untrackSelf();
            self.setTracked(false);
        }
    }

    // --- 核心计算逻辑 ---

    /**
     * 执行干扰计算
     * @param self 干扰接口实例
     * @param be 方块实体本身 (用于 markDirty 和 sendData)
     * @return 如果效率发生显著变化需触发 updateGeneratedRotation 则返回 true，否则返回 false
     */
    public static boolean performCalculation(IKineticInterference self, BlockEntity be) {
        if (self.getLevel() == null || self.getLevel().isClientSide()) return false;

        // 1. 准备参数
        double radius = self.getInterferenceRadius();
        double factor = self.getInterferenceFactor();
        double radiusSqr = radius * radius;
        BlockPos selfPos = self.getBlockPos();

        // 2. 扫描周围的活跃同类
        Set<BlockPos> currentSources = new HashSet<>();
        Set<BlockPos> activePositions = self.getActivePeers();

        for (BlockPos pos : activePositions) {
            // 跳过自身
            if (pos.equals(selfPos)) continue;
            // 距离检测
            if (pos.distSqr(selfPos) <= radiusSqr) {
                currentSources.add(pos);
            }
        }

        boolean dataChanged = false;

        // 3. 更新干扰源列表 (元数据)
        // 即使效率没变，如果干扰源位置变了，也需要同步给客户端用于渲染高亮
        Set<BlockPos> oldSources = self.getInterferenceSources();
        if (!currentSources.equals(oldSources)) {
            self.setInterferenceSources(currentSources);
            self.setNearbyCount(currentSources.size());
            dataChanged = true;
        }

        // 4. 计算新效率
        // 公式: Efficiency = 1 / (1 + Count * Factor)
        float newEfficiency = (float) (1.0 / (1.0 + (currentSources.size() * factor)));

        boolean efficiencyChanged = false;
        // 检测效率是否发生显著变化 (> 0.1%)
        if (Math.abs(newEfficiency - self.getEfficiencyFactor()) > 0.001f) {
            self.setEfficiencyFactor(newEfficiency);
            efficiencyChanged = true;
            dataChanged = true; // 效率变了，肯定需要同步 NBT
        }

        // 5. 数据同步
        if (dataChanged) {
            be.setChanged();
            // 同步 NBT 到客户端
            if (be instanceof SmartBlockEntity smartBe) {
                smartBe.sendData();
            }
        }

        // 返回是否需要触发动力网络更新
        return efficiencyChanged;
    }
}
