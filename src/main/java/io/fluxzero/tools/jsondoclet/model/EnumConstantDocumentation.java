package io.fluxzero.tools.jsondoclet.model;

import java.util.List;

/**
 * Documentation for an enum constant.
 */
public record EnumConstantDocumentation(String name,
        String qualifiedName,
        List<AnnotationDocumentation> annotations,
        String documentation) {
}
