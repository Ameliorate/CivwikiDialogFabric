package civwiki.dialogmw.client;

import net.minecraft.server.dialog.Dialog;

/**
 * Accessor applied to {@link net.minecraft.client.gui.screens.dialog.DialogScreen}
 * so the warning screen can read the dialog currently displayed. The field itself
 * stays private; only the accessor method is added to the class.
 */
public interface DialogAccessor {

	/** Returns the dialog currently displayed by the screen, or null if none. */
	Dialog dialog();
}