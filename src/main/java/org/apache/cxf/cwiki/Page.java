/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.cwiki;


import java.io.Serializable;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.helpers.DOMUtils;
import org.ccil.cowan.tagsoup.Parser;

/**
 * 
 */
public class Page extends AbstractPage implements Serializable {
    

    private static final long serialVersionUID = 1L;
    private static final Map<String, String> CODE_TYPE_MAP = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
    static {
        CODE_TYPE_MAP.put("applescript", "shBrushAppleScript.js");
        CODE_TYPE_MAP.put("actionscript3", "shBrushAS3.js");
        CODE_TYPE_MAP.put("as3", "shBrushAS3.js");
        CODE_TYPE_MAP.put("bash", "shBrushBash.js");
        CODE_TYPE_MAP.put("shell", "shBrushBash.js");
        CODE_TYPE_MAP.put("coldfusion", "shBrushColdFusion.js");
        CODE_TYPE_MAP.put("cpp", "shBrushCpp.js");
        CODE_TYPE_MAP.put("c", "shBrushCpp.js");
        CODE_TYPE_MAP.put("c#", "shBrushCSharp.js");
        CODE_TYPE_MAP.put("c-sharp", "shBrushCSharp.js");
        CODE_TYPE_MAP.put("csharp", "shBrushCSharp.js");
        CODE_TYPE_MAP.put("css", "shBrushCss.js");
        CODE_TYPE_MAP.put("delphi", "shBrushDelphi.js");
        CODE_TYPE_MAP.put("pascal", "shBrushDelphi.js");
        CODE_TYPE_MAP.put("diff", "shBrushDiff.js");
        CODE_TYPE_MAP.put("patch", "shBrushDiff.js");
        CODE_TYPE_MAP.put("pas", "shBrushDiff.js");
        CODE_TYPE_MAP.put("erl", "shBrushErlang.js");
        CODE_TYPE_MAP.put("erlang", "shBrushErlang.js");
        CODE_TYPE_MAP.put("groovy", "shBrushGroovy.js");
        CODE_TYPE_MAP.put("java", "shBrushJava.js");
        CODE_TYPE_MAP.put("jfx", "shBrushJavaFX.js");
        CODE_TYPE_MAP.put("javafx", "shBrushJavaFX.js");
        CODE_TYPE_MAP.put("js", "shBrushJScript.js");
        CODE_TYPE_MAP.put("jscript", "shBrushJScript.js");
        CODE_TYPE_MAP.put("javascript", "shBrushJScript.js");
        CODE_TYPE_MAP.put("perl", "shBrushPerl.js");
        CODE_TYPE_MAP.put("pl", "shBrushPerl.js");
        CODE_TYPE_MAP.put("php", "shBrushPhp.js");
        CODE_TYPE_MAP.put("text", "shBrushPlain.js");
        CODE_TYPE_MAP.put("plain", "shBrushPlain.js");
        CODE_TYPE_MAP.put("none", "shBrushPlain.js");
        CODE_TYPE_MAP.put("py", "shBrushPython.js");
        CODE_TYPE_MAP.put("python", "shBrushPython.js");
        CODE_TYPE_MAP.put("powershell", "shBrushPowerShell.js");
        CODE_TYPE_MAP.put("ps", "shBrushPowerShell.js");
        CODE_TYPE_MAP.put("posh", "shBrushPowerShell.js");
        CODE_TYPE_MAP.put("ruby", "shBrushRuby.js");
        CODE_TYPE_MAP.put("rails", "shBrushRuby.js");
        CODE_TYPE_MAP.put("ror", "shBrushRuby.js");
        CODE_TYPE_MAP.put("rb", "shBrushRuby.js");
        CODE_TYPE_MAP.put("sass", "shBrushSass.js");
        CODE_TYPE_MAP.put("scss", "shBrushSass.js");
        CODE_TYPE_MAP.put("scala", "shBrushScala.js");
        CODE_TYPE_MAP.put("sql", "shBrushSql.js");
        CODE_TYPE_MAP.put("vb", "shBrushVb.js");
        CODE_TYPE_MAP.put("vbnet", "shBrushVb.js");
        CODE_TYPE_MAP.put("xml", "shBrushXml.js");
        CODE_TYPE_MAP.put("xhtml", "shBrushXml.js");
        CODE_TYPE_MAP.put("xslt", "shBrushXml.js");
        CODE_TYPE_MAP.put("html", "shBrushXml.js");
        CODE_TYPE_MAP.put("html/xml", "shBrushXml.js");
    }
    
