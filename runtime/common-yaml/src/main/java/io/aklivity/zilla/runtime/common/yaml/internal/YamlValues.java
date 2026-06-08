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
import java.util.Iterator;
import java.util.List;

import io.aklivity.zilla.runtime.common.yaml.YamlArray;
import io.aklivity.zilla.runtime.common.yaml.YamlDocument;
import io.aklivity.zilla.runtime.common.yaml.YamlObject;
import io.aklivity.zilla.runtime.common.yaml.YamlScalar;
import io.aklivity.zilla.runtime.common.yaml.YamlStream;
import io.aklivity.zilla.runtime.common.yaml.YamlValue;

final class YamlValues
{
    private YamlValues()
    {
    }

    static YamlValue wrap(
        YamlNode node)
    {
        if (node instanceof YamlObjectNode object)
        {
            return new ObjectValue(object);
        }
        if (node instanceof YamlArrayNode array)
        {
            return new ArrayValue(array);
        }
        return new ScalarValue((YamlScalarNode) node);
    }

    static YamlDocument document(
        YamlNode node)
    {
        return new DocumentValue(node);
    }

    static YamlStream stream(
        List<YamlNode> nodes)
    {
        return new StreamValue(nodes.stream().map(YamlValues::document).toList());
    }

    static YamlNode node(
        YamlValue value)
    {
        if (value instanceof NodeValue nodeValue)
        {
            return nodeValue.node;
        }

        return switch (value.getValueType())
        {
        case OBJECT -> throw new IllegalArgumentException("Only parsed YAML objects can be emitted by this generator");
        case ARRAY -> arrayNode(value.asYamlArray());
        case STRING -> YamlScalarNode.string(value.asYamlScalar().getString(), 1, 1, 0);
        case NUMBER -> YamlScalarNode.number(value.asYamlScalar().getString(), 1, 1, 0);
        case TRUE -> YamlScalarNode.literal(YamlScalarType.TRUE, 1, 1, 0);
        case FALSE -> YamlScalarNode.literal(YamlScalarType.FALSE, 1, 1, 0);
        case NULL -> YamlScalarNode.literal(YamlScalarType.NULL, 1, 1, 0);
        };
    }

    static void copyMetadata(
        YamlNode source,
        YamlNode target)
    {
        target.tag = source.tag;
        target.anchor = source.anchor;
        target.alias = source.alias;
        target.style = source.style;
        target.leadingComments = source.leadingComments != null ? List.copyOf(source.leadingComments) : null;
        target.lineComment = source.lineComment;
    }

    private static YamlArrayNode arrayNode(
        YamlArray value)
    {
        YamlArrayNode array = new YamlArrayNode(1, 1, 0);
        for (int index = 0; index < value.size(); index++)
        {
            array.add(node(value.get(index)));
        }
        return array;
    }

    private abstract static class NodeValue implements YamlValue
    {
        final YamlNode node;

        NodeValue(
            YamlNode node)
        {
            this.node = node;
        }

        @Override
        public String getTag()
        {
            return node.tag;
        }

        @Override
        public String getAnchor()
        {
            return node.anchor;
        }

        @Override
        public String getAlias()
        {
            return node.alias;
        }

        @Override
        public String getStyle()
        {
            return node.style;
        }

        @Override
        public List<String> getLeadingComments()
        {
            return node.leadingComments != null ? List.copyOf(node.leadingComments) : List.of();
        }

        @Override
        public String getLineComment()
        {
            return node.lineComment;
        }

        @Override
        public YamlValue withTag(
            String tag)
        {
            node.tag = tag;
            return this;
        }

        @Override
        public YamlValue withAnchor(
            String anchor)
        {
            node.anchor = anchor;
            return this;
        }

        @Override
        public YamlValue withAlias(
            String alias)
        {
            node.alias = alias;
            return this;
        }

        @Override
        public YamlValue withStyle(
            String style)
        {
            node.style = style;
            return this;
        }

        @Override
        public YamlValue withLeadingComment(
            String comment)
        {
            if (node.leadingComments == null)
            {
                node.leadingComments = new ArrayList<>();
            }
            node.leadingComments.add(comment);
            return this;
        }

        @Override
        public YamlValue withLeadingComments(
            List<String> comments)
        {
            node.leadingComments = comments != null && !comments.isEmpty() ? List.copyOf(comments) : null;
            return this;
        }

        @Override
        public YamlValue withLineComment(
            String comment)
        {
            node.lineComment = comment;
            return this;
        }
    }

    private static final class DocumentValue implements YamlDocument
    {
        private final YamlValue value;

        private DocumentValue(
            YamlNode node)
        {
            this.value = wrap(node);
        }

        @Override
        public YamlValue getValue()
        {
            return value;
        }
    }

    private static final class StreamValue implements YamlStream
    {
        private final List<YamlDocument> documents;

        private StreamValue(
            List<YamlDocument> documents)
        {
            this.documents = List.copyOf(documents);
        }

        @Override
        public int size()
        {
            return documents.size();
        }

