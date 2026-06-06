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
        if (isEmptyObject(node))
        {
            writer.write("{}\n");
        }
        else if (isEmptyArray(node))
        {
            writer.write("[]\n");
        }
        else
        {
            writeNode(node, writer, 0);
            if (node instanceof YamlScalarNode)
            {
                writer.write('\n');
            }
        }
    }

    private static void writeNode(
        YamlNode node,
        Writer writer,
        int indent) throws IOException
    {
        if (node instanceof YamlObjectNode object)
        {
            writeObject(object, writer, indent);
        }
        else if (node instanceof YamlArrayNode array)
        {
            writeArray(array, writer, indent);
        }
        else
        {
            writer.write(formatScalar((YamlScalarNode) node));
        }
    }

    private static void writeObject(
        YamlObjectNode object,
        Writer writer,
        int indent) throws IOException
    {
        for (YamlEntry entry : object.entries)
        {
            writeIndent(writer, indent);
            writer.write(formatKey(entry.name));
            writer.write(':');
            writeObjectValue(entry.value, writer, indent);
        }
    }

    private static void writeObjectValue(
        YamlNode value,
        Writer writer,
        int indent) throws IOException
    {
        if (value instanceof YamlScalarNode scalar)
        {
            writer.write(' ');
            writer.write(formatScalar(scalar));
            writer.write('\n');
        }
        else if (isEmptyObject(value))
        {
            writer.write(" {}\n");
        }
        else if (isEmptyArray(value))
        {
            writer.write(" []\n");
        }
        else
        {
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
            if (value instanceof YamlScalarNode scalar)
            {
                writeIndent(writer, indent);
                writer.write("- ");
                writer.write(formatScalar(scalar));
                writer.write('\n');
            }
            else if (value instanceof YamlObjectNode object)
            {
                writeArrayObject(object, writer, indent);
            }
            else if (isEmptyArray(value))
            {
                writeIndent(writer, indent);
                writer.write("- []\n");
            }
            else
            {
                writeIndent(writer, indent);
                writer.write("-\n");
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
            writer.write("- {}\n");
            return;
        }

        YamlEntry first = object.entries.get(0);
        writeIndent(writer, indent);
        writer.write("- ");
        writer.write(formatKey(first.name));
        writer.write(':');
        if (first.value instanceof YamlScalarNode scalar)
        {
            writer.write(' ');
            writer.write(formatScalar(scalar));
            writer.write('\n');
        }
        else if (isEmptyObject(first.value))
        {
            writer.write(" {}\n");
        }
        else if (isEmptyArray(first.value))
        {
            writer.write(" []\n");
        }
        else
        {
            writer.write('\n');
            writeNode(first.value, writer, indent + 2);
        }

        for (int i = 1; i < object.entries.size(); i++)
        {
            YamlEntry entry = object.entries.get(i);
            writeIndent(writer, indent + 1);
            writer.write(formatKey(entry.name));
            writer.write(':');
            writeObjectValue(entry.value, writer, indent + 1);
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

    private static String formatScalar(
        YamlScalarNode scalar)
    {
        return switch (scalar.type)
        {
        case NUMBER -> scalar.value;
        case TRUE -> "true";
        case FALSE -> "false";
        case NULL -> "null";
        case STRING -> plain(scalar.value) ? scalar.value : quote(scalar.value);
        };
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
}
