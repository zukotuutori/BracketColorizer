# Bracket Colorizer

Bracket Colorizer adds rainbow brackets to JetBrains IDEs. Matching parentheses, square brackets and braces receive a color based on their nesting depth, which makes deeply nested code easier to follow.

The plugin reads the tokens produced by the current language's syntax highlighter instead of treating a file as plain text. It ignores recognized comments, strings, regular expression literals and markup text. If a language does not provide a working syntax highlighter, the plugin leaves that file unchanged.

## Configuration

Open the **Bracket Colorizer** tool window on the right side of the IDE to edit the colors while looking at your code. Changes made there are applied immediately. The same controls are available under **Settings | Editor | Bracket Colorizer**, where changes take effect after pressing **Apply** or **OK**.

Each nesting level can have its own color. Levels may be added or removed, brackets may be drawn in bold, and colors can either stop at the end of the configured palette or repeat for deeper nesting. Unmatched round, square and curly brackets can be highlighted with a separate color.

Round, square and curly brackets are enabled by default. Angle brackets are optional because operators such as `a < b` are ambiguous. Common comparison operators with whitespace on both sides are ignored. Compact comparisons can still be indistinguishable from type syntax, so angle brackets remain disabled by default. Their nesting stack never affects the colors of the other bracket types, and an angle bracket without a counterpart is left uncolored.

## Installation

Build the plugin, then open **Settings | Plugins**, click the gear icon and choose **Install Plugin from Disk**. Select the generated ZIP file from `build/distributions` and restart the IDE when prompted.

Bracket Colorizer supports JetBrains IDEs based on platform build 252, corresponding to version 2025.2, or newer.

## Development

A recent JDK is enough to start the Gradle wrapper. The build resolves and targets a Java 21 toolchain.

```shell
./gradlew test
./gradlew buildPlugin
./gradlew runIde
```

`test` runs the indexing, highlighting and settings tests. `buildPlugin` creates the installable archive in `build/distributions`. `runIde` starts a sandboxed IntelliJ IDEA instance with the plugin installed.

The default color palette is based on [Viasfora](https://github.com/tomasr/viasfora).

## License

Bracket Colorizer is available under the [MIT License](LICENSE).
