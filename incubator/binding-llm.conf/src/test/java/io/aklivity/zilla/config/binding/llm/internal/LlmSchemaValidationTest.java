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
package io.aklivity.zilla.config.binding.llm.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.config.engine.EngineConfig;
import io.aklivity.zilla.config.engine.EngineConfigReader;
import io.aklivity.zilla.config.engine.EngineInfo;

public class LlmSchemaValidationTest
{
    private final EngineConfigReader reader = new EngineConfigReader(
        text -> text, new EngineInfo(), LlmSchemaValidationTest::noop, LlmSchemaValidationTest::noop);

    private static void noop(
        String value)
    {
    }

    @Test
    public void shouldAcceptServerWithoutOptions()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptServerWithFixedDialect()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialect: openai
                exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerOption()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  server: example.com:8080
                exit: app0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptClientWithDialectAndServer()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: openai
                  server: example.com:8080
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientMissingServer()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: openai
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientMissingDialect()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  server: example.com:8080
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithMalformedServer()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: openai
                  server: not-a-host-and-port
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectUnknownKind()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                exit: app0
            """;

        reader.read(text);
    }
}
