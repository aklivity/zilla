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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resolves a raw (unresolved) parse tree into a resolved one: dereferences aliases against the
 * anchors declared earlier in document order and coerces tags. Merge keys ({@code <<}) are not
 * part of the YAML 1.2 JSON Schema and are treated as ordinary mapping keys. This is the
 * compose/construct pass extracted out of {@link YamlDocumentParser}'s inline resolved mode so
 * that the parse layer can stay reference-neutral.
 */
public final class YamlReferences
{
    private static final Pattern HEX_INTEGER_PATTERN = Pattern.compile("-?0x[0-9a-fA-F]+");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?(?:0|[1-9][0-9]*)");
    private static final Pattern FLOAT_PATTERN = Pattern.compile(
        "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)|-?(?:0|[1-9][0-9]*)\\.[0-9]+");

    private final YamlConfiguration config;
    private final Map<String, YamlNode> anchors;

    private YamlReferences(
        YamlConfiguration config)
    {
        this.config = config;
        this.anchors = new HashMap<>();
    }

    public static YamlNode resolve(
        YamlNode node,
        Map<String, ?> config)
    {
        return new YamlReferences(new YamlConfiguration(config)).resolveNode(node);
    }

    private YamlNode resolveNode(
        YamlNode node)
    {
        YamlNode resolved;
        if (node.alias != null)
        {
            resolved = resolveAlias(node);
        }
        else
        {
            YamlNode structure;
            if (node instanceof YamlObjectNode object)
            {
                structure = resolveObject(object);
            }
            else if (node instanceof YamlArrayNode array)
            {
                structure = resolveArray(array);
            }
            else
            {
                structure = node;
            }

            resolved = applyTag(structure, node.tag, sourceText(structure));
            if (resolved != structure)
            {
                resolved.leadingComments = structure.leadingComments;
                resolved.lineComment = structure.lineComment;
            }
            if (node.anchor != null)
            {
                resolved.anchor = node.anchor;
                anchors.put(node.anchor, YamlDocumentParser.copy(resolved));
            }
        }
        return resolved;
    }

    private YamlNode resolveAlias(
        YamlNode node)
    {
        YamlNode value = anchors.get(node.alias);
        if (value == null)
        {
            throw error("Unresolved YAML alias", node);
        }
        YamlNode resolved = YamlDocumentParser.copy(value);
        resolved.source = null;
        resolved.anchor = null;
        resolved.alias = node.alias;
        resolved.leadingComments = null;
        resolved.lineComment = null;
        if (node.tag != null)
        {
            resolved = applyTag(resolved, node.tag, sourceText(resolved));
            resolved.alias = node.alias;
        }
        return resolved;
    }

    private YamlObjectNode resolveObject(
        YamlObjectNode object)
    {
        YamlObjectNode result = new YamlObjectNode(object.line, object.column, object.offset);
        result.source = object.source;
        result.style = object.style;
        result.leadingComments = object.leadingComments;
        result.lineComment = object.lineComment;

        for (YamlEntry entry : object.entries)
        {
            YamlNode value = resolveNode(entry.value);
            result.add(entry.key != null ?
                new YamlEntry(resolveNode(entry.key), value, entry.line, entry.column, entry.offset) :
                new YamlEntry(entry.name, value, entry.line, entry.column, entry.offset));
        }
        return result;
    }

    private YamlArrayNode resolveArray(
        YamlArrayNode array)
    {
        YamlArrayNode result = new YamlArrayNode(array.line, array.column, array.offset);
        result.source = array.source;
        result.style = array.style;
        result.leadingComments = array.leadingComments;
        result.lineComment = array.lineComment;
        for (YamlNode value : array.values)
        {
            result.add(resolveNode(value));
        }
        return result;
    }

