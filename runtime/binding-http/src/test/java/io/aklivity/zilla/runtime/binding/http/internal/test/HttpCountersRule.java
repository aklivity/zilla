/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal.test;

import static org.junit.Assert.assertEquals;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import io.aklivity.zilla.runtime.engine.test.EngineRule;

public class HttpCountersRule implements TestRule
{
    private final EngineRule engine;

    public HttpCountersRule(
        EngineRule engine)
    {
        this.engine = engine;
    }

    @Override
    public Statement apply(Statement base, Description description)
    {
        return new Statement()
        {

            @Override
            public void evaluate() throws Throwable
            {
                assertEquals(0, streams());
                assertEquals(0, routes());
                assertEquals(0, enqueues());
                assertEquals(0, dequeues());
                base.evaluate();
                assertEquals(enqueues(), dequeues());
            }

        };
    }

    public long routes()
    {
        return engine.counter("http.routes");
    }

    public long streams()
    {
        return engine.counter("http.streams");
    }

    public long enqueues()
    {
        return engine.counter("http.enqueues");
    }

    public long dequeues()
    {
        return engine.counter("http.dequeues");
    }

    public long requestsRejected()
    {
        return engine.counter("http.requests.rejected");
    }
}
