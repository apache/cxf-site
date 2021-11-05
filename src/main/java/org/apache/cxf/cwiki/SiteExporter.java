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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.AsyncHandler;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Response;
import javax.xml.ws.Service;
import javax.xml.ws.soap.SOAPBinding;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import org.apache.cxf.clustering.FailoverFeature;
import org.apache.cxf.clustering.RetryStrategy;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.helpers.FileUtils;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.URLResourceLoader;
import org.ccil.cowan.tagsoup.Parser;
import org.ccil.cowan.tagsoup.XMLWriter;

import application.ContentResource;

/**
 * 
 */
public class SiteExporter implements Runnable {

    static final String HOST = "https://cwiki.apache.org";
    static final String ROOT = HOST + "/confluence";
    static final String RPC_ROOT = "/rpc/soap-axis/confluenceservice-v";    
    static final String SOAPNS = "http://soap.rpc.confluence.atlassian.com";
    static final String REST_API = ROOT + "/rest/api";
    
    static final String SEPARATOR = "&nbsp;&gt;&nbsp;";
    
    
    
    static boolean debug = false;
    static String userName = "cxf-export-user";
    static String password;
    
    static int apiVersion = 1;
    
    static boolean svn;
    static boolean commit;
    static StringBuilder svnCommitMessage = new StringBuilder();
    
    static File rootOutputDir = new File(".");
    static String loginToken;
    static Dispatch<Document> dispatch;
    static ContentResource contentResource;
    static AtomicInteger asyncCount = new AtomicInteger();
    static Map<String, Space> spaces = new ConcurrentHashMap<String, Space>();
    static List<SiteExporter> siteExporters;

    Map<String, Page> pages = new ConcurrentHashMap<String, Page>();
    Collection<Page> modifiedPages = new ConcurrentLinkedQueue<Page>();
    Set<String> globalPages = new CopyOnWriteArraySet<String>();
    
    Map<String, BlogEntrySummary> blog = new ConcurrentHashMap<String, BlogEntrySummary>();
    Set<BlogEntrySummary> modifiedBlog = new CopyOnWriteArraySet<BlogEntrySummary>();
    

    String spaceKey = "CXF";
    String pageCacheFile = "pagesConfig.obj";
    String templateName = "template/template.vm";
    String mainDivClass;
    boolean forceAll = true;
    String breadCrumbRoot;
    
    File outputDir = rootOutputDir;

    Template template;
    Space space;
    

    public SiteExporter(String fileName, boolean force) throws Exception {
        forceAll = force;
        
        Properties props = new Properties();
        props.load(new FileInputStream(fileName));
        
        if (props.containsKey("spaceKey")) {
            spaceKey = props.getProperty("spaceKey");
        }
        if (props.containsKey("pageCacheFile")) {
            pageCacheFile = props.getProperty("pageCacheFile");
        }
        if (props.containsKey("templateName")) {
            templateName = props.getProperty("templateName");
        }
        if (props.containsKey("outputDir")) {
            outputDir = new File(rootOutputDir, props.getProperty("outputDir"));
        }
        if (props.containsKey("mainDivClass")) {
            mainDivClass = props.getProperty("mainDivClass");
        }
        if (props.containsKey("breadCrumbRoot")) {
            breadCrumbRoot = props.getProperty("breadCrumbRoot");
        }
        if (props.containsKey("globalPages")) {
            String globals = props.getProperty("globalPages");
            String[] pgs = globals.split(",");
            globalPages.addAll(Arrays.asList(pgs));
        }
        
        props = new Properties();
        String clzName = URLResourceLoader.class.getName();
        props.put("resource.loader", "url");
        props.put("url.resource.loader.class", clzName);
        props.put("url.resource.loader.root", "");
        props.put("url.resource.loader.timeout", 10000);
        
        VelocityEngine engine = new VelocityEngine();
        engine.init(props);
            
        URL url = ClassLoaderUtils.getResource(templateName, this.getClass());
        if (url == null) {
            File file = new File(templateName);
            if (file.exists()) {
                url = file.toURI().toURL();
            } else {
                //try relative to this cfg file
                file = new File(fileName);
                file = new File(file.getParentFile().toURI().resolve(templateName));
                if (file.exists()) {
                    url = file.toURI().toURL();
                }
            }
        }
        if (url == null) {
            File file = new File(fileName);
            file = new File(file.getParentFile().toURI().resolve(templateName));
            System.err.println("Could not find " + templateName + "   " + fileName);
            System.err.println("               " + file.toURI().toURL());
        }
        template = engine.getTemplate(url.toURI().toString());
               
        outputDir.mkdirs();
    }
    
