package io.fluxzero.tools.jsondoclet.model;

/**
 * Minimal representation of package-level documentation.
 */
public record PackageDocumentation(String name, String qualifiedName, String documentation) {
}
