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
package io.aklivity.zilla.runtime.binding.llm.internal.openai;

import jakarta.json.JsonException;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

public final class LlmOpenaiRequestDecoder
{
    public enum Status
    {
        PENDING,
        BLOCKED,
        COMPLETE,
        REJECTED
    }

    public interface Sink
    {
        int available();

        void model(
            String model);

        void block(
            String type,
            int message,
            String extension);

        void data(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last);
    }

    @FunctionalInterface
    private interface Decoder
    {
        int decode(
            Request request,
            DirectBufferEx buffer,
            int offset,
            int progress,
            int limit);
    }

    private enum Scouted
    {
        FOUND,
        NEED_MORE,
        ABSENT
    }

    private enum Scout
    {
        MODEL,
        MESSAGE,
        PART
    }

    private static final DirectBufferEx BRACE = new UnsafeBufferEx(new byte[] {'{'});

    private static final int MIN_WINDOW = 64;
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;

    private static final String ROLE_TOOL = "tool";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_REFUSAL = "refusal";

    private static final String BLOCK_SYSTEM_INSTRUCTION = "system-instruction";
    private static final String BLOCK_USER_TEXT = "user-text";
    private static final String BLOCK_USER_IMAGE = "user-image";
    private static final String BLOCK_USER_DOCUMENT = "user-document";
    private static final String BLOCK_USER_AUDIO = "user-audio";
    private static final String BLOCK_ASSISTANT_TEXT = "assistant-text";
    private static final String BLOCK_ASSISTANT_REFUSAL = "assistant-refusal";
    private static final String BLOCK_TOOL_DEFINITION = "tool-definition";
    private static final String BLOCK_TOOL_CALL = "tool-call";
    private static final String BLOCK_TOOL_RESULT = "tool-result";
    private static final String BLOCK_UNKNOWN = "unknown";

    private final Decoder decodeModel = this::decodeModel;
    private final Decoder decodeStart = this::decodeStart;
    private final Decoder decodeRootStart = this::decodeRootStart;
    private final Decoder decodeRoot = this::decodeRoot;
    private final Decoder decodeMessagesStart = this::decodeMessagesStart;
    private final Decoder decodeMessages = this::decodeMessages;
    private final Decoder decodeMessageScout = this::decodeMessageScout;
    private final Decoder decodeMessage = this::decodeMessage;
    private final Decoder decodeContent = this::decodeContent;
    private final Decoder decodeRefusal = this::decodeRefusal;
    private final Decoder decodeToolCallsStart = this::decodeToolCallsStart;
    private final Decoder decodeToolCalls = this::decodeToolCalls;
    private final Decoder decodeToolsStart = this::decodeToolsStart;
    private final Decoder decodeTools = this::decodeTools;
    private final Decoder decodeParts = this::decodeParts;
    private final Decoder decodePartScout = this::decodePartScout;
    private final Decoder decodePart = this::decodePart;
    private final Decoder decodeCapture = this::decodeCapture;
    private final Decoder decodeText = this::decodeText;
    private final Decoder decodeSkip = this::decodeSkip;
    private final Decoder decodeEnd = this::decodeEnd;
    private final Decoder decodeIgnore = this::decodeIgnore;

    private final JsonParserEx scout;

    private byte[] textBytes;
    private UnsafeBufferEx textBuffer;

    private String scoutedModel;
    private String scoutedRole;
    private String scoutedToolCallId;
    private String scoutedType;
    private int scoutDepth;
    private int scoutWanted;

    public LlmOpenaiRequestDecoder()
    {
        this.scout = JsonEx.createParser();
        this.textBytes = new byte[1024];
        this.textBuffer = new UnsafeBufferEx(textBytes);
    }

    public Request newRequest(
        Sink sink,
        int hold)
    {
        return new Request(sink, hold);
    }

