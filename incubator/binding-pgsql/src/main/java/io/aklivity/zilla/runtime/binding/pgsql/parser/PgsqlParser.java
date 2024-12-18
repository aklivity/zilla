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
package io.aklivity.zilla.runtime.binding.pgsql.parser;

import java.util.List;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import io.aklivity.zilla.runtime.binding.pgsql.parser.listener.SqlAlterStreamTopicListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.listener.SqlAlterZtableTopicListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.listener.SqlCommandListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.listener.SqlCreateFunctionListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.listener.SqlCreateStreamListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.listener.SqlCreateZtableTopicListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.listener.SqlCreateZviewListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.listener.SqlDropListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.listener.SqlShowListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Alter;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateStream;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateTable;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZview;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Drop;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Function;

public final class PgsqlParser
{
    private final ParseTreeWalker walker;
    private final BailErrorStrategy errorStrategy;
    private final PostgreSqlLexer lexer;
    private final CommonTokenStream tokens;
    private final PostgreSqlParser parser;
    private final SqlCommandListener commandListener;
    private final SqlCreateStreamListener createStreamListener;
    private final SqlCreateZtableTopicListener createTableListener;
    private final SqlAlterZtableTopicListener alterTableListener;
    private final SqlAlterStreamTopicListener alterStreamListener;
    private final SqlCreateFunctionListener createFunctionListener;
    private final SqlCreateZviewListener createMaterializedViewListener;
    private final SqlShowListener showListener;
    private final SqlDropListener dropListener;

    public PgsqlParser()
    {
        this.walker = new ParseTreeWalker();
        this.errorStrategy = new BailErrorStrategy();
        this.lexer = new PostgreSqlLexer(null);
        this.tokens = new CommonTokenStream(lexer);
        this.parser = new PostgreSqlParser(tokens);
        this.commandListener = new SqlCommandListener(tokens);
        this.createTableListener = new SqlCreateZtableTopicListener(tokens);
        this.alterTableListener = new SqlAlterZtableTopicListener(tokens);
        this.alterStreamListener = new SqlAlterStreamTopicListener(tokens);
        this.createStreamListener = new SqlCreateStreamListener(tokens);
        this.createFunctionListener = new SqlCreateFunctionListener(tokens);
        this.createMaterializedViewListener = new SqlCreateZviewListener(tokens);
        this.dropListener = new SqlDropListener();
        this.showListener = new SqlShowListener();
        parser.setErrorHandler(errorStrategy);
    }

    public String parseCommand(
        String sql)
    {
        parser(sql, commandListener);
        return commandListener.command();
    }

    public CreateTable parseCreateTable(
        String sql)
    {
        parser(sql, createTableListener);
        return createTableListener.table();
    }

    public Alter parseAlterTable(
        String sql)
    {
        parser(sql, alterTableListener);
        return alterTableListener.alter();
    }

    public Alter parseAlterStream(
        String sql)
    {
        parser(sql, alterStreamListener);
        return alterStreamListener.alter();
    }

    public CreateStream parseCreateStream(
        String sql)
    {
        parser(sql, createStreamListener);
        return createStreamListener.stream();
    }

    public Function parseCreateFunction(
        String sql)
    {
        parser(sql, createFunctionListener);
        return createFunctionListener.function();
    }

    public CreateZview parseCreateZView(
        String sql)
    {
        parser(sql, createMaterializedViewListener);
        return createMaterializedViewListener.view();
    }

    public List<Drop> parseDrop(
        String sql)
    {
        parser(sql, dropListener);
        return dropListener.drops();
    }

    public String parseShow(
        String sql)
    {
        parser(sql, showListener);
        return showListener.type();
    }

    private void parser(
        String sql,
        PostgreSqlParserBaseListener listener)
    {
        try
        {
            sql = sql.replace("\u0000", "");

            CharStream input = CharStreams.fromString(sql);
            lexer.reset();
            lexer.setInputStream(input);

            tokens.setTokenSource(lexer);
            parser.setTokenStream(tokens);

            walker.walk(listener, parser.root());
        }
        catch (Exception ignore)
        {
        }
    }
}
