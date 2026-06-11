/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.common.protobuf.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufCanonicalizer;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * A protobuf conformance testee for the binary category, driven by the native
 * {@code conformance_test_runner} over stdin/stdout. Each {@code ConformanceRequest} / {@code
 * ConformanceResponse} is itself Protobuf, so this reads and writes them with {@code
 * common-protobuf}'s own reader and writer — no {@code protobuf-java}.
 * <p>
 * It handles {@code protobuf_payload} in / {@code PROTOBUF} out by canonicalizing against the
 * schema compiled from the conformance {@code FileDescriptorSet}; JSON/text formats are skipped
 * (the protobuf ↔ JSON mapping is owned by {@code model-protobuf}); malformed input is reported as
 * a parse error.
 */
public final class ConformanceTestee
{
    // google.protobuf.conformance field numbers
    private static final int REQUEST_PROTOBUF_PAYLOAD = 1;
    private static final int REQUEST_JSON_PAYLOAD = 2;
    private static final int REQUEST_REQUESTED_OUTPUT_FORMAT = 3;
    private static final int REQUEST_MESSAGE_TYPE = 4;
    private static final int REQUEST_TEXT_PAYLOAD = 8;

    private static final int RESPONSE_PARSE_ERROR = 1;
    private static final int RESPONSE_RUNTIME_ERROR = 2;
    private static final int RESPONSE_PROTOBUF_PAYLOAD = 3;
    private static final int RESPONSE_SKIPPED = 5;

    private static final int WIRE_FORMAT_PROTOBUF = 1;

    private final ProtobufSchema schema;
    private final ProtobufCanonicalizer canonicalizer;
    private final MutableDirectBuffer canonical;
    private final MutableDirectBuffer response;

    public ConformanceTestee(
        ProtobufSchema schema)
    {
        this.schema = schema;
        this.canonicalizer = Protobuf.canonicalizer(schema);
        this.canonical = new UnsafeBuffer(new byte[1 << 20]);
        this.response = new UnsafeBuffer(new byte[1 << 20]);
    }

    public void run(
        InputStream in,
        OutputStream out) throws IOException
    {
        byte[] header = new byte[4];
        while (readFully(in, header, 4))
        {
            int length = (header[0] & 0xff) | (header[1] & 0xff) << 8 |
                (header[2] & 0xff) << 16 | (header[3] & 0xff) << 24;
            byte[] request = new byte[length];
            if (!readFully(in, request, length))
            {
                throw new IOException("truncated conformance request");
            }
            byte[] reply = handle(request);
            writeFrame(out, reply);
            out.flush();
        }
    }

    public byte[] handle(
        byte[] request)
    {
        UnsafeBuffer buffer = new UnsafeBuffer(request);
        ProtobufReader reader = new ProtobufReader().wrap(buffer, 0, request.length);

        int payloadOffset = -1;
        int payloadLength = 0;
        int requestedFormat = 0;
        boolean nonBinaryInput = false;
        String messageType = "";

        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            switch (number)
            {
            case REQUEST_PROTOBUF_PAYLOAD:
                payloadLength = reader.readLength();
                payloadOffset = reader.offset();
                reader.skip(payloadLength);
                break;
            case REQUEST_REQUESTED_OUTPUT_FORMAT:
                requestedFormat = reader.readVarint32();
                break;
            case REQUEST_MESSAGE_TYPE:
                messageType = readString(reader);
                break;
            case REQUEST_JSON_PAYLOAD:
            case REQUEST_TEXT_PAYLOAD:
                nonBinaryInput = true;
                reader.skip(reader.readLength());
                break;
            default:
                reader.skipField(number, wireType);
                break;
            }
        }

        ProtobufWriter writer = new ProtobufWriter().wrap(response, 0);
        if (nonBinaryInput || payloadOffset < 0 || requestedFormat != WIRE_FORMAT_PROTOBUF)
        {
            skipped(writer, "common-protobuf testee handles binary protobuf only");
        }
        else if (schema.message(messageType) == null)
        {
            skipped(writer, "unknown message " + messageType);
        }
        else
        {
            encodeResult(writer, messageType, buffer, payloadOffset, payloadLength);
        }

        byte[] reply = new byte[writer.length()];
        response.getBytes(0, reply);
        return reply;
    }

    private void encodeResult(
        ProtobufWriter writer,
        String messageType,
        UnsafeBuffer buffer,
        int payloadOffset,
        int payloadLength)
    {
        try
        {
            int length = canonicalizer.canonicalize(messageType, buffer, payloadOffset, payloadLength, canonical, 0);
            writer.writeTag(RESPONSE_PROTOBUF_PAYLOAD, ProtobufWireType.LEN);
            writer.writeBytes(canonical, 0, length);
        }
        catch (ProtobufException ex)
        {
            writer.writeTag(RESPONSE_PARSE_ERROR, ProtobufWireType.LEN);
            writer.writeBytes(message(ex).getBytes(StandardCharsets.UTF_8));
        }
        catch (RuntimeException ex)
        {
            writer.writeTag(RESPONSE_RUNTIME_ERROR, ProtobufWireType.LEN);
            writer.writeBytes(message(ex).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void skipped(
        ProtobufWriter writer,
        String reason)
    {
        writer.writeTag(RESPONSE_SKIPPED, ProtobufWireType.LEN);
        writer.writeBytes(reason.getBytes(StandardCharsets.UTF_8));
    }

    private static String message(
        Throwable ex)
    {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private static String readString(
        ProtobufReader reader)
    {
        int length = reader.readLength();
        int at = reader.offset();
        String value = reader.buffer().getStringWithoutLengthUtf8(at, length);
        reader.skip(length);
        return value;
    }

    private static void writeFrame(
        OutputStream out,
        byte[] message) throws IOException
    {
        int length = message.length;
        out.write(length & 0xff);
        out.write(length >>> 8 & 0xff);
        out.write(length >>> 16 & 0xff);
        out.write(length >>> 24 & 0xff);
        out.write(message);
    }

    private static boolean readFully(
        InputStream in,
        byte[] target,
        int length) throws IOException
    {
        int read = 0;
        while (read < length)
        {
            int count = in.read(target, read, length - read);
            if (count < 0)
            {
                break;
            }
            read += count;
        }
        return read == length;
    }

    public static void main(
        String[] args) throws IOException
    {
        byte[] descriptorSet = Files.readAllBytes(Path.of(args[0]));
        ProtobufSchema schema = Protobuf.schema(new UnsafeBuffer(descriptorSet), 0, descriptorSet.length);
        new ConformanceTestee(schema).run(System.in, System.out);
    }
}
