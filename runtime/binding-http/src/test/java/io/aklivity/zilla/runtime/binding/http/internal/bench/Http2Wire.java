/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal.bench;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

final class Http2Wire
{
    static final int DATA = 0x00;
    static final int HEADERS = 0x01;
    static final int RST_STREAM = 0x03;
    static final int SETTINGS = 0x04;
    static final int GOAWAY = 0x07;
    static final int WINDOW_UPDATE = 0x08;

    static final int FLAG_END_STREAM = 0x01;
    static final int FLAG_ACK = 0x01;
    static final int FLAG_END_HEADERS = 0x04;

    static final int CONNECTION_WINDOW_INCREMENT = Integer.MAX_VALUE - 65_535;
    static final int MAX_CONCURRENT_STREAMS = 100;

    private static final int STREAM_ID_OFFSET = 5;
    private static final int STATIC_METHOD_GET = 2;
    private static final int STATIC_METHOD_POST = 3;
    private static final int STATIC_PATH_ROOT = 4;
    private static final int STATIC_SCHEME_HTTP = 6;
    private static final int STATIC_STATUS_200 = 8;
    private static final int STATIC_CONTENT_LENGTH = 28;
    private static final int STATIC_CONTENT_TYPE = 31;
    private static final int STATIC_AUTHORITY = 1;
    private static final int FIRST_DYNAMIC_INDEX = 62;

    private record Field(
        int staticIndex,
        int nameIndex,
        String name,
        String value)
    {
    }

    private Http2Wire()
    {
    }

    static byte[] preface()
    {
        return BenchMessages.ascii("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n");
    }

    static byte[] settings(
        int maxConcurrentStreams,
        int initialWindowSize)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        frameHeader(out, 12, SETTINGS, 0, 0);
        setting(out, 0x03, maxConcurrentStreams);
        setting(out, 0x04, initialWindowSize);

        return out.toByteArray();
    }

    static byte[] settingsAck()
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        frameHeader(out, 0, SETTINGS, FLAG_ACK, 0);

        return out.toByteArray();
    }

    static byte[] windowUpdate(
        int streamId,
        int increment)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        frameHeader(out, 4, WINDOW_UPDATE, 0, streamId);
        int32(out, increment);

        return out.toByteArray();
    }

    static byte[] requestHeaders(
        boolean first,
        int headerCount,
        int bodyLength)
    {
        final List<Field> fields = new ArrayList<>();
        fields.add(new Field(bodyLength != 0 ? STATIC_METHOD_POST : STATIC_METHOD_GET, 0, null, null));
        fields.add(new Field(STATIC_SCHEME_HTTP, 0, null, null));
        fields.add(new Field(STATIC_PATH_ROOT, 0, null, null));
        fields.add(new Field(0, STATIC_AUTHORITY, null, BenchMessages.AUTHORITY));

        if (bodyLength != 0)
        {
            fields.add(new Field(0, STATIC_CONTENT_TYPE, null, BenchMessages.CONTENT_TYPE));
            fields.add(new Field(0, STATIC_CONTENT_LENGTH, null, Integer.toString(bodyLength)));
        }

        extraHeaders(fields, headerCount);

        return headers(first, fields, bodyLength == 0);
    }

    static byte[] responseHeaders(
        boolean first,
        int headerCount,
        int bodyLength,
        boolean contentLength)
    {
        final List<Field> fields = new ArrayList<>();
        fields.add(new Field(STATIC_STATUS_200, 0, null, null));
        fields.add(new Field(0, STATIC_CONTENT_TYPE, null, BenchMessages.CONTENT_TYPE));

        if (contentLength || bodyLength == 0)
        {
            fields.add(new Field(0, STATIC_CONTENT_LENGTH, null, Integer.toString(bodyLength)));
        }

        extraHeaders(fields, headerCount);

        return headers(first, fields, bodyLength == 0);
    }

    static byte[] data(
        byte[] body,
        int offset,
        int length,
        boolean endStream)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        frameHeader(out, length, DATA, endStream ? FLAG_END_STREAM : 0, 0);
        out.write(body, offset, length);

        return out.toByteArray();
    }

    static void streamId(
        byte[] frame,
        int streamId)
    {
        frame[STREAM_ID_OFFSET] = (byte) (streamId >>> 24);
        frame[STREAM_ID_OFFSET + 1] = (byte) (streamId >>> 16);
        frame[STREAM_ID_OFFSET + 2] = (byte) (streamId >>> 8);
        frame[STREAM_ID_OFFSET + 3] = (byte) streamId;
    }

    private static void extraHeaders(
        List<Field> fields,
        int headerCount)
    {
        for (int index = 0; index < headerCount; index++)
        {
            fields.add(new Field(0, 0, BenchMessages.headerName(index), BenchMessages.headerValue(index)));
        }
    }

    private static byte[] headers(
        boolean first,
        List<Field> fields,
        boolean endStream)
    {
        final int dynamicCount = (int) fields.stream().filter(f -> f.staticIndex() == 0).count();
        final ByteArrayOutputStream block = new ByteArrayOutputStream();

        int dynamicSeen = 0;
        for (Field field : fields)
        {
            if (field.staticIndex() != 0)
            {
                block.write(0x80 | field.staticIndex());
            }
            else if (first)
            {
                block.write(0x40 | field.nameIndex());
                if (field.nameIndex() == 0)
                {
                    string(block, field.name());
                }
                string(block, field.value());
                dynamicSeen++;
            }
            else
            {
                block.write(0x80 | (FIRST_DYNAMIC_INDEX + dynamicCount - 1 - dynamicSeen));
                dynamicSeen++;
            }
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        frameHeader(out, block.size(), HEADERS, FLAG_END_HEADERS | (endStream ? FLAG_END_STREAM : 0), 0);
        out.writeBytes(block.toByteArray());

        return out.toByteArray();
    }

    private static void string(
        ByteArrayOutputStream out,
        String value)
    {
        final byte[] octets = BenchMessages.ascii(value);
        out.write(octets.length);
        out.writeBytes(octets);
    }

    private static void setting(
        ByteArrayOutputStream out,
        int identifier,
        int value)
    {
        out.write(identifier >>> 8);
        out.write(identifier);
        int32(out, value);
    }

    private static void frameHeader(
        ByteArrayOutputStream out,
        int length,
        int type,
        int flags,
        int streamId)
    {
        out.write(length >>> 16);
        out.write(length >>> 8);
        out.write(length);
        out.write(type);
        out.write(flags);
        int32(out, streamId);
    }

    private static void int32(
        ByteArrayOutputStream out,
        int value)
    {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }
}
