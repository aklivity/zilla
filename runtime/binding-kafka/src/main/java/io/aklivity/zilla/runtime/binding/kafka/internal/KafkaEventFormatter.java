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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.StringFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaApiVersionRejectedExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaAuthorizationFailedExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventExFW;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public final class KafkaEventFormatter implements EventFormatterSpi
{
    private final EventFW eventRO = new EventFW();
    private final KafkaEventExFW kafkaEventExRO = new KafkaEventExFW();

    KafkaEventFormatter(
        Configuration config)
    {
    }

    public String format(
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
            KafkaAuthorizationFailedExFW ex = extension.authorizationFailed();
            result = String.format("%s", identity(ex.identity()));
            break;
        }
        case API_VERSION_REJECTED:
        {
            final KafkaApiVersionRejectedExFW ex = extension.apiVersionRejected();
            result = String.format("%d %d", ex.apiKey(), ex.apiVersion());
        }
        }
        return result;
    }

    private static String identity(
        StringFW identity)
    {
        int length = identity.length();
        return length <= 0 ? "-" : identity.asString();
    }
}
