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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;

public final class GloveEmbeddingHandler implements EmbeddingHandler, AutoCloseable
{
    static final URI VECTORS_URL = URI.create("https://nlp.stanford.edu/data/glove.6B.zip");
    static final String VECTORS_ENTRY = "glove.6B.50d.txt";

    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final EngineContext context;
    private final Map<String, float[]> vectors;
    private final int dimensions;

    public GloveEmbeddingHandler(
        EngineContext context)
    {
        this.context = context;
        this.vectors = loadVectors(VECTORS_URL, VECTORS_ENTRY);
        this.dimensions = vectors.values().stream()
            .findFirst()
            .map(vector -> vector.length)
            .orElse(0);
    }

    @Override
    public void embed(
        long traceId,
        long bindingId,
        long contextId,
        String text,
        CompletionCallback completion)
    {
        float[] embedding = embed(text);
        context.dispatch(() -> completion.completed(contextId, embedding));
    }

    @Override
    public void close()
    {
    }

    private float[] embed(
        String text)
    {
        float[] sum = new float[dimensions];
        int matched = 0;

        for (String word : WORD_SEPARATOR.split(text.toLowerCase(Locale.ROOT)))
        {
            float[] vector = vectors.get(word);
            if (vector != null)
            {
                for (int i = 0; i < dimensions; i++)
                {
                    sum[i] += vector[i];
                }
                matched++;
            }
        }

        return matched == 0 ? null : average(sum, matched);
    }

    private static float[] average(
        float[] sum,
        int matched)
    {
        for (int i = 0; i < sum.length; i++)
        {
            sum[i] /= matched;
        }

        return sum;
    }

    static Map<String, float[]> loadVectors(
        URI url,
        String entry)
    {
        Map<String, float[]> vectors = new HashMap<>();

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(resolve(url))))
        {
            ZipEntry candidate;
            while ((candidate = zip.getNextEntry()) != null)
            {
                if (entry.equals(candidate.getName()))
                {
                    parseVectors(zip, vectors);
                    break;
                }
            }
        }
        catch (IOException | InterruptedException ex)
        {
            throw new IllegalStateException(ex);
        }

        return vectors;
    }

    private static void parseVectors(
        InputStream stream,
        Map<String, float[]> vectors) throws IOException
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] fields = line.split(" ");
                float[] vector = new float[fields.length - 1];
                for (int i = 1; i < fields.length; i++)
                {
                    vector[i - 1] = Float.parseFloat(fields[i]);
                }
                vectors.put(fields[0], vector);
            }
        }
    }

    private static Path resolve(
        URI url) throws IOException, InterruptedException
    {
        return "file".equals(url.getScheme()) ? Path.of(url) : download(url);
    }

    private static Path download(
        URI url) throws IOException, InterruptedException
    {
        Path cache = Path.of(System.getProperty("java.io.tmpdir"), "zilla-embedding-glove.zip");

        if (Files.notExists(cache))
        {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
            HttpRequest request = HttpRequest.newBuilder(url)
                .GET()
                .build();

            Path partial = Files.createTempFile(cache.getParent(), "zilla-embedding-glove", ".zip.tmp");
            client.send(request, HttpResponse.BodyHandlers.ofFile(partial));
            Files.move(partial, cache, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }

        return cache;
    }
}
