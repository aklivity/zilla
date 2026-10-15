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
package io.aklivity.zilla.runtime.classifier.presidio.internal;


import static java.nio.charset.CodingErrorAction.REPORT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.Anonymizer;
import io.aklivity.zilla.runtime.engine.classifier.ValueConsumerEx;

final class PresidioAnonymizer implements Anonymizer
{
    private final EngineContext context;
    private final PresidioAnalyzer analyzer;
    private final PresidioSurrogates surrogates;
    private final Map<String, String> aliasesByEntity;

    PresidioAnonymizer(
        EngineContext context,
        PresidioAnalyzer analyzer,
        PresidioSurrogates surrogates,
        Map<String, String> aliasesByEntity)
    {
        this.context = context;
        this.analyzer = analyzer;
        this.surrogates = surrogates;
        this.aliasesByEntity = aliasesByEntity;
    }

    @Override
    public void anonymize(
        long traceId,
        long bindingId,
        long scopeId,
        long contextId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumerEx next,
        CompletionCallback completion)
    {
        if (length == 0)
        {
            context.dispatch(() -> completion.completed(contextId, 0));
        }
        else
        {
            byte[] value = new byte[length];
            data.getBytes(index, value);

            try
            {
                String text = UTF_8.newDecoder()
                    .onMalformedInput(REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();

                analyzer.analyze(text).whenComplete((body, failure) ->
                    context.dispatch(() -> complete(scopeId, contextId, value, text, body, failure, next, completion)));
            }
            catch (CharacterCodingException ex)
            {
                context.dispatch(() -> completion.failed(contextId, ex));
            }
        }
    }

    private void complete(
        long scopeId,
        long contextId,
        byte[] value,
        String text,
        String body,
        Throwable failure,
        ValueConsumerEx next,
        CompletionCallback completion)
    {
        Throwable cause = PresidioAnalyzer.cause(failure);
        List<PresidioFinding> spans = List.of();

        if (cause == null)
        {
            try
            {
                List<PresidioFinding> findings = PresidioResponse.findings(body);
                findings.removeIf(finding -> !aliasesByEntity.containsKey(finding.entity()));
                spans = PresidioSpans.resolve(findings, text.codePointCount(0, text.length()));
            }
            catch (JsonException | IllegalArgumentException ex)
            {
                cause = ex;
            }
        }

        if (cause == null)
        {
            completion.completed(contextId, substitute(scopeId, value, spans, next));
        }
        else
        {
            completion.failed(contextId, cause);
        }
    }

    private int substitute(
        long scopeId,
        byte[] value,
        List<PresidioFinding> spans,
        ValueConsumerEx next)
    {
        DirectBuffer buffer = new UnsafeBuffer(value);
        int cursor = 0;
        int cursorPoint = 0;
        int consumed = 0;
        boolean accepting = true;

        for (int i = 0; accepting && i < spans.size(); i++)
        {
            PresidioFinding span = spans.get(i);
            int spanStart = skip(value, cursor, span.start() - cursorPoint);
            int spanEnd = skip(value, spanStart, span.end() - span.start());

            consumed = cursor + write(next, buffer, cursor, spanStart - cursor);
            accepting = consumed == spanStart;

            if (accepting)
            {
                PresidioSurrogate surrogate = surrogates.mint(scopeId, span.entity(), aliasesByEntity.get(span.entity()),
                    new String(value, spanStart, spanEnd - spanStart, UTF_8));

                accepting = next.writeSpan(surrogate.alias, surrogate.tokenBuffer, 0, surrogate.tokenBuffer.capacity());

                if (accepting)
                {
                    cursor = spanEnd;
                    cursorPoint = span.end();
                    consumed = cursor;
                }
            }
        }

        if (accepting)
        {
            consumed = cursor + write(next, buffer, cursor, value.length - cursor);
        }

        return consumed;
    }

    private static int write(
        ValueConsumerEx next,
        DirectBuffer buffer,
        int index,
        int length)
    {
        return length > 0 ? next.write(buffer, index, length) : 0;
    }

    private static int skip(
        byte[] value,
        int from,
        int points)
    {
        int position = from;

        for (int i = 0; i < points; i++)
        {
            position++;

            while (position < value.length && (value[position] & 0xC0) == 0x80)
            {
                position++;
            }
        }

        return position;
    }
}
