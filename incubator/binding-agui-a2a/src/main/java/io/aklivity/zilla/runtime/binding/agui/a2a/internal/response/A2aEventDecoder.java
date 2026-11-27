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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.response;

import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;

import java.util.Map;

import jakarta.json.JsonException;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.card.AguiA2aExtension;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.A2aFlag;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.A2aTaskState;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aArtifactUpdateDataExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aDataExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aMessageDataExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aStatusUpdateDataExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aTaskDataExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.StringEventPositions;

public final class A2aEventDecoder
{
    public static final String CODE_INVALID_MESSAGE = "invalid_message";

    private static final String ERROR_MALFORMED = "Malformed event";
    private static final String ERROR_TOO_LARGE = "Event too large";
    private static final String ERROR_TRUNCATED = "Truncated event";

    private enum TagSection
    {
        PART,
        METADATA,
        AGUI,
        DATA
    }

    private enum PartMember
    {
        URL,
        RAW,
        MEDIA_TYPE,
        FILENAME
    }

    @FunctionalInterface
    private interface A2aEventDecoderState
    {
        int decode();
    }

    private final A2aEventDecoderState decodeIdle = this::decodeIdle;
    private final A2aEventDecoderState decodeStart = this::decodeStart;
    private final A2aEventDecoderState decodeRootStart = this::decodeRootStart;
    private final A2aEventDecoderState decodeRoot = this::decodeRoot;
    private final A2aEventDecoderState decodeRootEnd = this::decodeRootEnd;
    private final A2aEventDecoderState decodeEnd = this::decodeEnd;
    private final A2aEventDecoderState decodeIgnore = this::decodeIgnore;
    private final A2aEventDecoderState decodeTaskStart = this::decodeTaskStart;
    private final A2aEventDecoderState decodeTask = this::decodeTask;
    private final A2aEventDecoderState decodeStatusUpdateStart = this::decodeStatusUpdateStart;
    private final A2aEventDecoderState decodeStatusUpdate = this::decodeStatusUpdate;
    private final A2aEventDecoderState decodeArtifactUpdateStart = this::decodeArtifactUpdateStart;
    private final A2aEventDecoderState decodeArtifactUpdate = this::decodeArtifactUpdate;
    private final A2aEventDecoderState decodeReplyStart = this::decodeReplyStart;
    private final A2aEventDecoderState decodeReplyEnd = this::decodeReplyEnd;
    private final A2aEventDecoderState decodeStatusValue = this::decodeStatusValue;
    private final A2aEventDecoderState decodeStatus = this::decodeStatus;
    private final A2aEventDecoderState decodeMessageValue = this::decodeMessageValue;
    private final A2aEventDecoderState decodeMessage = this::decodeMessage;
    private final A2aEventDecoderState decodeMessagePartsValue = this::decodeMessagePartsValue;
    private final A2aEventDecoderState decodeArtifactsValue = this::decodeArtifactsValue;
    private final A2aEventDecoderState decodeArtifacts = this::decodeArtifacts;
    private final A2aEventDecoderState decodeArtifactValue = this::decodeArtifactValue;
    private final A2aEventDecoderState decodeArtifact = this::decodeArtifact;
    private final A2aEventDecoderState decodeArtifactStart = this::decodeArtifactStart;
    private final A2aEventDecoderState decodeArtifactPartsValue = this::decodeArtifactPartsValue;
    private final A2aEventDecoderState decodeParts = this::decodeParts;
    private final A2aEventDecoderState decodePart = this::decodePart;
    private final A2aEventDecoderState decodePartText = this::decodePartText;
    private final A2aEventDecoderState decodeTextFragment = this::decodeTextFragment;
    private final A2aEventDecoderState decodePartValue = this::decodePartValue;
    private final A2aEventDecoderState decodeData = this::decodeData;
    private final A2aEventDecoderState decodeDataSeparator = this::decodeDataSeparator;
    private final A2aEventDecoderState decodePartStart = this::decodePartStart;
    private final A2aEventDecoderState decodeTaggedPartStart = this::decodeTaggedPartStart;
    private final A2aEventDecoderState decodeTaggedPart = this::decodeTaggedPart;
    private final A2aEventDecoderState decodeToolDataValue = this::decodeToolDataValue;
    private final A2aEventDecoderState decodeToolData = this::decodeToolData;
    private final A2aEventDecoderState decodeDroppedPartStart = this::decodeDroppedPartStart;
    private final A2aEventDecoderState decodeDroppedPart = this::decodeDroppedPart;
    private final A2aEventDecoderState decodeMalformedPartStart = this::decodeMalformedPartStart;

