package io.fluxzero.jsondoclet.model;

import java.util.List;

/**
 * Aggregated documentation for a single declared type.
 */
public record TypeDocumentation(String name,
        String qualifiedName,
        String packageName,
        String kind,
        List<String> modifiers,
        List<AnnotationDocumentation> annotations,
        String documentation,
        List<String> typeParameters,
        String superClass,
        List<String> interfaces,
        List<FieldDocumentation> fields,
        List<ConstructorDocumentation> constructors,
        List<MethodDocumentation> methods,
        List<EnumConstantDocumentation> enumConstants,
        List<RecordComponentDocumentation> recordComponents,
        List<NestedTypeDocumentation> nestedTypes) {
}
