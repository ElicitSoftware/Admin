package com.elicitsoftware.admin.flow;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * The console's stylesheets must not depend on custom properties that do not exist here.
 *
 * <p>Admin renders with Aura — {@link AppConfig} declares {@code @Theme(Aura.class)} and loads no
 * Lumo stylesheet — so the only {@code --lumo-*} properties that resolve are the color and
 * font-family ones {@code styles.css} defines itself, in its {@code html} block, to map the brand
 * contract onto the names the views still read. A {@code var()} naming any other {@code --lumo-*}
 * property is invalid at computed-value time, which drops the <em>whole</em> declaration: the rule
 * is written, shipped, and does nothing.</p>
 *
 * <p>That defect has reached production three times — issues #80 and #85 (the {@code LumoUtility}
 * class names, inert because the utility stylesheet 404ed and needs the Lumo theme in any case) and
 * the layer found while fixing #85, where the component stylesheets themselves were written against
 * {@code --lumo-space-*} and {@code --lumo-contrast-*pct} and had never had padding, background or
 * rounded corners. Nothing in the build noticed any of them. This is what notices.</p>
 *
 * <p>The convention it enforces, which {@code components/console-notice.css} shows: geometry and
 * surfaces on Aura's own {@code --vaadin-*} tokens, font sizes on the brand contract's
 * {@code --brand-font-size-*}, colors only on the {@code --lumo-*-text-color} properties
 * {@code styles.css} defines — and a literal fallback in every {@code var()}, so a token going
 * missing costs one value instead of a whole declaration.</p>
 */
class StylesheetTokenConsistencyTest {

    /** The served stylesheet tree: {@code styles.css} and the {@code components/} sheets it imports. */
    private static final Path RESOURCES = Path.of("src/main/resources/META-INF/resources");
    /**
     * The default brand. {@code AppConfig} inlines these into the same {@code <head>}, so an
     * unguarded {@code var(--lumo-…)} here fails in exactly the same way.
     */
    private static final Path BRAND = Path.of("src/main/resources/META-INF/brand");

    /** A custom property being *declared*. A usage is always {@code var(--lumo-x)} or {@code var(--lumo-x,}, never followed by a colon. */
    private static final Pattern DECLARATION = Pattern.compile("(--lumo-[A-Za-z0-9_-]+)\\s*:");
    /** A {@code var()} reference. Group 2 is non-null when a fallback follows. {@code \s} matches newlines, so a wrapped {@code var()} is handled. */
    private static final Pattern USE = Pattern.compile("var\\(\\s*(--lumo-[A-Za-z0-9_-]+)\\s*(,)?");
    private static final Pattern IMPORT = Pattern.compile("@import\\s+(?:url\\()?['\"]([^'\"]+)['\"]");
    private static final Pattern HTML_BLOCK = Pattern.compile("(?m)^html\\s*\\{");
    /** A Java string literal, so that {@code --lumo-*} named in a {@code //} comment is never seen. */
    private static final Pattern JAVA_LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");

    private record Finding(Path file, int line, String detail) {
        String render(Path root) {
            return "  " + root.relativize(file) + ":" + line + ": " + detail;
        }
    }

    @Test
    void everyLumoTokenReadIsOneStylesCssDefines() throws IOException {
        Path root = moduleRoot();
        Set<String> defined = definedTokens(root);

        List<Finding> findings = new ArrayList<>();
        for (Path file : stylesheets(root)) {
            String css = blankComments(Files.readString(file, StandardCharsets.UTF_8));
            Matcher m = USE.matcher(css);
            while (m.find()) {
                if (defined.contains(m.group(1))) {
                    continue;
                }
                findings.add(new Finding(file, lineOf(css, m.start()), m.group(2) == null
                        ? "var(" + m.group(1) + ") — undefined and unguarded, so the whole declaration is dropped"
                        : "var(" + m.group(1) + ", …) — the fallback saves the declaration, but the token can never resolve here"));
            }
        }
        if (!findings.isEmpty()) {
            StringBuilder sb = new StringBuilder().append(findings.size())
                    .append(" stylesheet declaration(s) read a --lumo-* custom property that styles.css does not")
                    .append(" define. Admin renders with Aura and loads no Lumo, so the property has no value")
                    .append(" (issues #80, #85):\n");
            findings.forEach(f -> sb.append(f.render(root)).append('\n'));
            sb.append("styles.css defines these ").append(defined.size()).append(", and only these:\n  ")
              .append(String.join(", ", defined)).append('\n')
              .append("Instead: --vaadin-padding-*/--vaadin-gap-*/--vaadin-radius-*/--vaadin-background-container/")
              .append("--vaadin-border-color/--vaadin-icon-size for geometry and surfaces, --brand-font-size-* for")
              .append(" font sizes, and one of the --lumo-*-text-color properties above for color — each with a")
              .append(" literal fallback, as components/console-notice.css does.");
            fail(sb.toString());
        }
    }

