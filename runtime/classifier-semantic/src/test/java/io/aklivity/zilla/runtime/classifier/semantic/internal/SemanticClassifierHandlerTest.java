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
package io.aklivity.zilla.runtime.classifier.semantic.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.aklivity.zilla.config.classifier.semantic.SemanticClassifierOptionsConfig;
import io.aklivity.zilla.config.engine.ClassifierConfig;
import io.aklivity.zilla.config.engine.GenericClassifierConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.Detector;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public class SemanticClassifierHandlerTest
{
    private static final long EMBEDDING_ID = 11L;
    private static final long STORE_ID = 22L;
    private static final long CONTEXT_ID = 33L;
    private static final List<String> PHRASES = List.of("alpha", "beta", "gamma");
    private static final float[][] VECTORS = {{1.0f, 0.0f}, {0.0f, 1.0f}, {1.0f, 1.0f}};

    private EmbeddingHandler embedding;
    private StoreHandler store;
    private Signaler signaler;
    private SemanticClassifierHandler handler;
    private RecordingCompletion completion;

    @Before
    public void init()
    {
        embedding = mock(EmbeddingHandler.class);
        store = mock(StoreHandler.class);
        signaler = mock(Signaler.class);
        completion = new RecordingCompletion();

        EngineContext context = mock(EngineContext.class);
        when(context.supplyEmbedding(EMBEDDING_ID)).thenReturn(embedding);
        when(context.supplyStore(STORE_ID)).thenReturn(store);
        when(context.signaler()).thenReturn(signaler);
        when(signaler.signalAt(anyLong(), anyInt(), any(IntConsumer.class))).thenReturn(1L);

        SemanticClassifierOptionsConfig options = SemanticClassifierOptionsConfig.builder()
            .threshold(0.9)
            .label("first", List.of("alpha", "beta"))
            .label("second", List.of("gamma"))
            .build();

        ClassifierConfig config = GenericClassifierConfig.builder()
            .namespace("test")
            .name("moderator0")
            .type("semantic")
            .embedding("embedding0")
            .store("store0")
            .options(options)
            .build();
        config.embeddingId = EMBEDDING_ID;
        config.storeId = STORE_ID;

        handler = new SemanticClassifierHandler(context, config, new SemanticClassifierConfiguration(new Configuration()));
    }

    @Test
    public void shouldNotSupportAnonymizerOrDeanonymizer()
    {
        assertThat(handler.initAnonymizer(List.of("first")), nullValue());
        assertThat(handler.initDeanonymizer(List.of("first")), nullValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectUnknownLabel()
    {
        handler.initDetector(List.of("unknown"));
    }

    @Test
    public void shouldDetectWhenSimilarityMeetsThreshold()
    {
        Detector detector = handler.initDetector(List.of("first"));
        ready();

        detector.detect(1L, 2L, CONTEXT_ID, "input", completion);
        completeEmbed(new float[][] {{1.0f, 0.0f}});

        assertThat(completion.contextId, equalTo(CONTEXT_ID));
        assertThat(completion.detected, equalTo(true));
    }

    @Test
    public void shouldNotDetectWhenSimilarityBelowThreshold()
    {
        Detector detector = handler.initDetector(List.of("first"));
        ready();

        detector.detect(1L, 2L, CONTEXT_ID, "input", completion);
        completeEmbed(new float[][] {{1.0f, 1.0f}});

        assertThat(completion.detected, equalTo(false));
        assertThat(completion.failed, nullValue());
    }

    @Test
    public void shouldDetectOnlyRequestedLabels()
    {
        Detector first = handler.initDetector(List.of("first"));
        Detector second = handler.initDetector(List.of("second"));
        Detector both = handler.initDetector(List.of("first", "second"));
        ready();

        RecordingCompletion firstCompletion = new RecordingCompletion();
        RecordingCompletion secondCompletion = new RecordingCompletion();
        RecordingCompletion bothCompletion = new RecordingCompletion();

        first.detect(1L, 2L, 1L, "input", firstCompletion);
        completeEmbed(new float[][] {{2.0f, 2.0f}});
        second.detect(1L, 2L, 2L, "input", secondCompletion);
        completeEmbed(new float[][] {{2.0f, 2.0f}});
        both.detect(1L, 2L, 3L, "input", bothCompletion);
        completeEmbed(new float[][] {{2.0f, 2.0f}});

        assertThat(firstCompletion.detected, equalTo(false));
        assertThat(secondCompletion.detected, equalTo(true));
        assertThat(bothCompletion.detected, equalTo(true));
    }

    @Test
    public void shouldEmbedValueWithCallerIdentity()
    {
        Detector detector = handler.initDetector(List.of("first"));
        ready();

        detector.detect(7L, 8L, CONTEXT_ID, "input", completion);

        verify(embedding).embed(eq(7L), eq(8L), eq(CONTEXT_ID), eq(List.of("input")), any());
    }

    @Test
    public void shouldFailWhenEmbeddingFails()
    {
        Detector detector = handler.initDetector(List.of("first"));
        ready();
        IllegalStateException cause = new IllegalStateException("embedding unavailable");

        detector.detect(1L, 2L, CONTEXT_ID, "input", completion);
        embedCallback().failed(CONTEXT_ID, cause);

        assertThat(completion.contextId, equalTo(CONTEXT_ID));
        assertThat(completion.failed, equalTo(cause));
        assertThat(completion.detected, nullValue());
    }

    @Test
    public void shouldFailWhenInputVectorIsNull()
    {
        Detector detector = handler.initDetector(List.of("first"));
        ready();

        detector.detect(1L, 2L, CONTEXT_ID, "input", completion);
        completeEmbed(new float[][] {null});

        assertThat(completion.failed, instanceOf(IllegalStateException.class));
        assertThat(completion.detected, nullValue());
    }

    @Test
    public void shouldFailWhenResultsAreMissing()
    {
        Detector detector = handler.initDetector(List.of("first"));
        ready();

        detector.detect(1L, 2L, CONTEXT_ID, "input", completion);
        completeEmbed(null);

        assertThat(completion.failed, instanceOf(IllegalStateException.class));
    }

    @Test
    public void shouldFailWhenResultCountDiffers()
    {
        Detector detector = handler.initDetector(List.of("first"));
        ready();

        detector.detect(1L, 2L, CONTEXT_ID, "input", completion);
        completeEmbed(new float[][] {{1.0f, 0.0f}, {0.0f, 1.0f}});

        assertThat(completion.failed, instanceOf(IllegalStateException.class));
    }

    @Test
    public void shouldDeferDetectionUntilReferencesAreReady()
    {
        Detector detector = handler.initDetector(List.of("first"));

        detector.detect(1L, 2L, CONTEXT_ID, "input", completion);
        verify(embedding, never()).embed(anyLong(), anyLong(), anyLong(), eq(List.of("input")), any());

        ready();

        verify(embedding).embed(eq(1L), eq(2L), eq(CONTEXT_ID), eq(List.of("input")), any());
    }

    @Test
    public void shouldFailDetectionWhenReferencesNeverBecomeReady()
    {
        Detector detector = handler.initDetector(List.of("first"));
        detector.detect(1L, 2L, CONTEXT_ID, "input", completion);

        ArgumentCaptor<IntConsumer> timeout = ArgumentCaptor.forClass(IntConsumer.class);
        verify(signaler, atLeastOnce()).signalAt(anyLong(), eq(SemanticReferences.TIMEOUT_SIGNAL_ID), timeout.capture());
        timeout.getValue().accept(SemanticReferences.TIMEOUT_SIGNAL_ID);

        assertThat(completion.contextId, equalTo(CONTEXT_ID));
        assertThat(completion.failed, instanceOf(TimeoutException.class));
        assertThat(completion.detected, nullValue());
    }

    @Test
    public void shouldFailPendingDetectionWhenClosed()
    {
        Detector detector = handler.initDetector(List.of("first"));
        detector.detect(1L, 2L, CONTEXT_ID, "input", completion);

        handler.close();

        assertThat(completion.failed, not(nullValue()));
    }

    private void ready()
    {
        ArgumentCaptor<BiConsumer<String, String>> get = ArgumentCaptor.forClass(BiConsumer.class);
        verify(store, atLeastOnce()).get(any(), get.capture());
        get.getValue().accept(null, SemanticVectorCodec.encode(VECTORS));
    }

    private void completeEmbed(
        float[][] results)
    {
        embedCallback().completed(CONTEXT_ID, results);
    }

    private EmbeddingHandler.CompletionCallback embedCallback()
    {
        ArgumentCaptor<EmbeddingHandler.CompletionCallback> callback =
            ArgumentCaptor.forClass(EmbeddingHandler.CompletionCallback.class);
        verify(embedding, atLeastOnce()).embed(anyLong(), anyLong(), anyLong(), eq(List.of("input")), callback.capture());
        return callback.getValue();
    }

    private static final class RecordingCompletion implements Detector.CompletionCallback
    {
        private Long contextId;
        private Boolean detected;
        private Throwable failed;

        @Override
        public void completed(
            long contextId,
            boolean detected)
        {
            this.contextId = contextId;
            this.detected = detected;
        }

        @Override
        public void failed(
            long contextId,
            Throwable ex)
        {
            this.contextId = contextId;
            this.failed = ex;
        }
    }
}
