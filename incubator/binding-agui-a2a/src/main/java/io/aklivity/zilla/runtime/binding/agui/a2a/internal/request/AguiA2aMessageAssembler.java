/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.request;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;
import java.util.function.Predicate;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRetained.Kind;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRetained.Resume;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;

public final class AguiA2aMessageAssembler
{
    public enum ResumeStatus
    {
        RESOLVED,
        CANCELLED
    }

    private static final int ABSENT = -1;

    private static final DirectBufferEx MESSAGE_OPEN = literal("{\"message\":{\"messageId\":");
    private static final DirectBufferEx ROLE = literal(",\"role\":\"ROLE_USER\"");
    private static final DirectBufferEx CONTEXT_ID = literal(",\"contextId\":");
    private static final DirectBufferEx TASK_ID = literal(",\"taskId\":");
    private static final DirectBufferEx PARTS_OPEN = literal(",\"parts\":[");
    private static final DirectBufferEx PARTS_CLOSE = literal("]");
    private static final DirectBufferEx METADATA_OPEN = literal(",\"metadata\":{");
    private static final DirectBufferEx METADATA_CLOSE = literal("}");
    private static final DirectBufferEx MESSAGE_CLOSE = literal("},\"configuration\":{\"historyLength\":0," +
        "\"acceptedOutputModes\":[\"text/plain\"]}}");
    private static final DirectBufferEx COMMA = literal(",");
    private static final DirectBufferEx TEXT_OPEN = literal("{\"text\":");
    private static final DirectBufferEx RAW_OPEN = literal("{\"raw\":");
    private static final DirectBufferEx URL_OPEN = literal("{\"url\":");
    private static final DirectBufferEx MEDIA_TYPE = literal(",\"mediaType\":");
    private static final DirectBufferEx PART_METADATA_OPEN = literal(",\"metadata\":");
    private static final DirectBufferEx OBJECT_CLOSE = literal("}");
    private static final DirectBufferEx DATA_OPEN = literal("{\"data\":{");
    private static final DirectBufferEx DATA_STATE = literal("\"state\":");
    private static final DirectBufferEx DATA_CONTEXT = literal("\"context\":");
    private static final DirectBufferEx DATA_CLOSE = literal("},\"mediaType\":\"application/json\"," +
        "\"metadata\":{\"agui\":{\"part\":\"input\"}}}");
    private static final DirectBufferEx DATA_PAYLOAD_OPEN = literal("{\"data\":");
    private static final DirectBufferEx DATA_EMPTY = literal("{\"data\":{}}");
    private static final DirectBufferEx FORWARDED_PROPS = literal("\"forwardedProps\":");
    private static final DirectBufferEx AGUI_RESUME_OPEN = literal("\"agui\":{\"resume\":");
    private static final DirectBufferEx AGUI_RESUME_CLOSE = literal("}");
    private static final DirectBufferEx QUOTE = literal("\"");

    private final UnsafeBufferEx escapeBuffer = new UnsafeBufferEx(new byte[0]);
    private final UnsafeBufferEx scratch = new UnsafeBufferEx(new byte[0]);

    private DirectBufferEx[] gatherBuffers = new DirectBufferEx[16];
    private int[] gatherOffsets = new int[16];
    private int[] gatherLengths = new int[16];
    private byte[] scratchBytes = new byte[256];
    private int gatherCount;
    private int gatherIndex;
    private int gatherProgress;
    private int gatherLength;
    private int scratchLength;
    private byte[] escapeBytes = new byte[256];
    private AguiA2aRetained retained;
    private DirectBufferEx slot;
    private String runId;
    private String contextId;
    private String taskId;
    private String interruptId;
    private boolean acceptsJson;
    private Predicate<String> acceptedMediaType = mediaType -> true;
    private String unacceptedMediaType;
    private int partCount;

    public AguiA2aMessageAssembler wrap(
        AguiA2aRetained retained,
        String runId,
        String contextId,
        String taskId,
        String interruptId,
        boolean acceptsJson)
    {
        this.retained = retained;
        this.slot = retained.buffer();
        this.runId = runId;
        this.contextId = contextId;
        this.taskId = taskId;
        this.interruptId = interruptId;
        this.acceptsJson = acceptsJson;
        return this;
    }

    public AguiA2aMessageAssembler accepting(
        Predicate<String> acceptedMediaType)
    {
        this.acceptedMediaType = acceptedMediaType;
        return this;
    }

    public String unacceptedMediaType()
    {
        return unacceptedMediaType;
    }