    final XMLGregorianCalendar modified;
    final String parent;
    final String spaceKey;
    Map<String, String> attachments;
    Set<String> includes;
    Map<String, Integer> childrenOf;
    boolean hasBlog;
    Set<String> codeTypes;
    
    transient String renderedContent;
    transient String renderedDivContent;
    transient String divIdForContent;
    
    transient SiteExporter exporter;

    public Page(Document doc, SiteExporter exp) throws Exception {
        this(DOMUtils.getFirstElement(doc.getDocumentElement()), exp);
    }
    
    public Page(Element root, SiteExporter exp) throws Exception {
        super(root);
        exporter = exp;
        //org.apache.cxf.helpers.XMLUtils.printDOM(doc.getDocumentElement());

        parent = DOMUtils.getChildContent(root, "parentId");
        spaceKey = DOMUtils.getChildContent(root, "space");

        String mod = DOMUtils.getChildContent(root, "modified");
        modified = DatatypeFactory.newInstance().newXMLGregorianCalendar(mod);
        modified.setMillisecond(0);
        
        String c = DOMUtils.getChildContent(root, "content");
        if (c != null) {
            if (exp.getAPIVersion() == 2) {
                checkContentV2(c);
            } else {
                checkContentV1(c);                
            }
        }
    }
    /*
     * Makes a shallow copy without any content
     */
    public Page(Page source) {
        super(source);
        this.modified = source.modified;
        this.parent = source.parent;
        this.spaceKey = source.spaceKey;
        this.attachments = source.attachments;
        this.includes = source.includes;
        this.childrenOf = source.childrenOf;
        this.exporter = source.exporter;
        this.hasBlog = source.hasBlog;
        this.codeTypes = source.codeTypes;
    }
    
