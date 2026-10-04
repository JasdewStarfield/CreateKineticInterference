package yourscraft.jasdewstarfield.createkineticinterference.mixin;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yourscraft.jasdewstarfield.createkineticinterference.common.IKineticInterference;

import java.util.List;
import yourscraft.jasdewstarfield.createkineticinterference.client.DensityTooltip;
import yourscraft.jasdewstarfield.createkineticinterference.common.density.DensityDiagnostics;

/** 保留 Create 的基础提示，也让调用 super 的附属继续添加自己的提示。 */
@Mixin(GeneratingKineticBlockEntity.class)
public abstract class MixinGeneratingKineticBlockEntity {
    @ModifyArg(method = "addToGoggleTooltip", at = @At(value = "INVOKE",
            target = "Lnet/createmod/catnip/lang/LangBuilder;add(Lnet/createmod/catnip/lang/LangBuilder;)Lnet/createmod/catnip/lang/LangBuilder;"), index = 0)
    private LangBuilder kineticInterference$rawCapacitySuffix(LangBuilder original) {
        if ((Object)this instanceof IKineticInterference source) {
            var data = ((DensityDiagnostics.View)source).cki$getDiagnostics();
            // 替换 Create 的速度说明后缀，容量数值与单位仍沿用原产出行。
            if (data.enabled()) return CreateLang.text("(").add(CreateLang.number(data.raw())
                    .translate("generic.unit.stress")).text(")").style(ChatFormatting.GRAY);
        }
        return original;
    }

    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"), cancellable = true)
    private void kineticInterference$appendTooltip(List<Component> tooltip, boolean sneaking,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof IKineticInterference source
                && (((DensityDiagnostics.View)source).cki$getDiagnostics().enabled()
                ? DensityTooltip.append(source,tooltip,sneaking) : source.appendInterferenceTooltip(tooltip,sneaking))) {
            // 即使父类没有添加内容，新增的干扰信息也应该让护目镜显示面板。
            cir.setReturnValue(true);
        }
    }
}
