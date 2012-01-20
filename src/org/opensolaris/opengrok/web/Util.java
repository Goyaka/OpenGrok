/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.history.HistoryException;
import org.opensolaris.opengrok.history.HistoryGuru;

/**
 * File for useful functions
 */
public final class Util {
    /**
     * Return a string which represents a <code>CharSequence</code> in HTML.
     *
     * @param q a character sequence
     * @return a string representing the character sequence in HTML
     */

    private Util() {
        // Util class, should not be constructed
    }
    
    public static String htmlize(CharSequence q) {
        StringBuilder sb = new StringBuilder(q.length() * 2);
        htmlize(q, sb);
        return sb.toString();
    }

    /**
     * Append a character sequence to an <code>Appendable</code> object. Escape
     * special characters for HTML.
     *
     * @param q a character sequence
     * @param out the object to append the character sequence to
     * @exception IOException if an I/O error occurs
     */
    public static void htmlize(CharSequence q, Appendable out)
            throws IOException {
        for (int i = 0; i < q.length(); i++) {
            htmlize(q.charAt(i), out);
        }
    }

    /**
     * Append a character sequence to a <code>StringBuilder</code>
     * object. Escape special characters for HTML. This method is identical to
     * <code>htmlize(CharSequence,Appendable)</code>, except that it is
     * guaranteed not to throw <code>IOException</code> because it uses a
     * <code>StringBuilder</code>.
     *
     * @param q a character sequence
     * @param out the object to append the character sequence to
     * @see #htmlize(CharSequence, Appendable)
     */
    @SuppressWarnings("PMD.AvoidThrowingRawExceptionTypes")
    public static void htmlize(CharSequence q, StringBuilder out) {
        try {
            htmlize(q, (Appendable) out);
        } catch (IOException ioe) {
            // StringBuilder's append methods are not declared to throw
            // IOException, so this should never happen.
            throw new RuntimeException("StringBuilder should not throw IOException", ioe);
        }
    }

    public static void htmlize(char[] cs, int length, Appendable out)
            throws IOException {
        for (int i = 0; i < length && i < cs.length; i++) {
            htmlize(cs[i], out);
        }
    }

    /**
     * Append a character to a an <code>Appendable</code> object. If the
     * character has special meaning in HTML, append a sequence of characters
     * representing the special character.
     *
     * @param c the character to append
     * @param out the object to append the character to
     * @exception IOException if an I/O error occurs
     */
    private static void htmlize(char c, Appendable out) throws IOException {
        switch (c) {
            case '&':
                out.append("&amp;");
                break;
            case '>':
                out.append("&gt;");
                break;
            case '<':
                out.append("&lt;");
                break;
            case '\n':
                out.append("<br/>");
                break;
            default:
                out.append(c);
        }
    }

    private static String versionP=htmlize(org.opensolaris.opengrok.Info.getRevision());
    /**
     * used by BUI - CSS needs this parameter for proper cache refresh (per changeset) in client browser
     * @return html escaped version (hg changeset)
     */
    public static String versionParameter() {
        return versionP;
    }

    /**
     * Same as {@code breadcrumbPath(urlPrefix, l, '/')}.
     * @see #breadcrumbPath(String, String, char)
     */
    public static String breadcrumbPath(String urlPrefix, String l) {
        return breadcrumbPath(urlPrefix, l, '/');
    }

    private static final String anchorLinkStart = "<a href=\"";
    private static final String anchorClassStart = "<a class=\"";
    private static final String anchorEnd = "</a>";
    private static final String closeQuotedTag = "\">";

    /**
     * Same as {@code breadcrumbPath(urlPrefix, l, sep, "", false)}.
     * @see #breadcrumbPath(String, String, char, String, boolean)
     */
    public static String breadcrumbPath(String urlPrefix, String l, char sep) {
        return breadcrumbPath(urlPrefix, l, sep, "", false);
    }

