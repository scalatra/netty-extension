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
 * Abstract Disk HttpData implementation
 *
 * @author frederic bregier
 *
 */
public abstract class AbstractDiskHttpData extends AbstractHttpData implements FileHttpData {

    protected File file = null;

    private boolean isRenamed = false;

    private FileChannel fileChannel = null;

    public AbstractDiskHttpData(String name, String charset, long size)
            throws NullPointerException, IllegalArgumentException {
        super(name, charset, size);
    }

    /**
     *
     * @return the real DiskFilename (basename)
     */
    protected abstract String getDiskFilename();
    /**
     *
     * @return the default prefix
     */
    protected abstract String getPrefix();
    /**
     *
     * @return the default base Directory
     */
    protected abstract String getBaseDirectory();
    /**
     *
     * @return the default postfix
     */
    protected abstract String getPostfix();
    /**
     *
     * @return True if the file should be deleted on Exit by default
     */
    protected abstract boolean deleteOnExit();

    /**
     *
     * @return a new Temp File from getDiskFilename(), default prefix, postfix and baseDirectory
     * @throws IOException
     */
    private File tempFile() throws IOException {
        String newpostfix = null;
        String diskFilename = getDiskFilename();
        if (diskFilename != null) {
            newpostfix = "_" + diskFilename;
        } else {
            newpostfix = getPostfix();
        }
        File tmpFile;
        if (getBaseDirectory() == null) {
            // create a temporary file
            tmpFile = File.createTempFile(getPrefix(), newpostfix);
        } else {
            tmpFile = File.createTempFile(getPrefix(), newpostfix, new File(
                    getBaseDirectory()));
        }
        if (deleteOnExit()) {
            tmpFile.deleteOnExit();
        }
        return tmpFile;
    }

    public void setContent(ChannelBuffer buffer) throws IOException {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        size = buffer.readableBytes();
        if (definedSize > 0 && definedSize < size) {
            throw new IOException("Out of size: " + size + " > " + definedSize);
        }
        if (file == null) {
            file = tempFile();
        }
        if (buffer.readableBytes() == 0) {
            // empty file
            file.createNewFile();
            return;
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

    public void addContent(ChannelBuffer buffer, boolean last)
            throws IOException {
        if (buffer != null) {
            int localsize = buffer.readableBytes();
            if (definedSize > 0 && definedSize < size + localsize) {
                throw new IOException("Out of size: " + (size + localsize) +
                        " > " + definedSize);
            }
            ByteBuffer byteBuffer = buffer.toByteBuffer();
            int written = 0;
            if (file == null) {
                file = tempFile();
            }
            if (fileChannel == null) {
                FileOutputStream outputStream = new FileOutputStream(file);
                fileChannel = outputStream.getChannel();
            }
            while (written < localsize) {
                written += fileChannel.write(byteBuffer);
                fileChannel.force(false);
            }
            size += localsize;
            buffer.readerIndex(buffer.readerIndex() + written);
        }
        if (last) {
            if (file == null) {
                file = tempFile();
            }
            if (fileChannel == null) {
                FileOutputStream outputStream = new FileOutputStream(file);
                fileChannel = outputStream.getChannel();
            }
            fileChannel.close();
            fileChannel = null;
            completed = true;
        } else {
            if (buffer == null) {
                throw new NullPointerException("buffer");
            }
        }
    }

    public void setContent(File file) throws IOException {
        if (this.file != null) {
            delete();
        }
        this.file = file;
        size = file.length();
        isRenamed = true;
        completed = true;
    }

    public void delete() {
        if (! isRenamed) {
            file.delete();
        }
    }

    public byte[] get() throws IOException {
        if (file == null) {
            return new byte[0];
        }
        return readFrom(file);
    }

    public ChannelBuffer getChannelBuffer() throws IOException {
        if (file == null) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        byte[] array = readFrom(file);
        return ChannelBuffers.wrappedBuffer(array);
    }

    public String getString() throws IOException {
        return getString(HttpCodecUtil.DEFAULT_CHARSET);
    }

    public String getString(String encoding) throws IOException {
        if (file == null) {
            return "";
        }
        if (encoding == null) {
            byte[] array = readFrom(file);
            return new String(array, HttpCodecUtil.DEFAULT_CHARSET);
        }
        byte[] array = readFrom(file);
        return new String(array, encoding);
    }

    public boolean isInMemory() {
        return false;
    }

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
                isRenamed = true;
                return true;
            } else {
                dest.delete();
                return false;
            }
        }
        file = dest;
        isRenamed = true;
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
}
