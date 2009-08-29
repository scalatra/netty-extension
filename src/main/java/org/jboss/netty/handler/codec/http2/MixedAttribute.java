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

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Mixed implementation using both in Memory and in File with a limit of size
 * @author frederic bregier
 *
 */
public class MixedAttribute implements Attribute {
    private Attribute attribute = null;

    private long limitSize = 0;

    public MixedAttribute(String name,
            long limitSize) throws NullPointerException,
            IllegalArgumentException {
        this.limitSize = limitSize;
        attribute = new MemoryAttribute(name);
    }

    public MixedAttribute(String name, String value,
            long limitSize) throws NullPointerException,
            IllegalArgumentException {
        this.limitSize = limitSize;
        if (value.length() > this.limitSize) {
            try {
                attribute = new DiskAttribute(name, value);
            } catch (IOException e) {
                // revert to Memory mode
                try {
                    attribute = new MemoryAttribute(name, value);
                } catch (IOException e1) {
                    throw new IllegalArgumentException(e);
                }
            }
        } else {
            try {
                attribute = new MemoryAttribute(name, value);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    public void addContent(ChannelBuffer buffer, boolean last)
            throws IOException {
        if (attribute instanceof MemoryAttribute) {
            if (attribute.length() + buffer.readableBytes() > limitSize) {
                DiskAttribute diskAttribute = new DiskAttribute(attribute
                        .getName());
                diskAttribute.addContent(((MemoryAttribute) attribute)
                        .getChannelBuffer(), false);
                attribute = diskAttribute;
            }
        }
        attribute.addContent(buffer, last);
    }

    public void delete() {
        attribute.delete();
    }

    public byte[] get() throws IOException {
        return attribute.get();
    }

    public ChannelBuffer getChannelBuffer() throws IOException {
        return attribute.getChannelBuffer();
    }

    public String getCharset() {
        return attribute.getCharset();
    }

    public String getString() throws IOException {
        return attribute.getString();
    }

    public String getString(String encoding) throws IOException {
        return attribute.getString(encoding);
    }

    public boolean isCompleted() {
        return attribute.isCompleted();
    }

    public boolean isInMemory() {
        return attribute.isInMemory();
    }

    public long length() {
        return attribute.length();
    }

    public boolean renameTo(File dest) throws IOException {
        return attribute.renameTo(dest);
    }

    public void setCharset(String charset) {
        attribute.setCharset(charset);
    }

    public void setContent(ChannelBuffer buffer) throws IOException {
        if (buffer.readableBytes() > limitSize) {
            if (attribute instanceof MemoryAttribute) {
                // change to Disk
                DiskAttribute diskAttribute = new DiskAttribute(attribute
                        .getName());
                attribute = diskAttribute;
            }
        }
        attribute.setContent(buffer);
    }

    public void setContent(File file) throws IOException {
        if (file.length() > limitSize) {
            if (attribute instanceof MemoryAttribute) {
                // change to Disk
                DiskAttribute diskAttribute = new DiskAttribute(attribute
                        .getName());
                attribute = diskAttribute;
            }
        }
        attribute.setContent(file);
    }

    public HttpDataType getHttpDataType() {
        return attribute.getHttpDataType();
    }

    public String getName() {
        return attribute.getName();
    }

    public int compareTo(HttpData o) {
        return attribute.compareTo(o);
    }

    @Override
    public String toString() {
        return "Mixed: " + attribute.toString();
    }

    public String getValue() throws IOException {
        return attribute.getValue();
    }

    public void setValue(String value) throws IOException {
        attribute.setValue(value);
    }
}
