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
package io.aklivity.zilla.runtime.classifier.patterns.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.classifier.patterns.PatternsClassifierOptionsConfig;
import io.aklivity.zilla.config.engine.ClassifierConfig;
import io.aklivity.zilla.config.engine.GenericClassifierConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.Locator;

public class PatternsLocatorTest
{
    private static final long CONTEXT = 99L;

    private final Queue<Runnable> dispatched = new ArrayDeque<>();
    private final List<String> events = new ArrayList<>();
    private final List<String> spans = new ArrayList<>();
    private final List<Throwable> failures = new ArrayList<>();
    private final Locator.SpanConsumer consumer = (label, offset, length) ->
    {
        spans.add(label + ":" + offset + ":" + length);
        events.add("span");
    };
    private final Locator.CompletionCallback completion = new Locator.CompletionCallback()
    {
        @Override
        public void completed(
            long contextId)
        {
            assertThat(contextId, equalTo(CONTEXT));
            events.add("completed");
        }

        @Override
        public void failed(
            long contextId,
            Throwable ex)
        {
            assertThat(contextId, equalTo(CONTEXT));
            failures.add(ex);
            events.add("failed");
        }
    };

    private EngineContext engine;

    @Before
    public void init()
    {
        engine = mock(EngineContext.class);
        doAnswer(invocation -> dispatched.add(invocation.getArgument(0))).when(engine).dispatch(any());
    }

    @Test
    public void shouldLocateSingleMatch()
    {
        Locator locator = locator(0.0, new Properties(), "key", "AKIA[0-9A-Z]{16}");

        locate(locator, "my key AKIAIOSFODNN7EXAMPLE leaked");

        assertThat(spans, contains("key:7:20"));
        assertThat(events, contains("span", "completed"));
    }

    @Test
    public void shouldLocateEveryMatchOfLabel()
    {
        Locator locator = locator(0.0, new Properties(), "key", "AKIA[0-9A-Z]{16}");

        locate(locator, "AKIAIOSFODNN7EXAMPLE and AKIAIOSFODNN7ANOTHER and AKIAIOSFODNN7THIRD12");

        assertThat(spans, contains("key:0:20", "key:25:20", "key:50:20"));
    }

    @Test
    public void shouldLocateEveryMatchOfEveryPatternOfLabel()
    {
        Locator locator = locator(0.0, new Properties(), "id", "[0-9]{3}-[0-9]{2}", "id", "[a-z]{3}@");

        locate(locator, "123-45 abc@ 678-90 def@");

        assertThat(spans, contains("id:0:6", "id:7:4", "id:12:6", "id:19:4"));
    }

    @Test
    public void shouldLocateSeveralLabelsInOrder()
    {
        Locator locator = locator(0.0, new Properties(), "email", "[a-z]+@[a-z]+\\.org", "phone", "[0-9]{3}-[0-9]{4}");

        locate(locator, "call 555-1234 or jane@example.org or 555-9876");

        assertThat(spans, contains("phone:5:8", "email:17:16", "phone:37:8"));
    }

    @Test
    public void shouldLocateOnlyRequestedLabels()
    {
        Locator locator = locator(0.0, new Properties(), List.of("phone"),
            "email", "[a-z]+@[a-z]+\\.org", "phone", "[0-9]{3}-[0-9]{4}");

        locate(locator, "call 555-1234 or jane@example.org");

        assertThat(spans, contains("phone:5:8"));
    }

    @Test
    public void shouldPreferLongerMatchWhenOverlappingAtSameStart()
    {
        Locator locator = locator(0.0, new Properties(), "short", "[0-9]{3}", "long", "[0-9]{3}-[0-9]{4}");

        locate(locator, "call 555-1234 now");

        assertThat(spans, contains("long:5:8"));
    }

    @Test
    public void shouldPreferEarlierPatternWhenOverlappingWithSameStartAndLength()
    {
        Locator locator = locator(0.0, new Properties(), "first", "[0-9]{4}", "second", "[0-9]{4}");

        locate(locator, "pin 1234 ok");

        assertThat(spans, contains("first:4:4"));
    }

    @Test
    public void shouldPreferEarlierStartWhenOverlapping()
    {
        Locator locator = locator(0.0, new Properties(), "left", "abcd", "right", "cdefgh");

        locate(locator, "xabcdefghx");

        assertThat(spans, contains("left:1:4"));
    }

    @Test
    public void shouldLocateAdjacentMatches()
    {
        Locator locator = locator(0.0, new Properties(), "left", "ab", "right", "cd");

        locate(locator, "xabcdx");

        assertThat(spans, contains("left:1:2", "right:3:2"));
    }

