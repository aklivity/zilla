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
package io.aklivity.zilla.runtime.classifier.presidio.internal;


import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

final class AnalyzerStub implements AutoCloseable
{
    private final HttpServer server;
    private final List<String> requests;

    private volatile int status;
    private volatile String body;
    private volatile CountDownLatch gate;

    AnalyzerStub() throws IOException
    {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.requests = new CopyOnWriteArrayList<>();
        this.status = 200;
        this.body = "[]";
        this.server.createContext("/analyze", this::handle);
        this.server.start();
    }

    String endpoint()
    {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void respond(
        int status,
        String body)
    {
        this.status = status;
        this.body = body;
    }

    void hold(
        CountDownLatch gate)
    {
        this.gate = gate;
    }

    List<String> requests()
    {
        return requests;
    }

    @Override
    public void close()
    {
        server.stop(0);
    }

    private void handle(
        HttpExchange exchange) throws IOException
    {
        requests.add(new String(exchange.getRequestBody().readAllBytes(), UTF_8));

        CountDownLatch held = gate;
        if (held != null)
        {
            try
            {
                held.await();
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }
        }

        byte[] payload = body.getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);

        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(payload);
        }
    }
}
