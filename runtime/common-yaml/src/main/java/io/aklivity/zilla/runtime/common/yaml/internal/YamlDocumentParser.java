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
package io.aklivity.zilla.runtime.common.yaml.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.json.stream.JsonParsingException;

final class YamlDocumentParser
{
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
        "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    private final List<Line> lines;
    private int index;

    private YamlDocumentParser(
        String text)
    {
        this.lines = lines(text);
    }

    static YamlNode parse(
        String text)
    {
        return new YamlDocumentParser(text).parse();
    }

    private YamlNode parse()
    {
        if (lines.isEmpty())
        {
            return YamlScalarNode.literal(YamlScalarType.NULL, 1, 1, 0);
        }

        YamlNode node = parseBlock(lines.get(0).indent);
        if (index < lines.size())
        {
            throw error("Unexpected indentation", lines.get(index));
        }
        return node;
    }

    private YamlNode parseBlock(
        int indent)
    {
        Line line = peek();
        if (line.indent < indent)
        {
            throw error("Unexpected dedent", line);
        }
        if (line.indent > indent)
        {
            throw error("Unexpected indentation", line);
        }

        return isSequence(line, indent) ? parseSequence(indent) :
            mappingColon(line.content) != -1 ? parseMapping(indent) :
            parsePlainLine();
    }

    private YamlObjectNode parseMapping(
        int indent)
    {
        Line line = peek();
        YamlObjectNode object = new YamlObjectNode(line.line, line.column, line.offset);

        while (index < lines.size())
        {
            line = peek();
            if (line.indent != indent || isSequence(line, indent))
            {
                break;
            }

            addMappingEntry(object, line.content, indent, line);
        }

        return object;
    }

    private YamlArrayNode parseSequence(
        int indent)
    {
        Line line = peek();
        YamlArrayNode array = new YamlArrayNode(line.line, line.column, line.offset);

        while (index < lines.size())
        {
            line = peek();
            if (!isSequence(line, indent))
            {
                break;
            }

            String item = line.content.length() == 1 ? "" : line.content.substring(2).trim();

            if (item.isEmpty())
            {
                index++;
                array.add(index < lines.size() && peek().indent > indent ?
                    parseBlock(peek().indent) :
                    YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column, line.offset));
            }
            else if (mappingColon(item) != -1)
            {
                YamlObjectNode object = new YamlObjectNode(line.line, line.column + 2, line.offset + 2);
                addMappingEntry(object, item, indent + 2, line);

                while (index < lines.size() && peek().indent > indent)
                {
                    Line next = peek();
                    if (isSequence(next, next.indent) || mappingColon(next.content) == -1)
                    {
                        throw error("Unexpected sequence item continuation", next);
                    }
                    object.addAll(parseMapping(next.indent));
                }

                array.add(object);
            }
            else
            {
                index++;
                array.add(parseInlineValue(item, line));
            }
        }

