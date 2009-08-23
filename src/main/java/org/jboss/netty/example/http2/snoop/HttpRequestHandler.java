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
package org.jboss.netty.example.http2.snoop;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http2.Attribute;
import org.jboss.netty.handler.codec.http2.Cookie;
import org.jboss.netty.handler.codec.http2.CookieEncoder;
import org.jboss.netty.handler.codec.http2.DefaultHttpDataFactory;
import org.jboss.netty.handler.codec.http2.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http2.DiskFileUpload;
import org.jboss.netty.handler.codec.http2.FileUpload;
import org.jboss.netty.handler.codec.http2.HttpChunk;
import org.jboss.netty.handler.codec.http2.HttpData;
import org.jboss.netty.handler.codec.http2.HttpDataDecoder;
import org.jboss.netty.handler.codec.http2.HttpDataFactory;
import org.jboss.netty.handler.codec.http2.HttpHeaders;
import org.jboss.netty.handler.codec.http2.HttpRequest;
import org.jboss.netty.handler.codec.http2.HttpResponse;
import org.jboss.netty.handler.codec.http2.HttpResponseStatus;
import org.jboss.netty.handler.codec.http2.HttpVersion;
import org.jboss.netty.handler.codec.http2.HttpData.HttpDataType;
import org.jboss.netty.handler.codec.http2.HttpDataDecoder.EndOfDataDecoderException;
import org.jboss.netty.handler.codec.http2.HttpDataDecoder.ErrorDataDecoderException;
import org.jboss.netty.handler.codec.http2.HttpDataDecoder.NotEnoughDataDecoderException;
import org.jboss.netty.handler.codec.http2.HttpDataDecoder.UnappropriatedMethodDecodeDataException;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class HttpRequestHandler extends SimpleChannelUpstreamHandler {

    private volatile HttpRequest request;

    private volatile boolean readingChunks;

    private final StringBuilder responseContent = new StringBuilder();

    private static final HttpDataFactory factory = new DefaultHttpDataFactory(
            true); // 16K

    private HttpDataDecoder decoder = null;
    static {
        DiskFileUpload.deleteOnExitTemporaryFile = true; // should delete file on exit (in normal exit)
        DiskFileUpload.baseDirectory = null; // system temp directory
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.channel.SimpleChannelUpstreamHandler#channelClosed(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelStateEvent)
     */
    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        if (decoder != null) {
            decoder.cleanFileUploads();
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        if (!readingChunks) {
            // clean previous FileUpload if Any
            if (decoder != null) {
                decoder.cleanFileUploads();
            }
            HttpRequest request = this.request = (HttpRequest) e.getMessage();
            if (!request.getUri().startsWith("/form")) {
                // Write Menu
                writeMenu(e);
                return;
            }
            responseContent.setLength(0);
            responseContent.append("WELCOME TO THE WILD WILD WEB SERVER\r\n");
            responseContent.append("===================================\r\n");

            responseContent.append("VERSION: " +
                    request.getProtocolVersion().getText() + "\r\n");

            try {
                decoder = new HttpDataDecoder(factory, request);
            } catch (NotEnoughDataDecoderException e1) {
                // continue
            } catch (ErrorDataDecoderException e1) {
                e1.printStackTrace();
                responseContent.append(e1.getMessage());
                writeResponse(e);
                Channels.close(e.getChannel());
                return;
            } catch (UnappropriatedMethodDecodeDataException e1) {
                e1.printStackTrace();
                responseContent.append(e1.getMessage());
                writeResponse(e);
                Channels.close(e.getChannel());
                return;
            }

            responseContent.append("REQUEST_URI: " + request.getUri() +
                    "\r\n\r\n");
            responseContent.append("\r\n\r\n");

            // new methods
            Map<String, List<Attribute>> headers = decoder
                    .getHeaderAttributes();
            for (String key: headers.keySet()) {
                for (Attribute header: headers.get(key)) {
                    responseContent.append("HEADER: " + header.toString() +
                            "\r\n");
                }
            }
            responseContent.append("\r\n\r\n");

            Map<String, Cookie> cookies = decoder.getCookies();
            for (String key: cookies.keySet()) {
                Cookie cookie = cookies.get(key);
                responseContent.append("COOKIE: " + cookie.toString() + "\r\n");
            }
            responseContent.append("\r\n\r\n");

            Map<String, List<Attribute>> uriAttributes;
            uriAttributes = decoder.getUriAttributes();
            for (String key: uriAttributes.keySet()) {
                for (Attribute uriAttribute: uriAttributes.get(key)) {
                    responseContent.append("URI: " + uriAttribute.toString() +
                            "\r\n");
                }
            }
            responseContent.append("\r\n\r\n");

            responseContent.append("Is Chunked: " + request.isChunked() +
                    "\r\n");
            responseContent.append("IsMultipart: " + decoder.isMultipart() +
                    "\r\n");
            if (request.isChunked()) {
                responseContent.append("Chunks: ");
                readingChunks = true;
            } else {
                ChannelBuffer content = request.getContent();
                if (content.readable()) {
                    responseContent.append("\r\n\r\n");
                    if (decoder.isMultipart()) {
                        readHttpData(e);
                    } else {
                        List<Attribute> bodyAttributes;
                        try {
                            bodyAttributes = decoder.getBodyListAttributes();
                        } catch (NotEnoughDataDecoderException e1) {
                            // should not be since not chunked !
                            e1.printStackTrace();
                            responseContent.append(e1.getMessage());
                            writeResponse(e);
                            Channels.close(e.getChannel());
                            return;
                        } catch (ErrorDataDecoderException e1) {
                            e1.printStackTrace();
                            responseContent.append(e1.getMessage());
                            writeResponse(e);
                            Channels.close(e.getChannel());
                            return;
                        } catch (UnappropriatedMethodDecodeDataException e1) {
                            e1.printStackTrace();
                            responseContent.append(e1.getMessage());
                            writeResponse(e);
                            Channels.close(e.getChannel());
                            return;
                        }
                        for (Attribute bodyAttribute: bodyAttributes) {
                            responseContent.append("BODY: " +
                                    bodyAttribute.toString() + "\r\n");
                        }
                    }
                }
                responseContent.append("\r\n\r\nEND OF CONTENT\r\n");
                writeResponse(e);
            }
        } else {
            HttpChunk chunk = (HttpChunk) e.getMessage();
            try {
                decoder.newChunk(chunk);
            } catch (UnappropriatedMethodDecodeDataException e1) {
                e1.printStackTrace();
                responseContent.append(e1.getMessage());
                writeResponse(e);
                Channels.close(e.getChannel());
                return;
            }
            responseContent.append("o");
            readHttpData(e);
        }
    }

    private void readHttpData(MessageEvent e) {
        try {
            while (decoder.hasNext()) {
                HttpData data = decoder.next();
                if (data != null) {
                    // next value
                    if (data.getHttpDataType() == HttpDataType.Attribute) {
                        if (((Attribute) data).getValue().length() > 100) {
                            responseContent.append("\r\nBODY DATA: " +
                                    data.getHttpDataType().name() + ": " +
                                    data.getName() + " data too long\r\n");
                        } else {
                            responseContent.append("\r\nBODY DATA: " +
                                    data.getHttpDataType().name() + ": " +
                                    data.toString() + "\r\n");
                        }
                    } else {
                        responseContent.append("\r\nBODY DATA: " +
                                data.getHttpDataType().name() + ": " +
                                data.toString() + "\r\n");
                        if (data.getHttpDataType() == HttpDataType.FileUpload) {
                            FileUpload fileUpload = (FileUpload) data;
                            if (fileUpload.isCompleted()) {
                                if (fileUpload.length() < 10000) {
                                    responseContent
                                            .append("\tContent of file\r\n");
                                    try {
                                        responseContent
                                                .append(((FileUpload) data)
                                                        .getString(((FileUpload) data)
                                                                .getCharset()));
                                    } catch (IOException e1) {
                                        // do nothing for the example
                                        e1.printStackTrace();
                                    }
                                    responseContent.append("\r\n");
                                } else {
                                    responseContent
                                            .append("\tFile too long to be printed out:" +
                                                    fileUpload.length() +
                                                    "\r\n");
                                }
                                //fileUpload.isInMemory();// tells if the file is in Memory or on File
                                //fileUpload.renameTo(dest); // enable to move into another File dest
                            } else {
                                responseContent
                                        .append("\tFile to be continued but should not!\r\n");
                            }
                        }
                    }
                }
            }
        } catch (NotEnoughDataDecoderException e1) {
            // continue
            return;
        } catch (EndOfDataDecoderException e1) {
            // end
            readingChunks = false;
            responseContent.append("\r\n\r\nEND OF CONTENT\r\n");
            writeResponse(e);
            return;
        } catch (ErrorDataDecoderException e1) {
            e1.printStackTrace();
            responseContent.append(e1.getMessage());
            writeResponse(e);
            Channels.close(e.getChannel());
            return;
        } catch (UnappropriatedMethodDecodeDataException e1) {
            e1.printStackTrace();
            responseContent.append(e1.getMessage());
            writeResponse(e);
            Channels.close(e.getChannel());
            return;
        }
    }

    private void writeResponse(MessageEvent e) {
        // Convert the response content to a ChannelBuffer.
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(responseContent
                .toString(), "UTF-8");
        responseContent.setLength(0);

        // Decide whether to close the connection or not.
        boolean close = HttpHeaders.Values.CLOSE.equalsIgnoreCase(decoder
                .getHeaderAttributeValue(HttpHeaders.Names.CONNECTION)) ||
                request.getProtocolVersion().equals(HttpVersion.HTTP_1_0) &&
                !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(decoder
                        .getHeaderAttributeValue(HttpHeaders.Names.CONNECTION));

        // Build the response object.
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        response.setContent(buf);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE,
                "text/plain; charset=UTF-8");

        if (!close) {
            // There's no need to add 'Content-Length' header
            // if this is the last response.
            response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, String
                    .valueOf(buf.readableBytes()));
        }

        Map<String, Cookie> map = decoder.getCookies();
        if (!map.isEmpty()) {
            // Reset the cookies if necessary.
            CookieEncoder cookieEncoder = new CookieEncoder(true);
            for (Cookie cookie: map.values()) {
                cookieEncoder.addCookie(cookie);
            }
            response.addHeader(HttpHeaders.Names.SET_COOKIE, cookieEncoder
                    .encode());
        }
        // Write the response.
        ChannelFuture future = e.getChannel().write(response);

        // Close the connection after the write operation is done if necessary.
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void writeMenu(MessageEvent e) {
        // print several HTML forms
        // Convert the response content to a ChannelBuffer.
        responseContent.setLength(0);

        // create Pseudo Menu
        responseContent.append("<html>");
        responseContent.append("<head>");
        responseContent.append("<title>Netty Test Form</title>\r\n");
        responseContent.append("</head>\r\n");
        responseContent
                .append("<body bgcolor=white><style>td{font-size: 12pt;}</style>");

        responseContent.append("<table border=\"0\">");
        responseContent.append("<tr>");
        responseContent.append("<td>");
        responseContent.append("<h1>Netty Test Form</h1>");
        responseContent.append("Choose one FORM");
        responseContent.append("</td>");
        responseContent.append("</tr>");
        responseContent.append("</table>\r\n");

        // GET
        responseContent
                .append("<CENTER>GET FORM<HR WIDTH=\"75%\" NOSHADE color=\"blue\"></CENTER>");
        responseContent.append("<FORM ACTION=\"/formget\" METHOD=\"GET\">");
        responseContent
                .append("<input type=hidden name=getform value=\"GET\">");
        responseContent.append("<table border=\"0\">");
        responseContent
                .append("<tr><td>Fill with value: <br> <input type=text name=\"info\" size=10></td></tr>");
        responseContent
                .append("<tr><td>Fill with value: <br> <input type=text name=\"secondinfo\" size=20>");
        responseContent
                .append("<tr><td>Fill with value: <br> <textarea name=\"thirdinfo\" cols=40 rows=10></textarea>");
        responseContent.append("</td></tr>");
        responseContent
                .append("<tr><td><INPUT TYPE=\"submit\" NAME=\"Send\" VALUE=\"Send\"></INPUT></td>");
        responseContent
                .append("<td><INPUT TYPE=\"reset\" NAME=\"Clear\" VALUE=\"Clear\" ></INPUT></td></tr>");
        responseContent.append("</table></FORM>\r\n");
        responseContent
                .append("<CENTER><HR WIDTH=\"75%\" NOSHADE color=\"blue\"></CENTER>");

        // POST
        responseContent
                .append("<CENTER>POST FORM<HR WIDTH=\"75%\" NOSHADE color=\"blue\"></CENTER>");
        responseContent.append("<FORM ACTION=\"/formpost\" METHOD=\"POST\">");
        responseContent
                .append("<input type=hidden name=getform value=\"POST\">");
        responseContent.append("<table border=\"0\">");
        responseContent
                .append("<tr><td>Fill with value: <br> <input type=text name=\"info\" size=10></td></tr>");
        responseContent
                .append("<tr><td>Fill with value: <br> <input type=text name=\"secondinfo\" size=20>");
        responseContent
                .append("<tr><td>Fill with value: <br> <textarea name=\"thirdinfo\" cols=40 rows=10></textarea>");
        responseContent
                .append("<tr><td>Fill with file (only file name will be transmitted): <br> <input type=file name=\"myfile\">");
        responseContent.append("</td></tr>");
        responseContent
                .append("<tr><td><INPUT TYPE=\"submit\" NAME=\"Send\" VALUE=\"Send\"></INPUT></td>");
        responseContent
                .append("<td><INPUT TYPE=\"reset\" NAME=\"Clear\" VALUE=\"Clear\" ></INPUT></td></tr>");
        responseContent.append("</table></FORM>\r\n");
        responseContent
                .append("<CENTER><HR WIDTH=\"75%\" NOSHADE color=\"blue\"></CENTER>");

        //POST with enctype="multipart/form-data"
        responseContent
                .append("<CENTER>POST MULTIPART FORM<HR WIDTH=\"75%\" NOSHADE color=\"blue\"></CENTER>");
        responseContent
                .append("<FORM ACTION=\"/formpostmultipart\" ENCTYPE=\"multipart/form-data\" METHOD=\"POST\">");
        responseContent
                .append("<input type=hidden name=getform value=\"POST\">");
        responseContent.append("<table border=\"0\">");
        responseContent
                .append("<tr><td>Fill with value: <br> <input type=text name=\"info\" size=10></td></tr>");
        responseContent
                .append("<tr><td>Fill with value: <br> <input type=text name=\"secondinfo\" size=20>");
        responseContent
                .append("<tr><td>Fill with value: <br> <textarea name=\"thirdinfo\" cols=40 rows=10></textarea>");
        responseContent
                .append("<tr><td>Fill with file: <br> <input type=file name=\"myfile\">");
        responseContent.append("</td></tr>");
        responseContent
                .append("<tr><td><INPUT TYPE=\"submit\" NAME=\"Send\" VALUE=\"Send\"></INPUT></td>");
        responseContent
                .append("<td><INPUT TYPE=\"reset\" NAME=\"Clear\" VALUE=\"Clear\" ></INPUT></td></tr>");
        responseContent.append("</table></FORM>\r\n");
        responseContent
                .append("<CENTER><HR WIDTH=\"75%\" NOSHADE color=\"blue\"></CENTER>");

        responseContent.append("</body>");
        responseContent.append("</html>");

        ChannelBuffer buf = ChannelBuffers.copiedBuffer(responseContent
                .toString(), "UTF-8");
        // Build the response object.
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        response.setContent(buf);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE,
                "text/html; charset=UTF-8");
        response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(buf
                .readableBytes()));
        // Write the response.
        e.getChannel().write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        e.getCause().printStackTrace();
        e.getChannel().close();
    }
}
