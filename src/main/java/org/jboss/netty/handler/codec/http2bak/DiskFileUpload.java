/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.http2bak;

/**
 * Disk FileUpload implementation that stores file into real files
 *
 * @author frederic bregier
 *
 */
public class DiskFileUpload extends AbstractDiskHttpData implements FileUpload {
    public static String baseDirectory = null;

    public static boolean deleteOnExitTemporaryFile = true;

    public static String prefix = "FUp_";

    public static String postfix = ".tmp";

    private String filename = null;

    private String contentType = null;

    private String contentTransferEncoding = null;

    public DiskFileUpload(String name, String filename, String contentType,
            String contentTransferEncoding, String charset, long size)
            throws NullPointerException, IllegalArgumentException {
        super(name, charset, size);
        setFilename(filename);
        setContentType(contentType);
        setContentTransferEncoding(contentTransferEncoding);
    }

    public HttpDataType getHttpDataType() {
        return HttpDataType.FileUpload;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2bak.FileUpload#getFilename()
     */
    public String getFilename() {
        return filename;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2bak.FileUpload#setFilename(java.lang.String)
     */
    public void setFilename(String filename) {
        if (filename == null) {
            throw new NullPointerException("filename");
        }
        this.filename = filename;
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Attribute)) {
            return false;
        }
        Attribute attribute = (Attribute) o;
        return getName().equalsIgnoreCase(attribute.getName());
    }

    public int compareTo(InterfaceHttpData arg0) {
        if (!(arg0 instanceof FileUpload)) {
            throw new ClassCastException("Cannot compare " + getHttpDataType() +
                    " with " + arg0.getHttpDataType());
        }
        return compareTo((FileUpload) arg0);
    }

    public int compareTo(FileUpload o) {
        int v;
        v = getName().compareToIgnoreCase(o.getName());
        if (v != 0) {
            return v;
        }
        // TODO should we compare size ?
        return v;
    }

    public void setContentType(String contentType) {
        if (contentType == null) {
            throw new NullPointerException("contentType");
        }
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    public String getContentTransferEncoding() {
        return contentTransferEncoding;
    }

    public void setContentTransferEncoding(String contentTransferEncoding) {
        this.contentTransferEncoding = contentTransferEncoding;
    }

    @Override
    public String toString() {
        return HttpPostBodyUtil.CONTENT_DISPOSITION+": "+
            HttpPostBodyUtil.FORM_DATA+"; "+HttpPostBodyUtil.NAME+"=\"" + getName() +
                "\"; "+HttpPostBodyUtil.FILENAME+"=\"" + filename + "\"\r\n" +
                HttpHeaders.Names.CONTENT_TYPE+": " + contentType +
                (charset != null? "; "+HttpHeaders.Values.CHARSET+"=" + charset + "\r\n" : "\r\n") +
                HttpHeaders.Names.CONTENT_LENGTH+": " + length() + "\r\n" +
                "Completed: " + isCompleted() +
                "\r\nIsInMemory: " + isInMemory() + "\r\nRealFile: " +
                file.getAbsolutePath() + " DefaultDeleteAfter: " +
                deleteOnExitTemporaryFile;
    }

    @Override
    protected boolean deleteOnExit() {
        return deleteOnExitTemporaryFile;
    }

    @Override
    protected String getBaseDirectory() {
        return baseDirectory;
    }

    @Override
    protected String getDiskFilename() {
        return filename;
    }

    @Override
    protected String getPostfix() {
        return postfix;
    }

    @Override
    protected String getPrefix() {
        return prefix;
    }
}
