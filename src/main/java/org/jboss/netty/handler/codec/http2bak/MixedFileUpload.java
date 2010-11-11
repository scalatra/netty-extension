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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Mixed implementation using both in Memory and in File with a limit of size
 * @author frederic bregier
 *
 */
public class MixedFileUpload implements FileUpload {
    private FileUpload fileUpload = null;

    private long limitSize = 0;

    private long definedSize = 0;

    public MixedFileUpload(String name, String filename, String contentType,
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

    public void delete() {
        fileUpload.delete();
    }

    public byte[] get() throws IOException {
        return fileUpload.get();
    }

    public ChannelBuffer getChannelBuffer() throws IOException {
        return fileUpload.getChannelBuffer();
    }

    public String getCharset() {
        return fileUpload.getCharset();
    }

    public String getContentType() {
        return fileUpload.getContentType();
    }

    public String getContentTransferEncoding() {
        return fileUpload.getContentTransferEncoding();
    }

    public String getFilename() {
        return fileUpload.getFilename();
    }

    public String getString() throws IOException {
        return fileUpload.getString();
    }

    public String getString(String encoding) throws IOException {
        return fileUpload.getString(encoding);
    }

    public boolean isCompleted() {
        return fileUpload.isCompleted();
    }

    public boolean isInMemory() {
        return fileUpload.isInMemory();
    }

    public long length() {
        return fileUpload.length();
    }

    public boolean renameTo(File dest) throws IOException {
        return fileUpload.renameTo(dest);
    }

    public void setCharset(String charset) {
        fileUpload.setCharset(charset);
    }

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

    public void setContent(InputStream inputStream) throws IOException {
        if (fileUpload instanceof MemoryFileUpload) {
            // change to Disk
            DiskFileUpload diskFileUpload = new DiskFileUpload(fileUpload
                    .getName(), fileUpload.getFilename(), fileUpload
                    .getContentType(), fileUpload
                    .getContentTransferEncoding(), fileUpload.getCharset(),
                    definedSize);
            fileUpload = diskFileUpload;
        }
        fileUpload.setContent(inputStream);
    }

    public void setContentType(String contentType) {
        fileUpload.setContentType(contentType);
    }

    public void setContentTransferEncoding(String contentTransferEncoding) {
        fileUpload.setContentTransferEncoding(contentTransferEncoding);
    }

    public void setFilename(String filename) {
        fileUpload.setFilename(filename);
    }

    public HttpDataType getHttpDataType() {
        return fileUpload.getHttpDataType();
    }

    public String getName() {
        return fileUpload.getName();
    }

    public int compareTo(InterfaceHttpData o) {
        return fileUpload.compareTo(o);
    }

    @Override
    public String toString() {
        return "Mixed: " + fileUpload.toString();
    }

    public ChannelBuffer getChunk(int length) throws IOException {
        return fileUpload.getChunk(length);
    }

    public File getFile() throws IOException {
        return fileUpload.getFile();
    }

}
