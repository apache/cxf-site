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

import java.io.File;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import org.ccil.cowan.tagsoup.XMLWriter;

/**
 * 
 */
public class ConfluenceCleanupWriter extends XMLWriter {

    private final AbstractPage page;
    private final SiteExporter exporter;
    private final String divId;
    private final String divCls;
    private final Stack<Integer> trStack = new Stack<Integer>(); 
    private int curTrCount;

    public ConfluenceCleanupWriter(SiteExporter exp, Writer writer, AbstractPage page, 
                                   String id, String divCls) {
        super(writer);
        this.page = page;
        this.exporter = exp;
        this.divId = id;
        this.divCls = divCls;
    }

    private File getPageDirectory() {
        String pageDir = page.getDirectory();
        if (pageDir.length() > 0) {
            return new File(exporter.outputDir, pageDir);
        } else {
            return exporter.outputDir;
        }
    }
    
    private String findPageWithURL(String url) throws Exception {
        String location = findPageWithURL(exporter, url);
        if (location == null) {
            for (SiteExporter siteExporter : SiteExporter.siteExporters) {
                if (exporter == siteExporter) {
                    continue;
                }
                location = findPageWithURL(siteExporter, url);
                if (location != null) {
                    break;
                }
            }
        }
        return location;
    }
    
    private String findPageWithURL(SiteExporter siteExporter, String url) throws Exception {
        if (siteExporter.getSpace().getURL().endsWith(url)) {
            String prefix = getRelativePath(SiteExporter.rootOutputDir, getPageDirectory(), siteExporter.outputDir);
            String location = prefix + "index.html";
            if (exporter != siteExporter) {
                System.out.println("Cross space link to " + location);
            }
            return location;
        } else {
            AbstractPage p = siteExporter.findPageByURL(url);
            if (p == null) {
                p = siteExporter.findBlogEntryByURL(url);
            }
            if (p != null) {
                String prefix = getRelativePath(SiteExporter.rootOutputDir, getPageDirectory(), siteExporter.outputDir);
                String location = prefix + p.getPath();
                if (exporter != siteExporter) {
                    System.out.println("Cross space link to " + location);
                }
                return location;
            }
        }
        return null;
    }
    
    private String findPageByID(String id) throws Exception {
        String location = findPageByID(exporter, id);        
        if (location == null) {
            for (SiteExporter siteExporter : SiteExporter.siteExporters) {
                if (exporter == siteExporter) {
                    continue;
                }
                location = findPageByID(siteExporter, id);
                if (location != null) {
                    break;
                }
            }
        }
        return location;
    }
    
    private String findPageByID(SiteExporter siteExporter, String url) throws Exception {
        AbstractPage p = siteExporter.findPageByID(url);
        if (p != null) {
            String prefix = getRelativePath(SiteExporter.rootOutputDir, getPageDirectory(), siteExporter.outputDir);
            String location = prefix + p.getPath();
            if (exporter != siteExporter) {
                System.out.println("Cross space link (via id) to " + location);
            }
            return location;
        }
        return null;
    }
    
