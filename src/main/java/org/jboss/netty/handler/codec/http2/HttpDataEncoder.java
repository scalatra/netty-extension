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
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;

/**
 * This encoder will help to encode Response.
 * @author frederic bregier
 *
 */
public class HttpDataEncoder {
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
     * Does the last chunk already decoded
     */
    private boolean isLastChunk = false;

    /**
     * Cookies
     */
    private Map<String, Cookie> cookies;

    /**
     * Attributes for URI
     */
    private Map<String, List<Attribute>> uriAttributes;

    /**
     * Attributes for Header
     */
    private Map<String, List<Attribute>> headerAttributes;

    /**
     * HttpData for Body
     */
    private List<HttpData> bodyListDatas = null;

    /**
    *
    * @param response the response to encode
    * @throws NullPointerException for response
    * @throws NotEnoughDataDecoderException Need more chunks and
    *   reset the readerInder to the previous value
    * @throws UnappropriatedMethodDecodeDataException if the request is not a PUT or POST request
    *          or if an error occurs
    * @throws ErrorDataDecoderException if the default charset was wrong when decoding or other errors
    */
    public HttpDataEncoder(HttpResponse response)
            throws NotEnoughDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException, NullPointerException {
        this(new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE),
                response, HttpCodecUtil.DEFAULT_CHARSET);
    }

    /**
     *
     * @param factory the factory used to create HttpData
     * @param response the response to encode
     * @throws NullPointerException for response and factory
     * @throws NotEnoughDataDecoderException Need more chunks and
     *   reset the readerInder to the previous value
     * @throws UnappropriatedMethodDecodeDataException if the request is not a PUT or POST request
     *          or if an error occurs
     * @throws ErrorDataDecoderException if the default charset was wrong when decoding or other errors
     */
    public HttpDataEncoder(HttpDataFactory factory, HttpResponse response)
            throws NotEnoughDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException, NullPointerException {
        this(factory, response, HttpCodecUtil.DEFAULT_CHARSET);
    }

    /**
     *
     * @param factory the factory used to create HttpData
     * @param response the response to encode
     * @param charset the charset to use as default
     * @throws NullPointerException for request or charset or factory
     * @throws NotEnoughDataDecoderException Need more chunks and
     *   reset the readerInder to the previous value
     * @throws UnappropriatedMethodDecodeDataException if the request is not a PUT or POST request
     *          or if an error occurs
     * @throws ErrorDataDecoderException if the default charset was wrong when decoding or other errors
     */
    public HttpDataEncoder(HttpDataFactory factory, HttpResponse response,
            String charset) throws NotEnoughDataDecoderException,
            ErrorDataDecoderException, UnappropriatedMethodDecodeDataException,
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
        cookies = new HashMap<String, Cookie>();
        uriAttributes = new HashMap<String, List<Attribute>>();
        headerAttributes = new HashMap<String, List<Attribute>>();
        bodyListDatas = new ArrayList<HttpData>();
        // default mode
        isLastChunk = false;
        isMultipart = false;
    }

    /**
     *
     * @return the Map of Cookies where the key are their names
     */
    public Map<String, Cookie> getCookies() {
        return cookies;
    }

    /**
     * Set the cookies map
     * @param cookies
     */
    public void setCookies(Map<String, Cookie> cookies)
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
        cookies.put(cookie.getName().toLowerCase(), cookie);
    }

    /**
     *
     * @return a Map of URI Attributes where the key are their names
     */
    public Map<String, List<Attribute>> getUriAttributes() {
        return uriAttributes;
    }

    /**
     * Set the URI Attributes
     * @param attributes
     */
    public void setUriAttributes(Map<String, List<Attribute>> attributes)
            throws NullPointerException {
        if (attributes == null) {
            throw new NullPointerException("attributes");
        }
        uriAttributes = attributes;
    }

    /**
     * Add the attribute to the URI map
     * @param attribute
     */
    public void addUriAttributes(Attribute attribute)
            throws NullPointerException {
        if (attribute == null) {
            throw new NullPointerException("attribute");
        }
        List<Attribute> attributes = uriAttributes.get(attribute.getName()
                .toLowerCase());
        if (attributes == null) {
            attributes = new ArrayList<Attribute>();
            uriAttributes.put(attribute.getName().toLowerCase(), attributes);
        }
        attributes.add(attribute);
    }

    /**
     *
     * @return a Map of Header Attributes (except Cookie) where the key are their names
     */
    public Map<String, List<Attribute>> getHeaderAttributes() {
        return headerAttributes;
    }

    /**
     * Set the Header Attributes
     * @param attributes
     */
    public void setHeaderAttributes(Map<String, List<Attribute>> attributes)
            throws NullPointerException {
        if (attributes == null) {
            throw new NullPointerException("attributes");
        }
        headerAttributes = attributes;
    }

    /**
     * Add the attribute to the Header map
     * @param attribute
     */
    public void addHeaderAttributes(Attribute attribute)
            throws NullPointerException {
        if (attribute == null) {
            throw new NullPointerException("attribute");
        }
        List<Attribute> attributes = headerAttributes.get(attribute.getName()
                .toLowerCase());
        if (attributes == null) {
            attributes = new ArrayList<Attribute>();
            headerAttributes.put(attribute.getName().toLowerCase(), attributes);
        }
        attributes.add(attribute);
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
    private final String multipartMixedBoundary = null;

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
     * Keep all FileUpload until cleanFileUploads() is called.
     */
    private final List<FileUpload> fileUploadsToDelete = null;

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
     * True if this request is a Multipart request
     * @return True if this request is a Multipart request
     */
    public boolean isMultipart() {
        return isMultipart;
    }

    /**
     * Set first ChannelBuffer either from the request (non chunked), or from the first chunk
     * of a chunked request.
     * @param delimiter
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
     *
     * @return a newly generated Delimiter (either for DATA or MIXED)
     */
    public String getNewMultipartDelimiter() {
        // construct a generated delimiter
        Random random = new Random();
        return Long.toHexString(random.nextLong()).toLowerCase();
    }

    /**
     * This method returns a List of all HttpData from body part.<br>

     * @return the list of HttpData from Body part
     */
    public List<HttpData> getBodyListAttributes()
            throws NotEnoughDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException {
        return bodyListDatas;
    }

    /**
     * Set the Body HttpDatas list
     * @param datas
     */
    public void setBodyHttpDatas(List<HttpData> datas)
            throws NullPointerException {
        if (datas == null) {
            throw new NullPointerException("datas");
        }
        bodyListDatas = datas;
    }

    /**
     * Add the HttpData to the Body list
     * @param data
     */
    public void addBodyHttpData(HttpData data) throws NullPointerException {
        if (data == null) {
            throw new NullPointerException("data");
        }
        bodyListDatas.add(data);
    }

    /**
     * Encode the next data from the body (multipart or not)
     * @return the next decoded HttpData
     * @throws EndOfDataDecoderException if the end of the decode operation is reached
     * @throws NotEnoughDataDecoderException Need more chunks
     * @throws UnappropriatedMethodDecodeDataException
     * @throws ErrorDataDecoderException if an error occurs during decode
     */
    private HttpData encodeBody() throws EndOfDataDecoderException,
            NotEnoughDataDecoderException, ErrorDataDecoderException,
            UnappropriatedMethodDecodeDataException {
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