    private YamlNode applyTag(
        YamlNode value,
        String tag,
        String text)
    {
        YamlNode tagged;
        if (tag == null)
        {
            tagged = value;
        }
        else if (!config.tags())
        {
            throw error("YAML tags are disabled", value);
        }
        else if ("!".equals(tag))
        {
            tagged = value instanceof YamlScalarNode ?
                YamlScalarNode.string(scalarText(value, text), value.line, value.column, value.offset) :
                value;
            tagged.tag = tag;
            tagged.style = value.style;
        }
        else
        {
            tagged = switch (tag)
            {
            case "tag:yaml.org,2002:str" -> YamlScalarNode.string(scalarText(value, text), value.line, value.column,
                value.offset);
            case "tag:yaml.org,2002:int" -> taggedInteger(value, text);
            case "tag:yaml.org,2002:float" -> taggedFloat(value, text);
            case "tag:yaml.org,2002:bool" -> taggedBool(value, text);
            case "tag:yaml.org,2002:null" -> YamlScalarNode.literal(YamlScalarType.NULL, value.line, value.column,
                value.offset);
            case "tag:yaml.org,2002:map" -> taggedMap(value);
            case "tag:yaml.org,2002:seq" -> taggedSeq(value);
            default -> value;
            };
            tagged.tag = tag;
            tagged.style = value.style;
        }
        return tagged;
    }

    private YamlNode taggedInteger(
        YamlNode value,
        String text)
    {
        String scalar = scalarText(value, text);
        if (!HEX_INTEGER_PATTERN.matcher(scalar).matches() && !INTEGER_PATTERN.matcher(scalar).matches())
        {
            throw error("Invalid tagged integer scalar", value);
        }
        return YamlScalarNode.number(numberText(scalar), value.line, value.column, value.offset);
    }

    private YamlNode taggedFloat(
        YamlNode value,
        String text)
    {
        String scalar = scalarText(value, text);
        rejectNonFinite(scalar, value);
        if (!FLOAT_PATTERN.matcher(scalar).matches() && !INTEGER_PATTERN.matcher(scalar).matches())
        {
            throw error("Invalid tagged float scalar", value);
        }
        return YamlScalarNode.number(scalar, value.line, value.column, value.offset);
    }

    private YamlNode taggedBool(
        YamlNode value,
        String text)
    {
        String scalar = scalarText(value, text).toLowerCase(Locale.ROOT);
        if (!"true".equals(scalar) && !"false".equals(scalar))
        {
            throw error("Invalid tagged boolean scalar", value);
        }
        return YamlScalarNode.literal("true".equals(scalar) ? YamlScalarType.TRUE : YamlScalarType.FALSE,
            value.line, value.column, value.offset);
    }

    private YamlNode taggedMap(
        YamlNode value)
    {
        if (!(value instanceof YamlObjectNode))
        {
            throw error("Tagged value is not a map", value);
        }
        return value;
    }

    private YamlNode taggedSeq(
        YamlNode value)
    {
        if (!(value instanceof YamlArrayNode))
        {
            throw error("Tagged value is not a sequence", value);
        }
        return value;
    }

    private static String scalarText(
        YamlNode value,
        String text)
    {
        String result;
        if (value instanceof YamlScalarNode scalar)
        {
            result = scalar.value != null ? scalar.value : switch (scalar.type)
            {
            case TRUE -> "true";
            case FALSE -> "false";
            case NULL -> text.isEmpty() ? "" : "null";
            default -> text;
            };
        }
        else
        {
            result = text;
        }
        return result;
    }

    private static String sourceText(
        YamlNode value)
    {
        String result;
        if (value instanceof YamlScalarNode scalar && scalar.value != null)
        {
            result = scalar.value;
        }
        else
        {
            result = "";
        }
        return result;
    }

    private static String numberText(
        String text)
    {
        boolean negative = text.startsWith("-");
        String scalar = negative ? text.substring(1) : text;
        String result;
        if (scalar.startsWith("0x"))
        {
            String value = new BigInteger(scalar.substring(2), 16).toString();
            result = negative ? "-" + value : value;
        }
        else
        {
            result = text;
        }
        return result;
    }

    private static void rejectNonFinite(
        String text,
        YamlNode node)
    {
        String lower = text.toLowerCase(Locale.ROOT);
        if (".nan".equals(lower) || ".inf".equals(lower) || "+.inf".equals(lower) || "-.inf".equals(lower))
        {
            throw error("Non-finite YAML numbers are not valid JSON values", node);
        }
    }

    private static YamlParseException error(
        String message,
        YamlNode node)
    {
        return new YamlParseException(message, new YamlLocation(node.line, node.column, node.offset));
    }
}
