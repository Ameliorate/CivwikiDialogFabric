package civwiki.dialogmw.client.mixin;

import com.mojang.blaze3d.platform.ClipboardManager;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogConnectionAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.Dialog;

import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import civwiki.dialogmw.client.CivWikiDialogMwClient;
import civwiki.dialogmw.client.DialogAccessor;
import civwiki.dialogmw.client.MediaWikiDialogRenderer;

/**
 * Adds a third option -- "Copy to MediaWiki Template" -- to the dialog warning
 * screen (the vanilla {@code ConfirmScreen} with "Disconnect" and "Back" that opens
 * when you click the "!" next to a dialog title, hover text "This is a custom
 * screen. Click here to learn more.").
 *
 * <p>How it works: the warning screen is created by {@code DialogScreen$WarningScreen.create(...)}
 * with the originating {@code DialogScreen} tucked inside its return-screen holder. A
 * constructor injection captures that context (dialog + connection access + Minecraft),
 * and an {@code addButtons} <em>override</em> appends the extra button to the button row
 * that {@code ConfirmScreen.init()} builds. Pressing it converts the captured
 * {@link Dialog} to {@code {{MC dialog|...}}} wikitext and puts it on the OS clipboard.</p>
 *
 * <p>An {@code addButtons} override (rather than an {@code @Inject}) is used because Mixin
 * cannot target the bare inherited method name in the target class; extending
 * {@link ConfirmScreen} also lets us call {@code super.addButtons(...)} -- the mixin's
 * parent type must be an ancestor of the target, which ConfirmScreen is.</p>
 */
@Mixin(targets = "net.minecraft.client.gui.screens.dialog.DialogScreen$WarningScreen")
public abstract class WarningScreenMixin extends ConfirmScreen {

	@Unique
	private Dialog civwiki$dialog;

	@Unique
	private DialogConnectionAccess civwiki$connectionAccess;

	@Unique
	private Minecraft civwiki$minecraft;

	@Unique
	private boolean civwiki$copyButtonAdded;

	protected WarningScreenMixin(BooleanConsumer callback, Component title, Component message,
			Component yesButton, Component noButton) {
		super(callback, title, message, yesButton, noButton);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void civwiki$captureContext(Minecraft minecraft, DialogConnectionAccess connectionAccess,
			MutableObject<Screen> returnScreen, CallbackInfo ci) {
		this.civwiki$minecraft = minecraft;
		this.civwiki$connectionAccess = connectionAccess;
		this.civwiki$copyButtonAdded = false;
		Screen previous = returnScreen.get();
		if (previous instanceof DialogAccessor accessor) {
			this.civwiki$dialog = accessor.dialog();
		}
	}

	@Override
	public void addButtons(LinearLayout rows) {
		super.addButtons(rows);
		if (this.civwiki$copyButtonAdded) {
			return;
		}
		this.civwiki$copyButtonAdded = true;
		if (this.civwiki$dialog == null) {
			return;
		}
		rows.addChild(Button.builder(
			Component.translatableWithFallback("civwikidialogmw.copy_to_mediawiki", "Copy to MediaWiki Template"),
			button -> this.civwiki$copyToClipboard()
		).size(Button.BIG_WIDTH, Button.DEFAULT_HEIGHT).build());
	}

	private void civwiki$copyToClipboard() {
		try {
			String wikitext = MediaWikiDialogRenderer.render(this.civwiki$dialog, this.civwiki$connectionAccess);
			ClipboardManager clipboard = new ClipboardManager();
			clipboard.setClipboard(this.civwiki$minecraft.getWindow(), wikitext);
			SystemToast.addOrUpdate(
				this.civwiki$minecraft.getToastManager(),
				new SystemToast.SystemToastId(),
				Component.translatableWithFallback("civwikidialogmw.copied_title", "Copied to MediaWiki Template"),
				Component.translatableWithFallback("civwikidialogmw.copied_message",
					"The dialog's wikitext is on your clipboard. Paste it into a wiki page!")
			);
		} catch (Throwable throwable) {
			CivWikiDialogMwClient.LOGGER.error("Failed to copy dialog to MediaWiki template", throwable);
			SystemToast.addOrUpdate(
				this.civwiki$minecraft.getToastManager(),
				new SystemToast.SystemToastId(),
				Component.translatableWithFallback("civwikidialogmw.copy_failed_title", "Copy to MediaWiki Template failed"),
				Component.translatableWithFallback("civwikidialogmw.copy_failed_message",
					"See the client log for details: %s", throwable.toString())
			);
		}
	}
}