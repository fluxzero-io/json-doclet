package io.fluxzero.jsondoclet.model;

import io.fluxzero.jsondoclet.model.MethodDocumentation.MethodParameter;
import java.util.List;

/**
 * Documentation for a constructor.
 */
public record ConstructorDocumentation(String name,
        String qualifiedName,
        List<String> modifiers,
        List<AnnotationDocumentation> annotations,
        List<String> typeParameters,
        List<MethodParameter> parameters,
        List<String> thrownTypes,
        boolean varArgs,
        String documentation) {
}
