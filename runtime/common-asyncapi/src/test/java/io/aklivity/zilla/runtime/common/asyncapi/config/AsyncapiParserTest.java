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
package io.aklivity.zilla.runtime.common.asyncapi.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import jakarta.json.bind.annotation.JsonbProperty;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.asyncapi.model.Asyncapi;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiOperationView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiSchemaItemView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiSecuritySchemeView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiView;

public class AsyncapiParserTest
{
    private static final String SPEC =
        """
        asyncapi: 3.0.0
        info:
          title: Test API
          version: 0.1.0
        servers:
          local:
            host: localhost:9092
            protocol: kafka
            bindings:
              x-zilla-sample:
                key: server-value
        operations:
          doSend:
            action: send
            channel:
              $ref: '#/channels/output'
            bindings:
              x-zilla-sample:
                key: operation-value
              x-zilla-unregistered:
                key: unregistered-value
        channels:
          output:
            messages:
              note:
                $ref: '#/components/messages/note'
        components:
          messages:
            note:
              payload:
                $ref: '#/components/schemas/note.payload'
              bindings:
                x-zilla-sample:
                  key: message-value
          schemas:
            note.payload:
              schema:
                type: string
        """;

    private static final String EXT_SPEC =
        """
        asyncapi: 3.0.0
        info:
          title: Test API
          version: 0.1.0
        x-zilla-sample:
          key: root-value
        x-zilla-unregistered:
          key: root-unregistered-value
        servers:
          local:
            host: localhost:9092
            protocol: kafka
            x-zilla-sample:
              key: server-value
        operations:
          doSend:
            action: send
            channel:
              $ref: '#/channels/output'
            x-zilla-sample:
              key: operation-value
        channels:
          output:
            messages:
              note:
                $ref: '#/components/messages/note'
        components:
          messages:
            note:
              payload:
                $ref: '#/components/schemas/note.payload'
              x-zilla-sample:
                key: message-value
          schemas:
            note.payload:
              schema:
                type: string
              x-zilla-sample:
                key: schema-value
          securitySchemes:
            sampleAuth:
              type: scramSha256
              x-zilla-sample:
                key: scheme-value
        """;

    public static final class SampleBinding
    {
        public String key;
    }

    public static final class SampleExtension
    {
        public String key;
    }

    public static final class CountExtension
    {
        public String key;
    }

    public static final class GoogleJwtExtension
    {
        public String issuer;
        @JsonbProperty("jwks_uri")
        public String jwksUri;
        public String audiences;
    }

    @Test
    public void shouldExposeOperationBindingGenerically()
    {
        AsyncapiParser parser = new AsyncapiParserFactory()
            .withOperationBinding("x-zilla-sample", SampleBinding.class)
            .createParser();

        Asyncapi model = parser.parse(SPEC);
        AsyncapiView view = AsyncapiView.of(model);
        AsyncapiOperationView operation = view.operations.get("doSend");

        assertTrue(operation.hasBinding("x-zilla-sample"));
        Optional<SampleBinding> binding = operation.binding("x-zilla-sample", SampleBinding.class);
        assertTrue(binding.isPresent());
        assertEquals("operation-value", binding.get().key);

        assertFalse(operation.hasBinding("x-zilla-unregistered"));
        assertFalse(operation.binding("x-zilla-unregistered", SampleBinding.class).isPresent());
    }

    @Test
    public void shouldExposeMessageBindingGenerically()
    {
        AsyncapiParser parser = new AsyncapiParserFactory()
            .withMessageBinding("x-zilla-sample", SampleBinding.class)
            .createParser();

        Asyncapi model = parser.parse(SPEC);
        AsyncapiView view = AsyncapiView.of(model);
        AsyncapiOperationView operation = view.operations.get("doSend");
        AsyncapiMessageView message = operation.messages.get(0);

        assertTrue(message.hasBinding("x-zilla-sample"));
        assertEquals("message-value", message.binding("x-zilla-sample", SampleBinding.class).get().key);
    }

