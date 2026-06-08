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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.common.yaml.YamlArray;
import io.aklivity.zilla.runtime.common.yaml.YamlArrayBuilder;
import io.aklivity.zilla.runtime.common.yaml.YamlBuilderFactory;
import io.aklivity.zilla.runtime.common.yaml.YamlObject;
import io.aklivity.zilla.runtime.common.yaml.YamlObjectBuilder;
import io.aklivity.zilla.runtime.common.yaml.YamlValue;

public final class YamlBuilderFactoryImpl implements YamlBuilderFactory
{
    private final Map<String, ?> config;

    public YamlBuilderFactoryImpl(
        Map<String, ?> config)
    {
        this.config = config == null ? Map.of() : Map.copyOf(config);
    }

    @Override
    public YamlObjectBuilder createObjectBuilder()
    {
        return new ObjectBuilder();
    }

    @Override
    public YamlObjectBuilder createObjectBuilder(
        YamlObject object)
    {
        ObjectBuilder builder = new ObjectBuilder();
        YamlObjectNode node = (YamlObjectNode) YamlValues.node(object);
        YamlValues.copyMetadata(node, builder.object);
        for (YamlEntry entry : node.entries)
        {
            builder.object.add(entry.key != null ?
                new YamlEntry(entry.key, entry.value, entry.line, entry.column, entry.offset) :
                new YamlEntry(entry.name, entry.value, entry.line, entry.column, entry.offset, entry.merged));
        }
        return builder;
    }

    @Override
    public YamlObjectBuilder createObjectBuilder(
        Map<String, ?> map)
    {
        return createObjectBuilder().addAll(map);
    }

    @Override
    public YamlArrayBuilder createArrayBuilder()
    {
        return new ArrayBuilder();
    }

    @Override
    public YamlArrayBuilder createArrayBuilder(
        YamlArray array)
    {
        ArrayBuilder builder = new ArrayBuilder();
        YamlArrayNode node = (YamlArrayNode) YamlValues.node(array);
        YamlValues.copyMetadata(node, builder.array);
        builder.array.values.addAll(node.values);
        return builder;
    }

    @Override
    public YamlArrayBuilder createArrayBuilder(
        Collection<?> collection)
    {
        return createArrayBuilder().addAll(collection);
    }

    @Override
    public Map<String, ?> getConfigInUse()
    {
        return config;
    }

    static YamlValue value(
        String value)
    {
        return YamlValues.wrap(YamlScalarNode.string(value, 1, 1, 0));
    }

    static YamlValue value(
        BigDecimal value)
    {
        return YamlValues.wrap(YamlScalarNode.number(value.toString(), 1, 1, 0));
    }

    static YamlValue value(
        BigInteger value)
    {
        return YamlValues.wrap(YamlScalarNode.number(value.toString(), 1, 1, 0));
    }

    static YamlValue value(
        Number value)
    {
        return switch (value)
        {
        case BigDecimal decimal -> value(decimal);
        case BigInteger integer -> value(integer);
        case Double doub -> doubleValue(doub);
        case Float flt -> doubleValue(flt.doubleValue());
        default -> YamlValues.wrap(YamlScalarNode.number(value.toString(), 1, 1, 0));
        };
    }

    static YamlValue value(
        int value)
    {
        return YamlValues.wrap(YamlScalarNode.number(Integer.toString(value), 1, 1, 0));
    }

    static YamlValue value(
        long value)
    {
        return YamlValues.wrap(YamlScalarNode.number(Long.toString(value), 1, 1, 0));
    }

    static YamlValue value(
        double value)
    {
        return doubleValue(value);
    }

    static YamlValue value(
        boolean value)
    {
        return YamlValues.wrap(YamlScalarNode.literal(value ? YamlScalarType.TRUE : YamlScalarType.FALSE, 1, 1, 0));
    }

    static YamlValue nullValue()
    {
        return YamlValues.wrap(YamlScalarNode.literal(YamlScalarType.NULL, 1, 1, 0));
    }

