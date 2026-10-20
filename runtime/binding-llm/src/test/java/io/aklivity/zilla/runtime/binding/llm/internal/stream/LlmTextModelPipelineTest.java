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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

public class LlmTextModelPipelineTest
{
    private static final int SLOT_CAPACITY = 32 * 1024;
    private static final int ROOM = 8 * 1024;
    private static final int FRAME = 1000;

    private final LlmTestBufferPool pool = new LlmTestBufferPool(SLOT_CAPACITY, 1);
    private final MutableDirectBufferEx transformBuffer = new UnsafeBufferEx(new byte[16 * 1024]);
    private final MutableDirectBufferEx output = new UnsafeBufferEx(new byte[64 * 1024]);

    @Test
    public void shouldRelayText()
    {
        final Delivery delivery = deliver(frames("{\"text\":\"abc\"}"), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo("{\"text\":\"abc\"}"));
    }

    @Test
    public void shouldRelayEmptyText()
    {
        final Delivery delivery = deliver(frames("{\"text\":\"\"}"), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo("{\"text\":\"\"}"));
    }

    @Test
    public void shouldTransformText()
    {
        final Delivery delivery = deliver(new MaskChain("secret", "******"), frames("{\"text\":\"my secret plan\"}"), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo("{\"text\":\"my ****** plan\"}"));
    }

    @Test
    public void shouldCarryMembersAfterText()
    {
        final String section = "{\"text\":\"abc\",\"openai:id\":\"rs_1\",\"openai:encrypted_content\":\"gAAAA\"}";

        final Delivery delivery = deliver(frames(section), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo(section));
    }

    @Test
    public void shouldCarryMembersAfterEmptyText()
    {
        final String section = "{\"text\":\"\",\"openai:id\":\"rs_1\"}";

        final Delivery delivery = deliver(frames(section), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo(section));
    }

    @Test
    public void shouldMoveMembersBeforeTextAfterText()
    {
        final Delivery delivery = deliver(frames("{\"openai:id\":\"rs_1\",\"text\":\"abc\"}"), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo("{\"text\":\"abc\",\"openai:id\":\"rs_1\"}"));
    }

    @Test
    public void shouldCarryMembersAroundText()
    {
        final Delivery delivery = deliver(frames("{\"openai:id\":\"rs_1\",\"text\":\"abc\",\"openai:note\":\"x\"}"), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo("{\"text\":\"abc\",\"openai:id\":\"rs_1\",\"openai:note\":\"x\"}"));
    }

    @Test
    public void shouldCloseSectionWithMembersInClosingFrame()
    {
        final Delivery delivery = deliver(new MaskChain("secret", "******"),
            frames("{\"text\":\"my secret\",\"openai:id\":\"rs_1\"}"), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.pieces, contains("{\"text\":\"my ******", "\",\"openai:id\":\"rs_1\"}"));
    }

    @Test
    public void shouldStreamMembersAfterTextWhileSectionContinues()
    {
        final String prefix = "{\"text\":\"abc\",\"openai:id\":\"rs_1\",\"openai:encrypted_content\":\"";
        final String content = "A".repeat(3000);

        final Delivery delivery = deliver(frames(prefix + content + "\"}", FRAME), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo(prefix + content + "\"}"));
        assertThat(delivery.beforeClosing(), containsString("\"openai:encrypted_content\":\"AAAA"));
    }

    @Test
    public void shouldRelaySectionInTinyFrames()
    {
        final String text = "\"text\":\"caf\u00e9 \\\"quoted\\\" \\u0001 text\"";
        final String members = "\"openai:id\":\"rs_1\",\"openai:note\":{\"a\":[1,2,{\"b\":\"c\"}]}";
        final String section = "{" + text + "," + members + ",\"openai:encrypted_content\":\"" + base64(300) + "\"}";

        for (int size : new int[] {1, 2, 3, 7, 13, 64, 100})
        {
            final Delivery delivery = deliver(frames(section, size), ROOM);

            assertThat("frame size " + size, delivery.rejected, is(false));
            assertThat("frame size " + size, delivery.text(), equalTo(section));
        }
    }

