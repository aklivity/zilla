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
package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

public class RisingwaveDropMaterializedViewTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        DROP MATERIALIZED VIEW %s;\u0000""";

    public RisingwaveDropMaterializedViewTemplate()
    {
    }

    public String generate(
        String drop)
    {
        return generate(drop, "");
    }

    public String generate(
        String drop,
        String suffix)
    {
        String source = "%s%s".formatted(drop, suffix);

        return String.format(sqlFormat, source);
    }
}
