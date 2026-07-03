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

import java.util.List;
import java.util.Optional;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.asyncapi.model.Asyncapi;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiOperationView;
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

    public static final class SampleBinding
    {
        public String key;
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
        AsyncapiView view = AsyncapiView.of(model, List.of(AsyncapiServerConfig.builder().build()));
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
}
