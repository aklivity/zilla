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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelRejection;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

// A section is a `{"text":…}` object, decoded on arrival and presented to the chain as one continuous text value.
// The chain is fed all but the trailing overlap bytes of what has arrived, so a pattern straddling two fragments is
// seen whole; what the chain produces for the bytes it was fed is embedded in an object of the section being
// delivered, and the held bytes are fed when more text arrives or the section ends.
//
// The members next to the text are carried untouched, after the text. Those that arrive before the text wait for it
// in half of a slot, and a section whose members before the text exceed that half is rejected. Once the text has
// been delivered, the members that follow it are released as the destination credit allows while the section
// continues, so their size is bounded by the credit and not by the slot. The members that arrive with the last
// fragment of the section ride in its closing frame when it fits the credit, and are released ahead of it otherwise.
final class LlmTextModelPipeline implements LlmGuardPipeline
{
    private static final String MEMBER_TEXT = "text";
    private static final byte[] PREFIX = "{\"text\":\"".getBytes(UTF_8);
    private static final byte OPEN = '{';
    private static final byte COMMA = ',';
    private static final byte CLOSE = '}';
    private static final int QUOTE_WIDTH = 1;
    private static final int ESCAPE_EXPANSION = 6;
    private static final int MEMBERS_CHUNK = 256;
    private static final DirectBufferEx EMPTY_TEXT = new UnsafeBufferEx(new byte[0]);

    @FunctionalInterface
    private interface Stage
    {
        ModelPipelineResult run(
            long traceId,
            long bindingId,
            long authorization,
            boolean flush,
            boolean last,
            int consumed,
            MutableDirectBufferEx dst,
            int dstIndex,
            int dstLimit);
    }

    private final ModelPipeline pipeline;
    private final int overlap;
    private final BufferPool pool;
    private final long streamId;
    private final MutableDirectBufferEx transformBuffer;
    private final LlmTextPipeline decoder;
    private final JsonGeneratorEx generator;
    private final ModelPipelineResult result;
    private final LlmSectionPipeline members;
    private final LlmEdgeFilter membersEdge;
    private final LlmSectionAttributes memberAttributes;

    private final Stage textStage;
    private final Stage tailStage;

    private Stage stage;
    private int slot;
    private MutableDirectBufferEx slotBuffer;
    private int textCapacity;
    private int memberLength;
    private int memberProgress;
    private int memberEmitted;
    private int textLength;
    private int textProgress;
    private int emitted;
    private boolean started;

    LlmTextModelPipeline(
        ModelPipeline pipeline,
        int overlap,
        BufferPool pool,
        long streamId,
        MutableDirectBufferEx transformBuffer,
        boolean optional)
    {
        this.pipeline = pipeline;
        this.overlap = overlap;
        this.pool = pool;
        this.streamId = streamId;
        this.transformBuffer = transformBuffer;
        this.slot = NO_SLOT;
        this.members = new LlmSectionPipeline(new LlmTextResidualTransform(), new UnsafeBufferEx(new byte[MEMBERS_CHUNK]));
        this.membersEdge = new LlmEdgeFilter((buffer, offset, length, last) -> onMember(buffer, offset, length));
        this.memberAttributes = new LlmSectionAttributes();
        this.decoder = new LlmTextPipeline(this::onText, optional);
        this.generator = JsonEx.createGenerator();
        this.result = new ModelPipelineResult();
        this.textStage = this::runText;
        this.tailStage = this::runTail;
        this.stage = textStage;
        decoder.begin();
    }

    static boolean objectShaped(
        LlmSectionType type)
    {
        return type == LlmSectionType.SYSTEM_INSTRUCTION || type == LlmSectionType.USER_TEXT ||
            type == LlmSectionType.ASSISTANT_TEXT || type == LlmSectionType.ASSISTANT_REFUSAL ||
            type == LlmSectionType.ASSISTANT_REASONING || type == LlmSectionType.TOOL_RESULT;
    }

    @Override
    public void reset()
    {
        pipeline.reset();
        decoder.begin();
        releaseSlot();
        stage = textStage;
        textLength = 0;
        textProgress = 0;
        memberLength = 0;
        memberProgress = 0;
        memberEmitted = 0;
        emitted = 0;
        started = false;
    }

