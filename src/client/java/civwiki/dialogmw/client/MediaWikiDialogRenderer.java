package civwiki.dialogmw.client;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import net.minecraft.core.Holder;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogListDialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.ServerLinksDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.InputControl;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Converts the in-memory server dialog ({@link Dialog}) into the
 * {@code {{MC dialog|...}}} wikitext used by the civwiki template set
 * (see the README of the civwiki-dialog package).
 *
 * <p>Mapping (mirrors the package README):</p>
 * <ul>
 *   <li>notice        -> content = message, footer = OK button</li>
 *   <li>confirmation  -> content = question, footer = Yes + No buttons</li>
 *   <li>multi_action  -> content = body + inputs + action button grid, footer = Back</li>
 *   <li>dialog_list   -> content = one button per sub-dialog, footer = Back</li>
 *   <li>server_links  -> content = one button per server link, footer = Back</li>
 * </ul>
 *
 * <p>An in-game dialog always shows the "!" warning button, so {@code |warning = 1}
 * is always emitted.</p>
 */
public final class MediaWikiDialogRenderer {

	private MediaWikiDialogRenderer() {
	}

	/** Renders the dialog as a complete {@code {{MC dialog|...}}} invocation. */
	public static String render(Dialog dialog, net.minecraft.client.gui.screens.dialog.DialogConnectionAccess connectionAccess) {

		CommonDialogData common = dialog.common();

		StringBuilder bodyWiki = new StringBuilder();
		for (DialogBody body : common.body()) {
			String piece = renderBody(body);
			if (!piece.isEmpty()) {
				bodyWiki.append(piece).append('\n');
			}
		}

		StringBuilder inputsWiki = new StringBuilder();
		for (Input input : common.inputs()) {
			String piece = renderInput(input);
			if (!piece.isEmpty()) {
				inputsWiki.append(piece).append('\n');
			}
		}

		String actionsWiki = renderActions(dialog, connectionAccess);
		String footerWiki = renderFooter(dialog, connectionAccess);

		StringBuilder out = new StringBuilder("{{MC dialog\n");
		out.append("| title = ").append(ComponentFormatting.toWikitext(common.title())).append('\n');
		out.append("| warning = 1\n");

		StringBuilder content = new StringBuilder();
		content.append(bodyWiki);
		content.append(inputsWiki);
		if (!actionsWiki.isEmpty()) {
			content.append(actionsWiki).append('\n');
		}
		if (!content.isEmpty()) {
			// Keep every line at column 0: a line starting with a space would be
			// rendered as a <pre> block by MediaWiki (the package's Lua strip helper
			// exists for exactly that reason).
			String trimmed = content.toString();
			while (trimmed.endsWith("\n")) {
				trimmed = trimmed.substring(0, trimmed.length() - 1);
			}
			out.append("| content = ").append(trimmed).append('\n');
		}

		if (!footerWiki.isEmpty()) {
			out.append("| footer = ").append(footerWiki).append('\n');
		}
		out.append("}}");
		return out.toString();
	}

	// ------------------------------------------------------------------ body

	private static String renderBody(DialogBody body) {
		if (body instanceof PlainMessage message) {
			StringBuilder sb = new StringBuilder("{{MC dialog/message|1=");
			sb.append(ComponentFormatting.toWikitext(message.contents()));
			if (message.width() > 0) {
				sb.append("|width=").append(message.width());
			}
			sb.append("}}");
			return sb.toString();
		}
		if (body instanceof ItemBody item) {
			StringBuilder sb = new StringBuilder("{{MC dialog/item|icon=");
			sb.append(iconFileName(item.item()));
			item.description().ifPresent(desc -> sb.append("|description=").append(ComponentFormatting.toWikitext(desc.contents())));
			sb.append("}}");
			return sb.toString();
		}
		return "";
	}

