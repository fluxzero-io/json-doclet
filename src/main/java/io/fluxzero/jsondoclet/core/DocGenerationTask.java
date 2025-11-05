package io.fluxzero.jsondoclet.core;

import io.fluxzero.jsondoclet.config.DocletConfiguration;
import io.fluxzero.jsondoclet.model.AnnotationDocumentation;
import io.fluxzero.jsondoclet.model.ConstructorDocumentation;
import io.fluxzero.jsondoclet.model.DirectoryIndex;
import io.fluxzero.jsondoclet.model.DirectoryIndex.IndexFileEntry;
import io.fluxzero.jsondoclet.model.DirectoryIndex.SubdirectoryEntry;
import io.fluxzero.jsondoclet.model.EnumConstantDocumentation;
import io.fluxzero.jsondoclet.model.FieldDocumentation;
import io.fluxzero.jsondoclet.model.MethodDocumentation;
import io.fluxzero.jsondoclet.model.MethodDocumentation.MethodParameter;
import io.fluxzero.jsondoclet.model.NestedTypeDocumentation;
import io.fluxzero.jsondoclet.model.PackageDocumentation;
import io.fluxzero.jsondoclet.model.RecordComponentDocumentation;
import io.fluxzero.jsondoclet.model.TypeDocumentation;
import io.fluxzero.jsondoclet.util.JsonWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
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
    private final Map<Path, DirectoryIndex> indexes = new HashMap<>();

    /**
     * Creates a new task bound to the supplied doclet execution context.
     */
    public DocGenerationTask(DocletConfiguration configuration, DocletEnvironment environment, Reporter reporter) {
        this.configuration = configuration;
        this.environment = environment;
        this.reporter = reporter;
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
                Element enclosing = type.getEnclosingElement();
                if (enclosing instanceof PackageElement) {
                    types.add(type);
                }
            }
        }

        packages.sort(Comparator.comparing(pkg -> pkg.getQualifiedName().toString()));
        types.sort(Comparator.comparing(type -> type.getQualifiedName().toString()));

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
            DirectoryIndex index = indexes.computeIfAbsent(packageDir, ignored -> new DirectoryIndex());
            index.setPackage(payload);
            registerAncestors(packageDir);
        });
    }

    private boolean processTypes(List<TypeElement> types, Elements elements) {
        return processElements(types, type -> writeTypeRecursively(type, elements));
    }

    private void writeTypeRecursively(TypeElement type, Elements elements) {
        Path packageDir = packageDirectory(type);
        createDirectories(packageDir);

        TypeDocumentation payload = buildTypeDocumentation(type, elements);
        Path typeFile = packageDir.resolve(typeFileName(type));
        if (!writeJson(typeFile, payload)) {
            throw new RuntimeException("Failed to write type documentation for " + payload.qualifiedName());
        }

        registerFile(packageDir, new IndexFileEntry(typeFile.getFileName().toString(),
                typeDisplayName(type, elements),
                payload.qualifiedName(),
                payload.kind()));
        registerAncestors(packageDir);

        ElementFilter.typesIn(type.getEnclosedElements()).stream()
                .sorted(Comparator.comparing(nested -> nested.getQualifiedName().toString()))
                .forEach(nested -> writeTypeRecursively(nested, elements));
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

    private TypeDocumentation buildTypeDocumentation(TypeElement type, Elements elements) {
        String qualifiedName = elements.getBinaryName(type).toString();
        String packageName = elements.getPackageOf(type).getQualifiedName().toString();
        String kind = type.getKind().name().toLowerCase();
        String documentation = elements.getDocComment(type);

        List<String> modifiers = modifiersOf(type);
        List<AnnotationDocumentation> annotations = annotationsOf(type);
        List<String> typeParameters = type.getTypeParameters().stream()
                .map(TypeParameterElement::toString)
                .toList();

        TypeMirror superClassMirror = type.getSuperclass();
        String superClass = superClassMirror != null && superClassMirror.getKind() != TypeKind.NONE
                ? superClassMirror.toString()
                : null;

        List<String> interfaces = type.getInterfaces().stream()
                .map(Object::toString)
                .sorted()
                .toList();

        List<FieldDocumentation> fields = extractFields(type, elements);
        List<ConstructorDocumentation> constructors = extractConstructors(type, elements);
        List<MethodDocumentation> methods = extractMethods(type, elements);
        List<EnumConstantDocumentation> enumConstants = extractEnumConstants(type, elements);
        List<RecordComponentDocumentation> recordComponents = extractRecordComponents(type, elements);
        List<NestedTypeDocumentation> nestedTypes = extractNestedTypes(type, elements);

        return new TypeDocumentation(type.getSimpleName().toString(),
                qualifiedName,
                packageName,
                kind,
                modifiers,
                annotations,
                documentation,
                typeParameters,
                superClass,
                interfaces,
                fields,
                constructors,
                methods,
                enumConstants,
                recordComponents,
                nestedTypes);
    }

    private List<FieldDocumentation> extractFields(TypeElement type, Elements elements) {
        String qualifiedTypeName = elements.getBinaryName(type).toString();
        return ElementFilter.fieldsIn(type.getEnclosedElements()).stream()
                .filter(field -> field.getKind() == ElementKind.FIELD)
                .sorted(Comparator.comparing(field -> field.getSimpleName().toString()))
                .map(field -> new FieldDocumentation(
                        field.getSimpleName().toString(),
                        qualifiedTypeName + "." + field.getSimpleName(),
                        field.asType().toString(),
                        modifiersOf(field),
                        annotationsOf(field),
                        elements.getDocComment(field),
                        field.getConstantValue()))
                .toList();
    }

    private List<ConstructorDocumentation> extractConstructors(TypeElement type, Elements elements) {
        return ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
                .sorted(methodComparator())
                .map(constructor -> toConstructorDocumentation(type, constructor, elements))
                .toList();
    }

    private ConstructorDocumentation toConstructorDocumentation(TypeElement declaringType,
            ExecutableElement constructor,
            Elements elements) {
        String constructorName = declaringType.getSimpleName().toString();
        String qualifiedTypeName = elements.getBinaryName(declaringType).toString();
        String qualifiedConstructorName = qualifiedTypeName + "#" + constructorName;
        String documentation = elements.getDocComment(constructor);

        List<String> modifiers = modifiersOf(constructor);
        List<AnnotationDocumentation> annotations = annotationsOf(constructor);
        List<String> typeParameters = constructor.getTypeParameters().stream()
                .map(TypeParameterElement::toString)
                .toList();
        List<MethodParameter> parameters = constructor.getParameters().stream()
                .map(parameter -> toParameterDocumentation(constructor, parameter))
                .toList();
        List<String> thrownTypes = constructor.getThrownTypes().stream()
                .map(Object::toString)
                .sorted()
                .toList();

        return new ConstructorDocumentation(constructorName,
                qualifiedConstructorName,
                modifiers,
                annotations,
                typeParameters,
                parameters,
                thrownTypes,
                constructor.isVarArgs(),
                documentation);
    }

    private List<MethodDocumentation> extractMethods(TypeElement type, Elements elements) {
        return ElementFilter.methodsIn(type.getEnclosedElements()).stream()
                .sorted(methodComparator())
                .map(method -> toMethodDocumentation(type, method, elements))
                .toList();
    }

    private MethodDocumentation toMethodDocumentation(TypeElement declaringType,
            ExecutableElement method,
            Elements elements) {
        String methodName = method.getSimpleName().toString();
        String qualifiedTypeName = elements.getBinaryName(declaringType).toString();
        String qualifiedMethodName = qualifiedTypeName + "#" + methodName;

        List<String> modifiers = modifiersOf(method);
        List<AnnotationDocumentation> annotations = annotationsOf(method);
        List<String> typeParameters = method.getTypeParameters().stream()
                .map(TypeParameterElement::toString)
                .toList();
        List<MethodParameter> parameters = method.getParameters().stream()
                .map(parameter -> toParameterDocumentation(method, parameter))
                .toList();
        List<String> thrownTypes = method.getThrownTypes().stream()
                .map(Object::toString)
                .sorted()
                .toList();

        return new MethodDocumentation(methodName,
                qualifiedMethodName,
                method.getReturnType().toString(),
                modifiers,
                annotations,
                typeParameters,
                parameters,
                thrownTypes,
                method.isVarArgs(),
                elements.getDocComment(method));
    }

    private List<EnumConstantDocumentation> extractEnumConstants(TypeElement type, Elements elements) {
        String qualifiedTypeName = elements.getBinaryName(type).toString();
        return ElementFilter.fieldsIn(type.getEnclosedElements()).stream()
                .filter(field -> field.getKind() == ElementKind.ENUM_CONSTANT)
                .sorted(Comparator.comparing(field -> field.getSimpleName().toString()))
                .map(constant -> new EnumConstantDocumentation(
                        constant.getSimpleName().toString(),
                        qualifiedTypeName + "." + constant.getSimpleName(),
                        annotationsOf(constant),
                        elements.getDocComment(constant)))
                .toList();
    }

    private List<RecordComponentDocumentation> extractRecordComponents(TypeElement type, Elements elements) {
        return ElementFilter.recordComponentsIn(type.getEnclosedElements()).stream()
                .sorted(Comparator.comparing(component -> component.getSimpleName().toString()))
                .map(component -> new RecordComponentDocumentation(
                        component.getSimpleName().toString(),
                        component.asType().toString(),
                        annotationsOf(component),
                        elements.getDocComment(component)))
                .toList();
    }

    private List<NestedTypeDocumentation> extractNestedTypes(TypeElement type, Elements elements) {
        return ElementFilter.typesIn(type.getEnclosedElements()).stream()
                .sorted(Comparator.comparing(nested -> nested.getSimpleName().toString()))
                .map(nested -> new NestedTypeDocumentation(
                        nested.getSimpleName().toString(),
                        elements.getBinaryName(nested).toString(),
                        nested.getKind().name().toLowerCase(),
                        modifiersOf(nested),
                        annotationsOf(nested)))
                .toList();
    }

    private MethodParameter toParameterDocumentation(ExecutableElement executable, VariableElement parameter) {
        List<AnnotationDocumentation> annotations = annotationsOf(parameter);
        boolean isVarArgsParam = executable.isVarArgs()
                && executable.getParameters().indexOf(parameter) == executable.getParameters().size() - 1;
        return new MethodParameter(parameter.getSimpleName().toString(),
                parameter.asType().toString(),
                isVarArgsParam,
                annotations);
    }

    private Comparator<ExecutableElement> methodComparator() {
        return Comparator
                .comparing((ExecutableElement method) -> method.getSimpleName().toString())
                .thenComparing(method -> method.getParameters().stream()
                        .map(param -> param.asType().toString())
                        .collect(Collectors.joining(",")));
    }

    private List<String> modifiersOf(Element element) {
        return element.getModifiers().stream()
                .map(Modifier::toString)
                .sorted()
                .toList();
    }

    private List<AnnotationDocumentation> annotationsOf(Element element) {
        return element.getAnnotationMirrors().stream()
                .map(this::toAnnotationDocumentation)
                .toList();
    }

    private AnnotationDocumentation toAnnotationDocumentation(AnnotationMirror mirror) {
        Map<String, String> values = new LinkedHashMap<>();
        mirror.getElementValues().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(e -> e.getSimpleName().toString())))
                .forEach(entry -> values.put(entry.getKey().getSimpleName().toString(), formatAnnotationValue(entry.getValue())));
        return new AnnotationDocumentation(mirror.getAnnotationType().toString(), values);
    }

    private String formatAnnotationValue(AnnotationValue value) {
        return value.getValue() == null ? "null" : value.getValue().toString();
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
            JsonWriter.write(path, payload, configuration.prettyPrint());
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
        PackageElement pkg = environment.getElementUtils().getPackageOf(type);
        return packageDirectory(pkg);
    }

    private String typeFileName(TypeElement type) {
        List<String> names = new ArrayList<>();
        Element current = type;
        while (current instanceof TypeElement typeElement) {
            names.add(0, typeElement.getSimpleName().toString());
            current = typeElement.getEnclosingElement();
        }
        return String.join(".", names) + ".json";
    }

    private String typeDisplayName(TypeElement type, Elements elements) {
        String qualifiedName = type.getQualifiedName().toString();
        PackageElement pkg = elements.getPackageOf(type);
        if (pkg != null) {
            String packageName = pkg.getQualifiedName().toString();
            if (!packageName.isEmpty() && qualifiedName.startsWith(packageName + ".")) {
                return qualifiedName.substring(packageName.length() + 1);
            }
        }
        return qualifiedName;
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

}
