/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package warlockfe.warlock3.wrayth.util

import com.eygraber.uri.Uri

// Stolen from Java, ported to Uri

// RFC2396 5.2
internal fun resolve(base: Uri, child: Uri): Uri {
    // check if child if opaque first so that NPE is thrown
    // if child is null.
    if (child.isOpaque || base.isOpaque) return child

    // 5.2 (2): Reference to current document (lone fragment)
    if ((child.scheme == null) && (child.authority == null)
        && child.path.isNullOrEmpty() && (child.fragment != null)
        && (child.query == null)
    ) {
        if ((base.fragment != null)
            && child.fragment.equals(base.fragment)
        ) {
            return base
        }
        val ru = Uri.Builder().apply {
            scheme(base.scheme)
            authority(base.authority)
            path(base.path)
            fragment(child.fragment)
            query(base.query)
        }
        return ru.build()
    }

    // 5.2 (3): Child is absolute
    if (child.scheme != null) return child

    val ru = Uri.Builder() // Resolved URI
    ru.scheme(base.scheme)
    ru.query(child.query)
    ru.fragment(child.fragment)

    // 5.2 (4): Authority
    if (child.authority == null) {
        ru.authority(base.authority)

        val cp = child.path
        if (!cp.isNullOrEmpty() && cp[0] == '/') {
            // 5.2 (5): Child path is absolute
            ru.path(child.path)
        } else {
            // 5.2 (6): Resolve relative path
            ru.path(resolvePath(base.path ?: "", cp ?: "", base.isAbsolute))
        }
    } else {
        ru.authority(child.authority)
        ru.path(child.path)
    }

    // 5.2 (7): Recombine (nothing to do here)
    return ru.build()
}

// RFC2396 5.2 (6)
private fun resolvePath(base: String, child: String, absolute: Boolean): String {
    val i = base.lastIndexOf('/')
    val cn = child.length
    var path = ""

    if (cn == 0) {
        // 5.2 (6a)
        if (i >= 0) path = base.take(i + 1)
    } else {
        // 5.2 (6a-b)
        if (i >= 0 || !absolute) {
            path = base.take(i + 1) + child
        } else {
            path = "/$child"
        }
    }

    // 5.2 (6c-f)
    val np: String = normalize(path)

    // 5.2 (6g): If the result is absolute but the path begins with "../",
    // then we simply leave the path as-is
    return np
}

// Normalize the given path string.  A normal path string has no empty
// segments (i.e., occurrences of "//"), no segments equal to ".", and no
// segments equal to ".." that are preceded by a segment not equal to "..".
// In contrast to Unix-style pathname normalization, for URI paths we
// always retain trailing slashes.
//
private fun normalize(ps: String): String {
    // Does this path need normalization?

    val ns: Int = needsNormalization(ps) // Number of segments
    if (ns < 0)  // Nope -- just return it
        return ps

    val path = ps.toCharArray() // Path in char-array form

    // Split path into segments
    val segs = IntArray(ns) // Segment-index array
    split(path, segs)

    // Remove dots
    removeDots(path, segs)

    // Prevent scheme-name confusion
    maybeAddLeadingDot(path, segs)

    // Join the remaining segments and return the result
    val s = path.concatToString(0, join(path, segs))
    if (s == ps) {
        // string was already normalized
        return ps
    }
    return s
}

private fun needsNormalization(path: String): Int {
    var normal = true
    var ns = 0 // Number of segments
    val end = path.length - 1 // Index of last char in path
    var p = 0 // Index of next char in path

    // Skip initial slashes
    while (p <= end) {
        if (path.get(p) != '/') break
        p++
    }
    if (p > 1) normal = false

    // Scan segments
    while (p <= end) {
        // Looking at "." or ".." ?

        if ((path.get(p) == '.')
            && ((p == end)
                    || ((path.get(p + 1) == '/')
                    || ((path.get(p + 1) == '.')
                    && ((p + 1 == end)
                    || (path.get(p + 2) == '/')))))
        ) {
            normal = false
        }
        ns++

        // Find beginning of next segment
        while (p <= end) {
            if (path.get(p++) != '/') continue

            // Skip redundant slashes
            while (p <= end) {
                if (path.get(p) != '/') break
                normal = false
                p++
            }

            break
        }
    }

    return if (normal) -1 else ns
}

