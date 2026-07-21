/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class ProtobufModelConfiguration extends Configuration
{
    private static final ConfigurationDef PROTOBUF_MODEL_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.model.protobuf");
        PROTOBUF_MODEL_CONFIG = config;
    }

    public ProtobufModelConfiguration()
    {
        super(PROTOBUF_MODEL_CONFIG, new Configuration());
    }

    public ProtobufModelConfiguration(
        Configuration config)
    {
        super(PROTOBUF_MODEL_CONFIG, config);
    }
}