	/** The wiki convention for the item icon: "minecraft:golden_apple" -> "Golden Apple.png". */
	private static String iconFileName(ItemStackTemplate item) {
		String registered = item.item().getRegisteredName();
		String path = registered.contains(":") ? registered.substring(registered.indexOf(':') + 1) : registered;
		String[] words = path.split("_");
		StringBuilder name = new StringBuilder();
		for (String word : words) {
			if (word.isEmpty()) {
				continue;
			}
			if (!name.isEmpty()) {
				name.append(' ');
			}
			name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return name + ".png";
	}

	// ----------------------------------------------------------------- input

	private static String renderInput(Input input) {
		InputControl control = input.control();
		if (control instanceof BooleanInput bool) {
			StringBuilder sb = new StringBuilder("{{MC dialog/checkbox|label=");
			sb.append(ComponentFormatting.toWikitext(bool.label()));
			if (bool.initial()) {
				sb.append("|checked=1");
			}
			sb.append("}}");
			return sb.toString();
		}
		if (control instanceof NumberRangeInput range) {
			NumberRangeInput.RangeInfo info = range.rangeInfo();
			float start = info.start();
			float end = info.end();
			float value = info.initial().orElse(start);
			int pos = (int) Math.round((value - start) / (end - start) * 100);
			if (pos < 0) {
				pos = 0;
			}
			if (pos > 100) {
				pos = 100;
			}
			StringBuilder sb = new StringBuilder("{{MC dialog/slider|label=");
			sb.append(ComponentFormatting.toWikitext(range.label()));
			sb.append("|value=").append(formatNumber(value));
			sb.append("|pos=").append(pos);
			if (range.width() > 0) {
				sb.append("|width=").append(range.width());
			}
			sb.append("}}");
			return sb.toString();
		}
		if (control instanceof SingleOptionInput choice) {
			Optional<SingleOptionInput.Entry> initial = choice.initial();
			SingleOptionInput.Entry shown;
			if (initial.isPresent()) {
				shown = initial.get();
			} else {
				shown = choice.entries().isEmpty() ? null : choice.entries().get(0);
			}
			StringBuilder sb = new StringBuilder("{{MC dialog/choice");
			if (choice.labelVisible()) {
				sb.append("|label=").append(ComponentFormatting.toWikitext(choice.label()));
			}
			if (shown != null) {
				sb.append("|value=").append(ComponentFormatting.toWikitext(shown.displayOrDefault()));
			}
			if (choice.width() > 0) {
				sb.append("|width=").append(choice.width());
			}
			sb.append("}}");
			return sb.toString();
		}
		if (control instanceof TextInput field) {
			StringBuilder sb = new StringBuilder("{{MC dialog/field|label=");
			sb.append(ComponentFormatting.toWikitext(field.label()));
			if (!field.initial().isEmpty()) {
				sb.append("|1=").append(ComponentFormatting.wikiParam(field.initial()));
			}
			Optional<TextInput.MultilineOptions> multiline = field.multiline();
			if (multiline.isPresent()) {
				sb.append("|multiline=yes");
				multiline.get().height().ifPresent(height -> {
					// Template computes height = 9 * lines + 8; invert it.
					int lines = Math.max(1, (height - 8) / 9);
					sb.append("|lines=").append(lines);
				});
			}
			if (field.width() > 0) {
				sb.append("|width=").append(field.width());
			}
			sb.append("}}");
			return sb.toString();
		}
		return "";
	}

	// --------------------------------------------------------------- actions

	private static String renderActions(Dialog dialog, net.minecraft.client.gui.screens.dialog.DialogConnectionAccess connectionAccess) {
		if (dialog instanceof MultiActionDialog multi) {
			return renderButtons(multi.actions().stream()
				.map(MediaWikiDialogRenderer::renderActionButton)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList()), multi.columns());
		}
		if (dialog instanceof DialogListDialog list) {
			List<String> buttons = new java.util.ArrayList<>();
			for (Holder<Dialog> holder : list.dialogs()) {
				if (!holder.isBound()) {
					continue;
				}
				Dialog sub = holder.value();
				if (sub == null) {
					continue;
				}
				buttons.add("{{MC dialog/button|label="
					+ ComponentFormatting.toWikitext(sub.common().computeExternalTitle())
					+ (list.buttonWidth() > 0 ? "|width=" + list.buttonWidth() : "")
					+ "}}");
			}
			return renderButtons(buttons, list.columns());
		}
		if (dialog instanceof ServerLinksDialog serverLinks) {
			List<String> buttons = new java.util.ArrayList<>();
			for (ServerLinks.Entry entry : connectionAccess.serverLinks().entries()) {
				buttons.add("{{MC dialog/button|label="
					+ ComponentFormatting.toWikitext(entry.displayName())
					+ "|tooltip=" + ComponentFormatting.tooltipParam(entry.link().toString())
					+ (serverLinks.buttonWidth() > 0 ? "|width=" + serverLinks.buttonWidth() : "")
					+ "}}");
			}
			return renderButtons(buttons, serverLinks.columns());
		}
		return "";
	}

