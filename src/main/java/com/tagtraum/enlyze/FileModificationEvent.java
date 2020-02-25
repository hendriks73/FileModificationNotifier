package com.tagtraum.enlyze;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Event signaling the modification of a file.
 * This may be a creation event, a deletion, or a real modification.
 * The provided diff is linewise ({@link Diff}). Unchanged lines start with {@code '= '},
 * added lines with {@code '> '}, and deleted lines with {@code '< '}.
 * In a newly created file, all lines are added. In a deleted file, all
 * lines are deleted.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class FileModificationEvent {

    private final Path file;
    private final FileTime fileTime;
    private final List<String> diff;

    /**
     * Create event.
     *
     * @param file file
     * @param oldFile older version of the same file
     * @throws IOException should something go wrong
     */
    public FileModificationEvent(final Path file, final Path oldFile) throws IOException {
        this(file, Diff.diff(oldFile, file));
    }

    /**
     * Create event.
     *
     * @param file file
     * @param diff diff between file and an older version of it
     * @throws IOException should something go wrong
     */
    public FileModificationEvent(final Path file, final List<String> diff) throws IOException {
        this(file, getLastModifiedTime(file), diff);
    }

    /**
     * Create event.
     *
     * @param file file
     * @param fileTime timestamp the change occurred
     * @param diff diff between file and an older version of it
     */
    public FileModificationEvent(final Path file, final FileTime fileTime, final List<String> diff) {
        this.file = file;
        this.fileTime = fileTime;
        this.diff = diff;
    }

    private static FileTime getLastModifiedTime(final Path file) throws IOException {
        return Files.exists(file)
            ? Files.getLastModifiedTime(file)
            : FileTime.from(Instant.now());
    }

    public Path getFile() {
        return file;
    }

    public FileTime getFileTime() {
        return fileTime;
    }

    /**
     * Linewise diff with prefixes indicating the modification ('&gt;', '&lt;', '=').
     *
     * @return linewise diff.
     */
    public List<String> getDiff() {
        return diff;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final FileModificationEvent that = (FileModificationEvent) o;
        return Objects.equals(file, that.file) &&
            Objects.equals(fileTime, that.fileTime) &&
            Objects.equals(diff, that.diff);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, fileTime, diff);
    }

    @Override
    public String toString() {
        final String prettyDiff = diff.stream().reduce("", (l1, l2) -> l1 + l2 + "\n");
        return "FileModificationEvent{" +
            "file=" + file +
            ", date=" + fileTime +
            ", diff:\n" + prettyDiff +
            '}';
    }
}
