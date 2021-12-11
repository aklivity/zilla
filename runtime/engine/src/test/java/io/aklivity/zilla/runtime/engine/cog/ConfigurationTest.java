/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.cog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.cog.Configuration.BooleanPropertyDef;
import io.aklivity.zilla.runtime.engine.cog.Configuration.BytePropertyDef;
import io.aklivity.zilla.runtime.engine.cog.Configuration.CharPropertyDef;
import io.aklivity.zilla.runtime.engine.cog.Configuration.ConfigurationDef;
import io.aklivity.zilla.runtime.engine.cog.Configuration.DoublePropertyDef;
import io.aklivity.zilla.runtime.engine.cog.Configuration.FloatPropertyDef;
import io.aklivity.zilla.runtime.engine.cog.Configuration.IntPropertyDef;
import io.aklivity.zilla.runtime.engine.cog.Configuration.LongPropertyDef;
import io.aklivity.zilla.runtime.engine.cog.Configuration.PropertyDef;
import io.aklivity.zilla.runtime.engine.cog.Configuration.ShortPropertyDef;
import io.aklivity.zilla.runtime.engine.cog.Configuration.ToByteFunction;
import io.aklivity.zilla.runtime.engine.cog.Configuration.ToCharFunction;
import io.aklivity.zilla.runtime.engine.cog.Configuration.ToFloatFunction;
import io.aklivity.zilla.runtime.engine.cog.Configuration.ToShortFunction;

public final class ConfigurationTest
{
    @Test
    public void shouldUseSystemProperties()
    {
        System.setProperty("zilla.test", "/path/to/zilla");

        Configuration config = new Configuration();

        assertEquals("/path/to/zilla", config.getProperty("zilla.test", "default"));
    }

    @Test
    public void shouldUseProperties()
    {
        Properties properties = new Properties();
        properties.setProperty("zilla.test", "/path/to/zilla");

        Configuration config = new Configuration(properties);

        assertEquals("/path/to/zilla", config.getProperty("zilla.test", "default"));
    }

    @Test
    public void shouldUseDefaultOverridesProperties()
    {
        Properties defaultOverrides = new Properties();
        defaultOverrides.setProperty("zilla.test", "/path/to/zilla");

        Configuration system = new Configuration();
        Configuration config = new Configuration(system, defaultOverrides);

        assertEquals("/path/to/zilla", config.getProperty("zilla.test", "default"));
    }

    @Test
    public void shouldUseSystemPropertiesBeforeDefaultProperties()
    {
        System.setProperty("zilla.test", "/system/path/to/zilla");

        Properties defaults = new Properties();
        defaults.setProperty("zilla.test", "/path/to/zilla");

        Configuration system = new Configuration();
        Configuration config = new Configuration(system, defaults);

        assertEquals("/system/path/to/zilla", config.getProperty("zilla.test", "default"));
    }

    @Test
    public void shouldUseDefaultProperties()
    {
        System.setProperty("zilla.test.default", Integer.toString(1048576));

        Properties defaults = new Properties();
        defaults.setProperty("zilla.test.default", Integer.toString(65536));

        Configuration system = new Configuration();
        Configuration config = new Configuration(system, defaults);

        assertEquals("default", config.getProperty("zilla.test", "default"));
    }

    @Test
    public void shouldUseDefaultOverridesPropertiesWhenWrapped()
    {
        Properties defaultOverrides = new Properties();
        defaultOverrides.setProperty("zilla.test", "/path/to/zilla");

        Configuration system = new Configuration();
        Configuration config = new Configuration(system, defaultOverrides);
        Configuration wrapped = new Configuration(config);

        assertEquals("/path/to/zilla", wrapped.getProperty("zilla.test", "default"));
    }

    @Test
    public void shouldGetProperty()
    {
        Properties properties = new Properties();
        properties.setProperty("scope.property.name", "value");

        ConfigurationDef configDef = new ConfigurationDef("scope");
        PropertyDef<String> propertyDef = configDef.property("property.name", "default");
        Configuration config = new Configuration(properties);

        assertEquals("value", propertyDef.get(config));
    }

