package io.fluxzero.jsondoclet.model;

import java.util.List;

/**
 * Documentation for a field declaration.
 */
public record FieldDocumentation(String name,
        String qualifiedName,
        String type,
        List<String> modifiers,
        List<AnnotationDocumentation> annotations,
        String documentation,
        Object constantValue) {
}
