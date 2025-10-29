package io.fluxzero.jsondoclet.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.fluxzero.jsondoclet.config.DocletConfiguration;
import io.fluxzero.jsondoclet.model.DirectoryIndex;
import io.fluxzero.jsondoclet.model.DirectoryIndex.IndexFileEntry;
import io.fluxzero.jsondoclet.model.DirectoryIndex.SubdirectoryEntry;
import io.fluxzero.jsondoclet.model.PackageDocumentation;
import io.fluxzero.jsondoclet.model.TypeDocumentation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

/**
 * Coordinates traversal and writing of documentation artifacts.
 */
public final class DocGenerationTask {
    private final DocletConfiguration configuration;
    private final DocletEnvironment environment;
    private final Reporter reporter;
    private final ObjectMapper mapper;
    private final Map<Path, DirectoryIndex> indexes = new HashMap<>();

    /**
     * Creates a new task bound to the supplied doclet execution context.
     */
    public DocGenerationTask(DocletConfiguration configuration, DocletEnvironment environment, Reporter reporter) {
        this.configuration = configuration;
        this.environment = environment;
        this.reporter = reporter;
        this.mapper = createMapper(configuration.prettyPrint());
    }

    /**
     * Executes the traversal of packages and types, writing JSON files and indexes.
     *
     * @return {@code true} when all data is written successfully; {@code false} otherwise
     */
    public boolean execute() {
        Elements elements = environment.getElementUtils();
        Set<? extends Element> includedElements = environment.getIncludedElements();

        List<PackageElement> packages = new ArrayList<>();
        List<TypeElement> types = new ArrayList<>();

        for (Element element : includedElements) {
            if (element instanceof PackageElement pkg) {
                packages.add(pkg);
            } else if (element instanceof TypeElement type) {
                types.add(type);
            }
        }

        boolean ok = processPackages(packages, elements) && processTypes(types, elements);
        if (!ok) {
            return false;
        }

        return writeIndexes();
    }

    private boolean processPackages(List<PackageElement> packages, Elements elements) {
        return processElements(packages, pkg -> {
            Path packageDir = packageDirectory(pkg);
            createDirectories(packageDir);

            String qualifiedName = pkg.getQualifiedName().toString();
            String docComment = elements.getDocComment(pkg);

            PackageDocumentation payload = new PackageDocumentation(pkg.getSimpleName().toString(), qualifiedName, docComment);
            Path packageFile = packageDir.resolve("package-info.json");
            if (!writeJson(packageFile, payload)) {
                throw new RuntimeException("Failed to write package documentation for " + qualifiedName);
            }

            registerFile(packageDir, new IndexFileEntry(packageFile.getFileName().toString(),
                    pkg.getSimpleName().toString(),
                    qualifiedName,
                    "package"));
            registerAncestors(packageDir);
        });
    }

    private boolean processTypes(List<TypeElement> types, Elements elements) {
        return processElements(types, type -> {
            Path packageDir = packageDirectory(type);
            createDirectories(packageDir);

            String qualifiedName = elements.getBinaryName(type).toString();
            String docComment = elements.getDocComment(type);
            String kind = type.getKind().name().toLowerCase();

            TypeDocumentation payload = new TypeDocumentation(type.getSimpleName().toString(), qualifiedName, kind, docComment);
            Path typeFile = packageDir.resolve(type.getSimpleName().toString() + ".json");
            if (!writeJson(typeFile, payload)) {
                throw new RuntimeException("Failed to write type documentation for " + qualifiedName);
            }

            registerFile(packageDir, new IndexFileEntry(typeFile.getFileName().toString(),
                    type.getSimpleName().toString(),
                    qualifiedName,
                    kind));
            registerAncestors(packageDir);
        });
    }

    private <T> boolean processElements(Collection<T> elements, Consumer<T> consumer) {
        try {
            elements.forEach(consumer);
            return true;
        } catch (RuntimeException ex) {
            reporter.print(Diagnostic.Kind.ERROR, ex.getMessage());
            return false;
        }
    }

    private boolean writeIndexes() {
        boolean success = true;
        for (Map.Entry<Path, DirectoryIndex> entry : indexes.entrySet()) {
            Path directory = entry.getKey();
            DirectoryIndex index = entry.getValue();
            Path indexFile = directory.resolve("index.json");
            if (!writeJson(indexFile, index)) {
                success = false;
            }
        }
        return success;
    }

    private boolean writeJson(Path path, Object payload) {
        try {
            mapper.writeValue(path.toFile(), payload);
            return true;
        } catch (IOException e) {
            reporter.print(Diagnostic.Kind.ERROR, "Failed to write " + path + ": " + e.getMessage());
            return false;
        }
    }

    private Path packageDirectory(PackageElement pkg) {
        String qualifiedName = pkg.getQualifiedName().toString();
        if (qualifiedName.isEmpty()) {
            return configuration.outputDirectory();
        }
        return configuration.outputDirectory().resolve(qualifiedName.replace('.', '/'));
    }

    private Path packageDirectory(TypeElement type) {
        Element enclosing = type.getEnclosingElement();
        if (enclosing instanceof PackageElement pkg) {
            return packageDirectory(pkg);
        }
        return configuration.outputDirectory();
    }

    private void createDirectories(Path directory) {
        if (directory == null) {
            return;
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create directory " + directory + ": " + e.getMessage(), e);
        }
    }

    private void registerFile(Path directory, IndexFileEntry fileEntry) {
        DirectoryIndex index = indexes.computeIfAbsent(directory, ignored -> new DirectoryIndex());
        index.addFile(fileEntry);
    }

    private void registerAncestors(Path directory) {
        if (directory == null) {
            return;
        }
        Path outputRoot = configuration.outputDirectory();
        Path current = directory;
        Path parent = current.getParent();
        while (parent != null && parent.startsWith(outputRoot)) {
            DirectoryIndex parentIndex = indexes.computeIfAbsent(parent, ignored -> new DirectoryIndex());
            String name = current.getFileName() != null ? current.getFileName().toString() : current.toString();
            Path relative = parent.relativize(current);
            parentIndex.addSubdirectory(new SubdirectoryEntry(name, relative.toString()));

            current = parent;
            parent = current.getParent();
        }
    }

    private static ObjectMapper createMapper(boolean prettyPrint) {
        ObjectMapper mapper = new ObjectMapper();
        if (prettyPrint) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        }
        return mapper;
    }
}