// Split the given path into segments, replacing slashes with nulls and
// filling in the given segment-index array.
//
// Preconditions:
//   segs.length == Number of segments in path
//
// Postconditions:
//   All slashes in path replaced by '\0'
//   segs[i] == Index of first char in segment i (0 <= i < segs.length)
//
private fun split(path: CharArray, segs: IntArray) {
    val end = path.size - 1 // Index of last char in path
    var p = 0 // Index of next char in path
    var i = 0 // Index of current segment

    // Skip initial slashes
    while (p <= end) {
        if (path[p] != '/') break
        path[p] = '\u0000'
        p++
    }

    while (p <= end) {
        // Note start of segment

        segs[i++] = p++

        // Find beginning of next segment
        while (p <= end) {
            if (path[p++] != '/') continue
            path[p - 1] = '\u0000'

            // Skip redundant slashes
            while (p <= end) {
                if (path[p] != '/') break
                path[p++] = '\u0000'
            }
            break
        }
    }

    if (i != segs.size) error("invalid size") // ASSERT
}

// Join the segments in the given path according to the given segment-index
// array, ignoring those segments whose index entries have been set to -1,
// and inserting slashes as needed.  Return the length of the resulting
// path.
//
// Preconditions:
//   segs[i] == -1 implies segment i is to be ignored
//   path computed by split, as above, with '\0' having replaced '/'
//
// Postconditions:
//   path[0] .. path[return value] == Resulting path
//
private fun join(path: CharArray, segs: IntArray): Int {
    val ns = segs.size // Number of segments
    val end = path.size - 1 // Index of last char in path
    var p = 0 // Index of next path char to write

    if (path[p] == '\u0000') {
        // Restore initial slash for absolute paths
        path[p++] = '/'
    }

    for (i in 0..<ns) {
        var q = segs[i] // Current segment
        if (q == -1)  // Ignore this segment
            continue

        if (p == q) {
            // We're already at this segment, so just skip to its end
            while ((p <= end) && (path[p] != '\u0000')) p++
            if (p <= end) {
                // Preserve trailing slash
                path[p++] = '/'
            }
        } else if (p < q) {
            // Copy q down to p
            while ((q <= end) && (path[q] != '\u0000')) path[p++] = path[q++]
            if (q <= end) {
                // Preserve trailing slash
                path[p++] = '/'
            }
        } else error("ASSERT") // ASSERT false
    }

    return p
}

// Remove "." segments from the given path, and remove segment pairs
// consisting of a non-".." segment followed by a ".." segment.
//
private fun removeDots(path: CharArray, segs: IntArray) {
    val ns = segs.size
    val end = path.size - 1

    var i = 0
    while (i < ns) {
        var dots = 0 // Number of dots found (0, 1, or 2)

        // Find next occurrence of "." or ".."
        do {
            val p = segs[i]
            if (path[p] == '.') {
                if (p == end) {
                    dots = 1
                    break
                } else if (path[p + 1] == '\u0000') {
                    dots = 1
                    break
                } else if ((path[p + 1] == '.')
                    && ((p + 1 == end)
                            || (path[p + 2] == '\u0000'))
                ) {
                    dots = 2
                    break
                }
            }
            i++
        } while (i < ns)
        if ((i > ns) || (dots == 0)) break

        if (dots == 1) {
            // Remove this occurrence of "."
            segs[i] = -1
        } else {
            // If there is a preceding non-".." segment, remove both that
            // segment and this occurrence of ".."; otherwise, leave this
            // ".." segment as-is.
            var j: Int
            j = i - 1
            while (j >= 0) {
                if (segs[j] != -1) break
                j--
            }
            if (j >= 0) {
                val q = segs[j]
                if (!((path[q] == '.')
                            && (path[q + 1] == '.')
                            && (path[q + 2] == '\u0000'))
                ) {
                    segs[i] = -1
                    segs[j] = -1
                }
            }
        }
        i++
    }
}

// DEVIATION: If the normalized path is relative, and if the first
// segment could be parsed as a scheme name, then prepend a "." segment
//
private fun maybeAddLeadingDot(path: CharArray, segs: IntArray) {
    if (path[0] == '\u0000')  // The path is absolute
        return

    val ns = segs.size
    var f = 0 // Index of first segment
    while (f < ns) {
        if (segs[f] >= 0) break
        f++
    }
    if ((f >= ns) || (f == 0))  // The path is empty, or else the original first segment survived,
    // in which case we already know that no leading "." is needed
        return

    var p = segs[f]
    while ((p < path.size) && (path[p] != ':') && (path[p] != '\u0000')) p++
    if (p >= path.size || path[p] == '\u0000')  // No colon in first segment, so no "." needed
        return

    // At this point we know that the first segment is unused,
    // hence we can insert a "." segment at that position
    path[0] = '.'
    path[1] = '\u0000'
    segs[0] = 0
}