    public int contentLength()
    {
        unacceptedMediaType = null;

        gatherCount = 0;
        gatherIndex = 0;
        gatherProgress = 0;
        gatherLength = 0;
        scratchLength = 0;
        partCount = 0;

        write(MESSAGE_OPEN);
        emitMessageId();
        write(ROLE);

        if (contextId != null)
        {
            write(CONTEXT_ID);
            writeString(contextId);
        }

        if (taskId != null)
        {
            write(TASK_ID);
            writeString(taskId);
        }

        write(PARTS_OPEN);

        if (interruptId != null)
        {
            emitResumeParts();
        }
        else
        {
            emitMessageParts();
            emitDataPart();
        }

        write(PARTS_CLOSE);
        emitMetadata();
        write(MESSAGE_CLOSE);

        return gatherLength;
    }

    public void emit(
        AguiA2aBodySink sink)
    {
        contentLength();
        emit(sink, Integer.MAX_VALUE);
    }

    public int emit(
        AguiA2aBodySink sink,
        int limit)
    {
        int emitted = 0;

        while (gatherIndex < gatherCount && emitted < limit)
        {
            final int length = Math.min(gatherLengths[gatherIndex] - gatherProgress, limit - emitted);
            final DirectBufferEx buffer = gatherBuffers[gatherIndex] != null ? gatherBuffers[gatherIndex] : scratch;

            sink.write(buffer, gatherOffsets[gatherIndex] + gatherProgress, length);

            emitted += length;
            gatherProgress += length;

            if (gatherProgress == gatherLengths[gatherIndex])
            {
                gatherIndex++;
                gatherProgress = 0;
            }
        }

        return emitted;
    }

    public boolean emitted()
    {
        return gatherIndex == gatherCount;
    }

    private void emitMessageId()
    {
        final int last = interruptId == null ? retained.lastIndexOf(Kind.MESSAGE) : ABSENT;
        final int id = last > 0 && retained.kind(last - 1) == Kind.MESSAGE_ID ? last - 1 : ABSENT;

        if (id != ABSENT)
        {
            writeEntry(id);
        }
        else
        {
            writeString(runId);
        }
    }

    private void emitMessageParts()
    {
        int index = 0;

        while (index < retained.count())
        {
            final Kind kind = retained.kind(index);

            index = kind == Kind.PART_TEXT || kind == Kind.PART_RAW || kind == Kind.PART_URL ? emitPart(index, kind) : index + 1;
        }
    }

    private int emitPart(
        int head,
        Kind kind)
    {
        int index = head + 1;

        separate();
        write(kind == Kind.PART_TEXT ? TEXT_OPEN : kind == Kind.PART_RAW ? RAW_OPEN : URL_OPEN);
        writeEntry(head);

        if (index < retained.count() && retained.kind(index) == Kind.PART_MIME)
        {
            final String mediaType = slot.getStringWithoutLengthUtf8(retained.offset(index) + 1, retained.length(index) - 2);

            if (unacceptedMediaType == null && !acceptedMediaType.test(mediaType))
            {
                unacceptedMediaType = mediaType;
            }

            write(MEDIA_TYPE);
            writeEntry(index++);
        }

        if (index < retained.count() && retained.kind(index) == Kind.PART_METADATA)
        {
            write(PART_METADATA_OPEN);
            writeEntry(index++);
        }

        write(OBJECT_CLOSE);

        return index;
    }

    private void emitDataPart()
    {
        final int state = retained.indexOf(Kind.STATE);
        final int context = retained.indexOf(Kind.CONTEXT);

        if (acceptsJson && (state != ABSENT || context != ABSENT))
        {
            separate();
            write(DATA_OPEN);

            if (state != ABSENT)
            {
                write(DATA_STATE);
                writeEntry(state);
            }

            if (context != ABSENT)
            {
                if (state != ABSENT)
                {
                    write(COMMA);
                }

                write(DATA_CONTEXT);
                writeEntry(context);
            }

            write(DATA_CLOSE);
        }
    }

    private void emitResumeParts()
    {
        final Resume resume = retained.resumeFor(interruptId);

        separate();

        if (resume == null || resume.payloadEnd == ABSENT || resume.payloadEvent == JsonEvent.VALUE_NULL)
        {
            write(DATA_EMPTY);
        }
        else if (resume.payloadEvent == JsonEvent.VALUE_STRING)
        {
            write(TEXT_OPEN);
            writeRange(resume.payloadStart, resume.payloadEnd);
            write(OBJECT_CLOSE);
        }
        else
        {
            write(DATA_PAYLOAD_OPEN);
            writeRange(resume.payloadStart, resume.payloadEnd);
            write(OBJECT_CLOSE);
        }
    }

