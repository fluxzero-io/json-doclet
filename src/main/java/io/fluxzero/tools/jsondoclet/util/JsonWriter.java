package io.fluxzero.tools.jsondoclet.util;

import io.fluxzero.tools.jsondoclet.model.DirectoryIndex;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal JSON serializer tailored to the doclet's data model.
 */
public final class JsonWriter {
    private JsonWriter() {
    }

    /**
     * Serializes the supplied value to the given path.
     */
    public static void write(Path path, Object value, boolean pretty) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            new Serializer(writer, pretty).writeValue(value);
            writer.write('\n');
        }
    }

    private static final class Serializer {
        private final Writer writer;
        private final boolean pretty;
        private final String indentUnit = "  ";
        private int depth;

        Serializer(Writer writer, boolean pretty) {
            this.writer = writer;
            this.pretty = pretty;
        }

        void writeValue(Object value) throws IOException {
            if (value == null) {
                writer.write("null");
            } else if (value instanceof String string) {
                writeString(string);
            } else if (value instanceof Character character) {
                writeString(character.toString());
            } else if (value instanceof Number || value instanceof Boolean) {
                writer.write(value.toString());
            } else if (value instanceof Enum<?> enumValue) {
                writeString(enumValue.name());
            } else if (value instanceof Map<?, ?> map) {
                writeMap(map);
            } else if (value instanceof Iterable<?> iterable) {
                writeIterable(iterable.iterator());
            } else if (value != null && value.getClass().isArray()) {
                writeArray(value);
            } else if (value instanceof DirectoryIndex index) {
                writeDirectoryIndex(index);
            } else if (value != null && value.getClass().isRecord()) {
                writeRecord(value);
            } else {
                throw new IllegalArgumentException("Unsupported JSON value: " + value);
            }
        }

        private void writeMap(Map<?, ?> map) throws IOException {
            writer.write('{');
            depth++;
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (!(key instanceof String keyString)) {
                    throw new IllegalArgumentException("Non-string map key: " + key);
                }
                if (!first) {
                    writer.write(',');
                }
                writeNewlineAndIndent();
                writeString(keyString);
                writer.write(pretty ? " : " : ":");
                writeValue(entry.getValue());
                first = false;
            }
            depth--;
            if (!first) {
                writeTrailingNewline();
            }
            writer.write('}');
        }

        private void writeIterable(Iterator<?> iterator) throws IOException {
            if (!pretty) {
                writer.write('[');
                if (!iterator.hasNext()) {
                    writer.write(']');
                    return;
                }
                writeValue(iterator.next());
                while (iterator.hasNext()) {
                    writer.write(',');
                    writeValue(iterator.next());
                }
                writer.write(']');
                return;
            }

            writer.write('[');
            if (!iterator.hasNext()) {
                writer.write(' ');
                writer.write(']');
                return;
            }
            writer.write(' ');
            writeValue(iterator.next());
            while (iterator.hasNext()) {
                writer.write(',');
                writer.write(' ');
                writeValue(iterator.next());
            }
            writer.write(' ');
            writer.write(']');
        }

        private void writeArray(Object array) throws IOException {
            int length = Array.getLength(array);
            writer.write('[');
            if (!pretty) {
                for (int i = 0; i < length; i++) {
                    if (i > 0) {
                        writer.write(',');
                    }
                    writeValue(Array.get(array, i));
                }
                writer.write(']');
                return;
            }

            if (length == 0) {
                writer.write(' ');
                writer.write(']');
                return;
            }
            writer.write(' ');
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    writer.write(',');
                    writer.write(' ');
                }
                writeValue(Array.get(array, i));
            }
            writer.write(' ');
            writer.write(']');
        }

        private void writeRecord(Object record) throws IOException {
            writer.write('{');
            depth++;
            boolean first = true;
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                Object componentValue = invokeAccessor(component, record);
                if (!first) {
                    writer.write(',');
                }
                writeNewlineAndIndent();
                writeString(component.getName());
                writer.write(pretty ? " : " : ":");
                writeValue(componentValue);
                first = false;
            }
            depth--;
            if (!first) {
                writeTrailingNewline();
            }
            writer.write('}');
        }

        private void writeDirectoryIndex(DirectoryIndex index) throws IOException {
            Map<String, Object> view = new LinkedHashMap<>();
            if (index.getPackage() != null) {
                view.put("package", index.getPackage());
            }
            view.put("files", index.getFiles());
            view.put("subdirectories", index.getSubdirectories());
            writeMap(view);
        }

        private Object invokeAccessor(RecordComponent component, Object record) {
            try {
                return component.getAccessor().invoke(record);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Unable to read component " + component.getName(), e);
            }
        }

        private void writeString(String value) throws IOException {
            Objects.requireNonNull(value, "JSON strings cannot be null");
            writer.write('"');
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                switch (ch) {
                    case '\\' -> writer.write("\\\\");
                    case '"' -> writer.write("\\\"");
                    case '\b' -> writer.write("\\b");
                    case '\f' -> writer.write("\\f");
                    case '\n' -> writer.write("\\n");
                    case '\r' -> writer.write("\\r");
                    case '\t' -> writer.write("\\t");
                    default -> {
                        if (ch < 0x20) {
                            writer.write(String.format("\\u%04X", (int) ch));
                        } else {
                            writer.write(ch);
                        }
                    }
                }
            }
            writer.write('"');
        }

        private void writeNewlineAndIndent() throws IOException {
            if (pretty) {
                writer.write('\n');
                for (int i = 0; i < depth; i++) {
                    writer.write(indentUnit);
                }
            }
        }

        private void writeTrailingNewline() throws IOException {
            if (pretty) {
                writer.write('\n');
                for (int i = 0; i < depth; i++) {
                    writer.write(indentUnit);
                }
            }
        }
    }
}
