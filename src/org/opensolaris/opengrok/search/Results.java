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

package org.opensolaris.opengrok.search;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Searcher;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.TagFilter;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryException;
import org.opensolaris.opengrok.search.context.Context;
import org.opensolaris.opengrok.search.context.HistoryContext;
import org.opensolaris.opengrok.web.Constants;
import org.opensolaris.opengrok.web.EftarFileReader;
import org.opensolaris.opengrok.web.Util;

/**
 *
 * @author Chandan
 * slightly rewritten by Lubos Kosco
 */
public final class Results {
    
    private Results() {
        // Util class, should not be constructed
    }
    
    /**
     * Prints out results in html form
     * @param searcher
     * @param hits
     * @param start
     * @param end
     * @param out
     * @param sourceContext
     * @param historyContext
     * @param summer
     * @param context url context (webapp link/name)
     * @param srcRoot
     * @param dataRoot
     * @param desc
     * @throws HistoryException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static void prettyPrintHTML(Searcher searcher,ScoreDoc[] hits, int start, int end, Writer out,
            Context sourceContext, HistoryContext historyContext,
            Summarizer summer, String context,
            String srcRoot,
            String dataRoot,
            EftarFileReader desc)
            throws HistoryException, IOException, ClassNotFoundException
    {
        char[] content = new char[1024*8];
        String xrefPrefix=context+Constants.xrefP;
        String morePrefix=context+Constants.moreP;
        LinkedHashMap<String, ArrayList<Document>> dirHash = new LinkedHashMap<String, ArrayList<Document>>();
        for (int i = start; i < end; i++) {
            int docId = hits[i].doc;            
            Document doc = searcher.doc(docId);
            String rpath = doc.get("path");
            String parent = rpath.substring(0,rpath.lastIndexOf('/'));
            ArrayList<Document> dirDocs = dirHash.get(parent);
            if(dirDocs == null) {
                dirDocs = new ArrayList<Document>();
                dirHash.put(parent, dirDocs);
            }
            dirDocs.add(doc);
        }
        
        for (Map.Entry<String, ArrayList<Document>> entry: dirHash.entrySet()) {
            String parent = entry.getKey();
            String tag = (desc == null) ? "" : " - <i>" + desc.get(parent) + "</i>";

            out.write("<tr class=\"dir\"><td colspan=\"3\">&nbsp;&nbsp;<a href=\"");
            out.write(Util.URIEncodePath(xrefPrefix + parent));
            out.write("/\">" + parent + "/</a>" + tag + "</td></tr>");

            boolean alt = false;
            for (Document doc: entry.getValue()) {
                String rpath = doc.get("path");
                String self = rpath.substring(rpath.lastIndexOf('/')+1, rpath.length());
                String selfUrl = Util.URIEncodePath(xrefPrefix + rpath);
                out.write("<tr ");
                if(alt) {
                    out.write(" class=\"alt\"");
                }
                alt ^= true;
                out.write(">");
                out.write("<td class=\"q\"><a href=\""+context+Constants.histL+rpath+"\" title=\"History\">H</a> <a href=\""+context+Constants.xrefP+rpath+"?a=true\" title=\"Annotate\">A</a> <a href=\""+context+Constants.rawP+rpath+"\" title=\"Download\">D</a>");
                out.write("</td>");
                out.write("<td class=\"f\"><a href=\"" +
                        selfUrl + "\">"+self+"</a>&nbsp;</td><td><tt class=\"con\">");
                if (sourceContext != null) {
                    String genre = doc.get("t");
                    Definitions tags = null;
                    Fieldable tagsField = doc.getFieldable("tags");
                    if (tagsField != null) {
                        tags = Definitions.deserialize(tagsField.getBinaryValue());
                    }
                    try {
                        if ("p".equals(genre) && srcRoot != null) {
                            sourceContext.getContext(new FileReader(srcRoot + rpath), out, xrefPrefix, morePrefix, rpath,
                                    tags, true, null);
                        } else if("x".equals(genre) && dataRoot != null && summer != null){
                            Reader r = null;
                            if ( RuntimeEnvironment.getInstance().isCompressXref() ) {
                                    r = new TagFilter(new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(dataRoot + Constants.xrefP + rpath+".gz"))))); }
                            else {
                                    r = new TagFilter(new BufferedReader(new FileReader(dataRoot + Constants.xrefP + rpath))); }
                            int len = r.read(content);
                            //FIXME use Highlighter from lucene contrib here, instead of summarizer, we'd also get rid of apache lucene in whole source ...
                            out.write(summer.getSummary(new String(content, 0, len)).toString());
                            r.close();
                        } else if("h".equals(genre) && srcRoot != null && summer != null){
                            Reader r = new TagFilter(new BufferedReader(new FileReader(srcRoot + rpath)));
                            int len = r.read(content);
                            out.write(summer.getSummary(new String(content, 0, len)).toString());
                            r.close();
                        } else {
                            sourceContext.getContext(null, out, xrefPrefix, morePrefix, rpath, tags, true, null);
                        }
                    } catch (IOException e) {
                        OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while creating summary of "+rpath, e);
                    }
                    //out.write("Genre = " + genre);
                }
                if(historyContext != null) {
                    historyContext.getContext(srcRoot + parent, self, rpath, out,context);
                }
                out.write("</tt></td></tr>\n");
            }
        }
    }
}
