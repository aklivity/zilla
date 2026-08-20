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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import java.util.List;

import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicHeaderType;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicTransformsType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

/**
 * A per-stream pipeline over the whole Kafka message — key, headers, and value together — with three
 * independently writable lanes that a {@link KafkaTransform} chain appends to in any interleaved order
 * during a single traversal.
 * <p>
 * {@code KafkaPipeline} sits one layer above {@link io.aklivity.zilla.runtime.engine.model.ModelPipeline}:
 * it owns the key's model pipeline (when a topic configures a structured key) and the value's model
 * pipeline, and translates their per-field output into its own event vocabulary, {@link KafkaEvent}. A
 * stage that finds a match therefore writes into the target lane the instant it is found rather than
 * capturing it for the owner to replay once the value completes.
 * </p>
 * <p>
 * The key and value lanes are driven separately because the cache entry's layout writes the key before
 * the value arrives; both drives share one transform chain and one terminal, so a stage sees the whole
 * message as one event stream regardless.
 * </p>
 * <p>
 * That same layout is why only two of the lane transitions the vocabulary can express have somewhere to
 * land today: a stage traversing the key may append to the key, and a stage traversing the value may
 * append to a header. Appending to the headers while traversing the key has no trailers under
 * construction yet; appending to the key while traversing the value comes after the key's hash has
 * already been computed and indexed; and the value's own bytes come from its model's output rather than
 * from this vocabulary, so there is nothing to append to the value lane at all. A stage reaching for any
 * of those is asserted rather than silently dropped, so lifting the restriction — by supporting fully
 * parallel key, headers, and value writes — stays a deliberate change.
 * </p>
 *
 * @see KafkaTransform
 * @see KafkaEvent
 */
public final class KafkaPipeline
{
    public static final KafkaPipeline NONE = new KafkaPipeline();

    // The terminal in force while the pipeline announces the lane it is traversing, and while no drive is
    // in flight. Neither has a destination to write into, so a stage appending content here is appending
    // where nothing can land.
    private static final KafkaSink ANNOUNCE = (control, source, event) ->
    {
        assert event != KafkaEvent.FIELD : "content appended with no destination to write it into";
        return ModelStatus.OK;
    };

    private final KafkaCacheModel key;
    private final KafkaCacheModel value;
    private final KafkaTransform transform;
    private final KafkaLaneGuard guard;

    private KafkaSink sink;

    public static KafkaPipeline decoder(
        ModelHandler keyModel,
        ModelHandler valueModel,
        KafkaTopicTransformsType transforms,
        MutableDirectBufferEx scratch)
    {
        final String extractKey = transforms != null ? transforms.extractKey : null;
        final List<KafkaTopicHeaderType> extractHeaders = transforms != null ? transforms.extractHeaders : null;

        return keyModel == null && valueModel == null
            ? NONE
            : new KafkaPipeline(keyModel, valueModel, extract(extractKey, extractHeaders),
                extractKey != null, extractHeaders != null && !extractHeaders.isEmpty(), scratch);
    }

    // visible for testing: a pipeline over an arbitrary stage chain, reaching the lane transitions the
    // entry's layout cannot honor, which no extractKey / extractHeaders configuration can produce. Named
    // apart from decoder rather than overloading it, so a null third argument stays unambiguous.
    static KafkaPipeline stagedDecoder(
        ModelHandler keyModel,
        ModelHandler valueModel,
        KafkaTransform transform,
        MutableDirectBufferEx scratch)
    {
        return new KafkaPipeline(keyModel, valueModel, transform, true, true, scratch);
    }

    private static KafkaTransform extract(
        String extractKey,
        List<KafkaTopicHeaderType> extractHeaders)
    {
        KafkaTransform transform = KafkaTransform.NONE;

        if (extractKey != null)
        {
            transform = transform.andThen(
                new KafkaExtractTransform(KafkaEvent.SWITCH_KEY, KafkaEvent.SWITCH_KEY, extractKey, extractKey));
        }

        if (extractHeaders != null)
        {
            for (KafkaTopicHeaderType header : extractHeaders)
            {
                transform = transform.andThen(
                    new KafkaExtractTransform(KafkaEvent.SWITCH_VALUE, KafkaEvent.SWITCH_HEADERS, header.path, header.name));
            }
        }

        return transform;
    }

    private KafkaPipeline()
    {
        this.key = KafkaCacheModel.NONE;
        this.value = KafkaCacheModel.NONE;
        this.transform = KafkaTransform.NONE;
        this.guard = new KafkaLaneGuard();
        this.sink = ANNOUNCE;
    }

    private KafkaPipeline(
        ModelHandler keyModel,
        ModelHandler valueModel,
        KafkaTransform transform,
        boolean extractingKey,
        boolean extractingHeaders,
        MutableDirectBufferEx scratch)
    {
        this.transform = transform;
        this.guard = new KafkaLaneGuard();
        this.sink = ANNOUNCE;
        this.key = model(keyModel, KafkaEvent.SWITCH_KEY, extractingKey, scratch);
        this.value = model(valueModel, KafkaEvent.SWITCH_VALUE, extractingHeaders, scratch);
    }

    /**
     * Whether a model is configured for the key lane, so the key is driven through this pipeline rather
     * than copied through unchanged.
     *
     * @return {@code true} if the key lane has a model
     */
    public boolean transformsKey()
    {
        return key != KafkaCacheModel.NONE;
    }

    /**
     * Whether a model is configured for the value lane, so the value is driven through this pipeline
     * rather than copied through unchanged.
     *
     * @return {@code true} if the value lane has a model
     */
    public boolean transformsValue()
    {
        return value != KafkaCacheModel.NONE;
    }

