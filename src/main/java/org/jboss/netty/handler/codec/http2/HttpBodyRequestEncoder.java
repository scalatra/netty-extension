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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * This encoder will help to encode Response for a FORM as POST or PUT.
 * @author frederic bregier
 *
 */
public class HttpBodyRequestEncoder {
    /**
     * Factory used to create HttpData
     */
    private final HttpDataFactory factory;

    /**
     * Response to encode
     */
    private final HttpResponse response;

    /**
     * Default charset to use
     */
    private final String charset;

    /**
     * Chunked false by default
     */
    private boolean isChunk = false;

    /**
     * Does the last chunk already decoded
     */
    private boolean isLastChunk = false;

    /**
     * Cookies
     */
    private Set<Cookie> cookies;

    /**
     * Attributes for URI
     */
    private Map<String, List<String>> uriAttributes;

    /**
     * Attributes for Header
     */
    private Map<String, List<String>> headerAttributes;

    /**
     * HttpData for Body
     */
    private List<HttpData> bodyListDatas = null;

    /**
    *
    * @param response the response to encode
    * @throws NullPointerException for response
    * @throws ErrorDataEncoderException if the default charset was wrong when decoding or other errors
    */
    public HttpBodyRequestEncoder(HttpResponse response)
            throws ErrorDataEncoderException, NullPointerException {
        this(new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE),
                response, HttpCodecUtil.DEFAULT_CHARSET);
    }

    /**
     *
     * @param factory the factory used to create HttpData
     * @param response the response to encode
     * @throws NullPointerException for response and factory
     * @throws ErrorDataEncoderException if the default charset was wrong when decoding or other errors
     */
    public HttpBodyRequestEncoder(HttpDataFactory factory, HttpResponse response)
            throws ErrorDataEncoderException, NullPointerException {
        this(factory, response, HttpCodecUtil.DEFAULT_CHARSET);
    }

    /**
     *
     * @param factory the factory used to create HttpData
     * @param response the response to encode
     * @param charset the charset to use as default
     * @throws NullPointerException for request or charset or factory
     * @throws ErrorDataEncoderException if the default charset was wrong when decoding or other errors
     */
    public HttpBodyRequestEncoder(HttpDataFactory factory, HttpResponse response,
            String charset) throws ErrorDataEncoderException,
            NullPointerException {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        if (response == null) {
            throw new NullPointerException("response");
        }
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        this.response = response;
        this.charset = charset;
        this.factory = factory;
        // Fill default values
        cookies = new TreeSet<Cookie>();
        uriAttributes = new TreeMap<String, List<String>>();
        headerAttributes = new HashMap<String, List<String>>();
        bodyListDatas = new ArrayList<HttpData>();
        // default mode
        isLastChunk = false;
        isMultipart = false;
    }

    /**
     *
     * @return the Set of Cookies
     */
    public Set<Cookie> getCookies() {
        return cookies;
    }

    /**
     * Set the cookies set
     * @param cookies
     */
    public void setCookies(Set<Cookie> cookies)
            throws NullPointerException {
        if (cookies == null) {
            throw new NullPointerException("cookies");
        }
        this.cookies = cookies;
    }

    /**
     * Add a Cookie
     * @param cookie
     */
    public void addCookie(Cookie cookie) throws NullPointerException {
        if (cookie == null) {
            throw new NullPointerException("cookie");
        }
        cookies.add(cookie);
    }

    /**
     *
     * @return a Map of URI Attributes where the key are their names
     */
    public Map<String, List<String>> getUriAttributes() {
        return uriAttributes;
    }

    /**
     * Set the URI Attributes
     * @param attributes
     */
    public void setUriAttributes(Map<String, List<String>> attributes)
            throws NullPointerException {
        if (attributes == null) {
            throw new NullPointerException("attributes");
        }
        uriAttributes = attributes;
    }

    /**
     * Add the attribute to the URI map
     * @param name
     * @param value
     */
    public void addUriAttributes(String name, String value)
            throws NullPointerException {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (value == null) {
            throw new NullPointerException("value");
        }
        List<String> attributes = uriAttributes.get(name);
        if (attributes == null) {
            attributes = new ArrayList<String>();
            uriAttributes.put(name, attributes);
        }
        attributes.add(value);
    }

    /**
     *
     * @return a Map of Header Attributes where the key are their names
     */
    public Map<String, List<String>> getHeaderAttributes() {
        return headerAttributes;
    }

    /**
     * Set the Header Attributes
     * @param attributes
     */
    public void setHeaderAttributes(Map<String, List<String>> attributes)
            throws NullPointerException {
        if (attributes == null) {
            throw new NullPointerException("attributes");
        }
        headerAttributes = attributes;
    }

    /**
     * Add the attribute to the Header map
     * @param name
     * @param value
     */
    public void addHeaderAttributes(String name, String value)
            throws NullPointerException {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (value == null) {
            throw new NullPointerException("value");
        }
        List<String> attributes = headerAttributes.get(name);
        if (attributes == null) {
            attributes = new ArrayList<String>();
            headerAttributes.put(name, attributes);
        }
        attributes.add(value);
    }

    // Here begins the Multipart specificity
    /**
     * Does this request is a Multipart request
     */
    private boolean isMultipart = false;

    /**
     * Body attributes if not in Multipart
     */
    private final ListIterator<Attribute> bodyIteratorAttributes = null;

    /**
     * If multipart, this is the boundary for the flobal multipart
     */
    private String multipartDataBoundary = null;

    /**
     * If multipart, there could be internal multiparts (mixed) to the global multipart.
     * Only one level is allowed.
     */
    private String multipartMixedBoundary = null;

    /**
     * Current status
     */
    private final MultiPartStatus currentStatus = MultiPartStatus.NOTSTARTED;

    /**
     * The current HttpData that was decoded but still no returned
     */
    private final HttpData currentHttpData = null;

    /**
     * Used in Multipart
     */
    private final Map<String, Attribute> currentFieldAttributes = null;

    /**
     * The current FileUpload that is currently in decode process
     */
    private final FileUpload currentFileUpload = null;

    /**
     * Global Body size
     */
    private long globalBodySize = 0;

    /**
     * states follow
     * NOTSTARTED PREAMBLE (
     *  (HEADERDELIMITER DISPOSITION (FIELD | FILEUPLOAD))*
     *  (HEADERDELIMITER DISPOSITION MIXEDPREAMBLE
     *     (MIXEDDELIMITER MIXEDDISPOSITION MIXEDFILEUPLOAD)+
     *   MIXEDCLOSEDELIMITER)*
     * CLOSEDELIMITER)+ EPILOGUE
     *
     *  First status is: NOSTARTED

        Content-type: multipart/form-data, boundary=AaB03x     => PREAMBLE

        --AaB03x                                               => HEADERDELIMITER
        content-disposition: form-data; name="field1"          => DISPOSITION

        Joe Blow                                               => FIELD
        --AaB03x                                               => HEADERDELIMITER
        content-disposition: form-data; name="pics"            => DISPOSITION
        Content-type: multipart/mixed, boundary=BbC04y

        --BbC04y                                               => MIXEDDELIMITER
        Content-disposition: attachment; filename="file1.txt"  => MIXEDDISPOSITION
        Content-Type: text/plain

        ... contents of file1.txt ...                          => MIXEDFILEUPLOAD
        --BbC04y                                               => MIXEDDELIMITER
        Content-disposition: attachment; filename="file2.gif"  => MIXEDDISPOSITION
        Content-type: image/gif
        Content-Transfer-Encoding: binary

          ...contents of file2.gif...                          => MIXEDFILEUPLOAD
        --BbC04y--                                             => MIXEDCLOSEDELIMITER
        --AaB03x--                                             => CLOSEDELIMITER

       Once CLOSEDELIMITER is found, last status is EPILOGUE
     *
     * @author frederic bregier
     *
     */
    private static enum MultiPartStatus {
        NOTSTARTED,
        PREAMBLE,
        HEADERDELIMITER,
        DISPOSITION,
        FIELD,
        FILEUPLOAD,
        MIXEDPREAMBLE,
        MIXEDDELIMITER,
        MIXEDDISPOSITION,
        MIXEDFILEUPLOAD,
        MIXEDCLOSEDELIMITER,
        CLOSEDELIMITER,
        EPILOGUE;
    }

    /**
     * True if this request is a Multipart request
     * @return True if this request is a Multipart request
     */
    public boolean isMultipart() {
        return isMultipart;
    }

    /**
     * Set the delimiter for Global Part (Data).
     * @param delimiter (may be null so computed)
     */
    public void setDataMultipart(String delimiter) {
        String newdelimiter = delimiter;
        if (delimiter == null) {
            newdelimiter = getNewMultipartDelimiter();
        }
        multipartDataBoundary = "--" + newdelimiter;
        isMultipart = true;
    }

    /**
     * Set the delimiter for Mixed Part (Mixed).
     * @param delimiter (may be null so computed)
     */
    public void setMixedMultipart(String delimiter) {
        String newdelimiter = delimiter;
        if (delimiter == null) {
            newdelimiter = getNewMultipartDelimiter();
        }
        multipartMixedBoundary = "--" + newdelimiter;
    }

    /**
     *
     * @return a newly generated Delimiter (either for DATA or MIXED)
     */
    private String getNewMultipartDelimiter() {
        // construct a generated delimiter
        Random random = new Random();
        return Long.toHexString(random.nextLong()).toLowerCase();
    }

    /**
     * This method returns a List of all HttpData from body part.<br>

     * @return the list of HttpData from Body part
     */
    public List<HttpData> getBodyListAttributes()
            throws ErrorDataEncoderException {
        return bodyListDatas;
    }

    /**
     * Set the Body HttpDatas list
     * @param datas
     * @throws ErrorDataEncoderException
     */
    public void setBodyHttpDatas(List<HttpData> datas)
            throws NullPointerException, ErrorDataEncoderException {
        if (datas == null) {
            throw new NullPointerException("datas");
        }
        globalBodySize = 0;
        bodyListDatas = datas;
        for (HttpData data: datas) {
            globalBodySize = getSizeFromHttpData(data);
            if (data instanceof FileUpload) {
                isMultipart = true;
            }
        }
    }

    private long getSizeFromHttpData(HttpData data) throws ErrorDataEncoderException {
        long localBodySize = 0;
        if (data instanceof FileUpload) {
            FileUpload fileUpload = (FileUpload) data;
            localBodySize = fileUpload.length() +
                    fileUpload.getName().length() +
                    HttpBodyUtil.CONTENT_DISPOSITION.length() + 2 +
                    HttpBodyUtil.FORM_DATA.length() + 2 +
                    HttpBodyUtil.NAME.length() + 1;
            String contentType = fileUpload.getContentType();
            if (contentType != null) {
                localBodySize += contentType.length() + 1 +
                HttpHeaders.Names.CONTENT_TYPE.length();
            }
            contentType = fileUpload.getCharset();
            if (contentType != null) {
                localBodySize += contentType.length() + 3 +
                HttpHeaders.Values.CHARSET.length();
            } else {
                localBodySize += 2 +
                HttpHeaders.Names.CONTENT_TRANSFER_ENCODING.length() +
                        HttpBodyUtil.TransferEncodingMechanism.BINARY.value
                                .length();
            }
        } else {
            Attribute attribute = (Attribute) data;
            try {
                localBodySize = attribute.getName().length() +
                        attribute.getValue().length() + 1;
            } catch (IOException e) {
                throw new ErrorDataEncoderException(e);
            }
        }
        return localBodySize;
    }

    /**
     * Add the HttpData to the Body list
     * @param data
     * @throws ErrorDataEncoderException
     */
    public void addBodyHttpData(HttpData data) throws NullPointerException, ErrorDataEncoderException {
        if (data == null) {
            throw new NullPointerException("data");
        }
        bodyListDatas.add(data);
        globalBodySize = getSizeFromHttpData(data);
        if (data instanceof FileUpload) {
            isMultipart = true;
        }
    }

    public void encodeHeader(boolean serverSide) throws ErrorDataEncoderException {
        //FIXME should we do close ?
        boolean close;
        close = HttpHeaders.Values.CLOSE .equalsIgnoreCase(headerAttributes.get(
                HttpHeaders.Names.CONNECTION).get(0)) ||
                response.getProtocolVersion().equals(HttpVersion.HTTP_1_0) &&
                !HttpHeaders.Values.KEEP_ALIVE
                .equalsIgnoreCase(headerAttributes.get(
                        HttpHeaders.Names.CONNECTION).get(0));
        List<String> cookieValueFromHeader = null;
        List<String> contentTypes = null;
        for (String name: headerAttributes.keySet()) {
            if (name.equalsIgnoreCase(HttpHeaders.Names.COOKIE)) {
                // FIXME set cookie later
                cookieValueFromHeader = headerAttributes.get(name);
                continue;
            } else if (name.equalsIgnoreCase(HttpHeaders.Names.CONTENT_TYPE)) {
                contentTypes = headerAttributes.get(name);
                continue;
            }
            response.setHeader(name, headerAttributes.get(name));
        }
        if (!cookies.isEmpty()) {
            CookieEncoder encoder = new CookieEncoder(serverSide);
            for (Cookie cookie: cookies) {
                encoder.addCookie(cookie);
            }
            if (cookieValueFromHeader != null) {
                CookieDecoder decoder = new CookieDecoder();
                for (String scookie : cookieValueFromHeader) {
                    Set<Cookie> headerCookies = decoder.decode(scookie);
                    if (!headerCookies.isEmpty()) {
                        for (Cookie cookie: headerCookies) {
                            encoder.addCookie(cookie);
                        }
                    }
                }
            }
            response.setHeader("Cookie", encoder.encode());
        }
        if (isMultipart) {
            boolean found = false;
            if (contentTypes != null) {
                for (String contentType: contentTypes) {
                    // "multipart/form-data; boundary=--89421926422648"
                    if (contentType.toLowerCase().startsWith(
                            HttpHeaders.Values.MULTIPART_FORM_DATA + "; " +
                            HttpHeaders.Values.BOUNDARY)) {
                        // OK
                        response.addHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                String value = HttpHeaders.Values.MULTIPART_FORM_DATA + "; " +
                HttpHeaders.Values.BOUNDARY + "=";
                if (multipartDataBoundary == null) {
                    multipartDataBoundary = "--" + getNewMultipartDelimiter();
                }
                value += multipartDataBoundary;
                addHeaderAttributes(HttpHeaders.Names.CONTENT_TYPE, value);
                response.addHeader(HttpHeaders.Names.CONTENT_TYPE, value);
            }
        } else {
            // Not multipart
            boolean found = false;
            if (contentTypes != null) {
                for (String contentType: contentTypes) {
                    // "application/x-www-form-urlencoded"
                    if (contentType.toLowerCase().startsWith(
                            HttpBodyUtil.STANDARD_APPLICATION_FORM)) {
                        // OK
                        response.addHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                addHeaderAttributes(HttpHeaders.Names.CONTENT_TYPE,
                        HttpBodyUtil.STANDARD_APPLICATION_FORM);
                response.addHeader(HttpHeaders.Names.CONTENT_TYPE,
                        HttpBodyUtil.STANDARD_APPLICATION_FORM);
            }
        }
        // Now consider size for chunk or not
        if (!close) {
            // There's no need to add 'Content-Length' header
            // if this is the last response.
            long realSize = globalBodySize;
            if (isMultipart) {
                realSize += bodyListDatas.size() *
                        (multipartDataBoundary.length() + 8);
                // --databoundary + 4 CRLF
            } else {
                realSize += bodyListDatas.size(); // &
            }
            response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, String
                    .valueOf(realSize));
            if (realSize > 8096) {
                isChunk = true;
            }
        }
    }

    /**
     * Encode the next data from the body (multipart or not)
     * @return the next decoded HttpData
     * @throws EndOfDataEncoderException if the end of the decode operation is reached
     * @throws ErrorDataEncoderException if an error occurs during decode
     */
    private HttpData encodeBody() throws EndOfDataEncoderException,
            ErrorDataEncoderException {
        if (isMultipart) {
            //return encodeMultipart(currentStatus);
            return null;//FIXME
        } else {
            if (bodyIteratorAttributes == null) {
                return null;
            }
            if (bodyIteratorAttributes.hasNext()) {
                return bodyIteratorAttributes.next();
            } else {
                return null;
            }
        }
    }

    /**
     * Exception when the body is fully decoded, even if there is still data
     *
     * @author frederic bregier
     *
     */
    public static class EndOfDataEncoderException extends Exception {
        /**
         *
         */
        private static final long serialVersionUID = 1336267941020800769L;

        /**
         *
         */
        public EndOfDataEncoderException() {
            super();
        }
    }

    /**
     * Exception when an error occurs while decoding
     *
     * @author frederic bregier
     *
     */
    public static class ErrorDataEncoderException extends Exception {
        /**
         *
         */
        private static final long serialVersionUID = 5020247425493164465L;

        /**
         *
         */
        public ErrorDataEncoderException() {
            super();
        }

        /**
         * @param arg0
         */
        public ErrorDataEncoderException(String arg0) {
            super(arg0);
        }

        /**
         * @param arg0
         */
        public ErrorDataEncoderException(Throwable arg0) {
            super(arg0);
        }

        /**
         * @param arg0
         * @param arg1
         */
        public ErrorDataEncoderException(String arg0, Throwable arg1) {
            super(arg0, arg1);
        }
    }
}
