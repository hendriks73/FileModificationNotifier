package com.tagtraum.enlyze;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

/**
 * TestFileModificationNotifier.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class TestFileModificationNotifier {

    private Path root = null;
    private Path repo = null;
    private Path rootFileA = null;
    private Path repoFileA = null;
    private FileModificationNotifier notifier = null;

    @Before
    public void setup() throws IOException {
        this.root = Files.createTempDirectory("root");
        this.repo = Files.createTempDirectory("repo");

        assertEquals(0, Files.list(root).count());
        assertEquals(0, Files.list(repo).count());

        this.rootFileA = Files.createTempFile(root,"pre", "suff");
        this.repoFileA = repo.resolve(rootFileA.getFileName());

        Files.writeString(rootFileA, "some content");
        this.notifier = new FileModificationNotifier(root, repo);
    }

    @After
    public void teardown() throws IOException {
        if (root != null && Files.exists(root)) deleteDirectory(root);
        if (repo != null && Files.exists(repo)) deleteDirectory(repo);
        if (this.notifier.isRunning()) this.notifier.stop();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalObserver() throws IOException {
        final CollectingObserver observer = new CollectingObserver();
        this.notifier.addObserver(root.resolve("../somefile.txt"), observer);
    }

    @Test
    public void testModification() throws IOException, InterruptedException {
        final CollectingObserver observer = new CollectingObserver();
        this.notifier.addObserver(rootFileA, observer);

        // because an observer was added, the file must now also exist in the repo
        // with the same content and the same timestamp
        final Path repoFileA = repo.resolve(rootFileA.getFileName());
        assertTrue(Files.exists(repoFileA));
        assertEquals(Files.getLastModifiedTime(rootFileA), Files.getLastModifiedTime(repoFileA));
        assertTrue(Diff.isIdentical(repoFileA, rootFileA));

        final String oldContent = Files.readString(rootFileA, UTF_8);
        final String newContent = "some new text " + System.currentTimeMillis();
        Files.writeString(rootFileA, newContent);

        // give the WatchService some time to detect the change.
        Thread.sleep(11000);

        assertEquals(1, observer.getEvents().size());
        final FileModificationEvent event = observer.getEvents().get(0);
        assertEquals(rootFileA, event.getFile());

        final List<String> diff = event.getDiff();
        assertEquals("< " + oldContent, diff.get(0));
        assertEquals("> " + newContent, diff.get(1));
        // remove observer
        this.notifier.removeObserver(rootFileA, observer);
    }

    @Test
    public void testDeletion() throws IOException, InterruptedException {
        final CollectingObserver observer = new CollectingObserver();
        this.notifier.addObserver(rootFileA, observer);
        assertTrue(Files.exists(repoFileA));
        final FileTime originalDate = Files.getLastModifiedTime(repoFileA);
        final String oldContent = Files.readString(rootFileA, UTF_8);
        Files.delete(rootFileA);

        // give the WatchService some time to detect the change.
        Thread.sleep(11000);

        assertEquals(1, observer.getEvents().size());
        final FileModificationEvent event = observer.getEvents().get(0);
        assertEquals(rootFileA, event.getFile());
        assertTrue(Files.notExists(repoFileA));

        final List<String> diff = event.getDiff();
        assertEquals("< " + oldContent, diff.get(0));
        assertEquals(1, diff.size());
        assertTrue(originalDate.toInstant().isBefore(event.getFileTime().toInstant()));

        // remove observer
        this.notifier.removeObserver(rootFileA, observer);
        // as we have removed the last observer, the repo file should be gone
        assertTrue(Files.notExists(repoFileA));
        assertFalse(notifier.isRunning());
    }

    @Test
    public void testCreation() throws IOException, InterruptedException {
        // we just use tempFile for the name
        final Path rootFileB = Files.createTempFile(root, "pre", "suff");
        // and therefore delete it right away
        Files.delete(rootFileB);
        final Path repoFileB = repo.resolve(rootFileB.getFileName());
        assertFalse(Files.exists(repoFileB));
        assertFalse(Files.exists(rootFileB));

        final CollectingObserver observer = new CollectingObserver();
        this.notifier.addObserver(rootFileB, observer);
        // now create the file
        final String newContent = "newly created";
        Files.writeString(rootFileB, newContent, UTF_8);

        // give the WatchService some time to detect the change.
        Thread.sleep(11000);

        assertEquals(1, observer.getEvents().size());
        final FileModificationEvent event = observer.getEvents().get(0);
        assertEquals(rootFileB, event.getFile());
        assertTrue(Files.exists(repoFileB));

        final List<String> diff = event.getDiff();
        assertEquals("> " + newContent, diff.get(0));
        assertEquals(1, diff.size());
        assertEquals(Files.getLastModifiedTime(rootFileB), event.getFileTime());

        // remove observer
        this.notifier.removeObserver(rootFileB, observer);
        // as we have removed the last observer, the repo file should be gone
        assertTrue(Files.notExists(repoFileB));
        assertFalse(notifier.isRunning());
    }

    /**
     * Helper to collect {@link FileModificationEvent}s.
     */
    private static class CollectingObserver implements FileModificationObserver {
        private List<FileModificationEvent> events = new ArrayList<>();

        @Override
        public void fileModified(final FileModificationEvent event) {
            this.events.add(event);
        }

        public List<FileModificationEvent> getEvents() {
            return events;
        }
    }


    /**
     * Helper to recursively delete directory.
     *
     * @param directory dir
     * @throws IOException if something goes wrong
     */
    private static void deleteDirectory(final Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
