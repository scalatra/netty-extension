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

/**
 * Default factory giving DefaultAttribute and FileUpload according to constructor
 *
 * FileUpload could be MemoryFileUpload, DiskFileUpload or MixteFileUpload
 * according to the constructor.
 *
 * @author frederic bregier
 *
 */
public class DefaultHttpDataFactory implements HttpDataFactory {
    /**
     * Proposed default MINSIZE as 16 KB.
     */
    public static long MINSIZE = 0x4000;

    private boolean useDisk = false;

    private boolean checkSize = false;

    private long minSize = 0L;

    /**
     * FileUpload will be always in memory
     */
    public DefaultHttpDataFactory() {
        // empty constructor
        useDisk = false;
        checkSize = false;
    }

    /**
     * FileUpload will be always on DiskFileUpload if useDisk is True, else always in Memory if False
     * @param useDisk
     */
    public DefaultHttpDataFactory(boolean useDisk) {
        this.useDisk = useDisk;
        checkSize = false;
    }

    /**
     * FileUpload will be on Disk if the size of the file is greater than minSize, else it
     * will be in memory. The type will be MixteFileUpload
     * @param minSize
     */
    public DefaultHttpDataFactory(long minSize) {
        useDisk = false;
        checkSize = true;
        this.minSize = minSize;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.HttpDataFactory#createAttribute(java.lang.String, java.lang.String)
     */
    public Attribute createAttribute(String name, String value)
            throws NullPointerException, IllegalArgumentException {
        return new DefaultAttribute(name, value);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.HttpDataFactory#createFileUpload(java.lang.String, java.lang.String, java.lang.String)
     */
    public FileUpload createFileUpload(String name, String filename,
            String contentType, String contentTransferEncoding, String charset,
            long size) throws NullPointerException, IllegalArgumentException {
        if (useDisk) {
            return new DiskFileUpload(name, filename, contentType,
                    contentTransferEncoding, charset, size);
        } else if (checkSize) {
            return new MixteFileUpload(name, filename, contentType,
                    contentTransferEncoding, charset, size, minSize);
        }
        return new MemoryFileUpload(name, filename, contentType,
                contentTransferEncoding, charset, size);
    }

}
