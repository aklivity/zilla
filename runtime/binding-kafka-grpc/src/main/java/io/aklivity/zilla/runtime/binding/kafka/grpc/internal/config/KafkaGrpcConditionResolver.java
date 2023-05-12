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

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String16FW;

public final class KafkaGrpcConditionResolver
{
    private static final String16FW WILDCARD = new String16FW("*");

    private final KafkaGrpcOptionsConfig options;
    private final KafkaGrpcConditionConfig condition;
    private final KafkaGrpcWithConfig with;


    public KafkaGrpcConditionResolver(
        KafkaGrpcOptionsConfig options,
        KafkaGrpcConditionConfig condition,
        KafkaGrpcWithConfig with)
    {
        this.options = options;
        this.condition = condition;
        this.with = with;
    }

    public KafkaGrpcConditionResult resolve()
    {
        String16FW topic = condition.topic;
        KafkaAckMode acks = options.acks;

        List<KafkaGrpcFetchFilterResult> filters = null;
        DirectBuffer key = null;
        if (condition.key.isPresent())
        {
            key = condition.key.get().value();
        }

        final List<KafkaGrpcFetchFilterHeaderResult> headers = new ArrayList<>();
        if (condition.headers.isPresent())
        {
            condition.headers.get().forEach((k, v) ->
            {
                DirectBuffer name = k.value();
                DirectBuffer value = v.value();

                headers.add(new KafkaGrpcFetchFilterHeaderResult(name, value));
            });
        }

        if (condition.service.isPresent())
        {
            String16FW service = condition.service.get();
            headers.add(new KafkaGrpcFetchFilterHeaderResult(options.correlation.service.value(),
                service.value()));
        }

        if (condition.method.isPresent() && WILDCARD.value().compareTo(condition.method.get().value()) != 0)
        {
            String16FW method = condition.method.get();
            headers.add(new KafkaGrpcFetchFilterHeaderResult(options.correlation.method.value(),
                method.value()));
        }

        if (condition.replyTo.isPresent())
        {
            headers.add(new KafkaGrpcFetchFilterHeaderResult(options.correlation.replyTo.value(),
                condition.replyTo.get().value()));
        }

        if (key != null || !headers.isEmpty())
        {
            filters = new ArrayList<>();
            filters.add(new KafkaGrpcFetchFilterResult(key, headers));
        }

        return new KafkaGrpcConditionResult(with.scheme, with.authority, topic, acks,
            filters, options.correlation);
    }
}
