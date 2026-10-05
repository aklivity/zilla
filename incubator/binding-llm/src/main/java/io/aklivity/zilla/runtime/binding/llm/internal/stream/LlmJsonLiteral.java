/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

final class LlmJsonLiteral
{
    static final int LITERAL_MAX = 4096;

    private final StringBuilder literal;

    private int depth;
    private boolean separate;
    private boolean afterKey;
    private boolean stringOpen;
    private boolean overflow;

    LlmJsonLiteral()
    {
        this.literal = new StringBuilder();
    }

    void reset()
    {
        literal.setLength(0);
        depth = 0;
        separate = false;
        afterKey = false;
        stringOpen = false;
        overflow = false;
    }

    boolean overflow()
    {
        return overflow;
    }

    String value()
    {
        return literal.toString();
    }

    boolean accept(
        JsonEvent event,
        JsonParserEx parser)
    {
        boolean complete = false;

        switch (event)
        {
        case START_OBJECT:
            open('{');
            break;
        case START_ARRAY:
            open('[');
            break;
        case END_OBJECT:
            complete = close('}');
            break;
        case END_ARRAY:
            complete = close(']');
            break;
        case KEY_NAME:
            onKey(parser);
            break;
        case VALUE_STRING:
            complete = onString(parser);
            break;
        case VALUE_NUMBER:
            complete = onScalar(parser.getString());
            break;
        case VALUE_TRUE:
            complete = onScalar("true");
            break;
        case VALUE_FALSE:
            complete = onScalar("false");
            break;
        case VALUE_NULL:
            complete = onScalar("null");
            break;
        default:
            overflow = true;
            break;
        }

        return complete || overflow;
    }

    private void open(
        char start)
    {
        beforeValue();
        literal.append(start);
        depth++;
        separate = false;
    }

    private boolean close(
        char end)
    {
        literal.append(end);
        depth--;
        separate = true;
        afterKey = false;
        limit();

        return depth == 0;
    }

    private void onKey(
        JsonParserEx parser)
    {
        if (!parser.deferredBytes())
        {
            if (separate)
            {
                literal.append(',');
            }

            literal.append('"');
            appendEscaped(parser.getStringView());
            literal.append("\":");
            separate = false;
            afterKey = true;
            limit();
        }
    }

    private boolean onString(
        JsonParserEx parser)
    {
        final boolean deferred = parser.deferredBytes();
        final CharSequence fragment = parser.getStringView();

        if (!stringOpen)
        {
            beforeValue();
            literal.append('"');
            stringOpen = true;
        }

        appendEscaped(fragment);

        if (deferred)
        {
            parser.consumed(fragment.length());
        }
        else
        {
            literal.append('"');
            stringOpen = false;
        }

        limit();

        return !deferred && afterValue();
    }

    private boolean onScalar(
        String scalar)
    {
        beforeValue();
        literal.append(scalar);
        limit();

        return afterValue();
    }

    private void beforeValue()
    {
        if (!afterKey && separate)
        {
            literal.append(',');
        }

        afterKey = false;
    }

    private boolean afterValue()
    {
        separate = true;

        return depth == 0;
    }

    private void appendEscaped(
        CharSequence text)
    {
        for (int index = 0; index < text.length(); index++)
        {
            final char ch = text.charAt(index);

            switch (ch)
            {
            case '"':
                literal.append("\\\"");
                break;
            case '\\':
                literal.append("\\\\");
                break;
            case '\n':
                literal.append("\\n");
                break;
            case '\r':
                literal.append("\\r");
                break;
            case '\t':
                literal.append("\\t");
                break;
            default:
                if (ch < 0x20)
                {
                    literal.append(String.format("\\u%04x", (int) ch));
                }
                else
                {
                    literal.append(ch);
                }
                break;
            }
        }
    }

    private void limit()
    {
        overflow |= literal.length() > LITERAL_MAX;
    }
}
