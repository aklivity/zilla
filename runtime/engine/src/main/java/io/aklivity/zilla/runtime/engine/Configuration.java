/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

public class Configuration
{
    public static final class ConfigurationDef
    {
        private final String format;
        private final Map<String, PropertyDef<?>> properties;

        public ConfigurationDef(
            String prefix)
        {
            this.format = String.format("%s.%%s", prefix);
            this.properties = new TreeMap<>();
        }

        public Collection<PropertyDef<?>> properties()
        {
            return properties.values();
        }

        public BooleanPropertyDef property(
            String name,
            boolean defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            BooleanPropertyDef property = new BooleanPropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public BooleanPropertyDef property(
            String name,
            Predicate<Configuration> defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            BooleanPropertyDef property = new BooleanPropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public BytePropertyDef property(
            String name,
            byte defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            BytePropertyDef property = new BytePropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public BytePropertyDef property(
            String name,
            ToByteFunction<Configuration> defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            BytePropertyDef property = new BytePropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public ShortPropertyDef property(
            String name,
            short defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            ShortPropertyDef property = new ShortPropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public ShortPropertyDef property(
            String name,
            ToShortFunction<Configuration> defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            ShortPropertyDef property = new ShortPropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public IntPropertyDef property(
            String name,
            int defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            IntPropertyDef property = new IntPropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public IntPropertyDef property(
            String name,
            ToIntFunction<Configuration> defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            IntPropertyDef property = new IntPropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public LongPropertyDef property(
            String name,
            long defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            LongPropertyDef property = new LongPropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public LongPropertyDef property(
            String name,
            ToLongFunction<Configuration> defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            LongPropertyDef property = new LongPropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public FloatPropertyDef property(
            String name,
            float defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            FloatPropertyDef property = new FloatPropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public FloatPropertyDef property(
            String name,
            ToFloatFunction<Configuration> defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            FloatPropertyDef property = new FloatPropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public DoublePropertyDef property(
            String name,
            double defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            DoublePropertyDef property = new DoublePropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public DoublePropertyDef property(
            String name,
            ToDoubleFunction<Configuration> defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            DoublePropertyDef property = new DoublePropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public CharPropertyDef property(
            String name,
            char defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            CharPropertyDef property = new CharPropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public CharPropertyDef property(
            String name,
            ToCharFunction<Configuration> defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            CharPropertyDef property = new CharPropertyDef(qualifiedName, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public PropertyDef<String> property(
            String name)
        {
            return property(String.class, name, identity(), c -> null);
        }

        public PropertyDef<String> property(
            String name,
            String defaultValue)
        {
            return property(String.class, name, identity(), c -> defaultValue);
        }

        public PropertyDef<String> property(
            String name,
            Function<Configuration, String> defaultValue)
        {
            return property(String.class, name, identity(), defaultValue);
        }

        public <T> PropertyDef<T> property(
            Class<T> kind,
            String name,
            Function<String, T> decodeValue,
            Function<Configuration, T> defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            PropertyDef<T> property = new ObjectPropertyDef<T>(kind, qualifiedName, decodeValue, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public <T> PropertyDef<T> property(
            Class<T> kind,
            String name,
            BiFunction<Configuration, String, T> decodeValue,
            Function<Configuration, T> defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            PropertyDef<T> property = new ObjectPropertyDef<T>(kind, qualifiedName, decodeValue, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        public <T> PropertyDef<T> property(
            Class<T> kind,
            String name,
            BiFunction<Configuration, String, T> decodeValue,
            String defaultValue)
        {
            String qualifiedName = qualifiedName(name);
            PropertyDef<T> property = new ObjectPropertyDef<T>(kind, qualifiedName, decodeValue, defaultValue);
            properties.put(qualifiedName, property);
            return property;
        }

        private String qualifiedName(
            String name)
        {
            String qualifiedName = String.format(format, name);
            if (properties.containsKey(qualifiedName))
            {
                throw new IllegalArgumentException(String.format("duplicate property name: %s", name));
            }
            return qualifiedName;
        }
    }

    public abstract static class PropertyDef<T>
    {
        protected final Class<T> kind;
        protected final String name;

        public PropertyDef(
            Class<T> kind,
            String name)
        {
            this.kind = kind;
            this.name = name;
        }

        public final Class<T> kind()
        {
            return kind;
        }

        public final String name()
        {
            return name;
        }

        public final T get(
            Configuration config)
        {
            T value = value(config);
            if (value == null)
            {
                value = defaultValue(config);
            }
            return value;
        }

        public abstract T value(
            Configuration config);

        public abstract T defaultValue(
            Configuration config);
    }

    public static final class BooleanPropertyDef extends PropertyDef<Boolean>
    {
        private final Predicate<Configuration> defaultValue;

        private BooleanPropertyDef(
            String name,
            boolean defaultValue)
        {
            this(name, c -> defaultValue);
        }

        private BooleanPropertyDef(
            String name,
            Predicate<Configuration> defaultValue)
        {
            super(Boolean.class, name);
            this.defaultValue = defaultValue;
        }

        public boolean getAsBoolean(
            Configuration config)
        {
            return get(config);
        }

        @Override
        public Boolean value(
            Configuration config)
        {
            String value = config.getProperty.apply(name, null);
            return value != null ? Boolean.parseBoolean(value) : null;
        }

        @Override
        public Boolean defaultValue(
            Configuration config)
        {
            String value = config.getPropertyDefault.apply(name, null);
            return value != null ? Boolean.parseBoolean(value) : defaultValue.test(config);
        }
    }

    public static final class BytePropertyDef extends PropertyDef<Byte>
    {
        private final ToByteFunction<Configuration> defaultValue;

        private BytePropertyDef(
            String name,
            byte defaultValue)
        {
            this(name, c -> defaultValue);
        }

        private BytePropertyDef(
            String name,
            ToByteFunction<Configuration> defaultValue)
        {
            super(Byte.class, name);
            this.defaultValue = defaultValue;
        }

        public byte getAsByte(
            Configuration config)
        {
            return get(config);
        }

        @Override
        public Byte value(
            Configuration config)
        {
            String value = config.getProperty.apply(name, null);
            return value != null ? Byte.decode(value) : null;
        }

        @Override
        public Byte defaultValue(
            Configuration config)
        {
            String value = config.getPropertyDefault.apply(name, null);
            return value != null ? Byte.decode(value) : defaultValue.applyAsByte(config);
        }
    }

    public static final class ShortPropertyDef extends PropertyDef<Short>
    {
        private final ToShortFunction<Configuration> defaultValue;

        private ShortPropertyDef(
            String name,
            short defaultValue)
        {
            this(name, c -> defaultValue);
        }

        private ShortPropertyDef(
            String name,
            ToShortFunction<Configuration> defaultValue)
        {
            super(Short.class, name);
            this.defaultValue = defaultValue;
        }

        public short getAsShort(
            Configuration config)
        {
            return get(config);
        }

        @Override
        public Short value(
            Configuration config)
        {
            String value = config.getProperty.apply(name, null);
            return value != null ? Short.decode(value) : null;
        }

        @Override
        public Short defaultValue(
            Configuration config)
        {
            String value = config.getPropertyDefault.apply(name, null);
            return value != null ? Short.decode(value) : defaultValue.applyAsShort(config);
        }
    }

    public static final class IntPropertyDef extends PropertyDef<Integer>
    {
        private final ToIntFunction<Configuration> defaultValue;

        private IntPropertyDef(
            String name,
            int defaultValue)
        {
            this(name, c -> defaultValue);
        }

        private IntPropertyDef(
            String name,
            ToIntFunction<Configuration> defaultValue)
        {
            super(Integer.class, name);
            this.defaultValue = defaultValue;
        }

        public int getAsInt(
            Configuration config)
        {
            return get(config);
        }

        @Override
        public Integer value(
            Configuration config)
        {
            String value = config.getProperty.apply(name, null);
            return value != null ? Integer.decode(value) : null;
        }

        @Override
        public Integer defaultValue(
            Configuration config)
        {
            String value = config.getPropertyDefault.apply(name, null);
            return value != null ? Integer.decode(value) : defaultValue.applyAsInt(config);
        }
    }

    public static final class LongPropertyDef extends PropertyDef<Long>
    {
        private final ToLongFunction<Configuration> defaultValue;

        private LongPropertyDef(
            String name,
            long defaultValue)
        {
            this(name, c -> defaultValue);
        }

        private LongPropertyDef(
            String name,
            ToLongFunction<Configuration> defaultValue)
        {
            super(Long.class, name);
            this.defaultValue = defaultValue;
        }

        public long getAsLong(
            Configuration config)
        {
            return get(config);
        }

        @Override
        public Long value(
            Configuration config)
        {
            String value = config.getProperty.apply(name, null);
            return value != null ? Long.decode(value) : null;
        }

        @Override
        public Long defaultValue(
            Configuration config)
        {
            String value = config.getProperty.apply(name, null);
            return value != null ? Long.decode(value) : defaultValue.applyAsLong(config);
        }
    }

    public static final class FloatPropertyDef extends PropertyDef<Float>
    {
        private final ToFloatFunction<Configuration> defaultValue;

        private FloatPropertyDef(
            String name,
            float defaultValue)
        {
            this(name, c -> defaultValue);
        }

        private FloatPropertyDef(
            String name,
            ToFloatFunction<Configuration> defaultValue)
        {
            super(Float.class, name);
            this.defaultValue = defaultValue;
        }

        public float getAsFloat(
            Configuration config)
        {
            return get(config);
        }

        @Override
        public Float value(
            Configuration config)
        {
            String value = config.getProperty.apply(name, null);
            return value != null ? Float.parseFloat(value) : null;
        }

        @Override
        public Float defaultValue(
            Configuration config)
        {
            String value = config.getProperty.apply(name, null);
            return value != null ? Float.parseFloat(value) : defaultValue.applyAsFloat(config);
        }
    }

    public static final class DoublePropertyDef extends PropertyDef<Double>
    {
        private final ToDoubleFunction<Configuration> defaultValue;

        private DoublePropertyDef(
            String name,
            double defaultValue)
        {
            this(name, c -> defaultValue);
        }

        private DoublePropertyDef(
            String name,
            ToDoubleFunction<Configuration> defaultValue)
        {
            super(Double.class, name);
            this.defaultValue = defaultValue;
        }

        public double getAsDouble(
            Configuration config)
        {
            return get(config);
        }

        @Override
        public Double value(
            Configuration config)
        {
            String value = config.getProperty.apply(name, null);
            return value != null ? Double.parseDouble(value) : null;
        }

        @Override
        public Double defaultValue(
            Configuration config)
        {
            String value = config.getProperty.apply(name, null);
            return value != null ? Double.parseDouble(value) : defaultValue.applyAsDouble(config);
        }
    }

    public static final class CharPropertyDef extends PropertyDef<Character>
    {
        private final ToCharFunction<Configuration> defaultValue;

        private CharPropertyDef(
            String name,
            char defaultValue)
        {
            this(name, c -> defaultValue);
        }

        private CharPropertyDef(
            String name,
            ToCharFunction<Configuration> defaultValue)
        {
            super(Character.class, name);
            this.defaultValue = defaultValue;
        }

        public char getAsChar(
            Configuration config)
        {
            return get(config);
        }

        @Override
        public Character value(
            Configuration config)
        {
            String value = config.getProperty.apply(name, null);
            return value != null ? parseCharacter(value) : null;
        }

        @Override
        public Character defaultValue(
            Configuration config)
        {
            String value = config.getProperty.apply(name, null);
            return value != null ? parseCharacter(value) : defaultValue.applyAsChar(config);
        }

        private static Character parseCharacter(
            String value)
        {
            if (value.length() != 1)
            {
                throw new IllegalArgumentException(String.format("invalid char: %s", value));
            }

            return value.charAt(0);
        }
    }

    private static final class ObjectPropertyDef<T> extends PropertyDef<T>
    {
        private final BiFunction<Configuration, String, T> decodeValue;
        private final Function<Configuration, T> defaultValue;

        private ObjectPropertyDef(
            Class<T> kind,
            String name,
            Function<String, T> decodeValue,
            T defaultValue)
        {
            this(kind, name, decodeValue, c -> defaultValue);
        }

        private ObjectPropertyDef(
            Class<T> kind,
            String name,
            Function<String, T> decodeValue,
            Function<Configuration, T> defaultValue)
        {
            super(kind, name);
            this.decodeValue = (c, v) -> decodeValue.apply(v);
            this.defaultValue = defaultValue;
        }

        private ObjectPropertyDef(
            Class<T> kind,
            String name,
            BiFunction<Configuration, String, T> decodeValue,
            Function<Configuration, T> defaultValue)
        {
            super(kind, name);
            this.decodeValue = decodeValue;
            this.defaultValue = defaultValue;
        }

        private ObjectPropertyDef(
            Class<T> kind,
            String name,
            BiFunction<Configuration, String, T> decodeValue,
            String defaultValue)
        {
            super(kind, name);
            this.decodeValue = decodeValue;
            this.defaultValue = c -> decodeValue.apply(c, defaultValue);
        }

        @Override
        public T value(
            Configuration config)
        {
            String value = config.getProperty.apply(name, null);
            return value != null ? decodeValue.apply(config, value) : null;
        }

        @Override
        public T defaultValue(
            Configuration config)
        {
            String value = config.getPropertyDefault.apply(name, null);
            return value != null ? decodeValue.apply(config, value) : defaultValue.apply(config);
        }
    }

    private static final ConfigurationDef EMPTY_DEFINITION = new ConfigurationDef("");

    private final ConfigurationDef definition;
    private final BiFunction<String, String, String> getProperty;
    private final BiFunction<String, String, String> getPropertyDefault;

    public Configuration()
    {
        this.definition = EMPTY_DEFINITION;
        this.getProperty = System::getProperty;
        this.getPropertyDefault = (p, d) -> d;
    }

    public Configuration(
        Properties properties)
    {
        requireNonNull(properties, "properties");
        this.definition = EMPTY_DEFINITION;
        this.getProperty = properties::getProperty;
        this.getPropertyDefault = (p, d) -> d;
    }

    protected Configuration(
        Configuration config)
    {
        this(EMPTY_DEFINITION, config);
    }

    protected Configuration(
        ConfigurationDef definition,
        Configuration config)
    {
        this.definition = definition;
        this.getProperty = config.getProperty;
        this.getPropertyDefault = config.getPropertyDefault;
    }

    protected Configuration(
        ConfigurationDef definition,
        Properties properties)
    {
        requireNonNull(properties, "properties");
        this.definition = definition;
        this.getProperty = properties::getProperty;
        this.getPropertyDefault = (p, d) -> d;
    }

    protected Configuration(
        Configuration config,
        Properties defaultOverrides)
    {
        this(EMPTY_DEFINITION, config, defaultOverrides);
    }

    protected Configuration(
        ConfigurationDef definition,
        Configuration config,
        Properties defaultOverrides)
    {
        this.definition = definition;
        this.getProperty = config.getProperty;
        this.getPropertyDefault = defaultOverrides::getProperty;
    }

    @Deprecated
    public Path directory()
    {
        return Paths.get(getProperty("zilla.engine.directory", "."));
    }

    public void properties(
        BiConsumer<String, Object> valueAction,
        BiConsumer<String, Object> defaultAction)
    {
        for (PropertyDef<?> key : definition.properties.values())
        {
            Object value = key.value(this);
            if (value != null)
            {
                valueAction.accept(key.name, value);
            }
            else
            {
                Object defaultValue = key.defaultValue(this);
                defaultAction.accept(key.name, defaultValue);
            }
        }
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        properties((s, o) -> sb.append(String.format("%s = %s (override)\n", s, o)),
            (s, o) -> sb.append(String.format("%s = %s (default)\n", s, o)));
        return sb.toString();
    }

    String getProperty(
        String key,
        String defaultValue)
    {
        return getProperty.apply(key, getPropertyDefault.apply(key, defaultValue));
    }

    @FunctionalInterface
    interface ToByteFunction<T>
    {
        byte applyAsByte(T value);
    }

    @FunctionalInterface
    interface ToShortFunction<T>
    {
        short applyAsShort(T value);
    }

    @FunctionalInterface
    interface ToFloatFunction<T>
    {
        float applyAsFloat(T value);
    }

    @FunctionalInterface
    interface ToCharFunction<T>
    {
        char applyAsChar(T value);
    }
}
