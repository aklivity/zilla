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

import net.sf.jsqlparser.statement.drop.Drop;

public class RisingwaveDropSinkTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        DROP SINK %s (%s%s);\u0000""";

    public RisingwaveDropSinkTemplate()
    {
    }

    public String generate(
        Drop dropTable)
    {
        return generate(dropTable, "");
    }

    public String generate(
        Drop dropTable,
        String suffix)
    {
        String topic = dropTable.getName().getName();
        String source = "%s%s".formatted(topic, suffix);

        return String.format(sqlFormat, source);
    }
}