    //CHECKSTYLE:OFF
    public void startElement(String uri, String localName, String qName, final Attributes atts)
        throws SAXException {
        AttributesWrapper newAtts = new AttributesWrapper(atts);
        if ("a".equals(localName.toLowerCase())
            || "a".equals(qName.toLowerCase())) {
            String href = atts.getValue("href");
            //Confluence sticks this on links from blog entries, but it's invalid
            newAtts.remove("data-username");
            if (href != null) {
                href = href.trim();
            }
            if (href != null && href.startsWith("/confluence/display/")) {
                String params = "";
                if (href.indexOf('#') != -1) {
                    params = href.substring(href.indexOf('#'));
                    href = href.substring(0, href.indexOf('#'));
                }
                if (href.indexOf('?') != -1) {
                    if (params.length() > 0) {
                        params = href.substring(href.indexOf('?')) + "#" + params;
                    } else {
                        params = href.substring(href.indexOf('?'));
                    }
                    href = href.substring(0, href.indexOf('?'));
                }
                try {
                    String p = findPageWithURL(href);
                    if (p != null) {
                        newAtts.addMapping("href", p + params);
                    } else {
                        if (href.indexOf('~') == -1) {
                            //link to a user page is OK, don't warn about it
                            System.out.println("Could not find page for " + href 
                                               + " linked from " + page.getTitle());
                        }
                        newAtts.addMapping("href", SiteExporter.ROOT + href.substring(11));
                    }
                } catch (Exception e) {
                    throw new SAXException(e);
                }
            } else if (href != null && href.startsWith("/confluence/plugins/")) {
                newAtts.addMapping("href", SiteExporter.ROOT + href.substring(11));
            } else if (href != null && href.contains("/confluence/pages/viewpage.action")) {
                String params = "";
                if (href.indexOf('#') != -1) {
                    params = href.substring(href.indexOf('#'));
                    href = href.substring(0, href.indexOf('#'));
                }
                int idx = href.indexOf("pageId=");
                String id = href.substring(idx + 7);
                try {
                    String location = findPageByID(id);
                    if (location != null) {
                          newAtts.addMapping("href", location + params);
                    } else {
                        System.out.println("Could not find page for id: " + id 
                                           + " linked from " + page.getTitle());
                    }   
                } catch (Exception e) {
                    throw new SAXException(e);
                }
            } else if (href != null && href.contains("/confluence/download/attachments")) {
                href = href.substring(href.lastIndexOf("/"));
                String dirName = page.createFileName();
                dirName = dirName.substring(0, dirName.lastIndexOf(".")) + ".data";

                newAtts.addMapping("href", dirName + href);
            } else if (href != null && href.contains("/confluence/pages/createpage.action")) {
                System.out.println("Adding createpage link for " + href + " from " + page.getTitle());
                newAtts.addMapping("href", SiteExporter.HOST + href);
            } else if (href != null 
                && (href.startsWith("http://")
                    || href.startsWith("https://"))) {
                URL url;
                try {
                    url = new URL(href);
                    if (url.getHost().contains("apache.org")) {
                        newAtts.remove("rel");
                    }
                    if (url.getHost().equals("cxf.apache.org")
                        && "external-link".equals(newAtts.getValue("class"))) {
                        newAtts.remove("class");
                    }
                } catch (MalformedURLException e) {
                    //ignore
                }
            }
        } else if ("img".equals(localName.toLowerCase())
            || "img".equals(qName.toLowerCase())) {
            String href = exporter.stripHost(atts.getValue("src"));
            if ("absmiddle".equalsIgnoreCase(atts.getValue("align"))) {
                newAtts.addMapping("align", "middle");
            }
            String cls = atts.getValue("class");
            if (href != null && href.startsWith("/confluence/images/")) {
                newAtts.addMapping("src", href.replaceFirst("^/confluence/images/", "/images/confluence/"));
            } else if (href != null && href.startsWith("/confluence/download/attachments")) {
                if (cls == null || cls.contains("confluence-embedded-image")) {
                    href = href.substring(0, href.lastIndexOf('?'));
                    href = href.substring(href.lastIndexOf('/'));
                    String dirName = page.createFileName();
                    dirName = dirName.substring(0, dirName.lastIndexOf(".")) + ".data";

                    newAtts.addMapping("src", dirName + href.replaceAll("\\+", "-"));
                } else if (cls.contains("userLogo")) {
                    String name = href;
                    try {
                        name = exporter.loadUserImage(page, href);
                    } catch (Exception ex) {
                        System.out.println("Could not download userLogo " + href 
                                           + " linked from " + page.getTitle());                    
                    }
                    String dirName = page.createFileName();
                    dirName = dirName.substring(0, dirName.lastIndexOf(".")) + ".userimage/";

                    newAtts.addMapping("src", dirName + name);                    
                } else {
                    newAtts.addMapping("src", SiteExporter.HOST + href.replaceAll("\\+", "-"));
                }
            } else if (href != null && href.startsWith("/confluence/download/thumbnails")) {
                String name = href;
                try {
                    name = exporter.loadThumbnail(page, href);
                } catch (Exception ex) {
                    System.out.println("Could not download thumbnail " + href 
                                       + " linked from " + page.getTitle());                    
                }
                String dirName = page.createFileName();
                dirName = dirName.substring(0, dirName.lastIndexOf(".")) + ".thumbs/";

                newAtts.addMapping("src", dirName + name);
            } else if (href != null && href.startsWith("/confluence")) {
                newAtts.addMapping("src", SiteExporter.HOST + href);
            }
        } else if ("th".equals(localName.toLowerCase())
            || "th".equals(qName.toLowerCase())) {
            curTrCount++;
        } else if ("td".equals(localName.toLowerCase())
            || "td".equals(qName.toLowerCase())) {
            curTrCount++;
            if (newAtts.getIndex("nowrap") != -1) {
                //make sure nowrap attribute is set to nowrap per HTML spec
                newAtts.addMapping("nowrap", "nowrap");
            }
        } else if ("tr".equals(localName.toLowerCase())
            || "tr".equals(qName.toLowerCase())) {
            trStack.push(curTrCount);
            curTrCount = 0;
        } else if ("div".equals(localName.toLowerCase())
            || "div".equals(qName.toLowerCase())) {
            String id = atts.getValue("id");
            if ("ConfluenceContent".equals(id)) {
                if (divCls != null) {
                    newAtts.addMapping("class", divCls);
                    newAtts.remove("id");
                }
                if (divId != null) {
                    newAtts.addMapping("id", divId);
                } 
            }
        } else if ("input".equals(localName.toLowerCase())
            || "input".equals(qName.toLowerCase())) {
            String value = atts.getValue("value");
            if (value != null && value.startsWith("/confluence/")) {
                newAtts.addMapping("value", SiteExporter.ROOT + value.substring(11));
            }
        } else if ("pre".equals(localName.toLowerCase())
                   || "pre".equals(qName.toLowerCase())) {
            String cls = atts.getValue("class");
            if ("syntaxhighlighter-pre".equalsIgnoreCase(cls)) {
                String brush = atts.getValue("data-syntaxhighlighter-params");
                if (brush.toLowerCase().startsWith("brush")) {
                    newAtts.remove("data-syntaxhighlighter-params");
                    newAtts.remove("data-theme");
                    newAtts.addMapping("class", brush);
                }
            }
        }
        super.startElement(uri, localName, qName, newAtts);
    }
    
    
    
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if ("tr".equals(localName.toLowerCase())
            || "tr".equals(qName.toLowerCase())) {
            if (curTrCount == 0) {
                super.startElement("td");
                super.endElement("td");
            }
            curTrCount = trStack.pop();
        }
        super.endElement(uri, localName, qName);
    }

    final class AttributesWrapper implements Attributes {
        private final Map<String, String> atts = new LinkedHashMap<String, String>();

        private AttributesWrapper(Attributes atts) {
            for (int x = 0; x < atts.getLength(); x++) {
                this.atts.put(atts.getQName(x), atts.getValue(x));
            }
        }
        private Map.Entry<String, String> getByIndex(int i) {
            for (Map.Entry<String, String> a : atts.entrySet()) {
                if  (i == 0) {
                    return a;
                }
                --i;
            }
            return null;
        }
        private int findIndex(String k) {
            int i = 0;
            for (Map.Entry<String, String> a : atts.entrySet()) {
                if  (a.getKey().equals(k)) {
                    return i;
                }
                ++i;
            }
            return -1;
        }
        
        public void remove(String k) {
            atts.remove(k);
        }
        
        public void addMapping(String k, String v) {
            atts.put(k, v);
        }
        
        public int getLength() {
            return atts.size();
        }
        
        public String getURI(int index) {
            return "";
        }

        public String getLocalName(int index) {
            return getByIndex(index).getKey();
        }

        public String getQName(int index) {
            return getByIndex(index).getKey();
        }

        public String getType(int index) {
            return "CDATA";
        }

        public int getIndex(String uri, String localName) {
            return findIndex(localName);
        }

        public int getIndex(String qName) {
            return findIndex(qName);
        }

        public String getType(String uri, String localName) {
            return "CDATA";
        }

        public String getType(String qName) {
            return "CDATA";
        }

        public String getValue(int index) {
            return getByIndex(index).getValue();
        }

        public String getValue(String uri, String localName) {
            return atts.get(localName);
        }

        public String getValue(String qName) {
            return atts.get(qName);
        }
    }

    private static String getRelativePath(File root, File current, File other) throws Exception {
        if (current.equals(other)) {
            return "";
        }
        
        String rootPath = root.getCanonicalPath();
        String currentPath = current.getCanonicalPath();
        StringBuilder builder = new StringBuilder();
        while (!rootPath.equals(currentPath)) {
            current = current.getParentFile();
            currentPath = current.getCanonicalPath();
            builder.append("../");
        }
        
        String otherPath = other.getCanonicalPath();
        
        if (rootPath.equals(otherPath)) {
            // nothing to do
        } else if (otherPath.startsWith(rootPath)) {
            String name = otherPath.substring(rootPath.length() + 1);
            builder.append(name);
            builder.append("/");
        } else {
            throw new RuntimeException("Non-relative locations: " + rootPath + " " + otherPath);
        }       
        
        return builder.toString();
    }
}
