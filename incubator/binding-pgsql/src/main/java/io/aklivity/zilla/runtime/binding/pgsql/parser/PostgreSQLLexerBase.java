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

import java.util.ArrayDeque;
import java.util.Deque;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public abstract class PostgreSQLLexerBase extends Lexer
{
    protected final Deque<String> tags = new ArrayDeque<>();

    protected PostgreSQLLexerBase(CharStream input)
    {
        super(input);

    }

    public void pushTag()
    {
        tags.push(getText());
    }

    public boolean isTag()
    {
        return getText().equals(tags.peek());
    }

    public void popTag()
    {
        tags.pop();
    }

    public boolean checkLA(int c)
    {
        return getInputStream().LA(1) != c;
    }

    public boolean charIsLetter()
    {
        return Character.isLetter(getInputStream().LA(-1));
    }

    public void HandleNumericFail()
    {
        getInputStream().seek(getInputStream().index() - 2);
        setType(PostgreSQLLexer.Integral);
    }

    public void HandleLessLessGreaterGreater()
    {
        if (getText() == "<<") setType(PostgreSQLLexer.LESS_LESS);
        if (getText() == ">>") setType(PostgreSQLLexer.GREATER_GREATER);
    }

    public void UnterminatedBlockCommentDebugAssert()
    {
        //Debug.Assert(InputStream.LA(1) == -1 /*EOF*/);
    }

    public boolean CheckIfUtf32Letter()
    {
        int codePoint = getInputStream().LA(-2) << 8 + getInputStream().LA(-1);
        char[] c;
        if (codePoint < 0x10000)
        {
            c = new char[]{(char) codePoint};
        }
        else
        {
            codePoint -= 0x10000;
            c = new char[]{(char) (codePoint / 0x400 + 0xd800), (char) (codePoint % 0x400 + 0xdc00)};
        }
        return Character.isLetter(c[0]);
    }
}
