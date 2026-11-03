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

final class LlmSchemaScanner
{
    static final int TARGET_ANTHROPIC = 0;
    static final int TARGET_GEMINI = 1;

    private static final int SCHEMA = 0;
    private static final int MAP = 1;
    private static final int SCHEMAS = 2;
    private static final int DATA = 3;

    private static final int NO_KEYWORD = 0;
    private static final int REF = 1;
    private static final int MIN_ITEMS = 2;

    private static final int DEPTH_MAX = 64;
    private static final int TOKEN_MAX = 64;

    private static final String KEYWORD_REF = "$ref";
    private static final String KEYWORD_MIN_ITEMS = "minItems";

    private static final String[] MAPS = {"properties", "patternProperties", "$defs", "definitions", "dependentSchemas"};
    private static final String[] SUBSCHEMAS =
    {
        "items", "additionalProperties", "not", "if", "then", "else", "contains", "propertyNames",
        "unevaluatedItems", "unevaluatedProperties", "additionalItems", "contentSchema"
    };
    private static final String[] SCHEMA_LISTS = {"allOf", "anyOf", "oneOf", "prefixItems"};
    private static final String[] ANTHROPIC_DENIED =
    {
        "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf", "minLength", "maxLength",
        "maxItems", "uniqueItems", "contains", "minContains", "maxContains"
    };
    private static final String[] GEMINI_DENIED =
    {
        "patternProperties", "not", "if", "then", "else", "dependentSchemas", "unevaluatedProperties"
    };

    private final int target;
    private final String[] denied;
    private final int[] kinds;
    private final StringBuilder token;

    private int depth;
    private int pending;
    private int keyword;

    LlmSchemaScanner(
        int target)
    {
        this.target = target;
        this.denied = target == TARGET_ANTHROPIC ? ANTHROPIC_DENIED : GEMINI_DENIED;
        this.kinds = new int[DEPTH_MAX];
        this.token = new StringBuilder();
    }

    void begin()
    {
        depth = 0;
        pending = SCHEMA;
        keyword = NO_KEYWORD;
        token.setLength(0);
    }

    String scan(
        JsonEvent event,
        CharSequence text,
        boolean deferred)
    {
        String rejected = null;

        switch (event)
        {
        case START_OBJECT:
            enter(parentKind() == SCHEMAS ? SCHEMA : pending == SCHEMA || pending == MAP ? pending : DATA);
            break;
        case START_ARRAY:
            enter(parentKind() != SCHEMAS && (pending == SCHEMA || pending == SCHEMAS) ? SCHEMAS : DATA);
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            keyword = NO_KEYWORD;
            break;
        case KEY_NAME:
        case VALUE_STRING:
        case VALUE_NUMBER:
            rejected = onToken(event, text, deferred);
            break;
        default:
            keyword = NO_KEYWORD;
            break;
        }

        return rejected;
    }

    private String onToken(
        JsonEvent event,
        CharSequence text,
        boolean deferred)
    {
        String rejected = null;

        if (token.length() < TOKEN_MAX)
        {
            token.append(text, 0, Math.min(text.length(), TOKEN_MAX - token.length()));
        }

        if (!deferred)
        {
            rejected = switch (event)
            {
            case KEY_NAME -> onKey(token);
            case VALUE_STRING -> onString(token);
            default -> onNumber(token);
            };

            token.setLength(0);
        }

        return rejected;
    }

    private int parentKind()
    {
        return depth > 0 ? kinds[depth - 1] : SCHEMA;
    }

    private void enter(
        int kind)
    {
        kinds[depth++] = kind;
        pending = DATA;
        keyword = NO_KEYWORD;
    }

    private String onKey(
        CharSequence key)
    {
        String rejected = null;
        final int kind = parentKind();

        keyword = NO_KEYWORD;

        if (kind == SCHEMA)
        {
            rejected = contains(denied, key) ? key.toString() : null;

            if (contains(MAPS, key))
            {
                pending = MAP;
            }
            else if (contains(SUBSCHEMAS, key))
            {
                pending = SCHEMA;
            }
            else if (contains(SCHEMA_LISTS, key))
            {
                pending = SCHEMAS;
            }
            else
            {
                pending = DATA;
            }

            if (matches(key, KEYWORD_REF))
            {
                keyword = REF;
            }
            else if (target == TARGET_ANTHROPIC && matches(key, KEYWORD_MIN_ITEMS))
            {
                keyword = MIN_ITEMS;
            }
        }
        else
        {
            pending = kind == MAP ? SCHEMA : DATA;
        }

        return rejected;
    }

    private String onString(
        CharSequence value)
    {
        String rejected = null;

        if (keyword == REF && parentKind() == SCHEMA)
        {
            final boolean external = value.length() == 0 || value.charAt(0) != '#';
            final boolean recursive = target == TARGET_ANTHROPIC && value.length() == 1;

            rejected = external || recursive ? KEYWORD_REF : null;
        }

        keyword = NO_KEYWORD;

        return rejected;
    }

    private String onNumber(
        CharSequence text)
    {
        final boolean limited = keyword == MIN_ITEMS && parentKind() == SCHEMA && !matches(text, "0") && !matches(text, "1");

        keyword = NO_KEYWORD;

        return limited ? KEYWORD_MIN_ITEMS : null;
    }

    private static boolean contains(
        String[] keywords,
        CharSequence key)
    {
        boolean contained = false;

        for (int index = 0; !contained && index < keywords.length; index++)
        {
            contained = matches(key, keywords[index]);
        }

        return contained;
    }

    private static boolean matches(
        CharSequence value,
        String expected)
    {
        final int length = expected.length();
        boolean matches = value.length() == length;

        for (int index = 0; matches && index < length; index++)
        {
            matches = value.charAt(index) == expected.charAt(index);
        }

        return matches;
    }
}