    private void checkContentV2(final String c) {
        try {
            //if ("Content Based Router".equals(title)) {
            //    System.out.println(c);
            //}
            
            XMLReader reader = new Parser();
            reader.setFeature(Parser.namespacesFeature, true);
            reader.setFeature(Parser.namespacePrefixesFeature, true);
            reader.setProperty(Parser.schemaProperty, new org.ccil.cowan.tagsoup.HTMLSchema() {
                {
                    //problem with nested lists that the confluence {toc} macro creates
                    elementType("ul", M_LI, M_BLOCK | M_LI, 0);
                }
            });
            reader.setContentHandler(new V2ContentHandler(this));
            reader.parse(new InputSource(new StringReader(c)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkContentV1(String c) {
        int idx = c.indexOf("{children");
        while (idx != -1) {
            if (childrenOf == null) {
                childrenOf = new HashMap<String, Integer>();
            }
            idx += 9;
            if (c.charAt(idx) != '}') {
                // {children:page=Foo|...}
                idx++;
                int idx2 = c.indexOf('}', idx);
                String paramString = c.substring(idx, idx2);
                String params[] = paramString.split("\\||=");
                String page = null;
                int depth = 1;
                for (int x = 0; x < params.length; x++) {
                    if ("page".equals(params[x])) {
                        page = params[x + 1];
                        x++;
                    } else if ("depth".equals(params[x])) {
                        depth = Integer.parseInt(params[x + 1]);
                        x++;
                    }
                }
                childrenOf.put(page, depth);
            } else {
                childrenOf.put(title, 1);
            }
            idx = c.indexOf("{children", idx);
        }
        
        idx = c.indexOf("{include:");
        while (idx != -1) {
            int idx2 = c.indexOf("}", idx);
            String inc = c.substring(idx + 9, idx2);
            if (includes == null) {
                includes = new CopyOnWriteArraySet<String>();
            }
            includes.add(inc);
            idx = c.indexOf("{include:", idx2);
        }
        idx = c.indexOf("{blog-posts");
        if (idx != -1) {
            hasBlog = true;
        }
        handleCode(c);
    }

    private void handleCode(String c) {
        int idx = c.indexOf("{code");
        while (idx != -1) {
            String type = "java";
            idx += 5;
            if (c.charAt(idx) != '}') {
                idx++;
                int idx2 = c.indexOf('}', idx);
                if (idx2 != -1) {
                    String paramString = c.substring(idx, idx2);
                    String params[] = paramString.split("\\||=");
                    for (int x = 0; x < params.length; x++) {
                        if ("type".equalsIgnoreCase(params[x])) {
                            type = params[x + 1];
                            x++;
                        } else if (CODE_TYPE_MAP.containsKey(params[x].toLowerCase())) {
                            type = params[x];
                        }
                    }
                }
            }

            if (codeTypes == null) {
                codeTypes = new ConcurrentSkipListSet<String>();
            }
            codeTypes.add(type);
            idx = c.indexOf("{code", idx + 1);
        } 
        idx = c.indexOf("{snippet");
        while (idx != -1) {
            String type = "java";
            idx += 8;
            if (c.charAt(idx) != '}') {
                idx++;
                int idx2 = c.indexOf('}', idx);
                if (idx2 != -1) {
                    String paramString = c.substring(idx, idx2);
                    String params[] = paramString.split("\\||=");
                    for (int x = 0; x < params.length; x++) {
                        if ("lang".equalsIgnoreCase(params[x])) {
                            type = params[x + 1];
                            x++;
                        } else if (CODE_TYPE_MAP.containsKey(params[x].toLowerCase())) {
                            type = params[x];
                        }
                    }
                }
            }

            if (codeTypes == null) {
                codeTypes = new ConcurrentSkipListSet<String>();
            }
            codeTypes.add(type);
            idx = c.indexOf("{snippet", idx + 1);
        } 
    }
    
    public boolean hasChildrenOf(String t, int d) {
        if (childrenOf == null) {
            return false;
        }
        Integer i = childrenOf.get(t);
        if (i == null) {
            return false;
        }
        return d <= i;
    }
    
    public boolean includesPage(String s) {
        if (includes == null) {
            return false;
        }
        return includes.contains(s);
    }
    

    public String getParentId() {
        return parent;
    }

    public XMLGregorianCalendar getModifiedTime() {
        return modified;
    }

    public void setContent(String c) {
        renderedContent = c;
    }
    
    public String getContent() {
        return renderedContent;
    }

    public String getSpaceKey() {
        return spaceKey;
    }

    public void addAttachment(String aid, String filename) {
        if (attachments == null) {
            attachments = new HashMap<String, String>();
        }
        attachments.put(aid, filename);
    }
    public String getAttachmentFilename(String aid) {
        if (attachments == null) {
            return null;
        }
        return attachments.get(aid);
    }

    public void setContentForDivId(String divId, String content) {
        renderedDivContent = content;
        divIdForContent = divId;
    }

    public String getContentForDivId(String divId) {
        if (divId == null) {
            return renderedContent;
        }
        if (divId.equals(divIdForContent)) {
            return renderedDivContent;
        }
        return null;
    }

    public String getLink() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("<a href=\"");
        buffer.append(url);
        buffer.append("\" title=\"");
        buffer.append(title);
        buffer.append("\">");
        buffer.append(title);
        buffer.append("</a>");
        return buffer.toString();
    }

    public Space getSpace() {
        return SiteExporter.getSpace(spaceKey);
    }

    public boolean hasChildren() {
        return exporter.hasChildren(this);
    }

    public List<Page> getChildren() {
        return exporter.getChildren(this);
    }

    protected void setExporter(SiteExporter exporter) {
        this.exporter = exporter;
    }
    
    protected SiteExporter getExporter() {
        return exporter;
    }

    public boolean hasBlog() {
        return hasBlog;
    }
    
    public boolean getHasCode() {
        return hasCode(new HashMap<String, Boolean>());
    }

    public boolean hasCode(Map<String, Boolean> done) {
        if (done.containsKey(getTitle())) {
            return done.get(getTitle());
        }
        if (codeTypes != null && !codeTypes.isEmpty()) {
            done.put(this.getTitle(), true);
            return true;
        }
        if (includes != null) {
            done.put(getTitle(), false);
            for (String i : includes) {
                try {
                    Page p = exporter.findPage(i);
                    if (p != null && p.hasCode(done)) {
                        done.put(this.getTitle(), true);
                        return true;
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    System.out.println(done);
                }
            }
        }
        return false;
    }
    
    public Set<String> getCodeScripts() throws Exception {
        Set<String> scripts = new HashSet<String>();
        if (codeTypes != null) {
            for (String s : codeTypes) {
                String sc = CODE_TYPE_MAP.get(s);
                if (sc == null) {
                    System.out.println("WARNING: no code highlighter for " + s);
                } else {
                    scripts.add(sc);
                }
            }
        }
        if (scripts.isEmpty()) {
            scripts.add(CODE_TYPE_MAP.get("java"));
            scripts.add(CODE_TYPE_MAP.get("plain"));
        }
        if (includes != null) {
            for (String i : includes) {
                try {
                    Page p = exporter.findPage(i);
                    if (p != null && p.getHasCode()) {
                        scripts.addAll(p.getCodeScripts());
                    } else if (p == null) {
                        System.out.println("    Did not find page " + i);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return scripts;
    }

    
    static class V2ContentHandler implements ContentHandler {
        private final Page page;
        
        enum State {
            NONE,
            CHILDREN,
            INCLUDE,
            BLOG_POSTS,
            CODE,
        };
        private State state = State.NONE;
        private Map<String, String> params = new HashMap<String, String>();
        private String paramName;
        private boolean unmigrated;

        V2ContentHandler(Page pg) {
            page = pg;
        }

        public void setDocumentLocator(Locator locator) {
        }

        public void startDocument() throws SAXException {
        }

        public void endDocument() throws SAXException {
        }

        public void startPrefixMapping(String prefix, String uri) throws SAXException {
        }

        public void endPrefixMapping(String prefix) throws SAXException {
        }

        public void startElement(String uri, String localName, String qName, Attributes atts)
            throws SAXException {
            if ("macro".equals(localName) || "structured-macro".equals(localName)) {
                String s = atts.getValue(uri, "name");
                if ("children".equals(s)) {
                    state = State.CHILDREN;
                } else if ("include".equals(s)) {
                    state = State.INCLUDE;
                } else if ("blog-posts".equals(s)) {
                    state = State.BLOG_POSTS;
                } else if ("code".equals(s)) {
                    state = State.CODE;
                } else if ("snippet".equals(s)) {
                    state = State.CODE;
                } else if ("unmigrated-wiki-markup".equals(s)
                    || "unmigrated-inline-wiki-markup".equals(s)) {
                    if (!unmigrated) {
                        System.out.println("WARNING: Page \"" + page.title + "\" (" 
                            + page.spaceKey + ") has unmigrated wiki content.");
                        unmigrated = true;
                        //no idea what is in there, lets just turn on the code highlighting
                        if (page.codeTypes == null) {
                            page.codeTypes = new ConcurrentSkipListSet<String>();
                        }
                        page.codeTypes.add("java");
                        page.codeTypes.add("xml");
                        page.codeTypes.add("plain");
                    }
                } else {
                    //System.out.println("Unknown macro: " + s);
                }
                params.clear();
                paramName = null;
            } else if ("parameter".equals(localName)) {
                paramName = atts.getValue(uri, "name");
            } else if ("default-parameter".equals(localName)) {
                paramName = "default-parameter";
            } else if ("page".equals(localName) && state == State.INCLUDE) {
                for (int x = 0; x < atts.getLength(); x++) {
                    if (atts.getLocalName(x).equals("content-title")
                        && paramName != null) {
                        params.put(paramName, atts.getValue(x));
                    }
                }
            }
        }

        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ("macro".equals(localName) || "structured-macro".equals(localName)) {
                switch (state) {
                case CHILDREN: {
                    String pageName = params.get("page");
                    String depth = params.get("depth");
                    if (depth == null || "".equals(depth.trim())) {
                        depth = "1";
                    }
                    if (page.childrenOf == null) {
                        page.childrenOf = new HashMap<String, Integer>();
                    }
                    if (pageName == null) {
                        page.childrenOf.put(page.title, Integer.parseInt(depth));                    
                    } else {
                        page.childrenOf.put(pageName, Integer.parseInt(depth));
                    }
                    params.clear();
                    state = State.NONE;
                    break;
                }
                case INCLUDE: {
                    if (page.includes == null) {
                        page.includes = new CopyOnWriteArraySet<String>();
                    }
                    String inc = params.get("default-parameter");
                    if (inc == null) {
                        inc = params.get("title");
                    }
                    if (inc == null) {
                        inc = params.get("");
                    }
                    if (inc == null) {
                        System.out.println(page.title + ": Did not find an include name " + params);
                    } else {
                        page.includes.add(inc);
                    }
                    state = State.NONE;
                    break;
                }
                case BLOG_POSTS:
                    page.hasBlog = true;
                    state = State.NONE;
                    break;
                case CODE: {
                    if (page.codeTypes == null) {
                        page.codeTypes = new ConcurrentSkipListSet<String>();
                    }
                    String lang = null;
                    for (Map.Entry<String, String> ent : params.entrySet()) {
                        if ("language".equals(ent.getKey())) {
                            lang = ent.getValue();
                        } else if (ent.getKey().contains(":")) {
                            String parts[] = ent.getKey().split(":");
                            for (String s : parts) {
                                if (("title".equals(s) && !params.containsKey("title"))
                                    || (!params.containsKey("language") 
                                        && ("xml".equals(s) || "java".equals(s)))) {
                                    System.out.println("WARNING Page " + page.title + " has a broken code block");
                                }
                            }
                        } else if ("default-parameter".equals(ent.getKey())) {
                            String s = ent.getValue();
                            if ("xml".equals(s) || "java".equals(s)) {
                                lang = s;
                            }
                        }
                    }
                    if (lang == null) {
                        lang = params.get("");
                        if (!StringUtils.isEmpty(lang)) {
                            page.codeTypes.add("java");
                        }
                    }
                    //System.out.println("l:  " + lang + "   " + params);
                    if (StringUtils.isEmpty(lang)) {
                        page.codeTypes.add("bash");
                        lang = "java";
                    }
                    page.codeTypes.add(lang);
                    state = State.NONE;
                    break;
                }                    
                default:
                    state = State.NONE;
                    break;
                }
            } else if ("parameter".equals(localName)) {
                paramName = null;
            } else if ("default-parameter".equals(localName)) {
                paramName = null;
            }            
        }

        public void characters(char[] ch, int start, int length) throws SAXException {
            if (paramName != null) {
                params.put(paramName, new String(ch, start, length));
            }
        }

        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        }

        public void processingInstruction(String target, String data) throws SAXException {
        }

        public void skippedEntity(String name) throws SAXException {
        }
    }
    
}
