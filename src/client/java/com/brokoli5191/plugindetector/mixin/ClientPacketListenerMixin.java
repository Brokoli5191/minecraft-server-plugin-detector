package com.brokoli5191.plugindetector.mixin;

import com.brokoli5191.plugindetector.PluginDetector;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Shadow
    public abstract com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.SharedSuggestionProvider> getCommands();

    @Inject(method = "handleCommands", at = @At("RETURN"))
    private void serverPluginDetector$analyzeCommandTree(ClientboundCommandsPacket packet, CallbackInfo ci) {
        PluginDetector.analyze(this.getCommands());
    }
}
