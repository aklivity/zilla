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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.TokenStream;

import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParser;
import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParserBaseListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZstream;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.ZstreamColumn;

public class SqlCreateZstreamListener extends PostgreSqlParserBaseListener
{
    private static final String PUBLIC_SCHEMA_NAME = "public";
    private static final String SCHEMA_PATTERN = "\\.";

    private final List<ZstreamColumn> columns;
    private final Map<String, String> commandHandlers;
    private final TokenStream tokens;

    private String schema;
    private String name;
    private String dispatchOn;

    public SqlCreateZstreamListener(
        TokenStream tokens)
    {
        this.columns = new ArrayList<>();
        this.commandHandlers = new LinkedHashMap<>();
        this.tokens = tokens;
    }

    public CreateZstream stream()
    {
        return new CreateZstream(schema, name, columns, dispatchOn, commandHandlers);
    }

    @Override
    public void enterRoot(
        PostgreSqlParser.RootContext ctx)
    {
        schema = null;
        name = null;
        dispatchOn = null;
        columns.clear();
        commandHandlers.clear();
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
    public void enterZreloption_elem(
        PostgreSqlParser.Zreloption_elemContext ctx)
    {
        dispatchOn = ctx.collabel().getText();
    }

    @Override
    public void enterZstream_columns(
        PostgreSqlParser.Zstream_columnsContext ctx)
    {
        ctx.zstream_column().forEach(c ->
        {
            String name = c.colid().getText();
            String type = tokens.getText(c.typename());
            String generatedAlways = tokens.getText(c.opt_generated_always());

            columns.add(new ZstreamColumn(name, type, generatedAlways));
        });
    }

    @Override
    public void enterCommand_function_mappings(
        PostgreSqlParser.Command_function_mappingsContext ctx)
    {
        ctx.command_function_mapping().forEach(c ->
        {
            String dispatch = c.sconst().getText();
            if (dispatch != null && dispatch.length() > 1)
            {
                dispatch = dispatch.substring(1, dispatch.length() - 1);
            }
            String handler = c.function_name().getText();

            if (handler != null && handler.length() > 1)
            {
                handler = handler.substring(1, handler.length() - 1);
            }

            commandHandlers.put(dispatch, handler);
        });
    }
}
