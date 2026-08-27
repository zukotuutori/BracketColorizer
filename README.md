# Bracket Colorizer

Bracket Colorizer adds rainbow brackets to JetBrains IDEs. Matching parentheses, square brackets and braces receive a color based on their nesting depth, which makes deeply nested code easier to follow.

The plugin reads the tokens produced by the current language's syntax highlighter instead of treating a file as plain text. It ignores recognized comments, strings, regular expression literals and markup text. If a language does not provide a working syntax highlighter, the plugin leaves that file unchanged.

## Features

* A separate color for every nesting level, with levels that can be added or removed freely.
* Ready made palettes taken from well known editor color schemes, instead of picking every color by hand.
* Own palettes saved as named templates, each of them editable, with a small manager to clean the list up again.
* Colors that either stop at the end of the palette or repeat for deeper nesting.
* Unmatched brackets highlighted in a color of their own, so a missing counterpart is easy to spot.
* Brackets optionally drawn in bold.
* Round, square and curly brackets on by default, angle brackets available for code that is heavy on templates and generics.
* A tool window that applies every change immediately, with a live preview and a legend.

## Which files are colorized

Bracket Colorizer works in every language the IDE can highlight, so the same palette applies whether a file is Kotlin, Python, Rust or JSON.

Two kinds of files are deliberately skipped. Plain text carries no nesting structure worth coloring, and in Markdown the ordinary link syntax `[text](url)` would light up on every line. Code fenced inside a Markdown file is a different language and is still colorized.

Brackets are also left alone wherever the language's own highlighter says they are not code. That covers comments, string and character literals, regular expressions, XML and HTML text, attribute values and CDATA sections.

## Configuration

Open the **Bracket Colorizer** tool window on the right side of the IDE to edit the colors while looking at your code. Changes made there are applied immediately. The same controls are available under **Settings | Editor | Bracket Colorizer**, where changes take effect after pressing **Apply** or **OK**.

Each nesting level can have its own color. Levels may be added or removed, brackets may be drawn in bold, and colors can either stop at the end of the configured palette or repeat for deeper nesting. Unmatched round, square and curly brackets can be highlighted with a separate color.

Colors come either from **Custom colors**, where every level is picked by hand, or from **From template**, which uses the bracket colors of a known editor color scheme: VS Code Dark+, VS Code Light+, VS Code High Contrast Dark, One Dark Pro, Dracula, Nord, or the Viasfora rainbow. A template replaces the level colors and the unmatched color; the hand picked colors are kept and come back when switching to **Custom colors** again. Templates define three to six colors, so the repeat option covers deeper nesting, the way those editors do it.

**Save as template** turns the colors currently picked by hand into a named template of your own, which then sits in the dropdown next to the built in ones.

A template covers more than the colors. It also carries which bracket types are colorized, whether the colors repeat, whether brackets are drawn in bold, and whether unmatched brackets are highlighted. Selecting a template ticks those boxes for you, and they stay editable: change one afterwards and your change simply applies, without being written back into the template. The master **Enable bracket colorizing** switch is never part of a template.

**Change template** opens the selected template for editing: its name, its colors, how many levels it has, its unmatched color, and the switches above. **Save** keeps the changes, **Discard** throws them away. Built in templates can be edited too.

**Customize templates** opens a small manager for the list: change the selected template, remove a single entry, delete every template at once, or put the built in ones back. Restoring the defaults undoes edits to the built in templates and adds back the ones that were deleted, while templates you saved yourself are left alone. With an empty list the plugin simply falls back to the custom colors.

Clicking a color chip opens the IDE color picker, which accepts hex and RGB values. The editor follows along while the picker is open, so a color can be judged in place before it is confirmed.

Ticking a bracket type while none was selected switches bracket colorizing back on, since it was only turned off because there was nothing left to color. While colorizing is off, the switch labels itself in red.

Round, square and curly brackets are enabled by default. Angle brackets are optional because operators such as `a < b` are ambiguous. Common comparison operators with whitespace on both sides are ignored. Compact comparisons can still be indistinguishable from type syntax, so angle brackets remain disabled by default. Their nesting stack never affects the colors of the other bracket types, and an angle bracket without a counterpart is left uncolored.

Settings are stored per installation and apply to every project.

## Requirements

Bracket Colorizer supports JetBrains IDEs based on platform build 252, corresponding to version 2025.2, or newer. It is built against IntelliJ IDEA Community and runs in every JetBrains IDE that provides the platform and language modules it declares, including PyCharm, WebStorm, GoLand, CLion, PhpStorm, RubyMine and Rider.

## Installation

Download or build the plugin archive, then open **Settings | Plugins**, click the gear icon and choose **Install Plugin from Disk**. Select the ZIP file and restart the IDE if prompted.

## Building

A recent JDK is enough to start the Gradle wrapper. The build resolves and targets a Java 21 toolchain.

```shell
./gradlew test
./gradlew buildPlugin
./gradlew runIde
```

`test` runs the indexing, highlighting and settings tests. `buildPlugin` creates the installable archive in `build/distributions`. `runIde` starts a sandboxed IntelliJ IDEA instance with the plugin installed.

To check the plugin against the JetBrains compatibility rules before publishing:

```shell
./gradlew verifyPlugin
```

## Credits

The default color palette is based on [Viasfora](https://github.com/tomasr/viasfora).

## License

Bracket Colorizer is available under the [MIT License](LICENSE).
