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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.Map;

import jakarta.json.JsonConfig;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReaderFactory;
import jakarta.json.spi.JsonProvider;

import org.junit.jupiter.api.Test;

public class JsonReaderUniqueKeysTest
{
    private final JsonReaderFactory strict = JsonProvider.provider().createReaderFactory(
        Map.of(JsonConfig.KEY_STRATEGY, JsonConfig.KeyStrategy.NONE));
    private final JsonReaderFactory lenient = JsonProvider.provider().createReaderFactory(Map.of());

    @Test
    public void shouldKeepLastValueWithLastStrategy()
    {
        JsonReaderFactory last = JsonProvider.provider()
            .createReaderFactory(Map.of(JsonConfig.KEY_STRATEGY, JsonConfig.KeyStrategy.LAST));

        assertEquals(2, last.createReader(new StringReader("{\"k\":1,\"k\":2}")).readObject().getInt("k"));
    }

    @Test
    public void shouldRejectFirstStrategyAsUnsupported()
    {
        assertThrows(IllegalArgumentException.class, () -> JsonProvider.provider()
            .createReaderFactory(Map.of(JsonConfig.KEY_STRATEGY, JsonConfig.KeyStrategy.FIRST)));
    }

    @Test
    public void shouldKeepLastValueByDefault()
    {
        JsonObject object = lenient.createReader(new StringReader("{\"k\":1,\"k\":2}")).readObject();

        assertEquals(2, object.getInt("k"));
    }

    @Test
    public void shouldRejectDuplicateKeyInRootObject()
    {
        assertThrows(JsonException.class, () -> strict.createReader(new StringReader("{\"k\":1,\"k\":2}")).readObject());
    }

    @Test
    public void shouldRejectDuplicateKeyInNestedObject()
    {
        assertThrows(JsonException.class,
            () -> strict.createReader(new StringReader("{\"o\":{\"k\":1,\"k\":2}}")).readObject());
        assertThrows(JsonException.class,
            () -> strict.createReader(new StringReader("{\"a\":[{\"k\":1,\"k\":2}]}")).readObject());
    }

    @Test
    public void shouldRejectDuplicateKeyFromInputStream()
    {
        assertThrows(JsonException.class,
            () -> strict.createReader(new ByteArrayInputStream("{\"k\":1,\"k\":2}".getBytes(UTF_8))).readObject());
    }

    @Test
    public void shouldAcceptRepeatedKeysAtDifferentLevels()
    {
        JsonObject object = strict.createReader(
            new StringReader("{\"k\":1,\"o\":{\"k\":2},\"a\":[{\"k\":3},{\"k\":4}]}")).readObject();

        assertEquals(1, object.getInt("k"));
    }
}
