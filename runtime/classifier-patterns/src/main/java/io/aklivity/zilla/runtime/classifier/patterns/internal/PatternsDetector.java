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

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.Detector;

final class PatternsDetector implements Detector
{
    private final EngineContext context;
    private final PatternsMatcher matcher;
    private final int inputMaxLength;
    private final long matchMaxSteps;

    PatternsDetector(
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
    public void detect(
        long traceId,
        long bindingId,
        long contextId,
        String value,
        CompletionCallback completion)
    {
        context.dispatch(() -> complete(contextId, value, completion));
    }

    private void complete(
        long contextId,
        String value,
        CompletionCallback completion)
    {
        boolean detected = false;
        Throwable failure = null;

        if (value.length() > inputMaxLength)
        {
            failure = new IllegalArgumentException(
                "value length " + value.length() + " exceeds " + inputMaxLength);
        }
        else
        {
            try
            {
                detected = matcher.find(value, matchMaxSteps);
            }
            catch (IllegalStateException | StackOverflowError ex)
            {
                failure = ex;
            }
        }

        if (failure == null)
        {
            completion.completed(contextId, detected);
        }
        else
        {
            completion.failed(contextId, failure);
        }
    }
}
