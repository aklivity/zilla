/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CACHE_DIRECTORY;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCleanupPolicy;
import io.aklivity.zilla.runtime.engine.Configuration;

public class KafkaConfiguration extends Configuration
{
    public static final boolean DEBUG = Boolean.getBoolean("zilla.binding.kafka.debug");
    public static final boolean DEBUG_PRODUCE = DEBUG || Boolean.getBoolean("zilla.binding.kafka.debug.produce");

    public static final IntPropertyDef KAFKA_CLIENT_MAX_IDLE_MILLIS;
    public static final IntPropertyDef KAFKA_CLIENT_META_MAX_AGE_MILLIS;
    public static final IntPropertyDef KAFKA_CLIENT_DESCRIBE_MAX_AGE_MILLIS;
    public static final IntPropertyDef KAFKA_CLIENT_FETCH_MAX_WAIT_MILLIS;
    public static final IntPropertyDef KAFKA_CLIENT_FETCH_MAX_BYTES;
    public static final IntPropertyDef KAFKA_CLIENT_FETCH_PARTITION_MAX_BYTES;
    public static final IntPropertyDef KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS;
    public static final IntPropertyDef KAFKA_CLIENT_PRODUCE_MAX_RESPONSE_MILLIS;
    public static final IntPropertyDef KAFKA_CLIENT_PRODUCE_MAX_BYTES;
    public static final PropertyDef<Path> KAFKA_CACHE_DIRECTORY;
    public static final LongPropertyDef KAFKA_CACHE_PRODUCE_CAPACITY;
    public static final PropertyDef<KafkaCacheCleanupPolicy> KAFKA_CACHE_CLEANUP_POLICY;
    public static final IntPropertyDef KAFKA_CACHE_MAX_MESSAGE_BYTES;
    public static final LongPropertyDef KAFKA_CACHE_RETENTION_MILLIS;
    public static final LongPropertyDef KAFKA_CACHE_RETENTION_BYTES;
    public static final LongPropertyDef KAFKA_CACHE_DELETE_RETENTION_MILLIS;
    public static final LongPropertyDef KAFKA_CACHE_MIN_COMPACTION_LAG_MILLIS;
    public static final LongPropertyDef KAFKA_CACHE_MAX_COMPACTION_LAG_MILLIS;
    public static final DoublePropertyDef KAFKA_CACHE_MIN_CLEANABLE_DIRTY_RATIO;
    public static final LongPropertyDef KAFKA_CACHE_SEGMENT_MILLIS;
    public static final IntPropertyDef KAFKA_CACHE_SEGMENT_BYTES;
    public static final IntPropertyDef KAFKA_CACHE_SEGMENT_INDEX_BYTES;
    public static final BooleanPropertyDef KAFKA_CACHE_SERVER_BOOTSTRAP;
    public static final IntPropertyDef KAFKA_CACHE_CLIENT_RECONNECT_DELAY;
    public static final IntPropertyDef KAFKA_CACHE_CLIENT_CLEANUP_DELAY;
    public static final IntPropertyDef KAFKA_CACHE_CLIENT_TRAILERS_SIZE_MAX;
    public static final IntPropertyDef KAFKA_CACHE_SERVER_RECONNECT_DELAY;
    public static final PropertyDef<NonceSupplier> KAFKA_CLIENT_SASL_SCRAM_NONCE;
    public static final PropertyDef<String> KAFKA_CLIENT_GROUP_INSTANCE_ID;
    public static final PropertyDef<Duration> KAFKA_CLIENT_GROUP_REBALANCE_TIMEOUT;
    public static final PropertyDef<String> KAFKA_CLIENT_ID;
    public static final PropertyDef<InstanceIdSupplier> KAFKA_CLIENT_INSTANCE_ID_SUPPLIER;

