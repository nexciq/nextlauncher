package com.nextlauncher.mod.mixin;

import com.nextlauncher.mod.NLUserRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends a green "[NL]" badge to the nametag of any registered NextLauncher user.
 *
 * MC 1.21.4+ rendering API: renderLabelIfPresent now takes a PlayerEntityRenderState
 * instead of the old (Entity, Text, ...) parameters. The entity UUID is resolved by
 * looking up the entity from the render state's int id field.
 *
 * state.displayName is repopulated by updateRenderState() before each render frame,
 * so modifying it here only affects the current frame.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"))
    private void nl$appendBadge(PlayerEntityRenderState state, MatrixStack matrices,
                                  OrderedRenderCommandQueue renderQueue, CameraRenderState cameraState,
                                  CallbackInfo ci) {
        if (state.displayName == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Entity entity = client.world.getEntityById(state.id);
        if (entity instanceof PlayerEntity player && NLUserRegistry.isNLUser(player.getUuid())) {
            state.displayName = Text.empty()
                    .append(state.displayName)
                    .append(Text.literal(" [NL]").formatted(Formatting.GREEN));
        }
    }
}