        @Override
        public YamlDocument getDocument(
            int index)
        {
            return documents.get(index);
        }

        @Override
        public List<YamlDocument> getDocuments()
        {
            return documents;
        }

        @Override
        public Iterator<YamlDocument> iterator()
        {
            return documents.iterator();
        }
    }

    private static final class ObjectValue extends NodeValue implements YamlObject
    {
        private final YamlObjectNode object;

        private ObjectValue(
            YamlObjectNode object)
        {
            super(object);
            this.object = object;
        }

        @Override
        public ValueType getValueType()
        {
            return ValueType.OBJECT;
        }

        @Override
        public boolean containsKey(
            String name)
        {
            return value(name) != null;
        }

        @Override
        public int size()
        {
            return object.entries.size();
        }

        @Override
        public YamlObject getObject(
            String name)
        {
            return value(name).asYamlObject();
        }

        @Override
        public YamlArray getArray(
            String name)
        {
            return value(name).asYamlArray();
        }

        @Override
        public YamlScalar getScalar(
            String name)
        {
            return value(name).asYamlScalar();
        }

        @Override
        public String getString(
            String name)
        {
            return getScalar(name).getString();
        }

        @Override
        public String getString(
            String name,
            String defaultValue)
        {
            YamlValue value = value(name);
            return value != null ? value.asYamlScalar().getString() : defaultValue;
        }

        @Override
        public int getInt(
            String name)
        {
            return Integer.parseInt(getString(name));
        }

        @Override
        public long getLong(
            String name)
        {
            return Long.parseLong(getString(name));
        }

        @Override
        public double getDouble(
            String name)
        {
            return Double.parseDouble(getString(name));
        }

        @Override
        public boolean getBoolean(
            String name)
        {
            return Boolean.parseBoolean(getString(name));
        }

        @Override
        public boolean isNull(
            String name)
        {
            YamlValue value = value(name);
            return value != null && value.getValueType() == ValueType.NULL;
        }

        private YamlValue value(
            String name)
        {
            for (int index = object.entries.size() - 1; index >= 0; index--)
            {
                YamlEntry entry = object.entries.get(index);
                if (name.equals(entry.name))
                {
                    return wrap(entry.value);
                }
            }
            return null;
        }
    }

    private static final class ArrayValue extends NodeValue implements YamlArray
    {
        private final YamlArrayNode array;

        private ArrayValue(
            YamlArrayNode array)
        {
            super(array);
            this.array = array;
        }

        @Override
        public ValueType getValueType()
        {
            return ValueType.ARRAY;
        }

        @Override
        public int size()
        {
            return array.values.size();
        }

        @Override
        public YamlValue get(
            int index)
        {
            return wrap(array.values.get(index));
        }

        @Override
        public YamlObject getObject(
            int index)
        {
            return get(index).asYamlObject();
        }

        @Override
        public YamlArray getArray(
            int index)
        {
            return get(index).asYamlArray();
        }

        @Override
        public YamlScalar getScalar(
            int index)
        {
            return get(index).asYamlScalar();
        }

        @Override
        public String getString(
            int index)
        {
            return getScalar(index).getString();
        }

        @Override
        public int getInt(
            int index)
        {
            return Integer.parseInt(getString(index));
        }

        @Override
        public long getLong(
            int index)
        {
            return Long.parseLong(getString(index));
        }

        @Override
        public double getDouble(
            int index)
        {
            return Double.parseDouble(getString(index));
        }

        @Override
        public boolean getBoolean(
            int index)
        {
            return Boolean.parseBoolean(getString(index));
        }

        @Override
        public boolean isNull(
            int index)
        {
            return get(index).getValueType() == ValueType.NULL;
        }
    }

    private static final class ScalarValue extends NodeValue implements YamlScalar
    {
        private final YamlScalarNode scalar;

        private ScalarValue(
            YamlScalarNode scalar)
        {
            super(scalar);
            this.scalar = scalar;
        }

        @Override
        public ValueType getValueType()
        {
            return switch (scalar.type)
            {
            case STRING -> ValueType.STRING;
            case NUMBER -> ValueType.NUMBER;
            case TRUE -> ValueType.TRUE;
            case FALSE -> ValueType.FALSE;
            case NULL -> ValueType.NULL;
            };
        }

        @Override
        public io.aklivity.zilla.runtime.common.yaml.YamlScalarType getType()
        {
            return switch (scalar.type)
            {
            case STRING -> io.aklivity.zilla.runtime.common.yaml.YamlScalarType.STRING;
            case NUMBER -> io.aklivity.zilla.runtime.common.yaml.YamlScalarType.NUMBER;
            case TRUE, FALSE -> io.aklivity.zilla.runtime.common.yaml.YamlScalarType.BOOLEAN;
            case NULL -> io.aklivity.zilla.runtime.common.yaml.YamlScalarType.NULL;
            };
        }

        @Override
        public String getString()
        {
            return switch (scalar.type)
            {
            case STRING, NUMBER -> scalar.value;
            case TRUE -> "true";
            case FALSE -> "false";
            case NULL -> null;
            };
        }
    }

}
