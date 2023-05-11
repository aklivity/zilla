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

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_CLIENT_CLEANUP_DELAY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_RECONNECT_DELAY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_PRODUCE_MAX_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_SASL_SCRAM_NONCE;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KafkaConfigurationTest
{
    public static final String KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS_NAME =
            "zilla.binding.kafka.client.produce.max.request.millis";
    public static final String KAFKA_CLIENT_PRODUCE_MAX_BYTES_NAME = "zilla.binding.kafka.client.produce.max.bytes";
    public static final String KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME = "zilla.binding.kafka.cache.server.reconnect";
    public static final String KAFKA_CACHE_CLIENT_CLEANUP_DELAY_NAME = "zilla.binding.kafka.cache.client.cleanup.delay";
    public static final String KAFKA_CLIENT_SASL_SCRAM_NONCE_NAME = "zilla.binding.kafka.client.sasl.scram.nonce";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS.name(), KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS_NAME);
        assertEquals(KAFKA_CLIENT_PRODUCE_MAX_BYTES.name(), KAFKA_CLIENT_PRODUCE_MAX_BYTES_NAME);
        assertEquals(KAFKA_CACHE_SERVER_RECONNECT_DELAY.name(), KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME);
        assertEquals(KAFKA_CACHE_CLIENT_CLEANUP_DELAY.name(), KAFKA_CACHE_CLIENT_CLEANUP_DELAY_NAME);
        assertEquals(KAFKA_CLIENT_SASL_SCRAM_NONCE.name(), KAFKA_CLIENT_SASL_SCRAM_NONCE_NAME);
    }
}
