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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;

public final class YamlGenerator implements JsonGenerator
{
    private final Writer writer;
    private final Deque<Context> stack;
    private YamlNode root;
    private boolean written;
    private boolean closed;

    public YamlGenerator(
        Writer writer)
    {
        this.writer = writer;
        this.stack = new ArrayDeque<>();
    }

    public YamlGenerator(
        OutputStream out)
    {
        this(new OutputStreamWriter(out, UTF_8));
    }

    @Override
    public JsonGenerator writeStartObject()
    {
        YamlObjectNode object = new YamlObjectNode(1, 1, 0);
        addValue(object);
        stack.push(Context.object(object));
        return this;
    }

    @Override
    public JsonGenerator writeStartObject(
        String name)
    {
        return writeKey(name).writeStartObject();
    }

    @Override
    public JsonGenerator writeKey(
        String name)
    {
        ensureOpen();
        Context context = requireObject();
        if (context.pendingKey != null)
        {
            throw new JsonException("Previous key has no value");
        }
        context.pendingKey = name;
        return this;
    }

    @Override
    public JsonGenerator writeStartArray()
    {
        YamlArrayNode array = new YamlArrayNode(1, 1, 0);
        addValue(array);
        stack.push(Context.array(array));
        return this;
    }

    @Override
    public JsonGenerator writeStartArray(
        String name)
    {
        return writeKey(name).writeStartArray();
    }

    @Override
    public JsonGenerator write(
        String name,
        JsonValue value)
    {
        return writeKey(name).write(value);
    }

    @Override
    public JsonGenerator write(
        String name,
        String value)
    {
        return writeKey(name).write(value);
    }

    @Override
    public JsonGenerator write(
        String name,
        BigInteger value)
    {
        return writeKey(name).write(value);
    }

    @Override
    public JsonGenerator write(
        String name,
        BigDecimal value)
    {
        return writeKey(name).write(value);
    }

    @Override
    public JsonGenerator write(
        String name,
        int value)
    {
        return writeKey(name).write(value);
    }

    @Override
    public JsonGenerator write(
        String name,
        long value)
    {
        return writeKey(name).write(value);
    }

    @Override
    public JsonGenerator write(
        String name,
        double value)
    {
        return writeKey(name).write(value);
    }

    @Override
    public JsonGenerator write(
        String name,
        boolean value)
    {
        return writeKey(name).write(value);
    }

    @Override
    public JsonGenerator writeNull(
        String name)
    {
        return writeKey(name).writeNull();
    }

    @Override
    public JsonGenerator writeEnd()
    {
        ensureOpen();
        if (stack.isEmpty())
        {
            throw new JsonException("No YAML structure to end");
        }
        Context context = stack.peek();
        if (context.pendingKey != null)
        {
            throw new JsonException("Object key has no value");
        }
        stack.pop();
        return this;
    }

    @Override
    public JsonGenerator write(
        JsonValue value)
    {
        addValue(fromJsonValue(value));
        return this;
    }

    @Override
    public JsonGenerator write(
        String value)
    {
        addValue(YamlScalarNode.string(value, 1, 1, 0));
        return this;
    }

    @Override
    public JsonGenerator write(
        BigDecimal value)
    {
        addValue(YamlScalarNode.number(value.toString(), 1, 1, 0));
        return this;
    }

    @Override
    public JsonGenerator write(
        BigInteger value)
    {
        addValue(YamlScalarNode.number(value.toString(), 1, 1, 0));
        return this;
    }

    @Override
    public JsonGenerator write(
        int value)
    {
        addValue(YamlScalarNode.number(Integer.toString(value), 1, 1, 0));
        return this;
    }

    @Override
    public JsonGenerator write(
        long value)
    {
        addValue(YamlScalarNode.number(Long.toString(value), 1, 1, 0));
        return this;
    }

    @Override
    public JsonGenerator write(
        double value)
    {
        if (!Double.isFinite(value))
        {
            throw new JsonException("Non-finite double values are not valid JSON values");
        }
        addValue(YamlScalarNode.number(Double.toString(value), 1, 1, 0));
        return this;
    }

