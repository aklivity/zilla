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
import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.A2aTaskState;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiActivityType;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiContentType;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiEncryptedSubtype;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiEventMetaFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiRole;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiStateType;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiStepType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public final class AguiResponseMapper implements A2aEventSink
{
    public static final int EX_MAX = 1024;

    public static final String CODE_FAILED = "agent_failed";
    public static final String CODE_REJECTED = "agent_rejected";
    public static final String CODE_INCOMPLETE = "agent_incomplete";
    public static final String CODE_ABORTED = "agent_aborted";
    public static final String CODE_INVALID_RESPONSE = "agent_invalid_response";
    public static final String CODE_PROTOCOL = "agent_protocol";

    public static final String CUSTOM_DATA = "agent.data";
    public static final String CUSTOM_FILE = "agent.file";

    public static final String ACTIVITY_STATUS = "status";

    public static final String REASON_INPUT = "input";
    public static final String REASON_AUTHORIZATION = "authorization";

    private static final int STATUS_BAD_GATEWAY = 502;
    private static final int STATUS_UNPROCESSABLE = 422;
    private static final String MESSAGE_FAILED = "Agent failed";
    private static final String MESSAGE_REJECTED = "Agent declined";
    private static final String MESSAGE_TOO_LARGE = "Event too large";
    private static final String MESSAGE_PROTOCOL = "Agent response invalid";
    private static final String REPLACEMENT_SEPARATOR = "#";

    private interface Content
    {
        void text(
            DirectBufferEx buffer,
            int offset,
            int limit);

        void data(
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit);

        void file(
            String mediaType,
            String name,
            String url,
            String raw);
    }

    private interface TagContent extends Content
    {
        void end();
    }

    private interface ReplyEvents extends A2aEventSink
    {
        void streamEnded(
            boolean error);

        void streamAborted(
            String reason);
    }

    private final AguiFrameSink sink;
    private final AguiResponseHost host;
    private final int aguiTypeId;
    private final int maximum;
    private final MutableDirectBufferEx exBuffer = new UnsafeBufferEx(new byte[EX_MAX]);
    private final MutableDirectBufferEx statusBuffer;
    private final MutableDirectBufferEx jsonBuffer;
    private final DirectBufferEx emptyBuffer = new UnsafeBufferEx(new byte[0]);
    private final AguiDataExFW.Builder dataExRW = new AguiDataExFW.Builder();
    private final MessageDigest digest = digest();
    private final MessageDigest wholeDigest = digest();
    private final byte[] digestBytes;
    private final Set<String> finished = new HashSet<>();
    private final Set<String> messageIds = new HashSet<>();
    private final Map<String, Integer> artifacts = new HashMap<>();
    private final Runnable noop = () ->
    {
    };
    private final Runnable closeOpen = this::closeOpenMessage;
    private final ReplyEvents open = new OpenEvents();
    private final ReplyEvents ended = new EndedEvents();
    private final Content ignoreContent = new IgnoreContent();
    private final Content statusContent = new StatusContent();
    private final Content statusCompletedContent = new StatusCompletedContent();
    private final Content newContent = new NewContent();
    private final Content continueContent = new ContinueContent();
    private final TagContent ignoreTag = new IgnoreTag();
    private final TagContent stepTag = new StepTag();
    private final TagContent dataTagNew = new DataTagNew();
    private final TagContent dataTagFlowing = new DataTagFlowing();
    private final TagContent textTagNew = new TextTagNew();
    private final TagContent textTagFlowing = new TextTagFlowing();

    private ReplyEvents events = open;
    private Content content = ignoreContent;
    private Content resumeContent = ignoreContent;
    private TagContent tagContent = ignoreTag;
    private A2aPartTag tag;
    private Runnable closing = noop;
    private Runnable leading = noop;
    private A2aEventKind kind;
    private A2aTaskState state = A2aTaskState.UNSPECIFIED;
    private Boolean append;
    private String taskId;
    private String contextId;
    private String artifactId;
    private String messageId;
    private String openArtifactId;
    private int statusLength;
    private String statusMessageId;
    private String statusText;
    private String activityId;

    public AguiResponseMapper(
        AguiFrameSink sink,
        AguiResponseHost host,
        int aguiTypeId,
        int maximum)
    {
        this.sink = sink;
        this.host = host;
        this.aguiTypeId = aguiTypeId;
        this.maximum = maximum;
        this.statusBuffer = new UnsafeBufferEx(new byte[maximum]);
        this.jsonBuffer = new UnsafeBufferEx(new byte[maximum]);
        this.digestBytes = new byte[maximum];
    }

    public String taskId()
    {
        return taskId;
    }

    public String contextId()
    {
        return contextId;
    }

    public A2aTaskState state()
    {
        return state;
    }

    public boolean terminal()
    {
        return events == ended;
    }

    public void streamEnded(
        boolean error)
    {
        events.streamEnded(error);
    }

    public void streamAborted(
        String reason)
    {
        events.streamAborted(reason);
    }

    @Override
    public void eventStarted(
        A2aEventKind kind,
        String taskId,
        String contextId,
        A2aTaskState state,
        Boolean append)
    {
        events.eventStarted(kind, taskId, contextId, state, append);
    }

    @Override
    public void messageStarted(
        A2aMessageScope scope,
        String messageId)
    {
        events.messageStarted(scope, messageId);
    }

    @Override
    public void artifactStarted(
        String artifactId)
    {
        events.artifactStarted(artifactId);
    }

    @Override
    public void text(
        int flags,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        events.text(flags, buffer, offset, limit);
    }

    @Override
    public void data(
        int flags,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        events.data(flags, buffer, offset, limit);
    }

    @Override
    public void file(
        String mediaType,
        String name,
        String url,
        String raw)
    {
        events.file(mediaType, name, url, raw);
    }

    @Override
    public void tagStarted(
        A2aPartTag tag)
    {
        events.tagStarted(tag);
    }

    @Override
    public void tagEnded()
    {
        events.tagEnded();
    }

    @Override
    public void tagDropped(
        String event)
    {
        events.tagDropped(event);
    }

    @Override
    public void tagMalformed(
        String event,
        String detail)
    {
        events.tagMalformed(event, detail);
    }

    @Override
    public void artifactEnded()
    {
        events.artifactEnded();
    }

    @Override
    public void messageEnded(
        A2aMessageScope scope)
    {
        events.messageEnded(scope);
    }

    @Override
    public void eventEnded(
        boolean lastChunk)
    {
        events.eventEnded(lastChunk);
    }

    @Override
    public void failed(
        String code,
        String message)
    {
        events.failed(code, message);
    }

    private final class OpenEvents implements ReplyEvents
    {
        @Override
        public void streamEnded(
            boolean error)
        {
            if (error)
            {
                runError(STATUS_BAD_GATEWAY, CODE_ABORTED, null);
            }
            else if (state == A2aTaskState.COMPLETED)
            {
                runFinished(AguiOutcomeType.SUCCESS);
            }
            else
            {
                runError(STATUS_BAD_GATEWAY, CODE_INCOMPLETE, null);
            }
        }

        @Override
        public void streamAborted(
            String reason)
        {
            runError(STATUS_BAD_GATEWAY, CODE_ABORTED, reason);
        }

        @Override
        public void eventStarted(
            A2aEventKind kind,
            String taskId,
            String contextId,
            A2aTaskState state,
            Boolean append)
        {
            AguiResponseMapper.this.kind = kind;
            AguiResponseMapper.this.append = append;
            AguiResponseMapper.this.taskId = taskId != null ? taskId : AguiResponseMapper.this.taskId;
            content = ignoreContent;
            statusText = null;

            if (AguiResponseMapper.this.contextId == null && contextId != null)
            {
                AguiResponseMapper.this.contextId = contextId;

                final byte[] json = host.snapshot(contextId);

                if (json != null)
                {
                    leading = () -> leadSnapshot(json);
                }
            }

            if (kind == A2aEventKind.TASK || kind == A2aEventKind.STATUS_UPDATE)
            {
                AguiResponseMapper.this.state = state;
            }
            else if (kind == A2aEventKind.MESSAGE && AguiResponseMapper.this.state == A2aTaskState.UNSPECIFIED)
            {
                AguiResponseMapper.this.state = A2aTaskState.COMPLETED;
            }
        }

        @Override
        public void messageStarted(
            A2aMessageScope scope,
            String messageId)
        {
            if (scope == A2aMessageScope.STATUS)
            {
                content = state == A2aTaskState.COMPLETED ? statusCompletedContent : statusContent;
                statusLength = 0;
                statusMessageId = messageId;
            }
            else
            {
                closing.run();
                content = newContent;
                artifactId = null;
                AguiResponseMapper.this.messageId = messageId;
            }
        }

        @Override
        public void artifactStarted(
            String id)
        {
            final String artifact = id != null ? id : UUID.randomUUID().toString();
            final boolean seen = artifacts.containsKey(artifact);

            artifactId = artifact;

            if (!seen)
            {
                artifacts.put(artifact, 0);
                messageId = artifact;
                content = newContent;
            }
            else if (kind == A2aEventKind.ARTIFACT_UPDATE)
            {
                if (!Boolean.FALSE.equals(append) && artifact.equals(openArtifactId))
                {
                    content = continueContent;
                }
                else
                {
                    messageId = artifact + REPLACEMENT_SEPARATOR + artifacts.merge(artifact, 1, Integer::sum);
                    content = newContent;
                }
            }
            else
            {
                content = ignoreContent;
            }
        }

        @Override
        public void text(
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (limit > offset)
            {
                content.text(buffer, offset, limit);
            }
        }

        @Override
        public void data(
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            content.data(flags, buffer, offset, limit);
        }

        @Override
        public void file(
            String mediaType,
            String name,
            String url,
            String raw)
        {
            content.file(mediaType, name, url, raw);
        }

        @Override
        public void tagStarted(
            A2aPartTag tag)
        {
            AguiResponseMapper.this.tag = tag;
            resumeContent = content;
            tagContent = content == ignoreContent ? ignoreTag : tagContentOf(tag);
            content = tagContent;
        }

        @Override
        public void tagEnded()
        {
            tagContent.end();
            content = resumeContent;
        }

        @Override
        public void tagDropped(
            String event)
        {
            host.tagDropped(event);
        }

        @Override
        public void tagMalformed(
            String event,
            String detail)
        {
            host.tagMalformed(event, detail);
            runError(STATUS_BAD_GATEWAY, CODE_PROTOCOL, MESSAGE_PROTOCOL);
        }

        @Override
        public void artifactEnded()
        {
            if (kind == A2aEventKind.TASK && content != ignoreContent)
            {
                closing.run();
            }

            content = ignoreContent;
        }

        @Override
        public void messageEnded(
            A2aMessageScope scope)
        {
            if (scope == A2aMessageScope.STATUS)
            {
                statusMessage();
            }
            else
            {
                closing.run();
            }

            content = ignoreContent;
        }

        @Override
        public void eventEnded(
            boolean lastChunk)
        {
            switch (kind)
            {
            case ARTIFACT_UPDATE:
                if (lastChunk && artifactId != null && artifactId.equals(openArtifactId))
                {
                    closing.run();
                }
                break;
            case TASK:
            case STATUS_UPDATE:
                stateReached();
                break;
            default:
                break;
            }
        }

        @Override
        public void failed(
            String code,
            String message)
        {
            runError(STATUS_BAD_GATEWAY, CODE_INVALID_RESPONSE, message);
        }
    }

    private final class EndedEvents implements ReplyEvents
    {
        @Override
        public void streamEnded(
            boolean error)
        {
        }

        @Override
        public void streamAborted(
            String reason)
        {
        }

        @Override
        public void eventStarted(
            A2aEventKind kind,
            String taskId,
            String contextId,
            A2aTaskState state,
            Boolean append)
        {
        }

        @Override
        public void messageStarted(
            A2aMessageScope scope,
            String messageId)
        {
        }

        @Override
        public void artifactStarted(
            String artifactId)
        {
        }

        @Override
        public void text(
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
        }

        @Override
        public void data(
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
        }

        @Override
        public void file(
            String mediaType,
            String name,
            String url,
            String raw)
        {
        }

        @Override
        public void tagStarted(
            A2aPartTag tag)
        {
        }

        @Override
        public void tagEnded()
        {
        }

        @Override
        public void tagDropped(
            String event)
        {
        }

        @Override
        public void tagMalformed(
            String event,
            String detail)
        {
        }

        @Override
        public void artifactEnded()
        {
        }

        @Override
        public void messageEnded(
            A2aMessageScope scope)
        {
        }

        @Override
        public void eventEnded(
            boolean lastChunk)
        {
        }

        @Override
        public void failed(
            String code,
            String message)
        {
        }
    }

    private class IgnoreContent implements Content
    {
        @Override
        public void text(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
        }

        @Override
        public void data(
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
        }

        @Override
        public void file(
            String mediaType,
            String name,
            String url,
            String raw)
        {
        }
    }

    private class StatusContent extends IgnoreContent
    {
        @Override
        public void text(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            accumulateStatus(buffer, offset, limit - offset);
        }
    }

    private final class StatusCompletedContent extends StatusContent
    {
        @Override
        public void data(
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            emitData(flags, buffer, offset, limit);
        }

        @Override
        public void file(
            String mediaType,
            String name,
            String url,
            String raw)
        {
            emitFile(mediaType, name, url, raw);
        }
    }

    private final class IgnoreTag extends IgnoreContent implements TagContent
    {
        @Override
        public void end()
        {
        }
    }

    private final class StepTag extends IgnoreContent implements TagContent
    {
        @Override
        public void end()
        {
            emitTag(INIT | FIN, emptyBuffer, 0, 0);
        }
    }

    private final class DataTagNew extends IgnoreContent implements TagContent
    {
        @Override
        public void data(
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            emitTag(flags, buffer, offset, limit);
            tagContent = dataTagFlowing;
            content = tagContent;
        }

        @Override
        public void end()
        {
            emitTag(INIT | FIN, emptyBuffer, 0, 0);
        }
    }

    private final class DataTagFlowing extends IgnoreContent implements TagContent
    {
        @Override
        public void data(
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            sink.message(flags, null, buffer, offset, limit);
        }

        @Override
        public void end()
        {
        }
    }

    private final class TextTagNew extends IgnoreContent implements TagContent
    {
        @Override
        public void text(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            emitTag(INIT, buffer, offset, limit);
            tagContent = textTagFlowing;
            content = tagContent;
        }

        @Override
        public void end()
        {
            emitTag(INIT | FIN, emptyBuffer, 0, 0);
        }
    }

    private final class TextTagFlowing extends IgnoreContent implements TagContent
    {
        @Override
        public void text(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            sink.message(0, null, buffer, offset, limit);
        }

        @Override
        public void end()
        {
            sink.message(FIN, null, emptyBuffer, 0, 0);
        }
    }

    private abstract class MessageContent implements Content
    {
        @Override
        public void data(
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            emitData(flags, buffer, offset, limit);
        }

        @Override
        public void file(
            String mediaType,
            String name,
            String url,
            String raw)
        {
            emitFile(mediaType, name, url, raw);
        }
    }

    private final class NewContent extends MessageContent
    {
        @Override
        public void text(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            closing.run();
            openMessage(buffer, offset, limit);
            content = continueContent;
        }
    }

    private final class ContinueContent extends MessageContent
    {
        @Override
        public void text(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            digestUpdate(buffer, offset, limit);
            sink.message(0, null, buffer, offset, limit);
        }
    }

    private void emitData(
        int flags,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if ((flags & INIT) != 0)
        {
            closing.run();
            custom(CUSTOM_DATA, flags, buffer, offset, limit);
        }
        else
        {
            sink.message(flags, null, buffer, offset, limit);
        }
    }

    private void emitFile(
        String mediaType,
        String name,
        String url,
        String raw)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();

        if (mediaType != null)
        {
            builder.add("mimeType", mediaType);
        }

        if (name != null)
        {
            builder.add("name", name);
        }

        if (url != null)
        {
            builder.add("url", url);
        }
        else
        {
            builder.add("data", raw);
        }

        final byte[] json = builder.build().toString().getBytes(UTF_8);

        if (json.length > maximum)
        {
            tooLarge();
        }
        else
        {
            jsonBuffer.putBytes(0, json);
            closing.run();
            custom(CUSTOM_FILE, INIT | FIN, jsonBuffer, 0, json.length);
        }
    }

    private void accumulateStatus(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        if (statusLength + length > maximum)
        {
            tooLarge();
        }
        else
        {
            statusBuffer.putBytes(statusLength, buffer, offset, length);
            statusLength += length;
        }
    }

    private void openMessage(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        ensureSnapshot();

        openArtifactId = artifactId;
        closing = closeOpen;

        digest.reset();
        digestUpdate(buffer, offset, limit);

        sink.message(INIT, textEx(unique(messageId)), buffer, offset, limit);
    }

    private void closeOpenMessage()
    {
        closing = noop;
        openArtifactId = null;

        sink.message(FIN, null, emptyBuffer, 0, 0);
        finished.add(HexFormat.of().formatHex(digest.digest()));
    }

    private void emitWhole(
        String id,
        byte[] text)
    {
        closing.run();
        ensureSnapshot();

        final DirectBufferEx payload = new UnsafeBufferEx(text);

        sink.message(INIT, textEx(unique(id)), payload, 0, text.length);
        sink.message(FIN, null, emptyBuffer, 0, 0);

        finished.add(hash(text));
    }

    private void emitActivity(
        String id,
        String text)
    {
        final byte[] json = Json.createObjectBuilder().add("text", text).build().toString().getBytes(UTF_8);

        if (json.length > maximum)
        {
            tooLarge();
        }
        else
        {
            closing.run();
            ensureSnapshot();

            activityId = activityId != null ? activityId : unique(id);

            final AguiDataExFW dataEx = dataExRW.wrap(exBuffer, 0, exBuffer.capacity())
                .typeId(aguiTypeId)
                .activity(a -> a.meta(this::meta).type(t -> t.set(AguiActivityType.SNAPSHOT)).messageId(activityId)
                    .activityType(ACTIVITY_STATUS))
                .build();

            jsonBuffer.putBytes(0, json);
            sink.message(INIT | FIN, dataEx, jsonBuffer, 0, json.length);
        }
    }

    private void custom(
        String name,
        int flags,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        ensureSnapshot();

        final AguiDataExFW dataEx = dataExRW.wrap(exBuffer, 0, exBuffer.capacity())
            .typeId(aguiTypeId)
            .custom(c -> c.meta(this::meta).name(name))
            .build();

        sink.message(flags, dataEx, buffer, offset, limit);
    }

    private TagContent tagContentOf(
        A2aPartTag tag)
    {
        final TagContent next;

        switch (tag.event)
        {
        case A2aPartTag.EVENT_STEP:
            next = stepTag;
            break;
        case A2aPartTag.EVENT_REASONING:
        case A2aPartTag.EVENT_REASONING_ENCRYPTED:
            next = textTagNew;
            break;
        default:
            next = dataTagNew;
            break;
        }

        return next;
    }

    private void emitTag(
        int flags,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        closing.run();
        ensureSnapshot();

        sink.message(flags, tagEx(), buffer, offset, limit);
    }

    private AguiDataExFW tagEx()
    {
        final AguiDataExFW.Builder builder = dataExRW.wrap(exBuffer, 0, exBuffer.capacity()).typeId(aguiTypeId);

        switch (tag.event)
        {
        case A2aPartTag.EVENT_STATE:
            builder.state(s -> s.meta(this::meta).type(t -> t.set(stateType(tag.type))));
            break;
        case A2aPartTag.EVENT_ACTIVITY:
            builder.activity(a -> a.meta(this::meta)
                .type(t -> t.set("delta".equals(tag.type) ? AguiActivityType.DELTA : AguiActivityType.SNAPSHOT))
                .messageId(tag.messageId)
                .activityType(tag.activityType)
                .replace(Boolean.FALSE.equals(tag.replace) ? 0 : 1));
            break;
        case A2aPartTag.EVENT_REASONING:
            builder.reasoning(r -> r.meta(this::meta).messageId(tag.messageId).groupId(tag.groupId));
            break;
        case A2aPartTag.EVENT_REASONING_ENCRYPTED:
            builder.reasoningEncrypted(r -> r.meta(this::meta)
                .subtype(t -> t.set("toolCall".equals(tag.subtype)
                    ? AguiEncryptedSubtype.TOOL_CALL
                    : AguiEncryptedSubtype.MESSAGE))
                .entityId(tag.entityId));
            break;
        case A2aPartTag.EVENT_STEP:
            builder.step(s -> s.meta(this::meta)
                .type(t -> t.set("finished".equals(tag.type) ? AguiStepType.FINISHED : AguiStepType.STARTED))
                .stepName(tag.name));
            break;
        case A2aPartTag.EVENT_TOOL_CALL:
            builder.toolCall(c -> c.meta(this::meta)
                .toolCallId(tag.callId)
                .toolCallName(tag.callName)
                .parentMessageId(tag.parentMessageId));
            break;
        case A2aPartTag.EVENT_TOOL_RESULT:
            builder.toolResult(r -> r.meta(this::meta)
                .messageId(unique(tag.messageId))
                .toolCallId(tag.resultCallId)
                .content(c -> c.set(AguiContentType.TEXT)));
            break;
        case A2aPartTag.EVENT_RAW:
            builder.raw(r -> r.meta(this::meta).source(tag.source));
            break;
        default:
            builder.custom(c -> c.meta(this::meta).name(tag.name));
            break;
        }

        return builder.build();
    }

    private static AguiStateType stateType(
        String type)
    {
        return switch (type)
        {
        case "delta" -> AguiStateType.DELTA;
        case "messages" -> AguiStateType.MESSAGES_SNAPSHOT;
        default -> AguiStateType.SNAPSHOT;
        };
    }

    private void ensureSnapshot()
    {
        leading.run();
    }

    private void leadSnapshot(
        byte[] json)
    {
        leading = noop;

        final AguiDataExFW dataEx = dataExRW.wrap(exBuffer, 0, exBuffer.capacity())
            .typeId(aguiTypeId)
            .state(s -> s.meta(this::meta).type(t -> t.set(AguiStateType.MESSAGES_SNAPSHOT)))
            .build();

        sink.message(INIT | FIN, dataEx, new UnsafeBufferEx(json), 0, json.length);
    }

    private AguiDataExFW textEx(
        String id)
    {
        return dataExRW.wrap(exBuffer, 0, exBuffer.capacity())
            .typeId(aguiTypeId)
            .text(t -> t.meta(this::meta).messageId(id).role(r -> r.set(AguiRole.ASSISTANT)))
            .build();
    }

    private AguiEventMetaFW.Builder meta(
        AguiEventMetaFW.Builder meta)
    {
        return meta.timestamp(-1).subagentRunId((String) null);
    }

    private String unique(
        String id)
    {
        String unique = id;

        while (unique == null || !messageIds.add(unique))
        {
            unique = UUID.randomUUID().toString();
        }

        return unique;
    }

    private void statusMessage()
    {
        final String text = statusLength > 0 ? statusBuffer.getStringWithoutLengthUtf8(0, statusLength) : null;

        switch (state)
        {
        case COMPLETED:
            if (text != null)
            {
                final byte[] bytes = text.getBytes(UTF_8);

                if (!finished.contains(hash(bytes)))
                {
                    emitWhole(statusMessageId, bytes);
                }
            }
            break;
        case FAILED:
        case REJECTED:
        case INPUT_REQUIRED:
        case AUTH_REQUIRED:
            statusText = text;
            break;
        case WORKING:
        case SUBMITTED:
            if (text != null)
            {
                emitActivity(statusMessageId, text);
            }
            break;
        default:
            break;
        }

        statusLength = 0;
    }

    private void stateReached()
    {
        switch (state)
        {
        case INPUT_REQUIRED:
            interrupt(REASON_INPUT);
            break;
        case AUTH_REQUIRED:
            interrupt(REASON_AUTHORIZATION);
            break;
        case FAILED:
            runError(STATUS_BAD_GATEWAY, CODE_FAILED, statusText != null ? statusText : MESSAGE_FAILED);
            break;
        case REJECTED:
            runError(STATUS_UNPROCESSABLE, CODE_REJECTED, statusText != null ? statusText : MESSAGE_REJECTED);
            break;
        case CANCELED:
            runFinished(AguiOutcomeType.CANCELLED);
            break;
        default:
            break;
        }
    }

    private void interrupt(
        String reason)
    {
        closing.run();
        ensureSnapshot();

        final InterruptHandle handle = host.interruptHandle(taskId, contextId);
        final JsonObjectBuilder interrupt = Json.createObjectBuilder()
            .add("id", handle.id())
            .add("reason", reason);

        if (statusText != null)
        {
            interrupt.add("message", statusText);
        }

        interrupt.add("expiresAt", Instant.ofEpochMilli(handle.expiresAt()).toString());

        final String detail = Json.createObjectBuilder()
            .add("outcome", Json.createObjectBuilder()
                .add("interrupts", Json.createArrayBuilder().add(interrupt)))
            .build()
            .toString();

        events = ended;
        sink.runFinished(AguiOutcomeType.INTERRUPT, detail);
    }

    private void runFinished(
        AguiOutcomeType outcome)
    {
        closing.run();

        ensureSnapshot();

        events = ended;
        sink.runFinished(outcome, null);
    }

    private void runError(
        int status,
        String code,
        String message)
    {
        events = ended;
        sink.runError(status, code, message);
    }

    private void tooLarge()
    {
        runError(STATUS_BAD_GATEWAY, CODE_INVALID_RESPONSE, MESSAGE_TOO_LARGE);
    }

    private void digestUpdate(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        buffer.getBytes(offset, digestBytes, 0, limit - offset);
        digest.update(digestBytes, 0, limit - offset);
    }

    private String hash(
        byte[] text)
    {
        return HexFormat.of().formatHex(wholeDigest.digest(text));
    }

    private static MessageDigest digest()
    {
        try
        {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException(ex);
        }
    }
}
