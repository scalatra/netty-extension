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
package org.jboss.netty.handler.codec.http2;

import java.io.File;
import java.io.IOException;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Mixted implementation using both in Memory and in File with a limit of size
 * @author frederic bregier
 *
 */
public class MixteFileUpload implements FileUpload {
    private FileUpload fileUpload = null;

    private long limitSize = 0;

    private long definedSize = 0;

    public MixteFileUpload(String name, String filename, String contentType,
            String contentTransferEncoding, String charset, long size,
            long limitSize) throws NullPointerException,
            IllegalArgumentException {
        this.limitSize = limitSize;
        if (size > this.limitSize) {
            fileUpload = new DiskFileUpload(name, filename, contentType,
                    contentTransferEncoding, charset, size);
        } else {
            fileUpload = new MemoryFileUpload(name, filename, contentType,
                    contentTransferEncoding, charset, size);
        }
        definedSize = size;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#addContent(org.jboss.netty.buffer.ChannelBuffer, boolean)
     */
    public void addContent(ChannelBuffer buffer, boolean last)
            throws IOException {
        if (fileUpload instanceof MemoryFileUpload) {
            if (fileUpload.length() + buffer.readableBytes() > limitSize) {
                DiskFileUpload diskFileUpload = new DiskFileUpload(fileUpload
                        .getName(), fileUpload.getFilename(), fileUpload
                        .getContentType(), fileUpload
                        .getContentTransferEncoding(), fileUpload.getCharset(),
                        definedSize);
                diskFileUpload.addContent(((MemoryFileUpload) fileUpload)
                        .getChannelBuffer(), false);
                fileUpload = diskFileUpload;
            }
        }
        fileUpload.addContent(buffer, last);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#delete()
     */
    public void delete() {
        fileUpload.delete();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#get()
     */
    public byte[] get() throws IOException {
        return fileUpload.get();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getChannelBuffer()
     */
    public ChannelBuffer getChannelBuffer() throws IOException {
        return fileUpload.getChannelBuffer();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getCharset()
     */
    public String getCharset() {
        return fileUpload.getCharset();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getContentType()
     */
    public String getContentType() {
        return fileUpload.getContentType();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getContentTransferEncoding()
     */
    @Override
    public String getContentTransferEncoding() {
        return fileUpload.getContentTransferEncoding();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getFilename()
     */
    public String getFilename() {
        return fileUpload.getFilename();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getString()
     */
    public String getString() throws IOException {
        return fileUpload.getString();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getString(java.lang.String)
     */
    public String getString(String encoding) throws IOException {
        return fileUpload.getString(encoding);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#isCompleted()
     */
    public boolean isCompleted() {
        return fileUpload.isCompleted();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#isInMemory()
     */
    public boolean isInMemory() {
        return fileUpload.isInMemory();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#length()
     */
    public long length() {
        return fileUpload.length();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#renameTo(java.io.File)
     */
    public boolean renameTo(File dest) throws IOException {
        return fileUpload.renameTo(dest);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#setCharset(java.lang.String)
     */
    public void setCharset(String charset) {
        fileUpload.setCharset(charset);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#setContent(org.jboss.netty.buffer.ChannelBuffer)
     */
    public void setContent(ChannelBuffer buffer) throws IOException {
        if (buffer.readableBytes() > limitSize) {
            if (fileUpload instanceof MemoryFileUpload) {
                // change to Disk
                DiskFileUpload diskFileUpload = new DiskFileUpload(fileUpload
                        .getName(), fileUpload.getFilename(), fileUpload
                        .getContentType(), fileUpload
                        .getContentTransferEncoding(), fileUpload.getCharset(),
                        definedSize);
                fileUpload = diskFileUpload;
            }
        }
        fileUpload.setContent(buffer);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#setContent(java.io.File)
     */
    public void setContent(File file) throws IOException {
        if (file.length() > limitSize) {
            if (fileUpload instanceof MemoryFileUpload) {
                // change to Disk
                DiskFileUpload diskFileUpload = new DiskFileUpload(fileUpload
                        .getName(), fileUpload.getFilename(), fileUpload
                        .getContentType(), fileUpload
                        .getContentTransferEncoding(), fileUpload.getCharset(),
                        definedSize);
                fileUpload = diskFileUpload;
            }
        }
        fileUpload.setContent(file);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#setContentType(java.lang.String)
     */
    public void setContentType(String contentType) {
        fileUpload.setContentType(contentType);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#setContentTransferEncoding(java.lang.String)
     */
    @Override
    public void setContentTransferEncoding(String contentTransferEncoding) {
        fileUpload.setContentTransferEncoding(contentTransferEncoding);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#setFilename(java.lang.String)
     */
    public void setFilename(String filename) {
        fileUpload.setFilename(filename);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.HttpData#getHttpDataType()
     */
    public HttpDataType getHttpDataType() {
        return fileUpload.getHttpDataType();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.HttpData#getName()
     */
    public String getName() {
        return fileUpload.getName();
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(HttpData o) {
        return fileUpload.compareTo(o);
    }

    @Override
    public String toString() {
        return "Mixted: " + fileUpload.toString();
    }
}
