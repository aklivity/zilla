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
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiEventMetaFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiRole;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiStateType;
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

    public static final String CUSTOM_DATA = "agent.data";
    public static final String CUSTOM_FILE = "agent.file";

    public static final String REASON_INPUT = "input";
    public static final String REASON_AUTHORIZATION = "authorization";

    private static final int STATUS_BAD_GATEWAY = 502;
    private static final int STATUS_UNPROCESSABLE = 422;
    private static final String MESSAGE_FAILED = "Agent failed";
    private static final String MESSAGE_REJECTED = "Agent declined";
    private static final String MESSAGE_TOO_LARGE = "Event too large";
    private static final String REPLACEMENT_SEPARATOR = "#";

    private enum Phase
    {
        OPEN,
        ENDED
    }

    private enum Target
    {
        NONE,
        STATUS,
        ARTIFACT,
        REPLY
    }

    private enum Mode
    {
        SKIP,
        NEW,
        CONTINUE
    }

    private final AguiFrameSink sink;
    private final AguiResponseHost host;
    private final int aguiTypeId;
    private final int maximum;
    private final MutableDirectBufferEx exBuffer = new UnsafeBufferEx(new byte[EX_MAX]);
    private final MutableDirectBufferEx statusBuffer;
    private final MutableDirectBufferEx fileBuffer;
    private final DirectBufferEx emptyBuffer = new UnsafeBufferEx(new byte[0]);
    private final AguiDataExFW.Builder dataExRW = new AguiDataExFW.Builder();
    private final MessageDigest digest = digest();
    private final MessageDigest wholeDigest = digest();
    private final byte[] digestBytes;
    private final Set<String> finished = new HashSet<>();
    private final Set<String> messageIds = new HashSet<>();
    private final Map<String, Integer> artifacts = new HashMap<>();

    private Phase phase = Phase.OPEN;
    private Target target = Target.NONE;
    private Mode mode = Mode.SKIP;
    private A2aEventKind kind;
    private A2aTaskState state = A2aTaskState.UNSPECIFIED;
    private Boolean append;
    private String taskId;
    private String contextId;
    private byte[] snapshot;
    private String artifactId;
    private String messageId;
    private String openId;
    private String openArtifactId;
    private int statusLength;
    private String statusMessageId;
    private String statusText;
    private String pendingReplyId;
    private String pendingReplyText;

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
        this.fileBuffer = new UnsafeBufferEx(new byte[maximum]);
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
        return phase == Phase.ENDED;
    }

    public void streamEnded(
        boolean error)
    {
        if (phase == Phase.OPEN)
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
    }

    public void streamAborted(
        String reason)
    {
        if (phase == Phase.OPEN)
        {
            runError(STATUS_BAD_GATEWAY, CODE_ABORTED, reason);
        }
    }

    @Override
    public void eventStarted(
        A2aEventKind kind,
        String taskId,
        String contextId,
        A2aTaskState state,
        Boolean append)
    {
        if (phase == Phase.OPEN)
        {
            this.kind = kind;
            this.append = append;
            this.target = Target.NONE;
            this.statusText = null;
            this.taskId = taskId != null ? taskId : this.taskId;

            if (this.contextId == null && contextId != null)
            {
                this.contextId = contextId;
                this.snapshot = host.snapshot(contextId);
            }

            if (kind == A2aEventKind.TASK || kind == A2aEventKind.STATUS_UPDATE)
            {
                this.state = state;
            }
            else if (kind == A2aEventKind.MESSAGE && this.state == A2aTaskState.UNSPECIFIED)
            {
                this.state = A2aTaskState.COMPLETED;
            }
        }
    }

    @Override
    public void messageStarted(
        A2aMessageScope scope,
        String messageId)
    {
        if (phase == Phase.OPEN)
        {
            if (scope == A2aMessageScope.STATUS)
            {
                target = Target.STATUS;
                statusLength = 0;
                statusMessageId = messageId;
            }
            else
            {
                closeMessage();
                target = Target.REPLY;
                mode = Mode.NEW;
                artifactId = null;
                this.messageId = messageId;
            }
        }
    }

    @Override
    public void artifactStarted(
        String artifactId)
    {
        if (phase == Phase.OPEN)
        {
            final String id = artifactId != null ? artifactId : UUID.randomUUID().toString();
            final boolean seen = artifacts.containsKey(id);

            target = Target.ARTIFACT;
            this.artifactId = id;

            if (!seen)
            {
                artifacts.put(id, 0);
                messageId = id;
                mode = Mode.NEW;
            }
            else if (kind == A2aEventKind.ARTIFACT_UPDATE)
            {
                if (!Boolean.FALSE.equals(append) && id.equals(openArtifactId))
                {
                    mode = Mode.CONTINUE;
                }
                else
                {
                    messageId = id + REPLACEMENT_SEPARATOR + artifacts.merge(id, 1, Integer::sum);
                    mode = Mode.NEW;
                }
            }
            else
            {
                mode = Mode.SKIP;
            }
        }
    }

    @Override
    public void text(
        int flags,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final int length = limit - offset;

        if (phase == Phase.OPEN && length > 0)
        {
            switch (target)
            {
            case STATUS:
                accumulateStatus(buffer, offset, length);
                break;
            case ARTIFACT:
            case REPLY:
                textMessage(buffer, offset, limit);
                break;
            default:
                break;
            }
        }
    }

    @Override
    public void data(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (phase == Phase.OPEN && contentTarget())
        {
            closeMessage();
            custom(CUSTOM_DATA, buffer, offset, limit);
        }
    }

    @Override
    public void file(
        String mediaType,
        String name,
        String url,
        String raw)
    {
        if (phase == Phase.OPEN && contentTarget())
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
                fileBuffer.putBytes(0, json);
                closeMessage();
                custom(CUSTOM_FILE, fileBuffer, 0, json.length);
            }
        }
    }

    @Override
    public void artifactEnded()
    {
        if (phase == Phase.OPEN)
        {
            if (kind == A2aEventKind.TASK && mode != Mode.SKIP)
            {
                closeMessage();
            }

            target = Target.NONE;
            mode = Mode.SKIP;
        }
    }

    @Override
    public void messageEnded(
        A2aMessageScope scope)
    {
        if (phase == Phase.OPEN)
        {
            if (scope == A2aMessageScope.STATUS)
            {
                statusMessage();
            }
            else
            {
                closeMessage();
            }

            target = Target.NONE;
            mode = Mode.SKIP;
        }
    }

    @Override
    public void eventEnded(
        boolean lastChunk)
    {
        if (phase == Phase.OPEN)
        {
            switch (kind)
            {
            case ARTIFACT_UPDATE:
                if (lastChunk && artifactId != null && artifactId.equals(openArtifactId))
                {
                    closeMessage();
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
    }

    @Override
    public void failed(
        String code,
        String message)
    {
        if (phase == Phase.OPEN)
        {
            runError(STATUS_BAD_GATEWAY, CODE_INVALID_RESPONSE, message);
        }
    }

    private boolean contentTarget()
    {
        return target == Target.REPLY ||
            target == Target.ARTIFACT && mode != Mode.SKIP ||
            target == Target.STATUS && state == A2aTaskState.COMPLETED;
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

    private void textMessage(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        switch (mode)
        {
        case NEW:
            closeMessage();
            openMessage(buffer, offset, limit);
            mode = Mode.CONTINUE;
            break;
        case CONTINUE:
            digestUpdate(buffer, offset, limit);
            sink.message(0, null, buffer, offset, limit);
            break;
        default:
            break;
        }
    }

    private void openMessage(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        ensureSnapshot();

        openId = unique(messageId);
        openArtifactId = target == Target.ARTIFACT ? artifactId : null;

        digest.reset();
        digestUpdate(buffer, offset, limit);

        sink.message(INIT, textEx(openId), buffer, offset, limit);
    }

    private void closeMessage()
    {
        if (openId != null)
        {
            sink.message(FIN, null, emptyBuffer, 0, 0);
            finished.add(HexFormat.of().formatHex(digest.digest()));
            openId = null;
            openArtifactId = null;
        }
    }

    private void emitWhole(
        String id,
        byte[] text)
    {
        closeMessage();
        ensureSnapshot();

        final DirectBufferEx payload = new UnsafeBufferEx(text);

        sink.message(INIT, textEx(unique(id)), payload, 0, text.length);
        sink.message(FIN, null, emptyBuffer, 0, 0);

        finished.add(hash(text));
    }

    private void custom(
        String name,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        ensureSnapshot();

        final AguiDataExFW dataEx = dataExRW.wrap(exBuffer, 0, exBuffer.capacity())
            .typeId(aguiTypeId)
            .custom(c -> c.meta(this::meta).name(name))
            .build();

        sink.message(INIT | FIN, dataEx, buffer, offset, limit);
    }

    private void ensureSnapshot()
    {
        if (snapshot != null)
        {
            final byte[] json = snapshot;
            snapshot = null;

            final AguiDataExFW dataEx = dataExRW.wrap(exBuffer, 0, exBuffer.capacity())
                .typeId(aguiTypeId)
                .state(s -> s.meta(this::meta).type(t -> t.set(AguiStateType.MESSAGES_SNAPSHOT)))
                .build();

            sink.message(INIT | FIN, dataEx, new UnsafeBufferEx(json), 0, json.length);
        }
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
                pendingReplyId = statusMessageId;
                pendingReplyText = text;
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
        closeMessage();
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

        phase = Phase.ENDED;
        sink.runFinished(AguiOutcomeType.INTERRUPT, detail);
    }

    private void runFinished(
        AguiOutcomeType outcome)
    {
        closeMessage();

        if (outcome == AguiOutcomeType.SUCCESS && finished.isEmpty() && pendingReplyText != null)
        {
            emitWhole(pendingReplyId, pendingReplyText.getBytes(UTF_8));
        }

        ensureSnapshot();

        phase = Phase.ENDED;
        sink.runFinished(outcome, null);
    }

    private void runError(
        int status,
        String code,
        String message)
    {
        phase = Phase.ENDED;
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