    @Test
    public void shouldLocateMatchesAfterDiscardedOverlap()
    {
        Locator locator = locator(0.0, new Properties(), "long", "abcdef", "short", "cd", "tail", "ef|gh");

        locate(locator, "abcdef gh");

        assertThat(spans, contains("long:0:6", "tail:7:2"));
    }

    @Test
    public void shouldIgnoreMatchBelowEntropy()
    {
        Locator locator = locator(3.0, new Properties(), "key", "AKIA[0-9A-Z]{16}");

        locate(locator, "AKIAAAAAAAAAAAAAAAAA placeholder AKIAIOSFODNN7EXAMPLE");

        assertThat(spans, contains("key:33:20"));
    }

    @Test
    public void shouldLocateBytesOffsetsForMultibyteCharacters()
    {
        Locator locator = locator(0.0, new Properties(), "id", "[0-9]{3}");

        locate(locator, "héllo wörld 123 ünï 456");

        assertThat(spans, contains("id:14:3", "id:24:3"));
    }

    @Test
    public void shouldLocateByteOffsetsForThreeByteCharacters()
    {
        Locator locator = locator(0.0, new Properties(), "id", "[0-9]{3}");

        locate(locator, "€€ 123");

        assertThat(spans, contains("id:7:3"));
    }

    @Test
    public void shouldLocateByteOffsetsForSurrogatePairs()
    {
        Locator locator = locator(0.0, new Properties(), "id", "[0-9]{3}");

        locate(locator, "😀😀 123 😀 456");

        assertThat(spans, contains("id:9:3", "id:18:3"));
    }

    @Test
    public void shouldLocateMatchContainingMultibyteCharacters()
    {
        Locator locator = locator(0.0, new Properties(), "name", "Zoë 😀 [a-z]+");

        locate(locator, "hi Zoë 😀 went");

        assertThat(spans, contains("name:3:14"));
    }

    @Test
    public void shouldLocateSpansRelativeToBuffer()
    {
        Locator locator = locator(0.0, new Properties(), "id", "[0-9]{3}");
        byte[] value = "é 123".getBytes(UTF_8);
        UnsafeBuffer data = new UnsafeBuffer(new byte[value.length + 10]);
        data.putBytes(5, value);

        locator.locate(1L, 2L, CONTEXT, data, 5, value.length, consumer, completion);
        runDispatched();

        assertThat(spans, contains("id:8:3"));
    }

    @Test
    public void shouldLocateNoSpansWhenNoMatch()
    {
        Locator locator = locator(0.0, new Properties(), "id", "[0-9]{3}");

        locate(locator, "nothing here");

        assertThat(spans, empty());
        assertThat(events, contains("completed"));
    }

    @Test
    public void shouldLocateNoSpansForEmptyValue()
    {
        Locator locator = locator(0.0, new Properties(), "id", "[0-9]*");

        locate(locator, "");

        assertThat(spans, empty());
        assertThat(events, contains("completed"));
    }

    @Test
    public void shouldIgnoreEmptyMatches()
    {
        Locator locator = locator(0.0, new Properties(), "id", "[0-9]*");

        locate(locator, "ab 12 cd");

        assertThat(spans, contains("id:3:2"));
    }

    @Test
    public void shouldCompleteOnlyAfterLocateReturns()
    {
        Locator locator = locator(0.0, new Properties(), "id", "[0-9]{3}");
        byte[] value = "abc 123".getBytes(UTF_8);

        locator.locate(1L, 2L, CONTEXT, new UnsafeBuffer(value), 0, value.length, consumer, completion);

        assertThat(events, empty());
        assertThat(dispatched.size(), equalTo(1));
    }

    @Test
    public void shouldNotRetainBufferAfterLocateReturns()
    {
        Locator locator = locator(0.0, new Properties(), "id", "[0-9]{3}");
        byte[] value = "abc 123".getBytes(UTF_8);
        UnsafeBuffer data = new UnsafeBuffer(value.clone());

        locator.locate(1L, 2L, CONTEXT, data, 0, value.length, consumer, completion);
        data.setMemory(0, value.length, (byte) 'X');
        runDispatched();

        assertThat(spans, contains("id:4:3"));
    }

    @Test
    public void shouldAnswerEveryCallExactlyOnce()
    {
        Locator locator = locator(0.0, new Properties(), "id", "[0-9]{3}");

        locate(locator, "123");
        locate(locator, "none");
        locate(locator, "456 789");

        assertThat(events, contains("span", "completed", "completed", "span", "span", "completed"));
        assertThat(spans, contains("id:0:3", "id:0:3", "id:4:3"));
    }

