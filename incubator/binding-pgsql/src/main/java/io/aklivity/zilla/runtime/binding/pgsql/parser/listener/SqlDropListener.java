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
package io.aklivity.zilla.runtime.binding.pgsql.parser.listener;

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParser;
import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParserBaseListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Drop;

public class SqlDropListener extends PostgreSqlParserBaseListener
{
    private static final String PUBLIC_SCHEMA_NAME = "public";
    private static final String SCHEMA_PATTERN = "\\.";

    private final List<Drop> drops;

    public SqlDropListener()
    {
        this.drops = new ArrayList<>();
    }
    public List<Drop> drops()
    {
        return drops;
    }

    @Override
    public void enterRoot(
        PostgreSqlParser.RootContext ctx)
    {
        drops.clear();
    }

    @Override
    public void enterDropstmt(
        PostgreSqlParser.DropstmtContext ctx)
    {
        ctx.any_name_list().any_name().forEach(n ->
        {
            String text = n.getText().replace("\"", "");
            String[] split = text.split(SCHEMA_PATTERN);
            String schema = split.length > 1 ? split[0] : PUBLIC_SCHEMA_NAME;
            String name = split.length > 1 ? split[1] : text;
            drops.add(new Drop(schema, name));
        });
    }
}
