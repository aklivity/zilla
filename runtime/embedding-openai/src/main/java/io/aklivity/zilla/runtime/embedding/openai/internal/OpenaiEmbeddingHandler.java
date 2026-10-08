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
package io.aklivity.zilla.runtime.embedding.openai.internal;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;

import io.aklivity.zilla.config.embedding.openai.OpenaiOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;

final class OpenaiEmbeddingHandler implements EmbeddingHandler, AutoCloseable
{
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int SIGNAL_ID = 1;
    // OpenAI's /v1/embeddings caps the `input` array at 2048 elements and the summed token
    // count of every input at 300,000 tokens per request, uniformly across embedding models.
    private static final int MAX_BATCH_SIZE = 2048;
    private static final int MAX_AGGREGATE_TOKENS = 300_000;
    // OpenAI's own documented rule of thumb for English text (~4 chars/token); no tokenizer
    // dependency is pulled in just to pre-flight a batch-split decision, so this rounds up
    // per text to stay conservative rather than exact.
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    private final Signaler signaler;
    private final HttpClient client;
    private final URI endpoint;
    private final String model;
    private final String apiKey;

    OpenaiEmbeddingHandler(
        EngineContext context,
        HttpClient client,
        OpenaiOptionsConfig options)
    {
        this.signaler = context.signaler();
        this.client = client;
        this.endpoint = URI.create(options.endpoint);
        this.model = options.model;
        this.apiKey = options.credentials.apiKey;
    }

    @Override
    public void embed(
        long traceId,
        long bindingId,
        long contextId,
        List<String> texts,
        CompletionCallback completion)
    {
        // OpenAI's /v1/embeddings caps both how many texts and how many total tokens land in one
        // request, so texts are grouped into chunks respecting both limits, each chunk issued as
        // one concurrent request, and every chunk's results scattered back into the caller's order.
        List<List<String>> chunks = chunk(texts);
        Batch batch = new Batch(texts.size(), chunks.size(), contextId, completion);

        int offset = 0;
        for (List<String> slice : chunks)
        {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(slice)))
                .build();

            int resultOffset = offset;
            int chunkSize = slice.size();
            client.sendAsync(request, BodyHandlers.ofString())
                .whenComplete((response, ex) -> dispatch(() -> onResponse(resultOffset, chunkSize, batch, response, ex)));
            offset += chunkSize;
        }
    }

    @Override
    public void close()
    {
    }

    private static List<List<String>> chunk(
        List<String> texts)
    {
        List<List<String>> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        long tokens = 0;
        for (String text : texts)
        {
            int estimated = estimateTokens(text);
            if (current.size() >= MAX_BATCH_SIZE || !current.isEmpty() && tokens + estimated > MAX_AGGREGATE_TOKENS)
            {
                chunks.add(current);
                current = new ArrayList<>();
                tokens = 0;
            }
            current.add(text);
            tokens += estimated;
        }
        if (!current.isEmpty())
        {
            chunks.add(current);
        }
        return chunks;
    }

    private static int estimateTokens(
        String text)
    {
        return (text.length() + CHARS_PER_TOKEN_ESTIMATE - 1) / CHARS_PER_TOKEN_ESTIMATE;
    }

    private void dispatch(
        Runnable task)
    {
        signaler.signalAt(System.currentTimeMillis(), SIGNAL_ID, signalId -> task.run());
    }

    private void onResponse(
        int offset,
        int chunkSize,
        Batch batch,
        HttpResponse<String> response,
        Throwable ex)
    {
        if (ex != null)
        {
            batch.fail(ex);
        }
        else if (response.statusCode() != 200)
        {
            batch.fail(new OpenaiEmbeddingException(response.statusCode(), response.body()));
        }
        else
        {
            try
            {
                batch.succeed(offset, embeddings(response.body(), chunkSize));
            }
            catch (Exception parseError)
            {
                batch.fail(parseError);
            }
        }
    }

    private String requestBody(
        List<String> texts)
    {
        JsonArrayBuilder input = Json.createArrayBuilder();
        for (String text : texts)
        {
            input.add(text);
        }

        JsonObject body = Json.createObjectBuilder()
            .add("model", model)
            .add("input", input)
            .build();

        StringWriter writer = new StringWriter();
        try (JsonWriter jsonWriter = Json.createWriter(writer))
        {
            jsonWriter.writeObject(body);
        }
        return writer.toString();
    }

    private static float[][] embeddings(
        String responseBody,
        int expected)
    {
        JsonObject response = Json.createReader(new StringReader(responseBody)).readObject();
        JsonArray data = response.getJsonArray("data");

        float[][] vectors = new float[expected][];
        for (int i = 0; i < data.size(); i++)
        {
            JsonObject entry = data.getJsonObject(i);
            JsonArray values = entry.getJsonArray("embedding");
            int index = entry.getInt("index", i);

            float[] vector = new float[values.size()];
            for (int j = 0; j < vector.length; j++)
            {
                vector[j] = (float) values.getJsonNumber(j).doubleValue();
            }
            vectors[index] = vector;
        }
        return vectors;
    }

    private static final class Batch
    {
        private final float[][] results;
        private final long contextId;
        private final CompletionCallback completion;

        private int remaining;
        private boolean settled;

        private Batch(
            int textCount,
            int chunkCount,
            long contextId,
            CompletionCallback completion)
        {
            this.results = new float[textCount][];
            this.contextId = contextId;
            this.completion = completion;
            this.remaining = chunkCount;
        }

        private void succeed(
            int offset,
            float[][] chunkResults)
        {
            if (!settled)
            {
                System.arraycopy(chunkResults, 0, results, offset, chunkResults.length);
                if (--remaining == 0)
                {
                    settled = true;
                    completion.completed(contextId, results);
                }
            }
        }

        private void fail(
            Throwable ex)
        {
            if (!settled)
            {
                settled = true;
                completion.failed(contextId, ex);
            }
        }
    }
}
