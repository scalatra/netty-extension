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
 * Interface to enable creation of HttpData objects
 *
 * @author frederic bregier
 *
 */
public interface HttpDataFactory {
    /**
     *
     * @param name
     * @param value
     * @return a new Attribute
     * @throws NullPointerException
     * @throws IllegalArgumentException
     */
    public Attribute createAttribute(String name, String value)
            throws NullPointerException, IllegalArgumentException;

    /**
     *
     * @param name
     * @param filename
     * @param contentType
     * @param charset
     * @param size the size of the Uploaded file
     * @return a new FileUpload
     */
    public FileUpload createFileUpload(String name, String filename,
            String contentType, String contentTransferEncoding, String charset,
            long size) throws NullPointerException, IllegalArgumentException;
}
