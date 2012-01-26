package com.goyaka.opengrok.web;

import org.apache.lucene.search.ScoreDoc;

public class SearchResult {
    // Search hit obtained for this document
    public ScoreDoc hit;
    
    // Absolute path of the document
    public String path;
    
    // BaseName of the document - rather the file name
    public String baseName;

    // Parent Document
    public String parent;
    
    // Parent Tag
    public String tag = "";
    
    // Parent Tag link
    public String tagLink;
    
    // Link to the view content
    public String link;
    
    // Link to the raw content to download
    public String rawLink;
    
    // Link to the history view of the file - shows the commits made to the file
    public String historyLink;
    
    // Link to the annotated view of the file
    public String blameLink;
    
    // Context summary for the document
    public String summary;
    
    // History context for the document if any
    public String history = null;
    
}
