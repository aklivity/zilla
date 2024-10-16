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

public abstract class PostgreSqlLexerBase extends Lexer
{
    protected final Deque<String> tags = new ArrayDeque<>();

    protected PostgreSqlLexerBase(CharStream input)
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

    public void handleNumericFail()
    {
        getInputStream().seek(getInputStream().index() - 2);
        setType(PostgreSqlLexer.Integral);
    }

    public void handleLessLessGreaterGreater()
    {
        if ("<<".equals(getText()))
        {
            setType(PostgreSqlLexer.LESS_LESS);
        }
        if (">>".equals(getText()))
        {
            setType(PostgreSqlLexer.GREATER_GREATER);
        }
    }

    public void unterminatedBlockCommentDebugAssert()
    {
        //Debug.Assert(InputStream.LA(1) == -1 /*EOF*/);
    }

    public boolean checkIfUtf32Letter()
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
