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

import java.io.IOException;

import org.jboss.netty.buffer.ChannelBuffers;

/**
 * Memory implementation of Attributes
 * @author frederic bregier
 *
 */
public class MemoryAttribute extends AbstractMemoryHttpData implements Attribute {

    public MemoryAttribute(String name) {
        super(name, HttpCodecUtil.DEFAULT_CHARSET, 0);
    }
    /**
     *
     * @param name
     * @param value
     * @throws NullPointerException
     * @throws IllegalArgumentException
     * @throws IOException
     */
    public MemoryAttribute(String name, String value)
            throws NullPointerException, IllegalArgumentException, IOException {
        super(name, HttpCodecUtil.DEFAULT_CHARSET, value.length());
        setValue(value);
    }

    public HttpDataType getHttpDataType() {
        return HttpDataType.Attribute;
    }

    public String getValue() {
        return getChannelBuffer().toString(charset);
    }

    public void setValue(String value) throws IOException {
        if (value == null) {
            throw new NullPointerException("value");
        }
        byte [] bytes = value.getBytes(charset);
        this.setContent(ChannelBuffers.wrappedBuffer(bytes));
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
        if (!(arg0 instanceof Attribute)) {
            throw new ClassCastException("Cannot compare " + getHttpDataType() +
                    " with " + arg0.getHttpDataType());
        }
        return compareTo((Attribute) arg0);
    }

    public int compareTo(Attribute o) {
        return getName().compareToIgnoreCase(o.getName());
    }

    @Override
    public String toString() {
        return getName() + "=" + getValue();
    }

}
