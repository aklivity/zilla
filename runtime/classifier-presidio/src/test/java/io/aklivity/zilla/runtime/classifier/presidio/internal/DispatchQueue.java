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
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.mockito.invocation.InvocationOnMock;

import io.aklivity.zilla.runtime.engine.EngineContext;

final class DispatchQueue
{
    private final BlockingQueue<Runnable> tasks;
    private final EngineContext engine;

    DispatchQueue()
    {
        this.tasks = new LinkedBlockingQueue<>();
        this.engine = mock(EngineContext.class);
        doAnswer(this::enqueue).when(engine).dispatch(any());
    }

    EngineContext engine()
    {
        return engine;
    }

    void runNext() throws InterruptedException
    {
        Runnable task = tasks.poll(5, SECONDS);

        assertThat(task, notNullValue());
        task.run();
    }

    private Object enqueue(
        InvocationOnMock invocation)
    {
        tasks.add(invocation.getArgument(0));
        return null;
    }
}
