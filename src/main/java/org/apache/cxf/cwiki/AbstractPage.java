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
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;

import org.apache.cxf.helpers.DOMUtils;

/**
 * 
 */
public class AbstractPage implements Serializable {

    private static final long serialVersionUID = 1L;
    
    final String id;
    final String title;
    final String url;

    Map<String, String> attachments;
    
    transient String directory;
    
    public AbstractPage(Element root) throws Exception {
        // org.apache.cxf.helpers.XMLUtils.printDOM(doc.getDocumentElement());

        id = DOMUtils.getChildContent(root, "id");
        title = DOMUtils.getChildContent(root, "title");
        url = DOMUtils.getChildContent(root, "url");
    }
    
    public AbstractPage(AbstractPage source) {
        this.id = source.id;
        this.title = source.title;
        this.url = source.url;
        this.directory = source.directory;
    }
    
    public String getDirectory() {
        return directory == null ? "" : directory;
    }
    
    public String getPath() {
        return getDirectory() + createFileName();
    }
    
    public String createFileName() {
        StringBuffer buffer = new StringBuffer();
        char array[] = title.toLowerCase().toCharArray();
        boolean separated = true;
        for (int x = 0; x < array.length; x++) {
            if ("abcdefghijklmnopqrstuvwxyz0123456789".indexOf(array[x]) >= 0) {
                buffer.append(Character.toLowerCase(array[x]));
                separated = false;
            } else if ("\r\n\t -".indexOf(array[x]) >= 0) {
                if (separated) {
                    continue;
                }
                buffer.append('-');
                separated = true;
            }
        }
        if (buffer.length() == 0) {
            return id + ".html";
        }
        return buffer.append(".html").toString();
    }
    
    public String getId() {
        return id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getURL() {
        return url;
    }
    
    public boolean getHasCode() {
        return false;
    }

    public String toString() {
        return "AbstractPage[id=" + id + ",title=" + title + ",url=" + url + "]";
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
    
}
