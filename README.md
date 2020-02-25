# File Modification Notifier

Simple service that allows observation of files below a root dir for
change, creation, and deletion.

## Building

This service is meant for Java 11. Thus, to build (and run), you need to have
Java 11 or later installed.

It can be built with [Maven](http://maven.apache.org) by executing:

    mvn clean install

You will find the file `enlyze-1.0.0.jar` in the `target` subdirectory.

## Running 

### Library

To use as library, follow these steps:

```      
import...

Path root = Paths.get("c:\\someRootDir");
// repo, needed to compute differences
Path repo = Paths.get("c:\\someRepoDir");

// create service
FileModificationNotifier notifier = new FileModificationNotifier(root, repo);

// create observer
FileModificationObserver observer = new FileModificationObserver() {
    @Override
    public void fileModified(final FileModificationEvent event) {
        System.out.println("File " + event.getFile()
            + " has been changed at "
            + event.getFileTime());
        System.out.println("Here's the diff:");
        final String prettyDiff = event.getDiff().stream().reduce("", (l1, l2) -> l1 + l2 + "\n");
        System.out.println(prettyDiff);
    }
};    
     
// register observer
notifier.addObserver(root.resolve("someFile.txt"), observer);

[...]
                
// clean up, when not used anymore
notifier.stop();
```       

The diff, marks each line as added (>), deleted (<) or identical (=).

### Command Line

To use from the command line:

```
java -jar enlyze-1.0.0.jar ROOT FILE+ 
```

You must specify a root directory and at least one file to watch.
Messages are logged to the console. 