    @Test
    public void shouldNotReportSpansAfterCompletion()
    {
        Locator locator = locator(0.0, new Properties(), "id", "[0-9]{3}");

        locate(locator, "123 456");
        int reported = spans.size();
        locate(locator, "none");

        assertThat(spans.size(), equalTo(reported));
        assertThat(events.get(events.size() - 1), equalTo("completed"));
    }

    @Test
    public void shouldFailWhenMatchStepsExceeded()
    {
        Properties properties = new Properties();
        properties.setProperty("zilla.classifier.patterns.match.max.steps", "10");
        Locator locator = locator(0.0, properties, "id", "[0-9]{3}");

        locate(locator, "abcdefghijklmnopqrstuvwxyz 123");

        assertThat(failures.size(), equalTo(1));
        assertThat(failures.get(0), instanceOf(IllegalStateException.class));
        assertThat(spans, empty());
        assertThat(events, contains("failed"));
    }

    @Test
    public void shouldFailWhenInputLengthExceeded()
    {
        Properties properties = new Properties();
        properties.setProperty("zilla.classifier.patterns.input.max.length", "8");
        Locator locator = locator(0.0, properties, "id", "[0-9]{3}");

        locate(locator, "123 456 789");

        assertThat(failures.size(), equalTo(1));
        assertThat(failures.get(0), instanceOf(IllegalArgumentException.class));
        assertThat(spans, empty());
        assertThat(events, contains("failed"));
    }

    @Test
    public void shouldLocateValueAtInputLengthLimit()
    {
        Properties properties = new Properties();
        properties.setProperty("zilla.classifier.patterns.input.max.length", "7");
        Locator locator = locator(0.0, properties, "id", "[0-9]{3}");

        locate(locator, "123 456");

        assertThat(spans, contains("id:0:3", "id:4:3"));
    }

    @Test
    public void shouldFailOnMalformedUtf8()
    {
        Locator locator = locator(0.0, new Properties(), "id", "[0-9]{3}");
        UnsafeBuffer data = new UnsafeBuffer(new byte[] {'o', 'k', (byte) 0xC3});

        locator.locate(1L, 2L, CONTEXT, data, 0, 3, consumer, completion);
        runDispatched();

        assertThat(failures.size(), equalTo(1));
        assertThat(spans, empty());
        assertThat(events, contains("failed"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectUnrecognizedLabel()
    {
        PatternsClassifierHandler handler = handler(0.0, new Properties(), "id", "[0-9]{3}");

        handler.initLocator(List.of("missing"));
    }

    private Locator locator(
        double entropy,
        Properties properties,
        String... labelsAndExpressions)
    {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < labelsAndExpressions.length; i += 2)
        {
            if (i + 1 < labelsAndExpressions.length && !labels.contains(labelsAndExpressions[i]))
            {
                labels.add(labelsAndExpressions[i]);
            }
        }

        return locator(entropy, properties, labels, labelsAndExpressions);
    }

    private Locator locator(
        double entropy,
        Properties properties,
        List<String> requested,
        String... labelsAndExpressions)
    {
        return handler(entropy, properties, labelsAndExpressions).initLocator(requested);
    }

    private PatternsClassifierHandler handler(
        double entropy,
        Properties properties,
        String... labelsAndExpressions)
    {
        var builder = PatternsClassifierOptionsConfig.builder().entropy(entropy);
        List<String> order = new ArrayList<>();
        for (int i = 0; i < labelsAndExpressions.length; i += 2)
        {
            if (!order.contains(labelsAndExpressions[i]))
            {
                order.add(labelsAndExpressions[i]);
            }
        }
        for (String label : order)
        {
            List<String> expressions = new ArrayList<>();
            for (int i = 0; i < labelsAndExpressions.length; i += 2)
            {
                if (labelsAndExpressions[i].equals(label))
                {
                    expressions.add(labelsAndExpressions[i + 1]);
                }
            }
            builder.label(label, expressions);
        }
        ClassifierConfig config = GenericClassifierConfig.builder()
            .namespace("test")
            .name("patterns0")
            .type("patterns")
            .options(builder.build())
            .build();

        return new PatternsClassifierHandler(engine, config,
            new PatternsClassifierConfiguration(new Configuration(properties)));
    }

    private void locate(
        Locator locator,
        String text)
    {
        byte[] value = text.getBytes(UTF_8);

        locator.locate(1L, 2L, CONTEXT, new UnsafeBuffer(value), 0, value.length, consumer, completion);
        runDispatched();
    }

    private void runDispatched()
    {
        Runnable task;
        while ((task = dispatched.poll()) != null)
        {
            task.run();
        }
    }
}
