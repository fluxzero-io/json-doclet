package io.fluxzero.jsondoclet.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Index metadata generated per output directory.
 */
public final class DirectoryIndex {
    private final List<IndexFileEntry> files = new ArrayList<>();
    private final List<SubdirectoryEntry> subdirectories = new ArrayList<>();

    /**
     * Files in the current directory that point to generated JSON artifacts.
     */
    @JsonProperty("files")
    public List<IndexFileEntry> getFiles() {
        return Collections.unmodifiableList(files);
    }

    /**
     * Subdirectories that contain further index files.
     */
    @JsonProperty("subdirectories")
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
