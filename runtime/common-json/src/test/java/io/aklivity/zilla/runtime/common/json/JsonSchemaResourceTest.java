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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import jakarta.json.Json;
import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonSchemaResourceTest
{
    @Test
    void shouldResolveSchemaRelativeToClass()
    {
        JsonSchema schema = JsonSchema.of(JsonSchemaResourceTest.class, "resource.schema.json");

        assertTrue(valid(schema, "{\"name\":\"value\"}"));
        assertFalse(valid(schema, "{\"name\":1}"));
        assertFalse(valid(schema, "{\"name\":\"value\",\"other\":true}"));
    }

    @Test
    void shouldRejectMissingSchemaResource()
    {
        assertThrows(AssertionError.class, () -> JsonSchema.of(JsonSchemaResourceTest.class, "missing.schema.json"));
    }

    @Test
    void shouldResolveSchemaFromNamedModule(
        @TempDir Path dir) throws Exception
    {
        ClassLoader loader = namedModule(dir, "opens owner;");

        Class<?> owner = loader.loadClass("owner.Owner");
        assertTrue(owner.getModule().isNamed());

        JsonSchema schema = JsonSchema.of(owner, "owner.schema.json");

        assertNotNull(schema);
        assertTrue(valid(schema, "{\"name\":\"value\"}"));
        assertFalse(valid(schema, "{\"name\":1}"));
    }

    @Test
    void shouldRejectSchemaFromNamedModuleWithoutOpenedPackage(
        @TempDir Path dir) throws Exception
    {
        ClassLoader loader = namedModule(dir, "");

        Class<?> owner = loader.loadClass("owner.Owner");

        assertThrows(AssertionError.class, () -> JsonSchema.of(owner, "owner.schema.json"));
    }

    private static boolean valid(
        JsonSchema schema,
        String json)
    {
        try (JsonParser parser = Json.createParser(new StringReader(json)))
        {
            return schema.validate(parser);
        }
    }

    private static ClassLoader namedModule(
        Path dir,
        String directives) throws IOException
    {
        Path src = dir.resolve("src");
        Path out = dir.resolve("out").resolve("owner.module");
        Files.createDirectories(src.resolve("owner"));
        Files.createDirectories(out.resolve("owner"));

        Files.writeString(src.resolve("module-info.java"), "module owner.module { " + directives + " }", UTF_8);
        Files.writeString(src.resolve("owner").resolve("Owner.java"), "package owner; public final class Owner { }", UTF_8);
        Files.copy(
            Path.of("src/test/resources/io/aklivity/zilla/runtime/common/json/resource.schema.json"),
            out.resolve("owner").resolve("owner.schema.json"));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null,
            "-d", out.toString(),
            src.resolve("module-info.java").toString(),
            src.resolve("owner").resolve("Owner.java").toString());
        assertEquals(0, result);

        ModuleFinder finder = ModuleFinder.of(out.getParent());
        Configuration configuration = ModuleLayer.boot().configuration()
            .resolve(finder, ModuleFinder.of(), Set.of("owner.module"));
        ModuleLayer layer = ModuleLayer.defineModulesWithOneLoader(
            configuration, List.of(ModuleLayer.boot()), ClassLoader.getSystemClassLoader()).layer();

        return layer.findLoader("owner.module");
    }
}