    /**
     * Create a breadcrumb path to allow navigation to each element of a path.
     *
     * @param urlPrefix what comes before the path in the URL
     * @param l the full path from which the breadcrumb path is built
     * @param sep the character that separates the path elements in {@code l}
     * @param urlPostfix what comes after the path in the URL
     * @param compact if {@code true}, remove {@code ..} and empty path
     * elements from the path in the links
     * @return HTML markup for the breadcrumb path
     */
    public static String breadcrumbPath(
            String urlPrefix, String l, char sep, String urlPostfix,
            boolean compact) {
        if (l == null || l.length() <= 1) {
            return l;
        }
        StringBuilder hyperl = new StringBuilder(20);
        String[] path = l.split(escapeForRegex(sep), -1);
        for (int i = 0; i < path.length; i++) {
            leaveBreadcrumb(
                    urlPrefix, sep, urlPostfix, compact, hyperl, path, i);
        }
        return hyperl.toString();
    }

    /**
     * Leave a breadcrumb to allow navigation to one of the parent directories.
     * Write a hyperlink to the specified {@code StringBuilder}.
     *
     * @param urlPrefix what comes before the path in the URL
     * @param sep the character that separates path elements
     * @param urlPostfix what comes after the path in the URL
     * @param compact if {@code true}, remove {@code ..} and empty path
     * elements from the path in the link
     * @param hyperl a string builder to which the hyperlink is written
     * @param path all the elements of the full path
     * @param index which path element to create a link to
     */
    private static void leaveBreadcrumb(
            String urlPrefix, char sep, String urlPostfix, boolean compact,
            StringBuilder hyperl, String[] path, int index) {
        // Only generate the link if the path element is non-empty. Empty
        // path elements could occur if the path contains two consecutive
        // separator characters, or if the path begins or ends with a path
        // separator.
        if (path[index].length() > 0) {
            hyperl.append(anchorLinkStart).append(urlPrefix);
            appendPath(path, index, hyperl, compact);
            hyperl.append(urlPostfix).append(closeQuotedTag).
                    append(path[index]).append(anchorEnd);
        }
        // Add a separator between each path element, but not after the last
        // one. If the original path ended with a separator, the last element
        // of the path array is an empty string, which means that the final
        // separator will be printed.
        if (index < path.length - 1) {
            hyperl.append(sep);
        }
    }

    /**
     * Append parts of a file path to a {@code StringBuilder}. Separate each
     * element in the path with "/". The path elements from index 0 up to
     * index {@code lastIndex} (inclusive) are used.
     *
     * @param path array of path elements
     * @param lastIndex the index of the last path element to use
     * @param out the {@code StringBuilder} to which the path is appended
     * @param compact if {@code true}, remove {@code ..} and empty path
     * elements from the path in the link
     */
    private static void appendPath(
            String[] path, int lastIndex, StringBuilder out, boolean compact) {
        final ArrayList<String> elements = new ArrayList<String>(lastIndex + 1);

        // Copy the relevant part of the path. If compact is false, just
        // copy the lastIndex first elements. If compact is true, remove empty
        // path elements, and follow .. up to the parent directory. Occurrences
        // of .. at the beginning of the path will be removed.
        for (int i = 0; i <= lastIndex; i++) {
            if (compact) {
                if ("..".equals(path[i])) {
                    if (!elements.isEmpty()) {
                        elements.remove(elements.size() - 1);
                    }
                } else if (!"".equals(path[i])) {
                    elements.add(URIEncodePath(path[i]));
                }
            } else {
                elements.add(URIEncodePath(path[i]));
            }
        }

        // Print the path with / between each element. No separator before
        // the first element or after the last element.
        for (int i = 0; i < elements.size(); i++) {
            out.append(elements.get(i));
            if (i < elements.size() - 1) {
                out.append("/");
            }
        }
    }

    /**
     * Generate a regex that matches the specified character. Escape it in
     * case it is a character that has a special meaning in a regex.
     *
     * @param c the character that the regex should match
     * @return a six-character string on the form <tt>&#92;u</tt><i>hhhh</i>
     */
    private static String escapeForRegex(char c) {
        StringBuilder sb = new StringBuilder(6);
        sb.append("\\u");
        String hex = Integer.toHexString((int) c);
        for (int i = 0; i < 4 - hex.length(); i++) {
            sb.append('0');
        }
        sb.append(hex);
        return sb.toString();
    }

