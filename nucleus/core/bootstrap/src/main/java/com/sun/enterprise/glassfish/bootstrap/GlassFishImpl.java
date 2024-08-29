/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.enterprise.glassfish.bootstrap;

import com.sun.enterprise.module.bootstrap.ModuleStartup;

import java.util.Properties;

import org.glassfish.embeddable.CommandRunner;
import org.glassfish.embeddable.Deployer;
import org.glassfish.embeddable.GlassFish;
import org.glassfish.embeddable.GlassFishException;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.extras.ExtrasUtilities;

/**
 * @author Sanjeeb.Sahoo@Sun.COM
 */

public class GlassFishImpl implements GlassFish {

    private ModuleStartup gfKernel;
    private ServiceLocator habitat;
    volatile Status status = Status.INIT;

    public GlassFishImpl(ModuleStartup gfKernel, ServiceLocator habitat, Properties gfProps) throws GlassFishException {
        this.gfKernel = gfKernel;
        this.habitat = habitat;
        /*
            We enable a temporary distribution service until the HK2 Extras package is fixed so that we can enable
            the topic distribution service provided by HK2.
        */
        ExtrasUtilities.enableTopicDistribution(habitat);

        configure(gfProps);
    }

    private void configure(Properties gfProps) throws GlassFishException {
        // If there are custom configurations like http.port, https.port, jmx.port then configure them.
        Configurator configurator = new Configurator(habitat);
        configurator.configure(gfProps);
    }

    @Override
    public synchronized void start() throws GlassFishException {
        if (status == Status.STARTED || status == Status.STARTING || status == Status.DISPOSED) {
            throw new IllegalStateException("Already in " + status + " state.");
        }
        status = Status.STARTING;
        gfKernel.start();
        status = Status.STARTED;
    }

    @Override
    public synchronized void stop() throws GlassFishException {
        if (status == Status.STOPPED || status == Status.STOPPING || status == Status.DISPOSED) {
            throw new IllegalStateException("Already in " + status + " state.");
        }
        status = Status.STOPPING;
        gfKernel.stop();
        status = Status.STOPPED;
    }

    @Override
    public synchronized void dispose() throws GlassFishException {
        if (status == Status.DISPOSED) {
            throw new IllegalStateException("Already disposed.");
        } else if (status != Status.STOPPED) {
            try {
                stop();
            } catch (Exception e) {
                // ignore and continue.
                e.printStackTrace();
            }
        }
        this.gfKernel = null;
        this.habitat = null;
        this.status = Status.DISPOSED;
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public <T> T getService(Class<T> serviceType) throws GlassFishException {
        return getService(serviceType, null);
    }

    @Override
    public synchronized <T> T getService(Class<T> serviceType, String serviceName) throws GlassFishException {
        if (status != Status.STARTED) {
            throw new IllegalArgumentException("Server is not started yet. It is in " + status + "state");
        }

        return serviceName != null ? habitat.<T>getService(serviceType, serviceName) :
                habitat.<T>getService(serviceType);
    }

    @Override
    public Deployer getDeployer() throws GlassFishException {
        return getService(Deployer.class);
    }

    @Override
    public CommandRunner getCommandRunner() throws GlassFishException {
        return getService(CommandRunner.class);
    }

}
