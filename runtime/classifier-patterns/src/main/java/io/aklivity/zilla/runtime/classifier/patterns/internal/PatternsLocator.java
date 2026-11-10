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

import static java.nio.charset.CodingErrorAction.REPORT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.Locator;

/**
 * Locates every match of the requested labels' patterns.
 * <p>
 * Where matches overlap, the match that starts earliest is kept, then the longest, then the one from the
 * pattern declared first, in the order the labels were requested and each label's patterns were configured.
 * Every other overlapping match is discarded, so the spans reported are ascending and never overlap.
 * </p>
 */
final class PatternsLocator implements Locator
{
    private final EngineContext context;
    private final PatternsMatcher matcher;
    private final int inputMaxLength;
    private final long matchMaxSteps;

    PatternsLocator(
        EngineContext context,
        PatternsMatcher matcher,
        int inputMaxLength,
        long matchMaxSteps)
    {
        this.context = context;
        this.matcher = matcher;
        this.inputMaxLength = inputMaxLength;
        this.matchMaxSteps = matchMaxSteps;
    }

    @Override
    public void locate(
        long traceId,
        long bindingId,
        long contextId,
        DirectBuffer data,
        int index,
        int length,
        SpanConsumer spans,
        CompletionCallback completion)
    {
        byte[] value = new byte[length];
        data.getBytes(index, value);

        try
        {
            String text = UTF_8.newDecoder()
                .onMalformedInput(REPORT)
                .decode(ByteBuffer.wrap(value))
                .toString();

            context.dispatch(() -> complete(contextId, index, text, spans, completion));
        }
        catch (CharacterCodingException ex)
        {
            context.dispatch(() -> completion.failed(contextId, ex));
        }
    }

    private void complete(
        long contextId,
        int index,
        String text,
        SpanConsumer spans,
        CompletionCallback completion)
    {
        int count = 0;
        Throwable failure = null;

        if (text.length() > inputMaxLength)
        {
            failure = new IllegalArgumentException(
                "value length " + text.length() + " exceeds " + inputMaxLength);
        }
        else
        {
            try
            {
                count = matcher.locate(text, matchMaxSteps);
            }
            catch (IllegalStateException | StackOverflowError ex)
            {
                failure = ex;
            }
        }

        if (failure == null)
        {
            report(index, text, count, spans);
            completion.completed(contextId);
        }
        else
        {
            completion.failed(contextId, failure);
        }
    }

    private void report(
        int index,
        String text,
        int count,
        SpanConsumer spans)
    {
        int limit = 0;
        int offset = index;

        for (int span = 0; span < count; span++)
        {
            final int start = matcher.start(span);
            final int end = matcher.end(span);
            final int length = utf8Length(text, start, end);

            offset += utf8Length(text, limit, start);
            spans.span(matcher.label(span), offset, length);
            offset += length;
            limit = end;
        }
    }

    private static int utf8Length(
        String text,
        int from,
        int to)
    {
        int length = 0;

        for (int i = from; i < to; i++)
        {
            final char c = text.charAt(i);

            if (c < 0x80)
            {
                length += 1;
            }
            else if (c < 0x800)
            {
                length += 2;
            }
            else if (Character.isHighSurrogate(c))
            {
                length += 4;
                i++;
            }
            else
            {
                length += 3;
            }
        }

        return length;
    }
}