    public static String redableSize(long num) {
        float l = (float) num;
        NumberFormat formatter = new DecimalFormat("#,###,###,###.#");
        if (l < 1024) {
            return formatter.format(l);
        } else if (l < 1048576) {
            return (formatter.format(l / 1024) + "K");
        } else {
            return ("<b>" + formatter.format(l / 1048576) + "M</b>");
        }
    }

    /**
     * Converts different html special characters into their encodings used in html
     * currently used only for tooltips of annotation revision number view
     * @param s input text
     * @return encoded text for use in <a title=""> tag
     */
    public static String encode(String s) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            switch (c) {
                case '"':
                    sb.append('\'');
                    break; // \\\"
                case '&':
                    sb.append("&amp;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case ' ':
                    sb.append("&nbsp;");
                    break;
                case '\t':
                    sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
                    break;
                case '\n':
                    sb.append("<br/>");
                    break;
                case '\r':
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }

        return sb.toString();
    }

    public static void readableLine(int num, Writer out, Annotation annotation)
            throws IOException {
        String snum = String.valueOf(num);
        if (num > 1) {
            out.write("\n");
        }
        out.write(anchorClassStart);
        out.write((num % 10 == 0 ? "hl" : "l"));
        out.write("\" name=\"");
        out.write(snum);
        out.write("\" href=\"#");
        out.write(snum);
        out.write(closeQuotedTag);
        out.write((num > 999 ? "&nbsp;&nbsp;&nbsp;" : (num > 99 ? "&nbsp;&nbsp;&nbsp;&nbsp;" : (num > 9 ? "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" : "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"))));
        out.write(snum);
        out.write("&nbsp;");
        out.write(anchorEnd);
        if (annotation != null) {
            String r = annotation.getRevision(num);
            boolean enabled = annotation.isEnabled(num);

            out.write("<span class=\"blame\"><span class=\"l\"> ");
            for (int i = r.length(); i < annotation.getWidestRevision(); i++) {
                out.write(" ");
            }

            if (enabled) {
                out.write(anchorLinkStart);
                out.write(URIEncode(annotation.getFilename()));
                out.write("?a=true&amp;r=");
                out.write(URIEncode(r));
                String msg=annotation.getDesc(r);
                if (msg!=null) {
                 out.write("\" name=\"r\" title=\""+msg+"\"");
                }
                out.write(closeQuotedTag);
            }

            htmlize(r, out);

            if (enabled) {
                out.write(anchorEnd);
            }

            out.write(" </span>");

            String a = annotation.getAuthor(num);
            out.write("<span class=\"l\"> ");
            for (int i = a.length(); i < annotation.getWidestAuthor(); i++) {
                out.write(" ");
            }
            String link = RuntimeEnvironment.getInstance().getUserPage();
            String suffix = RuntimeEnvironment.getInstance().getUserPageSuffix();
            if (link != null && link.length() > 0) {
                out.write(anchorLinkStart);
                out.write(link);
                out.write(URIEncode(a));
                if (suffix != null && 0 < suffix.length()) {
                    out.write(suffix);
                }
                out.write(closeQuotedTag);
                htmlize(a, out);
                out.write(anchorEnd);
            } else {
                htmlize(a, out);
            }
            out.write(" </span></span>");
        }
    }

    /**
     * Append path and date into a string in such a way that lexicographic
     * sorting gives the same results as a walk of the file hierarchy.  Thus
     * null (\u0000) is used both to separate directory components and to
     * separate the path from the date.
     */
    public static String uid(String path, String date) {
        return path.replace('/', '\u0000') + "\u0000" + date;
    }

    public static String uid2url(String uid) {
        String url = uid.replace('\u0000', '/'); // replace nulls with slashes
        return url.substring(0, url.lastIndexOf('/')); // remove date from end
    }

