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

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_ID_DEFAULT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class KafkaClientIdSupplierTest
{
    @Test
    public void shouldSupplyClientIdForNullServer() throws Exception
    {
        Configuration config = new Configuration();
        KafkaClientIdSupplier supplier = KafkaClientIdSupplier.instantiate(config);

        String clientId = supplier.get(null);

        assertEquals(clientId, KAFKA_CLIENT_ID_DEFAULT);
    }

    @Test
    public void shouldSupplyClientIdForConfluentServer() throws Exception
    {
        Configuration config = new Configuration();
        KafkaClientIdSupplier supplier = KafkaClientIdSupplier.instantiate(config);
        String server = "broker.confluent.cloud";

        String clientId = supplier.get(server);

        assertNotEquals(clientId, KAFKA_CLIENT_ID_DEFAULT);
    }
}
