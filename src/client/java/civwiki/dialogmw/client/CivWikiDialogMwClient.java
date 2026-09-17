package civwiki.dialogmw.client;

import net.fabricmc.api.ClientModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point for the "MC Dialog -> MediaWiki Template" mod.
 *
 * <p>All the actual work happens in {@link civwiki.dialogmw.client.mixin.WarningScreenMixin}:
 * a third option ("Copy to MediaWiki Template") is added to the dialog warning/debug screen
 * (the one reached by clicking the "!" next to a dialog's title), next to the vanilla
 * "Disconnect" and "Back" buttons. Clicking it converts the dialog that is currently on
 * screen into {@code {{MC dialog|...}}} wikitext and puts it on the OS clipboard.</p>
 */
public final class CivWikiDialogMwClient implements ClientModInitializer {

	public static final String MOD_ID = "civwikidialogmw";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("{} loaded: dialog -> MediaWiki template copier ready!", MOD_ID);
	}
}