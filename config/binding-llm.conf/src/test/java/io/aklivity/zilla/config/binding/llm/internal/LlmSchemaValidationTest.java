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

import io.aklivity.zilla.config.engine.ConfigException;
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

    @Test
    public void shouldAcceptServerWithGeminiDialect()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialect: gemini
                exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptServerWithAnthropicDialect()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialect: anthropic
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
                  server: http://example.com:8080
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
                  server: http://example.com:8080
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientWithAnthropicDialect()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientWithGeminiDialect()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: gemini
                  server: http://example.com:8080
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptServerAndClientWithDifferentDialects()
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
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                exit: net1
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
                  server: http://example.com:8080
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
                kind: bogus
                exit: app0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptServerWithEmptyOptions()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options: {}
                exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerWithUnknownOption()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  unknown: value
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerWithUnregisteredDialect()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialect: foo
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerWithNonStringDialect()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialect: 42
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithEmptyOptions()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options: {}
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithUnknownOption()
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
                  server: http://example.com:8080
                  unknown: value
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithUnregisteredDialect()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: foo
                  server: http://example.com:8080
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithNonStringDialect()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: 42
                  server: http://example.com:8080
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithNonStringServer()
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
                  server: 8080
                exit: net0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptProxyWithRoutesWhenDialectAndModel()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                routes:
                - when:
                  - dialect: openai
                    model: [ gpt-4o, gpt-4o-mini ]
                  exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptProxyWithRoutesWhenGeminiDialect()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                routes:
                - when:
                  - dialect: gemini
                    model: [ gemini-2.0-flash ]
                  exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptProxyWithRoutesWhenModelGlobAnyDialect()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                routes:
                - when:
                  - model: [ "claude-*" ]
                  exit: app0
                - when:
                  - model: [ "gpt-*" ]
                  exit: app1
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptProxyWithBareExitAndNoRoutes()
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

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectProxyRouteWhenWithUnknownProperty()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                routes:
                - when:
                  - unknown: value
                  exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectProxyRouteWhenWithUnregisteredDialect()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                routes:
                - when:
                  - dialect: foo
                  exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithoutOptions()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerWithVault()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: server
                vault: vault0
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerWithCatalog()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: server
                catalog:
                  catalog0:
                    - subject: subject0
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = ConfigException.class)
    public void shouldRejectServerRouteWith()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: server
                routes:
                  - with:
                      unknown: value
                    exit: app0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptServerRouteWhenDialect()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: server
                routes:
                  - when:
                      - dialect: openai
                    exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptServerRouteWhenModelGlob()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: server
                routes:
                  - when:
                      - model: [ gpt-4*, o1 ]
                    exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptServerGuardedRoute()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: server
                routes:
                  - guarded:
                      guard0:
                        - read
                    exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectProxyWithVault()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: proxy
                vault: vault0
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectProxyWithCatalog()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: proxy
                catalog:
                  catalog0:
                    - subject: subject0
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = ConfigException.class)
    public void shouldRejectProxyRouteWith()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: proxy
                routes:
                  - with:
                      unknown: value
                    exit: app0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptProxyRouteWithFallback()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: proxy
                routes:
                  - when:
                      - model: [ claude-sonnet-4-6 ]
                    exit: app0
                    with:
                      fallback: [ claude-opus-4-1, claude-haiku-4-5 ]
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = ConfigException.class)
    public void shouldRejectProxyRouteWithEmptyFallback()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: proxy
                routes:
                  - exit: app0
                    with:
                      fallback: []
            """;

        reader.read(text);
    }

    @Test(expected = ConfigException.class)
    public void shouldRejectProxyRouteWithFallbackNotArray()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: proxy
                routes:
                  - exit: app0
                    with:
                      fallback: claude-opus-4-1
            """;

        reader.read(text);
    }

    @Test(expected = ConfigException.class)
    public void shouldRejectProxyRouteWithFallbackPattern()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: proxy
                routes:
                  - exit: app0
                    with:
                      fallback: [ "claude-*" ]
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithVault()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: client
                vault: vault0
                options:
                  dialect: openai
                  server: http://example.com:8080
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithCatalog()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: client
                catalog:
                  catalog0:
                    - subject: subject0
                options:
                  dialect: openai
                  server: http://example.com:8080
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = ConfigException.class)
    public void shouldRejectClientRouteWith()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: client
                options:
                  dialect: openai
                  server: http://example.com:8080
                routes:
                  - with:
                      unknown: value
                    exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientRouteWhen()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: client
                options:
                  dialect: openai
                  server: http://example.com:8080
                routes:
                  - when:
                      - dialect: openai
                    exit: net0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptClientGuardedRoute()
    {
        String text =
            """
            name: test
            bindings:
              b0:
                type: llm
                kind: client
                options:
                  dialect: openai
                  server: http://example.com:8080
                routes:
                  - guarded:
                      guard0:
                        - read
                    exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptServerWithAuthorization()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  authorization:
                    guard0:
                      credentials: "Bearer {credentials}"
                exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerAuthorizationWithoutCredentialsPlaceholder()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  authorization:
                    guard0:
                      credentials: static-token
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerAuthorizationWithMultipleGuards()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  authorization:
                    guard0:
                      credentials: "Bearer {credentials}"
                    guard1:
                      credentials: "Bearer {credentials}"
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerAuthorizationWithUnknownProperty()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  authorization:
                    guard0:
                      credentials: "Bearer {credentials}"
                      unknown: value
                exit: app0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptClientWithAuthorization()
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
                  server: https://api.example.com
                  authorization:
                    guard0:
                      credentials: "x-api-key {credentials}"
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientAuthorizationWithoutCredentialsPlaceholder()
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
                  server: https://api.example.com
                  authorization:
                    guard0:
                      credentials: static-token
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientAuthorizationWithMultipleGuards()
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
                  server: https://api.example.com
                  authorization:
                    guard0:
                      credentials: "x-api-key {credentials}"
                    guard1:
                      credentials: "x-api-key {credentials}"
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientAuthorizationWithUnknownProperty()
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
                  server: https://api.example.com
                  authorization:
                    guard0:
                      credentials: "x-api-key {credentials}"
                      unknown: value
                exit: net0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptClientWithServerHostAndPort()
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
                  server: http://localhost:8080
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectProxyWithOptions()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                options:
                  dialect: openai
                exit: app0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptProxyWithGuardrails()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                options:
                  guardrails:
                    gpt-4o:
                      request:
                        - section: [ user-text, tool-result ]
                          value: test
                exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptProxyWithGuardrailsSectionAsString()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                options:
                  guardrails:
                    gpt-4o:
                      request:
                        - section: user-text
                          value: test
                      response:
                        - section: assistant-text
                          value: test
                exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectProxyGuardrailsWithUnknownSection()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                options:
                  guardrails:
                    gpt-4o:
                      request:
                        - section: user-poem
                          value: test
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectProxyGuardrailsWithoutValue()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                options:
                  guardrails:
                    gpt-4o:
                      request:
                        - section: user-text
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectProxyGuardrailsWithoutSection()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                options:
                  guardrails:
                    gpt-4o:
                      request:
                        - value: test
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectProxyGuardrailsWithUnknownProperty()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                options:
                  guardrails:
                    gpt-4o:
                      request:
                        - section: user-text
                          value: test
                      other: []
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerWithGuardrails()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  guardrails:
                    gpt-4o:
                      request:
                        - section: user-text
                          value: test
                exit: app0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptClientWithServerHyphenatedHostAndPath()
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
                  server: "http://mock-openai:4101/aicomp/v1"
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientWithServerIpv4Address()
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
                  server: "http://10.0.0.1:8080"
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientWithDialectOptions()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: test-dialect
                  server: http://example.com:8080
                  test-settings:
                    region: us-east-1
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientWithExtensionDialectWithoutOptions()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: test-dialect
                  server: http://example.com:8080
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectDialectOptionsForOtherDialect()
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
                  server: http://example.com:8080
                  test-settings:
                    region: us-east-1
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectUnknownOptionForExtensionDialect()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: test-dialect
                  server: http://example.com:8080
                  bogus: true
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectInvalidDialectOptions()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: test-dialect
                  server: http://example.com:8080
                  test-settings:
                    bogus: true
                exit: net0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptServerWithDialectOptions()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialect: test-dialect
                  test-settings:
                    region: us-east-1
                exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptServerWithExtensionDialectWithoutOptions()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialect: test-dialect
                exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerDialectOptionsForOtherDialect()
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
                  test-settings:
                    region: us-east-1
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerDialectOptionsWithoutDialect()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  test-settings:
                    region: us-east-1
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectUnknownServerOptionForExtensionDialect()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialect: test-dialect
                  bogus: true
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectInvalidServerDialectOptions()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialect: test-dialect
                  test-settings:
                    bogus: true
                exit: app0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptClientAnthropicModels()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  models:
                    claude-opus-4-7:
                      sampling: false
                      thinking: adaptive
                    claude-3-*:
                      thinking: enabled
                      budget:
                        minimum: 1024
                      ladder:
                        low: 2048
                        medium: 4096
                        high: 8192
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientAnthropicModelThinkingForms()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  models:
                    adaptive-only:
                      thinking: adaptive
                    adaptive-disabled:
                      thinking: adaptive-disabled
                    adaptive-enabled:
                      thinking: adaptive-enabled
                    enabled-only:
                      thinking: enabled
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientGeminiModels()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: gemini
                  server: http://example.com:8080
                  models:
                    gemini-2.5-pro:
                      thinking: budget
                      budget:
                        minimum: 128
                        maximum: 32768
                      ladder:
                        low: 1024
                        medium: 8192
                        high: 24576
                    gemini-3-*:
                      thinking: level
                      sampling: true
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientOpenaiModels()
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
                  server: http://example.com:8080
                  models:
                    o3*:
                      sampling: false
                      ladder:
                        low: 1024
                        high: 24576
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientAnthropicModelUnknownThinking()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  models:
                    claude-opus-4-7:
                      thinking: level
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientGeminiModelUnknownThinking()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: gemini
                  server: http://example.com:8080
                  models:
                    gemini-3-pro:
                      thinking: adaptive
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientOpenaiModelThinking()
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
                  server: http://example.com:8080
                  models:
                    gpt-5:
                      thinking: adaptive
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientOpenaiModelBudget()
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
                  server: http://example.com:8080
                  models:
                    gpt-5:
                      budget:
                        minimum: 1024
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientModelUnknownProperty()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  models:
                    claude-opus-4-7:
                      bogus: true
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientModelSamplingNotBoolean()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  models:
                    claude-opus-4-7:
                      sampling: no
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientModelLadderUnknownEffort()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  models:
                    claude-opus-4-7:
                      ladder:
                        extreme: 4096
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientModelLadderNegative()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  models:
                    claude-opus-4-7:
                      ladder:
                        low: -1
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientModelBudgetNegative()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: gemini
                  server: http://example.com:8080
                  models:
                    gemini-2.5-pro:
                      budget:
                        minimum: -1
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientModelBudgetUnknownProperty()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: gemini
                  server: http://example.com:8080
                  models:
                    gemini-2.5-pro:
                      budget:
                        off: true
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientModelsNotObject()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  models:
                    - claude-opus-4-7
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerWithModels()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialect: anthropic
                  models:
                    claude-opus-4-7:
                      sampling: false
                exit: app0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptClientOpenaiModelTiers()
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
                  server: http://example.com:8080
                  models:
                    gpt-5:
                      tiers:
                        - auto
                        - default
                        - flex
                        - priority
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientAnthropicModelTiers()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  models:
                    claude-opus-4-7:
                      tiers:
                        - auto
                        - default
                        - flex
                        - priority
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientGeminiModelTiers()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: gemini
                  server: http://example.com:8080
                  models:
                    gemini-3-flash:
                      tiers:
                        - auto
                        - default
                        - flex
                        - priority
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientModelNoTiers()
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
                  server: http://example.com:8080
                  models:
                    gpt-5:
                      tiers: []
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientModelTiersUnknown()
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
                  server: http://example.com:8080
                  models:
                    gpt-5:
                      tiers:
                        - scale
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientModelTiersNotArray()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  models:
                    claude-opus-4-7:
                      tiers: auto
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientModelTiersDuplicate()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: gemini
                  server: http://example.com:8080
                  models:
                    gemini-3-flash:
                      tiers:
                        - flex
                        - flex
                exit: net0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptClientOpenaiModelUnsupportedAndExclusive()
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
                  server: http://example.com:8080
                  models:
                    m-1:
                      unsupported:
                        - parallel_tool_calls
                        - response_format
                      exclusive:
                        - - reasoning_effort
                          - thinking_budget_tokens
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientOpenaiUnsupportedAndExclusive()
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
                  server: http://example.com:8080
                  unsupported:
                    - temperature
                    - top_p
                    - top_k
                    - stop
                    - seed
                    - frequency_penalty
                    - presence_penalty
                    - tool_choice
                    - parallel_tool_calls
                    - response_format
                    - reasoning_effort
                    - thinking_budget_tokens
                    - user
                    - service_tier
                  exclusive:
                    - - thinking_budget_tokens
                      - reasoning_effort
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientAnthropicModelUnsupportedAndExclusive()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  models:
                    m-1:
                      unsupported:
                        - parallel_tool_calls
                        - response_format
                      exclusive:
                        - - reasoning_effort
                          - thinking_budget_tokens
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientAnthropicUnsupportedAndExclusive()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  unsupported:
                    - temperature
                    - top_p
                    - top_k
                    - stop
                    - seed
                    - frequency_penalty
                    - presence_penalty
                    - tool_choice
                    - parallel_tool_calls
                    - response_format
                    - reasoning_effort
                    - thinking_budget_tokens
                    - user
                    - service_tier
                  exclusive:
                    - - thinking_budget_tokens
                      - reasoning_effort
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientGeminiModelUnsupportedAndExclusive()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: gemini
                  server: http://example.com:8080
                  models:
                    m-1:
                      unsupported:
                        - parallel_tool_calls
                        - response_format
                      exclusive:
                        - - reasoning_effort
                          - thinking_budget_tokens
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientGeminiUnsupportedAndExclusive()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: gemini
                  server: http://example.com:8080
                  unsupported:
                    - temperature
                    - top_p
                    - top_k
                    - stop
                    - seed
                    - frequency_penalty
                    - presence_penalty
                    - tool_choice
                    - parallel_tool_calls
                    - response_format
                    - reasoning_effort
                    - thinking_budget_tokens
                    - user
                    - service_tier
                  exclusive:
                    - - thinking_budget_tokens
                      - reasoning_effort
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientModelNoUnsupportedAndNoExclusive()
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
                  server: http://example.com:8080
                  unsupported:
                    - seed
                  models:
                    gpt-5:
                      unsupported: []
                      exclusive: []
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientUnsupportedUnknown()
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
                  server: http://example.com:8080
                  unsupported:
                    - logit_bias
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientUnsupportedDuplicate()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  unsupported:
                    - seed
                    - seed
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientUnsupportedNotArray()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: gemini
                  server: http://example.com:8080
                  unsupported: seed
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientModelUnsupportedUnknown()
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
                  server: http://example.com:8080
                  models:
                    gpt-5:
                      unsupported:
                        - logit_bias
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientModelUnsupportedDuplicate()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: gemini
                  server: http://example.com:8080
                  models:
                    gemini-3-flash:
                      unsupported:
                        - stop
                        - stop
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientExclusiveUnknownMember()
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
                  server: http://example.com:8080
                  exclusive:
                    - - reasoning_effort
                      - logit_bias
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientExclusiveGroupTooSmall()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  exclusive:
                    - - reasoning_effort
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientExclusiveGroupNotArray()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: gemini
                  server: http://example.com:8080
                  exclusive:
                    - reasoning_effort
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientExclusiveNotRecorded()
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
                  server: http://example.com:8080
                  exclusive:
                    - - stop
                      - seed
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientModelExclusiveDuplicateMember()
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
                  server: http://example.com:8080
                  models:
                    gpt-5:
                      exclusive:
                        - - reasoning_effort
                          - reasoning_effort
                exit: net0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptClientWithOpenaiApis()
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
                  server: http://example.com:8080
                  apis: [ chat/completions, responses ]
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptClientWithOpenaiResponsesApiOnly()
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
                  server: http://example.com:8080
                  apis: [ responses ]
                exit: net0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientApisForAnthropic()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialect: anthropic
                  server: http://example.com:8080
                  apis: [ chat/completions ]
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientApisUnknownApi()
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
                  server: http://example.com:8080
                  apis: [ embeddings ]
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientApisEmpty()
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
                  server: http://example.com:8080
                  apis: []
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientApisDuplicate()
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
                  server: http://example.com:8080
                  apis: [ responses, responses ]
                exit: net0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptServerWithOpenaiApis()
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
                  apis: [ chat/completions ]
                exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerApisWithoutDialect()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  apis: [ chat/completions ]
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerApisForAnthropic()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialect: anthropic
                  apis: [ chat/completions ]
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerApisUnknownApi()
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
                  apis: [ embeddings ]
                exit: app0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptProxyWithRoutesWhenOpenaiApi()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                routes:
                - when:
                  - dialect: openai
                    api: responses
                  exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectProxyWithRoutesWhenApiWithoutDialect()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                routes:
                - when:
                  - api: responses
                  exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectProxyWithRoutesWhenApiForAnthropic()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                routes:
                - when:
                  - dialect: anthropic
                    api: responses
                  exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectProxyWithRoutesWhenUnknownApi()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: proxy
                routes:
                - when:
                  - dialect: openai
                    api: embeddings
                  exit: app0
            """;

        reader.read(text);
    }

    @Test
    public void shouldAcceptServerWithDialects()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialects: [ openai, gemini ]
                exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptServerWithDialectsWithApis()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialects:
                    - openai:
                        apis: [ chat/completions ]
                    - anthropic
                exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test
    public void shouldAcceptServerWithDialectsAndAuthorization()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialects: [ openai, anthropic ]
                  authorization:
                    guard0:
                      credentials: "Bearer {credentials}"
                exit: app0
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerWithDialectAndDialects()
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
                  dialects: [ anthropic ]
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerDialectsEmpty()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialects: []
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerDialectsUnknownDialect()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialects: [ foo ]
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerDialectsObjectWithTwoKeys()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialects:
                    - openai: {}
                      anthropic: {}
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerDialectsApisForAnthropic()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialects:
                    - anthropic:
                        apis: [ responses ]
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerDialectsUnknownApi()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialects:
                    - openai:
                        apis: [ embeddings ]
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerDialectsDuplicateName()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialects: [ openai, openai ]
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerDialectsDuplicateNameAsObject()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialects:
                    - openai
                    - openai:
                        apis: [ responses ]
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerApisWithDialects()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: llm
                kind: server
                options:
                  dialects: [ openai ]
                  apis: [ responses ]
                exit: app0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithDialects()
    {
        String text =
            """
            name: test
            bindings:
              app0:
                type: llm
                kind: client
                options:
                  dialects: [ openai ]
                  server: http://example.com:8080
                exit: net0
            """;

        reader.read(text);
    }
}
