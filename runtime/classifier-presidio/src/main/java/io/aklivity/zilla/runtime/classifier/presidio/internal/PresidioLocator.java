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

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.Locator;

final class PresidioLocator implements Locator
{
    private final EngineContext context;
    private final PresidioAnalyzer analyzer;
    private final Map<String, String> aliasesByEntity;

    PresidioLocator(
        EngineContext context,
        PresidioAnalyzer analyzer,
        Map<String, String> aliasesByEntity)
    {
        this.context = context;
        this.analyzer = analyzer;
        this.aliasesByEntity = aliasesByEntity;
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
        if (length == 0)
        {
            context.dispatch(() -> completion.completed(contextId));
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

                analyzer.analyze(text, (body, failure) ->
                    context.dispatch(() -> complete(contextId, index, value, body, failure, spans, completion)));
            }
            catch (CharacterCodingException ex)
            {
                context.dispatch(() -> completion.failed(contextId, ex));
            }
        }
    }

    private void complete(
        long contextId,
        int index,
        byte[] value,
        String body,
        Throwable failure,
        SpanConsumer spans,
        CompletionCallback completion)
    {
        Throwable cause = PresidioAnalyzer.cause(failure);
        List<PresidioFinding> located = List.of();

        if (cause == null)
        {
            try
            {
                List<PresidioFinding> findings = PresidioResponse.findings(body);
                findings.removeIf(finding -> !aliasesByEntity.containsKey(finding.entity()));
                located = PresidioSpans.resolve(findings, value);
            }
            catch (JsonException | IllegalArgumentException ex)
            {
                cause = ex;
            }
        }

        if (cause == null)
        {
            for (int i = 0; i < located.size(); i++)
            {
                PresidioFinding span = located.get(i);
                spans.span(aliasesByEntity.get(span.entity()), index + span.start(), span.end() - span.start());
            }

            completion.completed(contextId);
        }
        else
        {
            completion.failed(contextId, cause);
        }
    }
}
