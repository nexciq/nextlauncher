package com.nextlauncher.mod.mixin;

import com.nextlauncher.mod.NLUserRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MC 1.20.x version — renderLabel has 5 params (no seeThrough boolean).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixinLegacy<T extends Entity> {

    @Unique
    private static final ThreadLocal<Entity> NL_ENTITY = new ThreadLocal<>();

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"))
    private void nl$captureEntity(T entity, Text text, MatrixStack matrices,
                                   VertexConsumerProvider vcp, int light,
                                   float tickDelta, CallbackInfo ci) {
        NL_ENTITY.set(entity);
    }

    @Inject(method = "renderLabelIfPresent", at = @At("RETURN"))
    private void nl$clearEntity(T entity, Text text, MatrixStack matrices,
                                 VertexConsumerProvider vcp, int light,
                                 float tickDelta, CallbackInfo ci) {
        NL_ENTITY.remove();
    }

    /**
     * MC 1.20.x: renderLabel(entity, text, matrices, vcp, light) — no seeThrough.
     */
    @ModifyArg(
        method = "renderLabelIfPresent",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderer;" +
                     "renderLabel(Lnet/minecraft/entity/Entity;" +
                     "Lnet/minecraft/text/Text;" +
                     "Lnet/minecraft/client/util/math/MatrixStack;" +
                     "Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
        ),
        index = 1
    )
    private Text nl$appendNLBadge(Text text) {
        Entity entity = NL_ENTITY.get();
        if (entity instanceof PlayerEntity player
                && NLUserRegistry.isNLUser(player.getUuid())) {
            return Text.empty()
                    .append(text)
                    .append(Text.literal(" [NL]").formatted(Formatting.GREEN));
        }
        return text;
    }
}