    /**
     * A stylesheet that *sets* a {@code --lumo-*} property outside {@code styles.css}'s {@code html}
     * block is the same defect from the other end: nothing under Aura reads it, and no fallback can
     * rescue it. {@code branded-header.css}'s {@code --lumo-icon-size-s} was the live example.
     */
    @Test
    void onlyStylesCssDefinesLumoProperties() throws IOException {
        Path root = moduleRoot();
        Path styles = root.resolve(RESOURCES).resolve("styles.css");

        List<Finding> findings = new ArrayList<>();
        for (Path file : stylesheets(root)) {
            // The brand sheets map the brand onto the --lumo-* names on purpose; that is their job.
            if (file.startsWith(root.resolve(BRAND))) {
                continue;
            }
            String css = blankComments(Files.readString(file, StandardCharsets.UTF_8));
            int[] block = file.equals(styles) ? htmlBlock(css, file) : new int[] {-1, -1};
            Matcher m = DECLARATION.matcher(css);
            while (m.find()) {
                if (m.start() >= block[0] && m.start() < block[1]) {
                    continue;
                }
                findings.add(new Finding(file, lineOf(css, m.start()), "sets " + m.group(1)));
            }
        }
        if (!findings.isEmpty()) {
            StringBuilder sb = new StringBuilder().append(findings.size())
                    .append(" declaration(s) set a --lumo-* custom property outside styles.css's html block.")
                    .append(" Aura never reads those, so they have no effect:\n");
            findings.forEach(f -> sb.append(f.render(root)).append('\n'));
            sb.append("Set the property the component actually reads — --vaadin-icon-size rather than")
              .append(" --lumo-icon-size-s — or, if a view genuinely needs to read the token, define it in")
              .append(" styles.css's html block where the brand contract lives.");
            fail(sb.toString());
        }
    }

    /**
     * An {@code @import} naming a file that is not served is how all of this started:
     * {@code styles.css} imported {@code lumo/lumo-utility.css}, which was never there, and the 404
     * was silent. A stylesheet nothing imports is the same absence, reached from the other side, and
     * is the easier mistake to make.
     */
    @Test
    void everyComponentStylesheetIsImportedAndEveryImportResolves() throws IOException {
        Path root = moduleRoot();
        Path resources = root.resolve(RESOURCES);
        Path styles = resources.resolve("styles.css");
        String css = blankComments(Files.readString(styles, StandardCharsets.UTF_8));

        Set<String> imported = new TreeSet<>();
        List<Finding> findings = new ArrayList<>();
        Matcher m = IMPORT.matcher(css);
        while (m.find()) {
            String target = m.group(1);
            if (target.startsWith("http") || target.startsWith("/")) {
                continue;   // a web font, say; not ours to resolve
            }
            imported.add(target);
            if (!Files.exists(resources.resolve(target))) {
                findings.add(new Finding(styles, lineOf(css, m.start()),
                        "@import '" + target + "' — no such file, so the import 404s and none of it applies"));
            }
        }
        try (Stream<Path> list = Files.list(resources.resolve("components"))) {
            list.filter(p -> p.getFileName().toString().endsWith(".css"))
                .sorted()
                .filter(p -> !imported.contains("components/" + p.getFileName()))
                .forEach(p -> findings.add(new Finding(p, 1,
                        "styles.css does not @import this file, so none of its rules are served")));
        }
        if (!findings.isEmpty()) {
            StringBuilder sb = new StringBuilder("components/ and styles.css's @import list disagree:\n");
            findings.forEach(f -> sb.append(f.render(root)).append('\n'));
            sb.append("Add the @import to styles.css, or delete the stylesheet.");
            fail(sb.toString());
        }
    }

