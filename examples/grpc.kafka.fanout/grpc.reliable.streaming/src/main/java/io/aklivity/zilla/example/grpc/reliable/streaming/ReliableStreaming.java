/*
 * Copyright 2021-2022 Aklivity. All rights reserved.
 */
package io.aklivity.zilla.example.grpc.reliable.streaming;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.protobuf.Empty;

import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;

public class ReliableStreaming
{
    private static final Logger LOGGER = Logger.getLogger(ReliableStreaming.class.getName());
    private static final String ENV_DISABLE_RETRYING = "DISABLE_RETRYING_IN_RETRYING_EXAMPLE";

    private final boolean enableRetries;
    private final ManagedChannel channel;
    private final FanoutServiceGrpc.FanoutServiceBlockingStub blockingStub;
    private final AtomicInteger totalRpcs = new AtomicInteger();
    private final AtomicInteger failedRpcs = new AtomicInteger();

    protected Map<String, ?> getRetryingServiceConfig()
    {
        return new Gson()
            .fromJson(
                new JsonReader(
                    new InputStreamReader(
                        ReliableStreaming.class.getResourceAsStream(
                            "config/retrying_service_config.json"), UTF_8)),
                Map.class);
    }

    /**
     * Construct client connecting to Fanout server at {@code host:port}.
     */
    public ReliableStreaming(String host, int port, boolean enableRetries) throws IOException
    {
        InputStream caCert = ReliableStreaming.class.getResourceAsStream("certs/test-ca.crt");
        ChannelCredentials creds = TlsChannelCredentials.newBuilder()
            .trustManager(caCert)
            .build();

        ManagedChannelBuilder<?> channelBuilder = Grpc.newChannelBuilderForAddress(host, port, creds)
            .intercept(new LastMessageIdInterceptor());

        if (enableRetries)
        {
            Map<String, ?> serviceConfig = getRetryingServiceConfig();
            LOGGER.info("Client started with retrying configuration: " + serviceConfig);
            channelBuilder.defaultServiceConfig(serviceConfig).enableRetry();
        }

        channel = channelBuilder.build();
        blockingStub = FanoutServiceGrpc.newBlockingStub(channel);
        this.enableRetries = enableRetries;
    }

    /**
     * Send empty message to server in a blocking call.
     */
    public void stream()
    {
        Empty request = Empty.newBuilder().build();
        Iterator<FanoutMessage> response = null;
        try
        {
            response = blockingStub.fanoutServerStream(request);
            while (response.hasNext())
            {
                totalRpcs.incrementAndGet();
                FanoutMessage message = response.next();
                LOGGER.info("Found message: " + message);
            }
        }
        catch (StatusRuntimeException ex)
        {
            failedRpcs.incrementAndGet();
        }
    }

    private void printSummary()
    {
        LOGGER.log(
            Level.INFO,
            "\n\nTotal RPCs sent: {0}. Total RPCs failed: {1}\n",
            new Object[]
                {
                    totalRpcs.get(), failedRpcs.get()
                });

        if (enableRetries)
        {
            LOGGER.log(
                Level.INFO,
                "Retrying enabled. To disable retries, run the client with environment variable {0}=true.",
                ENV_DISABLE_RETRYING);
        }
        else
        {
            LOGGER.log(
                Level.INFO,
                "Retrying disabled. To enable retries, unset environment variable {0} and then run the client.",
                ENV_DISABLE_RETRYING);
        }
    }


    public static void main(String[] args) throws Exception
    {
        boolean enableRetries = !Boolean.parseBoolean(System.getenv(ENV_DISABLE_RETRYING));
        final ReliableStreaming reliableStreaming = new ReliableStreaming("localhost", 9090, enableRetries);

        while (true)
        {
            reliableStreaming.stream();
            reliableStreaming.printSummary();
            Thread.sleep(10000);
        }
    }
}
