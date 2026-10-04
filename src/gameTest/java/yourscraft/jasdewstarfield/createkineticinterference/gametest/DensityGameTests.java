package yourscraft.jasdewstarfield.createkineticinterference.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;
import yourscraft.jasdewstarfield.createkineticinterference.common.*;
import yourscraft.jasdewstarfield.createkineticinterference.common.density.*;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import yourscraft.jasdewstarfield.createkineticinterference.CreatekineticinterferenceConfig;

/** 密度测试独立 namespace/batch，使用确定能力夹具核对真实 Mixin、网络和 SavedData。 */
@GameTestHolder("cki_density")
@PrefixGameTestTemplate(false)
public class DensityGameTests {
    private static void check(GameTestHelper helper,boolean condition,String message) { if (!condition) helper.fail(message); }
    private static void set(Object target,String name,Object value) {
        for (Class<?> type=target.getClass();type!=null;type=type.getSuperclass()) try {
            Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);return;
        } catch (NoSuchFieldException ignored) { /* Mixin 字段可能属于具体类或父类。 */ }
        catch (IllegalAccessException error) { throw new AssertionError(error); }
        throw new AssertionError("Missing fixture field "+name);
    }
    private static WaterWheelBlockEntity wheel(GameTestHelper helper,BlockPos pos,boolean large) {
        var state=large?AllBlocks.LARGE_WATER_WHEEL.getDefaultState():AllBlocks.WATER_WHEEL.getDefaultState();
        for (var property:state.getProperties()) if (property instanceof BooleanProperty flag && flag.getName().equals("picky")) state=state.setValue(flag,true);
        helper.setBlock(pos,state);
        var be=(WaterWheelBlockEntity)helper.getBlockEntity(pos); be.flowScore=1;
        if (ModList.get().isLoaded("createpickywheels")) {
            set(be,"createPickyWheels$biomeSTRESSMulti",2f);set(be,"createPickyWheels$optimalSTRESSMulti",3f);
            set(be,"createPickyWheels$biomeRPMMulti",1f);set(be,"createPickyWheels$optimalRPMMulti",1f);
        }
        be.setNetwork(be.getBlockPos().asLong());
        var network=be.getOrCreateNetwork();network.initialized=true;network.members.put(be,0f);
        network.sources.put(be,be.calculateAddedStressCapacity());
        return be;
    }
    private static void settle(GameTestHelper helper,DensityUpdateScheduler service) {
        // 重复推进真实预算任务，不修改求解状态；固定夹具的最后批次应可提交。
        for (int i=0;i<10000 && service.pending();i++) service.tick();
        check(helper,!service.pending(),"Stable input must settle: "+service.stats());
    }
    private static DensityDiagnostics diagnostics(KineticBlockEntity be) { return ((DensityDiagnostics.View)be).cki$getDiagnostics(); }
    private static void capacityMatches(GameTestHelper helper,KineticBlockEntity be) {
        double actual=be.getOrCreateNetwork().getActualCapacityOf(be),output=diagnostics(be).output();
        check(helper,Math.abs(actual-output)<=Math.max(0.01,diagnostics(be).raw()*2e-6),
                "Network SU must match allocated output; actual="+actual+" output="+output);
    }

    @GameTest(template="empty",batch="density-capacity",timeoutTicks=100)
    public static void waterSharingAndAbsolutePendingLimit(GameTestHelper helper) {
        helper.runAfterDelay(5,() -> {
            check(helper,WorldModelData.get(helper.getLevel())==CalculationModel.DENSITY,"Fixture must select DENSITY");
            var service=DensityUpdateScheduler.get(helper.getLevel());
            var first=wheel(helper,new BlockPos(2,2,2),false);
            double raw=SourceCapacityAdapter.potential(first);
            check(helper,first.calculateAddedStressCapacity()==0,"New source must output zero before first commit");
            settle(helper,service);capacityMatches(helper,first);
            double single=diagnostics(first).output();
            var second=wheel(helper,new BlockPos(2,6,2),true);
            check(helper,second.calculateAddedStressCapacity()==0,"New stacked source must have no committed allowance");
            settle(helper,service);capacityMatches(helper,first);capacityMatches(helper,second);
            check(helper,diagnostics(first).output()<single,"Same XZ different Y must share supply across separate networks");
            double secondRaw=SourceCapacityAdapter.potential(second);
            check(helper,Math.abs(diagnostics(first).output()/raw-diagnostics(second).output()/secondRaw)<1e-6,
                    "Coincident kernels must allocate in proportion to raw SU");
            double previous=diagnostics(first).output();
            if (ModList.get().isLoaded("createpickywheels")) set(first,"createPickyWheels$biomeSTRESSMulti",4f);
            else set(first,"flowScore",0);
            float coefficient=first.calculateAddedStressCapacity();
            check(helper,coefficient*Math.abs(first.getGeneratedSpeed())<=previous+0.02,"Capability change before commit must respect previous absolute SU");
            first.flowScore=1; service.observe(first); settle(helper,service);capacityMatches(helper,first);
            set(first,"overStressed",true);
            check(helper,SourceCapacityAdapter.potential(first)>0,"Overload must not erase raw generating capability");
            service.observe(first);settle(helper,service);
            check(helper,((IKineticInterference)first).isTracked(),"Overloaded generator must remain in demand");
            helper.setBlock(new BlockPos(2,6,2),Blocks.AIR);settle(helper,service);
            check(helper,((IKineticInterference)first).getNearbyCount()==0,"Destroyed peer must disappear without restart");
            capacityMatches(helper,first);
            System.out.println("CKI raw fixture smallSU="+raw+" largeSU="+secondRaw+" final="+service.stats());
            helper.setBlock(new BlockPos(2,2,2),Blocks.AIR);helper.succeed();
        });
    }

    @GameTest(template="empty",batch="density-unload",timeoutTicks=100)
    public static void snapshotsMigrationAndNoChunkLoading(GameTestHelper helper) {
        helper.runAfterDelay(5,() -> {
            var level=helper.getLevel();var data=InterferenceNetworkData.get(level);
            BlockPos old=new BlockPos(25000000,80,25000000);
            check(helper,!level.hasChunkAt(old),"Migration fixture must be unloaded");
            data.addWaterWheel(old);DensityUpdateScheduler.unload(level);
            var service=DensityUpdateScheduler.get(level);
            check(helper,data.getDensitySources().get(old).estimated(),"Old unloaded coordinates must receive marked estimated SU");
            check(helper,data.getDensitySources().get(old).rawPotentialSU()==service.settings().estimates().get("water"),"Estimate must be fixed configured SU");
            var tag=data.save(new CompoundTag(),level.registryAccess());
            var restored=InterferenceNetworkData.load(tag,level.registryAccess());
            check(helper,restored.getDensitySources().get(old).equals(data.getDensitySources().get(old)),"Source record must round trip");
            check(helper,tag.getInt("schemaVersion")==2 && tag.getInt("modelVersion")==1,"SavedData must be versioned");
            service.field().at("water",old.getX(),old.getZ());
            check(helper,!level.hasChunkAt(old),"Environment sampling must not load or generate chunks");
            var nether=level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            check(helper,nether!=null && !InterferenceNetworkData.get(nether).getDensitySources().containsKey(old),
                    "The same coordinate in another dimension must have an independent demand record");
            var be=wheel(helper,new BlockPos(2,2,2),false);service.observe(be);settle(helper,service);
            var packet=new CompoundTag();KineticInterferenceHandler.write((IKineticInterference)be,packet);
            // 重建服务模拟重启后的空派生缓存，同样的原始 P 也必须清除保存的旧 O。
            DensityUpdateScheduler.unload(level);service=DensityUpdateScheduler.get(level);
            KineticInterferenceHandler.read((IKineticInterference)be,packet);service.observe(be);
            check(helper,diagnostics(be).output()==0 && be.calculateAddedStressCapacity()==0,
                    "Restored diagnostics cannot reuse a previous session's allocated SU");
            settle(helper,service);capacityMatches(helper,be);
            var record=data.getDensitySources().get(be.getBlockPos());
            be.onChunkUnloaded();be.setRemoved();
            check(helper,data.getDensitySources().get(be.getBlockPos()).rawPotentialSU()==record.rawPotentialSU(),"Unload must preserve last known demand");
            helper.setBlock(new BlockPos(2,2,2),Blocks.AIR);
            data.removeWaterWheel(be.getBlockPos());data.removeWaterWheel(old);helper.succeed();
        });
    }

    @GameTest(template="empty",batch="density-sync",timeoutTicks=100)
    public static void boundedSyncAndWindIsolation(GameTestHelper helper) {
        helper.runAfterDelay(5,() -> {
            var first=wheel(helper,new BlockPos(2,2,2),false);
            var source=(IKineticInterference)first;var service=DensityUpdateScheduler.get(helper.getLevel());
            settle(helper,service);double before=diagnostics(first).output();
            var data=InterferenceNetworkData.get(helper.getLevel());
            var windPos=first.getBlockPos().above(8);
            // 未加载快照投影与加载源共用分配器；水力与风力互不兑换。
            helper.setBlock(new BlockPos(2,10,2),AllBlocks.WINDMILL_BEARING.getDefaultState());
            var wind=(WindmillBearingBlockEntity)helper.getBlockEntity(new BlockPos(2,10,2));
            set(wind,"running",true);set(wind,"lastGeneratedSpeed",8f);
            if (ModList.get().isLoaded("createpickywheels")) {
                set(wind,"createPickyWheels$hasFlow",true);set(wind,"createPickyWheels$boost",1f);
            }
            service.observe(wind);
            wind.setNetwork(wind.getBlockPos().asLong());var network=wind.getOrCreateNetwork();network.initialized=true;
            network.members.put(wind,0f);network.sources.put(wind,wind.calculateAddedStressCapacity());
            settle(helper,service);check(helper,Math.abs(diagnostics(first).output()-before)<0.001,"Different resource types must be isolated");
            capacityMatches(helper,wind);
            System.out.println("CKI raw fixture wind8RPM_SU="+SourceCapacityAdapter.potential(wind));
            double windOutput=diagnostics(wind).output();set(wind,"lastGeneratedSpeed",16f);
            check(helper,network.getActualCapacityOf(wind)<=windOutput+0.02,
                    "Cached coefficient times increased RPM must not expose uncommitted SU");
            settle(helper,service);capacityMatches(helper,wind);
            Set<BlockPos> highlights=new HashSet<>();for(int i=0;i<100;i++)highlights.add(new BlockPos(i,90,0));
            source.setInterferenceSources(highlights);source.setNearbyCount(100);
            var packet=new CompoundTag();KineticInterferenceHandler.write(source,packet);
            check(helper,packet.getLongArray("InterferenceSources").length==64 && packet.getBoolean("InterferenceSourcesTruncated"),"Highlight packet must be bounded and report truncation");
            check(helper,packet.getInt("InterferenceCount")==100,"True competitor total must survive truncation");
            source.setInterferenceSources(Set.of());KineticInterferenceHandler.write(source,packet);
            source.setInterferenceSources(highlights);KineticInterferenceHandler.read(source,packet);
            check(helper,source.getInterferenceSources().isEmpty(),"Empty packet must clear highlights");
            helper.setBlock(new BlockPos(2,10,2),Blocks.AIR);helper.setBlock(new BlockPos(2,2,2),Blocks.AIR);helper.succeed();
        });
    }

    /** 使用真实已绑定群系标签，替换一个资源的内容，验证整套规则的原子失败语义。 */
    private static ResourceManager profileOverride(ResourceManager original,String json) {
        var id=ResourceLocation.parse("createkineticinterference:cki_density_profiles/water.json");
        var resource=original.getResource(id).orElseThrow();
        return (ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},
                (proxy,method,args) -> {
                    if(method.getName().equals("listResources")) {
                        @SuppressWarnings("unchecked") var copy=new HashMap<>((Map<ResourceLocation,Resource>)method.invoke(original,args));
                        copy.put(id,new Resource(resource.source(),()->new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));return copy;
                    }
                    return method.invoke(original,args);
                });
    }
    @GameTest(template="empty",batch="density-profiles",timeoutTicks=100)
    public static void atomicProfilesAndFrozenModel(GameTestHelper helper) {
        helper.runAfterDelay(5,() -> {
            var level=helper.getLevel();var resources=level.getServer().getResourceManager();
            var service=DensityUpdateScheduler.get(level);
            var originalModel=CreatekineticinterferenceConfig.SERVER.calculationModel.get();
            CreatekineticinterferenceConfig.SERVER.calculationModel.set(CalculationModel.LEGACY);
            check(helper,WorldModelData.get(level)==CalculationModel.DENSITY,"Model must stay frozen until restart");
            CreatekineticinterferenceConfig.SERVER.calculationModel.set(originalModel);
            String valid="{\"schema_version\":1,\"resource_type\":\"water\",\"default_multiplier\":2,\"rules\":[]}";
            check(helper,DensityProfiles.reload(profileOverride(resources,valid),level.registryAccess(),"createkineticinterference:water","createkineticinterference:wind"),"Valid profiles must atomically replace snapshot");
            settle(helper,service);long version=DensityProfiles.version();double rho=service.field().at("water",0.5,0.5);
            for(String invalid:List.of(valid.replace("multiplier\":2","multiplier\":-1"),
                    valid.replace("\"rules\":[]","\"rules\":[{\"biome_tag\":\"cki_tests:missing\",\"priority\":1,\"multiplier\":1}]"),
                    valid.replace("\"rules\":[]","\"rules\":[{\"biome_tag\":\"createkineticinterference:water_abundant\",\"priority\":1,\"multiplier\":1},{\"biome_tag\":\"createkineticinterference:water_abundant\",\"priority\":1,\"multiplier\":2}]"))) {
                check(helper,!DensityProfiles.reload(profileOverride(resources,invalid),level.registryAccess(),"createkineticinterference:water","createkineticinterference:wind"),"Invalid/ambiguous profiles must be rejected");
                check(helper,DensityProfiles.version()==version && service.field().at("water",0.5,0.5)==rho,"Failure must retain complete previous field snapshot");
            }
            check(helper,DensityProfiles.reload(resources,level.registryAccess(),"createkineticinterference:water","createkineticinterference:wind"),"Original profiles must restore");
            settle(helper,service);helper.succeed();
        });
    }

    @GameTest(template="empty",batch="density-invalidation",timeoutTicks=100)
    public static void changingDemandInvalidatesPendingSnapshot(GameTestHelper helper) {
        helper.runAfterDelay(5,() -> {
            var level=helper.getLevel();var data=InterferenceNetworkData.get(level);
            var be=wheel(helper,new BlockPos(2,2,2),false);var service=DensityUpdateScheduler.get(level);settle(helper,service);
            List<BlockPos> ghosts=new ArrayList<>();
            for(int i=0;i<1000;i++) {
                var pos=new BlockPos(4000000+i%40*80,70,4000000+i/40*80);ghosts.add(pos);
                data.putDensitySource(new DensitySourceRecord(pos,"water","fixture",256,0,"validated"));
            }
            service.tick();check(helper,service.pending(),"Large snapshot must remain pending after its first budget slice");
            be.flowScore=0;
            check(helper,be.calculateAddedStressCapacity()==0,"Capability loss must apply while the older batch is pending");
            settle(helper,service);
            check(helper,!data.getDensitySources().containsKey(be.getBlockPos()) && !((IKineticInterference)be).isTracked(),
                    "A stale batch must not resurrect removed demand");
            check(helper,be.getOrCreateNetwork().getActualCapacityOf(be)==0,"Stale output must not return to the network");
            check(helper,diagnostics(be).enabled() && diagnostics(be).raw()==0 && diagnostics(be).output()==0,
                    "Stopped generators retain zero-output diagnostics for an N/A efficiency tooltip");
            for(var pos:ghosts)data.removeWaterWheel(pos);
            helper.setBlock(new BlockPos(2,2,2),Blocks.AIR);helper.succeed();
        });
    }
}
