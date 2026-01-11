IntroductionPlugin template

This folder contains the single canonical template used to generate the Velocity plugin class at build time.

Active template
- `IntroductionPlugin.java` — the only active Java template. It contains Maven placeholders that are filtered during the `generate-sources` phase:
  - `${plugin.id}`
  - `${plugin.name}`
  - `${plugin.version}`
  - `${plugin.author}`

How it works
1. Edit plugin metadata in `pom.xml` (properties `plugin.id`, `plugin.name`, `plugin.version`, `plugin.author`).
2. Run `mvn generate-sources` (or a full build). Maven copies `IntroductionPlugin.java` to `target/generated-sources/plugin-info`, substituting the placeholders.
3. The generated `IntroductionPlugin.java` is compiled as part of the project and bundled in the resulting JAR.

Notes
- Do not edit `target/generated-sources/...` directly — edit the template in `src/main/resources/templates/IntroductionPlugin.java` or the POM properties.
- Old/deprecated templates have been removed; only modify the single template listed above.