    @Override
    public ModelPipelineResult accept(
        long traceId,
        long bindingId,
        long authorization,
        boolean sectionInit,
        boolean sectionFin,
        DirectBufferEx src,
        int srcIndex,
        int srcLimit,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        final int consumed = srcLimit - srcIndex;

        if (sectionInit)
        {
            decoder.begin();
            members.begin(memberAttributes);
            membersEdge.reset();
            stage = textStage;
            memberLength = 0;
            memberProgress = 0;
            memberEmitted = 0;
        }

        ModelPipelineResult outcome = null;

        if (consumed > 0 && !accepted(src, srcIndex, consumed, sectionFin))
        {
            outcome = result.set(ModelStatus.REJECTED, consumed, 0);
        }
        else
        {
            outcome = stage.run(traceId, bindingId, authorization, !sectionFin && releasable(), sectionFin, consumed,
                dst, dstIndex, dstLimit);
        }

        return outcome;
    }

    @Override
    public ModelPipelineResult finish(
        long traceId,
        long bindingId,
        long authorization,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        return stage.run(traceId, bindingId, authorization, true, true, 0, dst, dstIndex, dstLimit);
    }

    @Override
    public int closeWidth()
    {
        final boolean text = decoder.extracted();
        final int backlog = memberLength - memberProgress;
        final int open = memberEmitted > 0 ? 0 : text
            ? (emitted == 0 ? PREFIX.length : 0) + QUOTE_WIDTH + (backlog > 0 ? 1 : 0)
            : 1;

        return open + backlog + 1;
    }

    @Override
    public int close(
        MutableDirectBufferEx dst,
        int dstIndex)
    {
        int position = dstIndex;

        if (memberEmitted == 0)
        {
            if (decoder.extracted())
            {
                generator.wrap(dst, dstIndex, dst.capacity());

                if (emitted == 0)
                {
                    beginObject();
                }

                generator.writeEndEscaped();
                position += generator.length();

                if (memberLength > memberProgress)
                {
                    dst.putByte(position++, COMMA);
                }
            }
            else
            {
                dst.putByte(position++, OPEN);
            }
        }

        if (memberLength > memberProgress)
        {
            dst.putBytes(position, slotBuffer, textCapacity + memberProgress, memberLength - memberProgress);
            position += memberLength - memberProgress;
        }

        dst.putByte(position++, CLOSE);

        emitted = 0;
        memberLength = 0;
        memberProgress = 0;
        memberEmitted = 0;

        return position - dstIndex;
    }

    private boolean accepted(
        DirectBufferEx src,
        int srcIndex,
        int length,
        boolean sectionFin)
    {
        compactMembers();

        return acquireSlot() &&
            (decoder.completed() || textLength + length <= textCapacity) &&
            memberLength + length + QUOTE_WIDTH <= memberCapacity() &&
            decoder.feed(src, srcIndex, length, sectionFin) &&
            members.feed(src, srcIndex, length, sectionFin, membersEdge);
    }

    private boolean releasable()
    {
        return decoder.completed() && memberLength > memberProgress;
    }

    private boolean acquireSlot()
    {
        if (slot == NO_SLOT)
        {
            slot = pool.acquire(streamId);

            if (slot != NO_SLOT)
            {
                slotBuffer = pool.buffer(slot);
                textCapacity = pool.slotCapacity() / 2;
            }
        }

        return slot != NO_SLOT;
    }

    private void releaseSlot()
    {
        if (slot != NO_SLOT)
        {
            pool.release(slot);
            slot = NO_SLOT;
            slotBuffer = null;
        }
    }

    private int memberCapacity()
    {
        return pool.slotCapacity() - textCapacity;
    }

    private void onMember(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        slotBuffer.putBytes(textCapacity + memberLength, buffer, offset, length);
        memberLength += length;
    }

