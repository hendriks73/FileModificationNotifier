package com.tagtraum.enlyze;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.util.logging.Level.*;

/**
 * Service that uses the Java {@link WatchService} to notify {@link FileModificationObserver}s
 * of changes to specific files.
 *
 * Implementation note: The Java {@link WatchService} is directory-based and under the hood allows
 * OS-level file system events (best case—if not, polling us used). Therefore the implementation
 * maps the file observation requests to directory observation requests.
 *
 * If the default {@code PollingWatchService} is used, the sensitivity (check interval) is
 * 10s (as of JDK 11).
 *
 * Note that path/file names are not necessarily canonicalized. It is the callers responsibility to
 * take care of this.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class FileModificationNotifier {

    private final static Logger LOGGER = Logger.getLogger(FileModificationNotifier.class.getName());

    private final Map<WatchKey, DirectoryObserver> keyToObserver = new HashMap<>();
    private final Map<Path, DirectoryObserver> dirToObserver = new HashMap<>();
    private WatchService watcher;
    private Path repository;
    private Path root;

    /**
     * Creates this service.
     * At creation time, only the repository directory is created.
     *
     * @param root root directory with files to watch
     * @param repository directory that will be used as a repository, so that we can create diffs
     * @throws IOException in case we cannot create the repository directory
     */
    public FileModificationNotifier(final Path root, final Path repository) throws IOException {
        this.repository = repository;
        this.root = root;
        if (Files.notExists(this.repository)) {
            Files.createDirectories(this.repository);
        }
    }

    /**
     * Adds a {@link FileModificationObserver} for a specific file to this service.
     * Should the file be changed, deleted or created, a {@link FileModificationEvent} is delivered.
     * Note that you may add the same observer for multiple files.
     *
     * Adding an observer implicitly starts the notifier.
     *
     * @param file file
     * @param observer observer
     * @throws IOException if something goes wrong.
     * @throws IllegalArgumentException if the file is a directory or does not reside below the root folder
     */
    public void addObserver(final Path file, final FileModificationObserver observer) throws IOException {
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("Expected a file name, not a directory: " + file);
        }
        synchronized (this) {
            if (!isRunning()) {
                start();
            }
        }

        final Path dir = file.getParent();
        if (!Files.exists(dir)) {
            LOGGER.info("Created directory " + dir + " so that it can be watched.");
            Files.createDirectories(dir);
        }

        if (!dir.toAbsolutePath().normalize().toString().startsWith(this.root.toAbsolutePath().normalize().toString())) {
            throw new IllegalArgumentException("The file you are trying to watch does not reside in the root directory. "
                +  "Root=" + this.root + ", File=" + file );
        }

        // ensure the watched file is in the repo
        final Path repositoryFile = getRepositoryFile(file);
        if (Files.exists(file) && Files.notExists(repositoryFile)) {
            if (!Files.exists(repositoryFile.getParent())) {
                Files.createDirectories(repositoryFile.getParent());
            }
            Files.copy(file, repositoryFile, COPY_ATTRIBUTES);
        }
        DirectoryObserver directoryObserver = this.dirToObserver.get(dir);
        if (directoryObserver == null) {
            LOGGER.info("Observer for file " + file + " requires a new DirectoryObserver, as " + dir + " is not watched yet.");
            final WatchKey key = dir.register(watcher, ENTRY_MODIFY, ENTRY_DELETE, ENTRY_CREATE);
            directoryObserver = new DirectoryObserver(dir, key);
            this.keyToObserver.put(key, directoryObserver);
            this.dirToObserver.put(dir, directoryObserver);
        }
        directoryObserver.addObserver(file, observer);
    }

    /**
     * Removes an {@link FileModificationObserver} for a specific file from this service.
     * If the last observer for a specific file/dir has been released, associated system
     * resources are also released. This includes the repository copy of a file (it is deleted).
     *
     * @param file file
     * @param observer observer
     * @throws IllegalArgumentException if the file is a directory
     */
    public void removeObserver(final Path file, final FileModificationObserver observer) throws IOException {
        final Path dir = file.getParent();
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("Expected a file name, not a directory: " + file);
        }
        final DirectoryObserver directoryObserver = this.dirToObserver.get(dir);
        if (directoryObserver != null) {
            directoryObserver.removeObserver(file, observer);
            if (!directoryObserver.getKey().isValid()) {
                this.dirToObserver.remove(dir);
                this.keyToObserver.remove(directoryObserver.getKey());
                LOGGER.info("DirectoryObserver for " + dir + " is not needed anymore and was removed.");
            }
            if (!directoryObserver.getFiles().contains(file)) {
                LOGGER.info("Removing repository copy of file, because it is not watched anymore: " + file);
                try {
                    Files.deleteIfExists(getRepositoryFile(file));
                } catch (IOException e) {
                    LOGGER.log(WARNING, "Failed to delete file from repository that is not watched anymore: " + file, e);
                }
            }
            if (this.dirToObserver.isEmpty() && isRunning()) {
                LOGGER.info("Last DirectoryObserver has been removed.");
                stop();
            }
        }
    }

    /**
     * Indicates whether service is running.
     *
     * @return true or false
     */
    public synchronized boolean isRunning() {
        return this.watcher != null;
    }

    /**
     * Start the notification service. {@link FileModificationEvent} are delivered
     * asynchronously.
     *
     * @throws IOException if we cannot start the used {@link WatchService}.
     * @throws  UnsupportedOperationException
     *          If the {@code FileSystem} does not support watching file system
     *          objects for changes and events. This exception is not thrown
     *          by {@code FileSystems} created by the default provider.
     */
    private synchronized void start() throws IOException {
        if (!isRunning()) {
            LOGGER.info("Starting " + this.getClass().getSimpleName());
            final WatchService ws = FileSystems.getDefault().newWatchService();
            // make sure we keep the reference in the thread,
            // even if we null this.watcher upon stopping.
            this.watcher = ws;
            new Thread(() -> {
                while (isRunning()) {
                    WatchKey key = null;
                    try {
                        key = ws.take();
                        final DirectoryObserver directoryObserver = keyToObserver.get(key);
                        if (directoryObserver != null) {
                            directoryObserver.fireFileModificationEvent();
                        }
                    } catch (InterruptedException e) {
                        LOGGER.info(this.getClass().getSimpleName() + " was stopped due to an interruption.");
                        return;
                    } catch (ClosedWatchServiceException e) {
                        LOGGER.info(this.getClass().getSimpleName() + " was closed.");
                        return;
                    } finally {
                        if (key != null) {
                            key.reset();
                        }
                    }
                }
                LOGGER.info(this.getClass().getSimpleName() + " was stopped.");
            }, FileModificationNotifier.class.getSimpleName()).start();
        } else {
            LOGGER.info(this.getClass().getSimpleName() + "  is already running.");
        }
    }

    /**
     * Stops notifications, removes all observers, and deletes files from the repository.
     * This means that before re-starting the notifier, you must re-add all observers.
     *
     * @throws IOException if something goes wrong.
     */
    public synchronized void stop() throws IOException {
        if (isRunning()) {
            LOGGER.info("Stopping " + this.getClass().getSimpleName());
            try {
                this.watcher.close();
            } finally {
                this.watcher = null;
                this.keyToObserver.clear();
                this.dirToObserver.clear();
            }
            deleteDirectory(this.repository);
        } else {
            LOGGER.info("Can't stop " + this.getClass().getSimpleName() + ". It's not running.");
        }
    }

    /**
     * Helper to recursively delete directory.
     *
     * @param directory dir
     * @throws IOException if something goes wrong
     */
    private static void deleteDirectory(final Path directory) throws IOException {
        if (Files.exists(directory)) {
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

    /**
     * Translate from file below the watched root directory to a file in the
     * repository.
     *
     * @param file file
     * @return corresponding file in repository (may or may not exist)
     */
    private Path getRepositoryFile(final Path file) {
        final Path relativeFile = FileModificationNotifier.this.root.relativize(file);
        return FileModificationNotifier.this.repository.resolve(relativeFile);
    }

    /**
     * Handles per-directory observation of files.
     */
    private final class DirectoryObserver {

        private final Map<Path, Set<FileModificationObserver>> observers = new HashMap<>();
        private final Path dir;
        private final WatchKey key;

        /**
         *
         * @param dir directory to watch
         * @param key {@link WatchKey} for the directory in question.
         */
        public DirectoryObserver(final Path dir, final WatchKey key) {
            this.dir = dir;
            this.key = key;
        }

        /**
         * Files that are watched by this directory observer.
         *
         * @return set of files
         */
        public Set<Path> getFiles() {
            return observers.keySet();
        }

        /**
         * {@link WatchKey} for the watched directory.
         *
         * @return key
         */
        public WatchKey getKey() {
            return key;
        }

        /**
         * Notify observers of a watched file change, creation or deletion in the
         * watched directory.
         */
        public void fireFileModificationEvent() {
            for (WatchEvent<?> e: key.pollEvents()) {
                final WatchEvent.Kind<?> kind = e.kind();
                if (kind == ENTRY_MODIFY || kind == ENTRY_CREATE || kind == ENTRY_DELETE) {
                    final Path file = dir.resolve((Path) e.context());
                    final Set<FileModificationObserver> fileModificationObservers = observers.get(file);
                    if (fileModificationObservers != null) {
                        try {
                            final Path repositoryFile = getRepositoryFile(file);
                            if (!Diff.isIdentical(file, repositoryFile)) {
                                final FileModificationEvent event = new FileModificationEvent(file, repositoryFile);
                                for (final FileModificationObserver observer : fileModificationObservers) {
                                    observer.fileModified(event);
                                }
                            }
                            if (kind == ENTRY_MODIFY || kind == ENTRY_CREATE) {
                                if (!Files.exists(repositoryFile.getParent())) {
                                    Files.createDirectories(repositoryFile.getParent());
                                }
                                Files.copy(file, repositoryFile, REPLACE_EXISTING, COPY_ATTRIBUTES);
                                LOGGER.log(INFO, "Copying file to repository: " + file);
                            } else {
                                Files.deleteIfExists(repositoryFile);
                                LOGGER.log(INFO, "Deleting repository file: " + file);
                            }
                        } catch (Exception ex) {
                            LOGGER.log(WARNING, "Failure while firing file modification event for " + file, ex);
                        }
                    }
                } else {
                    LOGGER.log(SEVERE, "Unknown file system event: " + e);
                }
            }
        }

        /**
         * Add an observer for a file in the directory of this observer.
         *
         * @param file file
         * @param observer observer
         */
        public void addObserver(final Path file, final FileModificationObserver observer) {
            observers.computeIfAbsent(file, k -> new HashSet<>()).add(observer);
        }

        /**
         * Remove an observer.
         * When the last observer for this directory is removed, {@link WatchKey#cancel()}
         * is called to release system resources.
         *
         * @param file file
         * @param observer observer
         */
        public void removeObserver(final Path file, final FileModificationObserver observer) {
            final Set<FileModificationObserver> fileModificationObservers = observers.get(file);
            if (fileModificationObservers != null) {
                fileModificationObservers.remove(observer);
                if (fileModificationObservers.isEmpty()) {
                    observers.remove(file);
                }
            }
            if (observers.isEmpty()) {
                key.cancel();
            }
        }
    }

}
