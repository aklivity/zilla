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
package io.aklivity.zilla.runtime.common.json;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * A restricted JSONPath (RFC 9535) expression evaluator, compiled once and matched against a
 * {@link JsonValue} document tree to yield RFC 6901 JSON Pointer strings for each matched node.
 * <p>
 * Only the subset of JSONPath needed to express OpenAPI Overlay Specification targets is
 * supported: the root selector ({@code $}), dot and bracket member selectors (including
 * single- or double-quoted bracket names for keys containing reserved characters), integer
 * array indices (including negative indices counted from the end), and the wildcard selector
 * ({@code .*} or {@code [*]}) over object values or array elements. Descendant segments,
 * filter expressions, slices and unions are not supported.
 */
public final class JsonPath
{
    private final List<Segment> segments;

    private JsonPath(
        List<Segment> segments)
    {
        this.segments = segments;
    }

    public static JsonPath compile(
        String expression)
    {
        return new JsonPath(new Parser(expression).parse());
    }

    public List<String> matches(
        JsonValue root)
    {
        List<List<String>> matched = new ArrayList<>();
        match(root, 0, new ArrayList<>(), matched);

        List<String> pointers = new ArrayList<>(matched.size());
        for (List<String> tokens : matched)
        {
            pointers.add(toPointer(tokens));
        }
        return pointers;
    }

    private void match(
        JsonValue node,
        int index,
        List<String> path,
        List<List<String>> matched)
    {
        if (index == segments.size())
        {
            matched.add(new ArrayList<>(path));
        }
        else
        {
            segments.get(index).match(node, (token, child) ->
            {
                path.add(token);
                match(child, index + 1, path, matched);
                path.remove(path.size() - 1);
            });
        }
    }

    private static String toPointer(
        List<String> tokens)
    {
        StringBuilder pointer = new StringBuilder();
        for (String token : tokens)
        {
            pointer.append('/').append(escape(token));
        }
        return pointer.toString();
    }

    private static String escape(
        String token)
    {
        return token.indexOf('~') == -1 && token.indexOf('/') == -1
            ? token
            : token.replace("~", "~0").replace("/", "~1");
    }

    @FunctionalInterface
    private interface Segment
    {
        void match(
            JsonValue node,
            BiConsumer<String, JsonValue> emit);
    }

    private static final class NameSegment implements Segment
    {
        private final String name;

        private NameSegment(
            String name)
        {
            this.name = name;
        }

        @Override
        public void match(
            JsonValue node,
            BiConsumer<String, JsonValue> emit)
        {
            if (node.getValueType() == JsonValue.ValueType.OBJECT)
            {
                JsonObject object = node.asJsonObject();
                if (object.containsKey(name))
                {
                    emit.accept(name, object.get(name));
                }
            }
        }
    }

    private static final class IndexSegment implements Segment
    {
        private final int index;

        private IndexSegment(
            int index)
        {
            this.index = index;
        }

        @Override
        public void match(
            JsonValue node,
            BiConsumer<String, JsonValue> emit)
        {
            if (node.getValueType() == JsonValue.ValueType.ARRAY)
            {
                JsonArray array = node.asJsonArray();
                int resolved = index >= 0 ? index : array.size() + index;
                if (resolved >= 0 && resolved < array.size())
                {
                    emit.accept(String.valueOf(resolved), array.get(resolved));
                }
            }
        }
    }

    private static final class WildcardSegment implements Segment
    {
        @Override
        public void match(
            JsonValue node,
            BiConsumer<String, JsonValue> emit)
        {
            if (node.getValueType() == JsonValue.ValueType.OBJECT)
            {
                node.asJsonObject().forEach(emit::accept);
            }
            else if (node.getValueType() == JsonValue.ValueType.ARRAY)
            {
                JsonArray array = node.asJsonArray();
                for (int i = 0; i < array.size(); i++)
                {
                    emit.accept(String.valueOf(i), array.get(i));
                }
            }
        }
    }

    private static final class Parser
    {
        private final String expression;
        private int position;

        private Parser(
            String expression)
        {
            this.expression = expression;
        }

        private List<Segment> parse()
        {
            if (expression.isEmpty() || expression.charAt(0) != '$')
            {
                throw new IllegalArgumentException("JSONPath expression must start with '$': " + expression);
            }
            position = 1;

            List<Segment> segments = new ArrayList<>();
            while (position < expression.length())
            {
                char c = expression.charAt(position);
                if (c == '.')
                {
                    position++;
                    segments.add(parseDotSegment());
                }
                else if (c == '[')
                {
                    segments.add(parseBracketSegment());
                }
                else
                {
                    throw new IllegalArgumentException("Unexpected character at " + position + " in: " + expression);
                }
            }
            return segments;
        }

        private Segment parseDotSegment()
        {
            Segment segment;
            if (position < expression.length() && expression.charAt(position) == '*')
            {
                position++;
                segment = new WildcardSegment();
            }
            else
            {
                int start = position;
                while (position < expression.length() && isNameChar(expression.charAt(position)))
                {
                    position++;
                }
                if (position == start)
                {
                    throw new IllegalArgumentException("Expected property name at " + position + " in: " + expression);
                }
                segment = new NameSegment(expression.substring(start, position));
            }
            return segment;
        }

        private Segment parseBracketSegment()
        {
            position++;
            if (position >= expression.length())
            {
                throw new IllegalArgumentException("Unterminated '[' in: " + expression);
            }

            char c = expression.charAt(position);
            Segment segment;
            if (c == '\'' || c == '"')
            {
                segment = new NameSegment(parseQuotedName(c));
            }
            else if (c == '*')
            {
                position++;
                segment = new WildcardSegment();
            }
            else
            {
                segment = new IndexSegment(parseIndex());
            }

            if (position >= expression.length() || expression.charAt(position) != ']')
            {
                throw new IllegalArgumentException("Expected ']' at " + position + " in: " + expression);
            }
            position++;

            return segment;
        }

        private String parseQuotedName(
            char quote)
        {
            position++;
            int start = position;
            while (position < expression.length() && expression.charAt(position) != quote)
            {
                position++;
            }
            if (position >= expression.length())
            {
                throw new IllegalArgumentException("Unterminated quoted name in: " + expression);
            }
            String name = expression.substring(start, position);
            position++;
            return name;
        }

        private int parseIndex()
        {
            int start = position;
            if (position < expression.length() && expression.charAt(position) == '-')
            {
                position++;
            }
            while (position < expression.length() && Character.isDigit(expression.charAt(position)))
            {
                position++;
            }
            if (position == start || position == start + 1 && expression.charAt(start) == '-')
            {
                throw new IllegalArgumentException("Expected array index at " + position + " in: " + expression);
            }
            return Integer.parseInt(expression.substring(start, position));
        }

        private static boolean isNameChar(
            char c)
        {
            return Character.isLetterOrDigit(c) || c == '_' || c == '-';
        }
    }
}
