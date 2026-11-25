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
package io.aklivity.zilla.specs.store.memory.config;

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
        .schemaPatch("io/aklivity/zilla/specs/store/memory/schema/memory.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/binding/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/store/memory/config");

    @Test
    public void shouldValidateStore()
    {
        JsonObject config = schema.validate("store.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreIncrementAssertions()
    {
        JsonObject config = schema.validate("store.increment.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreGetKeysAssertions()
    {
        JsonObject config = schema.validate("store.get.keys.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreDeleteKeysAssertions()
    {
        JsonObject config = schema.validate("store.delete.keys.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreReplaceAssertions()
    {
        JsonObject config = schema.validate("store.replace.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreTouch()
    {
        JsonObject config = schema.validate("store.touch.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreTouchAbsent()
    {
        JsonObject config = schema.validate("store.touch.absent.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreTouchWatch()
    {
        JsonObject config = schema.validate("store.touch.watch.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreTouchExpire()
    {
        JsonObject config = schema.validate("store.touch.expire.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreTouchExpired()
    {
        JsonObject config = schema.validate("store.touch.expired.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreTouchShorter()
    {
        JsonObject config = schema.validate("store.touch.shorter.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreTouchPersistent()
    {
        JsonObject config = schema.validate("store.touch.persistent.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreExpirationPut()
    {
        JsonObject config = schema.validate("store.expiration.put.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreExpirationPutIfAbsent()
    {
        JsonObject config = schema.validate("store.expiration.put.if.absent.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreExpirationReplace()
    {
        JsonObject config = schema.validate("store.expiration.replace.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreExpirationTouch()
    {
        JsonObject config = schema.validate("store.expiration.touch.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreExpirationTrackedOverUntracked()
    {
        JsonObject config = schema.validate("store.expiration.tracked.over.untracked.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreExpirationUntrackedOverTracked()
    {
        JsonObject config = schema.validate("store.expiration.untracked.over.tracked.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreExpirationIncrement()
    {
        JsonObject config = schema.validate("store.expiration.increment.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateStoreExpirationIncrementTracked()
    {
        JsonObject config = schema.validate("store.expiration.increment.tracked.yaml");

        assertThat(config, not(nullValue()));
    }
}