    /**
     * Drives the key through its model, selecting the key lane for the transform chain. A stage may append
     * to the key lane while this runs; appending to any other lane is not supported.
     *
     * @param traceId       the trace identifier for diagnostics
     * @param bindingId     the binding identifier
     * @param authorization the authorization in effect for the message being transformed
     * @param data          the buffer holding the untransformed key
     * @param index         the offset of the key
     * @param limit         the offset just past the key
     * @param next          receives the transformed key bytes
     * @param sink          the terminal each lane's content is written through
     * @return the transformed key length, or {@code -1} if the key was rejected
     */
    public int transformKey(
        long traceId,
        long bindingId,
        long authorization,
        DirectBufferEx data,
        int index,
        int limit,
        KafkaCacheModel.Output next,
        KafkaSink sink)
    {
        return drive(key, KafkaEvent.SWITCH_KEY, traceId, bindingId, authorization, data, index, limit, next, sink);
    }

    /**
     * Drives the value through its model, selecting the value lane for the transform chain. A stage may
     * append to the headers lane while this runs; appending to the key lane is not supported.
     *
     * @param traceId       the trace identifier for diagnostics
     * @param bindingId     the binding identifier
     * @param authorization the authorization in effect for the message being transformed
     * @param data          the buffer holding the untransformed value
     * @param index         the offset of the value
     * @param limit         the offset just past the value
     * @param next          receives the transformed value bytes
     * @param sink          the terminal each lane's content is written through
     * @return the transformed value length, or {@code -1} if the value was rejected
     */
    public int transformValue(
        long traceId,
        long bindingId,
        long authorization,
        DirectBufferEx data,
        int index,
        int limit,
        KafkaCacheModel.Output next,
        KafkaSink sink)
    {
        return drive(value, KafkaEvent.SWITCH_VALUE, traceId, bindingId, authorization, data, index, limit, next, sink);
    }

    /**
     * Returns the number of additional bytes the value lane's model may add to the given value.
     *
     * @param data   the buffer holding the untransformed value
     * @param index  the offset of the value
     * @param length the length of the value
     * @return the padding byte count
     */
    public int padding(
        DirectBufferEx data,
        int index,
        int length)
    {
        return value.padding(data, index, length);
    }

    /**
     * Resets both lanes and the transform chain so this pipeline is ready for the next message.
     */
    public void reset()
    {
        key.reset();
        value.reset();
        transform.reset();
    }

    private int drive(
        KafkaCacheModel model,
        KafkaEvent lane,
        long traceId,
        long bindingId,
        long authorization,
        DirectBufferEx data,
        int index,
        int limit,
        KafkaCacheModel.Output next,
        KafkaSink sink)
    {
        this.sink = guard.begin(lane, sink);
        transform.reset();

        final int transformed = model.transform(traceId, bindingId, authorization, data, index, limit, next);

        this.sink = ANNOUNCE;

        return transformed;
    }

    private KafkaCacheModel model(
        ModelHandler handler,
        KafkaEvent lane,
        boolean extracting,
        MutableDirectBufferEx scratch)
    {
        return KafkaCacheModel.decoder(handler, extracting ? new KafkaLane(lane) : ModelTransform.NONE, scratch);
    }

    // Holds the transform chain to the lane transitions the cache entry's layout can honor. The vocabulary
    // can express a stage appending to any lane from any lane, so the ones with nowhere to land are caught
    // here rather than silently dropped by the terminal.
    private final class KafkaLaneGuard implements KafkaSink
    {
        private KafkaEvent origin;
        private KafkaSink terminal;

        @Override
        public ModelStatus transform(
            KafkaController control,
            KafkaSource source,
            KafkaEvent event)
        {
            assert event == KafkaEvent.FIELD ||
                event == origin ||
                origin == KafkaEvent.SWITCH_VALUE && event == KafkaEvent.SWITCH_HEADERS :
                String.format("%s while traversing %s is not supported", event, origin);

            return terminal.transform(control, source, event);
        }

        private KafkaSink begin(
            KafkaEvent origin,
            KafkaSink terminal)
        {
            this.origin = origin;
            this.terminal = terminal;
            return this;
        }
    }

    // The bridge from one model's per-field events into this pipeline's whole-message vocabulary. It
    // announces its lane as the value opens, republishes each field as a KafkaEvent.FIELD in that lane,
    // and forwards the model event on unchanged — extraction observes the model's output, it never
    // rewrites it, so this stage is always an identity from the model's point of view.
    //
    // The opening announcement reaches the stages but not the terminal: it says where the fields that
    // follow are coming from, not where anything is going, and a field the traversal merely surfaces is
    // not content appended to a destination. Only a switch a stage raises selects a destination, which is
    // what lets extractKey read the key and write the key without the two being confused.
    private final class KafkaLane implements ModelTransform, KafkaSource, KafkaController
    {
        private final KafkaEvent lane;

        private ModelSource source;
        private ModelController control;

        private KafkaLane(
            KafkaEvent lane)
        {
            this.lane = lane;
        }

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            this.control = control;
            this.source = source;

            ModelStatus status = ModelStatus.OK;

            if (event == ModelEvent.START_VALUE)
            {
                status = transform.transform(this, this, lane, ANNOUNCE);
            }
            else if (event == ModelEvent.FIELD)
            {
                status = transform.transform(this, this, KafkaEvent.FIELD, KafkaPipeline.this.sink);
            }

            return status == ModelStatus.REJECTED
                ? status
                : sink.transform(control, source, event);
        }

        @Override
        public boolean identity()
        {
            return true;
        }

        @Override
        public String getPath()
        {
            return source.getPath();
        }

        @Override
        public DirectBufferEx getValue()
        {
            return source.getValue();
        }

        @Override
        public void reject(
            String diagnostic)
        {
            control.reject(diagnostic);
        }
    }
}
