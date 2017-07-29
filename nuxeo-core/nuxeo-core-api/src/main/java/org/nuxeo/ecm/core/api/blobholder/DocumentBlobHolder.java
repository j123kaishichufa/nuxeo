/*
 * (C) Copyright 2006-2016 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 */
package org.nuxeo.ecm.core.api.blobholder;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.utils.BlobsExtractor;

/**
 * {@link BlobHolder} implementation based on a {@link DocumentModel} and a XPath.
 *
 * @author tiry
 */
public class DocumentBlobHolder extends AbstractBlobHolder {

    protected final DocumentModel doc;

    protected final String xPath;

    protected List<Blob> blobList = null;

    public DocumentBlobHolder(DocumentModel doc, String xPath) {
        this.doc = doc;
        this.xPath = xPath;
    }

    @Override
    protected String getBasePath() {
        return doc.getPathAsString();
    }

    @Override
    public Blob getBlob() {
        return (Blob) doc.getPropertyValue(xPath);
    }

    @Override
    public void setBlob(Blob blob) {
        doc.getProperty(xPath).setValue(blob);
    }

    @Override
    public Calendar getModificationDate() {
        return (Calendar) doc.getProperty("dublincore", "modified");
    }

    @Override
    public String getHash() {
        Blob blob = getBlob();
        if (blob != null) {
            String h = blob.getDigest();
            if (h != null) {
                return h;
            }
        }
        return doc.getId() + xPath + String.valueOf(getModificationDate());
    }

    @Override
    public Serializable getProperty(String name) {
        return null;
    }

    @Override
    public Map<String, Serializable> getProperties() {
        return null;
    }

    @Override
    public List<Blob> getBlobs() {
        if (blobList == null) {
            List<Property> properties = getBlobProperties();
            blobList = properties.stream().map(prop -> (Blob) prop.getValue()).collect(Collectors.toList());
        }
        return blobList;
    }

    /**
     * Returns a new {@link DocumentBlobHolder} for the blob at the given {@code index} where {@link #getBlob} and
     * {@link #getXpath} will return information about the blob.
     *
     * @param index the blob index
     * @return the new blob holder
     * @throws IndexOutOfBoundsException if the index is invalid
     * @since 9.3
     */
    public DocumentBlobHolder asDirectBlobHolder(int index) throws IndexOutOfBoundsException {
        List<Property> properties = getBlobProperties();
        blobList = properties.stream().map(prop -> (Blob) prop.getValue()).collect(Collectors.toList());
        String xpath = properties.get(index).getXPath();
        DocumentBlobHolder bh = new DocumentBlobHolder(doc, xpath);
        bh.blobList = blobList;
        return bh;
    }

    /**
     * Gets all the blob properties, with the main blob first.
     *
     * @since 9.3
     */
    protected List<Property> getBlobProperties() {
        List<Property> properties = new BlobsExtractor().getBlobsProperties(doc);
        if (xPath != null) {
            // be sure that the "main" blob is always in first position
            Iterator<Property> it = properties.iterator();
            while (it.hasNext()) {
                Property property = it.next();
                if (property.getXPath().equals(xPath)) {
                    it.remove();
                    properties.add(0, property);
                    break;
                }
            }
        }
        return properties;
    }

    /**
     * @since 7.3
     */
    public String getXpath() {
        return xPath;
    }

    /**
     * @since 7.4
     */
    public DocumentModel getDocument() {
        return doc;
    }
}
