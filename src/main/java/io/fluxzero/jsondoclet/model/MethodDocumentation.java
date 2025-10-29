package io.fluxzero.jsondoclet.model;

import java.util.List;

/**
 * Documentation extracted for an individual method.
 */
public record MethodDocumentation(String name,
        String qualifiedName,
        String returnType,
        List<String> modifiers,
        List<AnnotationDocumentation> annotations,
        List<String> typeParameters,
        List<MethodParameter> parameters,
        List<String> thrownTypes,
        boolean varArgs,
        String documentation) {

    /**
     * Method parameter descriptor capturing the declaration type and name.
     */
    public record MethodParameter(String name,
            String type,
            boolean varArgs,
            List<AnnotationDocumentation> annotations) {
    }
}
