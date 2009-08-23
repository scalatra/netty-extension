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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * Disk FileUpload implementation that stores file into real files
 *
 * @author frederic bregier
 *
 */
public class DiskFileUpload implements FileUpload {
    public static String baseDirectory = null;

    public static boolean deleteOnExitTemporaryFile = true;

    public static String prefix = "FUp_";

    public static String postfix = ".tmp";

    private final String name;

    private String filename = null;

    private long definedSize = 0;

    private long size = 0;

    private File file = null;

    private FileChannel fileChannel = null;

    private String contentType = null;

    private String charset = HttpCodecUtil.DEFAULT_CHARSET;

    private boolean completed = false;

    public DiskFileUpload(String name, String filename, String contentType,
            String charset, long size) throws NullPointerException,
            IllegalArgumentException {
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
            throw new ClassCastException("Cannot compare " +
                    getHttpDataType() + " with " + arg0.getHttpDataType());
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

    /**
     *
     * @return a new Temp File from filename, default prefix, postfix and baseDirectory
     * @throws IOException
     */
    private File tempFile() throws IOException {
        String newpostfix = null;
        if (filename != null) {
            newpostfix = "_" + filename;
        } else {
            newpostfix = postfix;
        }
        File tmpFile;
        if (baseDirectory == null) {
            // create a temporary file
            tmpFile = File.createTempFile(prefix, newpostfix);
        } else {
            tmpFile = File.createTempFile(prefix, newpostfix, new File(
                    baseDirectory));
        }
        if (deleteOnExitTemporaryFile) {
            tmpFile.deleteOnExit();
        }
        return tmpFile;
    }

    public void setContent(ChannelBuffer buffer) throws IOException {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        file = tempFile();
        if (buffer.readableBytes() == 0) {
            // empty file
            file.createNewFile();
            return;
        }
        size = buffer.readableBytes();
        if (definedSize > 0 && definedSize < size) {
            throw new IOException("Out of size: " + size + " > " + definedSize);
        }
        FileOutputStream outputStream = new FileOutputStream(file);
        FileChannel localfileChannel = outputStream.getChannel();
        ByteBuffer byteBuffer = buffer.toByteBuffer();
        int written = 0;
        while (written < size) {
            written += localfileChannel.write(byteBuffer);
            localfileChannel.force(false);
        }
        buffer.readerIndex(buffer.readerIndex() + written);
        localfileChannel.close();
        completed = true;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#addContent(org.jboss.netty.buffer.ChannelBuffer, boolean)
     */
    public void addContent(ChannelBuffer buffer, boolean last)
            throws IOException {
        if (file == null) {
            file = tempFile();
        }
        if (fileChannel == null) {
            FileOutputStream outputStream = new FileOutputStream(file);
            fileChannel = outputStream.getChannel();
        }
        if (buffer != null) {
            int localsize = buffer.readableBytes();
            if (definedSize > 0 && definedSize < size + localsize) {
                throw new IOException("Out of size: " + (size + localsize) +
                        " > " + definedSize);
            }
            ByteBuffer byteBuffer = buffer.toByteBuffer();
            int written = 0;
            while (written < localsize) {
                written += fileChannel.write(byteBuffer);
                fileChannel.force(false);
            }
            size += localsize;
            buffer.readerIndex(buffer.readerIndex() + written);
        }
        if (last) {
            fileChannel.close();
            fileChannel = null;
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
        this.file = file;
        size = file.length();
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
        file.delete();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#get()
     */
    public byte[] get() throws IOException {
        if (file == null) {
            return new byte[0];
        }
        return readFrom(file);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getChannelBuffer()
     */
    public ChannelBuffer getChannelBuffer() throws IOException {
        if (file == null) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        byte[] array = readFrom(file);
        return ChannelBuffers.wrappedBuffer(array);
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
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getString()
     */
    public String getString() throws IOException {
        return getString(HttpCodecUtil.DEFAULT_CHARSET);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#getString(java.lang.String)
     */
    public String getString(String encoding) throws IOException {
        if (file == null) {
            return "";
        }
        if (encoding == null) {
            return getString(HttpCodecUtil.DEFAULT_CHARSET);
        }
        byte[] array = readFrom(file);
        return new String(array, encoding);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.FileUpload#isInMemory()
     */
    public boolean isInMemory() {
        return false;
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
        if (!file.renameTo(dest)) {
            // must copy
            FileInputStream inputStream = new FileInputStream(file);
            FileOutputStream outputStream = new FileOutputStream(dest);
            FileChannel in = inputStream.getChannel();
            FileChannel out = outputStream.getChannel();
            long destsize = in.transferTo(0, size, out);
            if (destsize == size) {
                file.delete();
                file = dest;
                return true;
            } else {
                dest.delete();
                return false;
            }
        }
        file = dest;
        return true;
    }

    /**
     * Utility function
     * @param src
     * @return the array of bytes
     * @throws IOException
     */
    private byte[] readFrom(File src) throws IOException {
        long srcsize = src.length();
        if (srcsize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "File too big to be loaded in memory");
        }
        FileInputStream inputStream = new FileInputStream(src);
        FileChannel fileChannel = inputStream.getChannel();
        byte[] array = new byte[(int) srcsize];
        ByteBuffer byteBuffer = ByteBuffer.wrap(array);
        int read = 0;
        while (read < srcsize) {
            read += fileChannel.read(byteBuffer);
        }
        fileChannel.close();
        return array;
    }

    @Override
    public String toString() {
        return "content-disposition: form-data; name=\"" + name +
                "\"; filename=\"" + filename + "\"\r\n" + "Content-Type: " +
                contentType +
                (charset != null? "; charset=" + charset + "\r\n" : "\r\n") +
                "Content-Length: " + size + "\r\n" + "Completed: " + completed +
                "\r\nIsInMemory: " + isInMemory() + "\r\nRealFile: " +
                file.getAbsolutePath() + " DefaultDeleteAfter: " +
                deleteOnExitTemporaryFile;
    }
}
