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

import java.io.IOException;
import java.io.Writer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class YamlEmitter
{
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
        "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private static final Pattern PLAIN_PATTERN = Pattern.compile("[A-Za-z0-9_./@+-]+");

    private YamlEmitter()
    {
    }

    public static void write(
        YamlNode node,
        Writer writer) throws IOException
    {
        if (node.source != null)
        {
            writer.write(node.source);
        }
        else if (node.alias != null)
        {
            writeLeadingComments(node, writer, 0);
            writer.write(formatAlias(node));
            writeLineComment(node, writer);
            writer.write('\n');
        }
        else if (isEmptyObject(node))
        {
            writeLeadingComments(node, writer, 0);
            writer.write("{}");
            writeLineComment(node, writer);
            writer.write('\n');
        }
        else if (isEmptyArray(node))
        {
            writeLeadingComments(node, writer, 0);
            writer.write("[]");
            writeLineComment(node, writer);
            writer.write('\n');
        }
        else
        {
            writeLeadingComments(node, writer, 0);
            writeNode(node, writer, 0);
            if ("flow".equals(node.style))
            {
                writeLineComment(node, writer);
                writer.write('\n');
            }
        }
    }

    private static void writeNode(
        YamlNode node,
        Writer writer,
        int indent) throws IOException
    {
        if (node.alias != null)
        {
            writer.write(formatAlias(node));
        }
        else if (node instanceof YamlObjectNode object)
        {
            if ("flow".equals(object.style))
            {
                writer.write(formatFlowObject(object));
            }
            else
            {
                writeObject(object, writer, indent);
            }
        }
        else if (node instanceof YamlArrayNode array)
        {
            if ("flow".equals(array.style))
            {
                writer.write(formatFlowArray(array));
            }
            else
            {
                writeArray(array, writer, indent);
            }
        }
        else
        {
            writeScalar((YamlScalarNode) node, writer, indent, false);
        }
    }

    private static void writeObject(
        YamlObjectNode object,
        Writer writer,
        int indent) throws IOException
    {
        for (YamlEntry entry : object.entries)
        {
            writeLeadingComments(entry.value, writer, indent);
            if (entry.key != null)
            {
                writeIndent(writer, indent);
                writer.write("? ");
                writer.write(formatNode(entry.key));
                writer.write('\n');
                writeIndent(writer, indent);
                writer.write(':');
                writeObjectValue(entry.value, writer, indent);
            }
            else
            {
                writeIndent(writer, indent);
                writer.write(formatKey(entry.name));
                writer.write(':');
                writeObjectValue(entry.value, writer, indent);
            }
        }
    }

    private static void writeObjectValue(
        YamlNode value,
        Writer writer,
        int indent) throws IOException
    {
        if (value.alias != null)
        {
            writer.write(' ');
            writer.write(formatAlias(value));
            writeLineComment(value, writer);
            writer.write('\n');
        }
        else if (value instanceof YamlScalarNode scalar)
        {
            writeScalar(scalar, writer, indent, true);
        }
        else if (isEmptyObject(value))
        {
            writer.write(' ');
            writer.write(formatPrefix(value));
            writer.write("{}");
            writeLineComment(value, writer);
            writer.write('\n');
        }
        else if (isEmptyArray(value))
        {
            writer.write(' ');
            writer.write(formatPrefix(value));
            writer.write("[]");
            writeLineComment(value, writer);
            writer.write('\n');
        }
        else if ("flow".equals(value.style))
        {
            writer.write(' ');
            writer.write(formatNode(value));
            writeLineComment(value, writer);
            writer.write('\n');
        }
        else
        {
            writeLineComment(value, writer);
            writer.write('\n');
            writeNode(value, writer, indent + 1);
        }
    }

    private static void writeArray(
        YamlArrayNode array,
        Writer writer,
        int indent) throws IOException
    {
        for (YamlNode value : array.values)
        {
            writeLeadingComments(value, writer, indent);
            if (value.alias != null)
            {
                writeIndent(writer, indent);
                writer.write("- ");
                writer.write(formatAlias(value));
                writeLineComment(value, writer);
                writer.write('\n');
            }
            else if (value instanceof YamlScalarNode scalar)
            {
                writeIndent(writer, indent);
                writer.write("- ");
                writeScalar(scalar, writer, indent, false);
            }
            else if (value instanceof YamlObjectNode object)
            {
                if (object.lineComment != null)
                {
                    writeIndent(writer, indent);
                    writer.write("-");
                    writeLineComment(object, writer);
                    writer.write('\n');
                    writeNode(object, writer, indent + 1);
                }
                else
                {
                    writeArrayObject(object, writer, indent);
                }
            }
            else if (isEmptyArray(value))
            {
                writeIndent(writer, indent);
                writer.write("- ");
                writer.write(formatPrefix(value));
                writer.write("[]");
                writeLineComment(value, writer);
                writer.write('\n');
            }
            else if ("flow".equals(value.style))
            {
                writeIndent(writer, indent);
                writer.write("- ");
                writer.write(formatNode(value));
                writeLineComment(value, writer);
                writer.write('\n');
            }
            else
            {
                writeIndent(writer, indent);
                writer.write("-");
                writeLineComment(value, writer);
                writer.write('\n');
                writeNode(value, writer, indent + 1);
            }
        }
    }

    private static void writeArrayObject(
        YamlObjectNode object,
        Writer writer,
        int indent) throws IOException
    {
        if (object.entries.isEmpty())
        {
            writeIndent(writer, indent);
            writer.write("- {}");
            writeLineComment(object, writer);
            writer.write('\n');
            return;
        }

        YamlEntry first = object.entries.get(0);
        if (first.key != null)
        {
            writeIndent(writer, indent);
            writer.write("- ");
            writer.write("? ");
            writer.write(formatNode(first.key));
            writer.write('\n');
            writeIndent(writer, indent + 1);
            writer.write(':');
            writeObjectValue(first.value, writer, indent + 1);
        }
        else
        {
            writeIndent(writer, indent);
            writer.write("- ");
            writer.write(formatKey(first.name));
            writer.write(':');
            if (first.value.alias != null)
            {
                writer.write(' ');
                writer.write(formatAlias(first.value));
                writeLineComment(first.value, writer);
                writer.write('\n');
            }
            else if (first.value instanceof YamlScalarNode scalar)
            {
                writeScalar(scalar, writer, indent + 1, true);
            }
            else if (isEmptyObject(first.value))
            {
                writer.write(" {}");
                writeLineComment(first.value, writer);
                writer.write('\n');
            }
            else if (isEmptyArray(first.value))
            {
                writer.write(" []");
                writeLineComment(first.value, writer);
                writer.write('\n');
            }
            else
            {
                writer.write('\n');
                writeNode(first.value, writer, indent + 2);
            }
        }

        for (int i = 1; i < object.entries.size(); i++)
        {
            YamlEntry entry = object.entries.get(i);
            if (entry.key != null)
            {
                writeIndent(writer, indent + 1);
                writer.write("? ");
                writer.write(formatNode(entry.key));
                writer.write('\n');
                writeIndent(writer, indent + 1);
                writer.write(':');
                writeObjectValue(entry.value, writer, indent + 1);
            }
            else
            {
                writeIndent(writer, indent + 1);
                writer.write(formatKey(entry.name));
                writer.write(':');
                writeObjectValue(entry.value, writer, indent + 1);
            }
        }
    }

    private static boolean isEmptyObject(
        YamlNode value)
    {
        return value instanceof YamlObjectNode object && object.entries.isEmpty();
    }

    private static boolean isEmptyArray(
        YamlNode value)
    {
        return value instanceof YamlArrayNode array && array.values.isEmpty();
    }

    private static void writeIndent(
        Writer writer,
        int indent) throws IOException
    {
        for (int i = 0; i < indent; i++)
        {
            writer.write("  ");
        }
    }

    private static String formatKey(
        String value)
    {
        return plain(value) ? value : quote(value);
    }

    private static String formatNode(
        YamlNode node)
    {
        if (node.alias != null)
        {
            return formatAlias(node);
        }
        if (node instanceof YamlScalarNode scalar)
        {
            return formatScalar(scalar);
        }
        if (node instanceof YamlArrayNode array)
        {
            return formatFlowArray(array);
        }
        return formatFlowObject((YamlObjectNode) node);
    }

    private static void writeScalar(
        YamlScalarNode scalar,
        Writer writer,
        int indent,
        boolean objectValue) throws IOException
    {
        if (scalar.style != null && (scalar.style.startsWith("|") || scalar.style.startsWith(">")))
        {
            if (objectValue)
            {
                writer.write(' ');
            }
            writer.write(formatPrefix(scalar));
            writer.write(scalar.style);
            writeLineComment(scalar, writer);
            writer.write('\n');
            writeBlockScalarContent(scalar.value, writer, indent + 1);
        }
        else
        {
            if (objectValue)
            {
                writer.write(' ');
            }
            writer.write(formatScalar(scalar));
            writeLineComment(scalar, writer);
            writer.write('\n');
        }
    }

    private static void writeLeadingComments(
        YamlNode node,
        Writer writer,
        int indent) throws IOException
    {
        if (node.leadingComments != null)
        {
            for (String comment : node.leadingComments)
            {
                writeIndent(writer, indent);
                writer.write(comment);
                writer.write('\n');
            }
        }
    }

    private static void writeLineComment(
        YamlNode node,
        Writer writer) throws IOException
    {
        if (node.lineComment != null)
        {
            writer.write(' ');
            writer.write(node.lineComment);
        }
    }

    private static String formatScalar(
        YamlScalarNode scalar)
    {
        String value = switch (scalar.type)
        {
        case NUMBER -> scalar.value;
        case TRUE -> "true";
        case FALSE -> "false";
        case NULL -> "null";
        case STRING -> switch (scalar.style == null ? "" : scalar.style)
        {
        case "'" -> singleQuote(scalar.value);
        case "\"" -> quote(scalar.value);
        default -> plain(scalar.value) ? scalar.value : quote(scalar.value);
        };
        };
        return formatPrefix(scalar) + value;
    }

    private static String formatPrefix(
        YamlNode node)
    {
        StringBuilder prefix = new StringBuilder();
        if (node.anchor != null)
        {
            prefix.append('&').append(node.anchor).append(' ');
        }
        if (node.tag != null)
        {
            prefix.append(formatTag(node.tag)).append(' ');
        }
        return prefix.toString();
    }

    private static String formatTag(
        String tag)
    {
        return tag.startsWith("tag:yaml.org,2002:") ? "!!" + tag.substring("tag:yaml.org,2002:".length()) :
            tag.startsWith("!") ? tag :
            "!<" + tag + ">";
    }

    private static String formatAlias(
        YamlNode node)
    {
        return "*" + node.alias;
    }

    private static String formatFlowArray(
        YamlArrayNode array)
    {
        StringBuilder builder = new StringBuilder(formatPrefix(array));
        builder.append('[');
        for (int i = 0; i < array.values.size(); i++)
        {
            if (i != 0)
            {
                builder.append(", ");
            }
            builder.append(formatNode(array.values.get(i)));
        }
        return builder.append(']').toString();
    }

    private static String formatFlowObject(
        YamlObjectNode object)
    {
        StringBuilder builder = new StringBuilder(formatPrefix(object));
        builder.append('{');
        for (int i = 0; i < object.entries.size(); i++)
        {
            if (i != 0)
            {
                builder.append(", ");
            }
            YamlEntry entry = object.entries.get(i);
            builder.append(entry.key != null ? formatNode(entry.key) : formatKey(entry.name));
            builder.append(": ");
            builder.append(formatNode(entry.value));
        }
        return builder.append('}').toString();
    }

    private static void writeBlockScalarContent(
        String value,
        Writer writer,
        int indent) throws IOException
    {
        String[] lines = value.split("\n", -1);
        int limit = lines.length > 0 && lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;
        for (int i = 0; i < limit; i++)
        {
            writeIndent(writer, indent);
            writer.write(lines[i]);
            writer.write('\n');
        }
    }

    private static boolean plain(
        String value)
    {
        String lower = value.toLowerCase(Locale.ROOT);
        return !value.isEmpty() &&
            !value.isBlank() &&
            PLAIN_PATTERN.matcher(value).matches() &&
            !NUMBER_PATTERN.matcher(value).matches() &&
            !"true".equals(lower) &&
            !"false".equals(lower) &&
            !"null".equals(lower) &&
            !"~".equals(lower) &&
            !value.startsWith("-") &&
            !value.startsWith("+") &&
            !value.startsWith(".");
    }

    private static String quote(
        String value)
    {
        StringBuilder text = new StringBuilder();
        text.append('"');
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            switch (c)
            {
            case '"' -> text.append("\\\"");
            case '\\' -> text.append("\\\\");
            case '\b' -> text.append("\\b");
            case '\f' -> text.append("\\f");
            case '\n' -> text.append("\\n");
            case '\r' -> text.append("\\r");
            case '\t' -> text.append("\\t");
            default -> text.append(c);
            }
        }
        return text.append('"').toString();
    }

    private static String singleQuote(
        String value)
    {
        StringBuilder text = new StringBuilder();
        text.append('\'');
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            if (c == '\'')
            {
                text.append("''");
            }
            else
            {
                text.append(c);
            }
        }
        return text.append('\'').toString();
    }
}
