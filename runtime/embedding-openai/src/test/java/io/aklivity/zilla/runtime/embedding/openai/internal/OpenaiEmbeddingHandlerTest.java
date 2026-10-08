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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntConsumer;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.aklivity.zilla.config.embedding.openai.OpenaiCredentialsConfig;
import io.aklivity.zilla.config.embedding.openai.OpenaiOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler.CompletionCallback;

public class OpenaiEmbeddingHandlerTest
{
    private HttpServer server;
    private String endpoint;
    private volatile int responseStatus;
    private volatile String responseBody;
    private volatile boolean dynamicResponse;
    private volatile Function<String, Float> embeddingValue = text -> (float) text.length();
    private final List<Integer> requestSizes = new CopyOnWriteArrayList<>();

    private EngineContext context;
    private HttpClient client;

    @Before
    public void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/embeddings", this::respond);
        server.start();
        endpoint = "http://localhost:" + server.getAddress().getPort() + "/v1/embeddings";

        Signaler signaler = mock(Signaler.class);
        when(signaler.signalAt(anyLong(), anyInt(), any(IntConsumer.class))).thenAnswer(invocation ->
        {
            IntConsumer task = invocation.getArgument(2);
            task.accept(0);
            return 1L;
        });

        context = mock(EngineContext.class);
        when(context.signaler()).thenReturn(signaler);

