package io.fluxzero.jsondoclet.model;

import java.util.List;

/**
 * Documentation extracted for an individual method.
 */
public record MethodDocumentation(String name,
        String qualifiedName,
        String returnType,
        List<MethodParameter> parameters,
        String documentation) {

    /**
     * Method parameter descriptor capturing the declaration type and name.
     */
    public record MethodParameter(String name, String type) {
    }
}
