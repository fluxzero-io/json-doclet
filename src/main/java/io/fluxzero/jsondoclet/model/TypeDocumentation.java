package io.fluxzero.jsondoclet.model;

import java.util.List;

/**
 * Minimal representation of a documented type.
 */
public record TypeDocumentation(String name,
        String qualifiedName,
        String kind,
        String documentation,
        List<MethodDocumentation> methods) {
}
