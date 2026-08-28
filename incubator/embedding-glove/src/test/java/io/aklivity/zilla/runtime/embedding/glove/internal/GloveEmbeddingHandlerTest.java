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
package io.aklivity.zilla.runtime.embedding.glove.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class GloveEmbeddingHandlerTest
{
    @Test
    public void shouldParseVectorsFromZipEntry() throws IOException
    {
        Path zip = Files.createTempFile("glove-embedding-handler-test", ".zip");
        zip.toFile().deleteOnExit();
        writeVectors(zip, "vectors.txt", "hello 1.0 2.0\nworld 3.0 4.0\n");

        Map<String, float[]> vectors = GloveEmbeddingHandler.loadVectors(zip.toUri(), "vectors.txt");

        assertThat(vectors.size(), equalTo(2));
        assertThat(vectors.get("hello"), equalTo(new float[] { 1.0f, 2.0f }));
        assertThat(vectors.get("world"), equalTo(new float[] { 3.0f, 4.0f }));
    }

    @Test
    public void shouldIgnoreEntriesOtherThanTheRequestedOne() throws IOException
    {
        Path zip = Files.createTempFile("glove-embedding-handler-test", ".zip");
        zip.toFile().deleteOnExit();
        writeVectors(zip, "other.txt", "hello 1.0 2.0\n");

        Map<String, float[]> vectors = GloveEmbeddingHandler.loadVectors(zip.toUri(), "vectors.txt");

        assertThat(vectors.size(), equalTo(0));
    }

    private static void writeVectors(
        Path zip,
        String entry,
        String content) throws IOException
    {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip)))
        {
            out.putNextEntry(new ZipEntry(entry));
            writeContent(out, content);
            out.closeEntry();
        }
    }

    private static void writeContent(
        OutputStream out,
        String content) throws IOException
    {
        out.write(content.getBytes(StandardCharsets.UTF_8));
    }
}
