package yourscraft.jasdewstarfield.createkineticinterference.mixin;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yourscraft.jasdewstarfield.createkineticinterference.CreatekineticinterferenceConfig;
import yourscraft.jasdewstarfield.createkineticinterference.common.DistanceType;
import yourscraft.jasdewstarfield.createkineticinterference.common.IKineticInterference;
import yourscraft.jasdewstarfield.createkineticinterference.common.KineticInterferenceHandler;
import yourscraft.jasdewstarfield.createkineticinterference.common.KineticInterferenceManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(WaterWheelBlockEntity.class)
public abstract class MixinWaterwheelBlockEntity extends GeneratingKineticBlockEntity implements IKineticInterference {
    public MixinWaterwheelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private float custom$efficiencyFactor = 1.0f; // 默认为 100% 效率
    @Unique
    private int custom$nearbyCount = 0;
    @Unique
    private boolean custom$isTracked = false; //缓存
    @Unique
    private Set<BlockPos> custom$interferenceSources = new HashSet<>();

    // --- 实现接口方法 ---

    // --- 基础 Getter/Setter ---
    @Override public float getEfficiencyFactor() { return custom$efficiencyFactor; }
    @Override public void setEfficiencyFactor(float factor) { this.custom$efficiencyFactor = factor; }
    @Override public DistanceType getDistanceType() { return CreatekineticinterferenceConfig.SERVER.waterwheelDistanceType.get(); }
    @Override public int getNearbyCount() { return custom$nearbyCount; }
    @Override public void setNearbyCount(int count) { this.custom$nearbyCount = count; }
    @Override public boolean isTracked() { return custom$isTracked; }
    @Override public void setTracked(boolean tracked) { this.custom$isTracked = tracked; }
    @Override public Set<BlockPos> getInterferenceSources() { return custom$interferenceSources; }
    @Override public void setInterferenceSources(Set<BlockPos> sources) { this.custom$interferenceSources = sources; }

    // 参数配置
    @Override public double getInterferenceRadius() { return CreatekineticinterferenceConfig.SERVER.waterwheelInterferenceRadius.get(); }
    @Override public double getInterferenceFactor() { return CreatekineticinterferenceConfig.SERVER.waterwheelInterferenceFactor.get(); }
    @Override public Set<BlockPos> getActivePeers() { return KineticInterferenceManager.getWaterWheelsInLevel(this.level); }
    @Override public void trackSelf() { KineticInterferenceManager.trackWaterWheel((WaterWheelBlockEntity) (Object)this); }
    @Override public void untrackSelf() { KineticInterferenceManager.untrackWaterWheel((WaterWheelBlockEntity)(Object)this); }

    // --- 数据同步 ---

    /**
     * 将自定义数据写入 NBT
     */
    @Inject(method = "write", at = @At("RETURN"))
    private void injectWrite(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        KineticInterferenceHandler.write(this, compound);
    }

    /**
     * 从 NBT 读取自定义数据
     */
    @Inject(method = "read", at = @At("RETURN"))
    private void injectRead(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        KineticInterferenceHandler.read(this, compound);
    }

    // --- 生命周期注入 ---

    @Unique
    private boolean isActiveSource() {
        return Math.abs(getGeneratedSpeed()) > 0;
    }

    /**
     * 当 BE 被加载到世界时调用 (包括区块加载或放置)
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            boolean active = isActiveSource();
            if (active) {
                KineticInterferenceHandler.updateTrackingState(this, true);
            } else {
                untrackSelf();
                setTracked(false);
            }
        }
    }

    /**
     * 当 BE 被移除时调用
     */
    @Override
    public void invalidate() {
        super.invalidate();
        KineticInterferenceHandler.invalidate(this, this.isChunkUnloaded());
    }

    // --- 核心逻辑 ---

    /**
     * 在 lazyTick 中定期扫描周围
     * Create 已对此进行过优化
     */
    @Inject(method = "lazyTick", at = @At("RETURN"))
    private void onLazyTick(CallbackInfo ci) {
        if (level == null || level.isClientSide) return;

        KineticInterferenceHandler.updateTrackingState(this, isActiveSource());

        if (getGeneratedSpeed() == 0) return;

        if (KineticInterferenceHandler.performCalculation(this, this)) {
            this.updateGeneratedRotation();
        }
    }

    /**
     * 修改 calculateAddedStressCapacity
     * 在返回结果前乘上效率系数
     */
    @Override
    public float calculateAddedStressCapacity() {
        return super.calculateAddedStressCapacity() * getEfficiencyFactor();
    }

    /**
     * 护目镜提示信息
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 先调用父类逻辑（显示应力容量等基础信息）
        boolean success = super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        // 只有当在运行且效率不为 100% 时才显示
        if (Math.abs(getGeneratedSpeed()) > 0 && custom$efficiencyFactor < 1.0f) {
            // 运行效率
            CreateLang.text("  ")
                    .add(CreateLang.translate("hint.interference_efficiency").style(ChatFormatting.DARK_GRAY))
                    .add(CreateLang.number(custom$efficiencyFactor * 100).text("%").style(ChatFormatting.RED))
                    .forGoggles(tooltip);

            // 干扰源
            CreateLang.text("  ")
                    .add(CreateLang.translate("hint.waterwheel.interference_source_pre").style(ChatFormatting.DARK_GRAY))
                    .add(CreateLang.number(custom$nearbyCount).style(ChatFormatting.GOLD))
                    .add(CreateLang.translate("hint.waterwheel.interference_source").style(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip);

            if (isPlayerSneaking) {
                CreateLang.text("  ")
                        .add(CreateLang.translate("hint.waterwheel.interference_hint_pre").style(ChatFormatting.DARK_GRAY))
                        .add(CreateLang.number(CreatekineticinterferenceConfig.SERVER.waterwheelInterferenceRadius.get()).style(ChatFormatting.GOLD))
                        .add(CreateLang.translate("hint.waterwheel.interference_hint").style(ChatFormatting.DARK_GRAY))
                        .forGoggles(tooltip);
            }
        }

        return success;
    }
}
