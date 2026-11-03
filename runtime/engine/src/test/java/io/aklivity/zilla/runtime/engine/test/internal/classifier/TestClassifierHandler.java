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

import java.util.List;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.engine.classifier.ClassifierHandler;
import io.aklivity.zilla.runtime.engine.classifier.Detector;

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