    public int decode(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        int progress = limit;

        if (request.status == Status.COMPLETE)
        {
            progress = trailing(request, buffer, offset, limit);
        }
        else if (request.status != Status.REJECTED)
        {
            try
            {
                request.status = request.status == Status.BLOCKED ? Status.PENDING : request.status;
                progress = decodeRequest(request, buffer, offset, limit, last);
            }
            catch (JsonException ex)
            {
                request.reject();
                progress = limit;
            }
        }

        return progress;
    }

    private int decodeRequest(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        int progress = offset;

        request.input = buffer;
        request.limit = limit;
        request.last = last;
        request.windowLimit = limit;

        if (request.decoder == decodeModel)
        {
            progress = request.decoder.decode(request, buffer, offset, progress, limit);
        }

        if (request.status == Status.PENDING && request.decoder != decodeModel)
        {
            final int window = (int) Math.min(limit - offset, request.sink.available());

            if (window < Math.min(MIN_WINDOW, limit - offset))
            {
                request.status = Status.BLOCKED;
            }
            else
            {
                progress = decodeWindow(request, buffer, offset, offset + window, limit);
            }
        }

        return progress;
    }

    private int decodeWindow(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int windowLimit,
        int limit)
    {
        final boolean windowLast = request.last && windowLimit == limit;

        request.windowLimit = windowLimit;
        request.captureFrom = offset;
        request.parser.wrap(buffer, offset, windowLimit, windowLast);

        Decoder previous = null;
        int progress = offset;
        while (progress <= limit && previous != request.decoder)
        {
            previous = request.decoder;
            progress = request.decoder.decode(request, buffer, offset, progress, limit);
        }

        if (request.captureOpen)
        {
            request.flushCapture(false);
            progress = request.position();
        }

        if (request.status == Status.COMPLETE)
        {
            progress = trailing(request, buffer, progress, limit);
        }
        else if (request.status == Status.PENDING && windowLast)
        {
            request.reject();
            progress = limit;
        }

        return progress;
    }

    private int decodeModel(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final Scouted scouted = scout(buffer, offset, limit, Scout.MODEL, false);

        if (scouted == Scouted.FOUND)
        {
            request.decoder = decodeStart;
            request.sink.model(scoutedModel);
        }
        else if (scouted == Scouted.ABSENT || request.last || limit - offset >= request.hold)
        {
            request.reject();
            progress = limit;
        }

        return progress;
    }

    private int decodeStart(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.START_DOCUMENT)
            {
                request.reject();
                break decode;
            }