    public static synchronized ContentResource getContentResource() {
        if (contentResource == null) {
            FailoverFeature failover = new FailoverFeature();
            RetryStrategy rs = new RetryStrategy();
            rs.setMaxNumberOfRetries(25);
            List<String> alternateAddresses = new ArrayList<String>();
            alternateAddresses.add(REST_API);
            rs.setAlternateAddresses(alternateAddresses);
            failover.setStrategy(rs);
            JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
            bean.setAddress(REST_API);
            
            bean.setServiceClass(ContentResource.class);
            List<Feature> features = new ArrayList<Feature>();
            
            
            features.add(failover);
            bean.setFeatures(features);
            bean.setUsername(userName);
            bean.setPassword(password);
            contentResource = bean.create(ContentResource.class);
        }
        return contentResource;
    }
    public static synchronized Dispatch<Document> getDispatch() {
        if (dispatch == null) {
            
            FailoverFeature failover = new FailoverFeature();
            RetryStrategy rs = new RetryStrategy();
            rs.setMaxNumberOfRetries(25);
            List<String> alternateAddresses = new ArrayList<String>();
            alternateAddresses.add(ROOT + RPC_ROOT + apiVersion);
            alternateAddresses.add(ROOT + RPC_ROOT + apiVersion);
            alternateAddresses.add(ROOT + RPC_ROOT + apiVersion);
            rs.setAlternateAddresses(alternateAddresses);
            failover.setStrategy(rs);
            
            
            Service service = Service.create(new QName(SOAPNS, "Service"), failover);
            service.addPort(new QName(SOAPNS, "Port"), 
                            SOAPBinding.SOAP11HTTP_BINDING,
                            ROOT + RPC_ROOT + apiVersion);
    
            dispatch = service.createDispatch(new QName(SOAPNS, "Port"), 
                                              Document.class, Service.Mode.PAYLOAD);
            if (debug) {
                ((org.apache.cxf.jaxws.DispatchImpl<?>)dispatch).getClient()
                    .getEndpoint().getInInterceptors().add(new LoggingInInterceptor());
                ((org.apache.cxf.jaxws.DispatchImpl<?>)dispatch).getClient()
                    .getEndpoint().getOutInterceptors().add(new LoggingOutInterceptor());
            }
            HTTPConduit c = (HTTPConduit)((org.apache.cxf.jaxws.DispatchImpl<?>)dispatch)
                .getClient().getConduit();
            HTTPClientPolicy clientPol = c.getClient();
            if (clientPol == null) {
                clientPol = new HTTPClientPolicy();
            }
            //CAMEL has a couple of HUGE HUGE pages that take a long time to render
            clientPol.setReceiveTimeout(5 * 60 * 1000);
            c.setClient(clientPol);
            
        }
        return dispatch;
    }
    
