package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import yourscraft.jasdewstarfield.createkineticinterference.Createkineticinterference;
import yourscraft.jasdewstarfield.createkineticinterference.common.InterferenceNetworkData;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** 管理员诊断只读；导出点数有上限，查询不会加载区块。 */
@EventBusSubscriber(modid=Createkineticinterference.MODID)
public final class DensityCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cki").requires(source -> source.hasPermission(2))
                .then(Commands.literal("density")
                        .then(Commands.literal("stats").executes(context -> stats(context.getSource())))
                        .then(Commands.literal("inspect").then(Commands.argument("pos",BlockPosArgument.blockPos())
                                .executes(context -> inspect(context.getSource(),BlockPosArgument.getBlockPos(context,"pos")))))
                        .then(Commands.literal("sample").then(Commands.argument("type",StringArgumentType.word())
                                .suggests((context,builder) -> { builder.suggest("water"); builder.suggest("wind"); return builder.buildFuture(); })
                                .then(Commands.argument("center",BlockPosArgument.blockPos())
                                        .then(Commands.argument("radius",DoubleArgumentType.doubleArg(0,64))
                                                .then(Commands.argument("step",DoubleArgumentType.doubleArg(0.5,64))
                                                        .executes(context -> sample(context.getSource(),StringArgumentType.getString(context,"type"),
                                                                BlockPosArgument.getBlockPos(context,"center"),DoubleArgumentType.getDouble(context,"radius"),
                                                                DoubleArgumentType.getDouble(context,"step"))))))))));
    }
    private static void say(CommandSourceStack source,String message) { source.sendSuccess(() -> Component.literal(message),false); }
    private static boolean density(CommandSourceStack source) {
        if (WorldModelData.get(source.getLevel())==CalculationModel.DENSITY) return true;
        source.sendFailure(Component.literal("CKI calculation model is LEGACY; select DENSITY in server config and restart after backing up.")); return false;
    }
    private static int stats(CommandSourceStack source) {
        if (!density(source)) return 0;
        say(source,DensityUpdateScheduler.get(source.getLevel()).stats()); return 1;
    }
    private static int inspect(CommandSourceStack source,BlockPos pos) {
        if (!density(source)) return 0;
        var data = InterferenceNetworkData.get(source.getLevel()); var record = data.getDensitySources().get(pos);
        if (record == null) { source.sendFailure(Component.literal("No recorded CKI source at "+pos.toShortString())); return 0; }
        var service = DensityUpdateScheduler.get(source.getLevel());
        boolean loaded = source.getLevel().hasChunkAt(pos);
        var allocation = service.sample(record.resourceType(),pos.getX()+0.5,pos.getZ()+0.5);
        // 需求减少时上批额度暂未重算，诊断同样显示当前可暴露的绝对上限。
        double output=Math.min(record.rawPotentialSU(),service.committedOutput(pos));
        say(source,"pos="+pos.toShortString()+" type="+record.resourceType()+" kind="+record.sourceKind()+" rawSU="+record.rawPotentialSU()
                +" outputSU="+output+" efficiency="+(record.rawPotentialSU()==0?0:output/record.rawPotentialSU())
                +" rho="+allocation.supply()+" D="+allocation.demand()+" a="+allocation.fulfillment()
                +" loaded="+loaded+" validation="+record.validationState()+" validatedTick="+record.lastValidatedGameTime()
                +" profile="+service.settings().profiles().get(record.resourceType())+" profileVersion="+DensityProfiles.version()+" modelVersion=2"
                +" "+service.sourceCounts(record)+" pending="+service.pending()); return 1;
    }
    private static int sample(CommandSourceStack source,String type,BlockPos center,double radius,double step) {
        if (!density(source)) return 0;
        if (!type.equals("water")&&!type.equals("wind")) { source.sendFailure(Component.literal("Resource type must be water or wind")); return 0; }
        int side=(int)Math.floor(2*radius/step)+1;
        if ((long)side*side>4096) { source.sendFailure(Component.literal("Sample limited to 4096 points")); return 0; }
        var service=DensityUpdateScheduler.get(source.getLevel()); var csv=new StringBuilder("x,z,rho_su_per_block2,demand_su_per_block2,fulfillment\n");
        long candidateCount=0;
        for (int ix=0;ix<side;ix++) for (int iz=0;iz<side;iz++) {
            double x=center.getX()+0.5-radius+ix*step,z=center.getZ()+0.5-radius+iz*step;
            candidateCount+=service.sampleCandidates(type,x,z);
            if (candidateCount>200000) { source.sendFailure(Component.literal("Sample exceeds 200000 source contributions; reduce radius or increase step")); return 0; }
            var point=service.sample(type,x,z); csv.append(x).append(',').append(z).append(',').append(point.supply())
                    .append(',').append(point.demand()).append(',').append(point.fulfillment()).append('\n');
        }
        try {
            Path folder=source.getServer().getServerDirectory().resolve("cki-diagnostics"); Files.createDirectories(folder);
            Path file=Files.createTempFile(folder,"density-"+type+"-",".csv"); Files.writeString(file,csv,StandardCharsets.UTF_8);
            say(source,"Exported "+side*side+" points to "+file.toAbsolutePath()+"; pending="+service.pending()+" sampleY="+service.field().sampleY()); return side*side;
        } catch (Exception error) { source.sendFailure(Component.literal("Density export failed: "+error.getMessage())); return 0; }
    }
}
