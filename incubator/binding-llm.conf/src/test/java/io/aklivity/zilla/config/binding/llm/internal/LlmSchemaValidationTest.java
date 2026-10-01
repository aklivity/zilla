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
                  dialect: gemini
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
                  dialect: gemini
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
                  - dialect: gemini
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

    @Test(expected = RuntimeException.class)
    public void shouldRejectServerRouteWhen()
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

        reader.read(text);
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
    public void shouldRejectClientWithHostlessServer()
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
                  server: "https://:8080"
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithServerWithoutHost()
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
                  server: "https://"
                exit: net0
            """;

        reader.read(text);
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

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithServerHostContainingUnderscore()
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
                  server: "http://mock_openai:4101"
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithServerHostThatIsNotHostname()
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
                  server: "http://999.1.1.1"
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithServerNonNumericPort()
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
                  server: "https://example.com:abc"
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithServerUserInfo()
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
                  server: "https://user@example.com"
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithServerQuery()
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
                  server: "https://example.com/v1?key=value"
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithServerFragment()
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
                  server: "https://example.com/v1#section"
                exit: net0
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectClientWithServerIllegalPathCharacter()
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
                  server: "https://example.com/v1/{model}"
                exit: net0
            """;

        reader.read(text);
    }
}
