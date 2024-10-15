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

import java.util.BitSet;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ProxyErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;


public class ParserDispatchingErrorListener implements ANTLRErrorListener
{
    @SuppressWarnings("checkstyle:MemberName")
    Parser _parent;

    public ParserDispatchingErrorListener(Parser parent)
    {
        _parent = parent;
    }

    public void syntaxError(
        Recognizer<?, ?> recognizer, Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e)
    {
        var foo = new ProxyErrorListener(_parent.getErrorListeners());
        foo.syntaxError(recognizer, offendingSymbol, line, charPositionInLine, msg, e);
    }

    public void reportAmbiguity(
        Parser recognizer,
        DFA dfa,
        int startIndex,
        int stopIndex,
        boolean exact,
        BitSet ambigAlts,
        ATNConfigSet configs)
    {
        ProxyErrorListener foo = new ProxyErrorListener(_parent.getErrorListeners());
        foo.reportAmbiguity(recognizer, dfa, startIndex, stopIndex, exact, ambigAlts, configs);
    }

    public void reportAttemptingFullContext(
        Parser recognizer,
        DFA dfa,
        int startIndex,
        int stopIndex,
        BitSet conflictingAlts,
        ATNConfigSet configs)
    {
        ProxyErrorListener foo = new ProxyErrorListener(_parent.getErrorListeners());
        foo.reportAttemptingFullContext(recognizer, dfa, startIndex, stopIndex, conflictingAlts, configs);
    }

    public void reportContextSensitivity(
        Parser recognizer,
        DFA dfa,
        int startIndex,
        int stopIndex,
        int prediction,
        ATNConfigSet configs)
    {
        ProxyErrorListener foo = new ProxyErrorListener(_parent.getErrorListeners());
        foo.reportContextSensitivity(recognizer, dfa, startIndex, stopIndex, prediction, configs);
    }
}