    /**
     * The same rule for the CSS written from Java. {@code ResultDialog} sets colors inline through
     * {@code getStyle()}; those are declarations in the same cascade with the same failure mode, and a
     * CSS-only gate cannot see them.
     */
    @Test
    void everyLumoTokenReadFromJavaIsOneStylesCssDefines() throws IOException {
        Path root = moduleRoot();
        Set<String> defined = definedTokens(root);

        List<Finding> findings = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root.resolve("src/main/java"))) {
            for (Path file : walk.filter(p -> p.getFileName().toString().endsWith(".java")).sorted().toList()) {
                String java = Files.readString(file, StandardCharsets.UTF_8);
                Matcher literal = JAVA_LITERAL.matcher(java);
                while (literal.find()) {
                    String text = literal.group(1);
                    Matcher use = USE.matcher(text);
                    while (use.find()) {
                        if (!defined.contains(use.group(1))) {
                            findings.add(new Finding(file, lineOf(java, literal.start()),
                                    "var(" + use.group(1) + ") — styles.css does not define it"));
                        }
                    }
                    if (text.startsWith("--lumo-")) {
                        findings.add(new Finding(file, lineOf(java, literal.start()),
                                "sets " + text + ", which nothing under Aura reads"));
                    }
                }
            }
        }
        if (!findings.isEmpty()) {
            StringBuilder sb = new StringBuilder().append(findings.size())
                    .append(" inline style(s) in Java use a --lumo-* property that does not resolve here:\n");
            findings.forEach(f -> sb.append(f.render(root)).append('\n'));
            sb.append("Use an Aura --vaadin-* token with a literal fallback, as ResultDialog does for its")
              .append(" section spacing.");
            fail(sb.toString());
        }
    }

    /**
     * The {@code --lumo-*} properties {@code styles.css} defines in its {@code html} block, which are
     * the only ones that resolve. Scoped to that block on purpose: a token declared inside a narrower
     * selector is not available to other rules, and harvesting it would green-light unguarded reads.
     */
    private static Set<String> definedTokens(Path root) throws IOException {
        Path styles = root.resolve(RESOURCES).resolve("styles.css");
        String css = blankComments(Files.readString(styles, StandardCharsets.UTF_8));
        int[] block = htmlBlock(css, styles);
        Set<String> defined = new TreeSet<>();
        Matcher m = DECLARATION.matcher(css.substring(block[0], block[1]));
        while (m.find()) {
            defined.add(m.group(1));
        }
        if (defined.isEmpty()) {
            fail(styles + " declares no --lumo-* properties in its html block; the brand contract has moved"
                    + " and this test can no longer tell a resolvable token from an inert one.");
        }
        return defined;
    }

    /** The {@code [start, end)} offsets of the {@code html { … }} block, found by brace depth. */
    private static int[] htmlBlock(String css, Path file) {
        Matcher m = HTML_BLOCK.matcher(css);
        if (!m.find()) {
            fail("no 'html {' block in " + file + "; that is where the brand contract maps the --lumo-*"
                    + " properties, and this test reads it to learn which ones resolve.");
        }
        int start = m.end();
        int depth = 1;
        for (int i = start; i < css.length(); i++) {
            char c = css.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return new int[] {start, i};
            }
        }
        return new int[] {start, css.length()};
    }

    /** {@code styles.css}, the {@code components/} sheets, and the default brand's own sheets. */
    private static List<Path> stylesheets(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path dir : List.of(root.resolve(RESOURCES), root.resolve(BRAND))) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(p -> p.getFileName().toString().endsWith(".css")).sorted().forEach(files::add);
            }
        }
        if (files.size() < 2) {
            fail("found " + files.size() + " stylesheet(s) under " + root.resolve(RESOURCES)
                    + "; the module root resolved wrongly and this test would pass without checking anything.");
        }
        return files;
    }

    /**
     * Replaces comment bodies with spaces, keeping every newline, so offsets and therefore the
     * reported line numbers still match the file on disk. The stylesheets' explanatory comments name
     * many {@code --lumo-*} properties — and would otherwise widen the defined set from prose.
     */
    private static String blankComments(String css) {
        StringBuilder out = new StringBuilder(css.length());
        int i = 0;
        while (i < css.length()) {
            if (css.charAt(i) == '/' && i + 1 < css.length() && css.charAt(i + 1) == '*') {
                int end = css.indexOf("*/", i + 2);
                end = end < 0 ? css.length() : end + 2;
                for (int k = i; k < end; k++) {
                    out.append(css.charAt(k) == '\n' ? '\n' : ' ');
                }
                i = end;
            } else {
                out.append(css.charAt(i++));
            }
        }
        return out.toString();
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** The DisplayedStringsCoverageTest idiom, copied rather than shared so this gate cannot be broken by refactoring that one. */
    private static Path moduleRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("pom.xml"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("pom.xml not found above " + Path.of("").toAbsolutePath());
        }
        return dir;
    }
}