    private ModelPipelineResult runText(
        long traceId,
        long bindingId,
        long authorization,
        boolean flush,
        boolean last,
        int consumed,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        final int limit = feedLimit(flush);
        final boolean absent = decoder.completed() && !decoder.extracted();

        ModelStatus status = flush && absent ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW;
        ModelRejection rejection = null;
        int produced = 0;

        if (!absent && (limit > textProgress || flush))
        {
            final int innerFlags = (started ? 0 : Flags.INIT) | (flush ? Flags.FIN : 0);

            final int room = Math.min(Math.max(dstLimit - dstIndex - (emitted == 0 ? PREFIX.length : 0), 0) / ESCAPE_EXPANSION,
                transformBuffer.capacity());

            final ModelPipelineResult inner = pipeline.transform(traceId, bindingId, authorization, innerFlags,
                slot != NO_SLOT ? slotBuffer : EMPTY_TEXT, textProgress, limit, transformBuffer, 0, room);

            status = inner.status();
            rejection = inner.rejection();
            textProgress += inner.consumed();
            started = true;
            produced = embed(inner.produced(), dst, dstIndex, dstLimit);

            compact();
        }

        if (flush && status != ModelStatus.OVERFLOW && status != ModelStatus.REJECTED && status != ModelStatus.SUSPENDED)
        {
            stage = tailStage;

            final ModelPipelineResult tail = runTail(traceId, bindingId, authorization, flush, last, consumed, dst,
                dstIndex + produced, dstLimit);

            status = tail.status();
            produced += tail.produced();
        }

        return result.set(status, consumed, produced, rejection);
    }

    private ModelPipelineResult runTail(
        long traceId,
        long bindingId,
        long authorization,
        boolean flush,
        boolean last,
        int consumed,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        final int room = dstLimit - dstIndex;
        final boolean drain = memberLength > memberProgress && (!last || closeWidth() > room);

        int produced = 0;

        if (drain)
        {
            produced = drainMembers(dst, dstIndex, dstLimit);
        }

        return result.set(drain ? ModelStatus.OVERFLOW : ModelStatus.COMPLETE, consumed, produced);
    }

    private int drainMembers(
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        final boolean text = decoder.extracted();
        final int open = memberEmitted > 0 ? 0 : text
            ? (emitted == 0 ? PREFIX.length : 0) + QUOTE_WIDTH + 1
            : 1;
        final int take = Math.min(memberLength - memberProgress, Math.max(dstLimit - dstIndex - open, 0));

        int produced = 0;

        if (take > 0)
        {
            int position = dstIndex;

            if (memberEmitted == 0)
            {
                if (text)
                {
                    generator.wrap(dst, dstIndex, dstLimit);

                    if (emitted == 0)
                    {
                        beginObject();
                    }

                    generator.writeEndEscaped();
                    position += generator.length();
                    emitted += generator.length();
                    dst.putByte(position++, COMMA);
                }
                else
                {
                    dst.putByte(position++, OPEN);
                }
            }

            dst.putBytes(position, slotBuffer, textCapacity + memberProgress, take);
            position += take;
            memberEmitted += take;
            memberProgress += take;
            produced = position - dstIndex;

            if (memberProgress == memberLength)
            {
                memberProgress = 0;
                memberLength = 0;
            }
        }

        return produced;
    }

    private int feedLimit(
        boolean flush)
    {
        int limit = textLength;

        if (!flush)
        {
            limit = Math.max(textLength - overlap, textProgress);

            while (limit > textProgress && limit < textLength && (slotBuffer.getByte(limit) & 0xC0) == 0x80)
            {
                limit--;
            }
        }

        return limit;
    }

    private int embed(
        int length,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        generator.wrap(dst, dstIndex, dstLimit);

        if (length > 0)
        {
            if (emitted == 0)
            {
                beginObject();
            }

            generator.writeSegment(transformBuffer, 0, length);
        }

        final int produced = generator.length();

        emitted += produced;

        return produced;
    }

    private void beginObject()
    {
        generator.reset();
        generator.writeStartObject().writeKey(MEMBER_TEXT).writeStartEscaped();
    }

    private void compact()
    {
        if (textProgress == textLength)
        {
            textProgress = 0;
            textLength = 0;
        }
        else if (textProgress > 0)
        {
            final int remaining = textLength - textProgress;

            for (int index = 0; index < remaining; index++)
            {
                slotBuffer.putByte(index, slotBuffer.getByte(textProgress + index));
            }

            textLength = remaining;
            textProgress = 0;
        }
    }

    private void compactMembers()
    {
        if (memberProgress > 0)
        {
            final int remaining = memberLength - memberProgress;

            for (int index = 0; index < remaining; index++)
            {
                slotBuffer.putByte(textCapacity + index, slotBuffer.getByte(textCapacity + memberProgress + index));
            }

            memberLength = remaining;
            memberProgress = 0;
        }
    }

    private void onText(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        slotBuffer.putBytes(textLength, buffer, offset, length);
        textLength += length;
    }
}
