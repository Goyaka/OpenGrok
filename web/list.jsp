<%--
CDDL HEADER START

The contents of this file are subject to the terms of the
Common Development and Distribution License (the "License").
You may not use this file except in compliance with the License.

See LICENSE.txt included in this distribution for the specific
language governing permissions and limitations under the License.

When distributing Covered Code, include this CDDL HEADER in each
file and include the License file at LICENSE.txt.
If applicable, add the following below this CDDL HEADER, with the
fields enclosed by brackets "[]" replaced with your own identifying
information: Portions Copyright [yyyy] [name of copyright owner]

CDDL HEADER END

Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
--%><%@ page import = "javax.servlet.*,
java.lang.*,
javax.servlet.http.*,
java.util.*,
java.io.*,
java.util.zip.GZIPInputStream,
java.util.logging.Level,
org.opensolaris.opengrok.OpenGrokLogger,
org.opensolaris.opengrok.analysis.*,
org.opensolaris.opengrok.configuration.Project,
org.opensolaris.opengrok.index.*,
org.opensolaris.opengrok.analysis.Definitions,
org.opensolaris.opengrok.analysis.FileAnalyzer.Genre,
org.opensolaris.opengrok.web.*,
org.opensolaris.opengrok.history.*
"
%><%@include file="mast.jsp"%>
<% if (!isDir) { %>
<script type="text/javascript">/* <![CDATA[ */
function lntoggle() {
   $("a").each(function() {
      if (this.className == 'l' || this.className == 'hl') {
         this.className=this.className+'-hide';
         this.setAttribute("tmp", this.innerHTML);
         this.innerHTML='';
      }
      else if (this.className == 'l-hide' || this.className == 'hl-hide') {
          this.innerHTML=this.getAttribute("tmp");
          this.className=this.className.substr(0,this.className.indexOf('-'));
      }
     }
    );
}

function get_sym_list_contents()
{
    var contents = "";

    //contents += "<input id=\"input_highlight\" name=\"input_highlight\" class=\"q\"/>";
    //contents += "&nbsp;&nbsp;";
    //contents += "<b><a href=\"#\" onclick=\"javascript:add_highlight();return false;\" title=\"Add highlight\">Highlight</a></b><br>";
    contents += "<a href=\"#\" onclick=\"javascript:lsttoggle();\">[Close]</a><br>"

    var symbol_classes = get_sym_list();
    for (var i = 0; i < symbol_classes.length; i++) {
        if (i > 0) {
            contents += "<br/>";
        }
        var symbol_class = symbol_classes[i];
        contents += "<b>" + symbol_class[0] + "</b><br/>";

        var class_name = symbol_class[1];

        var symbols = symbol_class[2];

        for (var j = 0; j < symbols.length; j++) {
            var symbol = symbols[j][0];
            var line = symbols[j][1];
            contents +=
                "<a href=\"#" + line + "\" class=\"" + class_name + "\">" +
                escape_html(symbol) + "</a><br/>";
        }
    }

    return contents;
}

function escape_html(string) {
    return string
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
}

// Initial value
document.sym_div_width = 240;
document.sym_div_height_max = 480;
document.sym_div_top = 100;
document.sym_div_left_margin = 40;
document.sym_div_height_margin = 40;

function get_sym_div_left()
{
    document.sym_div_left = $(window).width() - (document.sym_div_width + document.sym_div_left_margin);
    return document.sym_div_left;
}

function get_sym_div_height()
{
    document.sym_div_height = $(window).height() - document.sym_div_top - document.sym_div_height_margin;

    if (document.sym_div_height > document.sym_div_height_max)
        document.sym_div_height = document.sym_div_height_max;

    return document.sym_div_height;
}

function get_sym_div_top()
{
    return document.sym_div_top;
}

function get_sym_div_width()
{
    return document.sym_div_width;
}

function lsttoggle()
{
    if (document.sym_div == null)
    {
        document.sym_div = document.createElement("div");
        document.sym_div.id = "sym_div";

        document.sym_div.className = "sym_list_style";
        document.sym_div.style.margin = "0px auto";
        document.sym_div.style.width = get_sym_div_width() + "px";
        document.sym_div.style.height = get_sym_div_height() + "px";
        document.sym_div.style.top = get_sym_div_top() + "px";
        document.sym_div.style.left = get_sym_div_left() + "px";

        document.sym_div.innerHTML = get_sym_list_contents();

        document.body.appendChild(document.sym_div);
        document.sym_div_shown = 1;
    }
    else
    {
        if (document.sym_div_shown == 1)
        {
            document.sym_div.className = "sym_list_style_hide";
            document.sym_div_shown = 0;
        }
        else
        {
            document.sym_div.style.height = get_sym_div_height() + "px";
            document.sym_div.style.width = get_sym_div_width() + "px";
            document.sym_div.style.top = get_sym_div_top() + "px";
            document.sym_div.style.left = get_sym_div_left() + "px";
            document.sym_div.className = "sym_list_style";
            document.sym_div_shown = 1;
        }
    }
}

$(window).resize(
    function()
    {
        if (document.sym_div_shown == 1)
        {
            document.sym_div.style.left = get_sym_div_left() + "px";
            document.sym_div.style.height = get_sym_div_height() + "px";
        }
    }
);

// Highlighting
/*
// This will replace link's href contents as well, be careful
function HighlightKeywordsFullText(keywords)
{
    var el = $("body");

    $(keywords).each(
        function()
        {
            var pattern = new RegExp("("+this+")", ["gi"]);
            var rs = "<span style='background-color:#FFFF00;font-weight: bold;'>$1</span>";
            el.html(el.html().replace(pattern, rs)); 
        }
    );
}
//HighlightKeywordsFullText(["nfstcpsock"]);
*/

document.highlight_count = 0;
// This only changes matching tag's style
function HighlightKeyword(keyword)
{
    var high_colors=[
        "#ffff66",
        "#ffcccc",
        "#ccccff",
        "#99ff99",
        "#cc66ff"];

    var pattern = "a:contains('" + keyword + "')";
    $(pattern).css({
        'text-decoration' : 'underline',
        'background-color' : high_colors[document.highlight_count % high_colors.length],
        'font-weight' : 'bold'
    });

    document.highlight_count++;
}

//HighlightKeyword('timeval');

function add_highlight()
{
    var tbox = document.getElementById('input_highlight');
    HighlightKeyword(tbox.value);
}

/* ]]> */
</script>
<% } %>
<%
String rev = null;
if(!isDir && ef != null) {
    try {
        ef.close();
    } catch (IOException e) {
    }
    ef = null;
}

