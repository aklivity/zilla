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
package io.aklivity.zilla.config.binding.mcp.grpc.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.config.engine.EngineConfig;
import io.aklivity.zilla.config.engine.EngineConfigReader;
import io.aklivity.zilla.config.engine.EngineInfo;

public class McpGrpcSchemaValidationTest
{
    private final EngineConfigReader reader = new EngineConfigReader(
        text -> text, new EngineInfo(), McpGrpcSchemaValidationTest::noop, McpGrpcSchemaValidationTest::noop);

    private static void noop(
        String value)
    {
    }

    @Test
    public void shouldAcceptClientWithRoutes()
    {
        String text =
            """
            name: test
            bindings:
              mcpgrpc0:
                type: mcp-grpc
                kind: client
                options:
                  server: http://localhost:8080
                  catalog:
                    catalog0:
                      subject: sql
                      version: latest
                  tools:
                    query:
                      summary: Ran SQL query
                routes:
                  - when:
                      - tool: query
                    with:
                      method: example.sql.v1.SQLService/Query
                  - with:
                      option:
                        acme.audience: readonly
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectProxyKind()
    {
        String text =
            """
            name: test
            bindings:
              mcpgrpc0:
                type: mcp-grpc
                kind: proxy
                exit: grpc0
                options:
                  server: http://localhost:8080
                  catalog:
                    catalog0:
                      subject: sql
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectCatalogOverlay()
    {
        String text =
            """
            name: test
            bindings:
              mcpgrpc0:
                type: mcp-grpc
                kind: client
                options:
                  server: http://localhost:8080
                  catalog:
                    catalog0:
                      subject: sql
                      overlay:
                        overlay0:
                          subject: sql-overlay
            """;

        reader.read(text);
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectReservedAuthorizationHeader()
    {
        String text =
            """
            name: test
            bindings:
              mcpgrpc0:
                type: mcp-grpc
                kind: client
                options:
                  server: http://localhost:8080
                  catalog:
                    catalog0:
                      subject: sql
                  authorization:
                    guard0:
                      credentials:
                        headers:
                          grpc-timeout: 1S
            """;

        reader.read(text);
    }
}
