package yourscraft.jasdewstarfield.createkineticinterference.common;

import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import yourscraft.jasdewstarfield.createkineticinterference.common.density.DensitySourceRecord;

/**
 * 世界保存数据：存储所有活跃风车的坐标
 * 数据会随存档保存，且不会因为区块卸载而丢失。
 */
public class InterferenceNetworkData extends SavedData {

    private static final String DATA_NAME = "kinetic_interference_manager";

    private final Set<BlockPos> activeWindmills = new HashSet<>();
    private final Set<BlockPos> activeWaterWheels = new HashSet<>();
    private final Map<BlockPos, DensitySourceRecord> densitySources = new HashMap<>();
    private long densityRevision;

    // 同一 tick 内多个动力源读取时只校验一次，避免反复查询同一批方块实体。
    private long lastWindmillValidation = Long.MIN_VALUE;
    private long lastWaterWheelValidation = Long.MIN_VALUE;

    public static InterferenceNetworkData load(CompoundTag nbt, HolderLookup.Provider provider) {
        InterferenceNetworkData data = new InterferenceNetworkData();

        if (nbt.contains("ActiveWindmills")) {
            long[] array = nbt.getLongArray("ActiveWindmills");
            for (long val : array) data.activeWindmills.add(BlockPos.of(val));
        }

        if (nbt.contains("ActiveWaterWheels")) {
            long[] array = nbt.getLongArray("ActiveWaterWheels");
            for (long val : array) data.activeWaterWheels.add(BlockPos.of(val));
        }
        for (var element : nbt.getList("DensitySources", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) element;
            var record = new DensitySourceRecord(BlockPos.of(entry.getLong("pos")),entry.getString("resourceType"),
                    entry.getString("sourceKind"),entry.getDouble("rawPotentialSU"),
                    entry.getLong("lastValidatedGameTime"),entry.getString("validationState"));
            data.densitySources.put(record.pos(),record);
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag compound, HolderLookup.@NotNull Provider provider) {
        compound.putLongArray("ActiveWindmills", activeWindmills.stream().mapToLong(BlockPos::asLong).toArray());
        compound.putLongArray("ActiveWaterWheels", activeWaterWheels.stream().mapToLong(BlockPos::asLong).toArray());
        compound.putInt("schemaVersion",2);
        compound.putInt("modelVersion",2);
        var records = new ListTag();
        densitySources.values().stream().sorted(java.util.Comparator.comparingLong(r -> r.pos().asLong())).forEach(record -> {
            var entry = new CompoundTag();
            entry.putLong("pos",record.pos().asLong()); entry.putString("resourceType",record.resourceType());
            entry.putString("sourceKind",record.sourceKind()); entry.putDouble("rawPotentialSU",record.rawPotentialSU());
            entry.putLong("lastValidatedGameTime",record.lastValidatedGameTime());
            entry.putString("validationState",record.validationState()); records.add(entry);
        });
        compound.put("DensitySources",records);
        return compound;
    }

    /**
     * 获取当前维度的风车数据
     */
    public static InterferenceNetworkData get(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        InterferenceNetworkData::new,
                        InterferenceNetworkData::load,
                        null
                ),
                DATA_NAME
        );
    }

    // --- 风车操作 ---
    public void addWindmill(BlockPos pos) {
        if (activeWindmills.add(pos)) setDirty();
    }
    public void removeWindmill(BlockPos pos) {
        if (activeWindmills.remove(pos)) setDirty();
        removeDensitySource(pos);
    }
    public Set<BlockPos> getWindmills() { return activeWindmills; }

    // --- 水车操作 ---
    public void addWaterWheel(BlockPos pos) {
        if (activeWaterWheels.add(pos)) setDirty();
    }
    public void removeWaterWheel(BlockPos pos) {
        if (activeWaterWheels.remove(pos)) setDirty();
        removeDensitySource(pos);
    }
    public Set<BlockPos> getWaterWheels() { return activeWaterWheels; }

    /** 仅需求变化推进计算版本；时间戳更新可保存，但不重复求解相同布局。 */
    public void putDensitySource(DensitySourceRecord record) {
        var old = densitySources.put(record.pos(),record);
        if (old == null || old.rawPotentialSU() != record.rawPotentialSU()
                || !old.resourceType().equals(record.resourceType()) || old.estimated() != record.estimated()) densityRevision++;
        if (!record.equals(old)) setDirty();
        if (record.resourceType().equals("wind")) addWindmill(record.pos()); else addWaterWheel(record.pos());
    }
    public void removeDensitySource(BlockPos pos) {
        if (densitySources.remove(pos) != null) { densityRevision++; setDirty(); }
    }
    public Map<BlockPos,DensitySourceRecord> getDensitySources() { return Collections.unmodifiableMap(densitySources); }
    public long densityRevision() { return densityRevision; }

    /** 逐步修复旧版本遗留的幽灵坐标，绝不为了校验而加载区块。 */
    private void pruneMissingSources(Level level, Set<BlockPos> positions, Class<?> expectedType) {
        boolean changed = positions.removeIf(pos -> {
            boolean missing = level.hasChunkAt(pos) && !expectedType.isInstance(level.getBlockEntity(pos));
            if (missing) removeDensitySource(pos);
            return missing;
        });
        if (changed) setDirty();
    }

    /** 未加载区块的记录保留，已加载区块中被移除或替换的风车记录才删除。 */
    public Set<BlockPos> getValidatedWindmills(Level level) {
        if (lastWindmillValidation != level.getGameTime()) {
            lastWindmillValidation = level.getGameTime();
            pruneMissingSources(level, activeWindmills, WindmillBearingBlockEntity.class);
        }
        return activeWindmills;
    }

    /** 同时适用于小水车和继承 WaterWheelBlockEntity 的大水车。 */
    public Set<BlockPos> getValidatedWaterWheels(Level level) {
        if (lastWaterWheelValidation != level.getGameTime()) {
            lastWaterWheelValidation = level.getGameTime();
            pruneMissingSources(level, activeWaterWheels, WaterWheelBlockEntity.class);
        }
        return activeWaterWheels;
    }
}
