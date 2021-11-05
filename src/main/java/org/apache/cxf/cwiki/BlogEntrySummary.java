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

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.w3c.dom.Element;

import org.apache.cxf.helpers.DOMUtils;

/**
 * 
 */
public class BlogEntrySummary extends AbstractPage implements Serializable {

    private static final long serialVersionUID = 1L;
    
    final XMLGregorianCalendar published;
    
    // BlogEntrySummary does not have version field but the BlogEntry does.
    // We load and set the version separately. It will be used to decide 
    // whether the blog entry needs to be re-rendered or not.
    int version;
    
    public BlogEntrySummary(Element root) throws Exception {
        super(root);

        String mod = DOMUtils.getChildContent(root, "publishDate");
        published = DatatypeFactory.newInstance().newXMLGregorianCalendar(mod);
    }
    
    public String getDirectory() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.valueOf(published.getYear()));
        builder.append("/");
        if (published.getMonth() < 10) {
            builder.append("0");
        } 
        builder.append(String.valueOf(published.getMonth()));
        builder.append("/");
        if (published.getDay() < 10) {
            builder.append("0");
        } 
        builder.append(String.valueOf(published.getDay()));
        builder.append("/");
        return builder.toString();
    }
    
    public int getVersion() {
        return version;
    }
    
    void setVersion(int version) {
        this.version = version;
    }
    
    public XMLGregorianCalendar getPublished() {
        return published;
    }
    
    public String toString() {
        return "BlogEntrySummary[id=" + id + ",title=" + title + ",version=" + version + ",url=" + url + "]";
    }
}
