/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.events;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.StringFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaApiVersionRejectedExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaAuthorizationFailedExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaBrokerConnectionFailedExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaClusterAuthorizationFailedExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaGroupAuthorizationFailedExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaOffsetCommitFailedExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaProduceErrorExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaSaslAuthenticationFailedExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaTopicAuthorizationFailedExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaTransactionalIdAuthorizationFailedExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaUnsupportedSaslMechanismExFW;
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
            result = String.format("Unable to authenticate client with identity (%s).", asString(ex.identity()));
            break;
        }
        case API_VERSION_REJECTED:
        {
            final KafkaApiVersionRejectedExFW ex = extension.apiVersionRejected();
            KafkaApiKey apiKey = KafkaApiKey.of(ex.apiKey());
            result = String.format("%s (Version: %d)", apiKey.title(), ex.apiVersion());
            break;
        }
        case CLUSTER_AUTHORIZATION_FAILED:
        {
            final KafkaClusterAuthorizationFailedExFW ex = extension.clusterAuthorizationFailed();
            KafkaApiKey apiKey = KafkaApiKey.of(ex.apiKey());
            result = String.format("%s (Version: %d)", apiKey.title(), ex.apiVersion());
            break;
        }
        case TOPIC_AUTHORIZATION_FAILED:
        {
            final KafkaTopicAuthorizationFailedExFW ex = extension.topicAuthorizationFailed();
            KafkaApiKey apiKey = KafkaApiKey.of(ex.apiKey());
            result = String.format("%s (Version: %d) Topic authorization failed for topic (%s).",
                apiKey.title(), ex.apiVersion(), asString(ex.topic()));
            break;
        }
        case SASL_AUTHENTICATION_FAILED:
        {
            final KafkaSaslAuthenticationFailedExFW ex = extension.saslAuthenticationFailed();
            result = String.format("SASL authentication failed for identity (%s): %s",
                asString(ex.identity()), asString(ex.error()));
            break;
        }
        case BROKER_CONNECTION_FAILED:
        {
            final KafkaBrokerConnectionFailedExFW ex = extension.brokerConnectionFailed();
            result = String.format("Broker connection failed for host (%s), port (%d).",
                asString(ex.host()), ex.port());
            break;
        }
        case PRODUCE_ERROR:
        {
            final KafkaProduceErrorExFW ex = extension.produceError();
            KafkaApiKey apiKey = KafkaApiKey.of(ex.apiKey());
            result = String.format("%s (Version: %d) Produce error (%d) for topic (%s).",
                apiKey.title(), ex.apiVersion(), ex.errorCode(), asString(ex.topic()));
            break;
        }
        case GROUP_AUTHORIZATION_FAILED:
        {
            final KafkaGroupAuthorizationFailedExFW ex = extension.groupAuthorizationFailed();
            KafkaApiKey apiKey = KafkaApiKey.of(ex.apiKey());
            result = String.format("%s (Version: %d) Group authorization failed.", apiKey.title(), ex.apiVersion());
            break;
        }
        case TRANSACTIONAL_ID_AUTHORIZATION_FAILED:
        {
            final KafkaTransactionalIdAuthorizationFailedExFW ex = extension.transactionalIdAuthorizationFailed();
            KafkaApiKey apiKey = KafkaApiKey.of(ex.apiKey());
            result = String.format("%s (Version: %d) Transactional id authorization failed.",
                apiKey.title(), ex.apiVersion());
            break;
        }
        case UNSUPPORTED_SASL_MECHANISM:
        {
            final KafkaUnsupportedSaslMechanismExFW ex = extension.unsupportedSaslMechanism();
            result = String.format("Unsupported SASL mechanism (%s).", asString(ex.mechanism()));
            break;
        }
        case OFFSET_COMMIT_FAILED:
        {
            final KafkaOffsetCommitFailedExFW ex = extension.offsetCommitFailed();
            KafkaApiKey apiKey = KafkaApiKey.of(ex.apiKey());
            result = String.format("%s (Version: %d) Offset commit failed with error (%d).",
                apiKey.title(), ex.apiVersion(), ex.errorCode());
            break;
        }
        }
        return result;
    }

    private static String asString(
            StringFW stringFW)
    {
        String s = stringFW.asString();
        return s == null ? "" : s;
    }
}
