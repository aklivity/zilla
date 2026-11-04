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
package io.aklivity.zilla.runtime.engine.test.internal.classifier;

import static java.nio.charset.CodingErrorAction.REPORT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.List;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.classifier.ClassifierHandler;
import io.aklivity.zilla.runtime.engine.classifier.Detector;
import io.aklivity.zilla.runtime.engine.classifier.Locator;

public final class TestClassifierHandler implements ClassifierHandler
{
    private final Consumer<Runnable> dispatcher;

    public TestClassifierHandler(
        Consumer<Runnable> dispatcher)
    {
        this.dispatcher = dispatcher;
    }

    @Override
    public Detector initDetector(
        List<String> labels)
    {
        return (traceId, bindingId, contextId, value, completion) ->
            dispatcher.accept(() -> complete(labels, contextId, value, completion));
    }

    @Override
    public Locator initLocator(
        List<String> labels)
    {
        return (traceId, bindingId, contextId, data, index, length, spans, completion) ->
            locate(labels, contextId, data, index, length, spans, completion);
    }

    private void locate(
        List<String> labels,
        long contextId,
        DirectBuffer data,
        int index,
        int length,
        Locator.SpanConsumer spans,
        Locator.CompletionCallback completion)
    {
        final byte[] bytes = new byte[length];
        data.getBytes(index, bytes);

        try
        {
            final String value = UTF_8.newDecoder()
                .onMalformedInput(REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();

            dispatcher.accept(() -> report(labels, contextId, value, index, spans, completion));
        }
        catch (CharacterCodingException ex)
        {
            dispatcher.accept(() -> completion.failed(contextId, ex));
        }
    }

    private static void report(
        List<String> labels,
        long contextId,
        String value,
        int index,
        Locator.SpanConsumer spans,
        Locator.CompletionCallback completion)
    {
        try
        {
            int position = 0;
            int offset = index;

            while (position < value.length())
            {
                final int at = position;
                final String label = labels.stream()
                    .filter(candidate -> value.startsWith(candidate, at))
                    .max((a, b) -> Integer.compare(a.length(), b.length()))
                    .orElse(null);

                if (label != null)
                {
                    final int span = label.getBytes(UTF_8).length;
                    spans.span(label, offset, span);
                    position += label.length();
                    offset += span;
                }
                else
                {
                    final int next = value.offsetByCodePoints(position, 1);
                    offset += value.substring(position, next).getBytes(UTF_8).length;
                    position = next;
                }
            }

            completion.completed(contextId);
        }
        catch (Throwable ex)
        {
            completion.failed(contextId, ex);
        }
    }

    private static void complete(
        List<String> labels,
        long contextId,
        String value,
        Detector.CompletionCallback completion)
    {
        try
        {
            final boolean detected = value != null && labels.stream().anyMatch(value::contains);

            completion.completed(contextId, detected, detected || value == null ? 0 : retained(labels, value));
        }
        catch (Throwable ex)
        {
            completion.failed(contextId, ex);
        }
    }

    private static int retained(
        List<String> labels,
        String value)
    {
        int retained = 0;

        for (String label : labels)
        {
            for (int length = Math.min(label.length() - 1, value.length()); length > retained; length--)
            {
                if (value.regionMatches(value.length() - length, label, 0, length))
                {
                    retained = length;
                    break;
                }
            }
        }

        return retained;
    }
}
