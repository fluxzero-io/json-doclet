package io.fluxzero.tools.jsondoclet.config;

import java.nio.file.Path;

/**
 * Immutable configuration for JSON doclet execution.
 */
public record DocletConfiguration(Path outputDirectory, boolean prettyPrint, boolean includePrivate) {
}