            request.decoder = decodeRootStart;
        }

        return request.position();
    }

    private int decodeRootStart(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.START_OBJECT)
            {
                request.reject();
                break decode;
            }

            request.decoder = decodeRoot;
        }

        return request.position();
    }

    private int decodeRoot(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    break;
                }
                final CharSequence key = parser.getStringView();
                if (matches(key, "messages"))
                {
                    request.decoder = decodeMessagesStart;
                }
                else if (matches(key, "tools"))
                {
                    request.decoder = decodeToolsStart;
                }
                else
                {
                    request.skip(decodeRoot);
                    request.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                request.status = Status.COMPLETE;
                request.decoder = decodeEnd;
                break;
            default:
                request.reject();
                break decode;
            }

        }

        return request.position();
    }

    private int decodeMessagesStart(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.START_ARRAY)
            {
                request.reject();
                break decode;
            }

            request.message = -1;
            request.decoder = decodeMessages;
        }

        return request.position();
    }

    private int decodeMessages(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                request.message++;
                request.decoder = decodeMessageScout;
                break;
            case END_ARRAY:
                request.decoder = decodeRoot;
                break;
            default:
                request.reject();
                break decode;
            }

        }

        return request.position();
    }

    private int decodeMessageScout(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int at = request.position();
        final Scouted scouted = scout(buffer, at, limit, Scout.MESSAGE, true);

        if (scouted == Scouted.FOUND)
        {
            request.role = scoutedRole;
            request.toolCallId = scoutedToolCallId;
            request.decoder = decodeMessage;
        }
        else if (scouted == Scouted.ABSENT || request.last || limit - at >= request.hold)
        {
            request.reject();
        }

        return request.status == Status.REJECTED ? limit : request.position();
    }

    private int decodeMessage(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    break;
                }
                final CharSequence key = parser.getStringView();
                if (matches(key, "content"))
                {
                    request.decoder = decodeContent;
                }
                else if (matches(key, "refusal"))
                {
                    request.decoder = decodeRefusal;
                }
                else if (matches(key, "tool_calls"))
                {
                    request.decoder = decodeToolCallsStart;
                }
                else
                {
                    request.skip(decodeMessage);
                    request.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                request.decoder = decodeMessages;
                break;
            default:
                request.reject();
                break decode;
            }

        }

        return request.position();
    }

    private int decodeContent(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_STRING:
                request.text(textBlockType(request), textExtension(request), decodeMessage);
                progress = decodeText(request, buffer, offset, progress, limit, event);
                break decode;
            case START_ARRAY:
                request.decoder = decodeParts;
                break;
            case VALUE_NULL:
                request.decoder = decodeMessage;
                break;
            default:
                request.reject();
                break decode;
            }

        }

        return request.position();
    }

    private int decodeRefusal(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_STRING:
                request.text(BLOCK_ASSISTANT_REFUSAL, null, decodeMessage);
                progress = decodeText(request, buffer, offset, progress, limit, event);
                break decode;
            case VALUE_NULL:
                request.decoder = decodeMessage;
                break;
            default:
                request.reject();
                break decode;
            }

        }

        return request.position();
    }

    private int decodeToolCallsStart(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_ARRAY:
                request.decoder = decodeToolCalls;
                break;
            case VALUE_NULL:
                request.decoder = decodeMessage;
                break;
            default:
                request.reject();
                break decode;
            }

        }

        return request.position();
    }

    private int decodeToolCalls(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                request.capture(BLOCK_TOOL_CALL, request.message, decodeToolCalls);
                request.decoder = decodeCapture;
                break;
            case END_ARRAY:
                request.decoder = decodeMessage;
                break;
            default:
                request.reject();
                break decode;
            }

        }

        return request.position();
    }

    private int decodeToolsStart(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_ARRAY:
                request.decoder = decodeTools;
                break;
            case VALUE_NULL:
                request.decoder = decodeRoot;
                break;
            default:
                request.reject();
                break decode;
            }

        }

        return request.position();
    }

    private int decodeTools(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                request.capture(BLOCK_TOOL_DEFINITION, -1, decodeTools);
                request.decoder = decodeCapture;
                break;
            case END_ARRAY:
                request.decoder = decodeRoot;
                break;
            default:
                request.reject();
                break decode;
            }

        }

        return request.position();
    }

    private int decodeParts(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                request.decoder = decodePartScout;
                break;
            case END_ARRAY:
                request.decoder = decodeMessage;
                break;
            default:
                request.reject();
                break decode;
            }

        }

        return request.position();
    }

    private int decodePartScout(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int at = request.position();
        final Scouted scouted = scout(buffer, at, limit, Scout.PART, true);

        if (scouted == Scouted.FOUND)
        {
            if (TYPE_TEXT.equals(scoutedType) || TYPE_REFUSAL.equals(scoutedType))
            {
                request.decoder = decodePart;
            }
            else
            {
                request.capture(partBlockType(scoutedType), request.message, decodeParts);
                request.decoder = decodeCapture;
            }
        }
        else if (scouted == Scouted.ABSENT || request.last || limit - at >= request.hold)
        {
            request.reject();
        }

        return request.status == Status.REJECTED ? limit : request.position();
    }

    private int decodePart(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    break;
                }
                final CharSequence key = parser.getStringView();
                if (matches(key, TYPE_TEXT))
                {
                    request.text(textBlockType(request), textExtension(request), decodePart);
                    request.decoder = decodeText;
                }
                else if (matches(key, TYPE_REFUSAL))
                {
                    request.text(BLOCK_ASSISTANT_REFUSAL, null, decodePart);
                    request.decoder = decodeText;
                }
                else
                {
                    request.skip(decodePart);
                    request.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                request.decoder = decodeParts;
                break;
            default:
                request.reject();
                break decode;
            }

        }

        return request.position();
    }

    private int decodeCapture(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        while (request.decoder == decodeCapture && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                request.captureDepth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                request.captureDepth--;
                break;
            case VALUE_STRING:
                if (parser.deferredBytes())
                {
                    parser.consumed(parser.getStringView().length());
                }
                break;
            default:
                break;
            }

            if (request.captureDepth == 0)
            {
                request.flushCapture(true);
                request.captureOpen = false;
                request.decoder = request.captureThen;
            }

        }

        return request.position();
    }

    private int decodeText(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        while (request.decoder == decodeText && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            progress = decodeText(request, buffer, offset, progress, limit, event);
        }

        return request.position();
    }

    private int decodeText(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit,
        JsonEvent event)
    {
        final JsonParserEx parser = request.parser;

        request.decoder = decodeText;

        if (event == JsonEvent.VALUE_STRING)
        {
            if (!request.textOpen)
            {
                request.sink.block(request.textType, request.message, request.textExtension);
                request.textOpen = true;
            }

            final boolean fin = !parser.deferredBytes();
            final CharSequence chars = parser.getStringView();
            final int taken = chars.length();
            final int length = encode(request, chars, fin);
            request.sink.data(textBuffer, 0, length, fin);

            if (fin)
            {
                request.textOpen = false;
                request.decoder = request.textThen;
            }
            else
            {
                parser.consumed(taken);
            }
        }
        else if (event == JsonEvent.VALUE_NULL && !request.textOpen)
        {
            request.decoder = request.textThen;
        }
        else
        {
            request.reject();
        }

        return request.status == Status.REJECTED ? limit : request.position();
    }

    private int decodeSkip(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = request.parser;

        while (request.decoder == decodeSkip && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                request.skipDepth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                request.skipDepth--;
                break;
            default:
                break;
            }

            final boolean fragment = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

            if (fragment)
            {
                parser.consumed(parser.getStringView().length());
            }
            else if (event != JsonEvent.KEY_NAME && request.skipDepth == 0)
            {
                request.decoder = request.skipThen;
            }

        }

        return request.position();
    }

    private int decodeEnd(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return request.position();
    }

    private int decodeIgnore(
        Request request,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return limit;
    }

    private int trailing(
        Request request,
        DirectBufferEx buffer,
        int from,
        int limit)
    {
        int index = from;

        while (index < limit && isWhitespace(buffer.getByte(index)))
        {
            index++;
        }

        if (index < limit)
        {
            request.reject();
        }

        return limit;
    }

    private String textBlockType(
        Request request)
    {
        final String role = request.role;
        final String type;

        if (role == null)
        {
            type = BLOCK_UNKNOWN;
        }
        else
        {
            type = switch (role)
            {
            case "system", "developer" -> BLOCK_SYSTEM_INSTRUCTION;
            case "user" -> BLOCK_USER_TEXT;
            case "assistant" -> BLOCK_ASSISTANT_TEXT;
            case ROLE_TOOL -> BLOCK_TOOL_RESULT;
            default -> BLOCK_UNKNOWN;
            };
        }

        return type;
    }

    private String textExtension(
        Request request)
    {
        return ROLE_TOOL.equals(request.role) ? request.toolCallId : null;
    }

    private static String partBlockType(
        String type)
    {
        return switch (type)
        {
        case "image_url" -> BLOCK_USER_IMAGE;
        case "input_audio" -> BLOCK_USER_AUDIO;
        case "file" -> BLOCK_USER_DOCUMENT;
        default -> BLOCK_UNKNOWN;
        };
    }

    private Scouted scout(
        DirectBufferEx buffer,
        int from,
        int limit,
        Scout kind,
        boolean prefixed)
    {
        scoutedModel = null;
        scoutedRole = null;
        scoutedToolCallId = null;
        scoutedType = null;
        scoutDepth = 0;
        scoutWanted = 0;

        scout.reset();

        Scouted result = Scouted.NEED_MORE;

        if (prefixed)
        {
            scout.wrap(BRACE, 0, 1, false);
            result = scan(kind);
        }

        if (result == Scouted.NEED_MORE)
        {
            scout.wrap(buffer, from, limit, false);
            result = scan(kind);
        }

        return result;
    }

    private Scouted scan(
        Scout kind)
    {
        Scouted result = Scouted.NEED_MORE;

        scan:
        while (scout.hasNextEvent())
        {
            final JsonEvent event = scout.nextEvent();

            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                scoutDepth++;
                scoutWanted = 0;
                break;
            case END_OBJECT:
            case END_ARRAY:
                scoutDepth--;
                scoutWanted = 0;
                if (scoutDepth == 0)
                {
                    result = scouted(kind) ? Scouted.FOUND : Scouted.ABSENT;
                    break scan;
                }
                break;
            case KEY_NAME:
                if (!scout.deferredBytes())
                {
                    scoutWanted = scoutDepth == 1 ? wantedKey(kind, scout.getStringView()) : 0;
                }
                break;
            case VALUE_STRING:
                scoutString();
                break;
            default:
                scoutWanted = 0;
                break;
            }

            if (scoutDone(kind))
            {
                result = Scouted.FOUND;
                break;
            }
        }

        return result;
    }

    private void scoutString()
    {
        if (scout.deferredBytes())
        {
            scout.consumed(scout.getStringView().length());
        }
        else
        {
            if (scoutWanted != 0)
            {
                scouted(scoutWanted, scout.getString());
            }
            scoutWanted = 0;
        }
    }

    private static int wantedKey(
        Scout kind,
        CharSequence key)
    {
        int wanted = 0;

        if (kind == Scout.MODEL && matches(key, "model"))
        {
            wanted = 1;
        }
        else if (kind == Scout.MESSAGE && matches(key, "role"))
        {
            wanted = 2;
        }
        else if (kind == Scout.MESSAGE && matches(key, "tool_call_id"))
        {
            wanted = 3;
        }
        else if (kind == Scout.PART && matches(key, "type"))
        {
            wanted = 4;
        }

        return wanted;
    }

    private void scouted(
        int wanted,
        String value)
    {
        switch (wanted)
        {
        case 1:
            scoutedModel = value;
            break;
        case 2:
            scoutedRole = value;
            break;
        case 3:
            scoutedToolCallId = value;
            break;
        default:
            scoutedType = value;
            break;
        }
    }

    private boolean scouted(
        Scout kind)
    {
        return switch (kind)
        {
        case MODEL -> scoutedModel != null;
        case MESSAGE -> scoutedRole != null;
        case PART -> scoutedType != null;
        };
    }

    private boolean scoutDone(
        Scout kind)
    {
        return switch (kind)
        {
        case MODEL -> scoutedModel != null;
        case MESSAGE -> scoutedRole != null && (!ROLE_TOOL.equals(scoutedRole) || scoutedToolCallId != null);
        case PART -> scoutedType != null;
        };
    }

    private int encode(
        Request request,
        CharSequence chars,
        boolean fin)
    {
        final int length = chars.length();

        if (textBytes.length < length * 3 + 8)
        {
            textBytes = new byte[length * 3 + 8];
            textBuffer = new UnsafeBufferEx(textBytes);
        }

        int position = 0;
        int index = 0;

        if (request.textHighSurrogate != 0 && length > 0)
        {
            final char c = chars.charAt(0);
            if (Character.isLowSurrogate(c))
            {
                position = put(Character.toCodePoint(request.textHighSurrogate, c), position);
                index = 1;
            }
            else
            {
                position = put(REPLACEMENT_CHARACTER, position);
            }
            request.textHighSurrogate = 0;
        }

        while (index < length)
        {
            final char c = chars.charAt(index++);

            if (Character.isHighSurrogate(c))
            {
                if (index < length)
                {
                    final char d = chars.charAt(index);
                    if (Character.isLowSurrogate(d))
                    {
                        position = put(Character.toCodePoint(c, d), position);
                        index++;
                    }
                    else
                    {
                        position = put(REPLACEMENT_CHARACTER, position);
                    }
                }
                else if (fin)
                {
                    position = put(REPLACEMENT_CHARACTER, position);
                }
                else
                {
                    request.textHighSurrogate = c;
                }
            }
            else if (Character.isLowSurrogate(c))
            {
                position = put(REPLACEMENT_CHARACTER, position);
            }
            else
            {
                position = put(c, position);
            }
        }

        if (fin && request.textHighSurrogate != 0)
        {
            position = put(REPLACEMENT_CHARACTER, position);
            request.textHighSurrogate = 0;
        }

        return position;
    }

    private int put(
        int codePoint,
        int position)
    {
        int next = position;

        if (codePoint < 0x80)
        {
            textBytes[next++] = (byte) codePoint;
        }
        else if (codePoint < 0x800)
        {
            textBytes[next++] = (byte) (0xC0 | codePoint >> 6);
            textBytes[next++] = (byte) (0x80 | codePoint & 0x3F);
        }
        else if (codePoint < 0x10000)
        {
            textBytes[next++] = (byte) (0xE0 | codePoint >> 12);
            textBytes[next++] = (byte) (0x80 | codePoint >> 6 & 0x3F);
            textBytes[next++] = (byte) (0x80 | codePoint & 0x3F);
        }
        else
        {
            textBytes[next++] = (byte) (0xF0 | codePoint >> 18);
            textBytes[next++] = (byte) (0x80 | codePoint >> 12 & 0x3F);
            textBytes[next++] = (byte) (0x80 | codePoint >> 6 & 0x3F);
            textBytes[next++] = (byte) (0x80 | codePoint & 0x3F);
        }

        return next;
    }

    private static boolean isWhitespace(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private static boolean matches(
        CharSequence chars,
        String value)
    {
        boolean matches = chars.length() == value.length();

        for (int i = 0; matches && i < value.length(); i++)
        {
            matches = chars.charAt(i) == value.charAt(i);
        }

        return matches;
    }

    public final class Request
    {
        private final Sink sink;
        private final int hold;
        private final JsonParserEx parser;

        private Decoder decoder;
        private Status status;

        private DirectBufferEx input;
        private int limit;
        private int windowLimit;
        private boolean last;

        private int message;
        private String role;
        private String toolCallId;

        private Decoder skipThen;
        private int skipDepth;

        private Decoder textThen;
        private String textType;
        private String textExtension;
        private boolean textOpen;
        private char textHighSurrogate;

        private Decoder captureThen;
        private boolean captureOpen;
        private int captureDepth;
        private int captureFrom;

        private Request(
            Sink sink,
            int hold)
        {
            this.sink = sink;
            this.hold = hold;
            this.parser = JsonEx.createParser();
            this.decoder = decodeModel;
            this.status = Status.PENDING;
        }

        public Status status()
        {
            return status;
        }

        private int position()
        {
            return windowLimit - parser.remaining();
        }

        private void reject()
        {
            status = Status.REJECTED;
            decoder = decodeIgnore;
        }

        private void skip(
            Decoder then)
        {
            skipThen = then;
            skipDepth = 0;
        }

        private void text(
            String type,
            String extension,
            Decoder then)
        {
            textType = type;
            textExtension = extension;
            textThen = then;
            textOpen = false;
        }

        private void capture(
            String type,
            int blockMessage,
            Decoder then)
        {
            sink.block(type, blockMessage, null);
            sink.data(BRACE, 0, 1, false);

            captureThen = then;
            captureOpen = true;
            captureDepth = 1;
            captureFrom = position();
        }

        private void flushCapture(
            boolean fin)
        {
            final int end = position();

            if (end > captureFrom || fin)
            {
                sink.data(input, captureFrom, end - captureFrom, fin);
            }

            captureFrom = end;
        }
    }
}
