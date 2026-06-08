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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class YamlDocumentParser
{
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
        "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?(?:0|[1-9][0-9]*)");
    private static final Pattern FLOAT_PATTERN = Pattern.compile(
        "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)|-?(?:0|[1-9][0-9]*)\\.[0-9]+");

    private final List<Line> lines;
    private final Map<String, YamlNode> anchors;
    private final Map<String, String> tagHandles;
    private final YamlLocation end;
    private final String source;
    private final YamlConfiguration config;
    private final List<String> comments;
    private int index;

    private YamlDocumentParser(
        Document document)
    {
        this.lines = document.lines;
        this.anchors = new HashMap<>();
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
        YamlNode node = line.content.startsWith("{") || line.content.startsWith("[") ?
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
        return new FlowParser(flow.toString(), line, anchors, tagHandles, config).parse();
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

            String item = line.content.length() == 1 ? "" : line.content.substring(2).trim();
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
                YamlNode value = nextNestedValue(indent, line);
                value = applyTag(value, spec.tag, spec.value, line);
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
                YamlNode value = parseInlineValue(spec.value, spec.tag, line);
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
        index++;
        ValueSpec spec = ValueSpec.parse(line.content.trim(), line, tagHandles);
        validateSpec(spec, line);
        YamlNode value = spec.alias != null ?
            resolveAlias(spec.alias, line) :
            parseInlineValue(spec.value, spec.tag, line, true);
        attachComments(value, line);
        storeAnchor(spec.anchor, value, line);
        return value;
    }

    private void addExplicitMappingEntry(
        YamlObjectNode object,
        int indent,
        Line line)
    {
        String keyText = line.content.substring(2).trim();
        YamlNode keyNode = null;
        String key = null;
        if (keyText.startsWith("{") || keyText.startsWith("["))
        {
            if (!config.nonScalarKeys())
            {
                throw error("YAML non-scalar mapping keys are disabled", line);
            }
            keyNode = new FlowParser(keyText, line, anchors, tagHandles, config).parse();
        }
        else
        {
            KeySpec keySpec = parseKeySpec(keyText, line);
            key = keySpec.name;
            keyNode = keySpec.key;
        }
        index++;
        skipIgnorable();
        if (index >= lines.size() || lines.get(index).indent != indent || !lines.get(index).content.startsWith(":"))
        {
            throw error("Expected explicit mapping value", line);
        }

        Line valueLine = lines.get(index);
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

        YamlNode value = spec.alias != null ?
            resolveAlias(spec.alias, line) :
            spec.value.isEmpty() ? nextNestedValue(indent, line) :
            parseInlineValue(spec.value, spec.tag, line, false);

        value = applyTag(value, spec.tag, spec.value, line);
        attachComments(value, line);
        storeAnchor(spec.anchor, value, line);

        if ("<<".equals(key))
        {
            if (!config.mergeKeys())
            {
                throw error("YAML merge keys are disabled", line);
            }
            merge(object, value, line);
        }
        else
        {
            object.removeMerged(key);
            object.add(new YamlEntry(key, value, line.line, line.column, line.offset));
        }
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

        YamlNode value = spec.alias != null ?
            resolveAlias(spec.alias, line) :
            spec.value.isEmpty() ? nextNestedValue(indent, line) :
            parseInlineValue(spec.value, spec.tag, line, false);

        value = applyTag(value, spec.tag, spec.value, line);
        attachComments(value, line);
        storeAnchor(spec.anchor, value, line);
        object.add(new YamlEntry(key, value, line.line, line.column, line.offset));
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
            return applyTag(new FlowParser(flow, line, anchors, tagHandles, config).parse(), tag, flow, line);
        }
        return parseScalar(text, tag, line);
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
            flow.append('\n');
            flow.append(next.raw.substring(Math.min(next.indent, next.raw.length())));
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
        while (index < lines.size())
        {
            Line next = lines.get(index);
            if (next.offset >= source.length())
            {
                break;
            }
            if (!blockScalarEmpty(next) && next.indent < contentIndent)
            {
                break;
            }
            if (!allowSameIndent && !blockScalarEmpty(next) && next.indent <= line.indent)
            {
                break;
            }

            values.add(new BlockScalarLine(
                blockScalarEmpty(next) ? "" :
                    next.raw.length() >= contentIndent ? next.raw.substring(contentIndent) : "",
                !blockScalarEmpty(next) && next.indent > contentIndent));
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
            if (!blockScalarEmpty(line))
            {
                if (!allowSameIndent && line.indent <= parentIndent)
                {
                    throw error("Expected indented block scalar content", line);
                }
                return line.indent;
            }
        }
        return allowSameIndent ? parentIndent : parentIndent + 2;
    }

    private static boolean blockScalarEmpty(
        Line line)
    {
        return line.raw.isBlank();
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
        boolean previousBlank = false;
        boolean previousMoreIndented = false;
        boolean first = true;
        for (BlockScalarLine line : lines)
        {
            if (line.value.isEmpty())
            {
                value.append('\n');
                previousBlank = true;
            }
            else
            {
                if (!first)
                {
                    value.append(previousBlank || previousMoreIndented || line.moreIndented ? '\n' : ' ');
                }
                value.append(line.value);
                previousBlank = false;
                previousMoreIndented = line.moreIndented;
                first = false;
            }
        }
        return value.toString();
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
        default -> NUMBER_PATTERN.matcher(text).matches() ?
            YamlScalarNode.number(text, line.line, line.column, line.offset) :
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
            throw error("Non-scalar YAML mapping keys are not supported", line);
        }
        ValueSpec spec = ValueSpec.parse(text, line, tagHandles);
        validateSpec(spec, line);
        if (spec.alias != null || spec.anchor != null || spec.tag != null)
        {
            YamlNode key = spec.alias != null ? resolveAlias(spec.alias, line) :
                spec.value.startsWith("{") || spec.value.startsWith("[") ?
                    applyTag(new FlowParser(spec.value, line, anchors, tagHandles, config).parse(), spec.tag, spec.value, line) :
                    parseScalar(spec.value, spec.tag, line);
            storeAnchor(spec.anchor, key, line);
            return new KeySpec(null, key);
        }
        return new KeySpec(spec.value, null);
    }

    private void merge(
        YamlObjectNode target,
        YamlNode value,
        Line line)
    {
        if (value instanceof YamlObjectNode object)
        {
            mergeObject(target, object);
        }
        else if (value instanceof YamlArrayNode array)
        {
            for (YamlNode element : array.values)
            {
                if (!(element instanceof YamlObjectNode object))
                {
                    throw error("Merge aliases must resolve to objects", line);
                }
                mergeObject(target, object);
            }
        }
        else
        {
            throw error("Merge aliases must resolve to objects", line);
        }
    }

    private static void mergeObject(
        YamlObjectNode target,
        YamlObjectNode source)
    {
        for (YamlEntry entry : source.entries)
        {
            if (entry.name != null && !containsKey(target, entry.name))
            {
                target.add(new YamlEntry(entry.name, copy(entry.value), entry.line, entry.column, entry.offset, true));
            }
        }
    }

    private static boolean containsKey(
        YamlObjectNode object,
        String name)
    {
        return object.entries.stream().anyMatch(e -> name.equals(e.name));
    }

    private YamlNode applyTag(
        YamlNode value,
        String tag,
        String text,
        Line line)
    {
        if (tag == null || "!".equals(tag))
        {
            return value;
        }
        if (!config.tags())
        {
            throw error("YAML tags are disabled", line);
        }

        YamlNode tagged = switch (tag)
        {
        case "tag:yaml.org,2002:str" -> YamlScalarNode.string(scalarText(value, text), line.line, line.column, line.offset);
        case "tag:yaml.org,2002:int" ->
        {
            String scalar = scalarText(value, text);
            if (!INTEGER_PATTERN.matcher(scalar).matches())
            {
                throw error("Invalid tagged integer scalar", line);
            }
            yield YamlScalarNode.number(scalar, line.line, line.column, line.offset);
        }
        case "tag:yaml.org,2002:float" ->
        {
            String scalar = scalarText(value, text);
            rejectNonFinite(scalar, line);
            if (!FLOAT_PATTERN.matcher(scalar).matches() && !INTEGER_PATTERN.matcher(scalar).matches())
            {
                throw error("Invalid tagged float scalar", line);
            }
            yield YamlScalarNode.number(scalar, line.line, line.column, line.offset);
        }
        case "tag:yaml.org,2002:bool" ->
        {
            String scalar = scalarText(value, text).toLowerCase(Locale.ROOT);
            if (!"true".equals(scalar) && !"false".equals(scalar))
            {
                throw error("Invalid tagged boolean scalar", line);
            }
            yield YamlScalarNode.literal("true".equals(scalar) ? YamlScalarType.TRUE : YamlScalarType.FALSE,
                line.line, line.column, line.offset);
        }
        case "tag:yaml.org,2002:null" -> YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column, line.offset);
        case "tag:yaml.org,2002:map" ->
        {
            if (!(value instanceof YamlObjectNode))
            {
                throw error("Tagged value is not a map", line);
            }
            yield value;
        }
        case "tag:yaml.org,2002:seq" ->
        {
            if (!(value instanceof YamlArrayNode))
            {
                throw error("Tagged value is not a sequence", line);
            }
            yield value;
        }
        default -> value;
        };
        tagged.tag = tag;
        tagged.style = value.style;
        return tagged;
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
            case NULL -> "null";
            default -> text;
            };
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
            if (anchors.put(anchor, copy(value)) != null)
            {
                throw error("Duplicate YAML anchor", line);
            }
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
        YamlNode value = anchors.get(alias);
        if (value == null)
        {
            throw error("Unresolved YAML alias", line);
        }
        YamlNode resolved = copy(value);
        resolved.source = null;
        resolved.anchor = null;
        resolved.alias = alias;
        resolved.leadingComments = null;
        resolved.lineComment = null;
        return resolved;
    }

    private static YamlNode copy(
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
                new YamlEntry(entry.name, copy(entry.value), entry.line, entry.column, entry.offset, entry.merged));
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
        return line.indent == indent && (line.content.equals("-") || line.content.startsWith("- "));
    }

    private static boolean isExplicitKey(
        Line line)
    {
        return line.content.equals("?") || line.content.startsWith("? ");
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
            long endOffset = text.length();
            while (end < all.size())
            {
                Line line = all.get(end);
                if (!line.blank && (isDocumentStart(line) || isDocumentEnd(line)))
                {
                    if (!config.documentMarkers())
                    {
                        throw error("YAML document markers are disabled", line);
                    }
                    endOffset = isDocumentStart(line) ? line.offset : line.afterOffset;
                    break;
                }
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
                if (parts.length != 2)
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
                boolean ignorable = blank || directive;
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
                else if (c == '\t')
                {
                    throw error("Tabs are not supported in indentation",
                        new Line(line, indent, line, null, false, false, false,
                            lineNumber, indent + 1, offset + indent, offset + line.length()));
                }
                else
                {
                    break;
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
        private final Map<String, YamlNode> anchors;
        private final Map<String, String> tagHandles;
        private final YamlConfiguration config;
        private int cursor;

        private FlowParser(
            String text,
            Line line,
            Map<String, YamlNode> anchors,
            Map<String, String> tagHandles,
            YamlConfiguration config)
        {
            this.text = text;
            this.line = line;
            this.anchors = anchors;
            this.tagHandles = tagHandles;
            this.config = config;
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
            if (text.charAt(cursor) == ',' || text.charAt(cursor) == ']' || text.charAt(cursor) == '}')
            {
                throw error("Expected flow value", line);
            }

            ValueSpec spec = parseFlowDecorators();
            new YamlDocumentParser(new Document(List.of(), new YamlLocation(1, 1, 0), "",
                DocumentScanner.defaultTagHandles(), config))
                .validateSpec(spec, line);
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
                value = applyFlowTag(value, spec.tag, line, config);
                if (spec.anchor != null)
                {
                    if (!config.anchors())
                    {
                        throw error("YAML anchors are disabled", line);
                    }
                    value.anchor = spec.anchor;
                    if (anchors.put(spec.anchor, copy(value)) != null)
                    {
                        throw error("Duplicate YAML anchor", line);
                    }
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
                    throw error("Expected ':' in flow mapping", line);
                }
                skipWhitespaceAndComments();
                YamlNode value = cursor < text.length() && (text.charAt(cursor) == ',' || text.charAt(cursor) == '}') ?
                    YamlScalarNode.literal(YamlScalarType.NULL, line.line, line.column + cursor, line.offset + cursor) :
                    parseValue();
                if ("<<".equals(key.name))
                {
                    if (!config.mergeKeys())
                    {
                        throw error("YAML merge keys are disabled", line);
                    }
                    new YamlDocumentParser(new Document(List.of(), new YamlLocation(1, 1, text.length()), "",
                        DocumentScanner.defaultTagHandles(), config))
                        .merge(object, value, line);
                }
                else
                {
                    object.add(key.key != null ?
                        new YamlEntry(key.key, value, line.line, line.column + cursor, line.offset + cursor) :
                        new YamlEntry(key.name, value, line.line, line.column + cursor, line.offset + cursor));
                }
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
                        if (depth == 0)
                        {
                            String key = text.substring(cursor, i);
                            return !key.isBlank() && key.indexOf('\n') == -1;
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
            if (consume('?'))
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
            new YamlDocumentParser(new Document(List.of(), new YamlLocation(1, 1, 0), "",
                DocumentScanner.defaultTagHandles(), config))
                .validateSpec(spec, line);
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
                key = applyFlowTag(key, spec.tag, line, config);
                if (spec.anchor != null)
                {
                    if (!config.anchors())
                    {
                        throw error("YAML anchors are disabled", line);
                    }
                    key.anchor = spec.anchor;
                    if (anchors.put(spec.anchor, copy(key)) != null)
                    {
                        throw error("Duplicate YAML anchor", line);
                    }
                }
                return new KeySpec(null, key);
            }

            cursor = mark;
            return new KeySpec(parseBareFlowKey(), null);
        }

        private String parseBareFlowKey()
        {
            int start = cursor;
            while (cursor < text.length() && text.charAt(cursor) != ':')
            {
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
                if (c == ':' || c == ',' || c == ']' || c == '}' || c == '#')
                {
                    break;
                }
                cursor++;
            }
            return new YamlDocumentParser(new Document(List.of(), new YamlLocation(1, 1, text.length()), "",
                DocumentScanner.defaultTagHandles(), config))
                .parseScalar(foldFlowScalar(text.substring(start, cursor).trim()), null, line);
        }

        private static String foldFlowScalar(
            String value)
        {
            return value.replaceAll("[ \\t]*\\R[ \\t]*", " ");
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
                if (c == ',' || c == ']' || c == '}' || c == '#')
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
            return new YamlDocumentParser(new Document(List.of(), new YamlLocation(1, 1, text.length()), "",
                DocumentScanner.defaultTagHandles(), config))
                .parseScalar(value, null, line);
        }

        private YamlNode resolve(
            String alias)
        {
            if (!config.aliases())
            {
                throw error("YAML aliases are disabled", line);
            }
            YamlNode value = anchors.get(alias);
            if (value == null)
            {
                throw error("Unresolved YAML alias", line);
            }
            YamlNode resolved = copy(value);
            resolved.source = null;
            resolved.anchor = null;
            resolved.alias = alias;
            return resolved;
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
                else if (c == '#')
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
    }

    private static YamlNode applyFlowTag(
        YamlNode value,
        String tag,
        Line line,
        YamlConfiguration config)
    {
        return new YamlDocumentParser(new Document(List.of(), new YamlLocation(1, 1, 0), "",
            DocumentScanner.defaultTagHandles(), config))
            .applyTag(value, tag, scalarText(value, ""), line);
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
    }
}
