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
package io.aklivity.zilla.runtime.model.avro.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.config.model.avro.AvroModelConfig;
import io.aklivity.zilla.runtime.common.avro.AvroField;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;

public class AvroModelOverlayTest
{
    private static final int OVERLAY_SCHEMA_ID_BEFORE = 100;
    private static final int OVERLAY_SCHEMA_ID_AFTER = 200;

    private static final String SCHEMA = """
        {
          "type": "record",
          "name": "Event",
          "namespace": "io.aklivity.example",
          "fields": [
            { "name": "id", "type": "string" },
            { "name": "email", "type": "string" }
          ]
        }
        """;

    private static final String OVERLAY_TAGS_EMAIL = """
        {
          "overlay": "1.1.0",
          "actions": [
            {
              "target": "$.fields[?(@.name=='email')]",
              "update": { "zilla:tags": [ "PII" ] }
            }
          ]
        }
        """;

    private static final String OVERLAY_NOOP = """
        {
          "overlay": "1.1.0",
          "actions": [
            {
              "target": "$.fields[?(@.name=='id')]",
              "update": { "zilla:tags": [ "NONE" ] }
            }
          ]
        }
        """;

    private EngineContext context;
    private AvroModelConfiguration config;

    @Before
    public void init()
    {
        config = new AvroModelConfiguration(new Configuration());
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldApplyOverlayBeforeCachingSchema()
    {
        MovingOverlayCatalogHandler overlay = new MovingOverlayCatalogHandler(OVERLAY_TAGS_EMAIL, OVERLAY_NOOP);
        AvroModelHandlerImpl handler = newHandlerWithOverlay(overlay);

        AvroSchema schema = handler.supplySchema(1);

        AvroField email = field(schema, "email");
        JsonValue tags = email.attribute("zilla:tags");
        assertEquals("[\"PII\"]", tags.toString());
    }

    @Test
    public void shouldNotServeStaleCompiledSchemaWhenNonPinnedOverlayResolvesToNewId()
    {
        MovingOverlayCatalogHandler overlay = new MovingOverlayCatalogHandler(OVERLAY_TAGS_EMAIL, OVERLAY_NOOP);
        AvroModelHandlerImpl handler = newHandlerWithOverlay(overlay);

        // overlay's "latest" reference currently resolves to OVERLAY_SCHEMA_ID_BEFORE, whose overlay
        // tags "email" with a PII annotation
        AvroSchema first = handler.supplySchema(1);
        assertEquals("[\"PII\"]", field(first, "email").attribute("zilla:tags").toString());

        // the overlay catalog entry now resolves "latest" to OVERLAY_SCHEMA_ID_AFTER, an overlay that
        // doesn't touch "email", with the base schema's own schemaId unchanged -- the cache must not
        // keep serving the compiled schema from the first, now-stale overlay resolution
        overlay.advance();

        AvroSchema second = handler.supplySchema(1);
        assertNull(field(second, "email").attribute("zilla:tags"));
    }

    private static AvroField field(
        AvroSchema schema,
        String name)
    {
        return schema.type().fields().stream()
            .filter(f -> name.equals(f.name()))
            .findFirst()
            .orElseThrow();
    }

    private AvroModelHandlerImpl newHandlerWithOverlay(
        CatalogHandler overlayHandler)
    {
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .build()
            .build();

        AvroModelConfig model = AvroModelConfig.builder()
            .view("json")
            .catalog()
                .name("test0")
                .schema()
                    .subject("test-value")
                    .version("latest")
                    .overlay()
                        .name("test1")
                        .schema()
                            .subject("test-overlay")
                            .version("latest")
                            .build()
                        .build()
                    .build()
                .build()
            .build();
        model.cataloged.get(0).id = catalog.id;
        model.cataloged.get(0).schemas.get(0).overlay.id = 10L;

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        when(context.supplyCatalog(10L)).thenReturn(overlayHandler);

        return new AvroModelHandlerImpl(config, model, context);
    }

    private static final class MovingOverlayCatalogHandler implements CatalogHandler
    {
        private final String overlayBefore;
        private final String overlayAfter;
        private boolean moved;

        private MovingOverlayCatalogHandler(
            String overlayBefore,
            String overlayAfter)
        {
            this.overlayBefore = overlayBefore;
            this.overlayAfter = overlayAfter;
        }

        private void advance()
        {
            this.moved = true;
        }

        @Override
        public int resolve(
            String subject,
            String version)
        {
            return moved ? OVERLAY_SCHEMA_ID_AFTER : OVERLAY_SCHEMA_ID_BEFORE;
        }

        @Override
        public String resolve(
            int schemaId)
        {
            String resolved = null;
            if (schemaId == OVERLAY_SCHEMA_ID_BEFORE)
            {
                resolved = overlayBefore;
            }
            else if (schemaId == OVERLAY_SCHEMA_ID_AFTER)
            {
                resolved = overlayAfter;
            }
            return resolved;
        }
    }
}