    private static final ConfigurationDef KAFKA_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.kafka");
        KAFKA_CLIENT_MAX_IDLE_MILLIS = config.property("client.max.idle.ms", 1 * 60 * 1000);
        KAFKA_CLIENT_META_MAX_AGE_MILLIS = config.property("client.meta.max.age.ms", 5 * 60 * 1000);
        KAFKA_CLIENT_DESCRIBE_MAX_AGE_MILLIS = config.property("client.describe.max.age.ms", 5 * 60 * 1000);
        KAFKA_CLIENT_FETCH_MAX_WAIT_MILLIS = config.property("client.fetch.max.wait.millis", 1 * 60 * 1000);
        KAFKA_CLIENT_FETCH_MAX_BYTES = config.property("client.fetch.max.bytes", 50 * 1024 * 1024);
        KAFKA_CLIENT_FETCH_PARTITION_MAX_BYTES = config.property("client.fetch.partition.max.bytes", 50 * 1024 * 1024);
        KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS = config.property("client.produce.max.request.millis", 0);
        KAFKA_CLIENT_PRODUCE_MAX_RESPONSE_MILLIS = config.property("client.produce.max.response.millis", 120000);
        KAFKA_CLIENT_PRODUCE_MAX_BYTES = config.property("client.produce.max.bytes", Integer.MAX_VALUE);
        KAFKA_CACHE_DIRECTORY = config.property(Path.class, "cache.directory",
            KafkaConfiguration::cacheDirectory, KafkaBinding.NAME);
        KAFKA_CACHE_SERVER_BOOTSTRAP = config.property("cache.server.bootstrap", true);
        KAFKA_CACHE_PRODUCE_CAPACITY = config.property("cache.produce.capacity", Long.MAX_VALUE);
        KAFKA_CACHE_SERVER_RECONNECT_DELAY = config.property("cache.server.reconnect", 5);
        KAFKA_CACHE_CLIENT_RECONNECT_DELAY = config.property("cache.client.reconnect", 0);
        KAFKA_CACHE_CLIENT_CLEANUP_DELAY = config.property("cache.client.cleanup.delay", 30);
        KAFKA_CACHE_CLEANUP_POLICY = config.property(KafkaCacheCleanupPolicy.class, "cache.cleanup.policy",
                KafkaConfiguration::cleanupPolicy, "delete");
        KAFKA_CACHE_MAX_MESSAGE_BYTES = config.property("cache.max.message.bytes", 1000012);
        KAFKA_CACHE_RETENTION_MILLIS = config.property("cache.retention.ms", 604800000L);
        KAFKA_CACHE_RETENTION_BYTES = config.property("cache.retention.bytes", -1L);
        KAFKA_CACHE_DELETE_RETENTION_MILLIS = config.property("cache.delete.retention.ms", 86400000L);
        KAFKA_CACHE_MIN_COMPACTION_LAG_MILLIS = config.property("cache.min.compaction.lag.ms", 0L);
        KAFKA_CACHE_MAX_COMPACTION_LAG_MILLIS = config.property("cache.max.compaction.lag.ms", Long.MAX_VALUE);
        KAFKA_CACHE_MIN_CLEANABLE_DIRTY_RATIO = config.property("cache.min.cleanable.dirty.ratio", 0.5);
        KAFKA_CACHE_SEGMENT_MILLIS = config.property("cache.segment.ms", 604800000L);
        KAFKA_CACHE_SEGMENT_BYTES = config.property("cache.segment.bytes", 0x40000000);
        KAFKA_CACHE_SEGMENT_INDEX_BYTES = config.property("cache.segment.index.bytes", 0xA00000);
        KAFKA_CACHE_CLIENT_TRAILERS_SIZE_MAX = config.property("cache.client.trailers.size.max", 256);
        KAFKA_CLIENT_SASL_SCRAM_NONCE = config.property(NonceSupplier.class, "client.sasl.scram.nonce",
                KafkaConfiguration::decodeNonceSupplier, KafkaConfiguration::defaultNonceSupplier);
        KAFKA_CLIENT_GROUP_INSTANCE_ID = config.property("client.group.instance.id", UUID.randomUUID().toString());
        KAFKA_CLIENT_GROUP_REBALANCE_TIMEOUT = config.property(Duration.class, "client.group.rebalance.timeout",
            KafkaConfiguration::rebalanceTimeoutDuration, "PT4S");
        KAFKA_CLIENT_ID = config.property("client.id", "zilla");
        KAFKA_CLIENT_INSTANCE_ID_SUPPLIER = config.property(InstanceIdSupplier.class, "client.instance.id.supplier",
            KafkaConfiguration::decodeInstanceIdSupplier, KafkaConfiguration::defaultInstanceIdSupplier);
        KAFKA_CONFIG = config;
    }

    public KafkaConfiguration()
    {
        this(new Configuration());
    }

    public KafkaConfiguration(
        Configuration config)
    {
        super(KAFKA_CONFIG, config);
    }

    public long clientMaxIdleMillis()
    {
        return KAFKA_CLIENT_MAX_IDLE_MILLIS.getAsInt(this);
    }

    public long clientMetaMaxAgeMillis()
    {
        return KAFKA_CLIENT_META_MAX_AGE_MILLIS.getAsInt(this);
    }

    public long clientDescribeMaxAgeMillis()
    {
        return KAFKA_CLIENT_DESCRIBE_MAX_AGE_MILLIS.getAsInt(this);
    }

    public int clientFetchMaxWaitMillis()
    {
        return KAFKA_CLIENT_FETCH_MAX_WAIT_MILLIS.getAsInt(this);
    }

    public int clientFetchMaxBytes()
    {
        return KAFKA_CLIENT_FETCH_MAX_BYTES.getAsInt(this);
    }

    public int clientFetchPartitionMaxBytes()
    {
        return KAFKA_CLIENT_FETCH_PARTITION_MAX_BYTES.get(this);
    }

    public int clientProduceMaxRequestMillis()
    {
        return KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS.getAsInt(this);
    }

    public int clientProduceMaxResponseMillis()
    {
        return KAFKA_CLIENT_PRODUCE_MAX_RESPONSE_MILLIS.getAsInt(this);
    }

    public int clientProduceMaxBytes()
    {
        return KAFKA_CLIENT_PRODUCE_MAX_BYTES.getAsInt(this);
    }

    public Path cacheDirectory()
    {
        return KAFKA_CACHE_DIRECTORY.get(this);
    }

    public long cacheProduceCapacity()
    {
        return KAFKA_CACHE_PRODUCE_CAPACITY.get(this);
    }

    public KafkaCacheCleanupPolicy cacheCleanupPolicy()
    {
        return KAFKA_CACHE_CLEANUP_POLICY.get(this);
    }

    public int cacheMaxMessageBytes()
    {
        return KAFKA_CACHE_MAX_MESSAGE_BYTES.get(this);
    }

    public long cacheRetentionBytes()
    {
        return KAFKA_CACHE_RETENTION_BYTES.getAsLong(this);
    }

    public long cacheRetentionMillis()
    {
        return KAFKA_CACHE_RETENTION_MILLIS.getAsLong(this);
    }

    public long cacheSegmentMillis()
    {
        return KAFKA_CACHE_SEGMENT_MILLIS.getAsLong(this);
    }

    public long cacheDeleteRetentionMillis()
    {
        return KAFKA_CACHE_DELETE_RETENTION_MILLIS.getAsLong(this);
    }

    public long cacheMinCompactionLagMillis()
    {
        return KAFKA_CACHE_MIN_COMPACTION_LAG_MILLIS.getAsLong(this);
    }

    public long cacheMaxCompactionLagMillis()
    {
        return KAFKA_CACHE_MAX_COMPACTION_LAG_MILLIS.getAsLong(this);
    }

    public double cacheMinCleanableDirtyRatio()
    {
        return KAFKA_CACHE_MIN_CLEANABLE_DIRTY_RATIO.getAsDouble(this);
    }

    public int cacheSegmentBytes()
    {
        return KAFKA_CACHE_SEGMENT_BYTES.getAsInt(this);
    }

    public int cacheSegmentIndexBytes()
    {
        return KAFKA_CACHE_SEGMENT_INDEX_BYTES.getAsInt(this);
    }

    public boolean cacheServerBootstrap()
    {
        return KAFKA_CACHE_SERVER_BOOTSTRAP.getAsBoolean(this);
    }

    public int cacheClientReconnect()
    {
        return KAFKA_CACHE_CLIENT_RECONNECT_DELAY.getAsInt(this);
    }
    public int cacheClientCleanupDelay()
    {
        return KAFKA_CACHE_CLIENT_CLEANUP_DELAY.getAsInt(this);
    }

    public int cacheServerReconnect()
    {
        return KAFKA_CACHE_SERVER_RECONNECT_DELAY.getAsInt(this);
    }

    public int cacheClientTrailersSizeMax()
    {
        return KAFKA_CACHE_CLIENT_TRAILERS_SIZE_MAX.getAsInt(this);
    }

    public String clientGroupInstanceId()
    {
        return KAFKA_CLIENT_GROUP_INSTANCE_ID.get(this);
    }

    public String clientId()
    {
        return KAFKA_CLIENT_ID.get(this);
    }

    public Duration clientGroupRebalanceTimeout()
    {
        return KAFKA_CLIENT_GROUP_REBALANCE_TIMEOUT.get(this);
    }

    private static Path cacheDirectory(
        Configuration config,
        String cacheDirectory)
    {
        return ENGINE_CACHE_DIRECTORY.get(config).resolve(cacheDirectory);
    }

    private static KafkaCacheCleanupPolicy cleanupPolicy(
        Configuration config,
        String cleanupPolicy)
    {
        return KafkaCacheCleanupPolicy.valueOf(cleanupPolicy.toUpperCase());
    }

    private static Duration rebalanceTimeoutDuration(
        Configuration config,
        String timeout)
    {
        return Duration.parse(timeout);
    }

    public Supplier<String> nonceSupplier()
    {
        return KAFKA_CLIENT_SASL_SCRAM_NONCE.get(this)::get;
    }

    public Supplier<String> clientInstanceIdSupplier()
    {
        return KAFKA_CLIENT_INSTANCE_ID_SUPPLIER.get(this)::get;
    }

    @FunctionalInterface
    private interface NonceSupplier
    {
        String get();
    }

    private static NonceSupplier decodeNonceSupplier(
            Configuration config,
            String value)
    {
        NonceSupplier supplier = null;

        try
        {
            MethodType signature = MethodType.methodType(String.class);
            String[] parts = value.split("::");
            Class<?> ownerClass = Class.forName(parts[0]);
            String methodName = parts[1];
            MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
            supplier = () ->
            {
                String nonce = null;
                try
                {
                    nonce = (String) method.invoke();
                }
                catch (Throwable ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }

                return nonce;
            };
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return supplier;
    }

    private static NonceSupplier defaultNonceSupplier(
            Configuration config)
    {
        return () ->
                 new BigInteger(130, new SecureRandom()).toString(Character.MAX_RADIX);
    }

    @FunctionalInterface
    private interface InstanceIdSupplier extends Supplier<String>
    {
    }

    private static InstanceIdSupplier decodeInstanceIdSupplier(
        Configuration config,
        String value)
    {
        return () -> String.format("%s-%s", "zilla", UUID.randomUUID());
    }

    private static InstanceIdSupplier defaultInstanceIdSupplier(
        Configuration config)
    {
        return () -> String.format("%s-%s", KAFKA_CLIENT_ID.get(config), UUID.randomUUID());
    }
}
