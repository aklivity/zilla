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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types;

import static java.lang.Integer.MAX_VALUE;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableSet;

import java.util.LinkedHashSet;
import java.util.Set;

import org.kaazing.k3po.lang.types.StructuredTypeInfo;
import org.kaazing.k3po.lang.types.TypeInfo;
import org.kaazing.k3po.lang.types.TypeSystemSpi;

public final class ZillaTypeSystem implements TypeSystemSpi
{
    public static final String NAME = "zilla";

    public static final TypeInfo<String> OPTION_EPHEMERAL = new TypeInfo<>("ephemeral", String.class);
    public static final TypeInfo<String> OPTION_REPLY_TO = new TypeInfo<>("replyTo", String.class);
    public static final TypeInfo<Integer> OPTION_WINDOW = new TypeInfo<>("window", Integer.class);
    public static final TypeInfo<Integer> OPTION_SHARED_WINDOW = new TypeInfo<>("sharedWindow", Integer.class);
    public static final TypeInfo<Long> OPTION_BUDGET_ID = new TypeInfo<>("budgetId", Long.class);
    public static final TypeInfo<Long> OPTION_STREAM_ID = new TypeInfo<>("streamId", Long.class);
    public static final TypeInfo<Integer> OPTION_PADDING = new TypeInfo<>("padding", Integer.class);
    public static final TypeInfo<String> OPTION_UPDATE = new TypeInfo<>("update", String.class);
    public static final TypeInfo<String> OPTION_TRANSMISSION = new TypeInfo<>("transmission", String.class);
    public static final TypeInfo<String> OPTION_THROTTLE = new TypeInfo<>("throttle", String.class);
    public static final TypeInfo<Long> OPTION_AUTHORIZATION = new TypeInfo<>("authorization", Long.class);
    public static final TypeInfo<String> OPTION_BYTE_ORDER = new TypeInfo<>("byteorder", String.class);
    public static final TypeInfo<String> OPTION_ALIGNMENT = new TypeInfo<>("alignment", String.class);
    public static final TypeInfo<Long> OPTION_AFFINITY = new TypeInfo<>("affinity", Long.class);
    public static final TypeInfo<Byte> OPTION_CAPABILITIES = new TypeInfo<>("capabilities", Byte.class);
    public static final TypeInfo<Integer> OPTION_FLAGS = new TypeInfo<>("flags", Integer.class);
    public static final TypeInfo<Integer> OPTION_ACK = new TypeInfo<>("ack", Integer.class);

    public static final StructuredTypeInfo CONFIG_BEGIN_EXT =
            new StructuredTypeInfo(NAME, "begin.ext", emptyList(), MAX_VALUE);
    public static final StructuredTypeInfo CONFIG_DATA_EXT =
            new StructuredTypeInfo(NAME, "data.ext", emptyList(), MAX_VALUE);
    public static final StructuredTypeInfo CONFIG_DATA_EMPTY =
            new StructuredTypeInfo(NAME, "data.empty", emptyList(), 0);
    public static final StructuredTypeInfo CONFIG_DATA_NULL =
            new StructuredTypeInfo(NAME, "data.null", emptyList(), 0);
    public static final StructuredTypeInfo CONFIG_ABORT_EXT =
        new StructuredTypeInfo(NAME, "abort.ext", emptyList(), MAX_VALUE);
    public static final StructuredTypeInfo CONFIG_END_EXT =
            new StructuredTypeInfo(NAME, "end.ext", emptyList(), MAX_VALUE);
    public static final StructuredTypeInfo CONFIG_RESET_EXT =
            new StructuredTypeInfo(NAME, "reset.ext", emptyList(), MAX_VALUE);

    public static final StructuredTypeInfo ADVISORY_FLUSH =
            new StructuredTypeInfo(NAME, "flush", emptyList(), MAX_VALUE);
    public static final StructuredTypeInfo ADVISORY_CHALLENGE =
            new StructuredTypeInfo(NAME, "challenge", emptyList(), MAX_VALUE);


    private final Set<TypeInfo<?>> acceptOptions;
    private final Set<TypeInfo<?>> connectOptions;
    private final Set<TypeInfo<?>> readOptions;
    private final Set<TypeInfo<?>> writeOptions;
    private final Set<StructuredTypeInfo> readConfigs;
    private final Set<StructuredTypeInfo> writeConfigs;
    private final Set<StructuredTypeInfo> readAdvisories;
    private final Set<StructuredTypeInfo> writeAdvisories;

