package yourscraft.jasdewstarfield.createkineticinterference.mixin;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yourscraft.jasdewstarfield.createkineticinterference.common.density.WorldModelData;

/** 在 NeoForge 补齐新配置键之前识别旧配置，保留老世界的计数模型。 */
@Mixin(value = ServerLifecycleHooks.class, remap = false)
public class MixinServerLifecycleHooks {
    @Inject(method = "handleServerAboutToStart", at = @At("HEAD"))
    private static void cki$captureOldConfig(MinecraftServer server, CallbackInfo ci) {
        WorldModelData.captureBeforeConfigLoad(server);
    }
}
