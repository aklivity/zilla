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
package io.aklivity.zilla.runtime.model.classify.internal;

import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.NONE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.classifier.ClassifierHandler;
import io.aklivity.zilla.runtime.engine.classifier.Detector;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.test.internal.classifier.TestClassifierHandler;

public class ClassifyModelPipelineTest
{
    private static final int MAX_LENGTH = 64;

    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final UnsafeBufferEx dst = new UnsafeBufferEx(new byte[128]);

    private ClassifierHandler moderator;
    private ClassifierHandler patterns;
    private int resumed;

    @Before
    public void init()
    {
        moderator = new TestClassifierHandler(tasks::add);
        patterns = new TestClassifierHandler(tasks::add);
        resumed = 0;
    }

    @Test
    public void shouldAcceptValueWithoutLabels()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors());
        UnsafeBufferEx src = text("a completely unrelated message");

        ModelPipelineResult suspended = finish(pipeline, src);
        assertThat(suspended.status(), equalTo(ModelStatus.SUSPENDED));
        assertThat(resumed, equalTo(0));

        drain();
        assertThat(resumed, equalTo(1));

        ModelPipelineResult replayed = drained(pipeline);
        assertThat(replayed.status(), equalTo(ModelStatus.COMPLETE));
        assertThat(replayed.produced(), equalTo(src.capacity()));
        assertThat(dst.getStringWithoutLengthUtf8(0, replayed.produced()), equalTo("a completely unrelated message"));
        assertThat(pipeline.identity(), equalTo(true));
    }

    @Test
    public void shouldRejectValueWithLabel()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors());

        finish(pipeline, text("message containing prompt.injection"));
        drain();

        assertThat(resumed, equalTo(1));
        assertThat(drained(pipeline).status(), equalTo(ModelStatus.REJECTED));
    }

    @Test
    public void shouldRejectValueWithLabelFromSecondClassifier()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors());

        finish(pipeline, text("message containing secret.aws_key"));
        drain();

        assertThat(resumed, equalTo(1));
        assertThat(drained(pipeline).status(), equalTo(ModelStatus.REJECTED));
    }

    @Test
    public void shouldBufferFragmentsUntilFin()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors());
        UnsafeBufferEx first = text("message containing ");
        UnsafeBufferEx second = text("prompt.injection");

        ModelPipelineResult underflow = pipeline.transform(
            0L, 0L, 0L, NONE, first, 0, first.capacity(), dst, 0, dst.capacity());
        assertThat(underflow.status(), equalTo(ModelStatus.UNDERFLOW));
        assertThat(underflow.consumed(), equalTo(first.capacity()));
        assertThat(tasks.isEmpty(), equalTo(true));

        ModelPipelineResult suspended = finish(pipeline, second);
        assertThat(suspended.status(), equalTo(ModelStatus.SUSPENDED));
        drain();

        assertThat(drained(pipeline).status(), equalTo(ModelStatus.REJECTED));
    }

    @Test
    public void shouldRejectEarlyAndResumeOnce()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors());

        finish(pipeline, text("prompt.injection and secret.aws_key"));
        drain();

        assertThat(resumed, equalTo(1));
        assertThat(drained(pipeline).status(), equalTo(ModelStatus.REJECTED));
    }

    @Test
    public void shouldRejectWhenAnyDetectorFails()
    {
        Detector failing = (traceId, bindingId, contextId, value, completion) ->
            tasks.add(() -> completion.failed(contextId, new IllegalStateException("unavailable")));
        ClassifyModelPipeline pipeline = newPipeline(List.of(
            moderator.initDetector(List.of("prompt.injection")),
            failing));

        finish(pipeline, text("a completely unrelated message"));
        drain();

        assertThat(resumed, equalTo(1));
        assertThat(drained(pipeline).status(), equalTo(ModelStatus.REJECTED));
    }

    @Test
    public void shouldRejectValueExceedingMaxLength()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors());
        UnsafeBufferEx src = new UnsafeBufferEx(new byte[MAX_LENGTH + 1]);

        ModelPipelineResult result = finish(pipeline, src);

        assertThat(result.status(), equalTo(ModelStatus.REJECTED));
        assertThat(tasks.isEmpty(), equalTo(true));
    }

    @Test
    public void shouldAcceptValueAtMaxLength()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors());
        UnsafeBufferEx src = new UnsafeBufferEx("x".repeat(MAX_LENGTH).getBytes(StandardCharsets.UTF_8));

        ModelPipelineResult suspended = finish(pipeline, src);
        drain();

        assertThat(suspended.status(), equalTo(ModelStatus.SUSPENDED));
        assertThat(drained(pipeline).status(), equalTo(ModelStatus.COMPLETE));
    }

    @Test
    public void shouldDropCompletionAfterReset()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors());

        finish(pipeline, text("message containing prompt.injection"));
        pipeline.reset();
        drain();

        assertThat(resumed, equalTo(0));

        ModelPipelineResult suspended = finish(pipeline, text("a completely unrelated message"));
        drain();

        assertThat(suspended.status(), equalTo(ModelStatus.SUSPENDED));
        assertThat(resumed, equalTo(1));
        assertThat(drained(pipeline).status(), equalTo(ModelStatus.COMPLETE));
    }

    @Test
    public void shouldReportSuspendedWhileAwaiting()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors());

        finish(pipeline, text("a completely unrelated message"));

        assertThat(drained(pipeline).status(), equalTo(ModelStatus.SUSPENDED));
    }

    @Test
    public void shouldOverflowUntilValueDrained()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors());
        UnsafeBufferEx src = text("a completely unrelated message");
        UnsafeBufferEx narrow = new UnsafeBufferEx(new byte[10]);

        finish(pipeline, src);
        drain();

        ModelPipelineResult first = pipeline.transform(0L, 0L, 0L, NONE, src, 0, 0, narrow, 0, narrow.capacity());
        assertThat(first.status(), equalTo(ModelStatus.OVERFLOW));
        assertThat(first.produced(), equalTo(10));

        ModelPipelineResult rest = drained(pipeline);
        assertThat(rest.status(), equalTo(ModelStatus.COMPLETE));
        assertThat(rest.produced(), equalTo(src.capacity() - 10));
    }

    @Test
    public void shouldGrowExpandableDestinationRatherThanOverflow()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors());
        UnsafeBufferEx src = text("a completely unrelated message that outgrows its destination");
        ExpandableArrayBufferEx expandable = new ExpandableArrayBufferEx(8);

        finish(pipeline, src);
        drain();
        ModelPipelineResult result = pipeline.transform(
            0L, 0L, 0L, NONE, src, 0, 0, expandable, 0, expandable.capacity());

        assertThat(result.status(), equalTo(ModelStatus.COMPLETE));
        assertThat(expandable.getStringWithoutLengthUtf8(0, result.produced()),
            equalTo("a completely unrelated message that outgrows its destination"));
    }

    @Test
    public void shouldRelayBeforeFinKeepingOverlap()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors(), 8);

        ModelPipelineResult suspended = fragment(pipeline, text("0123456789abcdefghijklmnopqrstuv"), NONE);
        assertThat(suspended.status(), equalTo(ModelStatus.SUSPENDED));
        assertThat(suspended.consumed(), equalTo(32));
        drain();

        ModelPipelineResult relayed = drained(pipeline);
        assertThat(relayed.status(), equalTo(ModelStatus.UNDERFLOW));
        assertThat(relayed(relayed), equalTo("0123456789abcdefghijklmn"));

        ModelPipelineResult last = fragment(pipeline, text("end"), FIN);
        assertThat(last.status(), equalTo(ModelStatus.SUSPENDED));
        drain();

        ModelPipelineResult rest = drained(pipeline);
        assertThat(rest.status(), equalTo(ModelStatus.COMPLETE));
        assertThat(relayed(rest), equalTo("opqrstuvend"));
    }

    @Test
    public void shouldRejectLabelStraddlingCallsWithoutRelayingIt()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors(), 16);

        fragment(pipeline, text("message containing prompt.inj"), NONE);
        drain();

        ModelPipelineResult relayed = drained(pipeline);
        assertThat(relayed.status(), equalTo(ModelStatus.UNDERFLOW));
        assertThat(relayed(relayed), equalTo("message conta"));

        fragment(pipeline, text("ection and the rest"), FIN);
        drain();

        assertThat(drained(pipeline).status(), equalTo(ModelStatus.REJECTED));
    }

    @Test
    public void shouldRejectLabelInAlreadyVerifiedContext()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors(), 16);

        fragment(pipeline, text("clean text more clean text"), NONE);
        drain();
        drained(pipeline);

        fragment(pipeline, text(" and prompt.injection"), NONE);
        drain();

        assertThat(drained(pipeline).status(), equalTo(ModelStatus.REJECTED));
    }

    @Test
    public void shouldNotSplitCharacterWhenReleasing()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors(), 1);

        fragment(pipeline, text("aa\u00e9"), NONE);
        drain();

        ModelPipelineResult relayed = drained(pipeline);
        assertThat(relayed.status(), equalTo(ModelStatus.UNDERFLOW));
        assertThat(relayed(relayed), equalTo("aa"));

        fragment(pipeline, text("b"), FIN);
        drain();

        ModelPipelineResult rest = drained(pipeline);
        assertThat(rest.status(), equalTo(ModelStatus.COMPLETE));
        assertThat(relayed(rest), equalTo("\u00e9b"));
    }

    @Test
    public void shouldStreamValueLongerThanMaxLength()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors(), 8);
        final StringBuilder streamed = new StringBuilder();

        for (int fragment = 0; fragment < 20; fragment++)
        {
            ModelPipelineResult accepted = fragment(pipeline, text("0123456789abcdef"), NONE);
            assertThat(accepted.status(), equalTo(ModelStatus.SUSPENDED));
            drain();
            streamed.append(relayed(drained(pipeline)));
        }

        fragment(pipeline, text("."), FIN);
        drain();
        ModelPipelineResult rest = drained(pipeline);
        streamed.append(relayed(rest));

        assertThat(rest.status(), equalTo(ModelStatus.COMPLETE));
        assertThat(streamed.toString(), equalTo("0123456789abcdef".repeat(20) + "."));
    }

    @Test
    public void shouldCompleteEmptyFinAfterStreamedFragments()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors(), 8);

        fragment(pipeline, text("0123456789abcdefghijklmn"), NONE);
        drain();
        drained(pipeline);

        ModelPipelineResult last = fragment(pipeline, text(""), FIN);

        assertThat(last.status(), equalTo(ModelStatus.COMPLETE));
        assertThat(relayed(last), equalTo("ghijklmn"));
        assertThat(tasks.isEmpty(), equalTo(true));
    }

    @Test
    public void shouldClassifyEverythingArrivedInOneCall()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors(), 4);

        ModelPipelineResult suspended = fragment(pipeline, text("0123456789abcdefghijklmnopqrstuvwxyz0123"), NONE);
        assertThat(suspended.status(), equalTo(ModelStatus.SUSPENDED));
        assertThat(tasks.size(), equalTo(2));
        drain();

        ModelPipelineResult relayed = drained(pipeline);
        assertThat(relayed.status(), equalTo(ModelStatus.UNDERFLOW));
        assertThat(relayed(relayed), equalTo("0123456789abcdefghijklmnopqrstuvwxyz"));
    }

    @Test
    public void shouldWaitForMoreThanOverlapBeforeFirstCall()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors(), 8);

        ModelPipelineResult within = fragment(pipeline, text("0123"), NONE);
        assertThat(within.status(), equalTo(ModelStatus.UNDERFLOW));
        assertThat(within.consumed(), equalTo(4));
        assertThat(tasks.isEmpty(), equalTo(true));

        ModelPipelineResult beyond = fragment(pipeline, text("45678"), NONE);
        assertThat(beyond.status(), equalTo(ModelStatus.SUSPENDED));
        assertThat(tasks.size(), equalTo(2));
    }

    @Test
    public void shouldNotConsumeWhileAwaitingClassification()
    {
        ClassifyModelPipeline pipeline = newPipeline(detectors(), 4);

        fragment(pipeline, text("01234567"), NONE);

        ModelPipelineResult queued = fragment(pipeline, text("89abcdef"), NONE);
        assertThat(queued.status(), equalTo(ModelStatus.SUSPENDED));
        assertThat(queued.consumed(), equalTo(0));
        drain();

        ModelPipelineResult relayed = drained(pipeline);
        assertThat(relayed(relayed), equalTo("0123"));

        ModelPipelineResult next = fragment(pipeline, text("89abcdef"), NONE);
        assertThat(next.status(), equalTo(ModelStatus.SUSPENDED));
        assertThat(next.consumed(), equalTo(8));
        assertThat(tasks.size(), equalTo(2));
    }

    private List<Detector> detectors()
    {
        return List.of(
            moderator.initDetector(List.of("prompt.injection", "policy.exfiltration")),
            patterns.initDetector(List.of("secret.aws_key")));
    }

    private ClassifyModelPipeline newPipeline(
        List<Detector> detectors)
    {
        return newPipeline(detectors, MAX_LENGTH);
    }

    private ClassifyModelPipeline newPipeline(
        List<Detector> detectors,
        int overlap)
    {
        return new ClassifyModelPipeline(detectors, MAX_LENGTH, overlap, () -> resumed++);
    }

    private ModelPipelineResult fragment(
        ClassifyModelPipeline pipeline,
        UnsafeBufferEx src,
        int flags)
    {
        return pipeline.transform(0L, 0L, 0L, flags, src, 0, src.capacity(), dst, 0, dst.capacity());
    }

    private String relayed(
        ModelPipelineResult result)
    {
        return dst.getStringWithoutLengthUtf8(0, result.produced());
    }

    private ModelPipelineResult finish(
        ClassifyModelPipeline pipeline,
        UnsafeBufferEx src)
    {
        return pipeline.transform(0L, 0L, 0L, FIN, src, 0, src.capacity(), dst, 0, dst.capacity());
    }

    private ModelPipelineResult drained(
        ClassifyModelPipeline pipeline)
    {
        return pipeline.transform(0L, 0L, 0L, NONE, dst, 0, 0, dst, 0, dst.capacity());
    }

    private static UnsafeBufferEx text(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(StandardCharsets.UTF_8));
    }

    private void drain()
    {
        while (!tasks.isEmpty())
        {
            tasks.poll().run();
        }
    }
}