    public ZillaTypeSystem()
    {
        Set<TypeInfo<?>> acceptOptions = new LinkedHashSet<>();
        acceptOptions.add(OPTION_REPLY_TO);
        acceptOptions.add(OPTION_WINDOW);
        acceptOptions.add(OPTION_SHARED_WINDOW);
        acceptOptions.add(OPTION_BUDGET_ID);
        acceptOptions.add(OPTION_PADDING);
        acceptOptions.add(OPTION_UPDATE);
        acceptOptions.add(OPTION_AUTHORIZATION);
        acceptOptions.add(OPTION_THROTTLE);
        acceptOptions.add(OPTION_TRANSMISSION);
        acceptOptions.add(OPTION_BYTE_ORDER);
        acceptOptions.add(OPTION_ALIGNMENT);
        acceptOptions.add(OPTION_CAPABILITIES);
        this.acceptOptions = unmodifiableSet(acceptOptions);

        Set<TypeInfo<?>> connectOptions = new LinkedHashSet<>();
        connectOptions.add(OPTION_EPHEMERAL);
        connectOptions.add(OPTION_REPLY_TO);
        connectOptions.add(OPTION_WINDOW);
        connectOptions.add(OPTION_SHARED_WINDOW);
        connectOptions.add(OPTION_BUDGET_ID);
        connectOptions.add(OPTION_STREAM_ID);
        connectOptions.add(OPTION_PADDING);
        connectOptions.add(OPTION_UPDATE);
        connectOptions.add(OPTION_AUTHORIZATION);
        connectOptions.add(OPTION_THROTTLE);
        connectOptions.add(OPTION_TRANSMISSION);
        connectOptions.add(OPTION_BYTE_ORDER);
        connectOptions.add(OPTION_ALIGNMENT);
        connectOptions.add(OPTION_AFFINITY);
        connectOptions.add(OPTION_CAPABILITIES);
        this.connectOptions = unmodifiableSet(connectOptions);

        Set<TypeInfo<?>> readOptions = new LinkedHashSet<>();
        readOptions.add(OPTION_FLAGS);
        readOptions.add(OPTION_ACK);
        this.readOptions = unmodifiableSet(readOptions);

        Set<TypeInfo<?>> writeOptions = new LinkedHashSet<>();
        writeOptions.add(OPTION_FLAGS);
        this.writeOptions = unmodifiableSet(writeOptions);

        Set<StructuredTypeInfo> readConfigs = new LinkedHashSet<>();
        readConfigs.add(CONFIG_BEGIN_EXT);
        readConfigs.add(CONFIG_DATA_EXT);
        readConfigs.add(CONFIG_DATA_EMPTY);
        readConfigs.add(CONFIG_DATA_NULL);
        readConfigs.add(CONFIG_ABORT_EXT);
        readConfigs.add(CONFIG_END_EXT);
        readConfigs.add(CONFIG_RESET_EXT);
        this.readConfigs = readConfigs;

        Set<StructuredTypeInfo> writeConfigs = new LinkedHashSet<>();
        writeConfigs.add(CONFIG_BEGIN_EXT);
        writeConfigs.add(CONFIG_DATA_EXT);
        writeConfigs.add(CONFIG_DATA_EMPTY);
        writeConfigs.add(CONFIG_ABORT_EXT);
        writeConfigs.add(CONFIG_END_EXT);
        writeConfigs.add(CONFIG_RESET_EXT);
        this.writeConfigs = writeConfigs;

        Set<StructuredTypeInfo> readAdvisories = new LinkedHashSet<>();
        readAdvisories.add(ADVISORY_CHALLENGE);
        this.readAdvisories = readAdvisories;

        Set<StructuredTypeInfo> writeAdvisories = new LinkedHashSet<>();
        writeAdvisories.add(ADVISORY_FLUSH);
        this.writeAdvisories = writeAdvisories;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Set<TypeInfo<?>> acceptOptions()
    {
        return acceptOptions;
    }

    @Override
    public Set<TypeInfo<?>> connectOptions()
    {
        return connectOptions;
    }

    @Override
    public Set<TypeInfo<?>> readOptions()
    {
        return readOptions;
    }

    @Override
    public Set<TypeInfo<?>> writeOptions()
    {
        return writeOptions;
    }

    @Override
    public Set<StructuredTypeInfo> readConfigs()
    {
        return readConfigs;
    }

    @Override
    public Set<StructuredTypeInfo> writeConfigs()
    {
        return writeConfigs;
    }

    @Override
    public Set<StructuredTypeInfo> readAdvisories()
    {
        return readAdvisories;
    }

    @Override
    public Set<StructuredTypeInfo> writeAdvisories()
    {
        return writeAdvisories;
    }
}
