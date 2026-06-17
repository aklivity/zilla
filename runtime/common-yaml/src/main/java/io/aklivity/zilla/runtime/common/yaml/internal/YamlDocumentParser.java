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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YamlDocumentParser
{
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
        "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private static final Pattern HEX_INTEGER_PATTERN = Pattern.compile("-?0x[0-9a-fA-F]+");
    private static final Pattern FLOW_FOLD_PATTERN = Pattern.compile("[ \\t]*\\R[ \\t]*");

    private final List<Line> lines;
    private final Map<String, String> tagHandles;
    private final YamlLocation end;
    private final String source;
    private final YamlConfiguration config;
    private final List<String> comments;
    private Matcher numberMatcher;
    private Matcher hexIntegerMatcher;
    private int index;

    private YamlDocumentParser(
        Document document)
    {
        this.lines = document.lines;
        this.tagHandles = document.tagHandles;
        this.end = document.end;
        this.source = document.source;
        this.config = document.config;
        this.comments = new ArrayList<>();
    }

    public static Result parse(
        String text)
    {
        return parse(text, YamlConfiguration.DEFAULT);
    }

    public static Result parse(
        String text,
        Map<String, ?> config)
    {
        return parse(text, new YamlConfiguration(config));
    }

    public static Result parse(
        String text,
        YamlConfiguration config)
    {
        Document document = new DocumentScanner(text, config).scan();
        YamlNode node = new YamlDocumentParser(document).parse();
        return new Result(node, document.end);
    }

    public static List<Result> parseAll(
        String text)
    {
        return parseAll(text, YamlConfiguration.DEFAULT);
    }

    public static List<Result> parseAll(
        String text,
        Map<String, ?> config)
    {
        return parseAll(text, new YamlConfiguration(config));
    }

    public static List<Result> parseAll(
        String text,
        YamlConfiguration config)
    {
        List<Result> results = new ArrayList<>();
        int offset = 0;
        do
        {
            String remaining = text.substring(offset);
            Result result = parse(remaining, config);
            results.add(result);

            int read = (int) result.end.offset();
            if (read <= 0)
            {
                break;
            }
            offset += read;
            if (!config.multiDocumentStreams() && offset < text.length() && !text.substring(offset).isBlank())
            {
                throw new YamlParseException("YAML document streams are disabled", result.end);
            }
        }
        while (offset < text.length() && !text.substring(offset).isBlank());

        return results;
    }

    private YamlNode parse()
    {
        skipIgnorable();
        if (index >= lines.size())
        {
            YamlScalarNode nullNode = YamlScalarNode.literal(YamlScalarType.NULL, 1, 1, 0);
            if (config.preserveSource())
            {
                nullNode.source = source;
            }
            return nullNode;
        }

        Line line = peek();
        YamlNode node = (line.content.startsWith("{") || line.content.startsWith("[")) &&
            mappingColon(line.content) == -1 ?
            parseFlowDocument(line) :
            parseBlock(line.indent);

        skipIgnorable();
        if (index < lines.size())
        {
            throw error("Unexpected indentation", lines.get(index));
        }
        if (config.preserveSource())
        {
            node.source = source;
        }
        return node;
    }

    private YamlNode parseFlowDocument(
        Line line)
    {
        if (!config.flowCollections())
        {
            throw error("YAML flow collections are disabled", line);
        }
        StringBuilder flow = new StringBuilder(line.raw.substring(Math.min(line.indent, line.raw.length())));
        for (int i = index + 1; i < lines.size(); i++)
        {
            Line next = lines.get(i);
            flow.append('\n');
            flow.append(next.raw.substring(Math.min(next.indent, next.raw.length())));
        }
        index = lines.size();
        return new FlowParser(flow.toString(), line, tagHandles, config).parse();
    }

    private YamlNode parseBlock(
        int indent)
    {
        skipIgnorable();
        Line line = peek();
        if (line.indent < indent)
        {
            throw error("Unexpected dedent", line);
        }
        if (line.indent > indent)
        {
            throw error("Unexpected indentation", line);
        }
        if (tabIndented(line) && (isSequence(line, indent) || isExplicitKey(line) || mappingColon(line.content) != -1))
        {
            throw error("Tabs are not supported in block indentation", line);
        }

        return isSequence(line, indent) ? parseSequence(indent) :
            isExplicitKey(line) || mappingColon(line.content) != -1 ? parseMapping(indent) :
            parsePlainLine();
    }

    private YamlObjectNode parseMapping(
        int indent)
    {
        skipIgnorable();
        Line line = peek();
        YamlObjectNode object = new YamlObjectNode(line.line, line.column, line.offset);

        while (index < lines.size())
        {
            skipIgnorable();
            if (index >= lines.size())
            {
                break;
            }

            line = peek();
            if (line.indent != indent || isSequence(line, indent))
            {
                break;
            }

            if (isExplicitKey(line))
            {
                addExplicitMappingEntry(object, indent, line);
            }
            else
            {
                addMappingEntry(object, line.content, indent, line);
            }
        }

        return object;
    }

    private YamlArrayNode parseSequence(
        int indent)
    {
        skipIgnorable();
        Line line = peek();
        YamlArrayNode array = new YamlArrayNode(line.line, line.column, line.offset);

        while (index < lines.size())
        {
            skipIgnorable();
            if (index >= lines.size())
            {
                break;
            }

            line = peek();
            if (!isSequence(line, indent))
            {
                break;
            }
            int itemAt = 1;
            while (itemAt < line.content.length() && Character.isWhitespace(line.content.charAt(itemAt)))
            {
                itemAt++;
            }
            String item = itemAt == line.content.length() ? "" : line.content.substring(itemAt).trim();
            if (line.content.substring(0, itemAt).indexOf('\t') != -1 && ("-".equals(item) || item.startsWith("- ")))
            {
                throw error("Tabs are not supported after sequence indicators", line);
            }

            ValueSpec spec = ValueSpec.parse(item, line, tagHandles);
            validateSpec(spec, line);

            if (spec.alias != null)
            {
                index++;
                YamlNode value = resolveAlias(spec.alias, line);
                attachComments(value, line);
                array.add(value);
            }
            else if (spec.value.isEmpty())
            {
                index++;
                YamlNode value = nextNestedSequenceValue(indent, line);
                value = applyTag(value, spec.tag, spec.value, line);
                attachComments(value, line);
                storeAnchor(spec.anchor, value, line);
                array.add(value);
            }
            else if (isCompactSequence(spec.value))
            {
                index++;
                int nestedIndent = line.indent + itemAt;
                YamlArrayNode value = parseCompactSequence(spec.value, nestedIndent, line);
                while (index < lines.size() && isSequence(peek(), nestedIndent))
                {
                    YamlArrayNode continuation = parseSequence(nestedIndent);
                    continuation.values.forEach(value::add);
                }
                attachComments(value, line);
                storeAnchor(spec.anchor, value, line);
                array.add(value);
            }
            else if (mappingColon(spec.value) != -1)
            {
                List<String> itemComments = takeComments();
                YamlObjectNode object = new YamlObjectNode(line.line, line.column + 2, line.offset + 2);
                addMappingEntry(object, spec.value, indent + 2, line);

                while (true)
                {
                    skipIgnorable();
                    if (index >= lines.size() || peek().indent <= indent)
                    {
                        break;
                    }

                    Line next = peek();
                    if (isSequence(next, next.indent) || mappingColon(next.content) == -1)
                    {
                        throw error("Unexpected sequence item continuation", next);
                    }
                    object.addAll(parseMapping(next.indent));
                }

                object = (YamlObjectNode) applyTag(object, spec.tag, spec.value, line);
                attachComments(object, line);
                if (itemComments != null)
                {
                    object.leadingComments = itemComments;
                }
                storeAnchor(spec.anchor, object, line);
                array.add(object);
            }
            else
            {
                index++;
                YamlNode value = parseInlineValue(foldPlainScalar(spec.value, line, false, true), spec.tag, line);
                attachComments(value, line);
                storeAnchor(spec.anchor, value, line);
                array.add(value);
            }
        }

        return array;
    }

    private YamlNode parsePlainLine()
    {
        Line line = peek();
        if (line.directive)
        {
            throw error("Unexpected YAML directive", line);
        }
        index++;
        ValueSpec spec = ValueSpec.parse(line.content.trim(), line, tagHandles);
        validateSpec(spec, line);
        if (spec.anchor != null && spec.value.startsWith("- "))
        {
            throw error("YAML anchor cannot precede a block sequence entry", line);
        }
        YamlNode value = spec.alias != null ? resolveAlias(spec.alias, line) :
            spec.value.isEmpty() && (spec.anchor != null || spec.tag != null) ? nextAnchoredValue(line) :
            parseInlineValue(foldPlainScalar(spec.value, line, true, true), spec.tag, line, true);
        value = applyTag(value, spec.tag, spec.value, line);
        attachComments(value, line);
        storeAnchor(spec.anchor, value, line);
        return value;
    }

    private YamlNode nextAnchoredValue(
        Line line)
    {
        skipIgnorable();
        if (index < lines.size())
        {
            Line next = peek();
            if (next.indent > line.indent)
            {
                return parseBlock(next.indent);
            }
            if (next.content.startsWith("|") || next.content.startsWith(">"))
            {
                index++;
                Line scalar = next.indent < line.indent ? next.atIndent(0) : next;
                return parseInlineValue(scalar.content, null, scalar, true);
            }
            if (isSequence(next, next.indent))
            {
                return parseSequence(next.indent);
            }
            if ((line.content.trim().startsWith("&") || line.content.trim().startsWith("!")) &&
                next.indent == line.indent && (isExplicitKey(next) || mappingColon(next.content) != -1))
            {
                return parseMapping(next.indent);
            }
            if (next.indent == line.indent)
            {
                return parsePlainLine();
            }
        }
        return YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column, line.offset);
    }

    private YamlArrayNode parseCompactSequence(
        String text,
        int indent,
        Line line)
    {
        YamlArrayNode array = new YamlArrayNode(line.line, line.column, line.offset);
        String item = text.substring(2).trim();
        if (item.isEmpty())
        {
            array.add(YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column, line.offset));
        }
        else if (isCompactSequence(item))
        {
            array.add(parseCompactSequence(item, indent + 2, line));
        }
        else if (mappingColon(item) != -1)
        {
            YamlObjectNode object = new YamlObjectNode(line.line, line.column, line.offset);
            addCompactMappingEntry(object, item, line);
            array.add(object);
        }
        else
        {
            ValueSpec spec = ValueSpec.parse(item, line, tagHandles);
            validateSpec(spec, line);
            YamlNode value = spec.alias != null ? resolveAlias(spec.alias, line) :
                parseInlineValue(spec.value, spec.tag, line);
            value = applyTag(value, spec.tag, spec.value, line);
            storeAnchor(spec.anchor, value, line);
            array.add(value);
        }
        return array;
    }

    private void addCompactMappingEntry(
        YamlObjectNode object,
        String content,
        Line line)
    {
        int colonAt = mappingColon(content);
        String keyText = content.substring(0, colonAt).trim();
        String valueText = content.substring(colonAt + 1).trim();
        KeySpec key = parseKeySpec(keyText, line);
        ValueSpec spec = ValueSpec.parse(valueText, line, tagHandles);
        validateSpec(spec, line);
        YamlNode value = spec.alias != null ? resolveAlias(spec.alias, line) :
            spec.value.isEmpty() ? YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column, line.offset) :
            parseInlineValue(spec.value, spec.tag, line);
        value = applyTag(value, spec.tag, spec.value, line);
        storeAnchor(spec.anchor, value, line);
        object.add(key.key != null ?
            new YamlEntry(key.key, value, line.line, line.column, line.offset) :
            new YamlEntry(key.name, value, line.line, line.column, line.offset));
    }

    private void addExplicitMappingEntry(
        YamlObjectNode object,
        int indent,
        Line line)
    {
        if (line.content.length() > 1 && line.content.charAt(1) == '\t')
        {
            throw error("Tabs are not supported after explicit key indicators", line);
        }
        String keyText = line.content.substring(2).trim();
        YamlNode keyNode = null;
        String key = null;
        boolean advanced = false;
        if (keyText.startsWith("{") || keyText.startsWith("["))
        {
            if (!config.nonScalarKeys())
            {
                throw error("YAML non-scalar mapping keys are disabled", line);
            }
            keyNode = new FlowParser(keyText, line, tagHandles, config).parse();
        }
        else if (keyText.startsWith("|") || keyText.startsWith(">"))
        {
            index++;
            advanced = true;
            keyNode = parseInlineValue(keyText, null, line);
        }
        else
        {
            if (!keyText.isEmpty() && isFoldablePlainScalar(keyText))
            {
                index++;
                advanced = true;
                keyText = foldPlainScalar(keyText, line, false, true);
            }
            KeySpec keySpec = parseKeySpec(keyText, line);
            key = keySpec.name;
            keyNode = keySpec.key;
        }
        if (!advanced)
        {
            index++;
        }
        skipIgnorable();
        if (index >= lines.size() || lines.get(index).indent != indent || !lines.get(index).content.startsWith(":"))
        {
            if (keyNode != null)
            {
                addNullMappingEntry(object, keyNode, line);
            }
            else
            {
                object.add(new YamlEntry(key, YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column, line.offset),
                    line.line, line.column, line.offset));
            }
            return;
        }

        Line valueLine = lines.get(index);
        if (valueLine.content.length() > 1 && valueLine.content.charAt(1) == '\t')
        {
            throw error("Tabs are not supported after explicit value indicators", valueLine);
        }
        String valueText = valueLine.content.length() == 1 ? "" : valueLine.content.substring(1).trim();
        if (keyNode != null)
        {
            addMappingEntry(object, keyNode, valueText, indent, valueLine);
        }
        else
        {
            addMappingEntry(object, key, valueText, indent, valueLine);
        }
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
        KeySpec key = parseKeySpec(keyText, line);
        if (key.key != null)
        {
            addMappingEntry(object, key.key, valueText, indent, line);
        }
        else
        {
            addMappingEntry(object, key.name, valueText, indent, line);
        }
    }

    private void addMappingEntry(
        YamlObjectNode object,
        String key,
        String valueText,
        int indent,
        Line line)
    {
        ValueSpec spec = ValueSpec.parse(valueText, line, tagHandles);
        validateSpec(spec, line);
        index++;
        Line valueLine = line.indent < indent ? line.atIndent(indent) : line;

        YamlNode value = spec.alias != null ?
            resolveAlias(spec.alias, line) :
            spec.value.isEmpty() ? nextNestedValue(indent, line) :
            isCompactSequence(spec.value) ? parseCompactSequenceValue(spec.value, indent, line) :
            parseInlineValue(foldPlainScalar(spec.value, valueLine, false, false), spec.tag, valueLine, false);
        if (spec.anchor != null && value.anchor != null)
        {
            throw error("YAML node cannot define multiple anchors", line);
        }

        value = applyTag(value, spec.tag, spec.value, line);
        attachComments(value, line);
        storeAnchor(spec.anchor, value, line);

        object.add(new YamlEntry(key, value, line.line, line.column, line.offset));
    }

    private void addMappingEntry(
        YamlObjectNode object,
        YamlNode key,
        String valueText,
        int indent,
        Line line)
    {
        ValueSpec spec = ValueSpec.parse(valueText, line, tagHandles);
        validateSpec(spec, line);
        index++;
        Line valueLine = line.indent < indent ? line.atIndent(indent) : line;

        YamlNode value = spec.alias != null ?
            resolveAlias(spec.alias, line) :
            spec.value.isEmpty() ? nextNestedValue(indent, line) :
            isCompactSequence(spec.value) ? parseCompactSequenceValue(spec.value, indent, line) :
            parseInlineValue(foldPlainScalar(spec.value, valueLine, false, false), spec.tag, valueLine, false);
        if (spec.anchor != null && value.anchor != null)
        {
            throw error("YAML node cannot define multiple anchors", line);
        }

        value = applyTag(value, spec.tag, spec.value, line);
        attachComments(value, line);
        storeAnchor(spec.anchor, value, line);
        object.add(new YamlEntry(key, value, line.line, line.column, line.offset));
    }

    private YamlArrayNode parseCompactSequenceValue(
        String text,
        int indent,
        Line line)
    {
        YamlArrayNode value = parseCompactSequence(text, indent + 2, line);
        while (index < lines.size() && isSequence(peek(), indent + 2))
        {
            YamlArrayNode continuation = parseSequence(indent + 2);
            continuation.values.forEach(value::add);
        }
        return value;
    }

    private static void addNullMappingEntry(
        YamlObjectNode object,
        YamlNode key,
        Line line)
    {
        YamlNode value = YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column, line.offset);
        object.add(new YamlEntry(key, value, line.line, line.column, line.offset));
    }

    private String foldPlainScalar(
        String first,
        Line firstLine,
        boolean allowSameIndent,
        boolean allowIndentedSequence)
    {
        if (!isFoldablePlainScalar(first))
        {
            return first;
        }

        StringBuilder value = new StringBuilder(trimPlainLine(first));
        int indent = firstLine.indent;
        boolean commentTerminated = firstLine.comment != null;
        while (index < lines.size())
        {
            int blankAt = index;
            int blankLines = 0;
            boolean blankComment = false;
            while (index < lines.size() && peek().blank)
            {
                blankComment |= peek().comment != null;
                blankLines++;
                index++;
            }

            if (index >= lines.size() ||
                !isPlainScalarContinuation(peek(), indent, allowSameIndent, allowIndentedSequence))
            {
                index = blankAt;
                break;
            }
            if (commentTerminated || blankComment)
            {
                throw error("Plain scalar cannot continue after a comment", peek());
            }

            Line line = peek();
            index++;
            if (blankLines == 0)
            {
                value.append(' ');
            }
            else
            {
                appendLineBreaks(value, blankLines);
            }
            value.append(trimPlainLine(line.content));
            commentTerminated = line.comment != null;
        }

        return value.toString();
    }

    private static boolean isFoldablePlainScalar(
        String text)
    {
        return !text.isEmpty() &&
            !text.startsWith("\"") &&
            !text.startsWith("'") &&
            !text.startsWith("[") &&
            !text.startsWith("{") &&
            !text.startsWith("|") &&
            !text.startsWith(">") &&
            !text.startsWith("*");
    }

    private static boolean isPlainScalarContinuation(
        Line line,
        int indent,
        boolean allowSameIndent,
        boolean allowIndentedSequence)
    {
        if (isMarker(line.content, "---") || isMarker(line.content, "...") || line.indent < indent)
        {
            return false;
        }
        if (line.indent == indent)
        {
            return allowSameIndent && !isSequence(line, indent) && !isExplicitKey(line) && mappingColon(line.content) == -1;
        }
        if (!allowIndentedSequence && isSequence(line, line.indent))
        {
            return false;
        }
        return mappingColon(line.content) == -1;
    }

    private static String trimPlainLine(
        String text)
    {
        return text.strip();
    }

    private static boolean isMarker(
        String content,
        String marker)
    {
        return content.equals(marker) ||
            content.startsWith(marker) && content.length() > marker.length() &&
                Character.isWhitespace(content.charAt(marker.length()));
    }

    private YamlNode nextNestedValue(
        int indent,
        Line line)
    {
        skipIgnorable();
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

    private YamlNode nextNestedSequenceValue(
        int indent,
        Line line)
    {
        skipIgnorable();
        if (index < lines.size())
        {
            Line next = peek();
            if (next.indent > indent)
            {
                return parseBlock(next.indent);
            }
        }

        return YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column, line.offset);
    }

    private YamlNode parseInlineValue(
        String text,
        String tag,
        Line line)
    {
        return parseInlineValue(text, tag, line, false);
    }

    private YamlNode parseInlineValue(
        String text,
        String tag,
        Line line,
        boolean allowSameIndentBlockScalar)
    {
        if (text.startsWith("\"") || text.startsWith("'"))
        {
            text = collectQuotedText(text, line, allowSameIndentBlockScalar);
        }
        if (text.startsWith("|") || text.startsWith(">"))
        {
            if (!config.blockScalars())
            {
                throw error("YAML block scalars are disabled", line);
            }
            return parseBlockScalar(text, tag, line, allowSameIndentBlockScalar);
        }
        if (text.startsWith("{") || text.startsWith("["))
        {
            if (!config.flowCollections())
            {
                throw error("YAML flow collections are disabled", line);
            }
            String flow = collectFlowText(text, line);
            return applyTag(new FlowParser(flow, line, tagHandles, config).parse(), tag, flow, line);
        }
        return parseScalar(text, tag, line);
    }

    private String collectQuotedText(
        String text,
        Line line,
        boolean allowSameIndent)
    {
        int rawAt = line.raw.indexOf(text);
        StringBuilder quoted = new StringBuilder(rawAt == -1 ? text : line.raw.substring(rawAt));
        while (!isQuotedComplete(quoted.toString()) && index < lines.size())
        {
            Line next = lines.get(index);
            if (!next.blank && ("---".equals(next.content) || "...".equals(next.content) ||
                next.content.startsWith("--- ") || next.content.startsWith("... ")))
            {
                throw error("Unterminated quoted scalar", line);
            }
            if (!allowSameIndent && next.raw.startsWith("\t"))
            {
                throw error("Tabs are not supported in quoted scalar indentation", next);
            }
            if (!allowSameIndent && !next.blank && next.indent <= line.indent)
            {
                throw error("Wrong indented quoted scalar", next);
            }
            quoted.append('\n');
            quoted.append(next.raw.substring(Math.min(next.indent, next.raw.length())));
            index++;
        }
        return quoted.toString();
    }

    private static boolean isQuotedComplete(
        String text)
    {
        char quote = text.charAt(0);
        boolean escaped = false;
        for (int i = 1; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (quote == '"' && escaped)
            {
                escaped = false;
            }
            else if (quote == '"' && c == '\\')
            {
                escaped = true;
            }
            else if (quote == '\'' && c == '\'' && i + 1 < text.length() && text.charAt(i + 1) == '\'')
            {
                i++;
            }
            else if (c == quote)
            {
                return i == text.length() - 1;
            }
        }
        return false;
    }

    private String collectFlowText(
        String text,
        Line line)
    {
        StringBuilder flow = new StringBuilder(text);
        while (!isFlowComplete(flow.toString()) && index < lines.size())
        {
            Line next = lines.get(index);
            if (!next.blank && ("---".equals(next.content) || "...".equals(next.content)))
            {
                throw error("Unterminated flow collection", line);
            }
            if (!next.blank && next.indent <= line.indent && !next.content.startsWith("]") && !next.content.startsWith("}"))
            {
                throw error("Wrong indented flow collection", next);
            }
            if (!next.blank && next.raw.startsWith("\t"))
            {
                throw error("Tabs are not supported in flow indentation", next);
            }
            flow.append('\n');
            flow.append(stripLeadingWhitespace(next.raw));
            index++;
        }
        return flow.toString();
    }

    private static boolean isFlowComplete(
        String text)
    {
        boolean single = false;
        boolean doub = false;
        boolean escaped = false;
        boolean comment = false;
        int depth = 0;

        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (comment)
            {
                comment = c != '\n';
            }
            else if (doub)
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
                case '#' -> comment = true;
                case '\'' -> single = true;
                case '"' -> doub = true;
                case '{', '[' -> depth++;
                case '}', ']' -> depth--;
                default ->
                {
                    // continue
                }
                }
            }
        }

        return depth == 0 && !single && !doub;
    }

    private YamlScalarNode parseBlockScalar(
        String indicator,
        String tag,
        Line line,
        boolean allowSameIndent)
    {
        char style = indicator.charAt(0);
        char chomping = 0;
        int explicitIndent = -1;
        for (int i = 1; i < indicator.length(); i++)
        {
            char c = indicator.charAt(i);
            if (c == '-' || c == '+')
            {
                chomping = c;
            }
            else if (c >= '1' && c <= '9')
            {
                explicitIndent = c - '0';
            }
            else if (!Character.isWhitespace(c))
            {
                throw error("Malformed block scalar indicator", line);
            }
        }

        int contentIndent = explicitIndent != -1 ? line.indent + explicitIndent :
            detectBlockScalarIndent(line.indent, allowSameIndent);
        List<BlockScalarLine> values = new ArrayList<>();
        boolean seenContent = false;
        while (index < lines.size())
        {
            Line next = lines.get(index);
            if (next.offset - next.indent >= source.length())
            {
                break;
            }
            int nextIndent = spaceIndent(next.raw);
            boolean spaceOnly = isSpaceOnly(next.raw);
            if (!spaceOnly && nextIndent < contentIndent)
            {
                break;
            }
            if (!allowSameIndent && !spaceOnly && nextIndent <= line.indent)
            {
                break;
            }
            if (!seenContent && spaceOnly && !next.raw.isEmpty() && nextIndent > contentIndent &&
                hasFollowingBlockScalarContentOrComment(contentIndent))
            {
                throw error("Wrong indented block scalar line", next);
            }

            values.add(new BlockScalarLine(
                next.raw.length() >= contentIndent ? next.raw.substring(contentIndent) : "",
                !spaceOnly && (nextIndent > contentIndent ||
                    next.raw.length() > contentIndent && next.raw.charAt(contentIndent) == '\t')));
            seenContent |= !spaceOnly;
            index++;
        }

        String value = style == '|' ? literal(values) : folded(values);
        if (chomping == '-')
        {
            value = stripTrailingLineBreaks(value);
        }
        else if (chomping != '+')
        {
            value = clipTrailingLineBreaks(value);
        }

        YamlScalarNode scalar = YamlScalarNode.string(value, line.line, line.column, line.offset);
        scalar.style = indicator;
        return (YamlScalarNode) applyTag(scalar, tag, indicator, line);
    }

    private int detectBlockScalarIndent(
        int parentIndent,
        boolean allowSameIndent)
    {
        for (int i = index; i < lines.size(); i++)
        {
            Line line = lines.get(i);
            if (!isSpaceOnly(line.raw))
            {
                int indent = spaceIndent(line.raw);
                if (!allowSameIndent && indent <= parentIndent)
                {
                    if (isSequence(line, indent) || isExplicitKey(line) || mappingColon(line.content) != -1 ||
                        isMarker(line.content, "---") || isMarker(line.content, "..."))
                    {
                        break;
                    }
                    else
                    {
                        throw error("Expected indented block scalar content", line);
                    }
                }
                return indent;
            }
        }
        int blankIndent = -1;
        for (int i = index; i < lines.size(); i++)
        {
            Line line = lines.get(i);
            if (!isSpaceOnly(line.raw))
            {
                break;
            }
            if (!line.raw.isEmpty())
            {
                blankIndent = Math.max(blankIndent, spaceIndent(line.raw));
            }
        }
        if (blankIndent != -1)
        {
            return blankIndent;
        }
        return allowSameIndent ? parentIndent : parentIndent + 2;
    }

    private boolean hasFollowingBlockScalarContentOrComment(
        int contentIndent)
    {
        for (int i = index + 1; i < lines.size(); i++)
        {
            Line line = lines.get(i);
            if (!line.raw.isBlank() || line.comment != null)
            {
                return true;
            }
            if (spaceIndent(line.raw) < contentIndent)
            {
                return false;
            }
        }
        return false;
    }

    private static boolean isSpaceOnly(
        String raw)
    {
        for (int i = 0; i < raw.length(); i++)
        {
            if (raw.charAt(i) != ' ')
            {
                return false;
            }
        }
        return true;
    }

    private static int spaceIndent(
        String raw)
    {
        int indent = 0;
        while (indent < raw.length() && raw.charAt(indent) == ' ')
        {
            indent++;
        }
        return indent;
    }

    private static String literal(
        List<BlockScalarLine> lines)
    {
        StringBuilder value = new StringBuilder();
        for (BlockScalarLine line : lines)
        {
            value.append(line.value);
            value.append('\n');
        }
        return value.toString();
    }

    private static String folded(
        List<BlockScalarLine> lines)
    {
        StringBuilder value = new StringBuilder();
        boolean previousMoreIndented = false;
        boolean first = true;
        int blankLines = 0;
        for (BlockScalarLine line : lines)
        {
            if (line.value.isEmpty())
            {
                blankLines++;
            }
            else
            {
                if (blankLines != 0)
                {
                    appendLineBreaks(value, blankLines + (!first && (previousMoreIndented || line.moreIndented) ? 1 : 0));
                }
                else if (!first)
                {
                    value.append(previousMoreIndented || line.moreIndented ? '\n' : ' ');
                }
                value.append(line.value);
                previousMoreIndented = line.moreIndented;
                first = false;
                blankLines = 0;
            }
        }
        appendLineBreaks(value, blankLines);
        return value.toString();
    }

    private static void appendLineBreaks(
        StringBuilder value,
        int count)
    {
        for (int i = 0; i < count; i++)
        {
            value.append('\n');
        }
    }

    private static String stripTrailingLineBreaks(
        String value)
    {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '\n')
        {
            end--;
        }
        return value.substring(0, end);
    }

    private static String clipTrailingLineBreaks(
        String value)
    {
        value = stripTrailingLineBreaks(value);
        return value.isEmpty() ? value : value + "\n";
    }

    private Matcher numberMatcher()
    {
        if (numberMatcher == null)
        {
            numberMatcher = NUMBER_PATTERN.matcher("");
        }
        return numberMatcher;
    }

    private Matcher hexIntegerMatcher()
    {
        if (hexIntegerMatcher == null)
        {
            hexIntegerMatcher = HEX_INTEGER_PATTERN.matcher("");
        }
        return hexIntegerMatcher;
    }

    private YamlScalarNode parseScalar(
        String text,
        String tag,
        Line line)
    {
        if (text.startsWith("\"") || text.startsWith("'"))
        {
            YamlScalarNode scalar = YamlScalarNode.string(unquote(text, line), line.line, line.column, line.offset);
            scalar.style = text.startsWith("\"") ? "\"" : "'";
            return (YamlScalarNode) applyTag(scalar, tag, text, line);
        }
        if (mappingColon(text) != -1)
        {
            throw error("Invalid mapping in plain scalar", line);
        }
        if (!config.scalarResolution())
        {
            return (YamlScalarNode) applyTag(YamlScalarNode.string(text, line.line, line.column, line.offset), tag, text, line);
        }

        rejectNonFinite(text, line);
        String lower = text.toLowerCase(Locale.ROOT);
        YamlNode value = switch (lower)
        {
        case "true" -> YamlScalarNode.literal(YamlScalarType.TRUE, line.line, line.column, line.offset);
        case "false" -> YamlScalarNode.literal(YamlScalarType.FALSE, line.line, line.column, line.offset);
        case "null", "~" -> YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column, line.offset);
        default -> hexIntegerMatcher().reset(text).matches() || numberMatcher().reset(text).matches() ?
            YamlScalarNode.number(numberText(text), line.line, line.column, line.offset) :
            YamlScalarNode.string(text, line.line, line.column, line.offset);
        };
        return (YamlScalarNode) applyTag(value, tag, text, line);
    }

    private KeySpec parseKeySpec(
        String text,
        Line line)
    {
        if (text.isEmpty())
        {
            throw error("Expected mapping key", line);
        }
        if (text.startsWith("\"") || text.startsWith("'"))
        {
            return new KeySpec(unquote(text, line), null);
        }
        if (text.startsWith("{") || text.startsWith("["))
        {
            if (!config.nonScalarKeys())
            {
                throw error("YAML non-scalar mapping keys are disabled", line);
            }
            return new KeySpec(null, new FlowParser(text, line, tagHandles, config).parse());
        }
        ValueSpec spec = ValueSpec.parse(text, line, tagHandles);
        validateSpec(spec, line);
        if (spec.alias != null || spec.anchor != null || spec.tag != null)
        {
            YamlNode key = spec.alias != null ? resolveAlias(spec.alias, line) :
                spec.value.startsWith("{") || spec.value.startsWith("[") ?
                    applyTag(new FlowParser(spec.value, line, tagHandles, config).parse(), spec.tag, spec.value, line) :
                    parseScalar(spec.value, spec.tag, line);
            storeAnchor(spec.anchor, key, line);
            return new KeySpec(null, key);
        }
        return new KeySpec(spec.value, null);
    }

    private YamlNode applyTag(
        YamlNode value,
        String tag,
        String text,
        Line line)
    {
        if (tag != null)
        {
            if (!config.tags())
            {
                throw error("YAML tags are disabled", line);
            }
            value.tag = tag;
        }
        return value;
    }

    private static String scalarText(
        YamlNode value,
        String text)
    {
        if (value instanceof YamlScalarNode scalar)
        {
            return scalar.value != null ? scalar.value : switch (scalar.type)
            {
            case TRUE -> "true";
            case FALSE -> "false";
            case NULL -> text.isEmpty() ? "" : "null";
            default -> text;
            };
        }
        return text;
    }

    private static String numberText(
        String text)
    {
        boolean negative = text.startsWith("-");
        String scalar = negative ? text.substring(1) : text;
        if (scalar.startsWith("0x"))
        {
            String value = new BigInteger(scalar.substring(2), 16).toString();
            return negative ? "-" + value : value;
        }
        return text;
    }

    private void storeAnchor(
        String anchor,
        YamlNode value,
        Line line)
    {
        if (anchor != null)
        {
            if (!config.anchors())
            {
                throw error("YAML anchors are disabled", line);
            }
            value.anchor = anchor;
        }
    }

    private YamlNode resolveAlias(
        String alias,
        Line line)
    {
        if (!config.aliases())
        {
            throw error("YAML aliases are disabled", line);
        }
        YamlNode reference = YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column, line.offset);
        reference.alias = alias;
        return reference;
    }

    static YamlNode copy(
        YamlNode value)
    {
        if (value instanceof YamlScalarNode scalar)
        {
            YamlScalarNode copy = new YamlScalarNode(scalar.type, scalar.value, scalar.line, scalar.column, scalar.offset);
            copy.source = scalar.source;
            copy.tag = scalar.tag;
            copy.anchor = scalar.anchor;
            copy.alias = scalar.alias;
            copy.style = scalar.style;
            copy.leadingComments = scalar.leadingComments;
            copy.lineComment = scalar.lineComment;
            return copy;
        }
        if (value instanceof YamlArrayNode array)
        {
            YamlArrayNode copy = new YamlArrayNode(array.line, array.column, array.offset);
            copy.source = array.source;
            copy.tag = array.tag;
            copy.anchor = array.anchor;
            copy.alias = array.alias;
            copy.style = array.style;
            copy.leadingComments = array.leadingComments;
            copy.lineComment = array.lineComment;
            for (YamlNode element : array.values)
            {
                copy.add(copy(element));
            }
            return copy;
        }

        YamlObjectNode object = (YamlObjectNode) value;
        YamlObjectNode copy = new YamlObjectNode(object.line, object.column, object.offset);
        copy.source = object.source;
        copy.tag = object.tag;
        copy.anchor = object.anchor;
        copy.alias = object.alias;
        copy.style = object.style;
        copy.leadingComments = object.leadingComments;
        copy.lineComment = object.lineComment;
        for (YamlEntry entry : object.entries)
        {
            copy.add(entry.key != null ?
                new YamlEntry(copy(entry.key), copy(entry.value), entry.line, entry.column, entry.offset) :
                new YamlEntry(entry.name, copy(entry.value), entry.line, entry.column, entry.offset));
        }
        return copy;
    }

    private Line peek()
    {
        return lines.get(index);
    }

    private void skipIgnorable()
    {
        while (index < lines.size() && lines.get(index).ignorable)
        {
            Line line = lines.get(index);
            if (line.comment != null && line.content.isBlank())
            {
                if (!config.comments())
                {
                    throw error("YAML comments are disabled", line);
                }
                if (config.preserveComments())
                {
                    comments.add(line.comment);
                }
            }
            index++;
        }
    }

    private void attachComments(
        YamlNode node,
        Line line)
    {
        List<String> leading = config.preserveComments() ? takeComments() : null;
        if (leading != null)
        {
            node.leadingComments = leading;
        }
        if (line.comment != null && !line.content.isBlank())
        {
            if (!config.comments())
            {
                throw error("YAML comments are disabled", line);
            }
            if (config.preserveComments())
            {
                node.lineComment = line.comment;
            }
        }
    }

    private void validateSpec(
        ValueSpec spec,
        Line line)
    {
        if (spec.anchor != null && !config.anchors())
        {
            throw error("YAML anchors are disabled", line);
        }
        if (spec.alias != null && !config.aliases())
        {
            throw error("YAML aliases are disabled", line);
        }
        if (spec.tag != null && !config.tags())
        {
            throw error("YAML tags are disabled", line);
        }
        if (spec.anchor != null && spec.alias != null)
        {
            throw error("YAML alias cannot also define an anchor", line);
        }
    }

    private List<String> takeComments()
    {
        List<String> leading = comments.isEmpty() ? null : List.copyOf(comments);
        comments.clear();
        return leading;
    }

    private static boolean isSequence(
        Line line,
        int indent)
    {
        return line.indent == indent && (line.content.equals("-") ||
            line.content.length() > 1 && line.content.charAt(0) == '-' &&
                Character.isWhitespace(line.content.charAt(1)));
    }

    private static boolean isCompactSequence(
        String text)
    {
        return text.length() > 1 && text.charAt(0) == '-' && Character.isWhitespace(text.charAt(1));
    }

    private static boolean tabIndented(
        Line line)
    {
        return line.raw.substring(0, Math.min(line.indent, line.raw.length())).indexOf('\t') != -1;
    }

    private static boolean isExplicitKey(
        Line line)
    {
        return line.content.equals("?") || line.content.length() > 1 && line.content.charAt(0) == '?' &&
            Character.isWhitespace(line.content.charAt(1));
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
                case '\'' ->
                {
                    if (isQuotedTokenStart(text, i))
                    {
                        single = true;
                    }
                }
                case '"' ->
                {
                    if (isQuotedTokenStart(text, i))
                    {
                        doub = true;
                    }
                }
                case '{', '[' -> depth++;
                case '}', ']' ->
                {
                    if (depth > 0)
                    {
                        depth--;
                    }
                }
                case ':' ->
                {
                    if (depth == 0 && (i + 1 == text.length() || Character.isWhitespace(text.charAt(i + 1))))
                    {
                        if (!isAnchorOrAliasTokenColon(text, i))
                        {
                            return i;
                        }
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

    private static boolean isAnchorOrAliasTokenColon(
        String text,
        int colonAt)
    {
        int start = colonAt - 1;
        while (start >= 0 && !Character.isWhitespace(text.charAt(start)))
        {
            start--;
        }
        start++;
        return start < colonAt && (text.charAt(start) == '&' || text.charAt(start) == '*');
    }

    private static boolean isQuotedTokenStart(
        String text,
        int index)
    {
        char previous = index == 0 ? 0 : text.charAt(index - 1);
        return index == 0 || Character.isWhitespace(previous) ||
            previous == '[' || previous == '{' || previous == ',' || previous == ':';
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
        if (text.indexOf('\n') != -1)
        {
            text = quote + foldQuotedLines(text.substring(1, text.length() - 1)) + quote;
        }

        return quote == '\'' ? unquoteSingle(text, line) : unquoteDouble(text, line);
    }

    private static String foldQuotedLines(
        String text)
    {
        StringBuilder folded = new StringBuilder();
        String[] lines = text.split("\\R", -1);
        if (lines.length == 0)
        {
            return "";
        }

        folded.append(stripTrailingWhitespace(lines[0]));
        int blankLines = 0;
        for (int i = 1; i < lines.length; i++)
        {
            boolean last = i == lines.length - 1;
            String line = stripLeadingWhitespace(lines[i]);
            boolean blank = line.isEmpty();

            if (blank)
            {
                if (last)
                {
                    if (blankLines > 0)
                    {
                        appendLineBreaks(folded, blankLines);
                    }
                    else
                    {
                        folded.append(' ');
                    }
                }
                else
                {
                    blankLines++;
                }
                continue;
            }

            boolean separated = false;
            if (blankLines > 0)
            {
                appendLineBreaks(folded, blankLines);
                blankLines = 0;
                separated = true;
            }

            String normalized = last ? line : stripTrailingWhitespace(line);
            if (folded.length() > 0 && folded.charAt(folded.length() - 1) == '\\')
            {
                folded.setLength(folded.length() - 1);
            }
            else if (!separated)
            {
                folded.append(' ');
            }
            folded.append(normalized);
        }
        return folded.toString();
    }

    private static String stripLeadingWhitespace(
        String text)
    {
        int start = 0;
        while (start < text.length() && (text.charAt(start) == ' ' || text.charAt(start) == '\t'))
        {
            start++;
        }
        return text.substring(start);
    }

    private static String stripTrailingWhitespace(
        String text)
    {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t'))
        {
            if (text.charAt(end - 1) == '\t' && end > 1 && text.charAt(end - 2) == '\\')
            {
                break;
            }
            end--;
        }
        return text.substring(0, end);
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
                i = appendEscape(value, text, i, line);
            }
            else
            {
                value.append(c);
            }
        }
        return value.toString();
    }

    private static int appendEscape(
        StringBuilder value,
        String text,
        int at,
        Line line)
    {
        char escaped = text.charAt(at);
        switch (escaped)
        {
        case '0' -> value.append('\0');
        case 'a' -> value.append('\u0007');
        case '"' -> value.append('"');
        case '\\' -> value.append('\\');
        case '/' -> value.append('/');
        case 'b' -> value.append('\b');
        case 'e' -> value.append('\u001b');
        case 'f' -> value.append('\f');
        case 'n' -> value.append('\n');
        case 'r' -> value.append('\r');
        case 't' -> value.append('\t');
        case '\t' -> value.append('\t');
        case 'v' -> value.append('\u000b');
        case ' ' -> value.append(' ');
        case '_' -> value.append('\u00a0');
        case 'N' -> value.append('\u0085');
        case 'L' -> value.append('\u2028');
        case 'P' -> value.append('\u2029');
        case 'x' -> at = appendHexEscape(value, text, at, 2, line);
        case 'u' -> at = appendHexEscape(value, text, at, 4, line);
        case 'U' -> at = appendHexEscape(value, text, at, 8, line);
        default -> throw error("Unsupported escape sequence", line);
        }
        return at;
    }

    private static int appendHexEscape(
        StringBuilder value,
        String text,
        int at,
        int digits,
        Line line)
    {
        if (at + digits >= text.length())
        {
            throw error("Unterminated unicode escape sequence", line);
        }
        try
        {
            int codePoint = Integer.parseUnsignedInt(text.substring(at + 1, at + 1 + digits), 16);
            value.appendCodePoint(codePoint);
            return at + digits;
        }
        catch (IllegalArgumentException ex)
        {
            throw error("Invalid unicode escape sequence", line);
        }
    }

    private static void rejectNonFinite(
        String text,
        Line line)
    {
        String lower = text.toLowerCase(Locale.ROOT);
        if (".nan".equals(lower) || ".inf".equals(lower) || "+.inf".equals(lower) || "-.inf".equals(lower))
        {
            throw error("Non-finite YAML numbers are not valid JSON values", line);
        }
    }

    private static String stripComment(
        String text)
    {
        int index = commentIndex(text);
        return index == -1 ? text : text.substring(0, index);
    }

    private static String comment(
        String text)
    {
        int index = commentIndex(text);
        return index == -1 ? null : text.substring(index).stripTrailing();
    }

    private static int commentIndex(
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
                return i;
            }
        }

        return -1;
    }

    private static YamlParseException error(
        String message,
        Line line)
    {
        return new YamlParseException(message, new YamlLocation(line.line, line.column, line.offset));
    }

    public static final class Result
    {
        public final YamlNode node;
        public final YamlLocation end;

        Result(
            YamlNode node,
            YamlLocation end)
        {
            this.node = node;
            this.end = end;
        }
    }

    private static final class DocumentScanner
    {
        private final List<Line> all;
        private final String text;
        private final YamlConfiguration config;

        private DocumentScanner(
            String text,
            YamlConfiguration config)
        {
            this.text = text;
            this.config = config;
            this.all = lines(text);
        }

        private Document scan()
        {
            int start = 0;
            Map<String, String> tagHandles = defaultTagHandles();
            boolean directives = false;
            boolean yamlDirective = false;
            Line startContent = null;
            while (start < all.size())
            {
                Line line = all.get(start);
                if (line.blank)
                {
                    start++;
                }
                else if (line.directive)
                {
                    if (!config.directives())
                    {
                        throw error("YAML directives are disabled", line);
                    }
                    directives = true;
                    yamlDirective = readDirective(line, tagHandles, yamlDirective);
                    start++;
                }
                else if (isDocumentStart(line))
                {
                    if (!config.documentMarkers())
                    {
                        throw error("YAML document markers are disabled", line);
                    }
                    String content = markerContent(line, "---");
                    if (!content.isEmpty())
                    {
                        if (content.startsWith("&") && mappingColon(content) != -1)
                        {
                            throw error("YAML document start cannot anchor an implicit mapping", line);
                        }
                        startContent = contentLine(line, content);
                    }
                    start++;
                    break;
                }
                else if (isDocumentEnd(line))
                {
                    if (!config.documentMarkers())
                    {
                        throw error("YAML document markers are disabled", line);
                    }
                    if (directives)
                    {
                        throw error("YAML directives require an explicit document", line);
                    }
                    start++;
                }
                else
                {
                    if (directives)
                    {
                        throw error("YAML directives require an explicit document", line);
                    }
                    break;
                }
            }
            if (directives && start >= all.size())
            {
                Line line = all.get(start - 1);
                throw error("YAML directives require an explicit document", line);
            }
            int end = start;
            int flowDepth = startContent == null ? 0 : flowDepth(startContent.content, 0);
            long endOffset = text.length();
            while (end < all.size())
            {
                Line line = all.get(end);
                boolean topLevel = flowDepth == 0;
                if (!line.blank && topLevel && (isDocumentStart(line) || isDocumentEnd(line)))
                {
                    if (!config.documentMarkers())
                    {
                        throw error("YAML document markers are disabled", line);
                    }
                    endOffset = isDocumentStart(line) ? line.offset : line.afterOffset;
                    break;
                }
                flowDepth = flowDepth(line.raw.substring(Math.min(line.indent, line.raw.length())), flowDepth);
                end++;
            }

            List<Line> documentLines = new ArrayList<>();
            if (startContent != null)
            {
                documentLines.add(startContent);
            }
            for (int i = start; i < end; i++)
            {
                documentLines.add(all.get(i));
            }
            String source = text.substring(0, (int) Math.min(endOffset, text.length()));
            return new Document(documentLines, new YamlLocation(1, 1, endOffset), source, tagHandles, config);
        }

        private static Map<String, String> defaultTagHandles()
        {
            Map<String, String> tagHandles = new HashMap<>();
            tagHandles.put("!!", "tag:yaml.org,2002:");
            return tagHandles;
        }

        private static boolean readDirective(
            Line line,
            Map<String, String> tagHandles,
            boolean yamlDirective)
        {
            if (line.content.startsWith("%YAML"))
            {
                String[] parts = line.content.split("\\s+");
                if (parts.length != 2 || !parts[1].matches("[0-9]+\\.[0-9]+"))
                {
                    throw error("Malformed YAML directive", line);
                }
                if (yamlDirective)
                {
                    throw error("Duplicate YAML directive", line);
                }
                return true;
            }
            else if (line.content.startsWith("%TAG "))
            {
                String[] parts = line.content.split("\\s+");
                if (parts.length != 3)
                {
                    throw error("Malformed YAML TAG directive", line);
                }
                if (!parts[1].startsWith("!") || !parts[1].endsWith("!"))
                {
                    throw error("Malformed YAML TAG handle", line);
                }
                tagHandles.put(parts[1], parts[2]);
            }
            return yamlDirective;
        }

        private static boolean isDocumentStart(
            Line line)
        {
            return isMarker(line.content, "---");
        }

        private static boolean isDocumentEnd(
            Line line)
        {
            return line.content.equals("...");
        }

        private static boolean isMarker(
            String content,
            String marker)
        {
            return content.equals(marker) ||
                content.startsWith(marker) && content.length() > marker.length() &&
                    Character.isWhitespace(content.charAt(marker.length()));
        }

        private static int flowDepth(
            String content,
            int depth)
        {
            boolean single = false;
            boolean doub = false;
            boolean escaped = false;
            boolean comment = false;
            for (int i = 0; i < content.length(); i++)
            {
                char c = content.charAt(i);
                if (comment)
                {
                    break;
                }
                else if (doub)
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
                        if (i + 1 < content.length() && content.charAt(i + 1) == '\'')
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
                    case '#' ->
                    {
                        if (i == 0 || Character.isWhitespace(content.charAt(i - 1)))
                        {
                            comment = true;
                        }
                    }
                    case '\'' -> single = true;
                    case '"' -> doub = true;
                    case '{', '[' -> depth++;
                    case '}', ']' -> depth = Math.max(0, depth - 1);
                    default ->
                    {
                        // continue
                    }
                    }
                }
            }
            return depth;
        }

        private static String markerContent(
            Line line,
            String marker)
        {
            return line.content.length() == marker.length() ? "" :
                line.content.substring(marker.length()).stripLeading();
        }

        private static Line contentLine(
            Line line,
            String content)
        {
            int rawAt = line.raw.indexOf(content);
            int column = rawAt == -1 ? line.column + 4 : rawAt + 1;
            long offset = rawAt == -1 ? line.offset + 4 : line.offset - line.indent + rawAt;
            return new Line(content, 0, content, line.comment, content.isBlank(), false, content.isBlank(),
                line.line, column, offset, line.afterOffset);
        }

        private static List<Line> lines(
            String text)
        {
            List<Line> lines = new ArrayList<>();
            int offset = 0;
            int lineNumber = 1;

            for (String raw0 : text.split("\n", -1))
            {
                String raw = raw0.endsWith("\r") ? raw0.substring(0, raw0.length() - 1) : raw0;
                int indent = indent(raw, lineNumber, offset);
                String trimmed = raw.substring(indent);
                String content = stripComment(trimmed).stripTrailing();
                String comment = comment(trimmed);
                boolean blank = content.isBlank();
                boolean directive = content.startsWith("%");
                boolean ignorable = blank;
                lines.add(new Line(raw, indent, content, comment, blank, directive, ignorable,
                    lineNumber, indent + 1, offset + indent, offset + raw0.length() + 1));

                offset += raw0.length() + 1;
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
                else if (c != '\t')
                {
                    break;
                }
                else
                {
                    indent++;
                }
            }
            return indent;
        }
    }

    private static final class Document
    {
        final List<Line> lines;
        final YamlLocation end;
        final String source;
        final Map<String, String> tagHandles;
        final YamlConfiguration config;

        private Document(
            List<Line> lines,
            YamlLocation end,
            String source,
            Map<String, String> tagHandles,
            YamlConfiguration config)
        {
            this.lines = lines;
            this.end = end;
            this.source = source;
            this.tagHandles = tagHandles;
            this.config = config;
        }
    }

    private static final class ValueSpec
    {
        final String anchor;
        final String alias;
        final String tag;
        final String value;

        private ValueSpec(
            String anchor,
            String alias,
            String tag,
            String value)
        {
            this.anchor = anchor;
            this.alias = alias;
            this.tag = tag;
            this.value = value;
        }

        static ValueSpec parse(
            String text,
            Line line,
            Map<String, String> tagHandles)
        {
            String anchor = null;
            String alias = null;
            String tag = null;
            String value = text.trim();

            boolean progress;
            do
            {
                progress = false;
                if (value.startsWith("&"))
                {
                    int end = tokenEnd(value, 1);
                    anchor = value.substring(1, end);
                    if (anchor.isEmpty())
                    {
                        throw error("Expected YAML anchor name", line);
                    }
                    value = value.substring(end).trim();
                    progress = true;
                }
                else if (value.startsWith("*"))
                {
                    int end = tokenEnd(value, 1);
                    alias = value.substring(1, end);
                    if (alias.isEmpty())
                    {
                        throw error("Expected YAML alias name", line);
                    }
                    value = value.substring(end).trim();
                    progress = true;
                }
                else if (value.startsWith("!"))
                {
                    int end = tagEnd(value);
                    tag = normalizeTag(value.substring(0, end), line, tagHandles);
                    if (end < value.length() && value.charAt(end) == ',')
                    {
                        throw error("Malformed YAML tag", line);
                    }
                    value = value.substring(end).trim();
                    progress = true;
                }
            }
            while (progress);

            if (alias != null && !value.isEmpty())
            {
                throw error("YAML alias cannot have a trailing value", line);
            }
            return new ValueSpec(anchor, alias, tag, value);
        }

        private static int tokenEnd(
            String text,
            int start)
        {
            int end = start;
            while (end < text.length())
            {
                char c = text.charAt(end);
                if (Character.isWhitespace(c) || c == ',' || c == ']' || c == '}')
                {
                    break;
                }
                end++;
            }
            return end;
        }

        private static int tagEnd(
            String text)
        {
            if (text.startsWith("!<"))
            {
                int end = text.indexOf('>');
                return end == -1 ? text.length() : end + 1;
            }
            if (text.startsWith("!!"))
            {
                return tokenEnd(text, 2);
            }
            return tokenEnd(text, 1);
        }

        private static String normalizeTag(
            String tag,
            Line line,
            Map<String, String> tagHandles)
        {
            if ("!".equals(tag))
            {
                return tag;
            }
            if (tag.indexOf('{') != -1 || tag.indexOf('}') != -1)
            {
                throw error("Malformed YAML tag", line);
            }
            if (tag.startsWith("!<") && tag.endsWith(">"))
            {
                return tag.substring(2, tag.length() - 1);
            }
            String handle = tagHandles.keySet().stream()
                .filter(tag::startsWith)
                .max((a, b) -> Integer.compare(a.length(), b.length()))
                .orElse(null);
            if (handle != null)
            {
                return tagHandles.get(handle) + tag.substring(handle.length());
            }
            if (tag.indexOf('!', 1) != -1)
            {
                throw error("Unknown YAML tag handle", line);
            }
            return tag;
        }
    }

    private static final class KeySpec
    {
        final String name;
        final YamlNode key;

        private KeySpec(
            String name,
            YamlNode key)
        {
            this.name = name;
            this.key = key;
        }
    }

    private record BlockScalarLine(
        String value,
        boolean moreIndented)
    {
    }

    private static final class FlowParser
    {
        private final String text;
        private final Line line;
        private final Map<String, String> tagHandles;
        private final YamlConfiguration config;
        private int cursor;
        private YamlDocumentParser delegate;

        private FlowParser(
            String text,
            Line line,
            Map<String, String> tagHandles,
            YamlConfiguration config)
        {
            this.text = text;
            this.line = line;
            this.tagHandles = tagHandles;
            this.config = config;
        }

        private YamlDocumentParser delegate()
        {
            if (delegate == null)
            {
                delegate = new YamlDocumentParser(new Document(List.of(), new YamlLocation(1, 1, 0), "",
                    DocumentScanner.defaultTagHandles(), config));
            }
            return delegate;
        }

        private YamlNode applyFlowTag(
            YamlNode value,
            String tag,
            Line line)
        {
            return delegate().applyTag(value, tag, scalarText(value, ""), line);
        }

        private YamlNode parse()
        {
            YamlNode value = parseValue();
            skipWhitespaceAndComments();
            if (cursor != text.length())
            {
                throw error("Unexpected flow content", line);
            }
            return value;
        }

        private YamlNode parseValue()
        {
            skipWhitespaceAndComments();
            if (cursor >= text.length())
            {
                throw error("Expected flow value", line);
            }
            if (text.charAt(cursor) == ',' || text.charAt(cursor) == ']' || text.charAt(cursor) == '}' ||
                text.charAt(cursor) == '#')
            {
                throw error("Expected flow value", line);
            }

            ValueSpec spec = parseFlowDecorators();
            delegate().validateSpec(spec, line);
            YamlNode value;
            if (spec.alias != null)
            {
                value = resolve(spec.alias);
            }
            else
            {
                value = switch (text.charAt(cursor))
                {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '\'', '"' -> parseQuotedScalar();
                default -> parseBareScalar();
                };
                value = applyFlowTag(value, spec.tag, line);
                if (spec.anchor != null)
                {
                    if (!config.anchors())
                    {
                        throw error("YAML anchors are disabled", line);
                    }
                    value.anchor = spec.anchor;
                }
            }
            return value;
        }

        private ValueSpec parseFlowDecorators()
        {
            String anchor = null;
            String alias = null;
            String tag = null;

            boolean progress;
            do
            {
                progress = false;
                skipWhitespaceAndComments();
                if (cursor < text.length() && text.charAt(cursor) == '&')
                {
                    int start = ++cursor;
                    while (cursor < text.length() && isFlowToken(text.charAt(cursor)))
                    {
                        cursor++;
                    }
                    anchor = text.substring(start, cursor);
                    progress = true;
                }
                else if (cursor < text.length() && text.charAt(cursor) == '*')
                {
                    int start = ++cursor;
                    while (cursor < text.length() && isFlowToken(text.charAt(cursor)))
                    {
                        cursor++;
                    }
                    alias = text.substring(start, cursor);
                    progress = true;
                }
                else if (cursor < text.length() && text.charAt(cursor) == '!')
                {
                    int start = cursor++;
                    if (cursor < text.length() && text.charAt(cursor) == '!')
                    {
                        cursor++;
                    }
                    while (cursor < text.length() && isFlowToken(text.charAt(cursor)))
                    {
                        cursor++;
                    }
                    tag = ValueSpec.normalizeTag(text.substring(start, cursor), line, tagHandles);
                    progress = true;
                }
            }
            while (progress);

            return new ValueSpec(anchor, alias, tag, "");
        }

        private YamlObjectNode parseObject()
        {
            YamlObjectNode object = new YamlObjectNode(line.line, line.column + cursor, line.offset + cursor);
            object.style = "flow";
            cursor++;
            skipWhitespaceAndComments();
            if (consume('}'))
            {
                return object;
            }

            while (true)
            {
                KeySpec key = parseFlowKey();
                skipWhitespaceAndComments();
                if (!consume(':'))
                {
                    YamlNode value = YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column + cursor,
                        line.offset + cursor);
                    object.add(key.key != null ?
                        new YamlEntry(key.key, value, line.line, line.column + cursor, line.offset + cursor) :
                        new YamlEntry(key.name, value, line.line, line.column + cursor, line.offset + cursor));
                    skipWhitespaceAndComments();
                    if (!consume(','))
                    {
                        if (consume('}'))
                        {
                            return object;
                        }
                        throw error("Expected ':' in flow mapping", line);
                    }
                    skipWhitespaceAndComments();
                    if (consume('}'))
                    {
                        return object;
                    }
                    continue;
                }
                skipWhitespaceAndComments();
                YamlNode value = cursor < text.length() && (text.charAt(cursor) == ',' || text.charAt(cursor) == '}') ?
                    YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column + cursor, line.offset + cursor) :
                    parseValue();
                object.add(key.key != null ?
                    new YamlEntry(key.key, value, line.line, line.column + cursor, line.offset + cursor) :
                    new YamlEntry(key.name, value, line.line, line.column + cursor, line.offset + cursor));
                skipWhitespaceAndComments();
                if (!consume(','))
                {
                    break;
                }
                skipWhitespaceAndComments();
                if (consume('}'))
                {
                    return object;
                }
            }

            if (!consume('}'))
            {
                throw error("Expected '}' in flow mapping", line);
            }
            return object;
        }

        private YamlArrayNode parseArray()
        {
            YamlArrayNode array = new YamlArrayNode(line.line, line.column + cursor, line.offset + cursor);
            array.style = "flow";
            cursor++;
            skipWhitespaceAndComments();
            if (consume(']'))
            {
                return array;
            }

            while (true)
            {
                array.add(parseArrayEntry());
                skipWhitespaceAndComments();
                if (!consume(','))
                {
                    break;
                }
                skipWhitespaceAndComments();
                if (consume(']'))
                {
                    return array;
                }
            }

            if (!consume(']'))
            {
                throw error("Expected ']' in flow sequence", line);
            }
            return array;
        }

        private YamlNode parseArrayEntry()
        {
            if (flowEntryMapping())
            {
                YamlObjectNode object = new YamlObjectNode(line.line, line.column + cursor, line.offset + cursor);
                object.style = "flow";
                KeySpec key = parseFlowKey();
                if (!consume(':'))
                {
                    throw error("Expected ':' in flow mapping", line);
                }
                skipWhitespaceAndComments();
                YamlNode value = cursor < text.length() && (text.charAt(cursor) == ',' || text.charAt(cursor) == ']') ?
                    YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column + cursor, line.offset + cursor) :
                    parseValue();
                object.add(key.key != null ?
                    new YamlEntry(key.key, value, line.line, line.column + cursor, line.offset + cursor) :
                    new YamlEntry(key.name, value, line.line, line.column + cursor, line.offset + cursor));
                return object;
            }
            return parseValue();
        }

        private boolean flowEntryMapping()
        {
            boolean single = false;
            boolean doub = false;
            boolean escaped = false;
            int depth = 0;

            for (int i = cursor; i < text.length(); i++)
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
                    case '}', ']' ->
                    {
                        if (depth == 0)
                        {
                            return false;
                        }
                        depth--;
                    }
                    case ',' ->
                    {
                        if (depth == 0)
                        {
                            return false;
                        }
                    }
                    case ':' ->
                    {
                        if (depth == 0 && isFlowMappingColon(i))
                        {
                            String key = text.substring(cursor, i);
                            return !key.isBlank() && (key.indexOf('\n') == -1 || key.stripLeading().startsWith("?"));
                        }
                    }
                    default ->
                    {
                        // continue
                    }
                    }
                }
            }
            return false;
        }

        private KeySpec parseFlowKey()
        {
            skipWhitespaceAndComments();
            if (cursor < text.length() && text.charAt(cursor) == '?' &&
                (cursor + 1 == text.length() || Character.isWhitespace(text.charAt(cursor + 1))))
            {
                cursor++;
                skipWhitespaceAndComments();
                if (cursor < text.length() && (text.charAt(cursor) == '{' || text.charAt(cursor) == '['))
                {
                    if (!config.nonScalarKeys())
                    {
                        throw error("YAML non-scalar mapping keys are disabled", line);
                    }
                    return new KeySpec(null, parseValue());
                }
                if (cursor < text.length() && (text.charAt(cursor) == '\'' || text.charAt(cursor) == '"'))
                {
                    return new KeySpec(parseQuotedScalar().value, null);
                }
                return new KeySpec(foldFlowScalar(parseBareFlowKey()), null);
            }
            if (cursor < text.length() && (text.charAt(cursor) == '\'' || text.charAt(cursor) == '"'))
            {
                return new KeySpec(parseQuotedScalar().value, null);
            }
            if (cursor < text.length() && (text.charAt(cursor) == '{' || text.charAt(cursor) == '['))
            {
                if (!config.nonScalarKeys())
                {
                    throw error("YAML non-scalar mapping keys are disabled", line);
                }
                return new KeySpec(null, parseValue());
            }

            int mark = cursor;
            ValueSpec spec = parseFlowDecorators();
            delegate().validateSpec(spec, line);
            if (spec.alias != null)
            {
                return new KeySpec(null, resolve(spec.alias));
            }
            if (spec.anchor != null || spec.tag != null)
            {
                YamlNode key = switch (text.charAt(cursor))
                {
                case '\'', '"' -> parseQuotedScalar();
                case '{', '[' -> parseValue();
                default -> parseBareKeyScalar();
                };
                key = applyFlowTag(key, spec.tag, line);
                if (spec.anchor != null)
                {
                    if (!config.anchors())
                    {
                        throw error("YAML anchors are disabled", line);
                    }
                    key.anchor = spec.anchor;
                }
                return new KeySpec(null, key);
            }

            cursor = mark;
            return new KeySpec(foldFlowScalar(parseBareFlowKey()), null);
        }

        private String parseBareFlowKey()
        {
            int start = cursor;
            while (cursor < text.length() && text.charAt(cursor) != ',' && text.charAt(cursor) != '}')
            {
                if (text.charAt(cursor) == ':' && isFlowMappingColon(cursor))
                {
                    break;
                }
                cursor++;
            }
            return text.substring(start, cursor).trim();
        }

        private YamlScalarNode parseBareKeyScalar()
        {
            int start = cursor;
            while (cursor < text.length())
            {
                char c = text.charAt(cursor);
                if (c == ',' || c == ']' || c == '}' || isFlowComment(cursor) ||
                    c == ':' && isFlowMappingColon(cursor))
                {
                    break;
                }
                cursor++;
            }
            String value = foldFlowScalar(text.substring(start, cursor).trim());
            if (isFlowDocumentMarker(value))
            {
                throw error("Document markers are not allowed in flow collection", line);
            }
            return delegate().parseScalar(value, null, line);
        }

        private static String foldFlowScalar(
            String value)
        {
            return hasLineBreak(value) ? FLOW_FOLD_PATTERN.matcher(value).replaceAll(" ") : value;
        }

        private static boolean hasLineBreak(
            String value)
        {
            boolean found = false;
            for (int i = 0; i < value.length(); i++)
            {
                char c = value.charAt(i);
                if (c == '\n' || c == '\r' || c == '\f' || c == '\u000B' ||
                    c == '\u0085' || c == '\u2028' || c == '\u2029')
                {
                    found = true;
                    break;
                }
            }
            return found;
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
            YamlScalarNode scalar = YamlScalarNode.string(unquote(text.substring(start, cursor), line),
                line.line, line.column + start, line.offset + start);
            scalar.style = quote == '"' ? "\"" : "'";
            return scalar;
        }

        private YamlScalarNode parseBareScalar()
        {
            int start = cursor;
            while (cursor < text.length())
            {
                char c = text.charAt(cursor);
                if (c == ',' || c == ']' || c == '}' || isFlowComment(cursor))
                {
                    break;
                }
                if (c == ':' && isFlowMappingColon(cursor))
                {
                    break;
                }
                cursor++;
            }
            String value = foldFlowScalar(text.substring(start, cursor).trim());
            if ("-".equals(value))
            {
                throw error("Expected flow value", line);
            }
            if (isFlowDocumentMarker(value))
            {
                throw error("Document markers are not allowed in flow collection", line);
            }
            return delegate().parseScalar(value, null, line);
        }

        private static boolean isFlowDocumentMarker(
            String value)
        {
            return "---".equals(value) || "...".equals(value);
        }

        private YamlNode resolve(
            String alias)
        {
            if (!config.aliases())
            {
                throw error("YAML aliases are disabled", line);
            }
            YamlNode reference = YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column, line.offset);
            reference.alias = alias;
            return reference;
        }

        private boolean consume(
            char expected)
        {
            skipWhitespaceAndComments();
            if (cursor < text.length() && text.charAt(cursor) == expected)
            {
                cursor++;
                return true;
            }
            return false;
        }

        private void skipWhitespaceAndComments()
        {
            while (cursor < text.length())
            {
                char c = text.charAt(cursor);
                if (Character.isWhitespace(c))
                {
                    cursor++;
                }
                else if (c == '#' && (cursor == 0 || Character.isWhitespace(text.charAt(cursor - 1))))
                {
                    if (!config.comments())
                    {
                        throw error("YAML comments are disabled", line);
                    }
                    while (cursor < text.length() && text.charAt(cursor) != '\n')
                    {
                        cursor++;
                    }
                }
                else
                {
                    break;
                }
            }
        }

        private static boolean isFlowToken(
            char c)
        {
            return !Character.isWhitespace(c) && c != ',' && c != ']' && c != '}';
        }

        private boolean isFlowMappingColon(
            int index)
        {
            char next = index + 1 == text.length() ? 0 : text.charAt(index + 1);
            return index + 1 == text.length() || Character.isWhitespace(next) ||
                next == ',' || next == ']' || next == '}';
        }

        private boolean isFlowComment(
            int index)
        {
            return text.charAt(index) == '#' && (index == 0 || Character.isWhitespace(text.charAt(index - 1)));
        }
    }

    private static final class Line
    {
        final String raw;
        final int indent;
        final String content;
        final String comment;
        final boolean blank;
        final boolean directive;
        final boolean ignorable;
        final int line;
        final int column;
        final long offset;
        final long afterOffset;

        private Line(
            String raw,
            int indent,
            String content,
            String comment,
            boolean blank,
            boolean directive,
            boolean ignorable,
            int line,
            int column,
            long offset,
            long afterOffset)
        {
            this.raw = raw;
            this.indent = indent;
            this.content = content;
            this.comment = comment;
            this.blank = blank;
            this.directive = directive;
            this.ignorable = ignorable;
            this.line = line;
            this.column = column;
            this.offset = offset;
            this.afterOffset = afterOffset;
        }

        private Line atIndent(
            int indent)
        {
            return new Line(raw, indent, content, comment, blank, directive, ignorable, line, column, offset, afterOffset);
        }
    }
}
