package yourscraft.jasdewstarfield.createkineticinterference.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * 世界保存数据：存储所有活跃风车的坐标
 * 数据会随存档保存，且不会因为区块卸载而丢失。
 */
public class InterferenceNetworkData extends SavedData {

    private static final String DATA_NAME = "kinetic_interference_manager";

    private final Set<BlockPos> activeWindmills = new HashSet<>();
    private final Set<BlockPos> activeWaterWheels = new HashSet<>();

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
        return data;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag compound, HolderLookup.@NotNull Provider provider) {
        compound.putLongArray("ActiveWindmills", activeWindmills.stream().mapToLong(BlockPos::asLong).toArray());
        compound.putLongArray("ActiveWaterWheels", activeWaterWheels.stream().mapToLong(BlockPos::asLong).toArray());
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
    }
    public Set<BlockPos> getWindmills() { return activeWindmills; }

    // --- 水车操作 ---
    public void addWaterWheel(BlockPos pos) {
        if (activeWaterWheels.add(pos)) setDirty();
    }
    public void removeWaterWheel(BlockPos pos) {
        if (activeWaterWheels.remove(pos)) setDirty();
    }
    public Set<BlockPos> getWaterWheels() { return activeWaterWheels; }
}
