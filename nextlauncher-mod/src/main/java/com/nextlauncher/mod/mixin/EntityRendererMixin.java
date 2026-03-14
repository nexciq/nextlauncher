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
 * Appends a green "[NL]" badge to the nametag of any registered NextLauncher user.
 *
 * Strategy:
 *  1. @Inject HEAD stores the entity in a ThreadLocal.
 *  2. @ModifyArg on the inner renderLabel() call appends "[NL]" to the Text arg
 *     when the stored entity is a registered NL player.
 *  3. @Inject RETURN clears the ThreadLocal.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {

    @Unique
    private static final ThreadLocal<Entity> NL_ENTITY = new ThreadLocal<>();

    // ── 1. Capture entity reference ──────────────────────────────────────────

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

    // ── 2. Append [NL] badge to the Text before it's rendered ────────────────

    /**
     * Targets the internal renderLabel() call so we can modify the Text argument
     * without cancelling the whole method.
     *
     * Yarn mapping for renderLabel in MC 1.21.x:
     *   net/minecraft/client/render/entity/EntityRenderer.renderLabel(
     *     Lnet/minecraft/entity/Entity;
     *     Lnet/minecraft/text/Text;
     *     Lnet/minecraft/client/util/math/MatrixStack;
     *     Lnet/minecraft/client/render/VertexConsumerProvider;
     *     IZ)V
     */
    @ModifyArg(
        method = "renderLabelIfPresent",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderer;" +
                     "renderLabel(Lnet/minecraft/entity/Entity;" +
                     "Lnet/minecraft/text/Text;" +
                     "Lnet/minecraft/client/util/math/MatrixStack;" +
                     "Lnet/minecraft/client/render/VertexConsumerProvider;IZ)V"
        ),
        index = 1  // Text is the 2nd argument (index 1)
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
