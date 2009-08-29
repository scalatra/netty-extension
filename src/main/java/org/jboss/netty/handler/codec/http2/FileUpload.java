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
 * FileUpload interface that could be in memory, on temporary file or any other implementations.
 *
 * Most methods are inspired from java.io.File API.
 *
 * @author frederic bregier
 *
 */
public interface FileUpload extends FileHttpData {
    /**
     * Returns the original filename in the client's filesystem,
     * as provided by the browser (or other client software).
     * @return the original filename
     */
    public String getFilename();

    /**
     * Set the original filename
     * @param filename
     */
    public void setFilename(String filename);

    /**
     * Set the Content Type passed by the browser if defined
     * @param contentType Content Type to set - must be not null
     */
    public void setContentType(String contentType);

    /**
     * Returns the content type passed by the browser or null if not defined.
     * @return the content type passed by the browser or null if not defined.
     */
    public String getContentType();

    /**
     * Set the Content-Transfer-Encoding type from String as 7bit, 8bit or binary
     * @param contentTransferEncoding
     */
    public void setContentTransferEncoding(String contentTransferEncoding);

    /**
     * Returns the Content-Transfer-Encoding
     * @return the Content-Transfer-Encoding
     */
    public String getContentTransferEncoding();
}