        client = HttpClient.newHttpClient();
    }

    @After
    public void tearDown()
    {
        server.stop(0);
    }

    @Test
    public void shouldCompleteWithEmbeddingOnSuccess() throws Exception
    {
        responseStatus = 200;
        responseBody = "{\"data\":[{\"embedding\":[0.1,0.2,0.3],\"index\":0}],\"model\":\"text-embedding-3-small\"}";

        OpenaiEmbeddingHandler handler = new OpenaiEmbeddingHandler(context, client, options(endpoint));

        Result result = embed(handler, "hello world");

        assertThat(result.failure, nullValue());
        assertThat(result.vector, notNullValue());
        assertThat(result.vector, equalTo(new float[] { 0.1f, 0.2f, 0.3f }));
    }

    @Test
    public void shouldCompleteWithOneVectorPerTextInRequestOrderRegardlessOfResponseOrder() throws Exception
    {
        responseStatus = 200;
        responseBody = "{\"data\":[" +
            "{\"embedding\":[0.4,0.5,0.6],\"index\":1}," +
            "{\"embedding\":[0.1,0.2,0.3],\"index\":0}" +
            "],\"model\":\"text-embedding-3-small\"}";

        OpenaiEmbeddingHandler handler = new OpenaiEmbeddingHandler(context, client, options(endpoint));

        Results results = embedBatch(handler, List.of("first", "second"));

        assertThat(results.failure, nullValue());
        assertThat(results.vectors.length, equalTo(2));
        assertThat(results.vectors[0], equalTo(new float[] { 0.1f, 0.2f, 0.3f }));
        assertThat(results.vectors[1], equalTo(new float[] { 0.4f, 0.5f, 0.6f }));
    }

    @Test
    public void shouldFailOnUnauthorized() throws Exception
    {
        responseStatus = 401;
        responseBody = "{\"error\":{\"message\":\"Incorrect API key provided\",\"type\":\"invalid_request_error\"}}";

        OpenaiEmbeddingHandler handler = new OpenaiEmbeddingHandler(context, client, options(endpoint));

        Result result = embed(handler, "hello world");

        assertThat(result.vector, nullValue());
        assertThat(result.failure, instanceOf(OpenaiEmbeddingException.class));
    }

    @Test
    public void shouldFailOnRateLimited() throws Exception
    {
        responseStatus = 429;
        responseBody = "{\"error\":{\"message\":\"Rate limit exceeded\",\"type\":\"rate_limit_error\"}}";

        OpenaiEmbeddingHandler handler = new OpenaiEmbeddingHandler(context, client, options(endpoint));

        Result result = embed(handler, "hello world");

        assertThat(result.vector, nullValue());
        assertThat(result.failure, instanceOf(OpenaiEmbeddingException.class));
    }

    @Test
    public void shouldFailOnConnectionError() throws Exception
    {
        int unboundPort;
        try (ServerSocket unbound = new ServerSocket(0))
        {
            unboundPort = unbound.getLocalPort();
        }

        OpenaiEmbeddingHandler handler = new OpenaiEmbeddingHandler(
            context,
            client,
            options("http://localhost:" + unboundPort + "/v1/embeddings"));

        Result result = embed(handler, "hello world");

        assertThat(result.vector, nullValue());
        assertThat(result.failure, notNullValue());
    }

    @Test
    public void shouldFailOnMalformedResponse() throws Exception
    {
        responseStatus = 200;
        responseBody = "not valid json";

        OpenaiEmbeddingHandler handler = new OpenaiEmbeddingHandler(context, client, options(endpoint));

        Result result = embed(handler, "hello world");

        assertThat(result.vector, nullValue());
        assertThat(result.failure, notNullValue());
    }

    @Test
    public void shouldSplitBatchExceedingMaxItemCountIntoMultipleRequests() throws Exception
    {
        dynamicResponse = true;
        embeddingValue = text -> (float) Integer.parseInt(text);
        // Fixed-width (5-char) markers keep the aggregate token estimate (~2 per text) far under
        // OpenAI's 300,000-token cap, isolating item-count-driven splitting from token-budget splitting.
        int totalTexts = 2049; // one more than OpenAI's documented 2048-element cap on `input`
        List<String> texts = new ArrayList<>(totalTexts);
        for (int i = 0; i < totalTexts; i++)
        {
            texts.add(String.format("%05d", i));
        }

        OpenaiEmbeddingHandler handler = new OpenaiEmbeddingHandler(context, client, options(endpoint));

        Results results = embedBatch(handler, texts);

        assertThat(results.failure, nullValue());
        assertThat(requestSizes, containsInAnyOrder(2048, 1));
        for (int i = 0; i < totalTexts; i++)
        {
            assertThat(results.vectors[i], equalTo(new float[] { (float) i }));
        }
    }

    @Test
    public void shouldSplitBatchExceedingAggregateTokenBudgetIntoMultipleRequests() throws Exception
    {
        dynamicResponse = true;
        // ~4 chars/token estimate: two 700,000-char texts (~175,000 estimated tokens each) sum to
        // ~350,000, past OpenAI's documented 300,000 aggregate-token cap, while the item count (2)
        // stays far under the 2048-element cap -- this must split on token budget alone.
        List<String> texts = List.of("a".repeat(700_000), "b".repeat(700_004));

        OpenaiEmbeddingHandler handler = new OpenaiEmbeddingHandler(context, client, options(endpoint));

        Results results = embedBatch(handler, texts);

        assertThat(results.failure, nullValue());
        assertThat(requestSizes, containsInAnyOrder(1, 1));
        assertThat(results.vectors[0], equalTo(new float[] { 700_000f }));
        assertThat(results.vectors[1], equalTo(new float[] { 700_004f }));
    }

    private static OpenaiOptionsConfig options(
        String endpoint)
    {
        return OpenaiOptionsConfig.builder()
            .model("text-embedding-3-small")
            .endpoint(endpoint)
            .credentials(OpenaiCredentialsConfig.builder()
                .apiKey("test-key")
                .build())
            .build();
    }

    private Result embed(
        OpenaiEmbeddingHandler handler,
        String text) throws InterruptedException
    {
        Results results = embedBatch(handler, List.of(text));
        return new Result(results.vectors != null ? results.vectors[0] : null, results.failure);
    }

    private Results embedBatch(
        OpenaiEmbeddingHandler handler,
        List<String> texts) throws InterruptedException
    {
        AtomicReference<float[][]> vectors = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        handler.embed(0L, 0L, 0L, texts, new CompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                float[][] results)
            {
                vectors.set(results);
                latch.countDown();
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
                failure.set(ex);
                latch.countDown();
            }
        });

        latch.await(5, TimeUnit.SECONDS);

        return new Results(vectors.get(), failure.get());
    }

    private void respond(
        HttpExchange exchange) throws IOException
    {
        int status = responseStatus;
        String body = responseBody;
        if (dynamicResponse)
        {
            JsonArray input = requestInput(exchange);
            requestSizes.add(input.size());
            status = 200;
            body = echoResponseBody(input, embeddingValue);
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(bytes);
        }
    }

    private static JsonArray requestInput(
        HttpExchange exchange) throws IOException
    {
        String body;
        try (InputStream in = exchange.getRequestBody();
             ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            in.transferTo(out);
            body = out.toString(StandardCharsets.UTF_8);
        }
        return Json.createReader(new StringReader(body)).readObject().getJsonArray("input");
    }

    private static String echoResponseBody(
        JsonArray input,
        Function<String, Float> embeddingValue)
    {
        JsonArrayBuilder data = Json.createArrayBuilder();
        for (int i = 0; i < input.size(); i++)
        {
            float value = embeddingValue.apply(input.getString(i));
            data.add(Json.createObjectBuilder()
                .add("embedding", Json.createArrayBuilder().add(value))
                .add("index", i));
        }

        JsonObject body = Json.createObjectBuilder()
            .add("data", data)
            .add("model", "text-embedding-3-small")
            .build();
        return body.toString();
    }

    private static final class Result
    {
        private final float[] vector;
        private final Throwable failure;

        private Result(
            float[] vector,
            Throwable failure)
        {
            this.vector = vector;
            this.failure = failure;
        }
    }

    private static final class Results
    {
        private final float[][] vectors;
        private final Throwable failure;

        private Results(
            float[][] vectors,
            Throwable failure)
        {
            this.vectors = vectors;
            this.failure = failure;
        }
    }
}
