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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jboss.netty.buffer.AggregateChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * Abstract Memory HttpData implementation
 *
 * @author frederic bregier
 *
 */
public abstract class AbstractMemoryHttpData extends AbstractHttpData implements HttpData {

    private ChannelBuffer channelBuffer = null;

    private int chunkPosition = 0;

    protected boolean isRenamed = false;

    public AbstractMemoryHttpData(String name, String charset, long size)
            throws NullPointerException, IllegalArgumentException {
        super(name, charset, size);
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

    public void setContent(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new NullPointerException("inputStream");
        }
        ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();
        byte[] bytes = new byte[4096*4];
        int read = inputStream.read(bytes);
        int written = 0;
        while (read > 0) {
            buffer.writeBytes(bytes);
            written += read;
            read = inputStream.read(bytes);
        }
        size = written;
        if (definedSize > 0 && definedSize < size) {
            throw new IOException("Out of size: " + size + " > " + definedSize);
        }
        channelBuffer = buffer;
        completed = true;
    }

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
        byteBuffer.flip();
        channelBuffer = ChannelBuffers.wrappedBuffer(byteBuffer);
        size = newsize;
        completed = true;
    }

    public void delete() {
        // nothing to do
    }

    public byte[] get() {
        if (channelBuffer == null) {
            return new byte[0];
        }
        byte[] array = new byte[channelBuffer.readableBytes()];
        channelBuffer.getBytes(channelBuffer.readerIndex(), array);
        return array;
    }

    public String getString() {
        return getString(HttpCodecUtil.DEFAULT_CHARSET);
    }

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

    public ChannelBuffer getChunk(int length) throws IOException {
        if (channelBuffer == null || length == 0 || channelBuffer.readableBytes() == 0) {
            chunkPosition = 0;
            return ChannelBuffers.EMPTY_BUFFER;
        }
        int sizeLeft = channelBuffer.readableBytes() - chunkPosition;
        if (sizeLeft == 0) {
            chunkPosition = 0;
            return ChannelBuffers.EMPTY_BUFFER;
        }
        int sliceLength = length;
        if (sizeLeft < length) {
            sliceLength = sizeLeft;
        }
        ChannelBuffer chunk = channelBuffer.slice(chunkPosition, sliceLength);
        chunkPosition += sliceLength;
        return chunk;
    }

    public boolean isInMemory() {
        return true;
    }

    public boolean renameTo(File dest) throws IOException {
        if (dest == null) {
            throw new NullPointerException("dest");
        }
        if (channelBuffer == null) {
            // empty file
            dest.createNewFile();
            isRenamed = true;
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
        isRenamed = true;
        return written == length;
    }
    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.HttpData#getFile()
     */
    @Override
    public File getFile() throws IOException {
        throw new IOException("Not represented by a file");
    }
}
