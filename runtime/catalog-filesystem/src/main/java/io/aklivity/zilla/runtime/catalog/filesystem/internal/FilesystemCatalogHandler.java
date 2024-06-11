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
package io.aklivity.zilla.runtime.catalog.filesystem.internal;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.CRC32C;

import io.aklivity.zilla.runtime.catalog.filesystem.internal.config.FilesystemOptionsConfig;
import io.aklivity.zilla.runtime.catalog.filesystem.internal.config.FilesystemSchemaConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public class FilesystemCatalogHandler implements CatalogHandler
{
    private final Map<Integer, String> schemas;
    private final Map<String, Integer> schemaIds;
    private final CRC32C crc32c;
    private final FilesystemEventContext event;
    private final long catalogId;
    private final Function<String, Path> resolvePath;

    public FilesystemCatalogHandler(
        FilesystemOptionsConfig config,
        EngineContext context,
        long catalogId)
    {
        this.schemas = new HashMap<>();
        this.schemaIds =  new HashMap<>();
        this.crc32c = new CRC32C();
        this.event = new FilesystemEventContext(context);
        this.resolvePath = context::resolvePath;
        this.catalogId = catalogId;
        registerSchema(config.subjects);
    }

    @Override
    public String resolve(
        int schemaId)
    {
        return schemas.getOrDefault(schemaId, null);
    }

    @Override
    public int resolve(
        String subject,
        String version)
    {
        return schemaIds.getOrDefault(subject, NO_SCHEMA_ID);
    }

    private void registerSchema(
        List<FilesystemSchemaConfig> configs)
    {
        for (FilesystemSchemaConfig config : configs)
        {
            try
            {
                Path storePath = resolvePath.apply(config.path);
                try (InputStream input = Files.newInputStream(storePath))
                {
                    String schema = new String(input.readAllBytes());
                    int schemaId = generateCRC32C(schema);
                    schemas.put(schemaId, schema);
                    schemaIds.put(config.subject, schemaId);
                }
            }
            catch (Exception ex)
            {
                event.fileNotFound(catalogId, config.path);
            }
        }
    }

    private int generateCRC32C(
        String schema)
    {
        byte[] bytes = schema.getBytes();
        crc32c.reset();
        crc32c.update(bytes, 0, bytes.length);
        return (int) crc32c.getValue();
    }
}
