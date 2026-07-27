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
package io.aklivity.zilla.config.guard.jwt;

import java.io.StringReader;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import io.aklivity.zilla.config.guard.jwt.internal.JwtKeySetConfigAdapter;

public final class JwtKeySetConfigReader
{
    private final Jsonb jsonb;

    public JwtKeySetConfigReader()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new JwtKeySetConfigAdapter());
        this.jsonb = JsonbBuilder.newBuilder()
            .withConfig(config)
            .build();
    }

    public JwtKeySetConfig read(
        String text)
    {
        return jsonb.fromJson(new StringReader(text), JwtKeySetConfig.class);
    }
}
