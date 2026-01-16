package yourscraft.jasdewstarfield.createkineticinterference.common;

import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Set;

/**
 * 全局风车管理器
 * 负责追踪当前世界中所有已加载的风车方块实体。
 */
public class WindmillManager {

    /**
     * 注册风车
     */
    public static void trackWindmill(WindmillBearingBlockEntity windmill) {
        Level level = windmill.getLevel();
        if (level == null || level.isClientSide()) return;

        WindmillNetworkData data = WindmillNetworkData.get(level);
        if (data != null) {
            data.add(windmill.getBlockPos());
        }
    }

    /**
     * 注销风车
     */
    public static void untrackWindmill(WindmillBearingBlockEntity windmill) {
        Level level = windmill.getLevel();
        if (level == null || level.isClientSide()) return;

        WindmillNetworkData data = WindmillNetworkData.get(level);
        if (data != null) {
            data.remove(windmill.getBlockPos());
        }
    }

    /**
     * 获取指定维度内所有已加载的风车
     */
    public static Set<BlockPos> getWindmillsInLevel(Level level) {
        if (level == null || level.isClientSide()) return Collections.emptySet();

        WindmillNetworkData data = WindmillNetworkData.get(level);
        return data != null ? data.getAll() : Collections.emptySet();
    }
}