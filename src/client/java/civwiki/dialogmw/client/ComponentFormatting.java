package civwiki.dialogmw.client;

import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Helpers for turning in-game {@link Component}s into the two text flavours the
 * wiki templates understand:
 *
 * <ul>
 *   <li><b>wikitext</b> -- for titles, messages and labels. Styled substrings become
 *       {@code {{MC dialog/text|color=...|bold=yes|1=...}}} invocations, everything is
 *       HTML-entity escaped for safe MediaWiki parameter values.</li>
 *   <li><b>legacy tooltip</b> -- for button/choice tooltips (the {@code &0}-{@code &f}
 *       codes the minetip script renders), with {@code \&} escaped ampersands and
 *       {@code /} line breaks in the description part.</li>
 * </ul>
 */
public final class ComponentFormatting {

	/** The 16 classic Minecraft colors, rgb -> name (same palette the wiki templates use). */
	private static final Map<Integer, String> COLOR_NAMES = Map.ofEntries(
		Map.entry(0x000000, "black"),
		Map.entry(0x0000AA, "dark_blue"),
		Map.entry(0x00AA00, "dark_green"),
		Map.entry(0x00AAAA, "dark_aqua"),
		Map.entry(0xAA0000, "dark_red"),
		Map.entry(0xAA00AA, "dark_purple"),
		Map.entry(0xFFAA00, "gold"),
		Map.entry(0xAAAAAA, "gray"),
		Map.entry(0x555555, "dark_gray"),
		Map.entry(0x5555FF, "blue"),
		Map.entry(0x55FF55, "green"),
		Map.entry(0x55FFFF, "aqua"),
		Map.entry(0xFF5555, "red"),
		Map.entry(0xFF55FF, "light_purple"),
		Map.entry(0xFFFF55, "yellow"),
		Map.entry(0xFFFFFF, "white")
	);

	/** The 16 classic Minecraft colors, rgb -> legacy code char (without the '&'). */
	private static final Map<Integer, Character> COLOR_CODES = Map.ofEntries(
		Map.entry(0x000000, '0'),
		Map.entry(0x0000AA, '1'),
		Map.entry(0x00AA00, '2'),
		Map.entry(0x00AAAA, '3'),
		Map.entry(0xAA0000, '4'),
		Map.entry(0xAA00AA, '5'),
		Map.entry(0xFFAA00, '6'),
		Map.entry(0xAAAAAA, '7'),
		Map.entry(0x555555, '8'),
		Map.entry(0x5555FF, '9'),
		Map.entry(0x55FF55, 'a'),
		Map.entry(0x55FFFF, 'b'),
		Map.entry(0xFF5555, 'c'),
		Map.entry(0xFF55FF, 'd'),
		Map.entry(0xFFFF55, 'e'),
		Map.entry(0xFFFFFF, 'f')
	);

	private ComponentFormatting() {
	}

	/**
	 * Renders a component as inline wikitext. Plain runs are emitted as-is (escaped),
	 * styled runs are wrapped in {@code {{MC dialog/text|...}}}.
	 */
	public static String toWikitext(Component component) {
		if (component == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (Component node : component.toFlatList()) {
			String text = escapeWikitext(node.getString());
			if (text.isEmpty()) {
				continue;
			}
			Style style = node.getStyle();
			String color = colorName(style);
			boolean bold = style.isBold();
			boolean italic = style.isItalic();
			boolean underlined = style.isUnderlined();
			boolean strikethrough = style.isStrikethrough();
			if (color == null && !bold && !italic && !underlined && !strikethrough) {
				out.append(text);
				continue;
			}
			out.append("{{MC dialog/text|");
			if (color != null) {
				out.append("color=").append(color).append('|');
			}
			if (bold) {
				out.append("bold=yes|");
			}
			if (italic) {
				out.append("italic=yes|");
			}
			if (underlined) {
				out.append("underlined=yes|");
			}
			if (strikethrough) {
				out.append("strikethrough=yes|");
			}
			out.append("1=").append(text).append("}}");
		}
		return out.toString();
	}

	/**
	 * Renders a component as a single-line legacy-format string (for minetip tooltips).
	 * Named colors become {@code &x} codes; a custom hex color cannot be expressed in
	 * the legacy format and is dropped for that run.
	 */
	public static String toTooltip(Component component) {
		if (component == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (Component node : component.toFlatList()) {
			String text = escapeTooltip(node.getString());
			if (text.isEmpty()) {
				continue;
			}
			String codes = legacyCodes(node.getStyle());
			if (codes.isEmpty()) {
				out.append(text);
			} else {
				out.append(codes).append(text).append("&r");
			}
		}
		return out.toString();
	}

	/**
	 * Splits a component on newlines. The first line is the tooltip's main line,
	 * the rest are joined with the wiki's {@code /} description separator.
	 */
	public static String[] splitTooltipLines(Component component) {
		return toTooltip(component).split("\n", -1);
	}

	private static String legacyCodes(Style style) {
		StringBuilder codes = new StringBuilder();
		TextColor color = style.getColor();
		if (color != null) {
			Character code = COLOR_CODES.get(color.getValue());
			if (code != null) {
				codes.append('&').append(code);
			}
		}
		if (style.isBold()) {
			codes.append("&l");
		}
		if (style.isItalic()) {
			codes.append("&o");
		}
		if (style.isUnderlined()) {
			codes.append("&n");
		}
		if (style.isStrikethrough()) {
			codes.append("&m");
		}
		if (style.isObfuscated()) {
			codes.append("&k");
		}
		return codes.toString();
	}

	private static String colorName(Style style) {
		TextColor color = style.getColor();
		if (color == null) {
			return null;
		}
		int rgb = color.getValue();
		String name = COLOR_NAMES.get(rgb);
		if (name != null) {
			return name;
		}
		return String.format("#%06x", rgb);
	}

	/** Escapes text for safe use inside MediaWiki template parameter values. */
	public static String escapeWikitext(String text) {
		return text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("|", "{{!}}")
			.replace("{{", "<nowiki>{{</nowiki>")
			.replace("}}", "<nowiki>}}</nowiki>");
	}

	/** Escapes text for the minetip tooltip format ({@code \&} literal ampersand). */
	public static String escapeTooltip(String text) {
		return text
			.replace("\\", "\\\\")
			.replace("&", "\\&");
	}
}