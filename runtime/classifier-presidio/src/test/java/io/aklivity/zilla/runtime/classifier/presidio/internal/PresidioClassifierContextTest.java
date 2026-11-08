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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.classifier.presidio.PresidioClassifierOptionsConfig;
import io.aklivity.zilla.config.engine.ClassifierConfig;
import io.aklivity.zilla.config.engine.GenericClassifierConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.ClassifierHandler;
import io.aklivity.zilla.runtime.engine.classifier.Detector;

public class PresidioClassifierContextTest
{
    private static final long SLOW_DISPATCH_MILLIS = 250L;

    private PresidioClassifierContext context;
    private ClassifierConfig config;

    @Before
    public void init()
    {
        context = new PresidioClassifierContext(mock(EngineContext.class));

        config = GenericClassifierConfig.builder()
            .namespace("test")
            .name("presidio0")
            .type("presidio")
            .options(PresidioClassifierOptionsConfig.builder()
                .endpoint("http://localhost:3000")
                .build())
            .build();
    }

    @Test
    public void shouldAttachAndDetach()
    {
        ClassifierHandler handler = context.attach(config);

        assertThat(handler, instanceOf(PresidioClassifierHandler.class));

        context.detach(config);
    }

    @Test
    public void shouldDetachWhenNotAttached()
    {
        context.detach(config);
    }

    @Test
    public void shouldNotDispatchAfterDetachReturnsWhenDetachCancelsInFlightAnalyze() throws Exception
    {
        CountDownLatch gate = new CountDownLatch(1);
        AtomicBoolean detachReturned = new AtomicBoolean();
        AtomicBoolean dispatchedAfterDetach = new AtomicBoolean();
        CountDownLatch dispatched = new CountDownLatch(1);
        EngineContext engine = mock(EngineContext.class);
        doAnswer(invocation ->
        {
            Thread.sleep(SLOW_DISPATCH_MILLIS);
            dispatchedAfterDetach.set(detachReturned.get());
            dispatched.countDown();
            return null;
        }).when(engine).dispatch(any(Runnable.class));

        try (AnalyzerStub analyzer = new AnalyzerStub())
        {
            try
            {
                analyzer.hold(gate);
                config = GenericClassifierConfig.builder()
                    .namespace("test")
                    .name("presidio0")
                    .type("presidio")
                    .options(PresidioClassifierOptionsConfig.builder()
                        .endpoint(analyzer.endpoint())
                        .build())
                    .build();
                context = new PresidioClassifierContext(engine);

                ClassifierHandler handler = context.attach(config);
                Detector detector = handler.initDetector(List.of("PERSON"));
                detector.detect(1L, 2L, 3L, "jane.doe@example.com", new Detector.CompletionCallback()
                {
                    @Override
                    public void completed(
                        long contextId,
                        boolean detected,
                        int retained)
                    {
                    }

                    @Override
                    public void failed(
                        long contextId,
                        Throwable ex)
                    {
                    }
                });
                long deadline = System.nanoTime() + SECONDS.toNanos(15L);
                while (analyzer.requests().isEmpty() && System.nanoTime() < deadline)
                {
                    Thread.sleep(10L);
                }
                assertThat(analyzer.requests().isEmpty(), is(false));

                context.detach(config);
                detachReturned.set(true);

                assertThat(dispatched.await(5L, SECONDS), is(true));
                assertThat(dispatchedAfterDetach.get(), is(false));
            }
            finally
            {
                gate.countDown();
            }
        }
    }
}