if (valid) {
    if (isDir) {

        // verify that the current path is part of the selected project
        Project activeProject = Project.getProject(resourceFile);
//TODO: somehow integrate below with projects.jspf
        if (activeProject != null) {
            final List<String> project = new ArrayList<String>();

            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
             for (Cookie cookie : cookies) {
                if (cookie.getName().equals("OpenGrokProject")) {
                    for (String proj : cookie.getValue().split(",")) {
                        if (proj != "") {
                            if (Project.getByDescription(proj) != null) {
                            project.add(proj);
                            }
                        }
                    }
                }
             }
            }

            boolean found = false;
            for (String proj : project) {
                if (activeProject.getDescription().equalsIgnoreCase(proj)) {
                    found = true;
                    break;
                }
            }
// set this in case there is no project selected or current cookie doesn't contain current project from the link, so the rest of search works 100% :)
            if (!found) {
             StringBuffer sproject=new StringBuffer(activeProject.getDescription()+",");
             if (project!=null) {
                //only save found projects into cookies
                for (Iterator it = project.iterator(); it.hasNext();) {
                  sproject.append((String)it.next()+",");
                }
             }
             // update the cookie
             Cookie cookie = new Cookie("OpenGrokProject", sproject.toString());
             cookie.setPath(context + "/");
             response.addCookie(cookie);
            }
        }

        // If requesting a Directory listing -------------
        DirectoryListing dl = new DirectoryListing(ef);
        String[] files = resourceFile.list();
        if (files != null) {
            Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);
            List<String> readMes = dl.listTo(resourceFile, out, path, files);
            if(readMes != null && readMes.size() > 0) {
                File xdir = new File(environment.getDataRootPath() + Constants.xrefP + path);
                if (xdir.exists() && xdir.isDirectory()) {
                    char[] buf = new char[8192];
                    for (String readme : readMes) {
                      File readmeFile = new File(xdir, readme + ".gz");
                      Reader br = null;
                      try {
                        if (environment.isCompressXref() && readmeFile.exists()) {
                          br = new InputStreamReader(new GZIPInputStream(new FileInputStream(readmeFile)));
                        } else {
                          readmeFile = new File(xdir, readme);
                          if (readmeFile.exists()) {
                            br = new FileReader(readmeFile);
                          }
                        }

                        if (br != null) {
                          int len = 0;
                          %><h3><%=readme%></h3><div id="src"><pre><%
                          while((len = br.read(buf)) > 0) {
                              out.write(buf, 0, len);
                          }
                          %></pre></div><%
                        }
                      } catch(IOException e) {
                        OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while reading/writing readme:", e);
                      } finally {
                        if (br != null) {
                          try {
                            br.close();
                          } catch (IOException e) {
                            OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while closing file:", e);
                          }
                        }
                      }
                    }
                }
            }
        }
    } else if ((rev = request.getParameter("r")) != null && !rev.equals("")) {
        // Else if requesting a previous revision -------------
        if (noHistory) {
            response.sendError(404, "Revision not found");
        } else {
            FileAnalyzerFactory a = AnalyzerGuru.find(basename);
            Genre g = AnalyzerGuru.getGenre(a);
            if (g == Genre.PLAIN|| g == Genre.HTML || g == null) {
                InputStream in = null;
                try {
                    in = HistoryGuru.getInstance().getRevision(resourceFile.getParent(), basename, rev);
                } catch (Exception e) {
                    response.sendError(404, "Revision not found");
                    return;
                }
                if (in != null) {
                    try {
                        if (g == null) {
                            a = AnalyzerGuru.find(in);
                            g = AnalyzerGuru.getGenre(a);
                        }
                        if (g == Genre.DATA || g == Genre.XREFABLE || g == null) {
		            %><div id="src">Binary file [Click <a href="<%=context%>/raw<%=path%>?r=<%=rev%>">here</a> to download] </div><%
                        } else {
		            %><div id="src"><span class="pagetitle"><%=basename%> revision <%=rev%> </span><pre><%
                            if (g == Genre.PLAIN) {
                                // We don't have any way to get definitions
                                // for old revisions currently.
                                Definitions defs = null;
                                Annotation annotation = annotate ? HistoryGuru.getInstance().annotate(resourceFile, rev) : null;
                                //annotation.writeTooltipMap(out); //not needed yet
                                Reader r = new InputStreamReader(in);
                                AnalyzerGuru.writeXref(a, r, out, defs, annotation, Project.getProject(resourceFile));
                            } else if (g == Genre.IMAGE) {
			       %><img src="<%=context+Constants.rawP+path%>?r=<%=rev%>"/><%
                            } else if (g == Genre.HTML) {
                               char[] buf = new char[8192];
                               Reader br = new InputStreamReader(in);
                               int len = 0;
                               while((len = br.read(buf)) > 0) {
                                   out.write(buf, 0, len);
                               }
                            } else {
		               %> Click <a href="<%=context+Constants.rawP+path%>?r=<%=rev%>">download <%=basename%></a><%
                            }
                        }
                    } catch (IOException e) {
                        %> <h3 class="error">IO Error</h3> <p> <%=e.getMessage() %> </p> <%
                    }
                    %></pre></div><%
                    in.close();
                } else {
    	            %> <h3 class="error">Error reading file</h3> <%
                }
            } else if(g == Genre.IMAGE) {
	        %><div id="src"><img src="<%=context+Constants.rawP+path%>?r=<%=rev%>"/></div><%
            } else {
                %><div id="src"> Binary file [Click <a href="<%=context+Constants.rawP+path%>?r=<%=rev%>">here</a> to download] </div><%
            }
        }
    } else {
        // requesting cross referenced file -------------
        File xrefSource = new File(environment.getDataRootFile(), Constants.xrefP);
        File xrefFile = new File(xrefSource, path + ".gz");
        Reader fileReader = null;

        if (environment.isCompressXref() ) {
            if  (xrefFile.exists()) {
            fileReader = new InputStreamReader(new GZIPInputStream(new FileInputStream(xrefFile)));
            }
        } else {            
            xrefFile = new File(xrefSource, path);
            if (xrefFile.exists()) {
                fileReader = new FileReader(xrefFile);
            }
        }

        if (fileReader != null && !annotate) {
            char[] buf = new char[8192];
            BufferedReader br = new BufferedReader(fileReader);
            int len = 0;
            %><div id="src"><pre><%
            while((len = br.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            %></pre></div><%
            br.close();
        } else {
            BufferedInputStream bin = new BufferedInputStream(new FileInputStream(resourceFile));
            FileAnalyzerFactory a = AnalyzerGuru.find(basename);
            Genre g = AnalyzerGuru.getGenre(a);
            if(g == null) {
                a = AnalyzerGuru.find(bin);
                g = AnalyzerGuru.getGenre(a);
            }
            if (g == Genre.IMAGE) {
        	%><div id="src"><img src="<%=context+Constants.rawP+path%>"/></div><%
            } else if( g == Genre.HTML) {
                char[] buf = new char[8192];
                Reader br = new InputStreamReader(bin);
                int len = 0;
                while((len = br.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } else if(g == Genre.PLAIN) {
                %><div id="src"><pre><%
                // We're generating xref for the latest revision, so we can
                // find the definitions in the index.
                Definitions defs = IndexDatabase.getDefinitions(resourceFile);
                Annotation annotation = annotate ? HistoryGuru.getInstance().annotate(resourceFile, rev) : null;                
                Reader r = new InputStreamReader(bin);
                AnalyzerGuru.writeXref(a, r, out, defs, annotation, Project.getProject(resourceFile));
                %></pre></div><%
            } else {
	        %> Click <a href="<%=context+Constants.rawP+path%>">download <%=basename%></a><%
            }
        }
    }
    %><%@include file="foot.jspf"%><%
}
if (ef != null) {
    try {
        ef.close();
    } catch (IOException e) {
    }
}
%>
