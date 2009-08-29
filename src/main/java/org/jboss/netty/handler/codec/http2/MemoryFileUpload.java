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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jboss.netty.buffer.AggregateChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * Default FileUpload implementation that stores file into memory.<br><br>
 *
 * Warning: be aware of the memory limitation.
 *
 * @author frederic bregier
 *
 */
public class MemoryFileUpload implements FileUpload {
    private final String name;

    private String filename = null;

    private ChannelBuffer channelBuffer = null;

    private String contentType = null;

    private String contentTransferEncoding = null;

    private String charset = HttpCodecUtil.DEFAULT_CHARSET;

    private long definedSize = 0;

    private long size = 0;

    private boolean completed = false;

    public MemoryFileUpload(String name, String filename, String contentType,
            String contentTransferEncoding, String charset, long size)
            throws NullPointerException, IllegalArgumentException {
        if (name == null) {
            throw new NullPointerException("name");
        }
        name = name.trim();
        if (name.length() == 0) {
            throw new IllegalArgumentException("empty name");
        }

        for (int i = 0; i < name.length(); i ++) {
            char c = name.charAt(i);
            if (c > 127) {
                throw new IllegalArgumentException(
                        "name contains non-ascii character: " + name);
            }

            // Check prohibited characters.
            switch (c) {
            case '=':
            case ',':
            case ';':
            case ' ':
            case '\t':
            case '\r':
            case '\n':
            case '\f':
            case 0x0b: // Vertical tab
                throw new IllegalArgumentException(
                        "name contains one of the following prohibited characters: " +
                                "=,; \\t\\r\\n\\v\\f: " + name);
            }
        }
        this.name = name;
        setFilename(filename);
        setContentType(contentType);
        setContentTransferEncoding(contentTransferEncoding);
        if (charset != null) {
            setCharset(charset);
        }
        definedSize = size;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.HttpData#getHttpDataType()
     */
    public HttpDataType getHttpDataType() {
        return HttpDataType.FileUpload;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.HttpData#getName()
     */
    public String getName() {
        return name;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getFilename()
     */
    public String getFilename() {
        return filename;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#setFilename(java.lang.String)
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

    public int compareTo(HttpData arg0) {
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
        // TODO should we compare size for instance ?
        return v;
    }

    public void setContent(ChannelBuffer buffer) throws IOException {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        long localsize = buffer.readableBytes();
        if (definedSize > 0 && definedSize < localsize) {
            throw new IOException("Out of size: " + localsize + " > " +
                    definedSize);
        }
        channelBuffer = buffer;
        size = localsize;
        completed = true;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#addContent(org.jboss.netty.buffer.ChannelBuffer, boolean)
     */
    public void addContent(ChannelBuffer buffer, boolean last)
            throws IOException {
        if (buffer != null) {
            long localsize = buffer.readableBytes();
            if (definedSize > 0 && definedSize < size + localsize) {
                throw new IOException("Out of size: " + (size + localsize) +
                        " > " + definedSize);
            }
            size += localsize;
            if (channelBuffer == null) {
                channelBuffer = buffer;
            } else {
                //this.channelBuffer = ChannelBuffers.wrappedBuffer(this.channelBuffer, buffer);
                // less memory usage
                channelBuffer = AggregateChannelBuffer.wrappedCheckedBuffer(
                        channelBuffer, buffer);
            }
        }
        if (last) {
            completed = true;
        } else {
            if (buffer == null) {
                throw new NullPointerException("buffer");
            }
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#setContent(java.io.File)
     */
    public void setContent(File file) throws IOException {
        if (file == null) {
            throw new NullPointerException("file");
        }
        long newsize = file.length();
        if (newsize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "File too big to be loaded in memory");
        }
        FileInputStream inputStream = new FileInputStream(file);
        FileChannel fileChannel = inputStream.getChannel();
        byte[] array = new byte[(int) newsize];
        ByteBuffer byteBuffer = ByteBuffer.wrap(array);
        int read = 0;
        while (read < newsize) {
            read += fileChannel.read(byteBuffer);
        }
        fileChannel.close();
        channelBuffer = ChannelBuffers.wrappedBuffer(byteBuffer);
        size = newsize;
        completed = true;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#isCompleted()
     */
    public boolean isCompleted() {
        return completed;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#delete()
     */
    public void delete() {
        // nothing to do
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#get()
     */
    public byte[] get() {
        if (channelBuffer == null) {
            return new byte[0];
        }
        byte[] array = new byte[channelBuffer.readableBytes()];
        channelBuffer.getBytes(channelBuffer.readerIndex(), array);
        return array;
    }

    public void setContentType(String contentType) {
        if (contentType == null) {
            throw new NullPointerException("contentType");
        }
        this.contentType = contentType;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getContentType()
     */
    public String getContentType() {
        return contentType;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getCharset()
     */
    public String getCharset() {
        return charset;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#setCharset(java.lang.String)
     */
    public void setCharset(String charset) {
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        this.charset = charset;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getContentTransferEncoding()
     */
    @Override
    public String getContentTransferEncoding() {
        return contentTransferEncoding;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#setContentTransferEncoding(java.lang.String)
     */
    @Override
    public void setContentTransferEncoding(String contentTransferEncoding) {
        this.contentTransferEncoding = contentTransferEncoding;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getString()
     */
    public String getString() {
        return getString(HttpCodecUtil.DEFAULT_CHARSET);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getString(java.lang.String)
     */
    public String getString(String encoding) {
        if (channelBuffer == null) {
            return "";
        }
        if (encoding == null) {
            return getString(HttpCodecUtil.DEFAULT_CHARSET);
        }
        return channelBuffer.toString(encoding);
    }

    /**
     * Utility to go from a In Memory FileUpload
     * to a Disk (or another implementation) FileUpload
     * @return the attached ChannelBuffer containing the actual bytes
     */
    public ChannelBuffer getChannelBuffer() {
        return channelBuffer;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#isInMemory()
     */
    public boolean isInMemory() {
        return true;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#length()
     */
    public long length() {
        return size;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#renameTo(java.io.File)
     */
    public boolean renameTo(File dest) throws IOException {
        if (dest == null) {
            throw new NullPointerException("dest");
        }
        if (channelBuffer == null) {
            // empty file
            dest.createNewFile();
            return true;
        }
        int length = channelBuffer.readableBytes();
        FileOutputStream outputStream = new FileOutputStream(dest);
        FileChannel fileChannel = outputStream.getChannel();
        ByteBuffer byteBuffer = channelBuffer.toByteBuffer();
        int written = 0;
        while (written < length) {
            written += fileChannel.write(byteBuffer);
            fileChannel.force(false);
        }
        fileChannel.close();
        return written == length;
    }

    @Override
    public String toString() {
        return "content-disposition: form-data; name=\"" + name +
                "\"; filename=\"" + filename + "\"\r\n" + "Content-Type: " +
                contentType +
                (charset != null? "; charset=" + charset + "\r\n" : "\r\n") +
                "Content-Length: " + size + "\r\n" + "Completed: " + completed +
                "\r\nIsInMemory: " + isInMemory();
    }
}
