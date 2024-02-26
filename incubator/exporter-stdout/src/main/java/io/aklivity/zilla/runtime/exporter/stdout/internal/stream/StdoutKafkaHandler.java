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
package io.aklivity.zilla.runtime.exporter.stdout.internal.stream;

import java.io.PrintStream;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.exporter.stdout.internal.StdoutExporterContext;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.KafkaApiVersionRejectedFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.KafkaEventFW;

public class StdoutKafkaHandler extends EventHandler
{
    private static final String AUTHORIZATION_FAILED_FORMAT = "%s - [%s] AUTHORIZATION_FAILED%n";
    private static final String API_VERSION_REJECTED_FORMAT = "%s - [%s] API_VERSION_REJECTED %d %d%n";

    private final KafkaEventFW kafkaEventRO = new KafkaEventFW();

    public StdoutKafkaHandler(
        StdoutExporterContext context,
        PrintStream out)
    {
        super(context, out);
    }

    public void handleEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final KafkaEventFW event = kafkaEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case AUTHORIZATION_FAILED:
        {
            EventFW e = event.authorizationFailed();
            String qname = context.supplyQName(e.namespacedId());
            out.printf(AUTHORIZATION_FAILED_FORMAT, qname, asDateTime(e.timestamp()));
            break;
        }
        case API_VERSION_REJECTED:
        {
            KafkaApiVersionRejectedFW e = event.apiVersionRejected();
            String qname = context.supplyQName(e.namespacedId());
            out.printf(API_VERSION_REJECTED_FORMAT, qname, asDateTime(e.timestamp()), e.apiKey(), e.apiVersion());
            break;
        }
        }
    }
}