    /**
     * wrapper arround UTF-8 URL encoding of a string
     * @param q query to be encoded
     * @return null if fail, otherwise the encoded string
     */
    public static String URIEncode(String q) {
        try {           
            return URLEncoder.encode(q, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // Should not happen. UTF-8 must be supported by JVMs.
            return null;
        }
    }

    public static String URIEncodePath(String path) {
        try {
           URI uri = new URI(null, null, path, null);
           return uri.getRawPath();
        } catch (URISyntaxException ex) {
            OpenGrokLogger.getLogger().log(Level.WARNING, "Could not encode path " + path, ex);
            return "";
        }
    }

    public static String formQuoteEscape(String q) {
        if (q == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        char c;
        for (int i = 0; i < q.length(); i++) {
            c = q.charAt(i);
            if (c == '"') {
                sb.append("&quot;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Highlight the diffs between line1 and line2
     * @param line1
     * @param line2
     * @return new strings with html tags highlighting the diffs
     */
    public static String[] diffline(String line1, String line2) {
    String[] ret = new String[2];
    int s=0;
    int m=line1.length()-1;
    int n=line2.length()-1;
    while (s <= m && s <= n && (line1.charAt(s) == line2.charAt(s)))
       { s++; }

    while (s <= m && s <= n && (line1.charAt(m) == line2.charAt(n)))
        { m--;n--; }

    StringBuilder sb = new StringBuilder(line1);
    String spand="<span class=\"d\">";
    if(s <= m) {
        sb.insert(s, spand);
        sb.insert(spand.length()+m+1, "</span>");
        ret[0] = sb.toString();
    } else {
        ret[0] = line1;
    }
    String spana="<span class=\"a\">";
    if(s <= n) {
        sb = new StringBuilder(line2);
        sb.insert(s, spana);
        sb.insert(spana.length()+n+1, "</span>");
        ret[1] = sb.toString();
    } else {
        ret[1] = line2;
    }
    return ret;

    }

    /**
     * Dump the configuration as an HTML table.
     *
     * @param out destination for the HTML output
     * @throws IOException if an error happens while writing to {@code out}
     * @throws HistoryException if the history guru cannot be accesses
     */
    public static void dumpConfiguration(Appendable out)
            throws IOException, HistoryException {
        out.append("<table border=\"1\" width=\"100%\">");
        out.append("<tr><th>Variable</th><th>Value</th></tr>");

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        printTableRow(out, "Source root", env.getSourceRootPath());
        printTableRow(out, "Data root", env.getDataRootPath());
        printTableRow(out, "CTags", env.getCtags());
        printTableRow(out, "Bug page", env.getBugPage());
        printTableRow(out, "Bug pattern", env.getBugPattern());
        printTableRow(out, "User page", env.getUserPage());
        printTableRow(out, "Review page", env.getReviewPage());
        printTableRow(out, "Review pattern", env.getReviewPattern());
        printTableRow(out, "Using projects", env.hasProjects());

        out.append("<tr><td>Ignored files</td><td>");
        printUnorderedList(out, env.getIgnoredNames().getItems());
        out.append("</td></tr>");

        printTableRow(out, "Index word limit", env.getIndexWordLimit());
        printTableRow(out, "Allow leading wildcard in search",
                      env.isAllowLeadingWildcard());
        printTableRow(out, "History cache",
                      HistoryGuru.getInstance().getCacheInfo());

        out.append("</table>");
    }

    /**
     * Print a row in an HTML table.
     *
     * @param out destination for the HTML output
     * @param cells the values to print in the cells of the row
     * @throws IOException if an error happens while writing to {@code out}
     */
    private static void printTableRow(Appendable out, Object... cells)
            throws IOException {
        out.append("<tr>");
        for (Object cell : cells) {
            out.append("<td>");
            String str = (cell == null) ? "null" : cell.toString();
            htmlize(str, out);
            out.append("</td>");
        }
        out.append("</tr>");
    }

    /**
     * Print an unordered list (HTML).
     *
     * @param out destination for the HTML output
     * @param items the list items
     * @throws IOException if an error happens while writing to {@code out}
     */
    private static void printUnorderedList(
            Appendable out, Collection<String> items) throws IOException {
        out.append("<ul>");
        for (String item : items) {
            out.append("<li>");
            htmlize(item, out);
            out.append("</li>");
        }
        out.append("</ul>");
    }

    /**
     * Create a string literal for use in JavaScript functions.
     * @param str the string to be represented by the literal
     * @return a JavaScript string literal
     */
    public static String jsStringLiteral(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
