/*
 * Copyright 2021-2023 Aklivity Inc
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

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Table;

public class RisingwaveCreateTableTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        CREATE TABLE IF NOT EXISTS %s (%s%s);\u0000""";
    private final String primaryKeyFormat = ", PRIMARY KEY (%s)";
    private final String fieldFormat = "%s %s, ";

    public RisingwaveCreateTableTemplate()
    {
    }

    public String generate(
        Table table)
    {
        String topic = table.name();
        String primaryKey = !table.primaryKeys().isEmpty()
            ? String.format(primaryKeyFormat, table.primaryKeys().stream().findFirst().get())
            : "";

        fieldBuilder.setLength(0);

        table.columns()
            .forEach((k, v) -> fieldBuilder.append(
                String.format(fieldFormat, k, v)));

        fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

        return String.format(sqlFormat, topic, fieldBuilder, primaryKey);
    }
}