    @Override
    public JsonGenerator write(
        boolean value)
    {
        addValue(YamlScalarNode.literal(value ? YamlScalarType.TRUE : YamlScalarType.FALSE, 1, 1, 0));
        return this;
    }

    @Override
    public JsonGenerator writeNull()
    {
        addValue(YamlScalarNode.literal(YamlScalarType.NULL, 1, 1, 0));
        return this;
    }

    @Override
    public void close()
    {
        if (!closed)
        {
            writeDocument();
            try
            {
                writer.close();
            }
            catch (IOException ex)
            {
                throw new JsonException(ex.getMessage(), ex);
            }
            closed = true;
        }
    }

    @Override
    public void flush()
    {
        writeDocument();
        try
        {
            writer.flush();
        }
        catch (IOException ex)
        {
            throw new JsonException(ex.getMessage(), ex);
        }
    }

    private void addValue(
        YamlNode value)
    {
        ensureOpen();
        if (written)
        {
            throw new JsonException("YAML document has already been written");
        }
        if (stack.isEmpty())
        {
            if (root != null)
            {
                throw new JsonException("Only one root YAML value is supported");
            }
            root = value;
        }
        else
        {
            Context context = stack.peek();
            if (context.object != null)
            {
                if (context.pendingKey == null)
                {
                    throw new JsonException("Expected an object key");
                }
                context.object.add(new YamlEntry(context.pendingKey, value, 1, 1, 0));
                context.pendingKey = null;
            }
            else
            {
                context.array.add(value);
            }
        }
    }

    private Context requireObject()
    {
        if (stack.isEmpty() || stack.peek().object == null)
        {
            throw new JsonException("Expected object context");
        }
        return stack.peek();
    }

    private void writeDocument()
    {
        ensureOpen();
        if (!stack.isEmpty())
        {
            throw new JsonException("YAML document is incomplete");
        }
        if (!written && root != null)
        {
            try
            {
                YamlEmitter.write(root, writer);
            }
            catch (IOException ex)
            {
                throw new JsonException(ex.getMessage(), ex);
            }
            written = true;
        }
    }

    private void ensureOpen()
    {
        if (closed)
        {
            throw new JsonException("YAML generator is closed");
        }
    }

    private static YamlNode fromJsonValue(
        JsonValue value)
    {
        return switch (value.getValueType())
        {
        case OBJECT -> fromJsonObject(value.asJsonObject());
        case ARRAY -> fromJsonArray(value.asJsonArray());
        case STRING -> YamlScalarNode.string(((JsonString) value).getString(), 1, 1, 0);
        case NUMBER -> YamlScalarNode.number(((JsonNumber) value).toString(), 1, 1, 0);
        case TRUE -> YamlScalarNode.literal(YamlScalarType.TRUE, 1, 1, 0);
        case FALSE -> YamlScalarNode.literal(YamlScalarType.FALSE, 1, 1, 0);
        case NULL -> YamlScalarNode.literal(YamlScalarType.NULL, 1, 1, 0);
        };
    }

    private static YamlObjectNode fromJsonObject(
        JsonObject value)
    {
        YamlObjectNode object = new YamlObjectNode(1, 1, 0);
        for (Map.Entry<String, JsonValue> entry : value.entrySet())
        {
            object.add(new YamlEntry(entry.getKey(), fromJsonValue(entry.getValue()), 1, 1, 0));
        }
        return object;
    }

    private static YamlArrayNode fromJsonArray(
        JsonArray value)
    {
        YamlArrayNode array = new YamlArrayNode(1, 1, 0);
        for (JsonValue element : value)
        {
            array.add(fromJsonValue(element));
        }
        return array;
    }

    private static final class Context
    {
        final YamlObjectNode object;
        final YamlArrayNode array;
        String pendingKey;

        private Context(
            YamlObjectNode object,
            YamlArrayNode array)
        {
            this.object = object;
            this.array = array;
        }

        static Context object(
            YamlObjectNode object)
        {
            return new Context(object, null);
        }

        static Context array(
            YamlArrayNode array)
        {
            return new Context(null, array);
        }
    }
}
