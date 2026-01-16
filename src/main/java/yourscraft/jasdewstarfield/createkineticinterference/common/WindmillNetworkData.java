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
public class WindmillNetworkData extends SavedData {

    private static final String DATA_NAME = "windmill_interference_manager";

    private final Set<BlockPos> activeWindmills = new HashSet<>();

    public static WindmillNetworkData load(CompoundTag nbt, HolderLookup.Provider provider) {
        WindmillNetworkData data = new WindmillNetworkData();
        if (nbt.contains("ActiveWindmills")) {
            long[] array = nbt.getLongArray("ActiveWindmills");
            for (long val : array) {
                data.activeWindmills.add(BlockPos.of(val));
            }
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag compound, HolderLookup.@NotNull Provider provider) {
        long[] array = activeWindmills.stream().mapToLong(BlockPos::asLong).toArray();
        compound.putLongArray("ActiveWindmills", array);
        return compound;
    }

    /**
     * 获取当前维度的风车数据
     */
    public static WindmillNetworkData get(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        WindmillNetworkData::new,
                        WindmillNetworkData::load,
                        null
                ),
                DATA_NAME
        );
    }

    /**
     * 添加活跃风车
     */
    public void add(BlockPos pos) {
        if (activeWindmills.add(pos)) {
            setDirty();
        }
    }

    /**
     * 移除活跃风车
     */
    public void remove(BlockPos pos) {
        if (activeWindmills.remove(pos)) {
            setDirty();
        }
    }

    /**
     * 获取所有活跃风车坐标
     */
    public Set<BlockPos> getAll() {
        return activeWindmills;
    }
}
