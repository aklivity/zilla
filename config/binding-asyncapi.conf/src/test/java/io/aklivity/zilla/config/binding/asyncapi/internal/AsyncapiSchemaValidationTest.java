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
package io.aklivity.zilla.config.binding.asyncapi.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.config.engine.EngineConfig;
import io.aklivity.zilla.config.engine.EngineConfigReader;
import io.aklivity.zilla.config.engine.EngineInfo;

public class AsyncapiSchemaValidationTest
{
    private final EngineConfigReader reader = new EngineConfigReader(
        text -> text, new EngineInfo(), AsyncapiSchemaValidationTest::noop, AsyncapiSchemaValidationTest::noop);

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
              asyncapi0:
                type: asyncapi
                kind: client
                options:
                  specs:
                    mqtt_api:
                      servers:
                        - mqtt://localhost:1883
                      catalog:
                        catalog0:
                          subject: smartylighting
                          version: latest
                          overlay:
                            catalog1:
                              subject: smartylighting-overlay
                              version: latest
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
              asyncapi0:
                type: asyncapi
                kind: client
                options:
                  specs:
                    mqtt_api:
                      servers:
                        - mqtt://localhost:1883
                      catalog:
                        catalog0:
                          subject: smartylighting
                          version: latest
                          overlay:
                            catalog1:
                              subject: smartylighting-overlay
                              version: latest
                      overlay:
                        catalog2:
                          subject: smartylighting-overlay2
                          version: latest
            """;

        reader.read(text);
    }
}
