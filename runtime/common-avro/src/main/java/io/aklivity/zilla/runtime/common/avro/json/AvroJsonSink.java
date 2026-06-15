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
package io.aklivity.zilla.runtime.common.avro.json;

import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.ADVANCED;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.COMPLETED;
import static io.aklivity.zilla.runtime.common.avro.json.AvroJson.branchName;

import java.util.Arrays;
import java.util.Base64;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroKind;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroType;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;

/**
 * Terminal <b>Avro → JSON</b> {@link AvroSink} that adapts the parsed {@link AvroEvent} stream to typed
 * calls on the wrapped {@link JsonGeneratorEx}, applying the {@link AvroJson} type mapping: records and maps
 * become JSON objects, arrays become JSON arrays, {@code bytes}/{@code fixed} become base64 strings, enums
 * become their symbol string, and a union value becomes {@code null} or the single-entry {@code {"<branch>":
 * value}} wrapper.
 * <p>
 * Completion is signalled at {@link AvroEvent#END_MESSAGE}. The sink assumes the generator's target buffer
 * holds the whole JSON datum; it does not implement output back-pressure (no {@code SUSPENDED}). A value
 * streamed across input windows ({@code string}/{@code bytes}/{@code fixed}) is handled: strings are joined
 * through the generator's fragment writes and {@code bytes}/{@code fixed} are coalesced before base64
 * encoding.
 */
final class AvroJsonSink implements AvroSink
{
    private static final int RECORD = 1;
    private static final int ARRAY = 2;
    private static final int MAP = 3;
    private static final int UNION_WRAP = 4;

    private final JsonGeneratorEx generator;

    private int[] frames;
    private int depth;
    private byte[] coalesced;
    private int coalescedLength;

    AvroJsonSink(
        JsonGeneratorEx generator)
    {
        this.generator = generator;
        this.frames = new int[16];
        this.depth = 0;
        this.coalesced = new byte[64];
    }

    @Override
    public Status feed(
        AvroController control,
        AvroSource source,
        AvroEvent event)
    {
        Status status = ADVANCED;
        switch (event)
        {
        case START_MESSAGE:
            break;
        case END_MESSAGE:
            status = COMPLETED;
            break;
        case START_RECORD:
            generator.writeStartObject();
            push(RECORD);
            break;
        case START_MAP:
            generator.writeStartObject();
            push(MAP);
            break;
        case START_ARRAY:
            generator.writeStartArray();
            push(ARRAY);
            break;
        case END_RECORD:
        case END_MAP:
        case END_ARRAY:
            generator.writeEnd();
            pop();
            closeUnionWrap();
            break;
        case FIELD_NAME:
            generator.writeKey(source.getField());
            break;
        case MAP_KEY:
            generator.writeKey(source.getKey());
            break;
        case UNION_BRANCH:
            openUnionWrap(source);
            break;
        case NULL:
            generator.writeNull();
            closeUnionWrap();
            break;
        case BOOLEAN:
            generator.write(source.getBoolean());
            closeUnionWrap();
            break;
        case INT:
            generator.write(source.getInt());
            closeUnionWrap();
            break;
        case LONG:
            generator.write(source.getLong());
            closeUnionWrap();
            break;
        case FLOAT:
            generator.write((double) source.getFloat());
            closeUnionWrap();
            break;
        case DOUBLE:
            generator.write(source.getDouble());
            closeUnionWrap();
            break;
        case ENUM:
            generator.write(source.getString());
            closeUnionWrap();
            break;
        case STRING:
            writeString(source);
            break;
        case BYTES:
        case FIXED:
            writeBase64(source);
            break;
        default:
            break;
        }
        return status;
    }

    @Override
    public void reset()
    {
        depth = 0;
        coalescedLength = 0;
        generator.reset();
    }

    private void openUnionWrap(
        AvroSource source)
    {
        AvroType branch = source.type();
        if (branch.kind() != AvroKind.NULL)
        {
            generator.writeStartObject();
            generator.writeKey(branchName(branch));
            push(UNION_WRAP);
        }
    }

    private void writeString(
        AvroSource source)
    {
        boolean deferred = source.deferredBytes() > 0;
        generator.write(source.getString(), deferred ? Completion.INCOMPLETE : Completion.COMPLETE);
        if (!deferred)
        {
            closeUnionWrap();
        }
    }

    private void writeBase64(
        AvroSource source)
    {
        DirectBuffer segment = source.getSegment();
        int length = segment.capacity();
        if (coalescedLength + length > coalesced.length)
        {
            coalesced = Arrays.copyOf(coalesced, Math.max(coalesced.length * 2, coalescedLength + length));
        }
        segment.getBytes(0, coalesced, coalescedLength, length);
        coalescedLength += length;
        if (source.deferredBytes() == 0)
        {
            generator.write(Base64.getEncoder().encodeToString(Arrays.copyOf(coalesced, coalescedLength)));
            coalescedLength = 0;
            closeUnionWrap();
        }
    }

    private void push(
        int frame)
    {
        if (depth == frames.length)
        {
            frames = Arrays.copyOf(frames, frames.length * 2);
        }
        frames[depth++] = frame;
    }

    private void pop()
    {
        depth--;
    }

    private void closeUnionWrap()
    {
        while (depth > 0 && frames[depth - 1] == UNION_WRAP)
        {
            generator.writeEnd();
            depth--;
        }
    }
}
