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
package io.aklivity.zilla.runtime.binding.pgsql.parser;

import java.util.Set;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import io.aklivity.zilla.runtime.binding.pgsql.parser.listener.SqlCreateMaterializedViewListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.listener.SqlCreateTableListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.listener.SqlDropListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.module.TableInfo;
import io.aklivity.zilla.runtime.binding.pgsql.parser.module.ViewInfo;

public final class PgsqlParser
{
    private final ParseTreeWalker walker;
    private final BailErrorStrategy errorStrategy;
    private final PostgreSqlLexer lexer;
    private final CommonTokenStream tokens;
    private final PostgreSqlParser parser;
    private final SqlCreateTableListener createTableListener;
    private final SqlCreateMaterializedViewListener createMaterializedViewListener;
    private final SqlDropListener dropListener;

    public PgsqlParser()
    {
        this.walker = new ParseTreeWalker();
        this.errorStrategy = new BailErrorStrategy();
        this.lexer = new PostgreSqlLexer(null);
        this.parser = new PostgreSqlParser(null);
        this.tokens = new CommonTokenStream(lexer);
        this.createTableListener = new SqlCreateTableListener();
        this.createMaterializedViewListener = new SqlCreateMaterializedViewListener();
        this.dropListener = new SqlDropListener();
        parser.setErrorHandler(errorStrategy);
    }

    public TableInfo parseCreateTable(
        String sql)
    {
        parser(sql, createTableListener);
        return createTableListener.tableInfo();
    }

    public ViewInfo parseCreateMaterializedView(
        String sql)
    {
        parser(sql, createMaterializedViewListener);
        return createMaterializedViewListener.viewInfo();
    }

    public Set<String> parseDrop(
        String sql)
    {
        parser(sql, dropListener);
        return dropListener.drops();
    }

    private void parser(
        String sql,
        PostgreSqlParserBaseListener listener)
    {
        CharStream input = CharStreams.fromString(sql);
        lexer.reset();
        lexer.setInputStream(input);

        tokens.setTokenSource(lexer);
        parser.setTokenStream(tokens);

        walker.walk(listener, parser.root());
    }
}
