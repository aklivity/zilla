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
package io.aklivity.zilla.config.binding.mcp.openapi.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.config.engine.EngineConfig;
import io.aklivity.zilla.config.engine.EngineConfigReader;
import io.aklivity.zilla.config.engine.EngineInfo;

public class McpOpenapiSchemaValidationTest
{
    private final EngineConfigReader reader = new EngineConfigReader(
        text -> text, new EngineInfo(), McpOpenapiSchemaValidationTest::noop, McpOpenapiSchemaValidationTest::noop);

    private static void noop(
        String value)
    {
    }

    @Test
    public void shouldAcceptOverlayNestedInCatalog()
    {
        String text =
            """
            name: test
            bindings:
              mcpopenapi0:
                type: mcp-openapi
                kind: client
                options:
                  specs:
                    openapi_github0:
                      server: https://api.github.com
                      catalog:
                        catalog0:
                          subject: rest-api
                          version: latest
                          overlay:
                            overlay0:
                              subject: rest-api-overlay
                              version: latest
                routes:
                  - when:
                      - tool: create_pr
                    with:
                      spec: openapi_github0
                      operation: createPullRequest
            """;

        EngineConfig engine = reader.read(text);

        assertThat(engine, not(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectDeprecatedOverlayAlongsideNestedOverlay()
    {
        String text =
            """
            name: test
            bindings:
              mcpopenapi0:
                type: mcp-openapi
                kind: client
                options:
                  specs:
                    openapi_github0:
                      server: https://api.github.com
                      catalog:
                        catalog0:
                          subject: rest-api
                          version: latest
                          overlay:
                            overlay0:
                              subject: rest-api-overlay
                              version: latest
                      overlay:
                        overlay1:
                          subject: rest-api-overlay2
                          version: latest
                routes:
                  - when:
                      - tool: create_pr
                    with:
                      spec: openapi_github0
                      operation: createPullRequest
            """;

        reader.read(text);
    }
}
