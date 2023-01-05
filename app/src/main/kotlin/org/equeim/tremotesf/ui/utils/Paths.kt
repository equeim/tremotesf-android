package org.equeim.tremotesf.ui.utils

fun String.normalizePath(): String =
    ifEmpty { return this }
        .trim()
        .ifEmpty { return this }
        .run {
            val windows = isAbsoluteWindowsFilePath()
            if (windows) {
                fromNativeWindowsSeparators()
                    .capitalizeWindowsDriveLetter()
            } else {
                this
            }
                .collapseRepeatingSeparators()
                .dropTrailingSeparator(windows)
        }

private fun String.isAbsoluteWindowsFilePath(): Boolean = WINDOWS_FILE_PATH_REGEX.matches(this)

private fun String.fromNativeWindowsSeparators(): String =
    replace(WINDOWS_SEPARATOR_CHAR, UNIX_SEPARATOR_CHAR)

private fun String.capitalizeWindowsDriveLetter(): String =
    if (first().isLowerCase()) {
        replaceFirstChar { it.uppercase() }
    } else {
        this
    }

private fun String.collapseRepeatingSeparators(): String =
    replace(REPEATING_UNIX_SEPARATORS_REGEX, UNIX_SEPARATOR_STRING)

private fun String.toNativeWindowsSeparators(): String =
    replace(UNIX_SEPARATOR_CHAR, WINDOWS_SEPARATOR_CHAR)

private fun String.dropTrailingSeparator(isAbsoluteWindowsFilePath: Boolean): String =
    if (length > 1 &&
        (!isAbsoluteWindowsFilePath || length > MINIMUM_WINDOWS_PATH_LENGTH) &&
        last() == UNIX_SEPARATOR_CHAR
    ) {
        dropLast(1)
    } else {
        this
    }

/**
 * [this] should be normalized. Paths returned by libtremotesf are already normalized
 */
fun String.toNativeSeparators(): String =
    if (isAbsoluteWindowsFilePath()) toNativeWindowsSeparators() else this

private val WINDOWS_FILE_PATH_REGEX = Regex("(^[A-Za-z]:[\\\\/].*\$)")
private const val WINDOWS_SEPARATOR_CHAR = '\\'
private const val MINIMUM_WINDOWS_PATH_LENGTH = 3 // E.g. C:\

private val REPEATING_UNIX_SEPARATORS_REGEX = Regex("(/+)")
private const val UNIX_SEPARATOR_CHAR = '/'
private const val UNIX_SEPARATOR_STRING = "/"
