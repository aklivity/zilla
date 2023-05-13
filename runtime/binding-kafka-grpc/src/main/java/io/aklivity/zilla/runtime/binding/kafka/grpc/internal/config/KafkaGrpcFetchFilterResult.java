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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config;

import java.util.List;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaFilterFW;

public final class KafkaGrpcFetchFilterResult
{
    public final DirectBuffer key;
    public final List<KafkaGrpcFetchFilterHeaderResult> headers;

    KafkaGrpcFetchFilterResult(
        DirectBuffer key,
        List<KafkaGrpcFetchFilterHeaderResult> headers)
    {
        this.key = key;
        this.headers = headers;
    }

    public void filter(
        KafkaFilterFW.Builder builder)
    {
        if (key != null)
        {
            builder.conditionsItem(c -> c.key(k -> k.length(key.capacity())
                                                    .value(key, 0, key.capacity())));
        }

        if (headers != null)
        {
            headers.forEach(h -> builder.conditionsItem(h::condition));
        }
    }
}
