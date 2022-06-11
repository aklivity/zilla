/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.http.kafka.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;

import io.aklivity.zilla.specs.engine.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/binding/http/kafka/schema/http.kafka.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/http/kafka/config");

    @Test
    public void shouldValidateProxyDeleteItem()
    {
        JsonObject config = schema.validate("proxy.delete.item.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyDeleteItemNoReply()
    {
        JsonObject config = schema.validate("proxy.delete.item.no.reply.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyDeleteItemAsync()
    {
        JsonObject config = schema.validate("proxy.delete.item.async.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyGetItem()
    {
        JsonObject config = schema.validate("proxy.get.item.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyGetItemChild()
    {
        JsonObject config = schema.validate("proxy.get.item.child.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyGetItems()
    {
        JsonObject config = schema.validate("proxy.get.items.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyPatchItem()
    {
        JsonObject config = schema.validate("proxy.patch.item.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyPatchItemAsync()
    {
        JsonObject config = schema.validate("proxy.patch.item.async.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyPostItemCommand()
    {
        JsonObject config = schema.validate("proxy.post.item.command.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyPostItemCommandAsync()
    {
        JsonObject config = schema.validate("proxy.post.item.command.async.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyPostItems()
    {
        JsonObject config = schema.validate("proxy.post.items.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyPostItemsAsync()
    {
        JsonObject config = schema.validate("proxy.post.items.async.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyPutItem()
    {
        JsonObject config = schema.validate("proxy.put.item.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyPutItemNoReply()
    {
        JsonObject config = schema.validate("proxy.put.item.no.reply.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyPutItemAsync()
    {
        JsonObject config = schema.validate("proxy.put.item.async.json");

        assertThat(config, not(nullValue()));
    }
}
