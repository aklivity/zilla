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

public class SqlDropListener extends PostgreSqlParserBaseListener
{
    private final List<String> drops = new ArrayList<>();

    public List<String> drops()
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
        ctx.any_name_list().any_name().forEach(name -> drops.add(name.getText()));
    }
}
