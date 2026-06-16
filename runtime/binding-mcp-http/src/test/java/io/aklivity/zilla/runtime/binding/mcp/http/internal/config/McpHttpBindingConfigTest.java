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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.config;

import static io.aklivity.zilla.runtime.binding.mcp.http.internal.config.McpHttpBindingConfig.argPathValid;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class McpHttpBindingConfigTest
{
    private static final String FLAT = "{\"type\":\"object\",\"properties\":{\"owner\":{\"type\":\"string\"}}}";
    private static final String NESTED = "{\"type\":\"object\",\"properties\":" +
        "{\"user\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}}}";

    @Test
    public void shouldAcceptPresentFlatProperty()
    {
        assertTrue(argPathValid(FLAT, "owner"));
    }

    @Test
    public void shouldRejectAbsentFlatProperty()
    {
        assertFalse(argPathValid(FLAT, "ownerr"));
    }

    @Test
    public void shouldAcceptPresentNestedProperty()
    {
        assertTrue(argPathValid(NESTED, "user.id"));
    }

    @Test
    public void shouldRejectAbsentNestedProperty()
    {
        assertFalse(argPathValid(NESTED, "user.idd"));
    }

    @Test
    public void shouldAcceptWhenAdditionalPropertiesTrue()
    {
        final String schema = "{\"type\":\"object\",\"additionalProperties\":true," +
            "\"properties\":{\"owner\":{\"type\":\"string\"}}}";
        assertTrue(argPathValid(schema, "anything"));
    }

    @Test
    public void shouldAcceptWhenNoProperties()
    {
        assertTrue(argPathValid("{\"type\":\"object\"}", "owner"));
    }

    @Test
    public void shouldAcceptWhenRef()
    {
        assertTrue(argPathValid("{\"$ref\":\"#/definitions/Owner\"}", "owner"));
    }

    @Test
    public void shouldAcceptWhenComposed()
    {
        final String schema = "{\"allOf\":[{\"type\":\"object\",\"properties\":{\"owner\":{}}}]}";
        assertTrue(argPathValid(schema, "owner"));
    }

    @Test
    public void shouldAcceptWhenSchemaUnresolved()
    {
        assertTrue(argPathValid(null, "owner"));
        assertTrue(argPathValid("", "owner"));
        assertTrue(argPathValid("   ", "owner"));
    }

    @Test
    public void shouldAcceptWhenSchemaMalformed()
    {
        assertTrue(argPathValid("{ not json", "owner"));
    }
}
