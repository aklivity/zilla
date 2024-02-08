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
package io.aklivity.zilla.runtime.binding.kafka.internal.identity;

import io.aklivity.zilla.runtime.binding.kafka.identity.KafkaClientIdSupplierSpi;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

final class KafkaConfluentClientIdSupplier implements KafkaClientIdSupplierSpi
{
    private final String clientId;

    KafkaConfluentClientIdSupplier(
        Configuration config)
    {
        EngineConfiguration engine = new EngineConfiguration(config);
        clientId = String.format("cwc|0014U00003IYePAQA1|%s", engine.name());
    }

    public boolean matches(
        String server)
    {
        return
            server != null &&
            server.endsWith(".confluent.cloud");
    }

    public String get()
    {
        return clientId;
    }
}
