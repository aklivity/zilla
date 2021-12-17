/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivy.util.url;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.PasswordAuthentication;

import org.apache.ivy.util.Credentials;
import org.apache.ivy.util.Message;

import static org.apache.ivy.util.StringUtils.isNullOrEmpty;

/**
 *
 */
public final class IvyAuthenticator extends Authenticator {

    private Authenticator original;

    private static boolean securityWarningLogged = false;

    /**
     * Private c'tor to prevent instantiation.
     */
    private IvyAuthenticator(Authenticator original) {
        this.original = original;
    }

    /**
     * Installs an <tt>IvyAuthenticator</tt> as default <tt>Authenticator</tt>. Call this method
     * before opening HTTP(S) connections to enable Ivy authentication.
     */
    public static void install() {
        // We will try to use the original authenticator as backup authenticator.
        // Since there is no getter available, so try to use some reflection to
        // obtain it. If that doesn't work, assume there is no original authenticator
        Authenticator original = getCurrentAuthenticator();

        if (original instanceof IvyAuthenticator) {
            return;
        }

        try {
            Authenticator.setDefault(new IvyAuthenticator(original));
        } catch (SecurityException e) {
            if (!securityWarningLogged) {
                securityWarningLogged = true;
                Message.warn("Not enough permissions to set the IvyAuthenticator. "
                        + "HTTP(S) authentication will be disabled!");
            }
        }
    }

    // API ******************************************************************

    // Overriding Authenticator *********************************************

    protected PasswordAuthentication getPasswordAuthentication() {
        PasswordAuthentication result = null;

        if (isProxyAuthentication()) {
            String proxyUser = System.getProperty("http.proxyUser");
            if (!isNullOrEmpty(proxyUser)) {
                String proxyPass = System.getProperty("http.proxyPassword", "");
                Message.debug("authenticating to proxy server with username [" + proxyUser + "]");
                result = new PasswordAuthentication(proxyUser, proxyPass.toCharArray());
            }
        } else {
            Credentials c = CredentialsStore.INSTANCE.getCredentials(getRequestingPrompt(),
                getRequestingHost());
            Message.debug("authentication: k='"
                    + Credentials.buildKey(getRequestingPrompt(), getRequestingHost()) + "' c='"
                    + c + "'");
            if (c != null) {
                final String password = c.getPasswd() == null ? "" : c.getPasswd();
                result = new PasswordAuthentication(c.getUserName(), password.toCharArray());
            }
        }

        if (result == null) {
            String userInfo = getRequestingURL().getUserInfo();
            if (userInfo != null) {
                String username = userInfo;
                String password = null;

                int colonAt = userInfo.indexOf(':');
                if (colonAt != -1) {
                    username = userInfo.substring(0, colonAt);
                    password = userInfo.substring(colonAt + 1);
                    result = new PasswordAuthentication(username, password.toCharArray());
                }
            }
        }

        if (result == null && original != null) {
            Authenticator.setDefault(original);
            try {
                result = Authenticator.requestPasswordAuthentication(getRequestingHost(),
                    getRequestingSite(), getRequestingPort(), getRequestingProtocol(),
                    getRequestingPrompt(), getRequestingScheme(), getRequestingURL(), getRequestorType());
            } finally {
                Authenticator.setDefault(this);
            }
        }

        return result;
    }

    /**
     * The {@link Authenticator} doesn't have API before Java 9 to get hold of the current system
     * level {@link Authenticator}. This method does a best-effort attempt to try and get hold of
     * the current {@link Authenticator} in a way that's specific to the implementation of this
     * method.  There's no guarantee that this method will return the current authenticator.
     * <strong>Note: this method is intended to be used exclusively by tests.</strong>
     *
     * @return Returns the currently setup system level {@link Authenticator}. In cases where this
     * method isn't able to get the current authenticator, this method returns null
     */
    static Authenticator getCurrentAuthenticator() {
        return (getJavaVersion() < 9) ? getTheAuthenticator() : getDefaultAuthenticator();
    }

    /**
     * Checks if the current authentication request is for the proxy server.
     */
    private boolean isProxyAuthentication() {
        return RequestorType.PROXY.equals(getRequestorType());
    }

    private static Authenticator getDefaultAuthenticator() {
        try {
            final Method m = Authenticator.class.getDeclaredMethod("getDefault");
            return (Authenticator) m.invoke(null);
        } catch (final Throwable t) {
            handleReflectionException(t);
        }
        return null;
    }

    private static Authenticator getTheAuthenticator() {
        try {
            Field f = Authenticator.class.getDeclaredField("theAuthenticator");
            f.setAccessible(true);
            return (Authenticator) f.get(null);
        } catch (final Throwable t) {
            handleReflectionException(t);
        }
        return null;
    }

    private static void handleReflectionException(final Throwable t) {
        Message.debug("Error occurred while getting the original authenticator: "
                + t.getMessage());
    }

    private static int getJavaVersion() {
        // See https://docs.oracle.com/javase/8/docs/technotes/guides/versioning/spec/versioning2.html#wp90002
        final String[] version = System.getProperty("java.specification.version").split("\\.");
        final int major = Integer.parseInt(version[0]);
        return major == 1 ? Integer.parseInt(version[1]) : major;
    }
}

