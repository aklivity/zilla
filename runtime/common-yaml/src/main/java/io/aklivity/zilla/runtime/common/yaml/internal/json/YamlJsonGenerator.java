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
package io.aklivity.zilla.runtime.common.yaml.internal.json;

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

import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;

import io.aklivity.zilla.runtime.common.yaml.internal.YamlEmitter;

public final class YamlJsonGenerator implements JsonGenerator
{
    private static final int ROOT = 0;
    private static final int OBJECT_VALUE = 1;
    private static final int ARRAY_ELEMENT = 2;

    private final Writer writer;
    private final Deque<Scope> stack;
    private boolean rootDone;
    private boolean closed;

    public YamlJsonGenerator(
        Writer writer)
    {
        this.writer = writer;
        this.stack = new ArrayDeque<>();
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
        if (stack.isEmpty() || stack.peek().array)
        {
            throw new JsonException("Expected object context");
        }
        Scope context = stack.peek();
        if (context.pendingKey != null)
        {
            throw new JsonException("Previous key has no value");
        }
        context.pendingKey = name;
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
        Scope context = stack.peek();
        if (!context.array && context.pendingKey != null)
        {
            throw new JsonException("Object key has no value");
        }
        stack.pop();
        if (!context.opened)
        {
            closeEmpty(context);
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
        beginScalar(YamlEmitter.formatPlain(value));
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
        beginScalar(Integer.toString(value));
        return this;
    }

    @Override
    public JsonGenerator write(
        long value)
    {
        beginScalar(Long.toString(value));
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
        ensureOpen();
        if (stack.isEmpty())
        {
            requireRootAvailable();
            emit(text);
            emit("\n");
            rootDone = true;
        }
        else
        {
            Scope parent = stack.peek();
            ensureOpened(parent);
            if (parent.array)
            {
                parent.first = false;
                indent(parent.childIndent);
                emit("- ");
                emit(text);
                emit("\n");
            }
            else
            {
                String key = requireKey(parent);
                if (parent.firstInline && parent.first)
                {
                    parent.firstInline = false;
                }
                else
                {
                    indent(parent.childIndent);
                }
                emit(YamlEmitter.formatPlain(key));
                emit(": ");
                emit(text);
                emit("\n");
                parent.first = false;
            }
        }
    }

    private void beginContainer(
        boolean array)
    {
        ensureOpen();
        Scope child;
        if (stack.isEmpty())
        {
            requireRootAvailable();
            child = new Scope(ROOT, array, 0, null);
        }
        else
        {
            Scope parent = stack.peek();
            ensureOpened(parent);
            if (parent.array)
            {
                parent.first = false;
                child = new Scope(ARRAY_ELEMENT, array, parent.childIndent, null);
            }
            else
            {
                String key = requireKey(parent);
                parent.first = false;
                child = new Scope(OBJECT_VALUE, array, parent.childIndent, key);
            }
        }
        stack.push(child);
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
        case STRING -> beginScalar(YamlEmitter.formatPlain(((JsonString) value).getString()));
        case NUMBER -> beginScalar(((JsonNumber) value).toString());
        case TRUE -> beginScalar("true");
        case FALSE -> beginScalar("false");
        case NULL -> beginScalar("null");
        }
    }

    private void ensureOpened(
        Scope scope)
    {
        if (!scope.opened)
        {
            scope.opened = true;
            switch (scope.introKind)
            {
            case ROOT -> scope.childIndent = 0;
            case OBJECT_VALUE ->
            {
                indent(scope.introIndent);
                emit(YamlEmitter.formatPlain(scope.introKey));
                emit(":\n");
                scope.childIndent = scope.introIndent + 1;
            }
            case ARRAY_ELEMENT ->
            {
                indent(scope.introIndent);
                if (scope.array)
                {
                    emit("-\n");
                }
                else
                {
                    emit("- ");
                    scope.firstInline = true;
                }
                scope.childIndent = scope.introIndent + 1;
            }
            default ->
            {
            }
            }
        }
    }

    private void closeEmpty(
        Scope scope)
    {
        String body = scope.array ? "[]" : "{}";
        switch (scope.introKind)
        {
        case ROOT -> emit(body);
        case OBJECT_VALUE ->
        {
            indent(scope.introIndent);
            emit(YamlEmitter.formatPlain(scope.introKey));
            emit(": ");
            emit(body);
        }
        case ARRAY_ELEMENT ->
        {
            indent(scope.introIndent);
            emit("- ");
            emit(body);
        }
        default ->
        {
        }
        }
        emit("\n");
    }

    private String requireKey(
        Scope object)
    {
        if (object.pendingKey == null)
        {
            throw new JsonException("Expected an object key");
        }
        String key = object.pendingKey;
        object.pendingKey = null;
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

    private void ensureOpen()
    {
        if (closed)
        {
            throw new JsonException("YAML generator is closed");
        }
    }

    private static final class Scope
    {
        private final int introKind;
        private final boolean array;
        private final int introIndent;
        private final String introKey;
        private int childIndent;
        private boolean opened;
        private boolean first = true;
        private boolean firstInline;
        private String pendingKey;

        private Scope(
            int introKind,
            boolean array,
            int introIndent,
            String introKey)
        {
            this.introKind = introKind;
            this.array = array;
            this.introIndent = introIndent;
            this.introKey = introKey;
        }
    }
}
