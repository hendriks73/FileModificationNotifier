package com.tagtraum.enlyze;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.logging.*;

/**
 * CommandLine client.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class CommandLine {

    private CommandLine() {}

    public static void main(final String[] args) throws IOException {
        if (args.length < 2) {
            usage();
            System.exit(1);
        }
        setupLogging();
        startService(args);
    }

    private static void startService(final String[] args) throws IOException {
        final Path root = Paths.get(args[0]).toAbsolutePath();
        final Path repo = Files.createTempDirectory("fmn");
        System.out.println("Root         : " + root);
        System.out.println("Repository   : " + repo);
        final FileModificationNotifier notifier = new FileModificationNotifier(root, repo);

        // ensure shutdown with cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("Shutdown.");
                notifier.stop();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        final DateTimeFormatter formatter = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.LONG)
            .withZone(ZoneId.systemDefault());

        final FileModificationObserver observer = event -> {
            final String prettyDiff = event.getDiff()
                .stream()
                .reduce("", (l1, l2) -> l1 + l2 + "\n");
            System.out.println("" + formatter.format(event.getFileTime().toInstant())
                + ": " + event.getFile() + "\n"
                + prettyDiff);
        };
        
        for (int i = 1; i < args.length; i++) {
            final Path file = Paths.get(args[i]).toAbsolutePath();
            System.out.println("Observed file: " + file);
            try {
                notifier.addObserver(file, observer);
            } catch (Exception e) {
                System.err.println("Failed to add observer for " + file);
                e.printStackTrace();
            }
        }
    }

    private static void usage() {
        System.err.println("FileModificationNotifier.");
        System.err.println();
        System.err.println("Usage:");
        System.err.println("    java -jar enlyze-1.0.0.jar ROOT FILE+");
        System.err.println();
        System.err.println("ROOT denotes the root directory to watch.");
        System.err.println("FILE denotes one or more specific files to watch.");
        System.err.println("ROOT and FILE must either be absolute or will be interpreted as");
        System.err.println("relative to the working directory.");
    }

    private static void setupLogging() throws IOException {
        // remove default logger
        final Logger globalLogger = Logger.getLogger("");
        Handler[] handlers = globalLogger.getHandlers();
        for(Handler handler : handlers) {
            globalLogger.removeHandler(handler);
        }
        // add our own
        final Logger logger = Logger.getLogger("com.tagtraum.enlyze");

        final ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.WARNING);
        consoleHandler.setFormatter(new SimpleFormatter());
        logger.addHandler(consoleHandler);

        final FileHandler fileHandler = new FileHandler("FileModificationNotifier.log");
        fileHandler.setLevel(Level.FINE);
        fileHandler.setFormatter(new SimpleFormatter());
        logger.addHandler(fileHandler);

        System.out.println("Logging additional messages to: FileModificationNotifier.log");
    }
}
