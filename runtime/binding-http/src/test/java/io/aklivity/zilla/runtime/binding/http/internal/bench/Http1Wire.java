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

final class Http1Wire
{
    private Http1Wire()
    {
    }

    static byte[] request(
        int headerCount,
        byte[] body)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        write(out, String.format("%s / HTTP/1.1\r\n", body.length != 0 ? "POST" : "GET"));
        write(out, String.format("host: %s\r\n", BenchMessages.AUTHORITY));

        if (body.length != 0)
        {
            write(out, String.format("content-type: %s\r\ncontent-length: %d\r\n", BenchMessages.CONTENT_TYPE, body.length));
        }

        for (int index = 0; index < headerCount; index++)
        {
            write(out, String.format("%s: %s\r\n", BenchMessages.headerName(index), BenchMessages.headerValue(index)));
        }

        write(out, "\r\n");
        out.writeBytes(body);

        return out.toByteArray();
    }

    /**
     * @return the response as separately delivered fragments: the head, then the body, or the head, then one chunk
     *         per fragment and the terminating chunk when {@code chunkLength} is not zero
     */
    static byte[][] response(
        int headerCount,
        byte[] body,
        int chunkLength)
    {
        final boolean chunked = chunkLength != 0 && body.length != 0;
        final ByteArrayOutputStream head = new ByteArrayOutputStream();

        write(head, "HTTP/1.1 200 OK\r\n");
        write(head, String.format("content-type: %s\r\n", BenchMessages.CONTENT_TYPE));
        write(head, chunked ? "transfer-encoding: chunked\r\n" : String.format("content-length: %d\r\n", body.length));

        for (int index = 0; index < headerCount; index++)
        {
            write(head, String.format("%s: %s\r\n", BenchMessages.headerName(index), BenchMessages.headerValue(index)));
        }

        write(head, "\r\n");

        final byte[][] fragments;
        if (chunked)
        {
            final int chunks = (body.length + chunkLength - 1) / chunkLength;
            fragments = new byte[chunks + 2][];
            fragments[0] = head.toByteArray();

            for (int chunk = 0; chunk < chunks; chunk++)
            {
                final int offset = chunk * chunkLength;
                final int length = Math.min(chunkLength, body.length - offset);
                final ByteArrayOutputStream out = new ByteArrayOutputStream();

                write(out, String.format("%x\r\n", length));
                out.write(body, offset, length);
                write(out, "\r\n");
                fragments[chunk + 1] = out.toByteArray();
            }

            fragments[chunks + 1] = BenchMessages.ascii("0\r\n\r\n");
        }
        else
        {
            head.writeBytes(body);
            fragments = new byte[][] { head.toByteArray() };
        }

        return fragments;
    }

    private static void write(
        ByteArrayOutputStream out,
        String text)
    {
        out.writeBytes(BenchMessages.ascii(text));
    }
}
