package yourscraft.jasdewstarfield.createkineticinterference.mixin;

import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
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
import yourscraft.jasdewstarfield.createkineticinterference.common.IWindmillInterference;
import yourscraft.jasdewstarfield.createkineticinterference.common.WindmillManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(WindmillBearingBlockEntity.class)
public class MixinWindmillBearingBlockEntity extends MechanicalBearingBlockEntity implements IWindmillInterference {

    public MixinWindmillBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private float custom$efficiencyFactor = 1.0f; // 默认为 100% 效率
    @Unique
    private int custom$nearbyWindmills = 0;
    @Unique
    private boolean custom$isTracked = false; //缓存
    @Unique
    private Set<BlockPos> custom$interferenceSources = new HashSet<>();

    // --- 实现接口方法 ---

    @Override
    public float createKineticInterference$getEfficiencyFactor() {
        return custom$efficiencyFactor;
    }

    @Override
    public void createKineticInterference$setEfficiencyFactor(float factor) {
        this.custom$efficiencyFactor = factor;
    }

    @Override
    public Set<BlockPos> createKineticInterference$getInterferenceSources() {
        return custom$interferenceSources;
    }

    @Override
    public void createKineticInterference$setInterferenceSources(Set<BlockPos> sources) {
        this.custom$interferenceSources = sources;
    }

    // --- 数据同步 ---

    /**
     * 将自定义数据写入 NBT
     */
    @Inject(method = "write", at = @At("RETURN"))
    private void injectWrite(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        compound.putFloat("InterferenceEfficiency", custom$efficiencyFactor);
        compound.putInt("InterferenceCount", custom$nearbyWindmills);

        if (!custom$interferenceSources.isEmpty()) {
            long[] packed = custom$interferenceSources.stream().mapToLong(BlockPos::asLong).toArray();
            compound.putLongArray("InterferenceSources", packed);
        }
    }

    /**
     * 从 NBT 读取自定义数据
     */
    @Inject(method = "read", at = @At("RETURN"))
    private void injectRead(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (compound.contains("InterferenceEfficiency")) {
            custom$efficiencyFactor = compound.getFloat("InterferenceEfficiency");
        }
        if (compound.contains("InterferenceCount")) {
            custom$nearbyWindmills = compound.getInt("InterferenceCount");
        }

        if (compound.contains("InterferenceSources")) {
            custom$interferenceSources.clear();
            long[] packed = compound.getLongArray("InterferenceSources");
            for (long p : packed) {
                custom$interferenceSources.add(BlockPos.of(p));
            }
        } else {
            custom$interferenceSources.clear();
        }
    }

    // --- 生命周期注入 ---

    /**
     * 判断当前风车是否应该被视为“活跃干扰源”
     */
    @Unique
    private boolean createKineticInterference$shouldBeTracked() {
        // 只有正在运行且产生了速度的风车才算
        return isRunning() && getGeneratedSpeed() != 0;
    }

    /**
     * 更新风车在全局数据中的状态
     */
    @Unique
    private void createKineticInterference$updateTrackingState() {
        if (level == null || level.isClientSide) return;

        boolean should = createKineticInterference$shouldBeTracked();
        if (should != custom$isTracked) {
            if (should) {
                WindmillManager.trackWindmill((WindmillBearingBlockEntity) (Object) this);
            } else {
                WindmillManager.untrackWindmill((WindmillBearingBlockEntity) (Object) this);
            }
            custom$isTracked = should;
        }
    }

    /**
     * 当 BE 被加载到世界时调用 (包括区块加载或放置)
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            createKineticInterference$updateTrackingState();
        }
    }

    /**
     * 当 BE 被移除时调用 (包括区块卸载或破坏)
     */
    @Override
    public void invalidate() {
        super.invalidate();
        if (level != null && !level.isClientSide && !this.isChunkUnloaded()) {
            WindmillManager.untrackWindmill((WindmillBearingBlockEntity) (Object) this);
            custom$isTracked = false;
        }
    }

    // --- 核心逻辑 ---

    /**
     * 风车状态更新（组装/拆卸/变向）时触发。
     * 这确保了：
     * 1. 刚组装的风车立即被注册到 Manager。
     * 2. 刚组装的风车立即计算一次效率，玩家无需等待。
     */
    @Inject(method = "updateGeneratedRotation", at = @At("HEAD"))
    private void onUpdateGeneratedRotation(CallbackInfo ci) {
        if (level == null || level.isClientSide) return;

        // 立即更新追踪状态
        createKineticInterference$updateTrackingState();

        // 如果处于运行状态，立即进行一次计算
        if (isRunning()) {
            createKineticInterference$performInterferenceCalculation();
        }
    }

