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
import static io.aklivity.zilla.runtime.common.feature.FeatureFilter.filter;
import static java.util.ServiceLoader.load;

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaServerConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.engine.Configuration;

public final class KafkaClientIdSupplier
{
    public static KafkaClientIdSupplier instantiate(
        Configuration config)
    {
        return instantiate(config, filter(load(KafkaClientIdSupplierFactorySpi.class)));
    }

    private final List<KafkaClientIdSupplierSpi> suppliers;

    public String get(
        KafkaServerConfig server)
    {
        String clientId = null;

        match:
        for (int index = 0; index < suppliers.size(); index++)
        {
            KafkaClientIdSupplierSpi supplier = suppliers.get(index);
            if (supplier.matches(server))
            {
                clientId = supplier.get();
                break match;
            }
        }

        return clientId;
    }

    private KafkaClientIdSupplier(
        List<KafkaClientIdSupplierSpi> suppliers)
    {
        this.suppliers = suppliers;
    }

    private static KafkaClientIdSupplier instantiate(
        Configuration config,
        Iterable<KafkaClientIdSupplierFactorySpi> factories)
    {
        List<KafkaClientIdSupplierSpi> suppliers = new ArrayList<>();

        KafkaConfiguration kafka = new KafkaConfiguration(config);
        String clientId = kafka.clientId();

        if (clientId != null)
        {
            suppliers.add(new Fixed(clientId));
        }

        for (KafkaClientIdSupplierFactorySpi factory : factories)
        {
            suppliers.add(factory.create(config));
        }

        if (clientId == null)
        {
            suppliers.add(new Fixed(KAFKA_CLIENT_ID_DEFAULT));
        }

        return new KafkaClientIdSupplier(suppliers);
    }

    private static final class Fixed implements KafkaClientIdSupplierSpi
    {
        private final String clientId;

        private Fixed(
            String clientId)
        {
            this.clientId = clientId;
        }

        @Override
        public boolean matches(
            KafkaServerConfig server)
        {
            return true;
        }

        @Override
        public String get()
        {
            return clientId;
        }
    }
}