    private final A2aEventSink sink;
    private final int maximum;
    private final JsonParserEx parser =
        JsonEx.createParser(Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED));
    private final MutableDirectBufferEx slotBuffer;
    private final MutableDirectBufferEx textBuffer;
    private final A2aPartTag tag = new A2aPartTag();

    private final A2aEventDecoderState rootStart;
    private final A2aEventDecoderState taskThen;

    private A2aEventDecoderState decoder = decodeIdle;
    private A2aEventDecoderState statusThen;
    private A2aEventDecoderState messageThen;
    private A2aEventDecoderState artifactThen;
    private A2aEventDecoderState partsThen;
    private A2aEventDecoderState dataThen = decodePart;
    private A2aEventDecoderState textThen = decodePart;
    private TagSection tagSection;
    private TagSection tagEntering;
    private int tagSkipping;
    private String tagMember;
    private AguiA2aExtension extension;
    private A2aMessageScope messageScope;
    private PartMember partMember;
    private String taskId;
    private String contextId;
    private A2aTaskState state;
    private Boolean append;
    private boolean lastChunk;
    private String entityId;
    private String headEntityId;
    private String partUrl;
    private String partRaw;
    private String partMediaType;
    private String partFilename;
    private int carryLength;
    private int windowLimit;
    private boolean windowLast;
    private int dataFrom;
    private int dataLength;
    private int dataDepth;
    private char highSurrogate;

    public A2aEventDecoder(
        A2aEventSink sink,
        int maximum)
    {
        this(sink, maximum, A2aEventRoot.EVENT);
    }

    public A2aEventDecoder(
        A2aEventSink sink,
        int maximum,
        A2aEventRoot root)
    {
        this.sink = sink;
        this.maximum = maximum;
        this.rootStart = root == A2aEventRoot.TASK ? decodeTaskStart : decodeRootStart;
        this.taskThen = root == A2aEventRoot.TASK ? decodeRootEnd : decodeRoot;
        this.slotBuffer = new UnsafeBufferEx(new byte[maximum]);
        this.textBuffer = new UnsafeBufferEx(new byte[maximum + 4]);
    }

    public void decode(
        int flags,
        A2aDataExFW dataEx,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final boolean init = (flags & INIT) != 0;
        final boolean fin = (flags & FIN) != 0;

        if (decoder == decodeIdle && init)
        {
            parser.reset();
            carryLength = 0;
            head(dataEx);
            decoder = decodeStart;
        }
        else if (decoder == decodeIdle || init && decoder != decodeIgnore)
        {
            fail(ERROR_MALFORMED);
        }

        if (decoder != decodeIgnore)
        {
            decodeInput(fin, buffer, offset, limit);
        }

        if (fin && decoder == decodeEnd)
        {
            decoder = decodeIdle;
        }
        else if (fin && decoder != decodeIgnore)
        {
            fail(ERROR_TRUNCATED);
        }
    }

    public void activate(
        AguiA2aExtension extension)
    {
        this.extension = extension;
    }

    public void finish()
    {
        if (decoder != decodeIdle && decoder != decodeIgnore)
        {
            fail(ERROR_TRUNCATED);
        }
    }

    private void decodeInput(
        boolean last,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        do
        {
            final int copied = Math.min(maximum - carryLength, limit - progress);
            slotBuffer.putBytes(carryLength, buffer, progress, copied);
            progress += copied;

            windowLimit = carryLength + copied;
            windowLast = last && progress == limit;
            parser.wrap(slotBuffer, 0, windowLimit, windowLast);

            final int consumed = decodeWindow();

            if (decoder != decodeIgnore)
            {
                flushData(consumed);

                carryLength = windowLimit - consumed;
                System.arraycopy(slotBuffer.byteArray(), consumed, slotBuffer.byteArray(), 0, carryLength);

                if (copied == 0 && consumed == 0 && windowLimit == maximum)
                {
                    fail(ERROR_TOO_LARGE);
                }
            }
        }
        while (decoder != decodeIgnore && progress < limit);
    }

    private int decodeWindow()
    {
        int position = 0;

        try
        {
            int previous;
            A2aEventDecoderState previousDecoder;

            do
            {
                previous = position;
                previousDecoder = decoder;
                position = decoder.decode();
            }
            while ((position != previous || decoder != previousDecoder) && decoder != decodeIgnore);
        }
        catch (JsonException ex)
        {
            fail(ERROR_MALFORMED);
        }

        return position;
    }

    private int position()
    {
        return windowLimit - parser.remaining();
    }

    private int decodeIdle()
    {
        return position();
    }

    private void head(
        A2aDataExFW dataEx)
    {
        taskId = null;
        contextId = null;
        state = A2aTaskState.UNSPECIFIED;
        append = Boolean.FALSE;
        lastChunk = false;
        headEntityId = null;

        if (dataEx != null)
        {
            switch (dataEx.kind())
            {
            case A2aDataExFW.KIND_TASK:
                final A2aTaskDataExFW task = dataEx.task();
                taskId = task.taskId().asString();
                contextId = task.contextId().asString();
                state = task.state().get();
                headEntityId = task.messageId().asString();
                break;
            case A2aDataExFW.KIND_STATUS_UPDATE:
                final A2aStatusUpdateDataExFW statusUpdate = dataEx.statusUpdate();
                taskId = statusUpdate.taskId().asString();
                contextId = statusUpdate.contextId().asString();
                state = statusUpdate.state().get();
                headEntityId = statusUpdate.messageId().asString();
                break;
            case A2aDataExFW.KIND_MESSAGE:
                final A2aMessageDataExFW message = dataEx.message();
                taskId = message.taskId().asString();
                contextId = message.contextId().asString();
                headEntityId = message.messageId().asString();
                break;
            case A2aDataExFW.KIND_ARTIFACT_UPDATE:
                final A2aArtifactUpdateDataExFW artifactUpdate = dataEx.artifactUpdate();
                taskId = artifactUpdate.taskId().asString();
                contextId = artifactUpdate.contextId().asString();
                headEntityId = artifactUpdate.artifactId().asString();
                append = artifactUpdate.append().get() == A2aFlag.TRUE;
                lastChunk = artifactUpdate.lastChunk().get() == A2aFlag.TRUE;
                break;
            default:
                break;
            }
        }
    }

    private int decodeStart()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.START_DOCUMENT)
            {
                decoder = rootStart;
            }
            else
            {
                fail(ERROR_MALFORMED);
            }
        }

        return position();
    }

    private boolean lookaheadEntityId(
        String member)
    {
        boolean resolved = false;
        boolean pending = false;

        entityId = null;

        while (!resolved && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = parser.deferredBytes();

            switch (event)
            {
            case KEY_NAME:
                if (deferred)
                {
                    parser.consumed(0);
                }
                else if (matches(parser.getStringView(), member))
                {
                    pending = true;
                }
                else
                {
                    parser.skipValue();
                }
                break;
            case VALUE_STRING:
                if (deferred)
                {
                    parser.consumed(0);
                }
                else if (pending)
                {
                    entityId = parser.getString();
                    resolved = true;
                }
                break;
            default:
                resolved = pending || event == JsonEvent.END_OBJECT;
                break;
            }
        }

        return resolved || windowLimit == maximum || windowLast;
    }

    private int decodeRootStart()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.START_OBJECT)
            {
                decoder = decodeRoot;
            }
            else
            {
                fail(ERROR_MALFORMED);
            }
        }

        return position();
    }

    private int decodeRoot()
    {
        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                final CharSequence key = key();

                if (key == null)
                {
                    break;
                }
                else if (matches(key, "task"))
                {
                    decoder = decodeTaskStart;
                }
                else if (matches(key, "statusUpdate"))
                {
                    decoder = decodeStatusUpdateStart;
                }
                else if (matches(key, "artifactUpdate"))
                {
                    decoder = decodeArtifactUpdateStart;
                }
                else if (matches(key, "message"))
                {
                    decoder = decodeReplyStart;
                }
                else
                {
                    parser.skipValue();
                }
                break;
            case END_OBJECT:
                decoder = decodeRootEnd;
                break;
            default:
                fail(ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }

    private int decodeRootEnd()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.END_DOCUMENT)
            {
                decoder = decodeEnd;
            }
            else
            {
                fail(ERROR_MALFORMED);
            }
        }

        return position();
    }

    private int decodeEnd()
    {
        return position();
    }

    private int decodeIgnore()
    {
        return position();
    }

    private int decodeTaskStart()
    {
        return decodeEventStart(A2aEventKind.TASK, decodeTask);
    }

    private int decodeStatusUpdateStart()
    {
        return decodeEventStart(A2aEventKind.STATUS_UPDATE, decodeStatusUpdate);
    }

    private int decodeArtifactUpdateStart()
    {
        return decodeEventStart(A2aEventKind.ARTIFACT_UPDATE, decodeArtifactUpdate);
    }

    private int decodeEventStart(
        A2aEventKind eventKind,
        A2aEventDecoderState then)
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.START_OBJECT)
            {
                sink.eventStarted(eventKind, taskId, contextId, state, append);
                decoder = then;
            }
            else
            {
                fail(ERROR_MALFORMED);
            }
        }

        return position();
    }

    private int decodeTask()
    {
        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                final CharSequence key = key();

                if (key == null)
                {
                    break;
                }
                else if (matches(key, "status"))
                {
                    statusThen = decodeTask;
                    decoder = decodeStatusValue;
                }
                else if (matches(key, "artifacts"))
                {
                    decoder = decodeArtifactsValue;
                }
                else
                {
                    parser.skipValue();
                }
                break;
            case END_OBJECT:
                sink.eventEnded(lastChunk);
                decoder = taskThen;
                break;
            default:
                fail(ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }

    private int decodeStatusUpdate()
    {
        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                final CharSequence key = key();

                if (key == null)
                {
                    break;
                }
                else if (matches(key, "status"))
                {
                    statusThen = decodeStatusUpdate;
                    decoder = decodeStatusValue;
                }
                else
                {
                    parser.skipValue();
                }
                break;
            case END_OBJECT:
                sink.eventEnded(lastChunk);
                decoder = decodeRoot;
                break;
            default:
                fail(ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }

    private int decodeArtifactUpdate()
    {
        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                final CharSequence key = key();

                if (key == null)
                {
                    break;
                }
                else if (matches(key, "artifact"))
                {
                    decoder = decodeArtifactValue;
                }
                else
                {
                    parser.skipValue();
                }
                break;
            case END_OBJECT:
                sink.eventEnded(lastChunk);
                decoder = decodeRoot;
                break;
            default:
                fail(ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }

    private int decodeReplyStart()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.START_OBJECT)
            {
                sink.eventStarted(A2aEventKind.MESSAGE, taskId, contextId, state, append);
                sink.messageStarted(A2aMessageScope.REPLY, headEntityId);
                messageScope = A2aMessageScope.REPLY;
                messageThen = decodeReplyEnd;
                decoder = decodeMessage;
            }
            else
            {
                fail(ERROR_MALFORMED);
            }
        }

        return position();
    }

    private int decodeStatusValue()
    {
        if (parser.hasNextEvent())
        {
            decoder = parser.nextEvent() == JsonEvent.START_OBJECT ? decodeStatus : statusThen;
        }

        return position();
    }

    private int decodeStatus()
    {
        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                final CharSequence key = key();

                if (key == null)
                {
                    break;
                }
                else if (matches(key, "message"))
                {
                    decoder = decodeMessageValue;
                }
                else
                {
                    parser.skipValue();
                }
                break;
            case END_OBJECT:
                decoder = statusThen;
                break;
            default:
                fail(ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }

    private int decodeReplyEnd()
    {
        sink.eventEnded(lastChunk);
        decoder = decodeRoot;

        return position();
    }

    private int decodeMessageValue()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.START_OBJECT)
            {
                messageScope = A2aMessageScope.STATUS;
                messageThen = decodeStatus;
                sink.messageStarted(messageScope, headEntityId);
                decoder = decodeMessage;
            }
            else
            {
                decoder = decodeStatus;
            }
        }

        return position();
    }
    private int decodeMessage()
    {
        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                final CharSequence key = key();

                if (key == null)
                {
                    break;
                }
                else if (matches(key, "parts"))
                {
                    decoder = decodeMessagePartsValue;
                }
                else
                {
                    parser.skipValue();
                }
                break;
            case END_OBJECT:
                sink.messageEnded(messageScope);
                decoder = messageThen;
                break;
            default:
                fail(ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }

    private int decodeMessagePartsValue()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.START_ARRAY)
            {
                partsThen = decodeMessage;
                decoder = decodeParts;
            }
            else
            {
                decoder = decodeMessage;
            }
        }

        return position();
    }
    private int decodeArtifactsValue()
    {
        if (parser.hasNextEvent())
        {
            decoder = parser.nextEvent() == JsonEvent.START_ARRAY ? decodeArtifacts : decodeTask;
        }

        return position();
    }

    private int decodeArtifacts()
    {
        parser.mark();

        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case START_OBJECT:
                if (lookaheadEntityId("artifactId"))
                {
                    artifactThen = decodeArtifacts;
                    decoder = decodeArtifactStart;
                }

                parser.rewind();
                break;
            case END_ARRAY:
                decoder = decodeTask;
                break;
            default:
                fail(ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }
    private int decodeArtifactValue()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.START_OBJECT)
            {
                artifactThen = decodeArtifactUpdate;
                sink.artifactStarted(headEntityId);
                decoder = decodeArtifact;
            }
            else
            {
                decoder = decodeArtifactUpdate;
            }
        }

        return position();
    }
    private int decodeArtifactStart()
    {
        if (parser.hasNextEvent())
        {
            parser.nextEvent();
            sink.artifactStarted(entityId);
            decoder = decodeArtifact;
        }

        return position();
    }
    private int decodeArtifact()
    {
        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                final CharSequence key = key();

                if (key == null)
                {
                    break;
                }
                else if (matches(key, "parts"))
                {
                    decoder = decodeArtifactPartsValue;
                }
                else
                {
                    parser.skipValue();
                }
                break;
            case END_OBJECT:
                sink.artifactEnded();
                decoder = artifactThen;
                break;
            default:
                fail(ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }

    private int decodeArtifactPartsValue()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.START_ARRAY)
            {
                partsThen = decodeArtifact;
                decoder = decodeParts;
            }
            else
            {
                decoder = decodeArtifact;
            }
        }

        return position();
    }
    private int decodeParts()
    {
        parser.mark();

        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case START_OBJECT:
                if (extension == null)
                {
                    clearPart();
                    decoder = decodePart;
                }
                else
                {
                    if (lookaheadTag())
                    {
                        decoder = tagStart();
                    }

                    parser.rewind();
                }
                break;
            case END_ARRAY:
                decoder = partsThen;
                break;
            default:
                fail(ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }

    private boolean lookaheadTag()
    {
        boolean resolved = false;

        tag.clear();
        tagSection = TagSection.PART;
        tagEntering = null;
        tagSkipping = 0;
        tagMember = null;

        while (!resolved && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (parser.deferredBytes())
            {
                parser.consumed(0);
            }
            else if (tagSkipping > 0)
            {
                tagSkipping += event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY ? 1 :
                    event == JsonEvent.END_OBJECT || event == JsonEvent.END_ARRAY ? -1 : 0;
            }
            else
            {
                resolved = lookaheadTagEvent(event);
            }
        }

        if (!resolved)
        {
            tag.clear();
        }

        return resolved || windowLimit == maximum || windowLast;
    }

    private boolean lookaheadTagEvent(
        JsonEvent event)
    {
        boolean resolved = false;

        switch (event)
        {
        case KEY_NAME:
            lookaheadTagKey();
            break;
        case START_OBJECT:
            if (tagEntering != null)
            {
                tagSection = tagEntering;
            }
            else
            {
                tagSkipping = 1;
            }

            tagEntering = null;
            tagMember = null;
            break;
        case START_ARRAY:
            tagEntering = null;
            tagMember = null;
            tagSkipping = 1;
            break;
        case VALUE_STRING:
            if (tagMember != null)
            {
                lookaheadTagValue(parser.getString());
            }

            tagEntering = null;
            tagMember = null;
            break;
        case VALUE_TRUE:
        case VALUE_FALSE:
            if (tagSection == TagSection.AGUI && "replace".equals(tagMember))
            {
                tag.replace = event == JsonEvent.VALUE_TRUE;
            }

            tagEntering = null;
            tagMember = null;
            break;
        case END_OBJECT:
            resolved = lookaheadTagEnd();
            break;
        default:
            tagEntering = null;
            tagMember = null;
            break;
        }

        return resolved;
    }

    private void lookaheadTagKey()
    {
        final CharSequence key = parser.getStringView();

        switch (tagSection)
        {
        case PART:
            if (matches(key, "metadata"))
            {
                tagEntering = TagSection.METADATA;
            }
            else if (matches(key, "data"))
            {
                tagEntering = TagSection.DATA;
            }
            else
            {
                parser.skipValue();
            }
            break;
        case METADATA:
            if (matches(key, "agui"))
            {
                tagEntering = TagSection.AGUI;
            }
            else
            {
                parser.skipValue();
            }
            break;
        case AGUI:
            tagMember = A2aPartTag.TAG_MEMBERS.contains(key.toString()) ? key.toString() : null;
            lookaheadTagSkip();
            break;
        default:
            tagMember = A2aPartTag.DATA_MEMBERS.contains(key.toString()) ? key.toString() : null;
            lookaheadTagSkip();
            break;
        }
    }

    private void lookaheadTagSkip()
    {
        if (tagMember == null)
        {
            parser.skipValue();
        }
    }

    private void lookaheadTagValue(
        String value)
    {
        if (tagSection == TagSection.AGUI)
        {
            tag.tag(tagMember, value);
        }
        else if (tagSection == TagSection.DATA)
        {
            tag.data(tagMember, value);
        }
    }

    private boolean lookaheadTagEnd()
    {
        boolean resolved = false;

        switch (tagSection)
        {
        case AGUI:
            tagSection = TagSection.METADATA;
            resolved = !tag.needsData();
            break;
        case METADATA:
        case DATA:
            tagSection = TagSection.PART;
            break;
        default:
            resolved = true;
            break;
        }

        return resolved;
    }

    private A2aEventDecoderState tagStart()
    {
        final A2aEventDecoderState next;

        if (tag.tagged() && !extension.enabled(AguiA2aExtension.FEATURE_SUBAGENTS))
        {
            tag.subagentRunId = null;
        }

        if (!tag.tagged())
        {
            next = decodePartStart;
        }
        else if (!tag.known())
        {
            next = decodeDroppedPartStart;
        }
        else if (!extension.enabled(tag.feature()))
        {
            next = decodePartStart;
        }
        else if (tag.problem() != null)
        {
            next = decodeMalformedPartStart;
        }
        else
        {
            next = decodeTaggedPartStart;
        }

        return next;
    }

    private int decodePartStart()
    {
        if (parser.hasNextEvent())
        {
            parser.nextEvent();
            clearPart();
            dataThen = decodePart;
            textThen = decodePart;
            decoder = decodePart;
        }

        return position();
    }

    private int decodeTaggedPartStart()
    {
        if (parser.hasNextEvent())
        {
            parser.nextEvent();
            clearPart();
            dataThen = decodeTaggedPart;
            textThen = decodeTaggedPart;
            sink.tagStarted(tag);
            decoder = decodeTaggedPart;
        }

        return position();
    }

    private int decodeTaggedPart()
    {
        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                decodeTaggedPartMember();
                break;
            case END_OBJECT:
                sink.tagEnded();
                decoder = decodeParts;
                break;
            default:
                fail(ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }

    private void decodeTaggedPartMember()
    {
        final CharSequence key = key();

        if (key != null)
        {
            if (matches(key, "text"))
            {
                decoder = decodePartText;
            }
            else if (matches(key, "data"))
            {
                if (tag.needsData())
                {
                    decoder = decodeToolDataValue;
                }
                else
                {
                    dataFrom = position();
                    dataLength = 0;
                    dataDepth = 0;
                    dataThen = decodeTaggedPart;
                    decoder = decodeDataSeparator;
                }
            }
            else
            {
                parser.skipValue();
            }
        }
    }

    private int decodeToolDataValue()
    {
        if (parser.hasNextEvent())
        {
            decoder = parser.nextEvent() == JsonEvent.START_OBJECT ? decodeToolData : decodeTaggedPart;
        }

        return position();
    }

    private int decodeToolData()
    {
        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                final CharSequence key = key();

                if (key == null)
                {
                    break;
                }
                else if (matches(key, tag.dataMember()))
                {
                    dataFrom = position();
                    dataLength = 0;
                    dataDepth = 0;
                    dataThen = decodeToolData;
                    decoder = decodeDataSeparator;
                }
                else
                {
                    parser.skipValue();
                }
                break;
            case END_OBJECT:
                decoder = decodeTaggedPart;
                break;
            default:
                fail(ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }

    private int decodeDroppedPartStart()
    {
        if (parser.hasNextEvent())
        {
            parser.nextEvent();
            sink.tagDropped(tag.event);
            decoder = decodeDroppedPart;
        }

        return position();
    }

    private int decodeDroppedPart()
    {
        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                if (key() != null)
                {
                    parser.skipValue();
                }
                break;
            case END_OBJECT:
                decoder = decodeParts;
                break;
            default:
                fail(ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }

    private int decodeMalformedPartStart()
    {
        if (parser.hasNextEvent())
        {
            parser.nextEvent();
            decoder = decodeIgnore;
            sink.tagMalformed(tag.event, tag.problem());
        }

        return position();
    }

    private int decodePart()
    {
        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                decodePartMember();
                break;
            case END_OBJECT:
                endPart();
                decoder = decodeParts;
                break;
            default:
                fail(ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }

    private void decodePartMember()
    {
        final CharSequence key = key();

        if (key != null)
        {
            if (matches(key, "text"))
            {
                decoder = decodePartText;
            }
            else if (matches(key, "data"))
            {
                dataFrom = position();
                dataLength = 0;
                dataDepth = 0;
                dataThen = decodePart;
                decoder = decodeDataSeparator;
            }
            else if (matches(key, "url"))
            {
                partMember = PartMember.URL;
                decoder = decodePartValue;
            }
            else if (matches(key, "raw"))
            {
                partMember = PartMember.RAW;
                decoder = decodePartValue;
            }
            else if (matches(key, "mediaType"))
            {
                partMember = PartMember.MEDIA_TYPE;
                decoder = decodePartValue;
            }
            else if (matches(key, "filename"))
            {
                partMember = PartMember.FILENAME;
                decoder = decodePartValue;
            }
            else
            {
                parser.skipValue();
            }
        }
    }

    private int decodePartText()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.VALUE_STRING)
            {
                final boolean deferred = parser.deferredBytes();

                emitText(parser.getStringView(), INIT | (deferred ? 0 : FIN));

                if (deferred)
                {
                    parser.consumed(parser.getStringView().length());
                    decoder = decodeTextFragment;
                }
                else
                {
                    decoder = textThen;
                }
            }
            else
            {
                decoder = textThen;
            }
        }

        return position();
    }

    private int decodeTextFragment()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.VALUE_STRING)
            {
                final boolean deferred = parser.deferredBytes();

                emitText(parser.getStringView(), deferred ? 0 : FIN);

                if (deferred)
                {
                    parser.consumed(parser.getStringView().length());
                }
                else
                {
                    decoder = textThen;
                }
            }
            else
            {
                fail(ERROR_MALFORMED);
            }
        }

        return position();
    }

    private int decodePartValue()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.VALUE_STRING)
            {
                if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final String value = parser.getString();

                    switch (partMember)
                    {
                    case URL:
                        partUrl = value;
                        break;
                    case RAW:
                        partRaw = value;
                        break;
                    case MEDIA_TYPE:
                        partMediaType = value;
                        break;
                    case FILENAME:
                        partFilename = value;
                        break;
                    default:
                        break;
                    }

                    decoder = decodePart;
                }
            }
            else
            {
                decoder = decodePart;
            }
        }

        return position();
    }

    private int decodeDataSeparator()
    {
        final int start = valueStart(slotBuffer, dataFrom, windowLimit);

        if (start < windowLimit)
        {
            dataFrom = start;
            decoder = decodeData;
        }

        return position();
    }

    private int decodeData()
    {
        while (decoder == decodeData && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean fragment = (event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER) &&
                parser.deferredBytes();

            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                dataDepth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                dataDepth--;
                break;
            default:
                break;
            }

            if (fragment)
            {
                parser.consumed(parser.getStringView().length());
            }
            else if (event != JsonEvent.KEY_NAME && dataDepth == 0)
            {
                captureData(position(), true);
                decoder = dataThen;
            }
        }

        return position();
    }

    private void flushData(
        int consumed)
    {
        if (decoder == decodeData)
        {
            captureData(consumed, false);
        }

        dataFrom = 0;
    }

    private void captureData(
        int to,
        boolean complete)
    {
        final int length = to - dataFrom;
        final int flags = (dataLength == 0 ? INIT : 0) | (complete ? FIN : 0);

        if (flags != 0 || length > 0)
        {
            sink.data(flags, slotBuffer, dataFrom, to);
            dataLength += Math.max(length, 1);
        }

        dataFrom = to;
    }

    private void endPart()
    {
        if (dataLength == 0 && (partUrl != null || partRaw != null))
        {
            sink.file(partMediaType, partFilename, partUrl, partRaw);
        }

        clearPart();
    }

    private void clearPart()
    {
        partUrl = null;
        partRaw = null;
        partMediaType = null;
        partFilename = null;
        dataLength = 0;
    }

    private void emitText(
        CharSequence chars,
        int flags)
    {
        int textLength = 0;
        int textFlags = flags & INIT;

        for (int index = 0; index < chars.length(); index++)
        {
            final char unit = chars.charAt(index);

            if (textLength + 4 > maximum)
            {
                sink.text(textFlags, textBuffer, 0, textLength);
                textFlags = 0;
                textLength = 0;
            }

            if (Character.isHighSurrogate(unit))
            {
                highSurrogate = unit;
            }
            else if (Character.isLowSurrogate(unit) && highSurrogate != 0)
            {
                textLength = putCodePoint(textLength, Character.toCodePoint(highSurrogate, unit));
                highSurrogate = 0;
            }
            else
            {
                textLength = putCodePoint(textLength, unit);
            }
        }

        sink.text(textFlags | (flags & FIN), textBuffer, 0, textLength);

        if ((flags & FIN) != 0)
        {
            highSurrogate = 0;
        }
    }

    private int putCodePoint(
        int offset,
        int codePoint)
    {
        int length = offset;

        if (codePoint < 0x80)
        {
            textBuffer.putByte(length++, (byte) codePoint);
        }
        else if (codePoint < 0x800)
        {
            textBuffer.putByte(length++, (byte) (0xC0 | codePoint >> 6));
            textBuffer.putByte(length++, (byte) (0x80 | codePoint & 0x3F));
        }
        else if (codePoint < 0x10000)
        {
            textBuffer.putByte(length++, (byte) (0xE0 | codePoint >> 12));
            textBuffer.putByte(length++, (byte) (0x80 | codePoint >> 6 & 0x3F));
            textBuffer.putByte(length++, (byte) (0x80 | codePoint & 0x3F));
        }
        else
        {
            textBuffer.putByte(length++, (byte) (0xF0 | codePoint >> 18));
            textBuffer.putByte(length++, (byte) (0x80 | codePoint >> 12 & 0x3F));
            textBuffer.putByte(length++, (byte) (0x80 | codePoint >> 6 & 0x3F));
            textBuffer.putByte(length++, (byte) (0x80 | codePoint & 0x3F));
        }

        return length;
    }

    private CharSequence key()
    {
        final boolean deferred = parser.deferredBytes();

        if (deferred)
        {
            parser.consumed(0);
        }

        return deferred ? null : parser.getStringView();
    }

    private void fail(
        String message)
    {
        decoder = decodeIgnore;
        sink.failed(CODE_INVALID_MESSAGE, message);
    }

    private static int valueStart(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int index = skipWhitespace(buffer, offset, limit);

        if (index < limit && buffer.getByte(index) == ':')
        {
            index = skipWhitespace(buffer, index + 1, limit);
        }

        return index;
    }

    private static int skipWhitespace(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int index = offset;

        while (index < limit && isWhitespace(buffer.getByte(index)))
        {
            index++;
        }

        return index;
    }

    private static boolean isWhitespace(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private static boolean matches(
        CharSequence value,
        String name)
    {
        return name.contentEquals(value);
    }
}
