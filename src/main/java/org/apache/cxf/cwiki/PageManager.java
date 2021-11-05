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

import java.util.HashMap;
import java.util.Map;

/**
 * 
 */
public class PageManager {

    private SiteExporter exporter;
    private String dir;
    private Map<String, Page> pages = new HashMap<String, Page>();
    
    public PageManager(SiteExporter exporter) {
        this.exporter = exporter;
    }
    
    public void setDirectory(String d) {
        this.dir = d;
    }
    
    public Page getPage(String spaceKey, String title) throws Exception {
        // XXX: spaceKey must match exporter.getSpace().getKey()
        
        // lookup cached page
        Page cachedPage = pages.get(title);
        if (cachedPage == null) {
            // lookup real page       
            Page page = exporter.findPage(title);
            if (page != null) {
                cachedPage = new Page(page);                
                cachedPage.directory = dir;
                exporter.loadPageContent(cachedPage, null, null);
                pages.put(title, cachedPage);
            } else {
                System.err.println("Page not found: " + title);
            }
        }
        
        return cachedPage;
    }
    
}