    @Test
    public void shouldExposeServerBindingGenerically()
    {
        AsyncapiParser parser = new AsyncapiParserFactory()
            .withServerBinding("x-zilla-sample", SampleBinding.class)
            .createParser();

        Asyncapi model = parser.parse(SPEC);
        AsyncapiView view = AsyncapiView.of(model);
        AsyncapiServerView server = view.servers.get(0);

        assertTrue(server.hasBinding("x-zilla-sample"));
        assertEquals("server-value", server.binding("x-zilla-sample", SampleBinding.class).get().key);
    }

    @Test
    public void shouldNotExposeUnregisteredBindingType()
    {
        AsyncapiParser parser = new AsyncapiParserFactory().createParser();

        Asyncapi model = parser.parse(SPEC);
        AsyncapiView view = AsyncapiView.of(model);
        AsyncapiOperationView operation = view.operations.get("doSend");

        assertFalse(operation.hasBinding("x-zilla-sample"));
        assertEquals(Optional.empty(), operation.binding("x-zilla-sample", SampleBinding.class));
    }

    @Test
    public void shouldExposeSpecificationExtensionsGenerically()
    {
        AsyncapiParser parser = new AsyncapiParserFactory()
            .withExtension(AsyncapiExtension.of(AsyncapiExtension.Scope.ASYNCAPI, "x-zilla-sample", SampleExtension.class))
            .withExtension(AsyncapiExtension.of(AsyncapiExtension.Scope.OPERATION, "x-zilla-sample", SampleExtension.class))
            .createParser();

        Asyncapi model = parser.parse(EXT_SPEC);
        AsyncapiView view = AsyncapiView.of(model);
        AsyncapiOperationView operation = view.operations.get("doSend");

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
    public void shouldExposeMessageExtensionGenerically()
    {
        AsyncapiParser parser = new AsyncapiParserFactory()
            .withExtension(AsyncapiExtension.of(AsyncapiExtension.Scope.MESSAGE, "x-zilla-sample", SampleExtension.class))
            .createParser();

        Asyncapi model = parser.parse(EXT_SPEC);
        AsyncapiView view = AsyncapiView.of(model);
        AsyncapiOperationView operation = view.operations.get("doSend");
        AsyncapiMessageView message = operation.messages.get(0);

        assertTrue(message.hasExtension("x-zilla-sample"));
        assertEquals("message-value", message.extension("x-zilla-sample", SampleExtension.class).get().key);
    }

    @Test
    public void shouldExposeServerExtensionGenerically()
    {
        AsyncapiParser parser = new AsyncapiParserFactory()
            .withExtension(AsyncapiExtension.of(AsyncapiExtension.Scope.SERVER, "x-zilla-sample", SampleExtension.class))
            .createParser();

        Asyncapi model = parser.parse(EXT_SPEC);
        AsyncapiView view = AsyncapiView.of(model);
        AsyncapiServerView server = view.servers.get(0);

        assertTrue(server.hasExtension("x-zilla-sample"));
        assertEquals("server-value", server.extension("x-zilla-sample", SampleExtension.class).get().key);
    }

    @Test
    public void shouldExposeSchemaExtensionGenerically()
    {
        AsyncapiParser parser = new AsyncapiParserFactory()
            .withExtension(AsyncapiExtension.of(AsyncapiExtension.Scope.SCHEMA, "x-zilla-sample", SampleExtension.class))
            .createParser();

        Asyncapi model = parser.parse(EXT_SPEC);
        AsyncapiView view = AsyncapiView.of(model);
        AsyncapiSchemaItemView schema = view.components.schemas.get("note.payload");

        assertTrue(schema.hasExtension("x-zilla-sample"));
        assertEquals("schema-value", schema.extension("x-zilla-sample", SampleExtension.class).get().key);
    }

    @Test
    public void shouldExposeSecuritySchemeExtensionGenerically()
    {
        AsyncapiParser parser = new AsyncapiParserFactory()
            .withExtension(AsyncapiExtension.of(AsyncapiExtension.Scope.SECURITY_SCHEME, "x-zilla-sample", SampleExtension.class))
            .createParser();

        Asyncapi model = parser.parse(EXT_SPEC);
        AsyncapiView view = AsyncapiView.of(model);
        AsyncapiSecuritySchemeView scheme = view.components.securitySchemes.get("sampleAuth");

        assertTrue(scheme.hasExtension("x-zilla-sample"));
        assertEquals("scheme-value", scheme.extension("x-zilla-sample", SampleExtension.class).get().key);
    }

    @Test
    public void shouldNotExposeUnregisteredExtensionType()
    {
        AsyncapiParser parser = new AsyncapiParserFactory()
            .withExtension(AsyncapiExtension.of(AsyncapiExtension.Scope.ASYNCAPI, "x-zilla-sample", SampleExtension.class))
            .createParser();

        Asyncapi model = parser.parse(EXT_SPEC);
        AsyncapiView view = AsyncapiView.of(model);

        assertFalse(view.hasExtension("x-zilla-unregistered"));
        assertEquals(Optional.empty(), view.extension("x-zilla-unregistered", SampleExtension.class));
    }

    @Test
    public void shouldExposePrefixWildcardExtensionGenerically()
    {
        String spec =
            """
            asyncapi: 3.0.0
            info:
              title: Test API
              version: 0.1.0
            components:
              securitySchemes:
                googleJwt:
                  type: httpApiKey
                  name: Authorization
                  in: header
                  x-google-issuer: https://accounts.google.com
                  x-google-jwks_uri: https://www.googleapis.com/oauth2/v1/certs
                  x-google-audiences: 848149964201.apps.googleusercontent.com
            """;

        AsyncapiParser parser = new AsyncapiParserFactory()
            .withExtension(AsyncapiExtension.of(AsyncapiExtension.Scope.SECURITY_SCHEME, "x-google-*", GoogleJwtExtension.class))
            .createParser();

        Asyncapi model = parser.parse(spec);
        AsyncapiView view = AsyncapiView.of(model);
        AsyncapiSecuritySchemeView scheme = view.components.securitySchemes.get("googleJwt");

        assertTrue(scheme.hasExtension("x-google-*"));
        Optional<GoogleJwtExtension> extension = scheme.extension("x-google-*", GoogleJwtExtension.class);
        assertTrue(extension.isPresent());
        assertEquals("https://accounts.google.com", extension.get().issuer);
        assertEquals("https://www.googleapis.com/oauth2/v1/certs", extension.get().jwksUri);
        assertEquals("848149964201.apps.googleusercontent.com", extension.get().audiences);
    }

    @Test
    public void shouldNotExposePrefixWildcardExtensionWhenAbsent()
    {
        AsyncapiParser parser = new AsyncapiParserFactory()
            .withExtension(AsyncapiExtension.of(AsyncapiExtension.Scope.SECURITY_SCHEME, "x-google-*", GoogleJwtExtension.class))
            .createParser();

        Asyncapi model = parser.parse(EXT_SPEC);
        AsyncapiView view = AsyncapiView.of(model);
        AsyncapiSecuritySchemeView scheme = view.components.securitySchemes.get("sampleAuth");

        assertFalse(scheme.hasExtension("x-google-*"));
        assertEquals(Optional.empty(), scheme.extension("x-google-*", GoogleJwtExtension.class));
    }

    @Test
    public void shouldReuseExtensionNameWithDifferentTypesAcrossScopes()
    {
        AsyncapiParser parser = new AsyncapiParserFactory()
            .withExtension(AsyncapiExtension.of(AsyncapiExtension.Scope.OPERATION, "x-zilla-sample", SampleExtension.class))
            .withExtension(AsyncapiExtension.of(AsyncapiExtension.Scope.MESSAGE, "x-zilla-sample", CountExtension.class))
            .createParser();

        Asyncapi model = parser.parse(EXT_SPEC);
        AsyncapiView view = AsyncapiView.of(model);
        AsyncapiOperationView operation = view.operations.get("doSend");
        AsyncapiMessageView message = operation.messages.get(0);

        assertTrue(operation.hasExtension("x-zilla-sample"));
        assertEquals("operation-value", operation.extension("x-zilla-sample", SampleExtension.class).get().key);

        assertTrue(message.hasExtension("x-zilla-sample"));
        assertEquals("message-value", message.extension("x-zilla-sample", CountExtension.class).get().key);

        assertFalse(view.hasExtension("x-zilla-sample"));
    }
}
