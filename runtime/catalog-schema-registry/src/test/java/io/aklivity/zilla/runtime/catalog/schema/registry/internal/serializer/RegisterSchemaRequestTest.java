/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.serializer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RegisterSchemaRequestTest
{
    private RegisterSchemaRequest request = new RegisterSchemaRequest();

    @Test
    public void verifyValidRequestBody()
    {
        String schema = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
                "{\"name\":\"status\",\"type\":\"string\"}]," +
                "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

        String expected = "{\"schema\":\"{\\\"fields\\\":[{\\\"name\\\":\\\"id\\\",\\\"type\\\":\\\"string\\\"}," +
                "{\\\"name\\\":\\\"status\\\",\\\"type\\\":\\\"string\\\"}],\\\"name\\\":\\\"" +
                "Event\\\",\\\"namespace\\\":\\\"io.aklivity.example\\\",\\\"type\\\":\\\"record\\\"}\",\"schemaType\":\"AVRO\"}";

        String body = request.buildBody("avro", schema);

        assertEquals(expected, body);
    }

    @Test
    public void verifyValidResponse()
    {
        String response = "{\"id\":1}";

        int schemaId = request.resolveResponse(response);

        assertEquals(1, schemaId);
    }

    @Test
    public void verifyInValidResponse()
    {
        String response = "unexpected response";

        int schemaId = request.resolveResponse(response);

        assertEquals(0, schemaId);
    }
}
