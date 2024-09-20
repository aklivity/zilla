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

import java.util.Map;

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.view.CreateView;

public class RisingwaveCreateSinkTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        CREATE SINK %s_sink
        FROM %s
        WITH (
           connector='kafka',
           properties.bootstrap.server='%s',
           topic='%s.%s',
           primary_key='%s'
        ) FORMAT UPSERT ENCODE AVRO (
           schema.registry='%s'
        );\u0000""";

    private final String bootstrapServer;
    private final String schemaRegistry;

    public RisingwaveCreateSinkTemplate(
        String bootstrapServer,
        String schemaRegistry)
    {
        this.bootstrapServer = bootstrapServer;
        this.schemaRegistry = schemaRegistry;
    }

    public String generate(
        String database,
        Map<String, String> columns,
        Statement statement)
    {
        CreateView createView = (CreateView) statement;
        String viewName = createView.getView().getName();
        String primaryKey = columns.keySet().iterator().next();

        return String.format(sqlFormat, viewName, viewName, bootstrapServer, database, viewName, primaryKey, schemaRegistry);
    }
}