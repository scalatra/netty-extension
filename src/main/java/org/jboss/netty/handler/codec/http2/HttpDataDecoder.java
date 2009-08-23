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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.netty.buffer.AggregateChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffer;

/**
 * This decoder will decode request and can handle POST or PUT BODY.
 * @author frederic bregier
 *
 */
public class HttpDataDecoder {
    /**
     * Factory used to create HttpData
     */
    private final HttpDataFactory factory;

    /**
     * Request to decode
     */
    private final HttpRequest request;

    /**
     * Default charset to use
     */
    private final String charset;

    /**
     * Does request have a body to decode
     */
    private boolean bodyToDecode = false;

    /**
     * Does the last chunk already received
     */
    private boolean isLastChunk = false;

    /**
     * Cookies
     */
    private final Map<String, Cookie> cookies;

    /**
     * Attributes from URI
     */
    private final Map<String, List<Attribute>> uriAttributes;

    /**
     * Attributes from Header
     */
    private final Map<String, List<Attribute>> headerAttributes;

    /**
     * Attributes from Body, only valid if not Multipart (in list format)
     */
    private List<Attribute> bodyListAttributes = null;

    /**
     * Attributes from Body, only valid if not Multipart (in map format)
     */
    private Map<String, List<Attribute>> bodyMapAttributes = null;

