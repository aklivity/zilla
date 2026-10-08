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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.classifier.Detector;

public class TestClassifierHandlerTest
{
    private final Deque<Runnable> deferred = new ArrayDeque<>();
    private final TestClassifierHandler handler = new TestClassifierHandler(deferred::addLast);

    @Test
    public void shouldDetectLabelPresentInValue()
    {
        Boolean[] detected = new Boolean[1];
        Detector detector = handler.initDetector(List.of("secret"));

        detector.detect(0L, 0L, 42L, "a secret value", completion(42L, detected));
        assertNull(detected[0]);

        deferred.remove().run();
        assertTrue(detected[0]);
    }

    @Test
    public void shouldNotDetectLabelAbsentFromValue()
    {
        Boolean[] detected = new Boolean[1];
        Detector detector = handler.initDetector(List.of("secret"));

        detector.detect(0L, 0L, 7L, "a plain value", completion(7L, detected));
        assertNull(detected[0]);

        deferred.remove().run();
        assertFalse(detected[0]);
    }

    @Test
    public void shouldDetectAnyOfSeveralLabels()
    {
        Boolean[] detected = new Boolean[1];
        Detector detector = handler.initDetector(List.of("alpha", "beta"));

        detector.detect(0L, 0L, 1L, "contains beta", completion(1L, detected));
        deferred.remove().run();

        assertTrue(detected[0]);
    }

    @Test
    public void shouldNotProvideAnonymizerOrDeanonymizer()
    {
        assertNull(handler.initAnonymizer(List.of("secret")));
        assertNull(handler.initDeanonymizer(List.of("secret")));
    }

    private static Detector.CompletionCallback completion(
        long expectedContextId,
        Boolean[] detected)
    {
        return new Detector.CompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                boolean result)
            {
                assertEquals(expectedContextId, contextId);
                detected[0] = result;
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
                throw new AssertionError(ex);
            }
        };
    }
}
