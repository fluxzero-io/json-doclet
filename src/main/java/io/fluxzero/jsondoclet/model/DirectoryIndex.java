package io.fluxzero.jsondoclet.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Index metadata generated per output directory.
 */
public final class DirectoryIndex {
    private final List<IndexFileEntry> files = new ArrayList<>();
    private final List<SubdirectoryEntry> subdirectories = new ArrayList<>();
    private PackageDocumentation packageDocumentation;

    /**
     * Files in the current directory that point to generated JSON artifacts.
     */
    public List<IndexFileEntry> getFiles() {
        return Collections.unmodifiableList(files);
    }

    /**
     * Subdirectories that contain further index files.
     */
    public List<SubdirectoryEntry> getSubdirectories() {
        return Collections.unmodifiableList(subdirectories);
    }

    /**
     * Adds a file entry to the index.
     */
    public void addFile(IndexFileEntry entry) {
        if (!files.contains(entry)) {
            files.add(entry);
        }
    }

    /**
     * Adds a subdirectory reference to the index.
     */
    public void addSubdirectory(SubdirectoryEntry entry) {
        if (!subdirectories.contains(entry)) {
            subdirectories.add(entry);
        }
    }

    /**
     * Assigns package-level documentation metadata to this directory.
     */
    public void setPackage(PackageDocumentation documentation) {
        this.packageDocumentation = documentation;
    }

    /**
     * Returns documentation metadata for the package corresponding to this directory, if any.
     */
    public PackageDocumentation getPackage() {
        return packageDocumentation;
    }

    /**
     * Simple file entry describing nearby JSON output.
     */
    public record IndexFileEntry(String file, String name, String qualifiedName, String kind) {
    }

    /**
     * Subdirectory pointer for discovery.
     */
    public record SubdirectoryEntry(String name, String path) {
    }
}
