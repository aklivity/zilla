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

import java.util.LinkedHashMap;
import java.util.Map;

import org.antlr.v4.runtime.TokenStream;

import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParser;
import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParserBaseListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Stream;

public class SqlCreateStreamListener extends PostgreSqlParserBaseListener
{
    private static final String PUBLIC_SCHEMA_NAME = "public";
    private static final String SCHEMA_PATTERN = "\\.";

    private final Map<String, String> columns;
    private final TokenStream tokens;

    private String schema;
    private String name;

    public SqlCreateStreamListener(
        TokenStream tokens)
    {
        this.columns = new LinkedHashMap<>();
        this.tokens = tokens;
    }

    public Stream stream()
    {
        return new Stream(schema, name, columns);
    }

    @Override
    public void enterRoot(
        PostgreSqlParser.RootContext ctx)
    {
        name = null;
        columns.clear();
    }

    @Override
    public void enterQualified_name(
        PostgreSqlParser.Qualified_nameContext ctx)
    {
        String text = ctx.getText();
        String[] split = text.split(SCHEMA_PATTERN);
        schema = split.length > 1 ? split[0] : PUBLIC_SCHEMA_NAME;
        name = split.length > 1 ? split[1] : text;
    }

    @Override
    public void enterCreatestreamstmt(
        PostgreSqlParser.CreatestreamstmtContext ctx)
    {
        if (ctx.stream_columns() != null)
        {
            for (PostgreSqlParser.Stream_columnContext streamElement : ctx.stream_columns().stream_column())
            {
                String columnName = streamElement.colid().getText();
                String dataType = tokens.getText(streamElement.typename());
                columns.put(columnName, dataType);
            }
        }
    }
}
