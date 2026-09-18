package civwiki.dialogmw.client;

import java.util.Map;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.network.chat.contents.objects.ObjectInfo;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.resources.Identifier;

/**
 * Helpers for turning in-game {@link Component}s into the text flavours the wiki
 * templates understand.
 *
 * <ul>
 *   <li><b>wikitext text</b> ({@link #wikiParam}) -- for titles, messages and labels.
 *       The body is wrapped in {@code <nowiki>...</nowiki>} so anything the game text
 *       happens to contain -- {@code [[…]]}, {@code {{…}}}, pipes, line-start bullets,
 *       headings, italics … -- renders literally instead of being interpreted as wiki
 *       syntax. MediaWiki trims whitespace at the very edges of template arguments
 *       before any template sees it (trailing space is eaten, e.g. "Help: " -> "Help:"),
 *       so any leading/trailing spaces are re-emitted as {@code &#32;} entities, which
 *       survive argument trimming and decode to spaces in the rendered HTML. Text that
 *       itself contains a nowiki marker (it would terminate the block early) falls back
 *       to per-character entity escaping, which is equally literal.</li>
 *   <li><b>legacy tooltip</b> ({@link #toTooltip}/{@link #tooltipParam}) -- for button/choice
 *       tooltips (the {@code &0}-{@code &f} codes the minetip script renders). These live
 *       inside HTML attributes, so they are never nowiki-wrapped (the tags would not be
 *       stripped there); style codes are emitted verbatim so minetip applies them, while
 *       literal ampersands in the text are escaped as {@code \&} (minetip's
 *       literal-ampersand convention), backslashes as {@code \\}, pipes as {@code {{!}}},
 *       literal {@code /} as {@code \/} on description lines (minetip turns a plain
 *       {@code /} in the description into a line break), and edge spaces again as
 *       {@code &#32;}.</li>
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

	/** The glyph object (sprite/head) contents flatten to in plain text (U+FFFC). */
	private static final String OBJECT_PLACEHOLDER = "\uFFFC";

	private ComponentFormatting() {
	}

	/**
	 * Renders a component as inline wikitext. Plain runs become literal text,
	 * styled runs are wrapped in {@code {{MC dialog/text|...}}}; every text run
	 * goes through {@link #wikiParam} so it stays exactly as authored. Runs
	 * whose style carries a SHOW_TEXT hover event get their hover text appended
	 * as {@code |tooltip=} / {@code |tooltip_desc=} parameters (the same
	 * minetip contract as button tooltips). Object components are emitted as
	 * raw wiki template calls: atlas sprites as {@code {{ItemSprite|…}}},
	 * player heads as {@code {{playericon|…}}} (see {@link #objectWikiCall}).
	 */
	public static String toWikitext(Component component) {
		if (component == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		appendWikiRuns(component, Style.EMPTY, out);
		return out.toString();
	}

	/**
	 * Walks the component tree exactly like the vanilla flattener — the same
	 * {@code style.applyTo(...)} merge chain, per-content text visits, then
	 * siblings in order — but appends rendered wikitext on the way. Object
	 * components become raw template calls instead of their {@code \uFFFC}
	 * placeholder text. Runs are emitted one per content node, identical to
	 * {@code toFlatList()}'s granularity, so styling is unchanged.
	 */
	private static void appendWikiRuns(Component node, Style inherited, StringBuilder out) {
		Style merged = inherited.applyTo(node.getStyle());
		if (node.getContents() instanceof ObjectContents object) {
			out.append(objectWikiCall(object.contents()));
		} else {
			node.getContents().visit((Style style, String text) -> {
				appendTextRun(out, text, style);
				return Optional.empty();
			}, merged);
		}
		for (Component child : node.getSiblings()) {
			appendWikiRuns(child, merged, out);
		}
	}

	/** Renders one text run: wikiParam'd literal text, or a /text wrapper when styled. */
	private static void appendTextRun(StringBuilder out, String rawText, Style style) {
		String text = wikiParam(rawText);
		if (text.isEmpty()) {
			return;
		}
		String color = colorName(style);
		boolean bold = style.isBold();
		boolean italic = style.isItalic();
		boolean underlined = style.isUnderlined();
		boolean strikethrough = style.isStrikethrough();
		Optional<Component> hover = hoverText(style);
		if (color == null && !bold && !italic && !underlined && !strikethrough && hover.isEmpty()) {
			out.append(text);
			return;
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
		boolean tipEmitted = false;
		if (hover.isPresent()) {
			String tip = ComponentFormatting.tooltipParams(hover.get());
			if (tip != null) {
				out.append(tip);
				tipEmitted = true;
			}
		}
		// The style block (or the template's opening pipe) always leaves a
		// separator behind; a tooltip fragment does not, so restore one.
		if (tipEmitted) {
			out.append('|');
		}
		out.append("1=").append(text).append("}}");
	}

	/** The SHOW_TEXT hover component carried by a style, if any. */
	private static Optional<Component> hoverText(Style style) {
		HoverEvent hover = style.getHoverEvent();
		if (hover instanceof HoverEvent.ShowText showText) {
			return Optional.ofNullable(showText.value());
		}
		return Optional.empty();
	}

	/**
	 * Emits the wiki template invocation for an object component: atlas sprites
	 * as {@code {{ItemSprite|…}}}, player heads as {@code {{playericon|…}}}. A
	 * player head with no name (e.g. a material-placeholder head generated by
	 * MaterialSpritesGenerator) falls back to the plain player-head item sprite.
	 */
	private static String objectWikiCall(ObjectInfo info) {
		if (info instanceof AtlasSprite sprite) {
			return "{{ItemSprite|" + atlasSpriteItemId(sprite.sprite()) + "}}";
		}
		if (info instanceof PlayerSprite player) {
			Optional<String> name = player.player().name();
			if (name.isPresent() && !name.get().isBlank()) {
				return "{{playericon|" + name.get() + "}}";
			}
			return "{{ItemSprite|player-head}}";
		}
		return "";
	}

	/**
	 * Item id (wiki form) for an atlas sprite. {@code minecraft:item/...} and
	 * {@code minecraft:block/...} sprites map straight from their path; anything
	 * else (banner map decorations, the shield slot icon, particle and
	 * decorated-pot sprites, custom atlases) is looked up in the
	 * MaterialSpritesGenerator mapping, falling back to the dashed path when
	 * the sprite is not a known material sprite.
	 */
	private static String atlasSpriteItemId(Identifier sprite) {
		String path = sprite.getPath();
		if (path.startsWith("item/") || path.startsWith("block/")) {
			return itemSpriteName(sprite);
		}
		String material = MaterialSprites.itemId(sprite.toString());
		return material != null ? material : itemSpriteName(sprite);
	}

	/** {@code minecraft:item/iron_helmet} -> {@code iron-helmet} (wiki ItemSprite id). */
	private static String itemSpriteName(Identifier sprite) {
		String path = sprite.getPath();
		if (path.startsWith("item/")) {
			path = path.substring("item/".length());
		} else if (path.startsWith("block/")) {
			path = path.substring("block/".length());
		}
		String id = path.replace('_', '-');
		return "minecraft".equals(sprite.getNamespace()) ? id : sprite.getNamespace() + ":" + id;
	}

	/**
	 * Builds the {@code tooltip=} / {@code tooltip_desc=} parameter fragment
	 * (without surrounding pipes) for a hover component (a {@code SHOW_TEXT}
	 * hover event), returning {@code null} when there is nothing to show. The
	 * first line becomes the main tooltip text; each remaining line is prefixed
	 * with the wiki's {@code /} line-break separator, so the description always
	 * starts on its own line under the tooltip title.
	 * Callers add their own {@code |} separators, so the fragment is safe to
	 * splice into any parameter position.
	 */
	public static String tooltipParams(Component tooltip) {
		if (tooltip == null) {
			return null;
		}
		String[] lines = splitTooltipLines(tooltip);
		StringBuilder out = new StringBuilder();
		if (lines.length > 0 && !lines[0].isEmpty()) {
			out.append("tooltip=").append(tooltipParam(lines[0]));
		}
		if (lines.length > 1) {
			StringBuilder rest = new StringBuilder();
			for (int i = 1; i < lines.length; i++) {
				// Every description line is prefixed with a '/': minetip renders a
				// plain '/' as <br>, so the first description line moves onto its
				// own line below the tooltip title instead of gluing onto it (the
				// wiki's title/description spans are inline by default).
				rest.append('/').append(tooltipDescParam(lines[i]));
			}
			if (!rest.isEmpty()) {
				if (!out.isEmpty()) {
					out.append('|');
				}
				out.append("tooltip_desc=").append(rest);
			}
		}
		return out.isEmpty() ? null : out.toString();
	}

	/**
	 * Renders a component as a raw legacy-format string (colors/bold/... as {@code &x}
	 * codes, newlines preserved), with each text run already escaped for minetip
	 * (literal {@code &} as {@code \&}, {@code \} as {@code \\}, {@code |} as
	 * {@code {{!}}}). Callers split it into lines and run each through {@link #tooltipParam}
	 * before inserting it into a template argument. The codes are emitted verbatim
	 * <em>after</em> the per-run escaping, so they stay {@code &x} instead of being
	 * mangled into {@code \&x} (which minetip would render as literal text), and are
	 * re-emitted at the start of every line of a run so multi-line tooltips keep their
	 * styling once the output is split on newlines.
	 */
	public static String toTooltip(Component component) {
		if (component == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (Component node : component.toFlatList()) {
			String text = node.getString();
			if (text.isEmpty() || text.equals(OBJECT_PLACEHOLDER)) {
				// Object (sprite/head) contents flatten to the U+FFFC placeholder
				// glyph; wiki templates cannot live inside attribute values, so
				// there is simply nothing to copy for them here.
				continue;
			}
			String codes = legacyCodes(node.getStyle());
			if (codes.isEmpty()) {
				out.append(escapeTooltip(text));
				continue;
			}
			// Re-emit the codes at the start of every line of the run: callers split
			// on "\n", and a styled run spanning several lines would otherwise lose
			// its styling on every line after the first.
			String escaped = escapeTooltip(text);
			int from = 0;
			while (true) {
				int nl = escaped.indexOf('\n', from);
				if (nl < 0) {
					if (from < escaped.length()) {
						out.append(codes).append(escaped, from, escaped.length()).append("&r");
					}
					break;
				}
				if (from < nl) {
					out.append(codes).append(escaped, from, nl).append("&r");
				}
				out.append('\n');
				from = nl + 1;
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

	/**
	 * Formats an already-escaped tooltip line as a template parameter value.
	 * The line must already be in minetip form (as produced by {@link #toTooltip} or
	 * {@link #rawTooltipParam}): color/format codes as {@code &x}, literal ampersands
	 * as {@code \\&}, backslashes as {@code \\\\}, pipes as {@code {{!}}}. This step only
	 * re-emits edge whitespace as {@code &#32;}-style entities (argument trimming would
	 * otherwise eat it); the body is passed through verbatim, because re-escaping the
	 * ampersands would turn the {@code &x} color codes into {@code \\&x} literal text
	 * and double-escape the {@code \\&}/{@code \\\\} sequences.
	 */
	public static String tooltipParam(String escapedLine) {
		if (escapedLine == null || escapedLine.isEmpty()) {
			return "";
		}
		int start = 0;
		int end = escapedLine.length();
		while (start < end && isEdgeWhitespace(escapedLine.charAt(start))) {
			start++;
		}
		while (end > start && isEdgeWhitespace(escapedLine.charAt(end - 1))) {
			end--;
		}
		StringBuilder out = new StringBuilder();
		appendEdgeWhitespace(out, escapedLine, 0, start);
		if (end > start) {
			out.append(escapedLine, start, end);
		}
		appendEdgeWhitespace(out, escapedLine, end, escapedLine.length());
		return out.toString();
	}

	/**
	 * Formats an already-escaped tooltip <em>description</em> line as a template
	 * parameter value. Same as {@link #tooltipParam}, but literal {@code /} characters
	 * are additionally escaped as {@code \/}: the wiki's minetip script renders every
	 * {@code /} inside {@code |tooltip_desc=} as a line break, so a slash that is part of
	 * the tooltip text (e.g. "5/10") would otherwise split the description into extra
	 * lines. Only description lines (the ones joined with {@code /}) go through this;
	 * in the first line ({@code |tooltip=}) a {@code /} is already plain text, and
	 * escaping it would show a stray backslash.
	 */
	public static String tooltipDescParam(String escapedLine) {
		return tooltipParam(escapedLine).replace("/", "\\/");
	}

	/**
	 * Escapes a raw string (not a {@link Component}, e.g. a server-link URL) into a
	 * minetip tooltip line and formats it as a template parameter value: identical to
	 * {@link #tooltipParam} but with {@link #escapeTooltip} applied to the body first.
	 */
	public static String rawTooltipParam(String rawText) {
		if (rawText == null || rawText.isEmpty()) {
			return "";
		}
		return tooltipParam(escapeTooltip(rawText));
	}

	/**
	 * Escapes a text run for use inside a template parameter value (title, message,
	 * label, styled-text content, ...). Leading/trailing whitespace is preserved as
	 * entities; the middle is wrapped in {@code <nowiki>} so every character is
	 * literal. Text containing a nowiki marker takes the entity-fallback path, which
	 * is equally literal and leaves the markup readable for wiki editors.
	 */
	public static String wikiParam(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		int start = 0;
		int end = text.length();
		while (start < end && isEdgeWhitespace(text.charAt(start))) {
			start++;
		}
		while (end > start && isEdgeWhitespace(text.charAt(end - 1))) {
			end--;
		}
		StringBuilder out = new StringBuilder();
		appendEdgeWhitespace(out, text, 0, start);
		if (end > start) {
			String mid = text.substring(start, end);
			if (mid.contains("<nowiki") || mid.contains("</nowiki")) {
				out.append(escapeAll(mid));
			} else {
				out.append("<nowiki>").append(mid).append("</nowiki>");
			}
		}
		appendEdgeWhitespace(out, text, end, text.length());
		return out.toString();
	}

	// -------------------------------------------------------------- internal

	private static boolean isEdgeWhitespace(char c) {
		return c == ' ' || c == '\t' || c == '\n' || c == '\r';
	}

	private static void appendEdgeWhitespace(StringBuilder out, String text, int from, int to) {
		for (int i = from; i < to; i++) {
			switch (text.charAt(i)) {
				case ' ' -> out.append("&#32;");
				case '\t' -> out.append("&#9;");
				default -> out.append("&#10;");
			}
		}
	}

	/** Escapes literal text for the minetip format: {@code \\} literal backslash, {@code \&} literal amp, {@code {{!}}} pipe. */
	private static String escapeTooltip(String text) {
		return text
			.replace("\\", "\\\\")
			.replace("&", "\\&")
			.replace("|", "{{!}}");
	}

	/**
	 * Fallback for text that contains a nowiki marker: per-character entity escaping.
	 * Entities are decoded only when the HTML is produced, long after the wikitext
	 * parser has run, so every character renders literally.
	 */
	private static String escapeAll(String text) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '&' -> out.append("&amp;");
				case '<' -> out.append("&lt;");
				case '>' -> out.append("&gt;");
				case '[' -> out.append("&#91;");
				case ']' -> out.append("&#93;");
				case '{' -> out.append("&#123;");
				case '}' -> out.append("&#125;");
				case '|' -> out.append("{{!}}");
				case '\'' -> out.append("&#39;");
				case '*' -> out.append("&#42;");
				case '#' -> out.append("&#35;");
				case '=' -> out.append("&#61;");
				case ':' -> out.append("&#58;");
				case ';' -> out.append("&#59;");
				default -> out.append(c);
			}
		}
		return out.toString();
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
}