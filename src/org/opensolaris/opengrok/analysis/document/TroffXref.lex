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

package org.opensolaris.opengrok.analysis.document;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class TroffXref
%extends JFlexXref
%unicode
%int
%{ 
  boolean p = false;

  @Override
  public void write(Writer out) throws IOException {
        yyline++;
        this.out = out;
        while(yylex() != YYEOF) {
        }
  }

  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
%}

WhiteSpace     = [ \t\f]
EOL = \r|\n|\r\n
FNameChar = [a-zA-Z0-9_\-\.]
File = {FNameChar}+ "." ([chtsCHS]|"conf"|"java"|"cpp"|"CC"|"txt"|"htm"|"html"|"pl"|"xml")
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*)+[a-zA-Z0-9]

%state HEADER COMMENT BOLD TBL TBLL

%%
<YYINITIAL> {
^\.(SH|TH|SS|IP|NH|TL|UH)       { yybegin(HEADER);out.write("<div class=\"b\">");}
^(".\\\"")|(\'\\\")|("...\\\"") { yybegin(COMMENT);out.write("<span class=\"c\">");}
}

<HEADER> {
{EOL}   { yybegin(YYINITIAL);out.write("</div>"); yyline++;}
}

<COMMENT> {
{EOL}   { yybegin(YYINITIAL);out.write("</span><br>"); yyline++;}
}

^\.(B|U|BI|BX|UL|LG|NL|SB|BR|RB) { yybegin(BOLD); out.write("<span class=\"b\">"); }
^\.(I|SM|IB|IR|RI|IX) { yybegin(BOLD); out.write("<span class=\"s\">"); }
^\.(CW) { yybegin(BOLD); out.write("<span class=\"k\">"); }
^\.(DS|LD|ID|BD|CD|RD) { out.write("<span class=\"k\">"); }
^\.DE   { out.write("</span>"); }

<BOLD> {
{EOL}      { yybegin(YYINITIAL);out.write("</span> "); yyline++;}
}

"\\fB"  { out.write("<span class=\"b\">"); }
"\\fI"  { out.write("<span class=\"s\">"); }
"\\fC"|"\\f(CW" { out.write("<span class=\"k\">"); }
"\\fR"  { out.write("</span>"); }
"\\fP"  { out.write("</span>"); }

^\.(PP|LP|P|TP|IP|HP|PD|SP|br|mk) { 
    if(p)// TODO isn't this buggy ?
        out.write("</p>");
    out.write("<p>");
    p = true;
}

^\.(RS|RE)[^\n]* { out.write("\n"); }

^\.so {out.write(".so ");}
^\.(EQ|in|sp|ne|rt|br|pn|ds|de|if|ig|el|ft|hy|ie|ll|ps|rm|ta|ti)[^\n]*\n {}
^\.(NH|DT|EE)[^\n]* {}
^"\\(bu\n" {}
^".nf"  {out.write("<pre>"); }
^".fi"  {out.write("</pre>"); }
\\\*\(Tm { out.write(" TM "); }
\\\*\R { out.write(" (R) "); }
\\\((l|r)q { out.write('"'); }
\\\(mi { out.write('-'); }

^\.TS   {yybegin(TBL);out.write("<table border=\"1\" cellpadding=\"2\" rules=\"all\" bgcolor=\"#ddddcc\"><tr><td>");}
<TBL> {
tab\(.\) { char tab = yycharat(4); }
\.$    { yybegin(TBLL); }
.    {}
}
<TBLL> {
\007    { out.write("</td><td>"); }
^[\_\=]\n    {}
T[\{\}] {}
^\.TE   { yybegin(YYINITIAL); out.write("</td></tr></table>"); }
{EOL}       { out.write("</td></tr><tr><td>"); yyline++;}
}

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }

{File} {
        String path = yytext();
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");}

{Path}
        { out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/'));}
\\&.    {out.write(yycharat(yylength() - 1));}
\\-     { out.write('-'); }
"\\ "   { out.write(' '); }
"<"     {out.write( "&lt;");}
">"     {out.write( "&gt;");}
{EOL}   { out.write("\n"); yyline++;}
{WhiteSpace}+   { out.write(' '); }
[!-~]   { out.write(yycharat(0)); }
 .      { writeUnicodeChar(yycharat(0)); }
