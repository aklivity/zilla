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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;

public class HttpKafkaWithProduceHashTest
{
    @Test
    public void shouldCalculateCorrelationId()
    {
        // GIVEN
        String16FW correlationId = new String16FW("1");
        HttpKafkaWithProduceHash hash = new HttpKafkaWithProduceHash(correlationId);

        // WHEN
        hash.updateHash(new String16FW("Input 1").buffer());
        hash.updateHash(new String16FW("Input 2").buffer());
        hash.updateHash(new String16FW("Input 3").buffer());
        hash.digestHash();

        // THEN
        assertThat(hash.correlationId().asString(), equalTo("1-eee89e705930cd20684da32e9f768488"));
    }

    @Test
    public void shouldCalculateCorrelationIdConsistentlyIndependentOfInputOrder()
    {
        // GIVEN
        String16FW correlationId = new String16FW("1");
        HttpKafkaWithProduceHash hash1 = new HttpKafkaWithProduceHash(correlationId);
        HttpKafkaWithProduceHash hash2 = new HttpKafkaWithProduceHash(correlationId);
        HttpKafkaWithProduceHash hash3 = new HttpKafkaWithProduceHash(correlationId);

        // WHEN
        hash1.updateHash(new String16FW("Input 1").buffer());
        hash1.updateHash(new String16FW("Input 2").buffer());
        hash1.updateHash(new String16FW("Input 3").buffer());
        hash1.digestHash();

        hash2.updateHash(new String16FW("Input 3").buffer());
        hash2.updateHash(new String16FW("Input 2").buffer());
        hash2.updateHash(new String16FW("Input 1").buffer());
        hash2.digestHash();

        hash3.updateHash(new String16FW("Input 2").buffer());
        hash3.updateHash(new String16FW("Input 3").buffer());
        hash3.updateHash(new String16FW("Input 1").buffer());
        hash3.digestHash();

        // THEN
        assertThat(hash1.correlationId().asString(), equalTo("1-eee89e705930cd20684da32e9f768488"));
        assertThat(hash2.correlationId().asString(), equalTo("1-eee89e705930cd20684da32e9f768488"));
        assertThat(hash3.correlationId().asString(), equalTo("1-eee89e705930cd20684da32e9f768488"));
    }

    @Test
    public void shouldCalculateCorrelationIdConsistentlyIndependentOfInputOrderForLongerInputs()
    {
        // GIVEN
        String16FW correlationId = new String16FW("1");
        HttpKafkaWithProduceHash hash1 = new HttpKafkaWithProduceHash(correlationId);
        HttpKafkaWithProduceHash hash2 = new HttpKafkaWithProduceHash(correlationId);
        HttpKafkaWithProduceHash hash3 = new HttpKafkaWithProduceHash(correlationId);

        // WHEN
        hash1.updateHash(new String16FW("Input 4").buffer());
        hash1.updateHash(new String16FW("Input 42").buffer());
        hash1.updateHash(new String16FW("Input 4242").buffer());
        hash1.digestHash();

        hash2.updateHash(new String16FW("Input 4242").buffer());
        hash2.updateHash(new String16FW("Input 42").buffer());
        hash2.updateHash(new String16FW("Input 4").buffer());
        hash2.digestHash();

        hash3.updateHash(new String16FW("Input 42").buffer());
        hash3.updateHash(new String16FW("Input 4").buffer());
        hash3.updateHash(new String16FW("Input 4242").buffer());
        hash3.digestHash();

        // THEN
        assertThat(hash1.correlationId().asString(), equalTo("1-90362f12da9b41aba95c81b095fd7781"));
        assertThat(hash2.correlationId().asString(), equalTo("1-90362f12da9b41aba95c81b095fd7781"));
        assertThat(hash3.correlationId().asString(), equalTo("1-90362f12da9b41aba95c81b095fd7781"));
    }

    @Test
    public void shouldCalculateCorrelationIdConsistentlyIndependentOfInputOrderForDuplicatedInputs()
    {
        // GIVEN
        String16FW correlationId = new String16FW("1");
        HttpKafkaWithProduceHash hash1 = new HttpKafkaWithProduceHash(correlationId);
        HttpKafkaWithProduceHash hash2 = new HttpKafkaWithProduceHash(correlationId);
        HttpKafkaWithProduceHash hash3 = new HttpKafkaWithProduceHash(correlationId);

        // WHEN
        hash1.updateHash(new String16FW("Input 42").buffer());
        hash1.updateHash(new String16FW("Input 42").buffer());
        hash1.updateHash(new String16FW("Input 42").buffer());
        hash1.updateHash(new String16FW("Input 77").buffer());
        hash1.updateHash(new String16FW("Input 424242").buffer());
        hash1.updateHash(new String16FW("Input 424242").buffer());
        hash1.digestHash();

        hash2.updateHash(new String16FW("Input 424242").buffer());
        hash2.updateHash(new String16FW("Input 424242").buffer());
        hash2.updateHash(new String16FW("Input 42").buffer());
        hash2.updateHash(new String16FW("Input 42").buffer());
        hash2.updateHash(new String16FW("Input 77").buffer());
        hash2.updateHash(new String16FW("Input 42").buffer());
        hash2.digestHash();

        hash3.updateHash(new String16FW("Input 77").buffer());
        hash3.updateHash(new String16FW("Input 424242").buffer());
        hash3.updateHash(new String16FW("Input 42").buffer());
        hash3.updateHash(new String16FW("Input 424242").buffer());
        hash3.updateHash(new String16FW("Input 42").buffer());
        hash3.updateHash(new String16FW("Input 42").buffer());
        hash3.digestHash();

        // THEN
        assertThat(hash1.correlationId().asString(), equalTo("1-2285f6c8fbfda9041305b85eb5f4bb2b"));
        assertThat(hash2.correlationId().asString(), equalTo("1-2285f6c8fbfda9041305b85eb5f4bb2b"));
        assertThat(hash3.correlationId().asString(), equalTo("1-2285f6c8fbfda9041305b85eb5f4bb2b"));
    }
}
