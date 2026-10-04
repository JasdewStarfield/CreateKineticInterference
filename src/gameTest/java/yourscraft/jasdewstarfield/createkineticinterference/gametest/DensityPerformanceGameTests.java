package yourscraft.jasdewstarfield.createkineticinterference.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.*;
import yourscraft.jasdewstarfield.createkineticinterference.common.InterferenceNetworkData;
import yourscraft.jasdewstarfield.createkineticinterference.common.density.*;

import java.util.*;

/** 平坦测试世界中的卸载需求压力夹具；测真实 tick 调度与环境查询，不模拟卸载设备发电。 */
@GameTestHolder("cki_density_perf")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="cki_tests")
public class DensityPerformanceGameTests {
    private static long serverStart,maxServerTick;
    private static boolean measuring;
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void pre(ServerTickEvent.Pre event) { serverStart=System.nanoTime(); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void post(ServerTickEvent.Post event) {
        if(measuring)maxServerTick=Math.max(maxServerTick,System.nanoTime()-serverStart);
    }
    @GameTest(template="empty",timeoutTicks=50000)
    public static void snapshotPressureAndLocalRecovery(GameTestHelper helper) {
        var driver=new Driver(helper);
        System.out.println("CKI server benchmark java="+System.getProperty("java.version")+" cpu="+System.getenv("PROCESSOR_IDENTIFIER")
                +" processors="+Runtime.getRuntime().availableProcessors()+" maxHeap="+Runtime.getRuntime().maxMemory()
                +" environment=flat biome-source loadedGenerators=0 otherMods=none");
        helper.onEachTick(driver::tick);
    }
    private static final class Driver {
        private final GameTestHelper helper;
        private final InterferenceNetworkData data;
        private final List<BlockPos> positions=new ArrayList<>();
        private final List<Long> work=new ArrayList<>();
        private DensityUpdateScheduler service;
        private int scenario=-1,phase;
        private long phaseStart;
        Driver(GameTestHelper helper) {this.helper=helper;this.data=InterferenceNetworkData.get(helper.getLevel());}
        private void startScenario() {
            scenario++;if(scenario==4){measuring=false;helper.succeed();return;}
            int count=scenario<2?1000:10000;boolean scattered=scenario%2==1;
            int side=(int)Math.ceil(Math.sqrt(count));positions.clear();
            for(int i=0;i<count;i++) {
                var pos=new BlockPos(20000000+i%side*(scattered?80:1),70,20000000+i/side*(scattered?80:1));
                positions.add(pos);data.putDensitySource(new DensitySourceRecord(pos,"water","fixture",256,0,"validated"));
            }
            DensityUpdateScheduler.unload(helper.getLevel());service=DensityUpdateScheduler.get(helper.getLevel());
            phase=0;begin();
        }
        private void begin() {phaseStart=helper.getLevel().getGameTime();work.clear();maxServerTick=0;measuring=true;}
        private void tick() {
            if(scenario<0){startScenario();return;}
            work.add(service.lastWorkNanos());
            long elapsed=helper.getLevel().getGameTime()-phaseStart;
            if(phase==1 && elapsed<80)return;
            if(phase!=1 && service.pending())return;
            report(elapsed);
            if(phase==0){phase=1;begin();return;}
            if(phase==1) {
                var pos=positions.getFirst();data.putDensitySource(new DensitySourceRecord(pos,"water","fixture",512,helper.getLevel().getGameTime(),"validated"));
                phase=2;begin();return;
            }
            if(phase==2) {
                if(scenario<2 && elapsed>40)helper.fail("1000-source single change exceeded 40 ticks: "+elapsed);
                for(int i=0;i<positions.size()/2;i++)data.removeWaterWheel(positions.get(i));
                phase=3;begin();return;
            }
            if(phase==3) {
                DensityProfiles.reload(helper.getLevel().getServer().getResourceManager(),helper.getLevel().registryAccess(),
                        "createkineticinterference:water","createkineticinterference:wind");
                phase=4;begin();return;
            }
            for(var pos:positions) {
                if(helper.getLevel().hasChunkAt(pos))helper.fail("Pressure/environment query loaded a source chunk");
                data.removeWaterWheel(pos);
            }
            DensityUpdateScheduler.unload(helper.getLevel());startScenario();
        }
        private void report(long elapsed) {
            work.sort(Long::compare);double p95=work.get((int)Math.ceil(work.size()*0.95)-1)/1e6;
            String stage=new String[]{"initial","static","single-change","batch-removal","rule-reload"}[phase];
            System.out.printf(Locale.ROOT,"CKI server pressure stage=%s count=%d layout=%s ticks=%d workP95Ms=%.3f workMaxMs=%.3f serverTickMaxMs=%.3f nodes=%d envNodes=%d usedHeapMiB=%.2f stats=%s%n",
                    stage,positions.size(),scenario%2==1?"scattered":"dense",elapsed,p95,work.getLast()/1e6,maxServerTick/1e6,
                    service.nodeCount(),service.field().cachedNodes(),(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576d,service.stats());
            if(phase==1 && scenario<2 && p95>=2)helper.fail("Static 1000-source CKI work p95 exceeded 2 ms: "+p95);
        }
    }
}
