package dev.ryanland.dfoptimizer.mixin;

import dev.ryanland.dfoptimizer.PlotFilter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Don't spawn particles on other plots; every addParticle variant goes through doAddParticle. */
@Mixin(ClientLevel.class)
public class ClientLevelParticleMixin {
	@Inject(method = "doAddParticle", at = @At("HEAD"), cancellable = true)
	private void plotfilter$skipHidden(ParticleOptions options, boolean overrideLimiter, boolean alwaysShow,
			double x, double y, double z, double dx, double dy, double dz, CallbackInfo ci) {
		if (PlotFilter.hidesBlock(Mth.floor(x), Mth.floor(z))) ci.cancel();
	}
}
