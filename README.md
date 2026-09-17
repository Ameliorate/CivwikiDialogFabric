# MC Dialog -> MediaWiki Template (Fabric)

A tiny Fabric mod for **Minecraft 26.1.2** that adds a third option —
**Copy to MediaWiki Template** — to the dialog debug screen. The debug screen is
the one you reach by clicking the **"!"** button next to a dialog's title (hover
text: *"This is a custom screen. Click here to learn more."*); stock it only has
**Disconnect** and **Back**.

Clicking **Copy to MediaWiki Template** converts the dialog that is currently on
screen into `{{MC dialog|...}}` wikitext (the format of the
[civwiki-dialog](../) template package), copies it to the OS clipboard, and shows
a toast. The screen stays open so you can keep hunting dialogs — every "!" opens
the warning screen with the extra button.

## Build

```sh
./gradlew build
```

Produces `build/libs/civwikidialogmw-1.0.0.jar`. Drops the jar into your
`mods/` folder (Fabric Loader >= 0.19.5, Java 25+, Minecraft 26.1.x). No
Fabric API dependency.

## How it works

- [`WarningScreenMixin`](src/client/java/civwiki/dialogmw/client/mixin/WarningScreenMixin.java)
  mixes into `net.minecraft.client.gui.screens.dialog.DialogScreen$WarningScreen`
  (the `ConfirmScreen` behind the "!" button). A constructor injection captures
  the originating `DialogScreen` (tucked inside the vanilla return-screen holder)
  and pulls the displayed `Dialog` out of it through a small accessor mixin
  ([`DialogAccessor`](src/client/java/civwiki/dialogmw/client/DialogAccessor.java)
  / [`DialogScreenMixin`](src/client/java/civwiki/dialogmw/client/mixin/DialogScreenMixin.java)).
  An `addButtons` override then appends the third button to the vanilla button row.
- [`MediaWikiDialogRenderer`](src/client/java/civwiki/dialogmw/client/MediaWikiDialogRenderer.java)
  converts the dialog record to wikitext. It mirrors the mapping described in the
  package README:

  | In-game dialog           | `{{MC dialog|...}}` mapping                              |
  | ------------------------ | ------------------------------------------------------- |
  | `notice`                 | content = message, footer = one OK button               |
  | `confirmation`           | content = question, footer = Yes + No buttons           |
  | `multi_action`           | content = body + inputs + action grid, footer = Back    |
  | `dialog_list`            | content = one button per sub-dialog, footer = Back      |
  | `server_links`           | content = one button per server link, footer = Back     |

  Every dialog gets `|warning = 1` because the "!" button is always present
  in-game. Body items, inputs (`checkbox`/`slider`/`field`/`choice`), tooltips
  (minetip `&x` codes, `\&` literal ampersands, `/` description line breaks),
  colors (`{{MC dialog/text|...}}` for the 16 classic named colors) and widths are
  carried over; the generated lines never start with a space, so MediaWiki won't
  render `<pre>` blocks (no dependency on the package's Lua `strip` helper).
- Clipboard: `ClipboardManager.setClipboard(window, ...)` — the same API the game
  uses for its own copy-to-clipboard action.

## In-game verification

The mod was verified live in 26.1.2: a `/dialog` notice was shown, the "!" was
clicked, **Copy to MediaWiki Template** was pressed, and the clipboard contained:

```wikitext
{{MC dialog| title = A| warning = 1| content = {{MC dialog/message|1=a|width=200}}| footer = {{MC dialog/button|label=Ok|width=150}}}}
```

A standalone smoke test of the renderer (`ComponentFormatting` +
`MediaWikiDialogRenderer`) against all dialog types produced output matching the
`sandbox_test.wikitext` examples from the template package.

## Files

| File | Purpose |
| ---- | ------- |
| `src/client/java/civwiki/dialogmw/client/WarningScreenMixin.java` (in `mixin/`) | third button, clipboard + toast |
| `src/client/java/civwiki/dialogmw/client/DialogScreenMixin.java` (in `mixin/`) | exposes the private `dialog` field |
| `src/client/java/civwiki/dialogmw/client/MediaWikiDialogRenderer.java` | `Dialog` -> `{{MC dialog|...}}` converter |
| `src/client/java/civwiki/dialogmw/client/ComponentFormatting.java` | Component -> wikitext / legacy-tooltip text |
| `src/client/resources/assets/civwikidialogmw/lang/en.json` | button + toast labels |