package io.fluxzero.tools.jsondoclet.model;

import java.util.List;

/**
 * Summary information for nested/inner types declared within a parent.
 */
public record NestedTypeDocumentation(String name,
        String qualifiedName,
        String kind,
        List<String> modifiers,
        List<AnnotationDocumentation> annotations) {
}
