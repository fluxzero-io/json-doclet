package io.fluxzero.jsondoclet.model;

import java.util.List;

/**
 * Documentation for an enum constant.
 */
public record EnumConstantDocumentation(String name,
        String qualifiedName,
        List<AnnotationDocumentation> annotations,
        String documentation) {
}
