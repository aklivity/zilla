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
module io.aklivity.zilla.runtime.model.avro
{
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;
    requires io.aklivity.zilla.runtime.engine;

    exports io.aklivity.zilla.runtime.model.avro.config;

    uses io.aklivity.zilla.runtime.model.avro.internal.avro.Conversion;
    uses io.aklivity.zilla.runtime.model.avro.internal.avro.LogicalTypes$LogicalTypeFactory;

    provides io.aklivity.zilla.runtime.engine.config.ModelConfigAdapterSpi
        with io.aklivity.zilla.runtime.model.avro.internal.config.AvroModelConfigAdapter;

    provides io.aklivity.zilla.runtime.engine.model.ModelFactorySpi
        with io.aklivity.zilla.runtime.model.avro.internal.AvroModelFactorySpi;

    provides io.aklivity.zilla.runtime.engine.event.EventFormatterFactorySpi
        with io.aklivity.zilla.runtime.model.avro.internal.AvroModelEventFormatterFactory;
}
