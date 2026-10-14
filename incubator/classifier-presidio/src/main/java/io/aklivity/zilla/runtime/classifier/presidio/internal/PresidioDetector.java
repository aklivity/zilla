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

import jakarta.json.JsonException;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.Detector;

final class PresidioDetector implements Detector
{
    private final EngineContext context;
    private final PresidioAnalyzer analyzer;

    PresidioDetector(
        EngineContext context,
        PresidioAnalyzer analyzer)
    {
        this.context = context;
        this.analyzer = analyzer;
    }

    @Override
    public void detect(
        long traceId,
        long bindingId,
        long contextId,
        String value,
        CompletionCallback completion)
    {
        if (value.isEmpty())
        {
            context.dispatch(() -> completion.completed(contextId, false));
        }
        else
        {
            analyzer.analyze(value).whenComplete((body, failure) ->
                context.dispatch(() -> complete(contextId, body, failure, completion)));
        }
    }

    private void complete(
        long contextId,
        String body,
        Throwable failure,
        CompletionCallback completion)
    {
        boolean detected = false;
        Throwable cause = PresidioAnalyzer.cause(failure);

        if (cause == null)
        {
            try
            {
                detected = PresidioResponse.detected(body);
            }
            catch (JsonException | IllegalArgumentException ex)
            {
                cause = ex;
            }
        }

        if (cause == null)
        {
            completion.completed(contextId, detected);
        }
        else
        {
            completion.failed(contextId, cause);
        }
    }
}