	private static String renderButtons(List<String> buttons, int columns) {
		if (buttons.isEmpty()) {
			return "";
		}
		return "{{MC dialog/actions|columns=" + columns + "|" + String.join(" ", buttons) + "}}";
	}

	// ---------------------------------------------------------------- footer

	private static String renderFooter(Dialog dialog, net.minecraft.client.gui.screens.dialog.DialogConnectionAccess connectionAccess) {
		if (dialog instanceof NoticeDialog notice) {
			return renderActionButton(notice.action());
		}
		if (dialog instanceof ConfirmationDialog confirmation) {
			String yes = renderActionButton(confirmation.yesButton());
			String no = renderActionButton(confirmation.noButton());
			return (yes + " " + no).trim();
		}
		if (dialog instanceof MultiActionDialog multi) {
			return multi.exitAction().map(MediaWikiDialogRenderer::renderActionButton).orElse("");
		}
		if (dialog instanceof DialogListDialog list) {
			return list.exitAction().map(MediaWikiDialogRenderer::renderActionButton).orElse("");
		}
		if (dialog instanceof ServerLinksDialog serverLinks) {
			return serverLinks.exitAction().map(MediaWikiDialogRenderer::renderActionButton).orElse("");
		}
		return "";
	}

	// ---------------------------------------------------------------- button

	private static String renderActionButton(ActionButton actionButton) {
		CommonButtonData data = actionButton.button();
		StringBuilder sb = new StringBuilder("{{MC dialog/button|label=");
		sb.append(ComponentFormatting.toWikitext(data.label()));
		data.tooltip().ifPresent(tooltip -> {
			String[] lines = ComponentFormatting.splitTooltipLines(tooltip);
			if (lines.length > 0 && !lines[0].isEmpty()) {
				sb.append("|tooltip=").append(ComponentFormatting.tooltipParam(lines[0]));
			}
			if (lines.length > 1) {
				StringBuilder rest = new StringBuilder();
				for (int i = 1; i < lines.length; i++) {
					if (i > 1) {
						rest.append('/');
					}
					rest.append(ComponentFormatting.tooltipParam(lines[i]));
				}
				if (!rest.isEmpty()) {
					sb.append("|tooltip_desc=").append(rest);
				}
			}
		});
		if (data.width() > 0) {
			sb.append("|width=").append(data.width());
		}
		sb.append("}}");
		return sb.toString();
	}

	// ----------------------------------------------------------------- misc

	/** 30 -> "30", 1.5 -> "1.5" -- no trailing ".0". */
	private static String formatNumber(float value) {
		if (value == Math.floor(value) && !Float.isInfinite(value)) {
			return String.valueOf((int) value);
		}
		return String.valueOf(value);
	}
}