    private static YamlValue fromObject(
        Object value)
    {
        if (value == null)
        {
            return nullValue();
        }
        if (value instanceof YamlValue yaml)
        {
            return yaml;
        }
        if (value instanceof YamlObjectBuilder object)
        {
            return object.build();
        }
        if (value instanceof YamlArrayBuilder array)
        {
            return array.build();
        }
        if (value instanceof String string)
        {
            return value(string);
        }
        if (value instanceof Boolean bool)
        {
            return value(bool);
        }
        if (value instanceof Number number)
        {
            return value(number);
        }
        if (value instanceof Map<?, ?> map)
        {
            ObjectBuilder builder = new ObjectBuilder();
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                builder.add(String.valueOf(entry.getKey()), fromObject(entry.getValue()));
            }
            return builder.build();
        }
        if (value instanceof Collection<?> collection)
        {
            return new ArrayBuilder().addAll(collection).build();
        }
        throw new IllegalArgumentException("Unsupported YAML builder value: " + value.getClass());
    }

    private static YamlValue doubleValue(
        double value)
    {
        if (!Double.isFinite(value))
        {
            throw new IllegalArgumentException("Non-finite YAML numbers are not valid JSON values");
        }
        return YamlValues.wrap(YamlScalarNode.number(Double.toString(value), 1, 1, 0));
    }

    private static final class ObjectBuilder implements YamlObjectBuilder
    {
        private final YamlObjectNode object;

        private ObjectBuilder()
        {
            this.object = new YamlObjectNode(1, 1, 0);
        }

        @Override
        public YamlObjectBuilder add(
            String name,
            YamlValue value)
        {
            object.add(new YamlEntry(name, YamlValues.node(value), 1, 1, 0));
            return this;
        }

        @Override
        public YamlObjectBuilder add(
            YamlValue key,
            YamlValue value)
        {
            object.add(new YamlEntry(YamlValues.node(key), YamlValues.node(value), 1, 1, 0));
            return this;
        }

        @Override
        public YamlObjectBuilder add(
            String name,
            String value)
        {
            return add(name, value(value));
        }

        @Override
        public YamlObjectBuilder add(
            String name,
            BigDecimal value)
        {
            return add(name, value(value));
        }

        @Override
        public YamlObjectBuilder add(
            String name,
            BigInteger value)
        {
            return add(name, value(value));
        }

        @Override
        public YamlObjectBuilder add(
            String name,
            int value)
        {
            return add(name, value(value));
        }

        @Override
        public YamlObjectBuilder add(
            String name,
            long value)
        {
            return add(name, value(value));
        }

        @Override
        public YamlObjectBuilder add(
            String name,
            double value)
        {
            return add(name, value(value));
        }

        @Override
        public YamlObjectBuilder add(
            String name,
            boolean value)
        {
            return add(name, value(value));
        }

        @Override
        public YamlObjectBuilder addNull(
            String name)
        {
            return add(name, nullValue());
        }

        @Override
        public YamlObjectBuilder add(
            String name,
            YamlObjectBuilder builder)
        {
            return add(name, builder.build());
        }

        @Override
        public YamlObjectBuilder add(
            String name,
            YamlArrayBuilder builder)
        {
            return add(name, builder.build());
        }

        @Override
        public YamlObjectBuilder addAll(
            Map<String, ?> map)
        {
            map.forEach((name, value) -> add(name, fromObject(value)));
            return this;
        }

        @Override
        public YamlObjectBuilder addAll(
            YamlObjectBuilder builder)
        {
            YamlObjectNode node = (YamlObjectNode) YamlValues.node(builder.build());
            object.entries.addAll(node.entries);
            return this;
        }

        @Override
        public YamlObjectBuilder remove(
            String name)
        {
            object.entries.removeIf(e -> name.equals(e.name));
            return this;
        }

        @Override
        public YamlObjectBuilder withTag(
            String tag)
        {
            object.tag = tag;
            return this;
        }

        @Override
        public YamlObjectBuilder withAnchor(
            String anchor)
        {
            object.anchor = anchor;
            return this;
        }

        @Override
        public YamlObjectBuilder withStyle(
            String style)
        {
            object.style = style;
            return this;
        }

        @Override
        public YamlObjectBuilder withLeadingComment(
            String comment)
        {
            YamlValues.wrap(object).withLeadingComment(comment);
            return this;
        }

        @Override
        public YamlObjectBuilder withLeadingComments(
            List<String> comments)
        {
            object.leadingComments = comments != null && !comments.isEmpty() ? List.copyOf(comments) : null;
            return this;
        }

        @Override
        public YamlObjectBuilder withLineComment(
            String comment)
        {
            object.lineComment = comment;
            return this;
        }

        @Override
        public YamlObject build()
        {
            return YamlValues.wrap(object).asYamlObject();
        }
    }

    private static final class ArrayBuilder implements YamlArrayBuilder
    {
        private final YamlArrayNode array;

        private ArrayBuilder()
        {
            this.array = new YamlArrayNode(1, 1, 0);
        }

        @Override
        public YamlArrayBuilder add(
            YamlValue value)
        {
            array.add(YamlValues.node(value));
            return this;
        }

        @Override
        public YamlArrayBuilder add(
            String value)
        {
            return add(value(value));
        }

        @Override
        public YamlArrayBuilder add(
            BigDecimal value)
        {
            return add(value(value));
        }

        @Override
        public YamlArrayBuilder add(
            BigInteger value)
        {
            return add(value(value));
        }

        @Override
        public YamlArrayBuilder add(
            int value)
        {
            return add(value(value));
        }

        @Override
        public YamlArrayBuilder add(
            long value)
        {
            return add(value(value));
        }

        @Override
        public YamlArrayBuilder add(
            double value)
        {
            return add(value(value));
        }

        @Override
        public YamlArrayBuilder add(
            boolean value)
        {
            return add(value(value));
        }

        @Override
        public YamlArrayBuilder addNull()
        {
            return add(nullValue());
        }

        @Override
        public YamlArrayBuilder add(
            YamlObjectBuilder builder)
        {
            return add(builder.build());
        }

        @Override
        public YamlArrayBuilder add(
            YamlArrayBuilder builder)
        {
            return add(builder.build());
        }

        @Override
        public YamlArrayBuilder addAll(
            Collection<?> collection)
        {
            collection.forEach(value -> add(fromObject(value)));
            return this;
        }

        @Override
        public YamlArrayBuilder addAll(
            YamlArrayBuilder builder)
        {
            YamlArrayNode node = (YamlArrayNode) YamlValues.node(builder.build());
            array.values.addAll(node.values);
            return this;
        }

        @Override
        public YamlArrayBuilder add(
            int index,
            YamlValue value)
        {
            array.values.add(index, YamlValues.node(value));
            return this;
        }

        @Override
        public YamlArrayBuilder add(
            int index,
            String value)
        {
            return add(index, value(value));
        }

        @Override
        public YamlArrayBuilder add(
            int index,
            BigDecimal value)
        {
            return add(index, value(value));
        }

        @Override
        public YamlArrayBuilder add(
            int index,
            BigInteger value)
        {
            return add(index, value(value));
        }

        @Override
        public YamlArrayBuilder add(
            int index,
            int value)
        {
            return add(index, value(value));
        }

        @Override
        public YamlArrayBuilder add(
            int index,
            long value)
        {
            return add(index, value(value));
        }

        @Override
        public YamlArrayBuilder add(
            int index,
            double value)
        {
            return add(index, value(value));
        }

        @Override
        public YamlArrayBuilder add(
            int index,
            boolean value)
        {
            return add(index, value(value));
        }

        @Override
        public YamlArrayBuilder addNull(
            int index)
        {
            return add(index, nullValue());
        }

        @Override
        public YamlArrayBuilder add(
            int index,
            YamlObjectBuilder builder)
        {
            return add(index, builder.build());
        }

        @Override
        public YamlArrayBuilder add(
            int index,
            YamlArrayBuilder builder)
        {
            return add(index, builder.build());
        }

        @Override
        public YamlArrayBuilder set(
            int index,
            YamlValue value)
        {
            array.values.set(index, YamlValues.node(value));
            return this;
        }

        @Override
        public YamlArrayBuilder set(
            int index,
            String value)
        {
            return set(index, value(value));
        }

        @Override
        public YamlArrayBuilder set(
            int index,
            BigDecimal value)
        {
            return set(index, value(value));
        }

        @Override
        public YamlArrayBuilder set(
            int index,
            BigInteger value)
        {
            return set(index, value(value));
        }

        @Override
        public YamlArrayBuilder set(
            int index,
            int value)
        {
            return set(index, value(value));
        }

        @Override
        public YamlArrayBuilder set(
            int index,
            long value)
        {
            return set(index, value(value));
        }

        @Override
        public YamlArrayBuilder set(
            int index,
            double value)
        {
            return set(index, value(value));
        }

        @Override
        public YamlArrayBuilder set(
            int index,
            boolean value)
        {
            return set(index, value(value));
        }

        @Override
        public YamlArrayBuilder setNull(
            int index)
        {
            return set(index, nullValue());
        }

        @Override
        public YamlArrayBuilder set(
            int index,
            YamlObjectBuilder builder)
        {
            return set(index, builder.build());
        }

        @Override
        public YamlArrayBuilder set(
            int index,
            YamlArrayBuilder builder)
        {
            return set(index, builder.build());
        }

        @Override
        public YamlArrayBuilder remove(
            int index)
        {
            array.values.remove(index);
            return this;
        }

        @Override
        public YamlArrayBuilder withTag(
            String tag)
        {
            array.tag = tag;
            return this;
        }

        @Override
        public YamlArrayBuilder withAnchor(
            String anchor)
        {
            array.anchor = anchor;
            return this;
        }

        @Override
        public YamlArrayBuilder withStyle(
            String style)
        {
            array.style = style;
            return this;
        }

        @Override
        public YamlArrayBuilder withLeadingComment(
            String comment)
        {
            YamlValues.wrap(array).withLeadingComment(comment);
            return this;
        }

        @Override
        public YamlArrayBuilder withLeadingComments(
            List<String> comments)
        {
            array.leadingComments = comments != null && !comments.isEmpty() ? List.copyOf(comments) : null;
            return this;
        }

        @Override
        public YamlArrayBuilder withLineComment(
            String comment)
        {
            array.lineComment = comment;
            return this;
        }

        @Override
        public YamlArray build()
        {
            return YamlValues.wrap(array).asYamlArray();
        }
    }
}
