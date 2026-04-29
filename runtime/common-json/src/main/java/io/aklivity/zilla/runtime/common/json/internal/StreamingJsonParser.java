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
package io.aklivity.zilla.runtime.common.json.internal;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

import io.aklivity.zilla.runtime.common.json.StreamingJson;

public final class StreamingJsonParser implements JsonParser
{
    private final InputStream in;
    private final StreamingJsonTokenizer tokenizer;
    private final StreamingJsonLocation location;

    public StreamingJsonParser(
        InputStream in)
    {
        this(in, Map.of());
    }

    public StreamingJsonParser(
        InputStream in,
        Map<String, ?> config)
    {
        if (!in.markSupported())
        {
            throw new IllegalArgumentException("InputStream must support mark/reset");
        }
        this.in = in;
        this.tokenizer = new StreamingJsonTokenizer(
            pathList(config, StreamingJson.PATH_INCLUDES),
            pathList(config, StreamingJson.PATH_EXCLUDES),
            tokenMaxBytes(config));
        this.location = new StreamingJsonLocation(tokenizer);
    }

    @Override
    public boolean hasNext()
    {
        if (tokenizer.event() != null)
        {
            return true;
        }
        try
        {
            return tokenizer.advance(in);
        }
        catch (IOException ex)
        {
            throw new JsonParsingException(ex.getMessage(), ex, location);
        }
    }

    @Override
    public Event next()
    {
        if (tokenizer.event() == null && !hasNext())
        {
            throw new JsonParsingException("No more events", location);
        }
        Event e = tokenizer.event();
        tokenizer.clearEvent();
        return e;
    }

    @Override
    public String getString()
    {
        final String value = tokenizer.stringValue();
        if (value == null && !tokenizer.valueReadable())
        {
            throw new IllegalStateException("value not readable; configure path via " +
                "StreamingJson.PATH_INCLUDES (or remove from PATH_EXCLUDES)");
        }
        return value;
    }

    @Override
    public boolean isIntegralNumber()
    {
        String v = tokenizer.stringValue();
        if (v == null)
        {
            throw new IllegalStateException("Not a number");
        }
        for (int i = 0; i < v.length(); i++)
        {
            char c = v.charAt(i);
            if (c == '.' || c == 'e' || c == 'E')
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getInt()
    {
        return Integer.parseInt(tokenizer.stringValue());
    }

    @Override
    public long getLong()
    {
        return Long.parseLong(tokenizer.stringValue());
    }

    @Override
    public BigDecimal getBigDecimal()
    {
        return new BigDecimal(tokenizer.stringValue());
    }

    @Override
    public JsonLocation getLocation()
    {
        return location;
    }

    @Override
    public void close()
    {
    }

    @Override
    public jakarta.json.JsonObject getObject()
    {
        throw new UnsupportedOperationException("getObject not yet supported");
    }

    @Override
    public jakarta.json.JsonValue getValue()
    {
        throw new UnsupportedOperationException("getValue not yet supported");
    }

    @Override
    public jakarta.json.JsonArray getArray()
    {
        throw new UnsupportedOperationException("getArray not yet supported");
    }

    @Override
    public java.util.stream.Stream<jakarta.json.JsonValue> getArrayStream()
    {
        throw new UnsupportedOperationException("getArrayStream not yet supported");
    }

    @Override
    public java.util.stream.Stream<java.util.Map.Entry<String, jakarta.json.JsonValue>> getObjectStream()
    {
        throw new UnsupportedOperationException("getObjectStream not yet supported");
    }

    @Override
    public java.util.stream.Stream<jakarta.json.JsonValue> getValueStream()
    {
        throw new UnsupportedOperationException("getValueStream not yet supported");
    }

    @Override
    public void skipObject()
    {
        throw new UnsupportedOperationException("skipObject not yet supported; " +
            "use event-by-event consumption instead");
    }

    @Override
    public void skipArray()
    {
        throw new UnsupportedOperationException("skipArray not yet supported; " +
            "use event-by-event consumption instead");
    }

    @SuppressWarnings("unchecked")
    private static List<String> pathList(
        Map<String, ?> config,
        String key)
    {
        final Object raw = config.get(key);
        return raw == null ? List.of() : (List<String>) raw;
    }

    private static int tokenMaxBytes(
        Map<String, ?> config)
    {
        final Object raw = config.get(StreamingJson.TOKEN_MAX_BYTES);
        return raw == null ? Integer.MAX_VALUE : ((Number) raw).intValue();
    }
}
