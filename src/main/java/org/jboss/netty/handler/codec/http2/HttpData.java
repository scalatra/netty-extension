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
import java.io.InputStream;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Extended interface for InterfaceHttpData
 * @author frederic bregier
 *
 */
public interface HttpData extends InterfaceHttpData {
    /**
     * Set the content from the ChannelBuffer (erase any previous data)
     * @param buffer must be not null
     * @exception IOException
     */
    public void setContent(ChannelBuffer buffer) throws IOException;

    /**
     * Add the content from the ChannelBuffer
     * @param buffer must be not null except if last is set to False
     * @param last True of the buffer is the last one
     * @exception IOException
     */
    public void addContent(ChannelBuffer buffer, boolean last)
            throws IOException;

    /**
     * Set the content from the file (erase any previous data)
     * @param file must be not null
     * @exception IOException
     */
    public void setContent(File file) throws IOException;

    /**
     * Set the content from the inputStream (erase any previous data)
     * @param inputStream must be not null
     * @exception IOException
     */
    public void setContent(InputStream inputStream) throws IOException;

    /**
     *
     * @return True if the InterfaceHttpData is completed (all data are stored)
     */
    public boolean isCompleted();

    /**
     * Returns the size in byte of the InterfaceHttpData
     * @return the size of the InterfaceHttpData
     */
    public long length();

    /**
     * Deletes the underlying storage for a file item,
     * including deleting any associated temporary disk file.
     */
    public void delete();

    /**
     * Returns the contents of the file item as an array of bytes.
     * @return the contents of the file item as an array of bytes.
     * @exception IOException
     */
    public byte[] get() throws IOException;

    /**
     * Returns the content of the file item as a ChannelBuffer
     * @return the content of the file item as a ChannelBuffer
     * @throws IOException
     */
    public ChannelBuffer getChannelBuffer() throws IOException;

    /**
     * Returns a ChannelBuffer for the content from the current position with at most length read
     * bytes, increasing the current position of the Bytes read. Once it arrives at the end,
     * it returns an EMPTY_BUFFER and it resets the current position to 0.
     * @param length
     * @return a ChannelBuffer for the content from the current position or
     * an EMPTY_BUFFER if there is no more data to return
     * @throws IOException
     */
    public ChannelBuffer getChunk(int length) throws IOException;

    /**
     * Returns the contents of the file item as a String, using the default character encoding.
     * @return the contents of the file item as a String, using the default character encoding.
     * @exception IOException
     */
    public String getString() throws IOException;

    /**
     * Returns the contents of the file item as a String, using the specified charset.
     * @param encoding the charset to use
     * @return the contents of the file item as a String, using the specified charset.
     * @exception IOException
     */
    public String getString(String encoding) throws IOException;

    /**
     * Set the Charset passed by the browser if defined
     * @param charset Charset to set - must be not null
     */
    public void setCharset(String charset);

    /**
     * Returns the Charset passed by the browser or null if not defined.
     * @return the Charset passed by the browser or null if not defined.
     */
    public String getCharset();

    /**
     * A convenience method to write an uploaded item to disk.
     * If a previous one exists, it will be deleted.
     * Once this method is called, if successful, the new file will be out of the cleaner
     * of the factory that creates the original InterfaceHttpData object.
     * @param dest destination file - must be not null
     * @return True if the write is successful
     * @exception IOException
     */
    public boolean renameTo(File dest) throws IOException;

    /**
     * Provides a hint as to whether or not the file contents will be read from memory.
     * @return True if the file contents is in memory.
     */
    public boolean isInMemory();

    /**
     *
     * @return the associated File if this data is represented in a file
     * @exception IOException if this data is not represented by a file
     */
    public File getFile() throws IOException;

}
