/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.openapi.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.openapi.model.Openapi;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiResponseView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiSchemaView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiSecuritySchemeView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiServerView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiView;

public class OpenapiParserTest
{
    private static final String SPEC =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "sample", "version": "1.0.0" },
          "x-zilla-sample": { "key": "root-value" },
          "x-zilla-unregistered": { "key": "unregistered-value" },
          "servers": [
            { "url": "https://example.com", "x-zilla-sample": { "key": "server-value" } }
          ],
          "paths": {
            "/items": {
              "get": {
                "operationId": "listItems",
                "x-zilla-sample": { "key": "operation-value" },
                "responses": {
                  "200": {
                    "description": "ok",
                    "x-zilla-sample": { "key": "response-value" }
                  }
                }
              }
            }
          },
          "components": {
            "schemas": {
              "Item": {
                "type": "object",
                "x-zilla-sample": { "key": "schema-value" }
              }
            },
            "securitySchemes": {
              "bearerAuth": {
                "type": "http",
                "scheme": "bearer",
                "x-zilla-sample": { "key": "scheme-value" }
              }
            }
          }
        }
        """;

    public static final class SampleExtension
    {
        public String key;
    }

    @Test
    public void shouldExposeSpecificationExtensionsGenerically()
    {
        OpenapiParser parser = new OpenapiParserFactory()
            .withExtension("x-zilla-sample", SampleExtension.class)
            .createParser();

        Openapi model = parser.parse(SPEC);
        OpenapiView view = OpenapiView.of(model);
        OpenapiOperationView operation = view.operations.get("listItems");

        assertTrue(view.hasExtension("x-zilla-sample"));
        Optional<SampleExtension> rootExtension = view.extension("x-zilla-sample", SampleExtension.class);
        assertTrue(rootExtension.isPresent());
        assertEquals("root-value", rootExtension.get().key);

        assertTrue(operation.hasExtension("x-zilla-sample"));
        Optional<SampleExtension> operationExtension = operation.extension("x-zilla-sample", SampleExtension.class);
        assertTrue(operationExtension.isPresent());
        assertEquals("operation-value", operationExtension.get().key);

        assertFalse(operation.hasExtension("x-zilla-unknown"));
        assertFalse(operation.extension("x-zilla-unknown", SampleExtension.class).isPresent());
    }

    @Test
    public void shouldExposeSecuritySchemeExtensionGenerically()
    {
        OpenapiParser parser = new OpenapiParserFactory()
            .withExtension("x-zilla-sample", SampleExtension.class)
            .createParser();

        Openapi model = parser.parse(SPEC);
        OpenapiView view = OpenapiView.of(model);
        OpenapiSecuritySchemeView scheme = view.components.securitySchemes.get("bearerAuth");

        assertTrue(scheme.hasExtension("x-zilla-sample"));
        Optional<SampleExtension> extension = scheme.extension("x-zilla-sample", SampleExtension.class);
        assertTrue(extension.isPresent());
        assertEquals("scheme-value", extension.get().key);
    }

    @Test
    public void shouldBindRegisteredExtensionTypeEagerly()
    {
        OpenapiParser parser = new OpenapiParserFactory()
            .withExtension("x-zilla-sample", SampleExtension.class)
            .createParser();

        Openapi model = parser.parse(SPEC);
        OpenapiView view = OpenapiView.of(model);
        OpenapiOperationView operation = view.operations.get("listItems");
        OpenapiSecuritySchemeView scheme = view.components.securitySchemes.get("bearerAuth");

        assertEquals("root-value", view.extension("x-zilla-sample", SampleExtension.class).get().key);
        assertEquals("operation-value", operation.extension("x-zilla-sample", SampleExtension.class).get().key);
        assertEquals("scheme-value", scheme.extension("x-zilla-sample", SampleExtension.class).get().key);
    }

    @Test
    public void shouldExposeServerExtensionGenerically()
    {
        OpenapiParser parser = new OpenapiParserFactory()
            .withExtension("x-zilla-sample", SampleExtension.class)
            .createParser();

        Openapi model = parser.parse(SPEC);
        OpenapiView view = OpenapiView.of(model);
        OpenapiServerView server = view.servers.get(0);

        assertTrue(server.hasExtension("x-zilla-sample"));
        assertEquals("server-value", server.extension("x-zilla-sample", SampleExtension.class).get().key);
    }

    @Test
    public void shouldExposeResponseExtensionGenerically()
    {
        OpenapiParser parser = new OpenapiParserFactory()
            .withExtension("x-zilla-sample", SampleExtension.class)
            .createParser();

        Openapi model = parser.parse(SPEC);
        OpenapiView view = OpenapiView.of(model);
        OpenapiOperationView operation = view.operations.get("listItems");
        OpenapiResponseView response = operation.responses.get("200");

        assertTrue(response.hasExtension("x-zilla-sample"));
        assertEquals("response-value", response.extension("x-zilla-sample", SampleExtension.class).get().key);
    }

    @Test
    public void shouldExposeSchemaExtensionGenerically()
    {
        OpenapiParser parser = new OpenapiParserFactory()
            .withExtension("x-zilla-sample", SampleExtension.class)
            .createParser();

        Openapi model = parser.parse(SPEC);
        OpenapiView view = OpenapiView.of(model);
        OpenapiSchemaView schema = view.components.schemas.get("Item");

        assertTrue(schema.hasExtension("x-zilla-sample"));
        assertEquals("schema-value", schema.extension("x-zilla-sample", SampleExtension.class).get().key);
    }

    @Test
    public void shouldResolveOAuthFlowsAndScopesFromSecurityScheme()
    {
        String spec =
            """
            {
              "openapi": "3.1.0",
              "info": { "title": "sample", "version": "1.0.0" },
              "paths": {},
              "components": {
                "securitySchemes": {
                  "oauth2Auth": {
                    "type": "oauth2",
                    "flows": {
                      "authorizationCode": {
                        "authorizationUrl": "https://example.com/authorize",
                        "tokenUrl": "https://example.com/token",
                        "refreshUrl": "https://example.com/refresh",
                        "scopes": {
                          "read": "Read access",
                          "write": "Write access"
                        },
                        "x-zilla-sample": { "key": "flow-value" }
                      }
                    }
                  }
                }
              }
            }
            """;

        OpenapiParser parser = new OpenapiParserFactory()
            .withExtension("x-zilla-sample", SampleExtension.class)
            .createParser();

        Openapi model = parser.parse(spec);
        OpenapiView view = OpenapiView.of(model);
        OpenapiSecuritySchemeView scheme = view.components.securitySchemes.get("oauth2Auth");

        assertEquals("https://example.com/authorize", scheme.flows.authorizationCode.authorizationUrl);
        assertEquals("https://example.com/token", scheme.flows.authorizationCode.tokenUrl);
        assertEquals("https://example.com/refresh", scheme.flows.authorizationCode.refreshUrl);
        assertEquals(Map.of("read", "Read access", "write", "Write access"), scheme.flows.authorizationCode.scopes);
        assertEquals(List.of("read", "write"), scheme.scopes);

        assertTrue(scheme.flows.authorizationCode.hasExtension("x-zilla-sample"));
        assertEquals("flow-value",
            scheme.flows.authorizationCode.extension("x-zilla-sample", SampleExtension.class).get().key);
    }

    @Test
    public void shouldNotExposeUnregisteredExtensionType()
    {
        OpenapiParser parser = new OpenapiParserFactory()
            .withExtension("x-zilla-sample", SampleExtension.class)
            .createParser();

        Openapi model = parser.parse(SPEC);
        OpenapiView view = OpenapiView.of(model);

        assertFalse(view.hasExtension("x-zilla-unregistered"));
        assertEquals(Optional.empty(), view.extension("x-zilla-unregistered", SampleExtension.class));
    }
}