    private void emitMetadata()
    {
        final int members = retained.indexOf(Kind.FORWARDED_PROPS);
        final int value = retained.indexOf(Kind.FORWARDED_VALUE);
        final Resume resume = interruptId != null ? retained.resumeFor(interruptId) : null;
        final boolean resumed = resume != null && resume.metadataEnd != ABSENT && resume.metadataEvent != JsonEvent.VALUE_NULL;

        if (members != ABSENT || value != ABSENT || resumed)
        {
            write(METADATA_OPEN);

            if (members != ABSENT)
            {
                writeEntry(members);
            }
            else if (value != ABSENT)
            {
                write(FORWARDED_PROPS);
                writeEntry(value);
            }

            if (resumed)
            {
                if (members != ABSENT || value != ABSENT)
                {
                    write(COMMA);
                }

                write(AGUI_RESUME_OPEN);
                writeRange(resume.metadataStart, resume.metadataEnd);
                write(AGUI_RESUME_CLOSE);
            }

            write(METADATA_CLOSE);
        }
    }

    private void separate()
    {
        if (partCount++ > 0)
        {
            write(COMMA);
        }
    }

    private void gather(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        if (length > 0)
        {
            if (gatherCount == gatherBuffers.length)
            {
                gatherBuffers = Arrays.copyOf(gatherBuffers, gatherCount << 1);
                gatherOffsets = Arrays.copyOf(gatherOffsets, gatherCount << 1);
                gatherLengths = Arrays.copyOf(gatherLengths, gatherCount << 1);
            }

            if (buffer == escapeBuffer)
            {
                if (scratchLength + length > scratchBytes.length)
                {
                    scratchBytes = Arrays.copyOf(scratchBytes, Math.max(scratchBytes.length << 1, scratchLength + length));
                }

                buffer.getBytes(offset, scratchBytes, scratchLength, length);
                scratch.wrap(scratchBytes);

                gatherBuffers[gatherCount] = null;
                gatherOffsets[gatherCount] = scratchLength;
                scratchLength += length;
            }
            else
            {
                gatherBuffers[gatherCount] = buffer;
                gatherOffsets[gatherCount] = offset;
            }

            gatherLengths[gatherCount] = length;
            gatherCount++;
            gatherLength += length;
        }
    }

    private void write(
        DirectBufferEx literal)
    {
        gather(literal, 0, literal.capacity());
    }

    private void writeEntry(
        int index)
    {
        gather(slot, retained.offset(index), retained.length(index));
    }

    private void writeRange(
        int start,
        int end)
    {
        final int offset = retained.valueStart(start, end);

        gather(slot, offset, end - offset);
    }

    private void writeString(
        String value)
    {
        final int worst = value.length() * 6 + 2;
        if (escapeBytes.length < worst)
        {
            escapeBytes = new byte[worst];
        }

        int length = 0;
        escapeBytes[length++] = '"';

        int index = 0;
        while (index < value.length())
        {
            final int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);

            if (codePoint == '"' || codePoint == '\\')
            {
                escapeBytes[length++] = '\\';
                escapeBytes[length++] = (byte) codePoint;
            }
            else if (codePoint < 0x20)
            {
                length = escapeControl(codePoint, length);
            }
            else if (codePoint < 0x80)
            {
                escapeBytes[length++] = (byte) codePoint;
            }
            else
            {
                final byte[] encoded = new String(Character.toChars(codePoint)).getBytes(UTF_8);
                System.arraycopy(encoded, 0, escapeBytes, length, encoded.length);
                length += encoded.length;
            }
        }

        escapeBytes[length++] = '"';
        escapeBuffer.wrap(escapeBytes, 0, length);
        gather(escapeBuffer, 0, length);
    }

    private int escapeControl(
        int codePoint,
        int offset)
    {
        int length = offset;
        escapeBytes[length++] = '\\';

        switch (codePoint)
        {
        case '\b':
            escapeBytes[length++] = 'b';
            break;
        case '\f':
            escapeBytes[length++] = 'f';
            break;
        case '\n':
            escapeBytes[length++] = 'n';
            break;
        case '\r':
            escapeBytes[length++] = 'r';
            break;
        case '\t':
            escapeBytes[length++] = 't';
            break;
        default:
            escapeBytes[length++] = 'u';
            escapeBytes[length++] = '0';
            escapeBytes[length++] = '0';
            escapeBytes[length++] = (byte) Character.forDigit(codePoint >> 4, 16);
            escapeBytes[length++] = (byte) Character.forDigit(codePoint & 0xf, 16);
            break;
        }

        return length;
    }

    private static DirectBufferEx literal(
        String text)
    {
        return new UnsafeBufferEx(text.getBytes(UTF_8));
    }
}
