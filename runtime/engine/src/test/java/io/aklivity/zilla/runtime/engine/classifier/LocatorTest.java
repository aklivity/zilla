/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.classifier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.test.internal.classifier.TestClassifierHandler;

public class LocatorTest
{
    private final Queue<Runnable> dispatched = new LinkedList<>();
    private final List<String> spans = new ArrayList<>();
    private final List<String> outcomes = new ArrayList<>();

    private final Locator.SpanConsumer consumer = (label, offset, length) -> spans.add(label + ":" + offset + ":" + length);
    private final Locator.CompletionCallback completion = new Locator.CompletionCallback()
    {
        @Override
        public void completed(
            long contextId)
        {
            outcomes.add("completed:" + contextId);
        }

        @Override
        public void failed(
            long contextId,
            Throwable ex)
        {
            outcomes.add("failed:" + contextId + ":" + ex.getClass().getSimpleName());
        }
    };

    private final ClassifierHandler handler = new TestClassifierHandler(dispatched::add);

    @Test
    public void shouldReportSpansInOrder()
    {
        locate(List.of("secret", "token"), "a token and a secret", 0);

        assertThat(spans, contains("token:2:5", "secret:14:6"));
        assertThat(outcomes, contains("completed:7"));
    }

    @Test
    public void shouldCompleteStrictlyLater()
    {
        Locator locator = handler.initLocator(List.of("secret"));
        byte[] value = "my secret".getBytes(UTF_8);

        locator.locate(1L, 2L, 7L, new UnsafeBuffer(value), 0, value.length, consumer, completion);

        assertThat(spans, empty());
        assertThat(outcomes, empty());

        dispatched.remove().run();

        assertThat(spans, contains("secret:3:6"));
        assertThat(outcomes, contains("completed:7"));
    }

    @Test
    public void shouldReportUtf8ByteOffsets()
    {
        locate(List.of("secret"), "héllo 😀 secret", 0);

        assertThat(spans, contains("secret:12:6"));
    }

    @Test
    public void shouldReportSpansRelativeToBuffer()
    {
        locate(List.of("secret"), "my secret", 5);

        assertThat(spans, contains("secret:8:6"));
    }

    @Test
    public void shouldNotOverlapSpans()
    {
        locate(List.of("secretive", "secret"), "secretive secret", 0);

        assertThat(spans, contains("secretive:0:9", "secret:10:6"));
    }

    @Test
    public void shouldReportNoSpansWhenAbsent()
    {
        locate(List.of("secret"), "nothing here", 0);

        assertThat(spans, empty());
        assertThat(outcomes, contains("completed:7"));
    }

    @Test
    public void shouldFailOnMalformedUtf8()
    {
        Locator locator = handler.initLocator(List.of("secret"));
        UnsafeBuffer data = new UnsafeBuffer(new byte[] {'o', 'k', (byte) 0xC3});

        locator.locate(1L, 2L, 7L, data, 0, 3, consumer, completion);
        dispatched.remove().run();

        assertThat(spans, empty());
        assertThat(outcomes, contains("failed:7:MalformedInputException"));
    }

    @Test
    public void shouldDefaultToCannotLocate()
    {
        ClassifierHandler detectOnly = labels -> null;

        assertThat(detectOnly.initLocator(List.of("secret")), nullValue());
        assertThat(handler.initLocator(List.of("secret")), instanceOf(Locator.class));
    }

    private void locate(
        List<String> labels,
        String text,
        int index)
    {
        Locator locator = handler.initLocator(labels);
        byte[] value = text.getBytes(UTF_8);
        UnsafeBuffer data = new UnsafeBuffer(new byte[index + value.length]);
        data.putBytes(index, value);

        locator.locate(1L, 2L, 7L, data, index, value.length, consumer, completion);
        dispatched.remove().run();
    }
}
