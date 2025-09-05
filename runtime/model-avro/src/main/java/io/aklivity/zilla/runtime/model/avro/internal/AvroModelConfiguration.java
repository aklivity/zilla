/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.avro.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class AvroModelConfiguration extends Configuration
{
    private static final ConfigurationDef AVRO_MODEL_CONFIG;
    private static final int DEFAULT_PADDING_MAX_ITEMS = 100;

    static final IntPropertyDef PADDING_MAX_ITEMS;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.model.avro");

        PADDING_MAX_ITEMS = config.property("padding.max.items", DEFAULT_PADDING_MAX_ITEMS);

        AVRO_MODEL_CONFIG = config;
    }

    public AvroModelConfiguration(
        Configuration config)
    {
        super(AVRO_MODEL_CONFIG, config);
    }

    public int paddingMaxItems()
    {
        return PADDING_MAX_ITEMS.getAsInt(this);
    }
}
