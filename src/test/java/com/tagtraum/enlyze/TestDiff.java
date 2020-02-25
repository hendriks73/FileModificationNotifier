package com.tagtraum.enlyze;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

/**
 * TestTextFileDiff.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class TestDiff {

    @Test
    public void testIsIdentical() throws IOException {
        Path fileA = null;
        Path fileB = null;
        Path fileC = null;
        try {
            fileA = Files.createTempFile("pre", "suff");
            fileB = Files.createTempFile("pre", "suff");
            fileC = Files.createTempFile("pre", "suff");
            Files.writeString(fileA, "some string in file A and C", UTF_8);
            Files.writeString(fileB, "some string in file B", UTF_8);
            Files.writeString(fileC, "some string in file A and C", UTF_8);
            assertTrue(Diff.isIdentical(fileA, fileA));
            assertTrue(Diff.isIdentical(fileA, fileC));
            assertFalse(Diff.isIdentical(fileA, fileB));
            assertFalse(Diff.isIdentical(fileA, Paths.get("file_that_does_not_exist.txt")));
        } finally {
            if (fileA != null) Files.deleteIfExists(fileA);
            if (fileB != null) Files.deleteIfExists(fileB);
            if (fileC != null) Files.deleteIfExists(fileC);
        }
    }

    @Test
    public void testSimpleDiff() {
        final List<String> oldLines = Arrays.asList("aaaa", "bbbb", "cccc");
        final List<String> newLines = Arrays.asList("aaaa", "dddd", "eeee", "cccc");
        final List<String> diff = Diff.diff(oldLines, newLines);
        assertEquals("= aaaa", diff.get(0));
        assertEquals("< bbbb", diff.get(1));
        assertEquals("> dddd", diff.get(2));
        assertEquals("> eeee", diff.get(3));
        assertEquals("= cccc", diff.get(4));
    }
}
