package com.tagtraum.enlyze;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of a linewise diff solving the
 * <a href="https://en.wikipedia.org/wiki/Longest_common_subsequence_problem">Longest
 * Common Subsequence (LCS)</a> problem using dynamic programming.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class Diff {

    /**
     * Determines whether two files are identical.
     *
     * @param fileA file A
     * @param fileB file B
     * @return true, only if both files exists and their contents are identical
     * @throws IOException if one of the files cannot be read, even though it exists
     */
    public static boolean isIdentical(final Path fileA, final Path fileB) throws IOException {
        if (!Files.exists(fileA) || !Files.exists(fileB)) return false;
        if (fileA.equals(fileB)) return true;
        final byte[] oldBytes = Files.readAllBytes(fileA);
        final byte[] newBytes = Files.readAllBytes(fileB);
        return Arrays.equals(oldBytes, newBytes);
    }

    /**
     * Linewise diff between text file A and file B.
     * Additions in B are marked with '&lt;',
     * deletions in B are marked with '&gt;',
     * identical lines are marked with '='.
     *
     * @param fileA file A
     * @param fileB file B
     * @return a list of linewise diffs, each line marked with either '&lt;', '&gt;', or '='
     * @throws IOException if a file cannot be read
     */
    public static List<String> diff(final Path fileA, final Path fileB) throws IOException {
        if (!Files.exists(fileA) && Files.exists(fileB)) {
            return Files.readAllLines(fileB).stream().map(line -> "> " + line).collect(Collectors.toList());
        } else if (Files.exists(fileA) && !Files.exists(fileB)) {
            return Files.readAllLines(fileA).stream().map(line -> "< " + line).collect(Collectors.toList());
        }
        return diff(Files.readAllLines(fileA), Files.readAllLines(fileB));
    }

    /**
     * Generic diff between two lists of strings.
     *
     * @param x list of strings, e.g., lines or characters
     * @param y list of strings, e.g., lines or characters
     * @return list of diffs, each prefixed with '&lt;', '&gt;', or '='.
     */
    public static List<String> diff(final List<String> x, final List<String> y) {
        final int m = x.size();
        final int n = y.size();
        final int[][] lookup = new int[m + 1][n + 1];

        // fill accumulated cost lookup table
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (x.get(i - 1).equals(y.get(j - 1))) {
                    // if current elements of x and y match (diagonal)
                    lookup[i][j] = lookup[i - 1][j - 1] + 1;
                } else {
                    // if current element of x and y don't match, use max (not horizontal or vertical)
                    lookup[i][j] = Integer.max(lookup[i - 1][j], lookup[i][j - 1]);
                }
            }
        }
        // we have the cost table, now create a diff description
        final List<String> result = new ArrayList<>();
        diff(x, y, m , n, lookup, result);
        return result;
    }

    // Function to display the differences between two Strings
    private static void diff(final List<String> x, final List<String> y,
                                 final int m, final int n,
                                 final int[][] lookup,
                                 final List<String> result) {
        // if last element of x and y match
        if (m > 0 && n > 0 && x.get(m - 1).equals(y.get(n - 1))) {
            diff(x, y, m - 1, n - 1, lookup, result);
            result.add("= " + x.get(m - 1));
        }
        // current element of y is not present in x
        else if (n > 0 && (m == 0 || lookup[m][n - 1] >= lookup[m - 1][n])) {
            diff(x, y, m, n - 1, lookup, result);
            result.add("> " + y.get(n - 1));
        }
        // current element of x is not present in y
        else if (m > 0 && (n == 0 || lookup[m][n - 1] < lookup[m - 1][n])) {
            diff(x, y, m - 1, n, lookup, result);
            result.add("< " + x.get(m - 1));
        }
        // we're done
    }

}