        return array;
    }

    private YamlNode parsePlainLine()
    {
        Line line = peek();
        index++;
        return parseScalar(line.content.trim(), line);
    }

    private void addMappingEntry(
        YamlObjectNode object,
        String content,
        int indent,
        Line line)
    {
        int colonAt = mappingColon(content);
        if (colonAt == -1)
        {
            throw error("Expected mapping entry", line);
        }

        String keyText = content.substring(0, colonAt).trim();
        String valueText = content.substring(colonAt + 1).trim();
        String key = parseKey(keyText, line);

        index++;
        YamlNode value = valueText.isEmpty() ? parseEmptyValue(indent, line) : parseInlineValue(valueText, line);
        object.add(new YamlEntry(key, value, line.line, line.column, line.offset));
    }

    private YamlNode parseEmptyValue(
        int indent,
        Line line)
    {
        if (index < lines.size())
        {
            Line next = peek();
            if (next.indent > indent)
            {
                return parseBlock(next.indent);
            }
            if (next.indent == indent && isSequence(next, indent))
            {
                return parseSequence(indent);
            }
        }

        return YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column, line.offset);
    }

    private YamlNode parseInlineValue(
        String text,
        Line line)
    {
        rejectUnsupportedScalar(text, line);
        if (text.startsWith("{") || text.startsWith("["))
        {
            return new FlowParser(text, line).parse();
        }
        return parseScalar(text, line);
    }

    private static YamlScalarNode parseScalar(
        String text,
        Line line)
    {
        if (text.startsWith("\"") || text.startsWith("'"))
        {
            return YamlScalarNode.string(unquote(text, line), line.line, line.column, line.offset);
        }

        rejectUnsupportedScalar(text, line);
        String lower = text.toLowerCase(Locale.ROOT);
        return switch (lower)
        {
        case "true" -> YamlScalarNode.literal(YamlScalarType.TRUE, line.line, line.column, line.offset);
        case "false" -> YamlScalarNode.literal(YamlScalarType.FALSE, line.line, line.column, line.offset);
        case "null", "~" -> YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column, line.offset);
        default -> NUMBER_PATTERN.matcher(text).matches() ?
            YamlScalarNode.number(text, line.line, line.column, line.offset) :
            YamlScalarNode.string(text, line.line, line.column, line.offset);
        };
    }

    private static String parseKey(
        String text,
        Line line)
    {
        if (text.isEmpty())
        {
            throw error("Expected mapping key", line);
        }
        if (text.startsWith("\"") || text.startsWith("'"))
        {
            return unquote(text, line);
        }
        rejectUnsupportedScalar(text, line);
        return text;
    }

    private Line peek()
    {
        return lines.get(index);
    }

    private static boolean isSequence(
        Line line,
        int indent)
    {
        return line.indent == indent && (line.content.equals("-") || line.content.startsWith("- "));
    }

    private static int mappingColon(
        String text)
    {
        boolean single = false;
        boolean doub = false;
        boolean escaped = false;
        int depth = 0;

        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (doub)
            {
                if (escaped)
                {
                    escaped = false;
                }
                else if (c == '\\')
                {
                    escaped = true;
                }
                else if (c == '"')
                {
                    doub = false;
                }
            }
            else if (single)
            {
                if (c == '\'')
                {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\'')
                    {
                        i++;
                    }
                    else
                    {
                        single = false;
                    }
                }
            }
            else
            {
                switch (c)
                {
                case '\'' -> single = true;
                case '"' -> doub = true;
                case '{', '[' -> depth++;
                case '}', ']' -> depth--;
                case ':' ->
                {
                    if (depth == 0 && (i + 1 == text.length() || Character.isWhitespace(text.charAt(i + 1))))
                    {
                        return i;
                    }
                }
                default ->
                {
                    // continue
                }
                }
            }
        }

        return -1;
    }

    private static String unquote(
        String text,
        Line line)
    {
        if (text.length() < 2)
        {
            throw error("Unterminated quoted scalar", line);
        }

        char quote = text.charAt(0);
        if (text.charAt(text.length() - 1) != quote)
        {
            throw error("Unterminated quoted scalar", line);
        }

        return quote == '\'' ? unquoteSingle(text, line) : unquoteDouble(text, line);
    }

    private static String unquoteSingle(
        String text,
        Line line)
    {
        StringBuilder value = new StringBuilder();
        for (int i = 1; i < text.length() - 1; i++)
        {
            char c = text.charAt(i);
            if (c == '\'' && i + 1 < text.length() - 1 && text.charAt(i + 1) == '\'')
            {
                value.append('\'');
                i++;
            }
            else if (c == '\'')
            {
                throw error("Unexpected single quote in scalar", line);
            }
            else
            {
                value.append(c);
            }
        }
        return value.toString();
    }

    private static String unquoteDouble(
        String text,
        Line line)
    {
        StringBuilder value = new StringBuilder();
        for (int i = 1; i < text.length() - 1; i++)
        {
            char c = text.charAt(i);
            if (c == '\\')
            {
                if (++i >= text.length() - 1)
                {
                    throw error("Unterminated escape sequence", line);
                }
                appendEscape(value, text.charAt(i), text, i, line);
                if (text.charAt(i) == 'u')
                {
                    i += 4;
                }
            }
            else
            {
                value.append(c);
            }
        }
        return value.toString();
    }

    private static void appendEscape(
        StringBuilder value,
        char escaped,
        String text,
        int at,
        Line line)
    {
        switch (escaped)
        {
        case '"' -> value.append('"');
        case '\\' -> value.append('\\');
        case '/' -> value.append('/');
        case 'b' -> value.append('\b');
        case 'f' -> value.append('\f');
        case 'n' -> value.append('\n');
        case 'r' -> value.append('\r');
        case 't' -> value.append('\t');
        case 'u' -> value.append((char) Integer.parseInt(text.substring(at + 1, at + 5), 16));
        default -> throw error("Unsupported escape sequence", line);
        }
    }

    private static void rejectUnsupportedScalar(
        String text,
        Line line)
    {
        if (text.startsWith("&") || text.startsWith("*") || text.startsWith("!") ||
            text.startsWith("|") || text.startsWith(">"))
        {
            throw error("Unsupported YAML feature", line);
        }
    }

    private static List<Line> lines(
        String text)
    {
        List<Line> lines = new ArrayList<>();
        int offset = 0;
        int lineNumber = 1;
        boolean documentStartAllowed = true;

        for (String raw : text.split("\n", -1))
        {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            int indent = indent(line, lineNumber, offset);
            String content = stripComment(line.substring(indent)).stripTrailing();

            if (!content.isBlank())
            {
                if ("---".equals(content) && documentStartAllowed)
                {
                    documentStartAllowed = false;
                }
                else if ("---".equals(content) || "...".equals(content))
                {
                    throw error("Multiple YAML documents are not supported",
                        new Line(indent, content, lineNumber, indent + 1, offset + indent));
                }
                else
                {
                    documentStartAllowed = false;
                    lines.add(new Line(indent, content, lineNumber, indent + 1, offset + indent));
                }
            }

            offset += raw.length() + 1;
            lineNumber++;
        }

        return lines;
    }

    private static int indent(
        String line,
        int lineNumber,
        int offset)
    {
        int indent = 0;
        while (indent < line.length())
        {
            char c = line.charAt(indent);
            if (c == ' ')
            {
                indent++;
            }
            else if (c == '\t')
            {
                throw error("Tabs are not supported in indentation",
                    new Line(indent, line, lineNumber, indent + 1, offset + indent));
            }
            else
            {
                break;
            }
        }
        return indent;
    }

    private static String stripComment(
        String text)
    {
        boolean single = false;
        boolean doub = false;
        boolean escaped = false;

        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (doub)
            {
                if (escaped)
                {
                    escaped = false;
                }
                else if (c == '\\')
                {
                    escaped = true;
                }
                else if (c == '"')
                {
                    doub = false;
                }
            }
            else if (single)
            {
                if (c == '\'')
                {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\'')
                    {
                        i++;
                    }
                    else
                    {
                        single = false;
                    }
                }
            }
            else if (c == '\'')
            {
                single = true;
            }
            else if (c == '"')
            {
                doub = true;
            }
            else if (c == '#' && (i == 0 || Character.isWhitespace(text.charAt(i - 1))))
            {
                return text.substring(0, i);
            }
        }

        return text;
    }

    private static JsonParsingException error(
        String message,
        Line line)
    {
        return new JsonParsingException(message, new YamlLocation(line.line, line.column, line.offset));
    }

    private static final class FlowParser
    {
        private final String text;
        private final Line line;
        private int cursor;

        private FlowParser(
            String text,
            Line line)
        {
            this.text = text;
            this.line = line;
        }

        private YamlNode parse()
        {
            YamlNode value = parseValue();
            skipWhitespace();
            if (cursor != text.length())
            {
                throw error("Unexpected flow content", line);
            }
            return value;
        }

        private YamlNode parseValue()
        {
            skipWhitespace();
            if (cursor >= text.length())
            {
                throw error("Expected flow value", line);
            }

            return switch (text.charAt(cursor))
            {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '\'', '"' -> parseQuotedScalar();
            default -> parseBareScalar();
            };
        }

        private YamlObjectNode parseObject()
        {
            YamlObjectNode object = new YamlObjectNode(line.line, line.column + cursor, line.offset + cursor);
            cursor++;
            skipWhitespace();
            if (consume('}'))
            {
                return object;
            }

            do
            {
                String key = parseFlowKey();
                skipWhitespace();
                if (!consume(':'))
                {
                    throw error("Expected ':' in flow mapping", line);
                }
                YamlNode value = parseValue();
                object.add(new YamlEntry(key, value, line.line, line.column + cursor, line.offset + cursor));
                skipWhitespace();
            }
            while (consume(','));

            if (!consume('}'))
            {
                throw error("Expected '}' in flow mapping", line);
            }
            return object;
        }

        private YamlArrayNode parseArray()
        {
            YamlArrayNode array = new YamlArrayNode(line.line, line.column + cursor, line.offset + cursor);
            cursor++;
            skipWhitespace();
            if (consume(']'))
            {
                return array;
            }

            do
            {
                array.add(parseValue());
                skipWhitespace();
            }
            while (consume(','));

            if (!consume(']'))
            {
                throw error("Expected ']' in flow sequence", line);
            }
            return array;
        }

        private String parseFlowKey()
        {
            skipWhitespace();
            if (cursor < text.length() && (text.charAt(cursor) == '\'' || text.charAt(cursor) == '"'))
            {
                return ((YamlScalarNode) parseQuotedScalar()).value;
            }

            int start = cursor;
            while (cursor < text.length() && text.charAt(cursor) != ':')
            {
                cursor++;
            }
            return text.substring(start, cursor).trim();
        }

        private YamlScalarNode parseQuotedScalar()
        {
            int start = cursor;
            char quote = text.charAt(cursor++);
            boolean escaped = false;
            while (cursor < text.length())
            {
                char c = text.charAt(cursor++);
                if (quote == '"' && escaped)
                {
                    escaped = false;
                }
                else if (quote == '"' && c == '\\')
                {
                    escaped = true;
                }
                else if (c == quote)
                {
                    break;
                }
            }
            return YamlScalarNode.string(unquote(text.substring(start, cursor), line),
                line.line, line.column + start, line.offset + start);
        }

        private YamlScalarNode parseBareScalar()
        {
            int start = cursor;
            while (cursor < text.length())
            {
                char c = text.charAt(cursor);
                if (c == ',' || c == ']' || c == '}')
                {
                    break;
                }
                cursor++;
            }
            return parseScalar(text.substring(start, cursor).trim(), line);
        }

        private boolean consume(
            char expected)
        {
            skipWhitespace();
            if (cursor < text.length() && text.charAt(cursor) == expected)
            {
                cursor++;
                return true;
            }
            return false;
        }

        private void skipWhitespace()
        {
            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor)))
            {
                cursor++;
            }
        }
    }

    private static final class Line
    {
        final int indent;
        final String content;
        final int line;
        final int column;
        final long offset;

        private Line(
            int indent,
            String content,
            int line,
            int column,
            long offset)
        {
            this.indent = indent;
            this.content = content;
            this.line = line;
            this.column = column;
            this.offset = offset;
        }
    }
}
