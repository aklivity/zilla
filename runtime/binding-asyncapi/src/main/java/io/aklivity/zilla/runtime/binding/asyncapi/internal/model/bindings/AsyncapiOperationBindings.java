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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings;

import jakarta.json.bind.annotation.JsonbProperty;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.http.AsyncapiHttpOperationBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.http.kafka.AsyncapiHttpKafkaOperationBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.sse.AsyncapiSseOperationBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.sse.kafka.AsyncapiSseKafkaOperationBinding;

public class AsyncapiOperationBindings
{
    public AsyncapiHttpOperationBinding http;

    @JsonbProperty("x-zilla-http-kafka")
    public AsyncapiHttpKafkaOperationBinding httpKafka;

    @JsonbProperty("x-zilla-sse")
    public AsyncapiSseOperationBinding sse;

    @JsonbProperty("x-zilla-sse-kafka")
    public AsyncapiSseKafkaOperationBinding sseKafka;
}
