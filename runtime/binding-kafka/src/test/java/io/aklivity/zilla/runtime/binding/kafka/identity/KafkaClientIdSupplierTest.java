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
package io.aklivity.zilla.runtime.binding.kafka.identity;

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Properties;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class KafkaClientIdSupplierTest
{
    @Test
    public void shouldNotSupplyClientIdWhenNotConfigured() throws Exception
    {
        Configuration config = new Configuration();
        KafkaClientIdSupplier supplier = KafkaClientIdSupplier.instantiate(config);

        String clientId = supplier.get("localhost");

        assertNull(clientId);
    }

    @Test
    public void shouldSupplyClientIdWhenConfigured() throws Exception
    {
        Properties properties = new Properties();
        properties.setProperty(KAFKA_CLIENT_ID.name(), "custom client id");
        Configuration config = new Configuration(properties);
        KafkaClientIdSupplier supplier = KafkaClientIdSupplier.instantiate(config);

        String clientId = supplier.get("localhost");

        assertEquals("custom client id", clientId);
    }

    @Test
    public void shouldSupplyClientIdWhenConfluentServer() throws Exception
    {
        Configuration config = new Configuration();
        KafkaClientIdSupplier supplier = KafkaClientIdSupplier.instantiate(config);
        String server = "broker.confluent.cloud";

        String clientId = supplier.get(server);

        assertNotNull(clientId);
    }
}