    @Test
    public void shouldRelayLargeMembersAfterText()
    {
        final String prefix = "{\"text\":\"Let me think\",\"openai:id\":\"rs_1\",\"openai:encrypted_content\":\"";
        final String content = base64(40000);
        final String section = prefix + content + "\"}";

        final Delivery delivery = deliver(frames(section, FRAME), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo(section));
        assertThat(delivery.largestPiece(), lessThanOrEqualTo(ROOM));
    }

    @Test
    public void shouldRelayLargeMembersAfterTransformedText()
    {
        final String content = base64(40000);

        final Delivery delivery = deliver(new MaskChain("secret", "******"),
            frames("{\"text\":\"my secret plan\",\"openai:encrypted_content\":\"" + content + "\"}", FRAME), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo("{\"text\":\"my ****** plan\",\"openai:encrypted_content\":\"" + content + "\"}"));
    }

    @Test
    public void shouldRelayLargeMembersWithinSmallRoom()
    {
        final String prefix = "{\"text\":\"abc\",\"openai:encrypted_content\":\"";
        final String content = base64(20000);
        final String section = prefix + content + "\"}";

        final Delivery delivery = deliver(frames(section, FRAME), 300);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo(section));
        assertThat(delivery.largestPiece(), lessThanOrEqualTo(300));
    }

    @Test
    public void shouldRelayLargeMembersInFinalFrameWithinSmallRoom()
    {
        final String prefix = "{\"text\":\"abc\",\"openai:encrypted_content\":\"";
        final String content = base64(5000);
        final String section = prefix + content + "\"}";

        final Delivery delivery = deliver(frames(section), 400);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo(section));
        assertThat(delivery.largestPiece(), lessThanOrEqualTo(400));
    }

    @Test
    public void shouldRelayMembersBeforeTextWithinBound()
    {
        final String content = base64(10000);

        final String section = "{\"openai:encrypted_content\":\"" + content + "\",\"text\":\"abc\"}";

        final Delivery delivery = deliver(frames(section, FRAME), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo("{\"text\":\"abc\",\"openai:encrypted_content\":\"" + content + "\"}"));
    }

    @Test
    public void shouldRejectMembersBeforeTextBeyondBound()
    {
        final String content = base64(20000);

        final String section = "{\"openai:encrypted_content\":\"" + content + "\",\"text\":\"abc\"}";

        final Delivery delivery = deliver(frames(section, FRAME), ROOM);

        assertThat(delivery.rejected, is(true));
    }

    @Test
    public void shouldRejectMissingText()
    {
        final Delivery delivery = deliver(frames("{\"openai:id\":\"rs_1\"}"), ROOM);

        assertThat(delivery.rejected, is(true));
    }

    @Test
    public void shouldRejectTextRejectedByChain()
    {
        final Delivery delivery = deliver(new RejectChain("bad"),
            frames("{\"text\":\"a bad word\",\"openai:id\":\"rs_1\"}"), ROOM);

        assertThat(delivery.rejected, is(true));
    }

    @Test
    public void shouldRelayMembersWithoutTextWhenTextIsOptional()
    {
        final String section = "{\"openai:id\":\"rs_1\",\"openai:encrypted_content\":\"gAAAA\"}";

        final Delivery delivery = drive(optional(), frames(section), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo(section));
    }

    @Test
    public void shouldRelayMembersWithoutTextInFramesWhenTextIsOptional()
    {
        final String section = "{\"openai:id\":\"rs_1\",\"openai:encrypted_content\":\"" + base64(3000) + "\"}";

        final Delivery delivery = drive(optional(), frames(section, FRAME), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo(section));
    }

    @Test
    public void shouldRelayMembersWithoutTextWithinSmallRoomWhenTextIsOptional()
    {
        final String section = "{\"openai:id\":\"rs_1\",\"openai:encrypted_content\":\"" + base64(3000) + "\"}";

        final Delivery delivery = drive(optional(), frames(section, FRAME), 200);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo(section));
        assertThat(delivery.largestPiece(), lessThanOrEqualTo(200));
    }

    @Test
    public void shouldRelayEmptyObjectWhenTextIsOptional()
    {
        final Delivery delivery = drive(optional(), frames("{}"), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo("{}"));
    }

    @Test
    public void shouldRelayTextAndMembersWhenTextIsOptional()
    {
        final String section = "{\"text\":\"abc\",\"openai:id\":\"rs_1\"}";

        final Delivery delivery = drive(optional(), frames(section), ROOM);

        assertThat(delivery.rejected, is(false));
        assertThat(delivery.text(), equalTo(section));
    }

    @Test
    public void shouldRejectMembersWithoutTextBeyondBoundWhenTextIsOptional()
    {
        final String section = "{\"openai:encrypted_content\":\"" + base64(20000) + "\"}";

        final Delivery delivery = drive(optional(), frames(section, FRAME), ROOM);

        assertThat(delivery.rejected, is(true));
    }

    @Test
    public void shouldRelayConsecutiveSections()
    {
        final LlmTextModelPipeline pipeline = pipeline(new EchoChain());

        final Delivery first = drive(pipeline, frames("{\"text\":\"one\",\"openai:id\":\"rs_1\"}"), ROOM);
        pipeline.reset();
        final Delivery second = drive(pipeline, frames("{\"text\":\"two\"}"), ROOM);

        assertThat(first.text(), equalTo("{\"text\":\"one\",\"openai:id\":\"rs_1\"}"));
        assertThat(second.text(), equalTo("{\"text\":\"two\"}"));
    }

    private Delivery deliver(
        String[] frames,
        int room)
    {
        return deliver(new EchoChain(), frames, room);
    }

    private Delivery deliver(
        ModelPipeline chain,
        String[] frames,
        int room)
    {
        return drive(pipeline(chain), frames, room);
    }

    private LlmTextModelPipeline pipeline(
        ModelPipeline chain)
    {
        return new LlmTextModelPipeline(chain, pool, 1L, transformBuffer, false);
    }

    private LlmTextModelPipeline optional()
    {
        return new LlmTextModelPipeline(new EchoChain(), pool, 1L, transformBuffer, true);
    }

    private Delivery drive(
        LlmTextModelPipeline pipeline,
        String[] frames,
        int room)
    {
        final Delivery delivery = new Delivery();

        for (int index = 0; index < frames.length && !delivery.rejected; index++)
        {
            final byte[] bytes = frames[index].getBytes(UTF_8);
            final DirectBufferEx source = new UnsafeBufferEx(bytes);

            int position = 0;
            boolean init = index == 0;
            boolean progressed = true;

            while (progressed)
            {
                final ModelPipelineResult result = pipeline.accept(1L, 2L, 3L, init, index == frames.length - 1, source,
                    position, bytes.length, output, 0, room);

                position += result.consumed();
                init = false;
                delivery.add(output, result.produced());
                delivery.rejected = result.status() == ModelStatus.REJECTED;
                progressed = result.status() == ModelStatus.OVERFLOW && (result.consumed() > 0 || result.produced() > 0);
            }

            if (!delivery.rejected)
            {
                assertThat(position, is(bytes.length));
            }
        }

        boolean finishing = !delivery.rejected;

        while (finishing)
        {
            final ModelPipelineResult result = pipeline.finish(1L, 2L, 3L, output, 0, room);

            delivery.add(output, result.produced());
            delivery.rejected = result.status() == ModelStatus.REJECTED;
            finishing = result.status() == ModelStatus.OVERFLOW && result.produced() > 0;
        }

        if (!delivery.rejected)
        {
            final int width = pipeline.closeWidth();

            assertThat("closing frame fits", width, lessThanOrEqualTo(room));

            final int length = pipeline.close(output, 0);

            assertThat(length, lessThanOrEqualTo(width));
            delivery.closing = delivery.pieces.size();
            delivery.add(output, length);
        }

        pipeline.reset();

        return delivery;
    }

    private static String[] frames(
        String section)
    {
        return new String[] {section};
    }

    private static String[] frames(
        String section,
        int size)
    {
        final List<String> frames = new ArrayList<>();

        for (int offset = 0; offset < section.length(); offset += size)
        {
            frames.add(section.substring(offset, Math.min(offset + size, section.length())));
        }

        return frames.toArray(new String[0]);
    }

    private static String base64(
        int length)
    {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        final StringBuilder builder = new StringBuilder(length);

        for (int index = 0; index < length; index++)
        {
            builder.append(alphabet.charAt((index * 7 + index / 64) % alphabet.length()));
        }

        return builder.toString();
    }

    private static final class Delivery
    {
        private final List<String> pieces = new ArrayList<>();

        private boolean rejected;
        private int closing;

        private void add(
            DirectBufferEx buffer,
            int length)
        {
            if (length > 0)
            {
                pieces.add(buffer.getStringWithoutLengthUtf8(0, length));
            }
        }

        private String text()
        {
            return String.join("", pieces);
        }

        private int largestPiece()
        {
            return pieces.stream().mapToInt(String::length).max().orElse(0);
        }

        private String beforeClosing()
        {
            return String.join("", pieces.subList(0, closing));
        }
    }

    private static class EchoChain implements ModelPipeline
    {
        private final ModelPipelineResult result = new ModelPipelineResult();

        @Override
        public ModelPipelineResult transform(
            long traceId,
            long bindingId,
            long authorization,
            int flags,
            DirectBufferEx src,
            int srcIndex,
            int srcLimit,
            MutableDirectBufferEx dst,
            int dstIndex,
            int dstLimit)
        {
            final int length = Math.min(srcLimit - srcIndex, dstLimit - dstIndex);

            dst.putBytes(dstIndex, src, srcIndex, length);

            final boolean drained = length == srcLimit - srcIndex;

            final ModelStatus status = !drained ? ModelStatus.OVERFLOW
                : Flags.hasFin(flags) ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW;

            return result.set(status, length, length);
        }

        @Override
        public boolean identity()
        {
            return false;
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }

        @Override
        public void reset()
        {
        }
    }

    private static final class MaskChain extends EchoChain
    {
        private final byte[] word;
        private final byte[] mask;
        private final MutableDirectBufferEx pending = new UnsafeBufferEx(new byte[64 * 1024]);
        private final ModelPipelineResult result = new ModelPipelineResult();

        private int pendingLength;
        private int pendingDrained;

        private MaskChain(
            String word,
            String mask)
        {
            this.word = word.getBytes(UTF_8);
            this.mask = mask.getBytes(UTF_8);
        }

        @Override
        public ModelPipelineResult transform(
            long traceId,
            long bindingId,
            long authorization,
            int flags,
            DirectBufferEx src,
            int srcIndex,
            int srcLimit,
            MutableDirectBufferEx dst,
            int dstIndex,
            int dstLimit)
        {
            final int consumed = srcLimit - srcIndex;

            for (int index = srcIndex; index < srcLimit; index++)
            {
                pending.putByte(pendingLength++, src.getByte(index));
            }

            if (Flags.hasFin(flags) && pendingDrained == 0)
            {
                final String text = pending.getStringWithoutLengthUtf8(0, pendingLength);
                final String masked = text.replace(new String(word, UTF_8), new String(mask, UTF_8));

                pendingLength = masked.length();
                pending.putBytes(0, masked.getBytes(UTF_8));
            }

            int produced = 0;

            if (Flags.hasFin(flags))
            {
                produced = Math.min(pendingLength - pendingDrained, dstLimit - dstIndex);
                dst.putBytes(dstIndex, pending, pendingDrained, produced);
                pendingDrained += produced;
            }

            final boolean drained = pendingDrained == pendingLength;

            return result.set(Flags.hasFin(flags) && !drained ? ModelStatus.OVERFLOW
                : Flags.hasFin(flags) ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW, consumed, produced);
        }

        @Override
        public void reset()
        {
            pendingLength = 0;
            pendingDrained = 0;
        }
    }

    private static final class RejectChain extends EchoChain
    {
        private final byte[] word;
        private final ModelPipelineResult result = new ModelPipelineResult();

        private RejectChain(
            String word)
        {
            this.word = word.getBytes(UTF_8);
        }

        @Override
        public ModelPipelineResult transform(
            long traceId,
            long bindingId,
            long authorization,
            int flags,
            DirectBufferEx src,
            int srcIndex,
            int srcLimit,
            MutableDirectBufferEx dst,
            int dstIndex,
            int dstLimit)
        {
            final String text = src.getStringWithoutLengthUtf8(srcIndex, srcLimit - srcIndex);

            return text.contains(new String(word, UTF_8))
                ? result.set(ModelStatus.REJECTED, srcLimit - srcIndex, 0)
                : super.transform(traceId, bindingId, authorization, flags, src, srcIndex, srcLimit, dst, dstIndex, dstLimit);
        }
    }
}
