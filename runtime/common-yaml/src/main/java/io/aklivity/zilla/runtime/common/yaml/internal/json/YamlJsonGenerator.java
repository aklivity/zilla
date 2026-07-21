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
package io.aklivity.zilla.runtime.common.yaml.internal.json;

import static io.aklivity.zilla.runtime.common.yaml.internal.YamlGeneratorStack.ARRAY_ELEMENT;
import static io.aklivity.zilla.runtime.common.yaml.internal.YamlGeneratorStack.OBJECT_VALUE;
import static io.aklivity.zilla.runtime.common.yaml.internal.YamlGeneratorStack.ROOT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;

import io.aklivity.zilla.runtime.common.yaml.internal.YamlEmitter;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlGeneratorStack;

public final class YamlJsonGenerator implements JsonGenerator
{
    private final Writer writer;
    private final YamlGeneratorStack stack;
    private final char[] numberBuffer;
    private boolean rootDone;
    private boolean closed;

    public YamlJsonGenerator(
        Writer writer)
    {
        this.writer = writer;
        this.stack = new YamlGeneratorStack();
        this.numberBuffer = new char[20];
    }

    public YamlJsonGenerator(
        OutputStream out)
    {
        this(new OutputStreamWriter(out, UTF_8));
    }

    @Override
    public JsonGenerator writeStartObject()
    {
        beginContainer(false);
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
        beginContainer(true);
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
        if (stack.isEmpty() || stack.array())
        {
            throw new JsonException("Expected object context");
        }
        if (stack.pendingKey() != null)
        {
            throw new JsonException("Previous key has no value");
        }
        stack.pendingKey(name);
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
    public JsonGenerator writeEnd()
    {
        ensureOpen();
        if (stack.isEmpty())
        {
            throw new JsonException("No YAML structure to end");
        }
        if (!stack.array() && stack.pendingKey() != null)
        {
            throw new JsonException("Object key has no value");
        }
        boolean opened = stack.opened();
        int kind = stack.kind();
        boolean array = stack.array();
        int introIndent = stack.introIndent();
        String introKey = stack.introKey();
        stack.pop();
        if (!opened)
        {
            closeEmpty(kind, array, introIndent, introKey);
        }
        if (stack.isEmpty())
        {
            rootDone = true;
        }
        return this;
    }

    @Override
    public JsonGenerator write(
        JsonValue value)
    {
        writeValue(value);
        return this;
    }

    @Override
    public JsonGenerator write(
        String value)
    {
        beginScalarValue(value);
        return this;
    }

    @Override
    public JsonGenerator write(
        BigDecimal value)
    {
        beginScalar(value.toString());
        return this;
    }

    @Override
    public JsonGenerator write(
        BigInteger value)
    {
        beginScalar(value.toString());
        return this;
    }

    @Override
    public JsonGenerator write(
        int value)
    {
        beginInteger(value);
        return this;
    }

    @Override
    public JsonGenerator write(
        long value)
    {
        beginInteger(value);
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
        beginScalar(Double.toString(value));
        return this;
    }

    @Override
    public JsonGenerator write(
        boolean value)
    {
        beginScalar(value ? "true" : "false");
        return this;
    }

    @Override
    public JsonGenerator writeNull()
    {
        beginScalar("null");
        return this;
    }

    @Override
    public void close()
    {
        if (!closed)
        {
            if (!stack.isEmpty())
            {
                throw new JsonException("YAML document is incomplete");
            }
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
        ensureOpen();
        try
        {
            writer.flush();
        }
        catch (IOException ex)
        {
            throw new JsonException(ex.getMessage(), ex);
        }
    }

    private void beginScalar(
        String text)
    {
        beginScalarPrefix();
        emit(text);
        emit("\n");
    }

    private void beginScalarValue(
        String value)
    {
        beginScalarPrefix();
        emitScalar(value);
        emit("\n");
    }

    private void beginInteger(
        long value)
    {
        beginScalarPrefix();
        try
        {
            YamlEmitter.writeInteger(writer, value, numberBuffer);
        }
        catch (IOException ex)
        {
            throw new JsonException(ex.getMessage(), ex);
        }
        emit("\n");
    }

    private void beginScalarPrefix()
    {
        ensureOpen();
        if (stack.isEmpty())
        {
            requireRootAvailable();
            rootDone = true;
        }
        else
        {
            ensureOpened();
            if (stack.array())
            {
                stack.clearFirst();
                indent(stack.childIndent());
                emit("- ");
            }
            else
            {
                String key = requireKey();
                if (stack.firstInline() && stack.first())
                {
                    stack.clearFirstInline();
                }
                else
                {
                    indent(stack.childIndent());
                }
                emitScalar(key);
                emit(": ");
                stack.clearFirst();
            }
        }
    }

    private void beginContainer(
        boolean array)
    {
        ensureOpen();
        if (stack.isEmpty())
        {
            requireRootAvailable();
            stack.push(ROOT, array, 0, null);
        }
        else
        {
            ensureOpened();
            if (stack.array())
            {
                int childIndent = stack.childIndent();
                stack.clearFirst();
                stack.push(ARRAY_ELEMENT, array, childIndent, null);
            }
            else
            {
                String key = requireKey();
                int childIndent = stack.childIndent();
                stack.clearFirst();
                stack.push(OBJECT_VALUE, array, childIndent, key);
            }
        }
    }

    private void writeValue(
        JsonValue value)
    {
        switch (value.getValueType())
        {
        case OBJECT ->
        {
            beginContainer(false);
            for (Map.Entry<String, JsonValue> entry : value.asJsonObject().entrySet())
            {
                writeKey(entry.getKey());
                writeValue(entry.getValue());
            }
            writeEnd();
        }
        case ARRAY ->
        {
            beginContainer(true);
            for (JsonValue element : value.asJsonArray())
            {
                writeValue(element);
            }
            writeEnd();
        }
        case STRING -> beginScalarValue(((JsonString) value).getString());
        case NUMBER -> beginScalar(((JsonNumber) value).toString());
        case TRUE -> beginScalar("true");
        case FALSE -> beginScalar("false");
        case NULL -> beginScalar("null");
        }
    }

    private void ensureOpened()
    {
        if (!stack.opened())
        {
            stack.markOpened();
            switch (stack.kind())
            {
            case OBJECT_VALUE ->
            {
                indent(stack.introIndent());
                emitScalar(stack.introKey());
                emit(":\n");
            }
            case ARRAY_ELEMENT ->
            {
                indent(stack.introIndent());
                if (stack.array())
                {
                    emit("-\n");
                }
                else
                {
                    emit("- ");
                    stack.markFirstInline();
                }
            }
            default ->
            {
            }
            }
        }
    }

    private void closeEmpty(
        int kind,
        boolean array,
        int introIndent,
        String introKey)
    {
        String body = array ? "[]" : "{}";
        switch (kind)
        {
        case OBJECT_VALUE ->
        {
            indent(introIndent);
            emitScalar(introKey);
            emit(": ");
            emit(body);
        }
        case ARRAY_ELEMENT ->
        {
            indent(introIndent);
            emit("- ");
            emit(body);
        }
        default -> emit(body);
        }
        emit("\n");
    }

    private String requireKey()
    {
        String key = stack.pendingKey();
        if (key == null)
        {
            throw new JsonException("Expected an object key");
        }
        stack.pendingKey(null);
        return key;
    }

    private void requireRootAvailable()
    {
        if (rootDone)
        {
            throw new JsonException("Only one root YAML value is supported");
        }
    }

    private void indent(
        int depth)
    {
        for (int i = 0; i < depth; i++)
        {
            emit("  ");
        }
    }

    private void emit(
        String text)
    {
        try
        {
            writer.write(text);
        }
        catch (IOException ex)
        {
            throw new JsonException(ex.getMessage(), ex);
        }
    }

    private void emitScalar(
        String value)
    {
        try
        {
            YamlEmitter.writeScalar(writer, value);
        }
        catch (IOException ex)
        {
            throw new JsonException(ex.getMessage(), ex);
        }
    }

    private void ensureOpen()
    {
        if (closed)
        {
            throw new JsonException("YAML generator is closed");
        }
    }
}
