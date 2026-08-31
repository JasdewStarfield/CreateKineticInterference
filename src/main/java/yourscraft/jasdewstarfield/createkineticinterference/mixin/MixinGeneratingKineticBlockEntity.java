package yourscraft.jasdewstarfield.createkineticinterference.mixin;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yourscraft.jasdewstarfield.createkineticinterference.common.IKineticInterference;

import java.util.List;

/** 保留 Create 的基础提示，也让调用 super 的附属继续添加自己的提示。 */
@Mixin(GeneratingKineticBlockEntity.class)
public abstract class MixinGeneratingKineticBlockEntity {
    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"), cancellable = true)
    private void kineticInterference$appendTooltip(List<Component> tooltip, boolean sneaking,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof IKineticInterference source
                && source.appendInterferenceTooltip(tooltip, sneaking)) {
            // 即使父类没有添加内容，新增的干扰信息也应该让护目镜显示面板。
            cir.setReturnValue(true);
        }
    }
}
