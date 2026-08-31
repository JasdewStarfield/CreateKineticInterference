package yourscraft.jasdewstarfield.createkineticinterference.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yourscraft.jasdewstarfield.createkineticinterference.common.IKineticInterference;
import yourscraft.jasdewstarfield.createkineticinterference.common.InterferenceNetworkData;
import yourscraft.jasdewstarfield.createkineticinterference.common.KineticInterferenceHandler;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/** 用真实 Mixin 转换后的实体测试服务端行为；护目镜调用依赖客户端，另行手动验收。 */
@Mod("cki_tests")
@GameTestHolder("cki_tests")
@PrefixGameTestTemplate(false)
public class InterferenceGameTests {
    @GameTest(template = "empty")
    public static void capacityMultipliersCompose(GameTestHelper helper) {
        List<BlockState> states = List.of(AllBlocks.WATER_WHEEL.getDefaultState(),
                AllBlocks.LARGE_WATER_WHEEL.getDefaultState(), AllBlocks.WINDMILL_BEARING.getDefaultState());
        boolean picky = ModList.get().isLoaded("createpickywheels");
        for (int i = 0; i < states.size(); i++) {
            BlockPos pos = new BlockPos(2 + i * 4, 2, 2);
            BlockState state = states.get(i);
            // 测试环境直接放置方块，显式启用 Picky 属性来模拟玩家放置。
            for (var property : state.getProperties()) {
                if (property instanceof BooleanProperty flag && property.getName().equals("picky")) {
                    state = state.setValue(flag, true);
                }
            }
            helper.setBlock(pos, state);
            var be = (GeneratingKineticBlockEntity) helper.getBlockEntity(pos);
            var source = (IKineticInterference) be;
            source.setEfficiencyFactor(0.5f);
            source.setNearbyCount(1);
            float multiplier = 1;
            if (be instanceof WaterWheelBlockEntity wheel) {
                wheel.flowScore = 1;
                if (picky) {
                    // 不依赖环境扫描的时间：直接设置非平凡倍率以检测丢失或重复乘算。
                    field(be, "createPickyWheels$biomeSTRESSMulti", 2f);
                    field(be, "createPickyWheels$optimalSTRESSMulti", 3f);
                    field(be, "createPickyWheels$biomeRPMMulti", 1f);
                    field(be, "createPickyWheels$optimalRPMMulti", 1f);
                    multiplier = 6;
                }
            } else {
                field(be, "running", true);
                field(be, "lastGeneratedSpeed", 8f);
                if (picky) {
                    field(be, "createPickyWheels$hasFlow", true);
                    field(be, "createPickyWheels$boost", 1f);
                }
            }
            float expected = (float) BlockStressValues.getCapacity(state.getBlock()) * multiplier * 0.5f;
            check(helper, expected > 0, "Fixture must have a nonzero base capacity");
            for (int call = 0; call < 2; call++) {
                check(helper, Math.abs(be.calculateAddedStressCapacity() - expected) < 0.001f,
                        "Capacity must include each multiplier once: " + state);
                check(helper, Math.abs(((Number) field(be, "lastCapacityProvided")).floatValue() - expected) < 0.001f,
                        "Cached capacity must match the returned capacity");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removalAndChunkUnload(GameTestHelper helper) {
        var data = InterferenceNetworkData.get(helper.getLevel());
        BlockPos removed = new BlockPos(2, 2, 2);
        helper.setBlock(removed, AllBlocks.WATER_WHEEL.getDefaultState());
        var wheel = (WaterWheelBlockEntity) helper.getBlockEntity(removed);
        var source = (IKineticInterference) wheel;
        source.trackSelf();
        source.setTracked(true);
        BlockPos neighbor = new BlockPos(4, 2, 2);
        helper.setBlock(neighbor, AllBlocks.WATER_WHEEL.getDefaultState());
        var neighborBe = (WaterWheelBlockEntity) helper.getBlockEntity(neighbor);
        var neighborSource = (IKineticInterference) neighborBe;
        KineticInterferenceHandler.performCalculation(neighborSource, neighborBe);
        check(helper, neighborSource.getEfficiencyFactor() < 1, "Neighbor must initially be interfered with");
        helper.setBlock(removed, Blocks.AIR);
        check(helper, !data.getWaterWheels().contains(helper.absolutePos(removed)),
                "Destroying a wheel must immediately remove its saved position");
        check(helper, !source.isTracked(), "Removed entity must reset its tracking flag");
        KineticInterferenceHandler.performCalculation(neighborSource, neighborBe);
        check(helper, neighborSource.getEfficiencyFactor() == 1 && neighborSource.getNearbyCount() == 0,
                "Neighbor must recover after removal without restarting the world");

        BlockPos unloaded = new BlockPos(6, 2, 2);
        helper.setBlock(unloaded, AllBlocks.WATER_WHEEL.getDefaultState());
        var unloadingWheel = (WaterWheelBlockEntity) helper.getBlockEntity(unloaded);
        ((IKineticInterference) unloadingWheel).trackSelf();
        // 调用真实卸载生命周期，确保 setRemoved 不会把卸载误判成拆除。
        unloadingWheel.onChunkUnloaded();
        unloadingWheel.setRemoved();
        check(helper, data.getWaterWheels().contains(helper.absolutePos(unloaded)),
                "Chunk unload must preserve the saved source");
        data.removeWaterWheel(helper.absolutePos(unloaded));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void windmillTrackingSurvivesCancelledTick(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 2), AllBlocks.WINDMILL_BEARING.getDefaultState());
        var windmill = (WindmillBearingBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 2));
        var source = (IKineticInterference) windmill;
        field(windmill, "running", true);
        field(windmill, "lastGeneratedSpeed", 8f);
        if (ModList.get().isLoaded("createpickywheels")) {
            field(windmill, "createPickyWheels$hasFlow", true);
        }
        // 使用真正的 tick 调用链，验证 Picky 的取消注入不能截断父类追踪更新。
        windmill.tick();
        check(helper, source.isTracked(), "Active windmill must be tracked through the shared tick path");
        field(windmill, "lastGeneratedSpeed", 0f);
        windmill.tick();
        check(helper, !source.isTracked(), "Assembled but zero-output windmill must not remain active");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void staleSavedPositionsAndEmptySync(GameTestHelper helper) {
        // 独立 SavedData 避免并行测试共享校验节流状态；通过 NBT 模拟旧存档。
        BlockPos stale = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        BlockPos unloaded = new BlockPos(25000000, 80, 25000000);
        check(helper, !helper.getLevel().hasChunkAt(unloaded), "Fixture chunk must remain unloaded");
        var tag = new CompoundTag();
        tag.putLongArray("ActiveWaterWheels", new long[] {stale.asLong(), unloaded.asLong()});
        var data = InterferenceNetworkData.load(tag, helper.getLevel().registryAccess());
        Set<BlockPos> positions = data.getValidatedWaterWheels(helper.getLevel());
        check(helper, !positions.contains(stale) && positions.contains(unloaded),
                "Only verifiably missing sources in loaded chunks may be pruned");
        check(helper, data.isDirty(), "Repair must be saved");
        check(helper, !helper.getLevel().hasChunkAt(unloaded), "Validation must not load chunks");
        var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        check(helper, saved.getLongArray("ActiveWaterWheels").length == 1, "Repair must survive saving");

        helper.setBlock(new BlockPos(6, 2, 2), AllBlocks.WATER_WHEEL.getDefaultState());
        var source = (IKineticInterference) helper.getBlockEntity(new BlockPos(6, 2, 2));
        source.setInterferenceSources(Set.of(stale));
        var packet = new CompoundTag();
        source.setInterferenceSources(Set.of());
        KineticInterferenceHandler.write(source, packet);
        source.setInterferenceSources(Set.of(stale));
        KineticInterferenceHandler.read(source, packet);
        check(helper, source.getInterferenceSources().isEmpty(), "Empty sync must clear old client highlights");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    /** 只在测试中访问运行时字段，正式代码不引用可选模组或它们的内部实现。 */
    private static Field findField(Object target, String name) throws NoSuchFieldException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Mixin 字段可能合入当前类，也可能属于 Create 的父类。
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object field(Object target, String name) {
        try {
            return findField(target, name).get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Missing runtime fixture field: " + name, e);
        }
    }

    private static void field(Object target, String name, Object value) {
        try {
            findField(target, name).set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot set runtime fixture field: " + name, e);
        }
    }
}
