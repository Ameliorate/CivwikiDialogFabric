package civwiki.dialogmw.client.mixin;

import civwiki.dialogmw.client.DialogAccessor;
import net.minecraft.server.dialog.Dialog;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes {@code DialogScreen}'s private {@code dialog} field through the
 * {@link DialogAccessor} interface. The 26.x game jar is unobfuscated, so we mix
 * against the real class and field names.
 */
@Mixin(net.minecraft.client.gui.screens.dialog.DialogScreen.class)
public abstract class DialogScreenMixin implements DialogAccessor {

	@Shadow
	private Dialog dialog;

	@Override
	public Dialog dialog() {
		return this.dialog;
	}
}