    /**
     * 逻辑注入 1: 在 tick 中定期扫描周围
     * 我们每 100 tick (5秒) 扫描一次，错峰执行以减少卡顿
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void interferenceTick(CallbackInfo ci) {
        if (level == null || level.isClientSide) return;

        createKineticInterference$updateTrackingState();

        if (!isRunning()) return;

        // 错峰执行
        int checkInterval = CreatekineticinterferenceConfig.SERVER.checkInterval.get();
        if ((level.getGameTime() + this.worldPosition.hashCode()) % checkInterval != 0) {
            return;
        }

        createKineticInterference$performInterferenceCalculation();
    }

    /**
     * 扫描逻辑：查找周围的风车并更新效率
     */
    @Unique
    private void createKineticInterference$performInterferenceCalculation() {
        double interferenceRadius = CreatekineticinterferenceConfig.SERVER.interferenceRadius.get();
        double interferenceFactor = CreatekineticinterferenceConfig.SERVER.interferenceFactor.get();
        double radiusSqr = interferenceRadius * interferenceRadius;

        Set<BlockPos> currentSources = new HashSet<>();
        Set<BlockPos> activePositions = WindmillManager.getWindmillsInLevel(this.level);

        for (BlockPos pos : activePositions) {
            if (pos.equals(this.worldPosition)) continue;

            if (pos.distSqr(this.worldPosition) <= radiusSqr) {
                currentSources.add(pos);
            }
        }

        int nearbyCount = currentSources.size();

        // 检查数据是否发生变化
        boolean changed = false;
        if (!currentSources.equals(this.custom$interferenceSources)) {
            this.custom$interferenceSources = currentSources;
            this.custom$nearbyWindmills = nearbyCount;
            changed = true;
        }

        // 反比公式：1.0 / (1.0 + count * factor)
        float newEfficiency = (float) (1.0 / (1.0 + (nearbyCount * interferenceFactor)));

        if (Math.abs(newEfficiency - createKineticInterference$getEfficiencyFactor()) > 0.001f) {
            createKineticInterference$setEfficiencyFactor(newEfficiency);
            this.updateGeneratedRotation();
        }

        // 如果数据变了，通知客户端同步
        if (changed) {
            notifyUpdate();
            sendData();
        }
    }

    /**
     * 修改 calculateAddedStressCapacity
     * 在返回结果前乘上效率系数
     */
    @Override
    public float calculateAddedStressCapacity() {
        float originalCapacity = super.calculateAddedStressCapacity();
        return originalCapacity * createKineticInterference$getEfficiencyFactor();
    }

    /**
     * 新增：护目镜提示信息
     * 因为 WindmillBearingBlockEntity 没有重写这个方法，我们在 Mixin 中重写它
     * 实际上是覆盖了父类 MechanicalBearingBlockEntity 的逻辑，所以需要调用 super
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 先调用父类逻辑（显示应力容量等基础信息）
        boolean success = super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        // 只有当存在干扰或效率不为 100% 时才显示
        if (this.isRunning() && custom$efficiencyFactor < 1.0f) {
            // 运行效率
            CreateLang.text("  ")
                    .add(CreateLang.translate("hint.interference_efficiency").style(ChatFormatting.DARK_GRAY))
                    .add(CreateLang.number(custom$efficiencyFactor * 100).text("%").style(ChatFormatting.RED))
                    .forGoggles(tooltip);

            // 干扰源
            CreateLang.text("  ")
                    .add(CreateLang.translate("hint.interference_source_pre").style(ChatFormatting.DARK_GRAY))
                    .add(CreateLang.number(custom$nearbyWindmills).style(ChatFormatting.GOLD))
                    .add(CreateLang.translate("hint.interference_source").style(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip);

            if (isPlayerSneaking) {
                CreateLang.text("  ")
                        .add(CreateLang.translate("hint.interference_hint_pre").style(ChatFormatting.DARK_GRAY))
                        .add(CreateLang.number(CreatekineticinterferenceConfig.SERVER.interferenceRadius.get()).style(ChatFormatting.GOLD))
                        .add(CreateLang.translate("hint.interference_hint").style(ChatFormatting.DARK_GRAY))
                        .forGoggles(tooltip);
            }
        }

        return success;
    }
}
