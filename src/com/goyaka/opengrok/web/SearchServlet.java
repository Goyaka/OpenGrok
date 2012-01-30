package com.goyaka.opengrok.web;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.opensolaris.opengrok.search.*;
import org.opensolaris.opengrok.search.context.Context;
import org.opensolaris.opengrok.search.context.HistoryContext;
import org.opensolaris.opengrok.web.EftarFileReader;
import org.opensolaris.opengrok.analysis.CompatibleAnalyser;
import org.opensolaris.opengrok.configuration.*;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.search.*;
import org.apache.lucene.queryParser.*;

import com.google.gson.Gson;

public class SearchServlet extends HttpServlet {
    final String LASTMODTIME = "lastmodtime";
    final String RELEVANCY = "relevancy";
    final String BY_PATH = "fullpath";
    final SortField S_BY_PATH = new SortField(BY_PATH, SortField.STRING);

    final boolean docsScoredInOrder = true;

    String sortOrder = null;

    HttpServletRequest request = null;
    HttpServletResponse response = null;

    RuntimeEnvironment env = RuntimeEnvironment.getInstance();
    ArrayList<String> requested_projects = null;

    Searcher searcher = null;
    Query query = null;
    QueryBuilder queryBuilder = null;
    ScoreDoc[] hits = null;
    TopScoreDocCollector collector = null;
    Sort sortField = null;
    Context sourceContext = null;
    HistoryContext historyContext = null;
    Summarizer summer = null;
    EftarFileReader ef = null;
     
    int cachedPages = RuntimeEnvironment.getInstance().getCachePages();
    int maxResultsPerPage = RuntimeEnvironment.getInstance().getHitsPerPage();
    int resultsPerPage = maxResultsPerPage;
    int start = 0;
    
    private void raceConditionsCheck() throws Exception {
        String DATA_ROOT = env.getDataRootPath();
        if (DATA_ROOT.equals("")) {
            throw new Exception("DATA_ROOT parameter is not configured in web.xml!");
        }

        File data_root = new File(DATA_ROOT);
        if (!data_root.isDirectory()) {
            throw new Exception("DATA_ROOT parameter in web.xml does not exist or is not a directory!");
        }
    }

    private void setSearcher() throws IOException, CorruptIndexException {
        File root = new File(RuntimeEnvironment.getInstance().getDataRootFile(), "index");

        if (RuntimeEnvironment.getInstance().hasProjects()) {
            if (requested_projects.size() > 1) {
                IndexSearcher[] searchables = new IndexSearcher[requested_projects.size()];
                File droot = new File(RuntimeEnvironment.getInstance().getDataRootFile(), "index");
                int ii = 0;

                for (String proj : requested_projects) {
                    FSDirectory dir = FSDirectory.open(new File(droot, proj));
                    searchables[ii++] = new IndexSearcher(dir);
                }
                if (Runtime.getRuntime().availableProcessors() > 1) {
                    searcher = new ParallelMultiSearcher(searchables);
                } else {
                    searcher = new MultiSearcher(searchables);
                }
            } else { // just 1 project selected
                root = new File(root, requested_projects.get(0));
                FSDirectory dir = FSDirectory.open(root);
                searcher = new IndexSearcher(dir);
            }
        } else { // no project setup
            FSDirectory dir = FSDirectory.open(root);
            searcher = new IndexSearcher(dir);
        }
    }

    private void setProjectsToSearch() {
        requested_projects = new ArrayList<String>();
        List<Project> available_projects = env.getProjects();
        String pr[] = request.getParameterValues("project");
        if (pr != null && pr[0].length() > 0) {
            for (int midx = 0; midx < pr.length; midx++) {
                String p[] = pr[midx].split(",");
                for (int idx = 0; idx < p.length; idx++) {
                    if (Project.getByDescription(p[idx]) != null) {
                        requested_projects.add(p[idx]);
                    }
                }
            }
        } else {
            for (Project project : available_projects) {
                requested_projects.add(project.getDescription());
            }
        }
    }

    private void setSortOrder() {
        String sortParam = request.getParameter("sort");
        if (sortParam != null) {
            if (LASTMODTIME.equals(sortParam)) {
                sortOrder = LASTMODTIME;
                sortField = new Sort(new SortField("date", SortField.STRING, true));
            } else if (RELEVANCY.equals(sortParam)) {
                sortOrder = RELEVANCY;
            } else if (BY_PATH.equals(sortParam)) {
                sortOrder = BY_PATH;
                sortField = new Sort(S_BY_PATH);
            }
        } else {
            sortOrder = RELEVANCY;
        }
    }

    private void runSearch() throws IOException {
        int totalHits = 0;
        int start = 0;

        boolean allCollected = false;

        // Get the results first within hits
        collector = TopScoreDocCollector.create(resultsPerPage * cachedPages, docsScoredInOrder);
        if (RELEVANCY.equals(sortOrder)) {
            searcher.search(query, collector);
            totalHits = collector.getTotalHits();
            if (start >= resultsPerPage * cachedPages && !allCollected) {
                collector = TopScoreDocCollector.create(totalHits, docsScoredInOrder);
                searcher.search(query, collector);
                allCollected = true;
            }
            hits = collector.topDocs().scoreDocs;
        } else {
            TopFieldDocs fdocs = searcher.search(query, null, resultsPerPage * cachedPages, sortField);
            totalHits = fdocs.totalHits;
            if (start >= resultsPerPage * cachedPages && !allCollected) {
                fdocs = searcher.search(query, null, totalHits, sortField);
                allCollected = true;
            }
            hits = fdocs.scoreDocs;
        }
    }

