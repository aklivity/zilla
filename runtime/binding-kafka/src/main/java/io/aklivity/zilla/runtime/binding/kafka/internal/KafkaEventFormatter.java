/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaApiVersionRejectedExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventExFW;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public class KafkaEventFormatter implements EventFormatterSpi
{
    private static final String AUTHORIZATION_FAILED_FORMAT = "AUTHORIZATION_FAILED";
    private static final String API_VERSION_REJECTED_FORMAT = "API_VERSION_REJECTED %d %d";

    private final EventFW eventRO = new EventFW();
    private final KafkaEventExFW kafkaEventExRO = new KafkaEventExFW();

    @Override
    public String type()
    {
        return KafkaBinding.NAME;
    }

    public String formatEventEx(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final KafkaEventExFW extension = kafkaEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        String result = null;
        switch (extension.kind())
        {
        case AUTHORIZATION_FAILED:
        {
            result = AUTHORIZATION_FAILED_FORMAT;
            break;
        }
        case API_VERSION_REJECTED:
        {
            final KafkaApiVersionRejectedExFW ex = extension.apiVersionRejected();
            result = String.format(API_VERSION_REJECTED_FORMAT, ex.apiKey(), ex.apiVersion());
        }
        }
        return result;
    }
}
