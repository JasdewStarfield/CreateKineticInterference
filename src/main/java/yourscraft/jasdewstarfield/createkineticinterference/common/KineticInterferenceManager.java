package yourscraft.jasdewstarfield.createkineticinterference.common;

import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Set;

/**
 * 全局风车管理器
 * 负责追踪当前世界中所有已加载的风车或水车方块实体。
 */
public class KineticInterferenceManager {

    /**
     * 注册风车
     */
    public static void trackWindmill(WindmillBearingBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) return;
        InterferenceNetworkData data = InterferenceNetworkData.get(level);
        if (data != null) data.addWindmill(be.getBlockPos());
    }

    /**
     * 注销风车
     */
    public static void untrackWindmill(WindmillBearingBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) return;
        InterferenceNetworkData data = InterferenceNetworkData.get(level);
        if (data != null) data.removeWindmill(be.getBlockPos());
    }

    /**
     * 获取指定维度内所有已加载的风车
     */
    public static Set<BlockPos> getWindmillsInLevel(Level level) {
        if (level == null || level.isClientSide()) return Collections.emptySet();
        InterferenceNetworkData data = InterferenceNetworkData.get(level);
        return data != null ? data.getWindmills() : Collections.emptySet();
    }

    /**
     * 注册水车
     */
    public static void trackWaterWheel(WaterWheelBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) return;
        InterferenceNetworkData data = InterferenceNetworkData.get(level);
        if (data != null) data.addWaterWheel(be.getBlockPos());
    }

    /**
     * 注销水车
     */
    public static void untrackWaterWheel(WaterWheelBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) return;
        InterferenceNetworkData data = InterferenceNetworkData.get(level);
        if (data != null) data.removeWaterWheel(be.getBlockPos());
    }

    /**
     * 获取指定维度内所有已加载的水车
     */
    public static Set<BlockPos> getWaterWheelsInLevel(Level level) {
        if (level == null || level.isClientSide()) return Collections.emptySet();
        InterferenceNetworkData data = InterferenceNetworkData.get(level);
        return data != null ? data.getWaterWheels() : Collections.emptySet();
    }
}