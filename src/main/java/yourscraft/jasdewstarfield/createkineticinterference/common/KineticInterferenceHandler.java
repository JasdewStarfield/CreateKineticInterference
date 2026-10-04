package yourscraft.jasdewstarfield.createkineticinterference.common;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import yourscraft.jasdewstarfield.createkineticinterference.common.density.DensityDiagnostics;
import yourscraft.jasdewstarfield.createkineticinterference.common.density.DensityUpdateScheduler;
import net.minecraft.server.level.ServerLevel;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

public class KineticInterferenceHandler {

    // --- 数据同步逻辑 ---

    public static void write(IKineticInterference self, CompoundTag compound) {
        compound.putFloat("InterferenceEfficiency", self.getEfficiencyFactor());
        compound.putInt("InterferenceCount", self.getNearbyCount());
        var diagnostics=((DensityDiagnostics.View)self).cki$getDiagnostics();
        if(diagnostics.enabled() && self.getLevel() instanceof ServerLevel server) {
            // 包含正在进行的批次状态；提交保护内写出的包显示已完成。
            diagnostics=new DensityDiagnostics(true,diagnostics.type(),diagnostics.raw(),diagnostics.output(),diagnostics.localDensity(),
                    diagnostics.averageSupply(),diagnostics.radius(),diagnostics.sampleY(),diagnostics.estimatedSources(),
                    DensityUpdateScheduler.get(server).pending(),diagnostics.version());
        }
        compound.put("DensityDiagnostics", diagnostics.write());

        Set<BlockPos> sources = self.getInterferenceSources();
        // 空列表也发送，确保最后一个干扰源移除后客户端会清除旧高亮。
        compound.putLongArray("InterferenceSources", sources == null ? new long[0]
                : sources.stream().sorted(java.util.Comparator.comparingLong(BlockPos::asLong)).limit(64).mapToLong(BlockPos::asLong).toArray());
        compound.putBoolean("InterferenceSourcesTruncated", self.getNearbyCount() > 64);
    }

    public static void read(IKineticInterference self, CompoundTag compound) {
        ((DensityDiagnostics.View) self).cki$setDiagnostics(compound.contains("DensityDiagnostics")
                ? DensityDiagnostics.read(compound.getCompound("DensityDiagnostics")) : DensityDiagnostics.EMPTY);
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

        if (DensityUpdateScheduler.enabled(self)) {
            DensityUpdateScheduler.get((ServerLevel)self.getLevel()).observe((KineticBlockEntity)self);
            return;
        }

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
        if (DensityUpdateScheduler.enabled(self))
            DensityUpdateScheduler.get((ServerLevel)self.getLevel()).removed(self,isChunkUnloaded);
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
        if (DensityUpdateScheduler.enabled(self)) {
            DensityUpdateScheduler.get((ServerLevel)self.getLevel()).observe((KineticBlockEntity)be);
            return false;
        }

        // 1. 准备参数
        double radius = self.getInterferenceRadius();
        double factor = self.getInterferenceFactor();
        Set<BlockPos> currentSources = getInterferingBlockPos(self, radius);
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
        if (newEfficiency != self.getEfficiencyFactor()) {
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

    // 获取干扰源列表
    private static @NotNull Set<BlockPos> getInterferingBlockPos(IKineticInterference self, double radius) {
        double radiusSqr = radius * radius;
        BlockPos selfPos = self.getBlockPos();
        DistanceType distanceType = self.getDistanceType();

        // 扫描周围的活跃同类
        Set<BlockPos> currentSources = new HashSet<>();
        Set<BlockPos> activePositions = self.getActivePeers();

        for (BlockPos pos : activePositions) {
            // 跳过自身
            if (pos.equals(selfPos)) continue;
            // 距离检测
            boolean inRange;
            switch (distanceType) {
                case MANHATTAN_2D:
                    // 平面曼哈顿距离：|x1-x2| + |z1-z2|
                    double manhattanDist2D = Math.abs(pos.getX() - selfPos.getX())
                            + Math.abs(pos.getZ() - selfPos.getZ());
                    inRange = manhattanDist2D <= radius;
                    break;

                case MANHATTAN_3D:
                    // 曼哈顿距离：|x1-x2| + |y1-y2| + |z1-z2|
                    double manhattanDist = Math.abs(pos.getX() - selfPos.getX())
                            + Math.abs(pos.getY() - selfPos.getY())
                            + Math.abs(pos.getZ() - selfPos.getZ());
                    inRange = manhattanDist <= radius;
                    break;

                case EUCLIDEAN_2D:
                    // 平面距离：忽略 Y 轴，只计算 X 和 Z
                    double dX = pos.getX() - selfPos.getX();
                    double dZ = pos.getZ() - selfPos.getZ();
                    double distSqr2D = dX * dX + dZ * dZ;
                    inRange = distSqr2D <= radiusSqr;
                    break;

                case EUCLIDEAN_3D:
                default:
                    // 原版 3D 距离
                    inRange = pos.distSqr(selfPos) <= radiusSqr;
                    break;
            }

            if (inRange) {
                currentSources.add(pos);
            }
        }
        return currentSources;
    }
}
