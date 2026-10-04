package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import yourscraft.jasdewstarfield.createkineticinterference.Createkineticinterference;
import yourscraft.jasdewstarfield.createkineticinterference.CreatekineticinterferenceConfig;

/** 资源监听器等待标签绑定，再验证规则引用；服务端生命周期负责缓存释放。 */
@EventBusSubscriber(modid=Createkineticinterference.MODID)
public final class DensityEvents {
    private static ResourceManager resources;
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<ResourceManager>() {
            @Override protected ResourceManager prepare(ResourceManager manager,ProfilerFiller profiler) { return manager; }
            @Override protected void apply(ResourceManager manager,ResourceManager ignored,ProfilerFiller profiler) { resources=manager; }
        });
    }
    private static void loadProfiles(RegistryAccess access) {
        if (resources == null) return;
        var config = CreatekineticinterferenceConfig.SERVER;
        boolean loaded = CreatekineticinterferenceConfig.SERVER_SPEC.isLoaded();
        DensityProfiles.reload(resources,access,loaded?config.waterDensity.profile.get():"createkineticinterference:water",
                loaded?config.windDensity.profile.get():"createkineticinterference:wind");
    }
    @SubscribeEvent public static void tags(TagsUpdatedEvent event) {
        if (event.getUpdateCause()==TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) loadProfiles(event.getRegistryAccess());
    }
    @SubscribeEvent public static void starting(ServerAboutToStartEvent event) { loadProfiles(event.getServer().registryAccess()); }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && WorldModelData.get(level)==CalculationModel.DENSITY)
            DensityUpdateScheduler.get(level).tick();
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) DensityUpdateScheduler.unload(level);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        DensityUpdateScheduler.clear(); DensityProfiles.reset(); resources=null;
    }
}
