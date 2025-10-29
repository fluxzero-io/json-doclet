package io.fluxzero.jsondoclet.model;

/**
 * Minimal representation of a documented type.
 */
public record TypeDocumentation(String name, String qualifiedName, String kind, String documentation) {
}