    private List<SearchResult> constructSummary(ScoreDoc[] hits) {
        List<SearchResult> results = null;
        // Get the search summary to show
        if (query != null) {
            try {
                sourceContext = new Context(query, queryBuilder.getQueries());
                if (sourceContext != null) {
                    summer = new Summarizer(query, new CompatibleAnalyser());
                }
                
                historyContext = new HistoryContext(query);
                ef = new EftarFileReader(env.getDataRootPath() + "/index/dtags.eftar");
    
            } catch (Exception e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
            }
            
            try { 
                results = Results.getResultsData(searcher, hits, sourceContext, historyContext, summer, request.getContextPath(), env.getSourceRootPath(), env.getDataRootPath(), ef);
            } catch (Exception e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
            } 
        }
        return results;
    }

    private void renderJSONResponse() throws IOException {
        Gson json = new Gson();
        PrintWriter out = response.getWriter();
        response.setContentType("application/json");
        Map<String, Object> response_object = new HashMap<String, Object>();
        
        int end;
        ScoreDoc[] hitsThisPage;
        List<SearchResult> results = null;
        
        try {
            end = Math.min(start + resultsPerPage, hits.length);
            hitsThisPage = Arrays.copyOfRange(hits, start, end);
            results = constructSummary(hitsThisPage);
        } catch (NullPointerException e) {
            // hits could be null, not a problem. Or query could be null, which means hits still could be null
            end = 0;
            results = null;
        } finally {
            response_object.put("sortOrder", sortOrder);
            
            if(query == null || query.equals("")) { 
                response_object.put("query", "");    
            }
            else {
                response_object.put("query", query.toString());
            }
            if (results == null) {
                response_object.put("hits", new ArrayList<SearchResult>());
            } else {
                response_object.put("hits", results);
            }

            out.print(json.toJson(response_object));
        }        
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String errorMsg = null;
        try {
            // Set the request object
            request = req;
            response = resp;

            // Not sure why I am doing this.
            String context = request.getContextPath();
            env.setUrlPrefix(context + "/s?");
            env.register();

            // Initialize the list of requested projects to search from
            requested_projects = new ArrayList<String>();

            // Check if the environment has projects defined for this project
            if (env.hasProjects()) {

                // Set which all projects to be searched
                setProjectsToSearch();

                // Find out the requested sorting order of results
                setSortOrder();

                // Get the relevant search parameters
                String q = request.getParameter("q");
                String defs = request.getParameter("defs");
                String refs = request.getParameter("refs");
                String hist = request.getParameter("hist");
                String path = request.getParameter("path");

                // Initialize appropriately
                if (q != null && q.equals(""))
                    q = null;
                if (defs != null && defs.equals(""))
                    defs = null;
                if (refs != null && refs.equals(""))
                    refs = null;
                if (hist != null && hist.equals(""))
                    hist = null;
                if (path != null && path.equals(""))
                    path = null;

                // Set the starting offset of results
                String _requestStart = request.getParameter("start");
                if (_requestStart != null && !_requestStart.equals("")) {
                    start = Integer.parseInt(_requestStart);
                }

                // Set the number of results to be shown in this page
                String _requestResultsPerPage = request.getParameter("n");
                if (_requestResultsPerPage != null && !_requestResultsPerPage.equals("")) {
                    resultsPerPage = Integer.parseInt(_requestResultsPerPage);
                }

                // Set query to null. Lets construct the query from scratch.
                query = null;
                
                if (q != null || defs != null || refs != null || hist != null || path != null) {
                    // Check if configuration is properly available
                    raceConditionsCheck();

                    // Build the query
                    queryBuilder = new QueryBuilder().setFreetext(q).setDefs(defs).setRefs(refs).setPath(path).setHist(hist);
                    query = queryBuilder.build();

                    // Find and set the appropriate Searcher class for this
                    // query
                    setSearcher();

                    // Run the search
                    Date startTime = new Date();
                    runSearch();
                    Date endTime = new Date();
                }

                // All searching has been done, render the response
                renderJSONResponse();
            }
        } catch (BooleanQuery.TooManyClauses e) {
            e.printStackTrace();
            errorMsg = "Too many results for wildcard!";
        } catch (ParseException e) {
            e.printStackTrace();
            errorMsg = "Error parsing your query: You might try to enclose your search term in quotes\n" + "Error message from parser:<br/>" + e.getMessage();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            errorMsg = "Error: Index database not found";
        } catch (Exception e) {
            e.printStackTrace();
            errorMsg = "Error: " + e.getMessage();
        } finally {
            if (errorMsg != null) {
                System.err.println(errorMsg);
            }
        }
    }
}
