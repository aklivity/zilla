/*
 * Copyright 2021-2026 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_CLIENT_CLEANUP_DELAY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_CLIENT_TRAILERS_SIZE_MAX;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_PRODUCE_CAPACITY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_PRODUCE_DIRECTORY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_PRODUCE_SEGMENT_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_PRODUCE_SEGMENT_INDEX_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_RETENTION_MILLIS_MAX;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_RECONNECT_DELAY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_SHARE_LOCK_MARGIN_PERCENT;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_SHARE_STAGING_MAX_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_SHARE_STAGING_SEGMENT_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_TRAILERS_SIZE_MAX;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_API_VERSIONS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_CONNECTION_POOL_CLEANUP_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_DESCRIBE_CONFIG_INCLUDE_SYNONYMS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_INSTANCE_ID;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_META_MAX_AGE_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_PRODUCE_MAX_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_PRODUCE_MAX_IN_FLIGHT;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_RECONNECT_DELAY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_SASL_SCRAM_NONCE;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_SHARE_FETCH_MAX_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_SHARE_FETCH_MAX_RECORDS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_SHARE_FETCH_MAX_WAIT_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_SHARE_FETCH_MIN_BYTES;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CACHE_DIRECTORY;
import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.util.Properties;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class KafkaConfigurationTest
{
    public static final String KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS_NAME =
            "zilla.binding.kafka.client.produce.max.request.millis";
    public static final String KAFKA_CLIENT_PRODUCE_MAX_BYTES_NAME = "zilla.binding.kafka.client.produce.max.bytes";
    public static final String KAFKA_CLIENT_PRODUCE_MAX_IN_FLIGHT_NAME = "zilla.binding.kafka.client.produce.max.in.flight";
    public static final String KAFKA_CLIENT_CONNECTION_POOL_CLEANUP_MILLIS_NAME =
        "zilla.binding.kafka.client.connection.pool.cleanup.millis";
    public static final String KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME = "zilla.binding.kafka.cache.server.reconnect";
    public static final String KAFKA_CACHE_CLIENT_CLEANUP_DELAY_NAME = "zilla.binding.kafka.cache.client.cleanup.delay";
    public static final String KAFKA_CACHE_CLIENT_TRAILERS_SIZE_MAX_NAME =
        "zilla.binding.kafka.cache.client.trailers.size.max";
    public static final String KAFKA_CACHE_SERVER_TRAILERS_SIZE_MAX_NAME =
        "zilla.binding.kafka.cache.server.trailers.size.max";
    public static final String KAFKA_CLIENT_SASL_SCRAM_NONCE_NAME = "zilla.binding.kafka.client.sasl.scram.nonce";
    public static final String KAFKA_CLIENT_INSTANCE_ID_NAME = "zilla.binding.kafka.client.instance.id";
    public static final String KAFKA_CLIENT_DESCRIBE_CONFIG_INCLUDE_SYNONYMS_NAME =
        "zilla.binding.kafka.client.describe.config.include.synonyms";
    public static final String KAFKA_CLIENT_API_VERSIONS_NAME = "zilla.binding.kafka.client.api.versions";
    public static final String KAFKA_CLIENT_META_MAX_AGE_MILLIS_NAME = "zilla.binding.kafka.client.meta.max.age.ms";
    public static final String KAFKA_CLIENT_RECONNECT_DELAY_NAME = "zilla.binding.kafka.client.reconnect";
    public static final String KAFKA_CLIENT_SHARE_FETCH_MAX_WAIT_MILLIS_NAME =
        "zilla.binding.kafka.client.share.fetch.max.wait.millis";
    public static final String KAFKA_CLIENT_SHARE_FETCH_MIN_BYTES_NAME = "zilla.binding.kafka.client.share.fetch.min.bytes";
    public static final String KAFKA_CLIENT_SHARE_FETCH_MAX_BYTES_NAME = "zilla.binding.kafka.client.share.fetch.max.bytes";
    public static final String KAFKA_CLIENT_SHARE_FETCH_MAX_RECORDS_NAME = "zilla.binding.kafka.client.share.fetch.max.records";
    public static final String KAFKA_CACHE_SERVER_SHARE_STAGING_MAX_BYTES_NAME =
        "zilla.binding.kafka.cache.server.share.staging.max.bytes";
    public static final String KAFKA_CACHE_SERVER_SHARE_STAGING_SEGMENT_BYTES_NAME =
        "zilla.binding.kafka.cache.server.share.staging.segment.bytes";
    public static final String KAFKA_CACHE_SERVER_SHARE_LOCK_MARGIN_PERCENT_NAME =
        "zilla.binding.kafka.cache.server.share.lock.margin.percent";

    public static final String KAFKA_CACHE_PRODUCE_DIRECTORY_NAME = "zilla.binding.kafka.cache.produce.directory";
    public static final String KAFKA_CACHE_PRODUCE_CAPACITY_NAME = "zilla.binding.kafka.cache.produce.capacity";
    public static final String KAFKA_CACHE_PRODUCE_SEGMENT_BYTES_NAME = "zilla.binding.kafka.cache.produce.segment.bytes";
    public static final String KAFKA_CACHE_MAX_MESSAGE_BYTES_NAME = "zilla.binding.kafka.cache.max.message.bytes";
    public static final String KAFKA_CACHE_PRODUCE_SEGMENT_INDEX_BYTES_NAME =
        "zilla.binding.kafka.cache.produce.segment.index.bytes";
    public static final String KAFKA_CACHE_RETENTION_MILLIS_MAX_NAME =
        "zilla.binding.kafka.cache.retention.ms.max";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS.name(), KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS_NAME);
        assertEquals(KAFKA_CLIENT_CONNECTION_POOL_CLEANUP_MILLIS.name(), KAFKA_CLIENT_CONNECTION_POOL_CLEANUP_MILLIS_NAME);
        assertEquals(KAFKA_CLIENT_PRODUCE_MAX_BYTES.name(), KAFKA_CLIENT_PRODUCE_MAX_BYTES_NAME);
        assertEquals(KAFKA_CLIENT_PRODUCE_MAX_IN_FLIGHT.name(), KAFKA_CLIENT_PRODUCE_MAX_IN_FLIGHT_NAME);
        assertEquals(KAFKA_CACHE_SERVER_RECONNECT_DELAY.name(), KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME);
        assertEquals(KAFKA_CACHE_CLIENT_CLEANUP_DELAY.name(), KAFKA_CACHE_CLIENT_CLEANUP_DELAY_NAME);
        assertEquals(KAFKA_CACHE_CLIENT_TRAILERS_SIZE_MAX.name(), KAFKA_CACHE_CLIENT_TRAILERS_SIZE_MAX_NAME);
        assertEquals(KAFKA_CACHE_SERVER_TRAILERS_SIZE_MAX.name(), KAFKA_CACHE_SERVER_TRAILERS_SIZE_MAX_NAME);
        assertEquals(KAFKA_CLIENT_SASL_SCRAM_NONCE.name(), KAFKA_CLIENT_SASL_SCRAM_NONCE_NAME);
        assertEquals(KAFKA_CLIENT_INSTANCE_ID.name(), KAFKA_CLIENT_INSTANCE_ID_NAME);
        assertEquals(KAFKA_CACHE_PRODUCE_DIRECTORY.name(), KAFKA_CACHE_PRODUCE_DIRECTORY_NAME);
        assertEquals(KAFKA_CACHE_PRODUCE_CAPACITY.name(), KAFKA_CACHE_PRODUCE_CAPACITY_NAME);
        assertEquals(KAFKA_CACHE_PRODUCE_SEGMENT_BYTES.name(), KAFKA_CACHE_PRODUCE_SEGMENT_BYTES_NAME);
        assertEquals(KAFKA_CACHE_PRODUCE_SEGMENT_INDEX_BYTES.name(), KAFKA_CACHE_PRODUCE_SEGMENT_INDEX_BYTES_NAME);
        assertEquals(KAFKA_CACHE_RETENTION_MILLIS_MAX.name(), KAFKA_CACHE_RETENTION_MILLIS_MAX_NAME);
        assertEquals(KAFKA_CLIENT_DESCRIBE_CONFIG_INCLUDE_SYNONYMS.name(), KAFKA_CLIENT_DESCRIBE_CONFIG_INCLUDE_SYNONYMS_NAME);
        assertEquals(KAFKA_CLIENT_API_VERSIONS.name(), KAFKA_CLIENT_API_VERSIONS_NAME);
        assertEquals(KAFKA_CLIENT_META_MAX_AGE_MILLIS.name(), KAFKA_CLIENT_META_MAX_AGE_MILLIS_NAME);
        assertEquals(KAFKA_CLIENT_RECONNECT_DELAY.name(), KAFKA_CLIENT_RECONNECT_DELAY_NAME);
        assertEquals(KAFKA_CLIENT_SHARE_FETCH_MAX_WAIT_MILLIS.name(), KAFKA_CLIENT_SHARE_FETCH_MAX_WAIT_MILLIS_NAME);
        assertEquals(KAFKA_CLIENT_SHARE_FETCH_MIN_BYTES.name(), KAFKA_CLIENT_SHARE_FETCH_MIN_BYTES_NAME);
        assertEquals(KAFKA_CLIENT_SHARE_FETCH_MAX_BYTES.name(), KAFKA_CLIENT_SHARE_FETCH_MAX_BYTES_NAME);
        assertEquals(KAFKA_CLIENT_SHARE_FETCH_MAX_RECORDS.name(), KAFKA_CLIENT_SHARE_FETCH_MAX_RECORDS_NAME);
        assertEquals(KAFKA_CACHE_SERVER_SHARE_STAGING_MAX_BYTES.name(), KAFKA_CACHE_SERVER_SHARE_STAGING_MAX_BYTES_NAME);
        assertEquals(KAFKA_CACHE_SERVER_SHARE_STAGING_SEGMENT_BYTES.name(), KAFKA_CACHE_SERVER_SHARE_STAGING_SEGMENT_BYTES_NAME);
        assertEquals(KAFKA_CACHE_SERVER_SHARE_LOCK_MARGIN_PERCENT.name(), KAFKA_CACHE_SERVER_SHARE_LOCK_MARGIN_PERCENT_NAME);
    }

    @Test
    public void shouldDefaultProduceMaxInFlightToUnbounded() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();

        assertEquals(Integer.MAX_VALUE, config.clientProduceMaxInFlight());
    }

    @Test
    public void shouldDefaultProduceDirectoryToCacheDirectory() throws Exception
    {
        KafkaConfiguration config = new KafkaConfiguration();

        assertEquals(config.cacheDirectory(), config.cacheProduceDirectory());
    }

    @Test
    public void shouldResolveRelativeProduceDirectoryAgainstEngineCacheDirectory() throws Exception
    {
        Properties properties = new Properties();
        properties.setProperty(ENGINE_CACHE_DIRECTORY.name(), "/var/zilla");
        properties.setProperty(KAFKA_CACHE_PRODUCE_DIRECTORY.name(), "staging");
        KafkaConfiguration config = new KafkaConfiguration(new Configuration(properties));

        assertEquals(Path.of("/var/zilla/staging"), config.cacheProduceDirectory());
    }

    @Test
    public void shouldUseAbsoluteProduceDirectory() throws Exception
    {
        Properties properties = new Properties();
        properties.setProperty(KAFKA_CACHE_PRODUCE_DIRECTORY.name(), "/dev/shm/zilla");
        KafkaConfiguration config = new KafkaConfiguration(new Configuration(properties));

        assertEquals(Path.of("/dev/shm/zilla"), config.cacheProduceDirectory());
    }

    @Test
    public void shouldDefaultProduceSegmentBytesTo64MiBAndIndexBytesToCacheIndexBytes() throws Exception
    {
        Properties properties = new Properties();
        properties.setProperty("zilla.binding.kafka.cache.segment.bytes", "2097152");
        properties.setProperty("zilla.binding.kafka.cache.segment.index.bytes", "65536");
        KafkaConfiguration config = new KafkaConfiguration(new Configuration(properties));

        assertEquals(64 * 1024 * 1024, config.cacheProduceSegmentBytes());
        assertEquals(65536, config.cacheProduceSegmentIndexBytes());
    }

    @Test
    public void shouldOverrideProduceSegmentSizes() throws Exception
    {
        Properties properties = new Properties();
        properties.setProperty("zilla.binding.kafka.cache.segment.bytes", "2097152");
        properties.setProperty(KAFKA_CACHE_PRODUCE_SEGMENT_BYTES.name(), "1048576");
        properties.setProperty(KAFKA_CACHE_PRODUCE_SEGMENT_INDEX_BYTES.name(), "32768");
        KafkaConfiguration config = new KafkaConfiguration(new Configuration(properties));

        assertEquals(2097152, config.cacheSegmentBytes());
        assertEquals(1048576, config.cacheProduceSegmentBytes());
        assertEquals(32768, config.cacheProduceSegmentIndexBytes());
    }
}
