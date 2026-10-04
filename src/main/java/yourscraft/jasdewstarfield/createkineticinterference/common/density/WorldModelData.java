package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import yourscraft.jasdewstarfield.createkineticinterference.Createkineticinterference;
import yourscraft.jasdewstarfield.createkineticinterference.CreatekineticinterferenceConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 世界模型存于主世界，同一存档的各维度共享选择；配置仅在启动时覆盖标记。 */
public final class WorldModelData extends SavedData {
    private CalculationModel model;
    private boolean resolved;
    private static final Map<MinecraftServer,Boolean> OLD_CONFIG = new WeakHashMap<>();
    public static void captureBeforeConfigLoad(MinecraftServer server) {
        Path override = server.getWorldPath(new LevelResource("serverconfig"))
                .resolve("createkineticinterference-server.toml");
        Path config = Files.isRegularFile(override) ? override : FMLPaths.CONFIGDIR.get()
                .resolve("createkineticinterference-server.toml");
        try {
            String text = Files.isRegularFile(config) ? Files.readString(config,StandardCharsets.UTF_8) : "";
            boolean marked=java.util.regex.Pattern.compile("(?m)^\\s*calculationModel\\s*=").matcher(text).find();
            boolean oldKeys=java.util.regex.Pattern.compile("(?m)^\\s*(interferenceRadius|interferenceFactor|distanceCalculationMode)\\s*=").matcher(text).find();
            OLD_CONFIG.put(server,oldKeys&&!marked);
        } catch (Exception error) {
            OLD_CONFIG.put(server,true);
            Createkineticinterference.LOGGER.error("Unable to inspect old CKI config {}; retaining LEGACY for AUTO",config,error);
        }
    }
    private static WorldModelData load(CompoundTag tag,HolderLookup.Provider registries) {
        var data = new WorldModelData();
        data.model = CalculationModel.valueOf(tag.getString("calculationModel"));
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putInt("schemaVersion",1); tag.putString("calculationModel",model.name()); return tag;
    }
    public static CalculationModel get(ServerLevel level) {
        var server = level.getServer();
        var data = server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(WorldModelData::new,WorldModelData::load,null),"cki_calculation_model");
        if (!data.resolved) {
            var requested = CreatekineticinterferenceConfig.SERVER.calculationModel.get();
            if (requested != CalculationModel.AUTO) data.model = requested;
            else if (data.model == null) {
                boolean old = OLD_CONFIG.getOrDefault(server,false);
                Path root = server.getWorldPath(LevelResource.ROOT);
                for (var dimension : server.getAllLevels()) old |= Files.isRegularFile(
                        DimensionType.getStorageFolder(dimension.dimension(),root)
                                .resolve("data/kinetic_interference_manager.dat"));
                data.model = old ? CalculationModel.LEGACY : CalculationModel.DENSITY;
                if (old) Createkineticinterference.LOGGER.warn("Existing CKI world/config detected: keeping LEGACY. Back up before selecting DENSITY and restarting.");
            }
            data.resolved = true; data.setDirty();
            Createkineticinterference.LOGGER.info("CKI calculation model for this world: {}",data.model);
        }
        return data.model;
    }
}
