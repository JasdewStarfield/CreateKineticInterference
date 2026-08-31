package yourscraft.jasdewstarfield.createkineticinterference.mixin;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yourscraft.jasdewstarfield.createkineticinterference.common.IKineticInterference;
import yourscraft.jasdewstarfield.createkineticinterference.common.KineticInterferenceHandler;

/** 在 final 移除入口完成清理，不再覆盖 Flowing Fluids 的 invalidate。 */
@Mixin(SmartBlockEntity.class)
public abstract class MixinSmartBlockEntity {
    @Shadow public abstract boolean isChunkUnloaded();

    @Inject(method = "setRemoved", at = @At("RETURN"))
    private void kineticInterference$onRemoved(CallbackInfo ci) {
        if ((Object) this instanceof IKineticInterference source) {
            // 保持原有语义：真正拆除时注销，区块卸载时保留跨区块干扰记录。
            KineticInterferenceHandler.invalidate(source, isChunkUnloaded());
        }
    }
}
