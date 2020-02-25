package com.tagtraum.enlyze;

/**
 * FileModificationObserver.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public interface FileModificationObserver {

    void fileModified(FileModificationEvent event);

}
