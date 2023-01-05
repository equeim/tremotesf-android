package org.equeim.tremotesf

import org.equeim.tremotesf.ui.utils.normalizePath
import org.equeim.tremotesf.ui.utils.toNativeSeparators
import org.junit.Assert
import org.junit.Test

class PathsTest {
    private data class NormalizeTestCase(val inputPath: String, val expectedNormalizedPath: String)

    @Test
    fun checkNormalize() {
        val testCases = listOf(
            NormalizeTestCase("", ""),
            NormalizeTestCase("/", "/"),
            NormalizeTestCase("//", "/"),
            NormalizeTestCase("///", "/"),
            NormalizeTestCase(" / ", "/"),
            NormalizeTestCase("///home//foo", "/home/foo"),
            NormalizeTestCase("C:/home//foo", "C:/home/foo"),
            NormalizeTestCase("C:/home//foo/", "C:/home/foo"),
            NormalizeTestCase("C:\\home\\foo", "C:/home/foo"),
            NormalizeTestCase("C:\\home\\foo\\", "C:/home/foo"),
            NormalizeTestCase("C:\\home\\foo\\\\", "C:/home/foo"),
            NormalizeTestCase("z:\\home\\foo", "Z:/home/foo"),
            NormalizeTestCase("D:\\", "D:/"),
            NormalizeTestCase(" D:\\ ", "D:/"),
            NormalizeTestCase("D:\\\\", "D:/"),
            NormalizeTestCase("D:/", "D:/"),
            NormalizeTestCase("D://", "D:/"),

            // Backslashes in Unix paths are untouched
            NormalizeTestCase("///home//fo\\o", "/home/fo\\o"),

            // Internal whitespace is untouched
            NormalizeTestCase("///home//fo  o", "/home/fo  o"),
            NormalizeTestCase("C:\\home\\fo o", "C:/home/fo o"),

            // These are not absolute Windows file paths and are left untouched
            NormalizeTestCase("d:", "d:"),
            NormalizeTestCase("d:foo", "d:foo"),
            NormalizeTestCase("C::\\wtf", "C::\\wtf"),
            NormalizeTestCase("\\\\LOCALHOST\\c$\\home\\foo", "\\\\LOCALHOST\\c\$\\home\\foo")
        )
        testCases.forEach {
            println(it)
            Assert.assertEquals(it.expectedNormalizedPath, it.inputPath.normalizePath())
        }
    }

    private data class NativeSeparatorsTestCase(val inputPath: String, val expectedNativeSeparatorsPath: String)

    @Test
    fun checkToNativeSeparators() {
        val testCases = listOf(
            NativeSeparatorsTestCase("/", "/"),
            NativeSeparatorsTestCase("/home/foo", "/home/foo"),
            NativeSeparatorsTestCase("C:/", "C:\\"),
            NativeSeparatorsTestCase("C:/home/foo", "C:\\home\\foo"),

            // These are not absolute Windows file paths and are left untouched
            NativeSeparatorsTestCase("d:", "d:"),
            NativeSeparatorsTestCase("d:foo", "d:foo"),
            NativeSeparatorsTestCase("C::/wtf", "C::/wtf"),
            NativeSeparatorsTestCase("\\\\LOCALHOST\\c$\\home\\foo", "\\\\LOCALHOST\\c\$\\home\\foo")
        );
        testCases.forEach {
            println(it)
            Assert.assertEquals(it.expectedNativeSeparatorsPath, it.inputPath.toNativeSeparators())
        }
    }
}