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

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Shared Static object between HttpMessageDecoder, HttpBodyRequestDecoder and HttpBodyRequestEncoder
 *
 * @author frederic bregier
 *
 */
public class HttpBodyUtil {
    /**
     * HTTP content disposition header name.
     */
    static final String CONTENT_DISPOSITION = "Content-Disposition";

    static final String NAME = "name";

    static final String FILENAME = "filename";

    /**
     * Content-disposition value for form data.
     */
    static final String FORM_DATA = "form-data";

    /**
     * Content-disposition value for file attachment.
     */
    static final String ATTACHMENT = "attachment";

    /**
     * HTTP content type body attribute for multiple uploads.
     */
    static final String MULTIPART_MIXED = "multipart/mixed";

    /**
     * Standard HTTP content type header when not multipart forms.
     */
    static final String STANDARD_APPLICATION_FORM = "application/x-www-form-urlencoded";

    /**
     * Charset for 8BIT
     */
    static final String ISO_8859_1 = "ISO-8859-1";

    /**
     * Charset for 7BIT
     */
    static final String US_ASCII = "US-ASCII";

    /**
     * Allowed mechanism for multipart
     * mechanism := "7bit"
                  / "8bit"
                  / "binary"
       Not allowed: "quoted-printable"
                  / "base64"
     */
    static enum TransferEncodingMechanism {
        /**
         * Default encoding
         */
        BIT7("7bit"),
        /**
         * Short lines but not in ASCII - no encoding
         */
        BIT8("8bit"),
        /**
         * Could be long text not in ASCII - no encoding
         */
        BINARY;

        public String value;

        private TransferEncodingMechanism(String value) {
            this.value = value;
        }

        private TransferEncodingMechanism() {
            value = name();
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private HttpBodyUtil() {
        super();
    }

    //Some commons methods between HttpBodyRequestDecoder and HttpMessageDecoder
    /**
     * Skip control Characters
     * @param buffer
     */
    static void skipControlCharacters(ChannelBuffer buffer) {
        for (;;) {
            char c = (char) buffer.readUnsignedByte();
            if (!Character.isISOControl(c) && !Character.isWhitespace(c)) {
                buffer.readerIndex(buffer.readerIndex() - 1);
                break;
            }
        }
    }

    /**
     * Find the first non whitespace
     * @param sb
     * @param offset
     * @return the rank of the first non whitespace
     */
    static int findNonWhitespace(String sb, int offset) {
        int result;
        for (result = offset; result < sb.length(); result ++) {
            if (!Character.isWhitespace(sb.charAt(result))) {
                break;
            }
        }
        return result;
    }

    /**
     * Find the first whitespace
     * @param sb
     * @param offset
     * @return the rank of the first whitespace
     */
    static int findWhitespace(String sb, int offset) {
        int result;
        for (result = offset; result < sb.length(); result ++) {
            if (Character.isWhitespace(sb.charAt(result))) {
                break;
            }
        }
        return result;
    }

    /**
     * Find the end of String
     * @param sb
     * @return the rank of the end of string
     */
    static int findEndOfString(String sb) {
        int result;
        for (result = sb.length(); result > 0; result --) {
            if (!Character.isWhitespace(sb.charAt(result - 1))) {
                break;
            }
        }
        return result;
    }

}