    /**
    *
    * @param request the request to decode
    * @throws NullPointerException for request
    * @throws NotEnoughDataDecoderException Need more chunks and
    *   reset the readerInder to the previous value
    * @throws UnappropriatedMethodDecodeDataException if the request is not a PUT or POST request
    *          or if an error occurs
    * @throws ErrorDataDecoderException if the default charset was wrong when decoding or other errors
    */
    public HttpDataDecoder(HttpRequest request)
            throws NotEnoughDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException, NullPointerException {
        this(new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE),
                request, HttpCodecUtil.DEFAULT_CHARSET);
    }

    /**
     *
     * @param factory the factory used to create HttpData
     * @param request the request to decode
     * @throws NullPointerException for request or factory
     * @throws NotEnoughDataDecoderException Need more chunks and
     *   reset the readerInder to the previous value
     * @throws UnappropriatedMethodDecodeDataException if the request is not a PUT or POST request
     *          or if an error occurs
     * @throws ErrorDataDecoderException if the default charset was wrong when decoding or other errors
     */
    public HttpDataDecoder(HttpDataFactory factory, HttpRequest request)
            throws NotEnoughDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException, NullPointerException {
        this(factory, request, HttpCodecUtil.DEFAULT_CHARSET);
    }

    /**
     *
     * @param factory the factory used to create HttpData
     * @param request the request to decode
     * @param charset the charset to use as default
     * @throws NullPointerException for request or charset or factory
     * @throws NotEnoughDataDecoderException Need more chunks and
     *   reset the readerInder to the previous value
     * @throws UnappropriatedMethodDecodeDataException if the request is not a PUT or POST request
     *          or if an error occurs
     * @throws ErrorDataDecoderException if the default charset was wrong when decoding or other errors
     */
    public HttpDataDecoder(HttpDataFactory factory, HttpRequest request,
            String charset) throws NotEnoughDataDecoderException,
            ErrorDataDecoderException, UnappropriatedMethodDecodeDataException,
            NullPointerException {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        if (request == null) {
            throw new NullPointerException("request");
        }
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        this.request = request;
        this.charset = charset;
        this.factory = factory;
        // Fill default values
        cookies = new HashMap<String, Cookie>();
        setCookies();
        uriAttributes = new HashMap<String, List<Attribute>>();
        setUriAttributes();
        headerAttributes = new HashMap<String, List<Attribute>>();
        setHeaderAttributes();
        if (headerAttributes.containsKey(CONTENT_TYPE)) {
            checkMultipart(headerAttributes.get(CONTENT_TYPE).get(0).getValue());
            if (isMultipart) {
                bodyToDecode = true;
            }
        } else {
            isMultipart = false;
            bodyToDecode = false;
        }
        HttpMethod method = request.getMethod();
        if (method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT)) {
            bodyToDecode = true;
            if (!request.isChunked()) {
                undecodedChunk = request.getContent();
                isLastChunk = true;
            }
        }
        // Now check if not multipart and not chunked
        if (!isMultipart && isLastChunk) {
            // can decode Body
            setBodyAttributes();
        }
    }

    /**
     *
     * @return the Map of Cookies where the key are their names
     */
    public Map<String, Cookie> getCookies() {
        return cookies;
    }

    /**
     * Fill the cookies map
     */
    private void setCookies() {
        // Really decode the cookies
        CookieDecoder decoder = new CookieDecoder();
        List<String> list = request.getHeaders(HttpHeaders.Names.COOKIE);
        for (String scookie: list) {
            Set<Cookie> set = decoder.decode(scookie);
            for (Cookie cookie: set) {
                String name = cookie.getName();
                cookies.put(name.toLowerCase(), cookie);
            }
        }
    }

    /**
     *
     * @param name
     * @return the cookie associated with the given name (ignore case)
     */
    public Cookie getCookie(String name) {
        return cookies.get(name.toLowerCase());
    }

    /**
     *
     * @return a Map of URI Attributes where the key are their names
     */
    public Map<String, List<Attribute>> getUriAttributes() {
        return uriAttributes;
    }

    /**
     * Fill the URI Attributes
     * @throws ErrorDataDecoderException
     */
    private void setUriAttributes() throws ErrorDataDecoderException {
        // really decode the URI
        decodeUriAttribute(uriAttributes, request.getUri());
    }

    /**
     *
     * @param name
     * @return the list of URI attributes with the given name (ignore case)
     */
    public List<Attribute> getUriAttributes(String name) {
        return uriAttributes.get(name.toLowerCase());
    }

    /**
     *
     * @param name
     * @return the first URI attributes with the given name (ignore case)
     */
    public Attribute getUriAttribute(String name) {
        List<Attribute> list = uriAttributes.get(name.toLowerCase());
        if (list.isEmpty()) {
            return null;
        } else {
            return list.get(0);
        }
    }

    /**
    *
    * @param name
    * @return the value of the first URI attributes with the given name (ignore case)
    */
    public String getUriAttributeValue(String name) {
        List<Attribute> list = uriAttributes.get(name.toLowerCase());
        if (list.isEmpty()) {
            return null;
        } else {
            return list.get(0).getValue();
        }
    }

    /**
     *
     * @return a Map of Header Attributes (except Cookie) where the key are their names
     */
    public Map<String, List<Attribute>> getHeaderAttributes() {
        return headerAttributes;
    }

    /**
     * Fill the Header Attributes (except Cookie)
     * @throws ErrorDataDecoderException
     */
    private void setHeaderAttributes() throws ErrorDataDecoderException {
        // Construction from already decoded Request except COOKIE
        Set<String> headers = request.getHeaderNames();
        for (String name: headers) {
            if (!name.equalsIgnoreCase(HttpHeaders.Names.COOKIE)) {
                List<String> slist = request.getHeaders(name);
                List<Attribute> lattribute = new ArrayList<Attribute>(slist
                        .size());
                for (String value: slist) {
                    Attribute attribute;
                    try {
                        attribute = factory.createAttribute(name, value);
                    } catch (Exception e) {
                        throw new ErrorDataDecoderException(e);
                    }
                    lattribute.add(attribute);
                }
                headerAttributes.put(name.toLowerCase(), lattribute);
            }
        }
    }

    /**
    *
    * @param name
    * @return the list of Header attributes with the given name (ignore case)
    */
    public List<Attribute> getHeaderAttributes(String name) {
        return headerAttributes.get(name.toLowerCase());
    }

    /**
     *
     * @param name
     * @return the first Header attributes with the given name (ignore case)
     */
    public Attribute getHeaderAttribute(String name) {
        List<Attribute> list = headerAttributes.get(name.toLowerCase());
        if (list.isEmpty()) {
            return null;
        } else {
            return list.get(0);
        }
    }

    /**
    *
    * @param name
    * @return the value of the first Header attributes with the given name (ignore case)
    */
    public String getHeaderAttributeValue(String name) {
        List<Attribute> list = headerAttributes.get(name.toLowerCase());
        if (list.isEmpty()) {
            return null;
        } else {
            return list.get(0).getValue();
        }
    }

    // Here begins the Multipart specificity
    /**
     * The current channelBuffer
     */
    private ChannelBuffer undecodedChunk = null;

    /**
     * Does this request is a Multipart request
     */
    private boolean isMultipart = false;

    /**
     * Body attributes if not in Multipart
     */
    private ListIterator<Attribute> bodyIteratorAttributes = null;

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
    private MultiPartStatus currentStatus = MultiPartStatus.NOTSTARTED;

    /**
     * The current HttpData that was decoded but still no returned
     */
    private HttpData currentHttpData = null;

    /**
     * Used in Multipart
     */
    private Map<String, Attribute> currentFieldAttributes = null;

    /**
     * The current FileUpload that is currently in decode process
     */
    private FileUpload currentFileUpload = null;

    /**
     * Keep all FileUpload until cleanFileUploads() is called.
     */
    private List<FileUpload> fileUploadsToDelete = null;

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
     * HTTP content type header name.
     */
    static final String CONTENT_TYPE = "content-type";

    static final String BOUNDARY = "boundary";

    static final String CHARSET = "charset";

    /**
     * HTTP content disposition header name.
     */
    static final String CONTENT_DISPOSITION = "content-disposition";

    static final String NAME = "name";

    static final String FILENAME = "filename";

    /**
     * HTTP content length header name.
     */
    static final String CONTENT_LENGTH = "content-length";

    /**
     * Content-disposition value for form data.
     */
    static final String FORM_DATA = "form-data";

    /**
     * Content-disposition value for file attachment.
     */
    static final String ATTACHMENT = "attachment";

    /**
     * HTTP content type header for multipart forms.
     */
    static final String MULTIPART_FORM_DATA = "multipart/form-data";

    /**
     * HTTP content type header for multiple uploads.
     */
    static final String MULTIPART_MIXED = "multipart/mixed";

    /**
     * HTTP content transfer encoding header name.
     */
    static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";

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

    /**
     * Initialized the internals from a new chunk
     * @param chunk the new received chunk
     * @throws UnappropriatedMethodDecodeDataException if the request is not a PUT or POST request
     */
    public void newChunk(HttpChunk chunk)
            throws UnappropriatedMethodDecodeDataException {
        if (!bodyToDecode) {
            throw new UnappropriatedMethodDecodeDataException(
                    "Only Post or Put request are supported for Body decode");
        }
        ChannelBuffer chunked = chunk.getContent();
        if (undecodedChunk == null) {
            undecodedChunk = chunked;
        } else {
            //undecodedChunk = ChannelBuffers.wrappedBuffer(undecodedChunk, chunk.getContent());
            // less memory usage
            undecodedChunk = AggregateChannelBuffer.wrappedCheckedBuffer(
                    undecodedChunk, chunked);
        }
        if (chunk.isLast()) {
            isLastChunk = true;
        }
    }

    /**
     * True if this request is a Multipart request
     * @return True if this request is a Multipart request
     */
    public boolean isMultipart() {
        return isMultipart;
    }

    /**
     * Set first ChannelBuffer either from the request (non chunked), or from the first chunk
     * of a chunked request.
     * @param contentType
     * @throws ErrorDataDecoderException
     */
    private void checkMultipart(String contentType)
            throws ErrorDataDecoderException {
        // Check if Post using "multipart/form-data; boundary=--89421926422648"
        String[] initialLine = splitInitialMultipartLine(contentType);
        if (initialLine[0].toLowerCase().startsWith(MULTIPART_FORM_DATA) &&
                initialLine[1].toLowerCase().startsWith(BOUNDARY)) {
            String[] boundary = initialLine[1].split("=");
            if (boundary.length != 2) {
                throw new ErrorDataDecoderException("Needs a boundary value");
            }
            multipartDataBoundary = "--" + boundary[1];
            isMultipart = true;
            currentStatus = MultiPartStatus.HEADERDELIMITER;
        } else {
            isMultipart = false;
        }
    }

    /**
     * This method returns a map of all Attributes from POST or PUT method without Multipart
     * and once all chunks are received and set using newChunk() method.<br>
     *
     * It does not touch the internal buffer, such that it allows however
     * to use however the hasNext() and next() methods.
     *
     * @return the map of Attribute from Body part for POST method only without Multipart
     * @throws NotEnoughDataDecoderException Need more chunks and
     *   reset the readerInder to the previous value
     * @throws UnappropriatedMethodDecodeDataException if the request is not a PUT or POST request
     *          or if an error occurs
     * @throws ErrorDataDecoderException if there is a problem with the charset decoding or other errors
     */
    public Map<String, List<Attribute>> getBodyAttributes()
            throws NotEnoughDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException {
        if (bodyMapAttributes != null) {
            return bodyMapAttributes;
        }
        setBodyAttributes();
        return bodyMapAttributes;
    }

    /**
     * This method returns a List of all Attributes from POST or PUT method without Multipart
     * and once all chunks are received and set using newChunk() method.<br>
     *
     * It does not touch the internal buffer, such that it allows however
     * to use however the hasNext() and next() methods.
     *
     * @return the list of Attribute from Body part for POST method only without Multipart
     * @throws NotEnoughDataDecoderException Need more chunks and
     *   reset the readerInder to the previous value
     * @throws UnappropriatedMethodDecodeDataException if the request is not a PUT or POST request
     *          or if an error occurs
     * @throws ErrorDataDecoderException if there is a problem with the charset decoding or other errors
     */
    public List<Attribute> getBodyListAttributes()
            throws NotEnoughDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException {
        if (bodyListAttributes != null) {
            return bodyListAttributes;
        }
        setBodyAttributes();
        return bodyListAttributes;
    }

    /**
    *
    * @param name
    * @return the list of Header attributes with the given name (ignore case)
     * @throws UnappropriatedMethodDecodeDataException
     * @throws ErrorDataDecoderException
     * @throws NotEnoughDataDecoderException
    */
    public List<Attribute> getBodyAttributes(String name)
            throws NotEnoughDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException {
        if (bodyMapAttributes == null) {
            setBodyAttributes();
        }
        return bodyMapAttributes.get(name.toLowerCase());
    }

    /**
     *
     * @param name
     * @return the first Header attributes with the given name (ignore case)
     * @throws UnappropriatedMethodDecodeDataException
     * @throws ErrorDataDecoderException
     * @throws NotEnoughDataDecoderException
     */
    public Attribute getBodyAttribute(String name)
            throws NotEnoughDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException {
        if (bodyMapAttributes == null) {
            setBodyAttributes();
        }
        List<Attribute> list = bodyMapAttributes.get(name.toLowerCase());
        if (list.isEmpty()) {
            return null;
        } else {
            return list.get(0);
        }
    }

    /**
    *
    * @param name
    * @return the value of the first Header attributes with the given name (ignore case)
     * @throws UnappropriatedMethodDecodeDataException
     * @throws ErrorDataDecoderException
     * @throws NotEnoughDataDecoderException
    */
    public String getBodyAttributeValue(String name)
            throws NotEnoughDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException {
        if (bodyMapAttributes == null) {
            setBodyAttributes();
        }
        List<Attribute> list = bodyMapAttributes.get(name.toLowerCase());
        if (list.isEmpty()) {
            return null;
        } else {
            return list.get(0).getValue();
        }
    }

    /**
     * This method fill the map of all Attributes from POST or PUT method without Multipart
     * and once all chunks are received and set using newChunk() method.<br>
     *
     * It does not touch the internal buffer, such that it allows however
     * to use however the hasNext() and next() methods.
     *
     * @throws NotEnoughDataDecoderException Need more chunks and
     *   reset the readerInder to the previous value
     * @throws ErrorDataDecoderException if there is a problem with the charset decoding or other errors
     * @throws UnappropriatedMethodDecodeDataException  if the request is not a PUT or POST request
     *          or if an error occurs
     */
    private void setBodyAttributes() throws NotEnoughDataDecoderException,
            ErrorDataDecoderException, UnappropriatedMethodDecodeDataException {
        // Is a POST or PUT method
        if (!bodyToDecode) {
            throw new UnappropriatedMethodDecodeDataException(
                    "Only POST request without Multipart are supported for getBodyAttributes");
        }
        // Is it not a Multipart
        if (isMultipart) {
            throw new UnappropriatedMethodDecodeDataException(
                    "Only POST request without Multipart are supported for getBodyAttributes");
        }
        // Last chunk already received
        if (!isLastChunk) {
            throw new NotEnoughDataDecoderException(
                    "All chunks must have been received for getBodyAttributes");
        }
        String body = undecodedChunk.toString(charset);
        Map<String, List<Attribute>> mapAttributes = new HashMap<String, List<Attribute>>();
        List<Attribute> newbodyAttributes = new ArrayList<Attribute>();
        decodeListAttribute(newbodyAttributes, mapAttributes, body);
        bodyListAttributes = newbodyAttributes;
        bodyMapAttributes = mapAttributes;
    }

    /**
     * True if at current status, there is a decoded HttpData from the Body.
     *
     * This method works for chunked and not chunked request.
     * However, once this function is called, the getBodyAttributes could not be called again.
     *
     * @return True if at current status, there is a decoded HttpData
     * @throws NotEnoughDataDecoderException Need more chunks
     * @throws EndOfDataDecoderException No more data will be available
     * @throws UnappropriatedMethodDecodeDataException if the request is not a PUT or POST request
     *          or if an error occurs
     * @throws ErrorDataDecoderException if there is a problem with the charset decoding or other errors
     */
    public boolean hasNext() throws NotEnoughDataDecoderException,
            EndOfDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException {
        if (!bodyToDecode) {
            throw new UnappropriatedMethodDecodeDataException(
                    "Only Post or Put request are supported for Body decode");
        }
        if (currentStatus == MultiPartStatus.EPILOGUE) {
            throw new EndOfDataDecoderException();
        }
        if (currentHttpData != null) {
            // still one decoded not get
            return true;
        }
        if (undecodedChunk == null || undecodedChunk.readableBytes() == 0) {
            // nothing to decode
            return false;
        }
        currentHttpData = decodeBody();
        return currentHttpData != null;
    }

    /**
     * Returns the next available HttpData or null if, at the time it is called, there is no more
     * available HttpData. A subsequent call to newChunk(httpChunk) could enable more data.
     *
     * @return the next available HttpData or null if none
     * @throws NotEnoughDataDecoderException Need more chunks
     * @throws EndOfDataDecoderException No more data will be available
     * @throws UnappropriatedMethodDecodeDataException if the request is not a PUT or POST request
     *          or if an error occurs
     * @throws ErrorDataDecoderException if there is a problem with the charset decoding or other errors
     */
    public HttpData next() throws NotEnoughDataDecoderException,
            EndOfDataDecoderException, UnappropriatedMethodDecodeDataException,
            ErrorDataDecoderException {
        if (!bodyToDecode) {
            throw new UnappropriatedMethodDecodeDataException(
                    "Only Post or Put request are supported for Body decode");
        }
        HttpData data = currentHttpData;
        if (currentHttpData != null) {
            // has already get a decode one
            currentHttpData = null;
        } else {
            // force get next (may be null)
            data = decodeBody();
        }
        return data;
    }

    // inspired from QueryStringDecoder
    private static final Pattern PARAM_PATTERN = Pattern
            .compile("([^=]*)=([^&]*)&*");

    /**
     * Decode as a List and as a map (one of them could be null)
     * @param list
     * @param map
     * @param arg
     * @throws ErrorDataDecoderException
     */
    private void decodeListAttribute(List<Attribute> list,
            Map<String, List<Attribute>> map, String arg)
            throws ErrorDataDecoderException {
        int pathEndPos = arg.indexOf('?');
        if (pathEndPos < 0) {
            decodeListParams(list, map, arg);
        } else {
            decodeListParams(list, map, arg.substring(pathEndPos + 1));
        }
    }

    /**
     * Decode as a List and as a map (one of them could be null)
     * @param list
     * @param map
     * @param s
     * @throws ErrorDataDecoderException
     */
    private void decodeListParams(List<Attribute> list,
            Map<String, List<Attribute>> map, String s)
            throws ErrorDataDecoderException {
        Matcher m = PARAM_PATTERN.matcher(s);
        int pos = 0;
        while (m.find(pos)) {
            pos = m.end();
            String key = decodeComponent(m.group(1), charset).toLowerCase();
            String value = decodeComponent(m.group(2), charset);
            Attribute attribute;
            try {
                attribute = factory.createAttribute(key, value);
            } catch (NullPointerException e) {
                throw new ErrorDataDecoderException(e);
            } catch (IllegalArgumentException e) {
                throw new ErrorDataDecoderException(e);
            }
            if (map != null) {
                List<Attribute> attrs = map.get(key);
                if (attrs == null) {
                    attrs = new ArrayList<Attribute>(1);
                    map.put(key, attrs);
                }
                attrs.add(attribute);
            }
            if (list != null) {
                list.add(attribute);
            }
        }
    }

    /**
     * Decode as a Map
     * @param map
     * @param arg
     * @throws ErrorDataDecoderException
     */
    private void decodeUriAttribute(Map<String, List<Attribute>> map, String arg)
            throws ErrorDataDecoderException {
        decodeListAttribute(null, map, arg);
    }

    /**
     * Decode component
     * @param s
     * @param charset
     * @return the decoded component
     * @throws ErrorDataDecoderException
     */
    private static String decodeComponent(String s, String charset)
            throws ErrorDataDecoderException {
        if (s == null) {
            return "";
        }
        try {
            return URLDecoder.decode(s, charset);
        } catch (UnsupportedEncodingException e) {
            throw new ErrorDataDecoderException(charset, e);
        }
    }

    /**
     * Decode the next data from the body (multipart or not)
     * @return the next decoded HttpData
     * @throws EndOfDataDecoderException if the end of the decode operation is reached
     * @throws NotEnoughDataDecoderException Need more chunks
     * @throws UnappropriatedMethodDecodeDataException
     * @throws ErrorDataDecoderException if an error occurs during decode
     */
    private HttpData decodeBody() throws EndOfDataDecoderException,
            NotEnoughDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException {
        if (isMultipart) {
            return decodeMultipart(currentStatus);
        } else {
            // decode standard Body without Multipart
            // does only work when all chunks are received
            if (isLastChunk) {
                if (bodyIteratorAttributes == null) {
                    if (bodyListAttributes == null) {
                        setBodyAttributes();
                    }
                    bodyIteratorAttributes = bodyListAttributes.listIterator();
                }
                if (bodyIteratorAttributes.hasNext()) {
                    return bodyIteratorAttributes.next();
                } else {
                    // end of iterator
                    throw new EndOfDataDecoderException();
                }
            } else {
                return null;
            }
        }
    }

    /**
     * Decode a multipart request by pieces<br>
     * <br>
     * NOTSTARTED PREAMBLE (<br>
     *  (HEADERDELIMITER DISPOSITION (FIELD | FILEUPLOAD))*<br>
     *  (HEADERDELIMITER DISPOSITION MIXEDPREAMBLE<br>
     *     (MIXEDDELIMITER MIXEDDISPOSITION MIXEDFILEUPLOAD)+<br>
     *   MIXEDCLOSEDELIMITER)*<br>
     * CLOSEDELIMITER)+ EPILOGUE<br>
     *
     * Inspired from HttpMessageDecoder
     *
     * @param state
     * @return the next decoded HttpData or null if none until now.
     * @throws EndOfDataDecoderException when there is no more data to decode
     * @throws ErrorDataDecoderException if an error occurs
     * @throws UnappropriatedMethodDecodeDataException
     */
    private HttpData decodeMultipart(MultiPartStatus state)
            throws EndOfDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException {
        switch (state) {
        case NOTSTARTED:
            throw new UnappropriatedMethodDecodeDataException(
                    "Should not be called with the current status");
        case PREAMBLE:
            // Content-type: multipart/form-data, boundary=AaB03x
            throw new UnappropriatedMethodDecodeDataException(
                    "Should not be called with the current status");
        case HEADERDELIMITER: {
            // --AaB03x or --AaB03x--
            return findMultipartDelimiter(multipartDataBoundary,
                    MultiPartStatus.DISPOSITION, MultiPartStatus.EPILOGUE);
        }
        case DISPOSITION: {
            //  content-disposition: form-data; name="field1"
            //  content-disposition: form-data; name="pics"; filename="file1.txt"
            // and other immediate values like
            //  Content-type: image/gif
            //  Content-Type: text/plain
            //  Content-Type: text/plain; charset=ISO-8859-1
            //  Content-Transfer-Encoding: binary
            // The following line implies a change of mode (mixed mode)
            //  Content-type: multipart/mixed, boundary=BbC04y
            return findMultipartDisposition();
        }
        case FIELD: {
            // Now get value according to Content-Type and Charset
            String localCharset = charset;
            Attribute charsetAttribute = currentFieldAttributes.get(CHARSET);
            if (charsetAttribute != null) {
                localCharset = charsetAttribute.getValue();
            }
            Attribute nameAttribute = currentFieldAttributes.get(NAME);
            // load data
            String finalValue;
            try {
                finalValue = readFieldMultipart(multipartDataBoundary,
                        localCharset);
            } catch (NotEnoughDataDecoderException e) {
                return null;
            }
            Attribute finalAttribute;
            try {
                finalAttribute = factory.createAttribute(nameAttribute
                        .getValue(), finalValue);
            } catch (NullPointerException e) {
                throw new ErrorDataDecoderException(e);
            } catch (IllegalArgumentException e) {
                throw new ErrorDataDecoderException(e);
            }
            currentFieldAttributes = null;
            // ready to load the next one
            currentStatus = MultiPartStatus.HEADERDELIMITER;
            return finalAttribute;
        }
        case FILEUPLOAD: {
            // eventually restart from existing FileUpload
            return getFileUpload(multipartDataBoundary);
        }
        case MIXEDDELIMITER: {
            // --AaB03x or --AaB03x--
            // Note that currentFieldAttributes exists
            return findMultipartDelimiter(multipartMixedBoundary,
                    MultiPartStatus.MIXEDDISPOSITION,
                    MultiPartStatus.HEADERDELIMITER);
        }
        case MIXEDDISPOSITION: {
            return findMultipartDisposition();
        }
        case MIXEDFILEUPLOAD: {
            // eventually restart from existing FileUpload
            return getFileUpload(multipartMixedBoundary);
        }
        case EPILOGUE:
            // should throw endofdecode
            throw new EndOfDataDecoderException();
        default:
            throw new ErrorDataDecoderException("Shouldn't reach here.");
        }
    }

    /**
     * Find the next Multipart Delimiter
     * @param delimiter delimiter to find
     * @param dispositionStatus the next status if the delimiter is a start
     * @param closeDelimiterStatus the next status if the delimiter is a close delimiter
     * @return the next HttpData if any
     * @throws EndOfDataDecoderException
     * @throws ErrorDataDecoderException
     * @throws UnappropriatedMethodDecodeDataException
     */
    private HttpData findMultipartDelimiter(String delimiter,
            MultiPartStatus dispositionStatus,
            MultiPartStatus closeDelimiterStatus)
            throws EndOfDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException {
        // --AaB03x or --AaB03x--
        int readerIndex = undecodedChunk.readerIndex();
        HttpCodecUtil.skipControlCharacters(undecodedChunk);
        skipOneLine();
        String newline;
        try {
            newline = readLine();
        } catch (NotEnoughDataDecoderException e) {
            undecodedChunk.readerIndex(readerIndex);
            return null;
        }
        if (newline.equals(delimiter)) {
            currentStatus = dispositionStatus;
            return decodeMultipart(dispositionStatus);
        } else if (newline.equals(delimiter + "--")) {
            // CLOSEDELIMITER or MIXED CLOSEDELIMITER found
            currentStatus = closeDelimiterStatus;
            if (currentStatus == MultiPartStatus.HEADERDELIMITER) {
                // MIXEDCLOSEDELIMITER
                // end of the Mixed part
                currentFieldAttributes = null;
                return decodeMultipart(MultiPartStatus.HEADERDELIMITER);
            }
            return null;
        }
        undecodedChunk.readerIndex(readerIndex);
        throw new ErrorDataDecoderException("No Multipart delimiter found");
    }

    /**
     * Find the next Disposition
     * @return the next HttpData if any
     * @throws EndOfDataDecoderException
     * @throws ErrorDataDecoderException
     * @throws UnappropriatedMethodDecodeDataException
     */
    private HttpData findMultipartDisposition()
            throws EndOfDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException {
        int readerIndex = undecodedChunk.readerIndex();
        if (currentStatus == MultiPartStatus.DISPOSITION) {
            currentFieldAttributes = new HashMap<String, Attribute>();
        }
        // read many lines until empty line with newline found! Store all data
        while (!skipOneLine()) {
            HttpCodecUtil.skipControlCharacters(undecodedChunk);
            String newline;
            try {
                newline = readLine();
            } catch (NotEnoughDataDecoderException e) {
                undecodedChunk.readerIndex(readerIndex);
                return null;
            }
            String[] contents = splitMultipartHeader(newline);
            if (contents[0].equalsIgnoreCase(CONTENT_DISPOSITION)) {
                boolean checkSecondArg = false;
                if (currentStatus == MultiPartStatus.DISPOSITION) {
                    checkSecondArg = contents[1].equalsIgnoreCase(FORM_DATA);
                } else {
                    checkSecondArg = contents[1].equalsIgnoreCase(ATTACHMENT);
                }
                if (checkSecondArg) {
                    // read next values and store them in the list as Attribute
                    for (int i = 2; i < contents.length; i ++) {
                        String[] values = contents[i].split("=");
                        Attribute attribute;
                        try {
                            attribute = factory.createAttribute(values[0]
                                    .toLowerCase().trim(),
                                    cleanString(values[1]));
                        } catch (NullPointerException e) {
                            throw new ErrorDataDecoderException(e);
                        } catch (IllegalArgumentException e) {
                            throw new ErrorDataDecoderException(e);
                        }
                        currentFieldAttributes.put(attribute.getName(),
                                attribute);
                    }
                }
            } else if (contents[0].equalsIgnoreCase(CONTENT_TRANSFER_ENCODING)) {
                Attribute attribute;
                try {
                    attribute = factory
                            .createAttribute(CONTENT_TRANSFER_ENCODING,
                                    cleanString(contents[1]));
                } catch (NullPointerException e) {
                    throw new ErrorDataDecoderException(e);
                } catch (IllegalArgumentException e) {
                    throw new ErrorDataDecoderException(e);
                }
                currentFieldAttributes
                        .put(CONTENT_TRANSFER_ENCODING, attribute);
            } else if (contents[0].equalsIgnoreCase(CONTENT_LENGTH)) {
                Attribute attribute;
                try {
                    attribute = factory.createAttribute(CONTENT_LENGTH,
                            cleanString(contents[1]));
                } catch (NullPointerException e) {
                    throw new ErrorDataDecoderException(e);
                } catch (IllegalArgumentException e) {
                    throw new ErrorDataDecoderException(e);
                }
                currentFieldAttributes.put(CONTENT_LENGTH, attribute);
            } else if (contents[0].equalsIgnoreCase(CONTENT_TYPE)) {
                // Take care of possible "multipart/mixed"
                if (contents[1].equalsIgnoreCase(MULTIPART_MIXED)) {
                    if (currentStatus == MultiPartStatus.DISPOSITION) {
                        String[] values = contents[2].split("=");
                        multipartMixedBoundary = "--" + values[1];
                        currentStatus = MultiPartStatus.MIXEDDELIMITER;
                        return decodeMultipart(MultiPartStatus.MIXEDDELIMITER);
                    } else {
                        throw new ErrorDataDecoderException(
                                "Mixed Multipart found in a previous Mixed Multipart");
                    }
                } else {
                    for (int i = 1; i < contents.length; i ++) {
                        if (contents[i].toLowerCase().startsWith(CHARSET)) {
                            String[] values = contents[i].split("=");
                            Attribute attribute;
                            try {
                                attribute = factory.createAttribute(CHARSET,
                                        cleanString(values[1]));
                            } catch (NullPointerException e) {
                                throw new ErrorDataDecoderException(e);
                            } catch (IllegalArgumentException e) {
                                throw new ErrorDataDecoderException(e);
                            }
                            currentFieldAttributes.put(CHARSET, attribute);
                        } else {
                            Attribute attribute;
                            try {
                                attribute = factory.createAttribute(contents[0]
                                        .toLowerCase().trim(),
                                        cleanString(contents[i]));
                            } catch (NullPointerException e) {
                                throw new ErrorDataDecoderException(e);
                            } catch (IllegalArgumentException e) {
                                throw new ErrorDataDecoderException(e);
                            }
                            currentFieldAttributes.put(attribute.getName(),
                                    attribute);
                        }
                    }
                }
            } else {
                throw new ErrorDataDecoderException("Unknown Params: " +
                        newline);
            }
        }
        // Is it a FileUpload
        Attribute filenameAttribute = currentFieldAttributes.get(FILENAME);
        if (currentStatus == MultiPartStatus.DISPOSITION) {
            if (filenameAttribute != null) {
                // FileUpload
                currentStatus = MultiPartStatus.FILEUPLOAD;
                // do not change the buffer position
                return decodeMultipart(MultiPartStatus.FILEUPLOAD);
            } else {
                // Field
                currentStatus = MultiPartStatus.FIELD;
                // do not change the buffer position
                return decodeMultipart(MultiPartStatus.FIELD);
            }
        } else {
            if (filenameAttribute != null) {
                // FileUpload
                currentStatus = MultiPartStatus.MIXEDFILEUPLOAD;
                // do not change the buffer position
                return decodeMultipart(MultiPartStatus.MIXEDFILEUPLOAD);
            } else {
                // Field is not supported in MIXED mode
                throw new ErrorDataDecoderException("Filename not found");
            }
        }
    }

    /**
     * Get the FileUpload (new one or current one)
     * @param delimiter the delimiter to use
     * @return the HttpData if any
     * @throws ErrorDataDecoderException
     */
    private HttpData getFileUpload(String delimiter)
            throws ErrorDataDecoderException {
        // eventually restart from existing FileUpload
        // Now get value according to Content-Type and Charset
        Attribute encoding = currentFieldAttributes
                .get(CONTENT_TRANSFER_ENCODING);
        String localCharset = charset;
        if (encoding != null) {
            String code = encoding.getValue().toLowerCase();
            if (code.equals(TransferEncodingMechanism.BIT7)) {
                localCharset = US_ASCII;
            } else if (code.equals(TransferEncodingMechanism.BIT8)) {
                localCharset = ISO_8859_1;
            } else if (code.equals(TransferEncodingMechanism.BINARY)) {
                // no real charset, so let the default
            } else {
                throw new ErrorDataDecoderException(
                        "TransferEncoding Unknown: " + code);
            }
        }
        Attribute charsetAttribute = currentFieldAttributes.get(CHARSET);
        if (charsetAttribute != null) {
            localCharset = charsetAttribute.getValue();
        }
        if (currentFileUpload == null) {
            Attribute filenameAttribute = currentFieldAttributes.get(FILENAME);
            Attribute nameAttribute = currentFieldAttributes.get(NAME);
            Attribute contentTypeAttribute = currentFieldAttributes
                    .get(CONTENT_TYPE);
            if (contentTypeAttribute == null) {
                throw new ErrorDataDecoderException(
                        "Content-Type is absent but required");
            }
            Attribute lengthAttribute = currentFieldAttributes
                    .get(CONTENT_LENGTH);
            long size = 0L;
            try {
                size = lengthAttribute != null? Long.parseLong(lengthAttribute
                        .getValue()) : 0L;
            } catch (NumberFormatException e) {
            }
            try {
                currentFileUpload = factory.createFileUpload(nameAttribute
                        .getValue(), filenameAttribute.getValue(),
                        contentTypeAttribute.getValue(), localCharset, size);
            } catch (NullPointerException e) {
                throw new ErrorDataDecoderException(e);
            } catch (IllegalArgumentException e) {
                throw new ErrorDataDecoderException(e);
            }
            addFileUpload();
        }
        // load data as much as possible
        try {
            readFileUploadByteMultipart(delimiter);
        } catch (NotEnoughDataDecoderException e) {
            // do not change the buffer position
            // since some can be already saved into FileUpload
            // So do not change the currentStatus
            return null;
        }
        if (currentFileUpload.isCompleted()) {
            // ready to load the next one
            if (currentStatus == MultiPartStatus.FILEUPLOAD) {
                currentStatus = MultiPartStatus.HEADERDELIMITER;
                currentFieldAttributes = null;
            } else {
                currentStatus = MultiPartStatus.MIXEDDELIMITER;
                cleanMixedAttributes();
            }
            FileUpload fileUpload = currentFileUpload;
            currentFileUpload = null;
            return fileUpload;
        }
        // do not change the buffer position
        // since some can be already saved into FileUpload
        // So do not change the currentStatus
        return null;
    }

    /**
     * Internal to add new FileUpload into the list to delete
     */
    private void addFileUpload() {
        if (fileUploadsToDelete == null) {
            fileUploadsToDelete = new ArrayList<FileUpload>();
        }
        fileUploadsToDelete.add(currentFileUpload);
    }

    /**
     * Clean all FileUploads (even on Disk).
     *
     */
    public void cleanFileUploads() {
        if (fileUploadsToDelete != null) {
            for (FileUpload fileUpload: fileUploadsToDelete) {
                fileUpload.delete();
            }
            fileUploadsToDelete.clear();
            fileUploadsToDelete = null;
        }
    }

    /**
     * Remove the given FileUpload from the list of FileUploads to clean
     * @param fileUpload
     */
    public void removeFileUploadFromClean(FileUpload fileUpload) {
        if (fileUploadsToDelete != null) {
            fileUploadsToDelete.remove(fileUpload);
        }
    }

    /**
     * Remove all Attributes that should be cleaned between two FileUpload in Mixed mode
     */
    private void cleanMixedAttributes() {
        currentFieldAttributes.remove(CHARSET);
        currentFieldAttributes.remove(CONTENT_LENGTH);
        currentFieldAttributes.remove(CONTENT_TRANSFER_ENCODING);
        currentFieldAttributes.remove(CONTENT_TYPE);
        currentFieldAttributes.remove(FILENAME);
    }

    /**
     * Read one line up to the CRLF or LF
     * @return the String from one line
     * @throws NotEnoughDataDecoderException Need more chunks and
     *   reset the readerInder to the previous value
     */
    private String readLine() throws NotEnoughDataDecoderException {
        int readerIndex = undecodedChunk.readerIndex();
        try {
            StringBuilder sb = new StringBuilder(64);
            while (undecodedChunk.readable()) {
                byte nextByte = undecodedChunk.readByte();
                if (nextByte == HttpCodecUtil.CR) {
                    nextByte = undecodedChunk.readByte();
                    if (nextByte == HttpCodecUtil.LF) {
                        return sb.toString();
                    }
                } else if (nextByte == HttpCodecUtil.LF) {
                    return sb.toString();
                } else {
                    sb.append((char) nextByte);
                }
            }
        } catch (IndexOutOfBoundsException e) {
            undecodedChunk.readerIndex(readerIndex);
            throw new NotEnoughDataDecoderException(e);
        }
        undecodedChunk.readerIndex(readerIndex);
        throw new NotEnoughDataDecoderException();
    }

    /**
     * Read a FileUpload data as Byte (Binary) and add the bytes directly to the
     * FileUpload. If the delimiter is found, the FileUpload is completed.
     * @param delimiter
     * @throws NotEnoughDataDecoderException Need more chunks but
     *   do not reset the readerInder since some values will be already added to the FileOutput
     * @throws ErrorDataDecoderException write IO error occurs with the FileUpload
     */
    private void readFileUploadByteMultipart(String delimiter)
            throws NotEnoughDataDecoderException, ErrorDataDecoderException {
        int readerIndex = undecodedChunk.readerIndex();
        // found the decoder limit
        boolean newLine = true;
        int index = 0;
        int lastPosition = undecodedChunk.readerIndex();
        boolean found = false;
        while (undecodedChunk.readable()) {
            byte nextByte = undecodedChunk.readByte();
            if (newLine) {
                // Check the delimiter
                if (nextByte == delimiter.codePointAt(index)) {
                    index ++;
                    if (delimiter.length() == index) {
                        found = true;
                        break;
                    }
                    continue;
                } else {
                    newLine = false;
                    index = 0;
                    // continue until end of line
                    if (nextByte == HttpCodecUtil.CR) {
                        if (undecodedChunk.readable()) {
                            nextByte = undecodedChunk.readByte();
                            if (nextByte == HttpCodecUtil.LF) {
                                newLine = true;
                                index = 0;
                                lastPosition = undecodedChunk.readerIndex() - 2;
                            }
                        }
                    } else if (nextByte == HttpCodecUtil.LF) {
                        newLine = true;
                        index = 0;
                        lastPosition = undecodedChunk.readerIndex() - 1;
                    } else {
                        // save last valid position
                        lastPosition = undecodedChunk.readerIndex();
                    }
                }
            } else {
                // continue until end of line
                if (nextByte == HttpCodecUtil.CR) {
                    if (undecodedChunk.readable()) {
                        nextByte = undecodedChunk.readByte();
                        if (nextByte == HttpCodecUtil.LF) {
                            newLine = true;
                            index = 0;
                            lastPosition = undecodedChunk.readerIndex() - 2;
                        }
                    }
                } else if (nextByte == HttpCodecUtil.LF) {
                    newLine = true;
                    index = 0;
                    lastPosition = undecodedChunk.readerIndex() - 1;
                } else {
                    // save last valid position
                    lastPosition = undecodedChunk.readerIndex();
                }
            }
        }
        ChannelBuffer buffer = undecodedChunk.slice(readerIndex, lastPosition -
                readerIndex);
        if (found) {
            // found so lastPosition is correct and final
            try {
                currentFileUpload.addContent(buffer, true);
                // just before the CRLF and delimiter
                undecodedChunk.readerIndex(lastPosition);
            } catch (IOException e) {
                throw new ErrorDataDecoderException("IOException", e);
            }
        } else {
            // possibly the delimiter is partially found but still the last position is OK
            try {
                currentFileUpload.addContent(buffer, false);
                // last valid char (not CR, not LF, not beginning of delimiter)
                undecodedChunk.readerIndex(lastPosition);
                throw new NotEnoughDataDecoderException();
            } catch (IOException e) {
                throw new ErrorDataDecoderException("IOException", e);
            }
        }
    }

    /**
     * Read the field value from a Multipart request
     * @return the field value
     * @throws NotEnoughDataDecoderException Need more chunks and
     *   reset the readerInder to the previous value
     */
    private String readFieldMultipart(String delimiter, String newcharset)
            throws NotEnoughDataDecoderException {
        int readerIndex = undecodedChunk.readerIndex();
        try {
            // found the decoder limit
            boolean newLine = true;
            int index = 0;
            int lastPosition = undecodedChunk.readerIndex();
            boolean found = false;
            while (undecodedChunk.readable()) {
                byte nextByte = undecodedChunk.readByte();
                if (newLine) {
                    // Check the delimiter
                    if (nextByte == delimiter.codePointAt(index)) {
                        index ++;
                        if (delimiter.length() == index) {
                            found = true;
                            break;
                        }
                        continue;
                    } else {
                        newLine = false;
                        index = 0;
                        // continue until end of line
                        if (nextByte == HttpCodecUtil.CR) {
                            nextByte = undecodedChunk.readByte();
                            if (nextByte == HttpCodecUtil.LF) {
                                newLine = true;
                                index = 0;
                                lastPosition = undecodedChunk.readerIndex() - 2;
                            }
                        } else if (nextByte == HttpCodecUtil.LF) {
                            newLine = true;
                            index = 0;
                            lastPosition = undecodedChunk.readerIndex() - 1;
                        }
                    }
                } else {
                    // continue until end of line
                    if (nextByte == HttpCodecUtil.CR) {
                        nextByte = undecodedChunk.readByte();
                        if (nextByte == HttpCodecUtil.LF) {
                            newLine = true;
                            index = 0;
                            lastPosition = undecodedChunk.readerIndex() - 2;
                        }
                    } else if (nextByte == HttpCodecUtil.LF) {
                        newLine = true;
                        index = 0;
                        lastPosition = undecodedChunk.readerIndex() - 1;
                    }
                }
            }
            if (found) {
                // found so lastPosition is correct
                // but position is just after the delimiter (either close delimiter or simple one)
                // so go back of delimiter size
                undecodedChunk.readerIndex(lastPosition);
                return undecodedChunk.toString(readerIndex, lastPosition -
                        readerIndex, newcharset);
            } else {
                undecodedChunk.readerIndex(readerIndex);
                throw new NotEnoughDataDecoderException();
            }
        } catch (IndexOutOfBoundsException e) {
            undecodedChunk.readerIndex(readerIndex);
            throw new NotEnoughDataDecoderException(e);
        }
    }

    /**
     * Clean the String from any unallowed character
     * @return the cleaned String
     */
    private String cleanString(String field) {
        StringBuilder sb = new StringBuilder(field.length());
        int i = 0;
        for (i = 0; i < field.length(); i ++) {
            char nextChar = field.charAt(i);
            if (nextChar == HttpCodecUtil.COLON) {
                sb.append(HttpCodecUtil.SP);
            } else if (nextChar == HttpCodecUtil.COMMA) {
                sb.append(HttpCodecUtil.SP);
            } else if (nextChar == HttpCodecUtil.EQUALS) {
                sb.append(HttpCodecUtil.SP);
            } else if (nextChar == HttpCodecUtil.SEMICOLON) {
                sb.append(HttpCodecUtil.SP);
            } else if (nextChar == HttpCodecUtil.HT) {
                sb.append(HttpCodecUtil.SP);
            } else if (nextChar == HttpCodecUtil.DOUBLE_QUOTE) {
                // nothing added, just removes it
            } else {
                sb.append(nextChar);
            }
        }
        return sb.toString().trim();
    }

    /**
     * Skip one empty line
     * @return True if one empty line was skipped
     */
    private boolean skipOneLine() {
        if (!undecodedChunk.readable()) {
            return false;
        }
        byte nextByte = undecodedChunk.readByte();
        if (nextByte == HttpCodecUtil.CR) {
            if (!undecodedChunk.readable()) {
                undecodedChunk.readerIndex(undecodedChunk.readerIndex() - 1);
                return false;
            }
            nextByte = undecodedChunk.readByte();
            if (nextByte == HttpCodecUtil.LF) {
                return true;
            }
            undecodedChunk.readerIndex(undecodedChunk.readerIndex() - 2);
            return false;
        } else if (nextByte == HttpCodecUtil.LF) {
            return true;
        }
        undecodedChunk.readerIndex(undecodedChunk.readerIndex() - 1);
        return false;
    }

    /**
     * Split the very first line (Content-Type value) in 2 Strings
     * @param sb
     * @return the array of 2 Strings
     */
    private String[] splitInitialMultipartLine(String sb) {
        int size = sb.length();
        int aStart;
        int aEnd;
        int bStart;
        int bEnd;
        aStart = HttpCodecUtil.findNonWhitespace(sb, 0);
        aEnd = HttpCodecUtil.findWhitespace(sb, aStart);
        if (aEnd >= size) {
            return new String[] { sb, "" };
        }
        if (sb.charAt(aEnd) == ';') {
            aEnd --;
        }
        bStart = HttpCodecUtil.findNonWhitespace(sb, aEnd);
        bEnd = HttpCodecUtil.findEndOfString(sb);
        return new String[] { sb.substring(aStart, aEnd),
                sb.substring(bStart, bEnd) };
    }

    /**
     * Split one header in Multipart
     * @param sb
     * @return an array of String where rank 0 is the name of the header, follows by several
     *  values that were separated by ';' or ','
     */
    private String[] splitMultipartHeader(String sb) {
        ArrayList<String> headers = new ArrayList<String>(1);
        int nameStart;
        int nameEnd;
        int colonEnd;
        int valueStart;
        int valueEnd;
        nameStart = HttpCodecUtil.findNonWhitespace(sb, 0);
        for (nameEnd = nameStart; nameEnd < sb.length(); nameEnd ++) {
            char ch = sb.charAt(nameEnd);
            if (ch == ':' || Character.isWhitespace(ch)) {
                break;
            }
        }
        for (colonEnd = nameEnd; colonEnd < sb.length(); colonEnd ++) {
            if (sb.charAt(colonEnd) == ':') {
                colonEnd ++;
                break;
            }
        }
        valueStart = HttpCodecUtil.findNonWhitespace(sb, colonEnd);
        valueEnd = HttpCodecUtil.findEndOfString(sb);
        headers.add(sb.substring(nameStart, nameEnd));
        String svalue = sb.substring(valueStart, valueEnd);
        String[] values = null;
        if (svalue.indexOf(";") >= 0) {
            values = svalue.split(";");
        } else {
            values = svalue.split(",");
        }
        for (String value: values) {
            headers.add(value.trim());
        }
        String[] array = new String[headers.size()];
        for (int i = 0; i < headers.size(); i ++) {
            array[i] = headers.get(i);
        }
        return array;
    }

    /**
     * Exception when try reading data from request in chunked format, and not enough
     * data are available (need more chunks)
     *
     * @author frederic bregier
     *
     */
    public static class NotEnoughDataDecoderException extends Exception {
        /**
         *
         */
        private static final long serialVersionUID = -7846841864603865638L;

        /**
         *
         */
        public NotEnoughDataDecoderException() {
            super();
        }

        /**
         * @param arg0
         */
        public NotEnoughDataDecoderException(String arg0) {
            super(arg0);
        }

        /**
         * @param arg0
         */
        public NotEnoughDataDecoderException(Throwable arg0) {
            super(arg0);
        }

        /**
         * @param arg0
         * @param arg1
         */
        public NotEnoughDataDecoderException(String arg0, Throwable arg1) {
            super(arg0, arg1);
        }
    }

    /**
     * Exception when the body is fully decoded, even if there is still data
     *
     * @author frederic bregier
     *
     */
    public static class EndOfDataDecoderException extends Exception {
        /**
         *
         */
        private static final long serialVersionUID = 1336267941020800769L;

        /**
         *
         */
        public EndOfDataDecoderException() {
            super();
        }
    }

    /**
     * Exception when an error occurs while decoding
     *
     * @author frederic bregier
     *
     */
    public static class ErrorDataDecoderException extends Exception {
        /**
         *
         */
        private static final long serialVersionUID = 5020247425493164465L;

        /**
         *
         */
        public ErrorDataDecoderException() {
            super();
        }

        /**
         * @param arg0
         */
        public ErrorDataDecoderException(String arg0) {
            super(arg0);
        }

        /**
         * @param arg0
         */
        public ErrorDataDecoderException(Throwable arg0) {
            super(arg0);
        }

        /**
         * @param arg0
         * @param arg1
         */
        public ErrorDataDecoderException(String arg0, Throwable arg1) {
            super(arg0, arg1);
        }
    }

    /**
     * Exception when an unappropriated method was called on a request
     *
     * @author frederic bregier
     *
     */
    public class UnappropriatedMethodDecodeDataException extends Exception {
        /**
         *
         */
        private static final long serialVersionUID = -953268047926250267L;

        /**
         *
         */
        public UnappropriatedMethodDecodeDataException() {
            super();
        }

        /**
         * @param arg0
         */
        public UnappropriatedMethodDecodeDataException(String arg0) {
            super(arg0);
        }

        /**
         * @param arg0
         */
        public UnappropriatedMethodDecodeDataException(Throwable arg0) {
            super(arg0);
        }

        /**
         * @param arg0
         * @param arg1
         */
        public UnappropriatedMethodDecodeDataException(String arg0,
                Throwable arg1) {
            super(arg0, arg1);
        }
    }
}
