package io.fluxzero.jsondoclet.model;

import java.util.List;

/**
 * Documentation for a record component (Java 16+).
 */
public record RecordComponentDocumentation(String name,
        String type,
        List<AnnotationDocumentation> annotations,
        String documentation) {
}
