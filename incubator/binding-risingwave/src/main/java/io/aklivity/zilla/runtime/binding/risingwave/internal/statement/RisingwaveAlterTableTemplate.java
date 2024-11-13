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

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Alter;

public class RisingwaveAlterTableTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        ALTER TABLE %s %s;\u0000""";
    private final String fieldFormat = "%s COLUMN %s %s, ";

    public String generate(
        Alter alter)
    {
        String topic = alter.name();
        fieldBuilder.setLength(0);

        alter.expressions()
            .forEach(c -> fieldBuilder.append(
                String.format(fieldFormat, c.operation().name(), c.columnName(), c.columnType())));

        fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

        return String.format(sqlFormat, topic, fieldBuilder);
    }
}
