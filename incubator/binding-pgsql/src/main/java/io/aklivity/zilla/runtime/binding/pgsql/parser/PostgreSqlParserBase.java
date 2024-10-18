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

import java.util.List;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;

public abstract class PostgreSqlParserBase extends Parser
{

    public PostgreSqlParserBase(TokenStream input)
    {
        super(input);
    }

    ParserRuleContext getParsedSqlTree(
        String script,
        int line)
    {
        PostgreSqlParser ph = getPostgreSqlParser(script);
        ParserRuleContext result = ph.root();
        return result;
    }

    public void parseRoutineBody(
        PostgreSqlParser.Createfunc_opt_listContext localctx)
    {
        String lang = null;
        for (PostgreSqlParser.Createfunc_opt_itemContext coi : localctx.createfunc_opt_item())
        {
            if (coi.LANGUAGE() != null)
            {
                if (coi.nonreservedword_or_sconst() != null)
                {
                    if (coi.nonreservedword_or_sconst().sconst() != null)
                    {
                        lang = coi.nonreservedword_or_sconst().sconst().getText();
                        break;
                    }
                    if (coi.nonreservedword_or_sconst().nonreservedword() != null)
                    {
                        if (coi.nonreservedword_or_sconst().nonreservedword().identifier() != null)
                        {
                            if (coi.nonreservedword_or_sconst().nonreservedword().identifier().Identifier() != null)
                            {
                                lang = coi.nonreservedword_or_sconst().nonreservedword().identifier().Identifier().getText();
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (null == lang)
        {
            return;
        }
        PostgreSqlParser.Createfunc_opt_itemContext funcAs = null;
        for (PostgreSqlParser.Createfunc_opt_itemContext a : localctx.createfunc_opt_item())
        {
            if (a.func_as() != null)
            {
                funcAs = a;
                break;
            }

        }
        if (funcAs != null)
        {
            String txt = getRoutineBodyString(funcAs.func_as().sconst(0));
            PostgreSqlParser ph = getPostgreSqlParser(txt);
            switch (lang)
            {
            case "plpgsql":
                funcAs.func_as().Definition = ph.plsqlroot();
                break;
            case "sql":
                funcAs.func_as().Definition = ph.root();
                break;
            }
        }
    }

    private String trimQuotes(String s)
    {
        return (s == null || s.isEmpty()) ? s : s.substring(1, s.length() - 1);
    }

    public String unquote(
        String s)
    {
        int slength = s.length();
        StringBuilder r = new StringBuilder(slength);
        int i = 0;
        while (i < slength)
        {
            Character c = s.charAt(i);
            r.append(c);
            if (c == '\'' && i < slength - 1 && s.charAt(i + 1) == '\'')
            {
                i++;
            }
            i++;
        }
        return r.toString();
    }

    public String getRoutineBodyString(
        PostgreSqlParser.SconstContext rule)
    {
        PostgreSqlParser.AnysconstContext anysconst = rule.anysconst();
        org.antlr.v4.runtime.tree.TerminalNode stringConstant = anysconst.StringConstant();
        if (null != stringConstant)
        {
            return trimQuotes(stringConstant.getText());
        }
        org.antlr.v4.runtime.tree.TerminalNode unicodeEscapeStringConstant = anysconst.UnicodeEscapeStringConstant();
        if (null != unicodeEscapeStringConstant)
        {
            return unquote(unicodeEscapeStringConstant.getText());
        }
        org.antlr.v4.runtime.tree.TerminalNode escapeStringConstant = anysconst.EscapeStringConstant();
        if (null != escapeStringConstant)
        {
            return unquote(escapeStringConstant.getText());
        }
        String result = "";
        List<org.antlr.v4.runtime.tree.TerminalNode> dollartext = anysconst.DollarText();
        for (org.antlr.v4.runtime.tree.TerminalNode s : dollartext)
        {
            result += s.getText();
        }
        return result;
    }

    public PostgreSqlParser getPostgreSqlParser(
        String script)
    {
        CharStream charStream = CharStreams.fromString(script);
        Lexer lexer = new PostgreSqlLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PostgreSqlParser parser = new PostgreSqlParser(tokens);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        LexerDispatchingErrorListener listenerLexer =
            new LexerDispatchingErrorListener((Lexer)(this.getInputStream().getTokenSource()));
        ParserDispatchingErrorListener listenerParser = new ParserDispatchingErrorListener(this);
        lexer.addErrorListener(listenerLexer);
        parser.addErrorListener(listenerParser);
        return parser;
    }
}
