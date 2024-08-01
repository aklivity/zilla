/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.view;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.parser.AsyncapiParser;

public class AsyncapiViewTest
{
    @Test
    public void shouldCreateEmpty() throws Exception
    {
        Asyncapi model = new AsyncapiParser().parse("""
            asyncapi: 3.0.0
            info:
              title: Test API
              version: 0.1.0
            """);
        AsyncapiView view = AsyncapiView.of(model);

        assertThat(view, is(not(nullValue())));
    }

    @Test
    public void shouldCreate() throws Exception
    {
        Asyncapi model = new AsyncapiParser().parse("""
            asyncapi: 3.0.0
            info:
              title: Test API
              version: 0.1.0
            operations:
              doSend:
                action: send
                channel:
                  $ref: '#/channels/output'
                reply:
                  address:
                    location: '$message.header#reply-to'
                  channel:
                    $ref: '#/channels/replies'
            channels:
              output:
                messages:
                  note:
                    $ref: '#/components/messages/note'
              replies:
                messages:
                  note:
                    $ref: '#/components/messages/note'
            components:
              messages:
                note:
                  payload:
                    $ref: '#/components/schemas/note.payload'
              schemas:
                note.payload:
                  schema:
                    type: string
            """);
        AsyncapiView view = AsyncapiView.of(model);

        assertThat(view, is(not(nullValue())));
    }

    @Test
    public void shouldCreateWithKafkaBindings() throws Exception
    {
        Asyncapi model = new AsyncapiParser().parse("""
            asyncapi: 3.0.0
            info:
              title: Test API
              version: 0.1.0
            operations:
              onEvents:
                action: receive
                channel:
                  $ref: '#/channels/events'
                bindings:
                  x-zilla-sse:
                    bindingVersion: latest
            channels:
              events:
                messages:
                  note:
                    $ref: '#/components/messages/event'
            components:
              messages:
                event:
                  payload:
                    $ref: '#/components/schemas/event.payload'
              schemas:
                event.payload:
                  schema:
                    type: string
            """);
        AsyncapiView view = AsyncapiView.of(model);

        assertThat(view, is(not(nullValue())));
    }

    @Test
    public void shouldCreateWithSseBindings() throws Exception
    {
        Asyncapi model = new AsyncapiParser().parse("""
            asyncapi: 3.0.0
            info:
              title: Test API
              version: 0.1.0
            servers:
              local:
                host: localhost:9092
                protocol: kafka
                bindings:
                  kafka:
                    schemaRegistryUrl: http://localhost:8081/
                    schemaRegistryVendor: karapace
            operations:
              doSend:
                action: send
                channel:
                  $ref: '#/channels/output'
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
                    kafka:
                      key:
                         schemaFormat: 'application/vnd.apache.avro;version=1.9.0'
                         schema:
                           type: string
              schemas:
                note.payload:
                  schema:
                    type: string
            """);
        AsyncapiView view = AsyncapiView.of(model);

        assertThat(view, is(not(nullValue())));
    }
}
