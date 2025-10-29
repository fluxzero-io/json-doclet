package io.fluxzero.jsondoclet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import io.fluxzero.jsondoclet.config.DocletConfiguration;
import io.fluxzero.jsondoclet.core.DocGenerationTask;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

/**
 * Doclet entry point that produces JSON output instead of HTML.
 */
public class JsonDoclet implements Doclet {
    private Locale locale = Locale.getDefault();
    private Reporter reporter;
    private Path outputDirectory = Paths.get("build", "json-doclet");
    private boolean prettyPrint;
    private boolean includePrivate;

    private final Set<Option> supportedOptions = new LinkedHashSet<>();

    /**
     * Default constructor required by the doclet infrastructure.
     */
    public JsonDoclet() {
        supportedOptions.add(new SimpleOption(
                "-d",
                1,
                Option.Kind.STANDARD,
                "<directory>",
                "Destination directory for generated JSON files",
                args -> outputDirectory = Paths.get(args.get(0))));
        supportedOptions.add(new SimpleOption(
                "--pretty",
                0,
                Option.Kind.OTHER,
                "",
                "Pretty print generated JSON files",
                args -> prettyPrint = true));
        supportedOptions.add(new SimpleOption(
                "--include-private",
                0,
                Option.Kind.OTHER,
                "",
                "Include private members in output",
                args -> includePrivate = true));
    }

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.locale = locale != null ? locale : Locale.getDefault();
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return "json-doclet";
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return supportedOptions;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean run(DocletEnvironment environment) {
        Instant start = Instant.now();
        if (reporter == null) {
            throw new IllegalStateException("Reporter not initialised");
        }

        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            reporter.print(Diagnostic.Kind.ERROR, "Unable to create output directory: " + outputDirectory + " -> " + e.getMessage());
            return false;
        }

        reporter.print(Diagnostic.Kind.NOTE, "JSON Doclet output directory: " + outputDirectory.toAbsolutePath());
        reporter.print(Diagnostic.Kind.NOTE, "Pretty print: " + prettyPrint + ", include private: " + includePrivate);

        DocletConfiguration configuration = new DocletConfiguration(outputDirectory, prettyPrint, includePrivate);
        DocGenerationTask task = new DocGenerationTask(configuration, environment, reporter);
        boolean success = task.execute();

        Duration elapsed = Duration.between(start, Instant.now());
        reporter.print(success ? Diagnostic.Kind.NOTE : Diagnostic.Kind.ERROR,
                "JSON doclet completed in " + elapsed.toMillis() + " ms");

        return success;
    }

    private static final class SimpleOption implements Option {
        private final List<String> names;
        private final int argumentCount;
        private final Kind kind;
        private final String parameters;
        private final String description;
        private final Consumer<List<String>> processor;

        private SimpleOption(String name,
                int argumentCount,
                Kind kind,
                String parameters,
                String description,
                Consumer<List<String>> processor) {
            this.names = List.of(name);
            this.argumentCount = argumentCount;
            this.kind = kind;
            this.parameters = parameters;
            this.description = description;
            this.processor = processor;
        }

        @Override
        public int getArgumentCount() {
            return argumentCount;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public List<String> getNames() {
            return names;
        }

        @Override
        public String getParameters() {
            return parameters;
        }

        @Override
        public boolean process(String option, List<String> arguments) {
            if (arguments.size() != argumentCount) {
                throw new IllegalArgumentException("Option " + option + " expected " + argumentCount + " arguments but got " + arguments.size());
            }

            processor.accept(new ArrayList<>(arguments));
            return true;
        }
    }
}
