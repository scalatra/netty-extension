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

import java.util.ArrayList;
import java.util.List;

/**
 * This Attribute is only for Encoder use to insert special command between object if needed
 * (like Multipart Mixed mode)
 *
 * @author frederic bregier
 *
 */
public class InternalAttribute implements HttpData {
    protected List<String> value = new ArrayList<String>();

    /**
     *
     * @throws NullPointerException
     * @throws IllegalArgumentException
     * @throws IOException
     */
    public InternalAttribute() {
    }

    public HttpDataType getHttpDataType() {
        return HttpDataType.InternalAttribute;
    }

    public List<String> getValue() {
        return value;
    }

    public void addValue(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        this.value.add(value);
    }

    public void addValue(String value, int rank) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        this.value.add(rank, value);
    }

    public void setValue(String value, int rank) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        this.value.set(rank, value);
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
        if (!(arg0 instanceof InternalAttribute)) {
            throw new ClassCastException("Cannot compare " + getHttpDataType() +
                    " with " + arg0.getHttpDataType());
        }
        return compareTo((InternalAttribute) arg0);
    }

    public int compareTo(InternalAttribute o) {
        return getName().compareToIgnoreCase(o.getName());
    }

    public int size() {
        int size = 0;
        for (String elt : value) {
            size += elt.length();
        }
        return size;
    }
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (String elt : value) {
            result.append(elt);
        }
        return result.toString();
    }
    @Override
    public String getName() {
        return "InternalAttribute";
    }
}
