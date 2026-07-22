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
package io.aklivity.zilla.runtime.common.json.internal.json;

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

public final class JsonTextGeneratorImpl implements JsonGenerator
{
    private static final String INDENT = "    ";

    private final Writer writer;
    private final Deque<Context> stack;
    private final boolean pretty;
    private boolean closed;
    private boolean afterKey;
    private boolean rooted;

    public JsonTextGeneratorImpl(
        Writer writer)
    {
        this(writer, false);
    }

    public JsonTextGeneratorImpl(
        OutputStream out)
    {
        this(new OutputStreamWriter(out, UTF_8));
    }

    public JsonTextGeneratorImpl(
        Writer writer,
        boolean pretty)
    {
        this.writer = writer;
        this.stack = new ArrayDeque<>();
        this.pretty = pretty;
    }

    public JsonTextGeneratorImpl(
        OutputStream out,
        boolean pretty)
    {
        this(new OutputStreamWriter(out, UTF_8), pretty);
    }

    @Override
    public JsonGenerator writeStartObject()
    {
        preValue();
        emit('{');
        stack.push(new Context(false));
        return this;
    }

    @Override
    public JsonGenerator writeStartObject(
        String name)
    {
        return writeKey(name).writeStartObject();
    }

    @Override
    public JsonGenerator writeStartArray()
    {
        preValue();
        emit('[');
        stack.push(new Context(true));
        return this;
    }

    @Override
    public JsonGenerator writeStartArray(
        String name)
    {
        return writeKey(name).writeStartArray();
    }

    @Override
    public JsonGenerator writeKey(
        String name)
    {
        ensureOpen();
        Context context = requireObject();
        if (context.members)
        {
            emit(',');
        }
        context.members = true;
        if (pretty)
        {
            emitNewLine(stack.size());
        }
        emitString(name);
        emit(':');
        if (pretty)
        {
            emit(' ');
        }
        afterKey = true;
        return this;
    }

    @Override
    public JsonGenerator writeEnd()
    {
        ensureOpen();
        if (stack.isEmpty())
        {
            throw new JsonException("No JSON structure to end");
        }
        Context context = stack.pop();
        if (pretty && context.members)
        {
            emitNewLine(stack.size());
        }
        emit(context.array ? ']' : '}');
        return this;
    }

    @Override
    public JsonGenerator write(
        String value)
    {
        preValue();
        emitString(value);
        return this;
    }

    @Override
    public JsonGenerator write(
        BigDecimal value)
    {
        preValue();
        emit(value.toString());
        return this;
    }

    @Override
    public JsonGenerator write(
        BigInteger value)
    {
        preValue();
        emit(value.toString());
        return this;
    }

    @Override
    public JsonGenerator write(
        int value)
    {
        preValue();
        emit(Integer.toString(value));
        return this;
    }

    @Override
    public JsonGenerator write(
        long value)
    {
        preValue();
        emit(Long.toString(value));
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
        preValue();
        emit(Double.toString(value));
        return this;
    }

    @Override
    public JsonGenerator write(
        boolean value)
    {
        preValue();
        emit(value ? "true" : "false");
        return this;
    }

    @Override
    public JsonGenerator writeNull()
    {
        preValue();
        emit("null");
        return this;
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
    public JsonGenerator write(
        JsonValue value)
    {
        switch (value.getValueType())
        {
        case OBJECT -> writeObject(value.asJsonObject());
        case ARRAY -> writeArray(value.asJsonArray());
        case STRING -> write(((JsonString) value).getString());
        case NUMBER -> writeNumber((JsonNumber) value);
        case TRUE -> write(true);
        case FALSE -> write(false);
        case NULL -> writeNull();
        default -> throw new JsonException("Unexpected JSON value: " + value);
        }
        return this;
    }

    @Override
    public void close()
    {
        if (!closed)
        {
            ensureComplete();
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
        try
        {
            writer.flush();
        }
        catch (IOException ex)
        {
            throw new JsonException(ex.getMessage(), ex);
        }
    }

    private void writeObject(
        JsonObject object)
    {
        writeStartObject();
        for (Map.Entry<String, JsonValue> entry : object.entrySet())
        {
            write(entry.getKey(), entry.getValue());
        }
        writeEnd();
    }

    private void writeArray(
        JsonArray array)
    {
        writeStartArray();
        for (JsonValue element : array)
        {
            write(element);
        }
        writeEnd();
    }

    private void writeNumber(
        JsonNumber number)
    {
        preValue();
        emit(number.toString());
    }

    private Context requireObject()
    {
        if (stack.isEmpty() || stack.peek().array)
        {
            throw new JsonException("Expected object context");
        }
        return stack.peek();
    }

    private void preValue()
    {
        ensureOpen();
        if (afterKey)
        {
            afterKey = false;
        }
        else if (stack.isEmpty())
        {
            if (rooted)
            {
                throw new JsonException("Only one root JSON value is supported");
            }
            rooted = true;
        }
        else
        {
            Context context = stack.peek();
            if (!context.array)
            {
                throw new JsonException("Expected an object key");
            }
            if (context.members)
            {
                emit(',');
            }
            context.members = true;
            if (pretty)
            {
                emitNewLine(stack.size());
            }
        }
    }

    private void ensureOpen()
    {
        if (closed)
        {
            throw new JsonException("JSON generator is closed");
        }
    }

    private void ensureComplete()
    {
        if (!stack.isEmpty())
        {
            throw new JsonException("JSON document is incomplete");
        }
    }

    private void emitString(
        String value)
    {
        emit('"');
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            switch (c)
            {
            case '"' -> emit("\\\"");
            case '\\' -> emit("\\\\");
            case '\b' -> emit("\\b");
            case '\f' -> emit("\\f");
            case '\n' -> emit("\\n");
            case '\r' -> emit("\\r");
            case '\t' -> emit("\\t");
            default ->
            {
                if (c < 0x20)
                {
                    emit(String.format("\\u%04x", (int) c));
                }
                else
                {
                    emit(c);
                }
            }
            }
        }
        emit('"');
    }

    private void emitNewLine(
        int depth)
    {
        emit('\n');
        for (int i = 0; i < depth; i++)
        {
            emit(INDENT);
        }
    }

    private void emit(
        char value)
    {
        try
        {
            writer.write(value);
        }
        catch (IOException ex)
        {
            throw new JsonException(ex.getMessage(), ex);
        }
    }

    private void emit(
        String value)
    {
        try
        {
            writer.write(value);
        }
        catch (IOException ex)
        {
            throw new JsonException(ex.getMessage(), ex);
        }
    }

    private static final class Context
    {
        private final boolean array;
        private boolean members;

        private Context(
            boolean array)
        {
            this.array = array;
        }
    }
}
