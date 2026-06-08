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

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;

/**
 * A {@link JsonParser} decorator base that forwards every method to {@link #delegate()}. Subclasses
 * override only the methods whose behavior they intercept (e.g. {@link #next()} or {@link
 * #getString()}) and supply the delegate via the abstract accessor.
 */
public abstract class ForwardingJsonParser implements JsonParser
{
    protected abstract JsonParser delegate();

    @Override
    public boolean hasNext()
    {
        return delegate().hasNext();
    }

    @Override
    public Event next()
    {
        return delegate().next();
    }

    @Override
    public String getString()
    {
        return delegate().getString();
    }

    @Override
    public boolean isIntegralNumber()
    {
        return delegate().isIntegralNumber();
    }

    @Override
    public int getInt()
    {
        return delegate().getInt();
    }

    @Override
    public long getLong()
    {
        return delegate().getLong();
    }

    @Override
    public BigDecimal getBigDecimal()
    {
        return delegate().getBigDecimal();
    }

    @Override
    public JsonLocation getLocation()
    {
        return delegate().getLocation();
    }

    @Override
    public JsonObject getObject()
    {
        return delegate().getObject();
    }

    @Override
    public JsonValue getValue()
    {
        return delegate().getValue();
    }

    @Override
    public JsonArray getArray()
    {
        return delegate().getArray();
    }

    @Override
    public Stream<JsonValue> getArrayStream()
    {
        return delegate().getArrayStream();
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream()
    {
        return delegate().getObjectStream();
    }

    @Override
    public Stream<JsonValue> getValueStream()
    {
        return delegate().getValueStream();
    }

    @Override
    public void skipObject()
    {
        delegate().skipObject();
    }

    @Override
    public void skipArray()
    {
        delegate().skipArray();
    }

    @Override
    public void close()
    {
        delegate().close();
    }
}
