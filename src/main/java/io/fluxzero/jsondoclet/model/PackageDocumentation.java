package io.fluxzero.jsondoclet.model;

/**
 * Minimal representation of package-level documentation.
 */
public record PackageDocumentation(String name, String qualifiedName, String documentation) {
}