    @Test
    public void shouldDefaultGetProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        PropertyDef<String> propertyDef = configDef.property("property.name", "default");
        Configuration config = new Configuration();

        assertEquals("default", propertyDef.get(config));
    }

    @Test
    public void shouldSupplyDefaultGetProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        PropertyDef<String> propertyDef = configDef.property(String.class, "property.name", (c, v) -> v.toUpperCase(), "default");
        Configuration config = new Configuration();

        assertEquals("DEFAULT", propertyDef.get(config));
    }

    @Test
    public void shouldGetBooleanProperty()
    {
        System.setProperty("scope.boolean.property.name", Boolean.TRUE.toString());

        ConfigurationDef configDef = new ConfigurationDef("scope");
        BooleanPropertyDef propertyDef = configDef.property("boolean.property.name", false);
        Configuration config = new Configuration();

        assertTrue(propertyDef.getAsBoolean(config));
    }

    @Test
    public void shouldDefaultBooleanProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        BooleanPropertyDef propertyDef = configDef.property("boolean.property.name", true);
        Configuration config = new Configuration();

        assertTrue(propertyDef.getAsBoolean(config));
    }

    @Test
    public void shouldSupplyDefaultBooleanProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        BooleanPropertyDef propertyDef = configDef.property("boolean.property.name", (Predicate<Configuration>) c -> true);
        Configuration config = new Configuration();

        assertTrue(propertyDef.getAsBoolean(config));
    }

    @Test
    public void shouldGetByteProperty()
    {
        System.setProperty("scope.byte.property.name", "0x7f");

        ConfigurationDef configDef = new ConfigurationDef("scope");
        BytePropertyDef propertyDef = configDef.property("byte.property.name", Byte.decode("0x00"));
        Configuration config = new Configuration();

        assertEquals(Byte.MAX_VALUE, propertyDef.getAsByte(config));
    }

    @Test
    public void shouldDefaultByteProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        BytePropertyDef propertyDef = configDef.property("byte.property.name", Byte.decode("0x7f"));
        Configuration config = new Configuration();

        assertEquals(Byte.MAX_VALUE, propertyDef.getAsByte(config));
    }

    @Test
    public void shouldSupplyDefaultByteProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        BytePropertyDef propertyDef = configDef.property("byte.property.name",
                (ToByteFunction<Configuration>) c -> Byte.decode("0x7f"));
        Configuration config = new Configuration();

        assertEquals(Byte.MAX_VALUE, propertyDef.getAsByte(config));
    }

    @Test
    public void shouldGetShortProperty()
    {
        System.setProperty("scope.short.property.name", "0x7fff");

        ConfigurationDef configDef = new ConfigurationDef("scope");
        ShortPropertyDef propertyDef = configDef.property("short.property.name", Short.decode("0x00"));
        Configuration config = new Configuration();

        assertEquals(Short.MAX_VALUE, propertyDef.getAsShort(config));
    }

    @Test
    public void shouldDefaultShortProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        ShortPropertyDef propertyDef = configDef.property("short.property.name", Short.decode("0x7fff"));
        Configuration config = new Configuration();

        assertEquals(Short.MAX_VALUE, propertyDef.getAsShort(config));
    }

    @Test
    public void shouldSupplyDefaultShortProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        ShortPropertyDef propertyDef = configDef.property("short.property.name",
                (ToShortFunction<Configuration>) c -> Short.decode("0x7fff"));
        Configuration config = new Configuration();

        assertEquals(Short.MAX_VALUE, propertyDef.getAsShort(config));
    }

    @Test
    public void shouldGetIntegerProperty()
    {
        System.setProperty("scope.integer.property.name", Integer.toString(1234));

        ConfigurationDef configDef = new ConfigurationDef("scope");
        IntPropertyDef propertyDef = configDef.property("integer.property.name", 5678);
        Configuration config = new Configuration();

        assertEquals(1234, propertyDef.getAsInt(config));
    }

    @Test
    public void shouldDefaultIntegerProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        IntPropertyDef propertyDef = configDef.property("integer.property.name", 1234);
        Configuration config = new Configuration();

        assertEquals(1234, propertyDef.getAsInt(config));
    }

    @Test
    public void shouldSupplyDefaultIntegerProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        IntPropertyDef propertyDef = configDef.property("integer.property.name", (ToIntFunction<Configuration>) c -> 1234);
        Configuration config = new Configuration();

        assertEquals(1234, propertyDef.getAsInt(config));
    }

    @Test
    public void shouldGetLongProperty()
    {
        System.setProperty("scope.long.property.name", Long.toString(1234L));

        ConfigurationDef configDef = new ConfigurationDef("scope");
        LongPropertyDef propertyDef = configDef.property("long.property.name", 5678L);
        Configuration config = new Configuration();

        assertEquals(1234L, propertyDef.getAsLong(config));
    }

    @Test
    public void shouldDefaultLongProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        LongPropertyDef propertyDef = configDef.property("long.property.name", 1234L);
        Configuration config = new Configuration();

        assertEquals(1234L, propertyDef.getAsLong(config));
    }

    @Test
    public void shouldSupplyDefaultLongProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        LongPropertyDef propertyDef = configDef.property("long.property.name", (ToLongFunction<Configuration>) c -> 1234L);
        Configuration config = new Configuration();

        assertEquals(1234L, propertyDef.getAsLong(config));
    }

    @Test
    public void shouldGetCharProperty()
    {
        System.setProperty("scope.char.property.name", "a");

        ConfigurationDef configDef = new ConfigurationDef("scope");
        CharPropertyDef propertyDef = configDef.property("char.property.name", 'z');
        Configuration config = new Configuration();

        assertEquals('a', propertyDef.getAsChar(config));
    }

    @Test
    public void shouldDefaultCharProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        CharPropertyDef propertyDef = configDef.property("char.property.name", 'a');
        Configuration config = new Configuration();

        assertEquals('a', propertyDef.getAsChar(config));
    }

    @Test
    public void shouldSupplyDefaultCharProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        CharPropertyDef propertyDef = configDef.property("char.property.name", (ToCharFunction<Configuration>) c -> 'a');
        Configuration config = new Configuration();

        assertEquals('a', propertyDef.getAsChar(config));
    }

    @Test
    public void shouldGetFloatProperty()
    {
        System.setProperty("scope.float.property.name", Float.toString(0.1234f));

        ConfigurationDef configDef = new ConfigurationDef("scope");
        FloatPropertyDef propertyDef = configDef.property("float.property.name", 0.5678f);
        Configuration config = new Configuration();

        assertEquals(0.1234f, propertyDef.getAsFloat(config), 0.0f);
    }

    @Test
    public void shouldDefaultFloatProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        FloatPropertyDef propertyDef = configDef.property("float.property.name", 0.1234f);
        Configuration config = new Configuration();

        assertEquals(0.1234f, propertyDef.getAsFloat(config), 0.0f);
    }

    @Test
    public void shouldSupplyDefaultFloatProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        FloatPropertyDef propertyDef = configDef.property("float.property.name", (ToFloatFunction<Configuration>) c -> 0.1234f);
        Configuration config = new Configuration();

        assertEquals(0.1234f, propertyDef.getAsFloat(config), 0.0f);
    }

    @Test
    public void shouldGetDoubleProperty()
    {
        System.setProperty("scope.double.property.name", Double.toString(0.1234));

        ConfigurationDef configDef = new ConfigurationDef("scope");
        DoublePropertyDef propertyDef = configDef.property("double.property.name", 0.5678);
        Configuration config = new Configuration();

        assertEquals(0.1234, propertyDef.getAsDouble(config), 0.0);
    }

    @Test
    public void shouldDefaultDoubleProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        DoublePropertyDef propertyDef = configDef.property("double.property.name", 0.1234);
        Configuration config = new Configuration();

        assertEquals(0.1234, propertyDef.getAsDouble(config), 0.0);
    }

    @Test
    public void shouldSupplyDefaultDoubleProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        DoublePropertyDef propertyDef = configDef.property("double.property.name", (ToDoubleFunction<Configuration>) c -> 0.1234);
        Configuration config = new Configuration();

        assertEquals(0.1234, propertyDef.getAsDouble(config), 0.0);
    }
}
