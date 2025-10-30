package io.fluxzero.jsondoclet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;

/**
 * Exercises the doclet against a tiny sample project to ensure JSON files are written.
 */
class JsonDocletSmokeTest {
    private static final Path TEST_RESOURCES_ROOT = Path.of("src", "test", "resources");
    private static final Path SCENARIO_ROOT = TEST_RESOURCES_ROOT.resolve("example");
    private static final Path SOURCE_ROOT = SCENARIO_ROOT.resolve("source");
    private static final Path EXPECTED_ROOT = SCENARIO_ROOT.resolve("expected");

    @Test
    void generatesJsonOutputForSampleSources() throws Exception {
        Path generatedRoot = Path.of("build", "test-generated", "example");
        deleteDirectory(generatedRoot);
        Path sourceDir = generatedRoot.resolve("src");
        Path outputDir = generatedRoot.resolve("actual");
        copyTree(SOURCE_ROOT, sourceDir);

        DocumentationTool docTool = ToolProvider.getSystemDocumentationTool();
        assertNotNull(docTool, "System documentation tool is not available");

        try (StandardJavaFileManager fileManager = docTool.getStandardFileManager(null, null, null)) {
            List<Path> javaFiles = collectJavaFiles(sourceDir);
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromPaths(javaFiles);

            List<String> options = new ArrayList<>();
            options.add("-docletpath");
            options.add(System.getProperty("java.class.path"));
            options.add("-doclet");
            options.add(JsonDoclet.class.getName());
            options.add("-d");
            options.add(outputDir.toString());
            options.add("--pretty");

            DocumentationTool.DocumentationTask task =
                    docTool.getTask(null, fileManager, null, null, options, compilationUnits);
            assertTrue(task.call(), "Doclet invocation failed");
        }

        assertJsonOutputsMatch(EXPECTED_ROOT, outputDir);
    }

    private List<Path> collectJavaFiles(Path sourceDir) throws IOException {
        try (var stream = Files.walk(sourceDir)) {
            return stream.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private void copyTree(Path sourceRoot, Path targetRoot) throws IOException {
        try (var stream = Files.walk(sourceRoot)) {
            stream.forEach(source -> {
                try {
                    Path relative = sourceRoot.relativize(source);
                    Path destination = targetRoot.resolve(relative.toString());
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy tree from " + sourceRoot + " to " + targetRoot, e);
                }
            });
        }
    }

    private void assertJsonOutputsMatch(Path expectedRoot, Path actualRoot) throws IOException {
        if (Boolean.getBoolean("jsondoclet.updateExpected")) {
            overwriteExpected(expectedRoot, actualRoot);
            return;
        }

        List<Path> expectedFiles;
        try (var stream = Files.walk(expectedRoot)) {
            expectedFiles = stream.filter(Files::isRegularFile).toList();
        }

        for (Path expected : expectedFiles) {
            Path relative = expectedRoot.relativize(expected);
            Path actual = actualRoot.resolve(relative.toString());
            assertTrue(Files.exists(actual), "Missing generated file: " + relative);
            String expectedJson = readNormalized(expected);
            String actualJson = readNormalized(actual);
            assertEquals(expectedJson, actualJson, "Mismatch for " + relative);
        }

        List<Path> actualFiles;
        try (var stream = Files.walk(actualRoot)) {
            actualFiles = stream.filter(Files::isRegularFile).toList();
        }

        for (Path actual : actualFiles) {
            Path relative = actualRoot.relativize(actual);
            assertTrue(Files.exists(expectedRoot.resolve(relative.toString())),
                    "Unexpected generated file: " + relative);
        }
    }

    private void deleteDirectory(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete " + path, e);
                        }
                    });
        }
    }

    private void overwriteExpected(Path expectedRoot, Path actualRoot) throws IOException {
        deleteDirectory(expectedRoot);
        if (!Files.exists(actualRoot)) {
            return;
        }
        copyTree(actualRoot, expectedRoot);
    }

    private String readNormalized(Path file) throws IOException {
        return Files.readString(file).replace("\r\n", "\n");
    }
}