    public void run() {
    	System.out.println("In run");
        try {
            render();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void forcePage(String s) throws Exception {
        Page p = findPage(s);
        if (p != null) {
            pages.remove(p.getId());
            if (!modifiedPages.contains(p)) {
                modifiedPages.add(p);
            }
        }
    }
    
    /**
     * @return true if some pages have changed - rendering is needed
     * @throws Exception
     */
    public boolean initialize() throws Exception {
        if (!forceAll) {
            loadCache();
        }
        
        // debug stuff, force regen of a page
        //forcePage("Navigation");
        //forcePage("Index");
        //forcePage("JavaDocs");
        //forcePage("DOSGi Architecture");
        //forcePage("Book In One Page");
        //forcePage("Security");
        //forcePage("FAQ");
        
        /*
        if (modifiedPages.isEmpty() && checkRSS()) {
            System.out.println("(" + spaceKey + ") No changes detected from RSS");
            return false;
        }
        */

        doLogin();
        checkVersion();
        getSpace();
        if ("-space-".equals(breadCrumbRoot)) {
            breadCrumbRoot = space.getName();
        }
        loadBlog();
        loadPages();
        
        return true;
    }
        
    private void checkVersion() throws ParserConfigurationException, IOException {
        Document doc = DOMUtils.createDocument();
        Element el = doc.createElementNS(SOAPNS, "ns1:getServerInfo");
        Element el2 = doc.createElement("in0");
        el.appendChild(el2);
        el2.setTextContent(loginToken);
        doc.appendChild(el);

        doc = getDispatch().invoke(doc);
        el = DOMUtils.getFirstElement(DOMUtils.getFirstElement(doc.getDocumentElement()));
        while (el != null) {
            if ("majorVersion".equals(el.getLocalName())) {
                String major = DOMUtils.getContent(el);
                if (Integer.parseInt(major) >= 5) {
                    apiVersion = 2;
                    ((java.io.Closeable)dispatch).close();
                    dispatch = null;
                }
            }
              
            el = DOMUtils.getNextElement(el);
        }
    }

    protected void render() throws Exception {
    	for (Page p : modifiedPages) {
            if (globalPages.contains(p.getTitle())) {
                modifiedPages.clear();
                modifiedPages.addAll(pages.values());
                break;
            }
        }
        
        if (forceAll) {
            modifiedPages.clear();
            modifiedPages.addAll(pages.values());
            
            modifiedBlog.clear();
            modifiedBlog.addAll(blog.values());
        }
        if (!modifiedBlog.isEmpty()) {
            //blogs changed, see if any pages have blogs
            for (Page p : pages.values()) {
                if (p.hasBlog() && !modifiedPages.contains(p)) {
                    modifiedPages.add(p);
                }
            }
        }
        if (!modifiedPages.isEmpty() || !modifiedBlog.isEmpty()) {
            renderBlog();
            renderPages();
            saveCache();
        }
    }


    public boolean checkRSS() throws Exception {
        if (forceAll || pages == null || pages.isEmpty()) {
            return false;
        }
        URL url = new URL(ROOT + "/createrssfeed.action?types=page&types=blogpost&types=mail&"
                          //+ "types=comment&"  //cannot handle comment updates yet
                          + "types=attachment&statuses=created&statuses=modified"
                          + "&spaces=" + spaceKey + "&rssType=atom&maxResults=20&timeSpan=2"
                          + "&publicFeed=true");
        InputStream ins = url.openStream();
        Document doc = StaxUtils.read(ins);
        ins.close();
        List<Element> els = DOMUtils.getChildrenWithName(doc.getDocumentElement(),
                                                        "http://www.w3.org/2005/Atom", 
                                                        "entry");
        // XMLUtils.printDOM(doc);
        for (Element el : els) {
            Element e2 = DOMUtils.getFirstChildWithName(el, "http://www.w3.org/2005/Atom", "updated");
            String val = DOMUtils.getContent(e2);
            XMLGregorianCalendar cal = DatatypeFactory.newInstance().newXMLGregorianCalendar(val);
            e2 = DOMUtils.getFirstChildWithName(el, "http://www.w3.org/2005/Atom", "title");
            String title = DOMUtils.getContent(e2);
            
            Page p = findPage(title);
            if (p != null) {
                //found a modified page - need to rebuild
                if (cal.compare(p.getModifiedTime()) > 0) {
                    System.out.println("(" + spaceKey + ") Changed page found: " + title);
                    return false;
                }
            } else {
                BlogEntrySummary entry = findBlogEntry(title);
                if (entry != null) {
                    // we don't have modified date so just assume it's modified
                    // we'll use version number to actually figure out if page is modified or not
                    System.out.println("(" + spaceKey + ") Possible changed blog page found: " + title);
                    return false;
                } else {
                    System.out.println("(" + spaceKey + ") Did not find page for: " + title);
                    return false;
                }
            }
        }
        
        return true;
    }

    private void saveCache() throws Exception {
        File file = new File(rootOutputDir, pageCacheFile);
        file.getParentFile().mkdirs();
        FileOutputStream fout = new FileOutputStream(file);
        ObjectOutputStream oout = new ObjectOutputStream(fout);
        oout.writeObject(pages);
        oout.writeObject(blog);
        oout.close();
    }

    private void renderPages() throws Exception {
        PageManager pageManager = new PageManager(this);
        Renderer renderer = new Renderer(this);
        
        int total = modifiedPages.size();
        int count = 0;
        for (Page p : modifiedPages) {
            count++;
            System.out.println("(" + spaceKey + ") Rendering " + p.getTitle() 
                               + "    (" + count + "/" + total + ")");
            loadAttachments(p);
            
            try {
                loadPageContent(p, null, null);
                
                VelocityContext ctx = new VelocityContext();
                ctx.put("autoexport", this);
                ctx.put("page", p);
                ctx.put("body", p.getContent());
                ctx.put("confluenceUri", ROOT);
                ctx.put("pageManager", pageManager);
                ctx.put("renderer", renderer);
                ctx.put("exporter", this);
                
                File file = new File(outputDir, p.createFileName());
                System.out.println("   -> " + file);
                boolean isNew = !file.exists();
                
                FileWriter writer = new FileWriter(file);
                ctx.put("out", writer);
                template.merge(ctx, writer);
                writer.close();
                if (isNew) {
                    //call "svn add"
                    callSvn("add", file.getAbsolutePath());
                    svnCommitMessage.append("Adding: " + file.getName() + "\n");
                } else {
                    svnCommitMessage.append("Modified: " + file.getName() + "\n");                
                }
                
                p.setContent(null);
            } catch (Exception e) {
                System.out.println("Could not render page " + p.getTitle() + " due to " + e.getMessage());
                e.printStackTrace();
            }

        }
    }
    
    private void renderBlog() throws Exception {
        PageManager pageManager = new PageManager(this);
        Renderer renderer = new Renderer(this);
        
        int total = modifiedBlog.size();
        int count = 0;
        for (BlogEntrySummary entry : modifiedBlog) {
            count++;
            System.out.println("(" + spaceKey + ") Rendering Blog Entry " + entry.getTitle() 
                               + "    (" + count + "/" + total + ")");
            
            try {
                loadAttachments(entry);
                String body = renderPage(entry);
                body = updateContentLinks(entry, body, null, mainDivClass);
                
                pageManager.setDirectory(entry.getDirectory());
                
                VelocityContext ctx = new VelocityContext();
                ctx.put("autoexport", this);
                ctx.put("page", entry);
                ctx.put("body", body);
                ctx.put("confluenceUri", ROOT);
                ctx.put("pageManager", pageManager);
                ctx.put("renderer", renderer);
                ctx.put("exporter", this);
                ctx.put("isBlogEntry", Boolean.TRUE);
                
                File file = new File(outputDir, entry.getPath());
                file.getParentFile().mkdirs();
                boolean isNew = !file.exists();
                
                FileWriter writer = new FileWriter(file);
                ctx.put("out", writer);
                template.merge(ctx, writer);
                writer.close();
                if (isNew) {
                    //call "svn add"
                    callSvn("add", file.getAbsolutePath());
                    svnCommitMessage.append("Adding: " + file.getName() + "\n");
                } else {
                    svnCommitMessage.append("Modified: " + file.getName() + "\n");                
                }
            } catch (Exception e) {
                System.out.println("Could not render blog " + entry.getTitle() + " due to " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    void callSvn(String ... commands) throws Exception {
        callSvn(outputDir, commands);
    }
    static void callSvn(File dir, String ... commands) throws Exception {
        if (svn) {
            List<String> cmds = new ArrayList<String>();
            cmds.add("svn");
            cmds.add("--non-interactive");
            cmds.addAll(Arrays.asList(commands));
            Process p = Runtime.getRuntime().exec(cmds.toArray(new String[cmds.size()]),
                                                  new String[0], dir);
            if (p.waitFor() != 0) {
                IOUtils.copy(p.getErrorStream(), System.err);
            }
        }
    }
    
    private void loadAttachments(AbstractPage p) throws Exception {
        Document doc = DOMUtils.createDocument();
        Element el = doc.createElementNS(SOAPNS, "ns1:getAttachments");
        Element el2 = doc.createElement("in0");
        el.appendChild(el2);
        el2.setTextContent(loginToken);
        el2 = doc.createElement("in1");
        el.appendChild(el2);
        el2.setTextContent(p.getId());
        el.appendChild(el2);
        doc.appendChild(el);

        doc = getDispatch().invoke(doc);
        el = DOMUtils.getFirstElement(DOMUtils.getFirstElement(doc.getDocumentElement()));
        while (el != null) {
            try {
                String filename = DOMUtils.getChildContent(el, "fileName");
                String durl = DOMUtils.getChildContent(el, "url");
                String aid = DOMUtils.getChildContent(el, "id");
                
                p.addAttachment(aid, filename);
                
                String dirName = p.getPath();
                dirName = dirName.substring(0, dirName.lastIndexOf(".")) + ".data";
                File file = new File(outputDir, dirName);
                if (!file.exists()) {
                    callSvn("mkdir", file.getAbsolutePath());
                    file.mkdirs();
                }
                file = new File(file, filename);
                boolean exists = file.exists();
                FileOutputStream out = new FileOutputStream(file);
                URL url = new URL(durl);
                InputStream ins = url.openStream();
                IOUtils.copy(ins, out);
                out.close();
                ins.close();
                if (!exists) {
                    callSvn("add", file.getAbsolutePath());
                    svnCommitMessage.append("Added: " + dirName + "/" + file.getName() + "\n");
                } else {
                    svnCommitMessage.append("Modified: " + dirName + "/" + file.getName() + "\n");
                }
                if (filename.indexOf(' ') != -1) {
                    filename = filename.replace(' ', '-');
                    file = new File(outputDir, dirName);
                    File f2 = new File(file, filename);
                    exists = f2.exists();
                    out = new FileOutputStream(f2);
                    url = new URL(durl);
                    ins = url.openStream();
                    IOUtils.copy(ins, out);
                    out.close();
                    ins.close();
                    if (!exists) {
                        callSvn("add", f2.getAbsolutePath());
                        svnCommitMessage.append("Added: " + dirName + "/" + f2.getName() + "\n");
                    } else {
                        svnCommitMessage.append("Modified: " + dirName + "/" + f2.getName() + "\n");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            el = DOMUtils.getNextElement(el);
        }
    }
    String loadUserImage(AbstractPage p, String href) throws Exception {
        return loadPageBinaryData(p, href, "userimage", true);
    }
    String loadThumbnail(AbstractPage p, String href) throws Exception {
        return loadPageBinaryData(p, href, "thumbs", false);
    }
    String loadPageBinaryData(AbstractPage p, String href, String type, boolean auth) throws Exception {
        String filename = href.substring(href.lastIndexOf('/') + 1);
        filename = filename.replace(' ', '_');
        if (filename.indexOf('?') != -1) {
            filename = filename.substring(0, filename.indexOf('?'));
        }
        
        String dirName = p.getPath();
        dirName = dirName.substring(0, dirName.lastIndexOf(".")) + "." + type;
        File file = new File(outputDir, dirName);
        if (!file.exists()) {
            callSvn("mkdir", file.getAbsolutePath());
            file.mkdirs();
        }
        file = new File(file, filename);
        boolean exists = file.exists();
        FileOutputStream out = new FileOutputStream(file);
        if (auth) {
            if (href.indexOf('?') != -1) {
                href += "&os_authType=basic";
            } else {
                href += "?os_authType=basic";
            }
        }
        URL url = new URL(HOST + href);
        URLConnection con = url.openConnection();
        if (auth) {
            con.addRequestProperty("Authorization", getBasicAuthHeader());
        }
        InputStream ins = con.getInputStream();
        IOUtils.copy(ins, out);
        out.close();
        ins.close();
        if (!exists) {
            callSvn("add", file.getAbsolutePath());
            svnCommitMessage.append("Added: " + dirName + "/" + file.getName() + "\n");                
        } else {
            svnCommitMessage.append("Modified: " + dirName + "/" + file.getName() + "\n");
        }
        return file.getName();
    }
    public String getBasicAuthHeader() {
        String userAndPass = userName + ":" + password;
        try {
            return "Basic " + Base64Utility.encode(userAndPass.getBytes("ISO-8859-1"));
        } catch (UnsupportedEncodingException e) {
            return "Basic";
        }
    }
    public Page findPage(String title) throws Exception {
        return (Page) findByTitle(title, pages.values());
    }
    
    public Page findPageByURL(String url) throws Exception {
        return (Page) findByURL(url, pages.values());
    }
    
    public Page findPageByID(String id) {
        for (Page p : pages.values()) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        return null;
    }

    public String breadcrumbs(BlogEntrySummary page) {
        StringBuffer buffer = new StringBuffer();
        if (breadCrumbRoot != null) {
            buffer.append("<a href=\"");
            buffer.append("../../../index.html");
            buffer.append("\">");
            buffer.append(breadCrumbRoot);
            buffer.append("</a>");
            buffer.append(SEPARATOR);
        } else {
            buffer.append("<a href=\"../../../index.html\">Index</a>");
            buffer.append(SEPARATOR);
        }
        XMLGregorianCalendar published = page.getPublished();
        buffer.append(String.valueOf(published.getYear()));
        buffer.append(SEPARATOR);
        if (published.getMonth() < 10) {
            buffer.append("0");
        } 
        buffer.append(String.valueOf(published.getMonth()));
        buffer.append(SEPARATOR);
        if (published.getDay() < 10) {
            buffer.append("0");
        } 
        buffer.append(String.valueOf(published.getDay()));
        buffer.append(SEPARATOR);
        buffer.append("<a href=\"");
        buffer.append(page.createFileName());
        buffer.append("\">");
        buffer.append(page.getTitle());
        buffer.append("</a>");
        return buffer.toString();
    }
    
    public String breadcrumbs(Page page) {
        StringBuffer buffer = new StringBuffer();
        List<Page> p = new LinkedList<Page>();
        String parentId = page.getParentId();
        Page parent = pages.get(parentId);
        while (parent != null) {
            p.add(0, parent);
            parentId = parent.getParentId();
            parent = pages.get(parentId);
        }
        if (breadCrumbRoot != null) {
            buffer.append("<a href=\"");
            buffer.append("index.html");
            buffer.append("\">");
            buffer.append(breadCrumbRoot);
            buffer.append("</a>");
            buffer.append(SEPARATOR);
        }
        for (Page p2 : p) {
            buffer.append("<a href=\"");
            buffer.append(p2.createFileName());
            buffer.append("\">");
            buffer.append(p2.getTitle());
            buffer.append("</a>");
            buffer.append(SEPARATOR);
        }
        buffer.append("<a href=\"");
        buffer.append(page.createFileName());
        buffer.append("\">");
        buffer.append(page.getTitle());
        buffer.append("</a>");

        return buffer.toString();
    }
    
    public String getPageContent(String title, String divId) throws Exception {
        Page p = findPage(title);
        String s = p.getContentForDivId(divId);
        if (s == null) {
            s = loadPageContent(p, divId, null);
        }
        return s;
    }
    public String getPageContent(String title, String divId, String cls) throws Exception {
        Page p = findPage(title);
        String s = p.getContentForDivId(divId);
        if (s == null) {
            s = loadPageContent(p, divId, cls);
        }
        return s;
    }
    public String getPageContent(String title) throws Exception {
        Page p = findPage(title);
        String s = p.getContent();
        if (s == null) {
            loadPageContent(p, null, null);
        }
        return p.getContent();
    }
    protected String loadPageContent(Page p, String divId, String divCls) throws Exception {
        String content = renderPage(p);
        content = updateContentLinks(p, content, divId, 
                                     divCls == null && divId == null ? mainDivClass : divCls);
        if (divId == null) {
            p.setContent(content);
        } else {
            p.setContentForDivId(divId, content);
        }
        return content;
    }

    private String renderPage(AbstractPage p) throws ParserConfigurationException, IOException {
        ContentResource content = getContentResource();
        InputStream ins = content.getContentById(p.getId(), null, null, "body.export_view")
                .readEntity(InputStream.class);
        
        JsonParser parser = new JsonFactory().createParser(ins);
        JsonToken tok = parser.nextToken();
        boolean inExportView = false;
        while (tok != null) {
            if (tok == JsonToken.FIELD_NAME) {
                if (parser.getCurrentName().equals("export_view")) {
                    inExportView = true;
                }
            } else if (tok == JsonToken.VALUE_STRING && inExportView && parser.getCurrentName().equals("value")) {
                return "<div id='ConfluenceContent'>" + parser.getText() + "</div>";
            }
            tok = parser.nextToken();
        }
        System.out.println("No text for page \"" + p.getTitle() + "\"");
        return "";
    }

    public String unwrap(String v) throws Exception {
        if (v == null) {
            return null;
        }
        return v.trim().replaceFirst("^<div[^>]*>", "").replaceFirst("</div>$", "");
    }

    private static synchronized void doLogin() throws Exception {
        if (loginToken == null) {
            Document doc = DOMUtils.createDocument();
            Element el = doc.createElementNS(SOAPNS, "ns1:login");
            Element el2 = doc.createElement("in0");
            
            if (userName == null) {
                System.out.println("Enter username: ");
                el2.setTextContent(System.console().readLine());
            } else {
                el2.setTextContent(userName);
            }
            el.appendChild(el2);
            el2 = doc.createElement("in1");
            el.appendChild(el2);
            if (password == null) {
                System.out.println("Enter password: ");
                el2.setTextContent(new String(System.console().readPassword()));
            } else {
                el2.setTextContent(password);
            }
            doc.appendChild(el);
            doc = getDispatch().invoke(doc);
            loginToken = doc.getDocumentElement().getFirstChild().getTextContent();
        }
    }

    public void loadCache() throws Exception {
        File file = new File(rootOutputDir, pageCacheFile);
        if (file.exists()) {
            try {
                FileInputStream fin = new FileInputStream(file);
                ObjectInputStream oin = new ObjectInputStream(fin);
                pages = CastUtils.cast((Map<?, ?>)oin.readObject());
                blog = CastUtils.cast((Map<?, ?>)oin.readObject());
                oin.close();
                
                for (Page p : pages.values()) {
                    p.setExporter(this);
                }
            } catch (Throwable t) {
                //invalid cache, punt
                pages.clear();
                blog.clear();
            }
        }
    }
    
    public int getBlogVersion(String pageId) throws Exception {
        Document doc = DOMUtils.newDocument();
        Element el = doc.createElementNS(SOAPNS, "ns1:getBlogEntry");
        Element el2 = doc.createElement("in0");
        el.appendChild(el2);
        el2.setTextContent(loginToken);
        el2 = doc.createElement("in1");
        el.appendChild(el2);
        el2.setTextContent(pageId);
        doc.appendChild(el);
        doc = getDispatch().invoke(doc);
        
        Node nd = doc.getDocumentElement().getFirstChild();
        
        String version = DOMUtils.getChildContent(nd, "version");
        return Integer.parseInt(version);
    }
    
    public void loadBlog() throws Exception {
        System.out.println("Loading Blog entries for " + spaceKey);
        Document doc = DOMUtils.createDocument();
        Element el = doc.createElementNS(SOAPNS, "ns1:getBlogEntries");
        Element el2 = doc.createElement("in0");
        el.appendChild(el2);
        el2.setTextContent(loginToken);
        el2 = doc.createElement("in1");
        el.appendChild(el2);
        el2.setTextContent(spaceKey);
        doc.appendChild(el);
        doc = getDispatch().invoke(doc);
        
        Map<String, BlogEntrySummary> oldBlog = new ConcurrentHashMap<String, BlogEntrySummary>(blog);
        
        Node nd = doc.getDocumentElement().getFirstChild().getFirstChild();
        while (nd != null) {
            if (nd instanceof Element) {
                BlogEntrySummary entry = new BlogEntrySummary((Element)nd);
                entry.setVersion(getBlogVersion(entry.id));
                BlogEntrySummary oldEntry = blog.put(entry.getId(), entry);
                System.out.println("Found Blog entry for " + entry.getTitle() + " " + entry.getPath());

                if (oldEntry == null || oldEntry.getVersion() != entry.getVersion()) {
                    System.out.println("   and it's modified");
                    modifiedBlog.add(entry);
                } else {
                    System.out.println("   but it's not modified");
                }
                oldBlog.remove(entry.getId());
            }
            nd = nd.getNextSibling();
        }
        
        for (String id : oldBlog.keySet()) {
            //these pages have been deleted
            BlogEntrySummary p = blog.remove(id);
            File file = new File(outputDir, p.getPath());
            if (file.exists()) {
                callSvn("rm", file.getAbsolutePath());
                svnCommitMessage.append("Deleted: " + file.getName() + "\n");                
            }
            if (file.exists()) {
                file.delete();
            }            
        }
    }
        
    public BlogEntrySummary findBlogEntry(String title) throws Exception {
        return (BlogEntrySummary) findByTitle(title, blog.values());
    }
    
    public BlogEntrySummary findBlogEntryByURL(String url) throws Exception {
        return (BlogEntrySummary) findByURL(url, blog.values());
    }
    
    private static AbstractPage findByURL(String url, Collection<? extends AbstractPage> pages) throws Exception {
        for (AbstractPage p : pages) {
            if (p.getURL().endsWith(url)) {
                return p;
            }
        }
        return null;
    }
    
    private static AbstractPage findByTitle(String title, Collection<? extends AbstractPage> pages) throws Exception {
        for (AbstractPage p : pages) {
            if (title.equals(p.getTitle())) {
                return p;
            }
        }
        return null;
    }
    
    public void loadPages() throws Exception {
        Document doc = DOMUtils.newDocument();
        Element el = doc.createElementNS(SOAPNS, "ns1:getPages");
        Element el2 = doc.createElement("in0");
        el.appendChild(el2);
        el2.setTextContent(loginToken);
        el2 = doc.createElement("in1");
        el.appendChild(el2);
        el2.setTextContent(spaceKey);
        doc.appendChild(el);
        doc = getDispatch().invoke(doc);
        
        Set<String> allPages = new CopyOnWriteArraySet<String>(pages.keySet());
        Set<Page> newPages = new CopyOnWriteArraySet<Page>();
        List<Future<?>> futures = new ArrayList<Future<?>>(allPages.size());
        
        // XMLUtils.printDOM(doc.getDocumentElement());

        Node nd = doc.getDocumentElement().getFirstChild().getFirstChild();
        while (nd != null) {
            if (nd instanceof Element) {
                futures.add(loadPage((Element)nd, allPages, newPages));
            }
            nd = nd.getNextSibling();
        }
        for (Future<?> f : futures) {
            //wait for all the pages to be done
            f.get();
        }
        for (Page p : newPages) {
            //pages have been added, need to check
            checkForChildren(p);
        }
        for (String id : allPages) {
            //these pages have been deleted
            Page p = pages.remove(id);
            checkForChildren(p);
            
            File file = new File(outputDir, p.createFileName());
            if (file.exists()) {
                callSvn("rm", file.getAbsolutePath());
                svnCommitMessage.append("Deleted: " + file.getName() + "\n");                
            }
            if (file.exists()) {
                file.delete();
            }            
        }
        while (checkIncludes()) {
            // nothing
        }
        
    }

    public boolean checkIncludes() {
        for (Page p : modifiedPages) {
            if (checkIncludes(p)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean checkIncludes(Page p) {
        for (Page p2 : pages.values()) {
            if (p2.includesPage(p.getTitle())
                && !modifiedPages.contains(p2)) {
                modifiedPages.add(p2);
                return true;
            }
        }
        return false;
    }
    public void checkForChildren(Page p) {
        Page parent = pages.get(p.getParentId());
        int d = 1;
        while (parent != null) {
            for (Page p2 : pages.values()) {
                if (p2.hasChildrenOf(parent.getTitle(), d)
                    && !modifiedPages.contains(p2)) {
                    modifiedPages.add(p2);
                }
            }
            parent = pages.get(parent.getParentId());
            d++;
        }
    }
    
    public static synchronized Space getSpace(String key) { 
        Space space = spaces.get(key);
        if (space == null) {
            try {
                doLogin();
                
                Document doc = DOMUtils.newDocument();
                Element el = doc.createElementNS(SOAPNS, "ns1:getSpace");
                Element el2 = doc.createElement("in0");
                el.appendChild(el2);
                el2.setTextContent(loginToken);
                el2 = doc.createElement("in1");
                el.appendChild(el2);
                el2.setTextContent(key);
                doc.appendChild(el);
                
                Document out = getDispatch().invoke(doc);
                space = new Space(out);
                spaces.put(key, space);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return space;
    }
    
    public Future<?> loadPage(Element pageSumEl,
                         final Set<String> allPages,
                         final Set<Page> newPages) throws Exception {
        Document doc = DOMUtils.newDocument();
        Element el = doc.createElementNS(SOAPNS, "ns1:getPage");
        Element el2 = doc.createElement("in0");
        el.appendChild(el2);
        el2.setTextContent(loginToken);
        el2 = doc.createElement("in1");
        el.appendChild(el2);
        el2.setTextContent(DOMUtils.getChildContent(pageSumEl, "id"));
        doc.appendChild(el);
        
        //make sure we only fire off about 15-20 or confluence may get a bit overloaded
        while (asyncCount.get() > 15) {
            Thread.sleep(10);
        }
        asyncCount.incrementAndGet();
        Future<?> f = getDispatch().invokeAsync(doc, new AsyncHandler<Document>() {
            public void handleResponse(Response<Document> doc) {
                try {
                    Page page = new Page(doc.get(), SiteExporter.this);
                    page.setExporter(SiteExporter.this);
                    Page oldPage = pages.put(page.getId(), page);
                    if (oldPage == null || page.getModifiedTime().compare(oldPage.getModifiedTime()) > 0) {
                        if (!modifiedPages.contains(page)) {
                            modifiedPages.add(page);
                        }
                        if (oldPage == null) {
                            //need to check parents to see if it has a {children} tag so we can re-render
                            newPages.add(page);
                        }
                    }
                    if (allPages.contains(page.getId())) {
                        allPages.remove(page.getId());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    asyncCount.decrementAndGet();
                }
            }
        });
        return f;
    }    
    
    private String updateContentLinks(AbstractPage page, String content,
                                      String id, String divCls) throws Exception {
        XMLReader parser = createTagSoupParser();
        StringWriter w = new StringWriter();
        parser.setContentHandler(createContentHandler(page, w, id, divCls));
        parser.parse(new InputSource(new StringReader(content)));
        content = w.toString();
        
        if (content.indexOf("html>") != -1) {
            content = content.substring("<html><body>".length());
            content = content.substring(0, content.lastIndexOf("</body></html>"));
        }
        
        int idx = content.indexOf('>');
        if (idx != -1
            && content.substring(idx + 1).startsWith("<p></p>")) {
            //new confluence tends to stick an empty paragraph at the beginning for some pages (like Banner)
            //that causes major formatting issues.  Strip it.
            content = content.substring(0, idx + 1) + content.substring(idx + 8);
        }
        return content;
    }
    protected XMLReader createTagSoupParser() throws Exception {
        XMLReader reader = new Parser();
        reader.setFeature(Parser.namespacesFeature, false);
        reader.setFeature(Parser.namespacePrefixesFeature, false);
        reader.setProperty(Parser.schemaProperty, new org.ccil.cowan.tagsoup.HTMLSchema() {
            {
                //problem with nested lists that the confluence {toc} macro creates
                elementType("ul", M_LI, M_BLOCK | M_LI, 0);
            }
        });

        return reader;
    }
    protected ContentHandler createContentHandler(AbstractPage page, Writer w, 
                                                  String id, String divCls) {
        XMLWriter xmlWriter = new ConfluenceCleanupWriter(this, w, page, id, divCls);
        xmlWriter.setOutputProperty(XMLWriter.OMIT_XML_DECLARATION, "yes");
        xmlWriter.setOutputProperty(XMLWriter.METHOD, "html");
        return xmlWriter;
    }

    public static void main(String[] args) throws Exception {
        Authenticator.setDefault(new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(userName, password.toCharArray());
            }            
        });
        ListIterator<String> it = Arrays.asList(args).listIterator();
        List<String> files = new ArrayList<String>();
        boolean forceAll = false;
        int maxThreads = -1;
        while (it.hasNext()) {
            String s = it.next();
            if ("-debug".equals(s)) {
                debug = true;
            } else if ("-user".equals(s)) {
                userName = it.next(); 
            } else if ("-password".equals(s)) {
                password = it.next(); 
            } else if ("-d".equals(s)) {
                rootOutputDir = new File(it.next());
            } else if ("-force".equals(s)) {
                forceAll = true;
            } else if ("-svn".equals(s)) {
                svn = true;
            } else if ("-commit".equals(s)) {
                commit = true;
            } else if ("-maxThreads".equals(s)) {
                maxThreads = Integer.parseInt(it.next());
            } else if (s != null && s.length() > 0) {
                files.add(s);
            }
        }
        
        
        List<SiteExporter> exporters = new ArrayList<SiteExporter>();
        for (String file : files) {
        	System.out.println("Setting up " + file);
            exporters.add(new SiteExporter(file, forceAll));
        }
        List<SiteExporter> modified = new ArrayList<SiteExporter>();
        for (SiteExporter exporter : exporters) {
            if (exporter.initialize()) {
                modified.add(exporter);
            }
        }
        
        // render stuff only if needed
        if (!modified.isEmpty()) {
            setSiteExporters(exporters);

            if (maxThreads <= 0) {
                maxThreads = modified.size();
            }

            ExecutorService executor = Executors.newFixedThreadPool(maxThreads, new ThreadFactory() {
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setDaemon(true);
                        return t;
                    }
                });
            List<Future<?>> futures = new ArrayList<Future<?>>(modified.size());
            for (SiteExporter exporter : modified) {
                futures.add(executor.submit(exporter));
            }
            for (Future<?> t : futures) {
                t.get();
            }
        }
                
        if (commit) {
            File file = FileUtils.createTempFile("svncommit", "txt");
            FileWriter writer = new FileWriter(file);
            writer.write(svnCommitMessage.toString());
            writer.close();
            callSvn(rootOutputDir, "commit", "-F", file.getAbsolutePath(), rootOutputDir.getAbsolutePath());
            svnCommitMessage.setLength(0);
        }
    }

    public boolean hasChildren(Page page) {
        for (Page p : pages.values()) {
            if (p == page) {
                continue;
            }
            if (page.getId().equals(p.getParentId())) {
                return true;
            }
        }
        return false;
    }

    public List<Page> getChildren(Page page) {
        List<Page> children = new ArrayList<Page>();
        for (Page p : pages.values()) {
            if (p == page) {
                continue;
            }
            if (page.getId().equals(p.getParentId())) {
                children.add(p);
            }
        }
        return children;
    }

    public String link(Page page) {
        return page.getLink();
    }

    public Space getSpace() {
        if (space == null) {
            space = getSpace(spaceKey);
        }
        return space;
    }
    
    private static void setSiteExporters(List<SiteExporter> exporters) {
        siteExporters = exporters;
    }

    public int getAPIVersion() {
        return apiVersion;
    }

    public String stripHost(String value) {
        if (value.startsWith(HOST)) {
            value = value.substring(HOST.length());
        }
        return value;
    }
    
}
