package io.fluxzero.jsondoclet.model;

import java.util.Map;

/**
 * Representation of an applied annotation, including element values when present.
 */
public record AnnotationDocumentation(String annotationType, Map<String, String> values) {
}
