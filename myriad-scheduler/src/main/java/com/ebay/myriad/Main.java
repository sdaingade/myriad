/**
 * Copyright 2015 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ebay.myriad;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.ebay.myriad.configuration.MyriadConfiguration;
import com.ebay.myriad.health.MesosDriverHealthCheck;
import com.ebay.myriad.health.MesosMasterHealthCheck;
import com.ebay.myriad.health.ZookeeperHealthCheck;
import com.ebay.myriad.scheduler.MyriadDriverManager;
import com.ebay.myriad.scheduler.MyriadOperations;
import com.ebay.myriad.scheduler.NMProfile;
import com.ebay.myriad.scheduler.NMProfileManager;
import com.ebay.myriad.scheduler.Rebalancer;
import com.ebay.myriad.scheduler.TaskTerminator;
import com.ebay.myriad.scheduler.yarn.interceptor.InterceptorRegistry;
import com.ebay.myriad.webapp.MyriadWebServer;
import com.ebay.myriad.webapp.WebAppGuiceModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.commons.collections.MapUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.server.resourcemanager.RMContext;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.AbstractYarnScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main entry point for myriad scheduler
 *
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private MyriadWebServer webServer;
    private ScheduledExecutorService terminatorService;

    private ScheduledExecutorService rebalancerService;
    private HealthCheckRegistry healthCheckRegistry;

    private static Injector injector;

    public static void initialize(Configuration hadoopConf,
                                  AbstractYarnScheduler yarnScheduler,
                                  RMContext rmContext,
                                  InterceptorRegistry registry) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        MyriadConfiguration cfg = mapper.readValue(
                Thread.currentThread().getContextClassLoader().getResource("myriad-config-default.yml"),
                MyriadConfiguration.class);

        MyriadModule myriadModule = new MyriadModule(cfg, hadoopConf, yarnScheduler, rmContext, registry);
        MesosModule mesosModule = new MesosModule();
        injector = Guice.createInjector(
                myriadModule, mesosModule,
                new WebAppGuiceModule());

        new Main().run(cfg);
    }

    // TODO (Kannan Rajah) Hack to get injector in unit test.
    public static Injector getInjector() {
      return injector;
    }

    public void run(MyriadConfiguration cfg) throws Exception {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Bindings: " + injector.getAllBindings());
        }

        JmxReporter.forRegistry(new MetricRegistry()).build().start();

        initWebApp(injector);
        initHealthChecks(injector);
        initProfiles(injector);
        validateNMInstances(injector);
        initDisruptors(injector);

        initRebalancerService(cfg, injector);
        initTerminatorService(injector);
        startMesosDriver(injector);
        startNMInstances(injector);
    }

    private void startMesosDriver(Injector injector) {
        LOGGER.info("starting mesosDriver..");
        injector.getInstance(MyriadDriverManager.class).startDriver();
        LOGGER.info("started mesosDriver..");
    }

    /**
     * Brings up the embedded jetty webserver for serving REST APIs.
     *
     * @param injector
     */
    private void initWebApp(Injector injector) throws Exception {
        webServer = injector.getInstance(MyriadWebServer.class);
        webServer.start();
    }

    /**
     * Initializes health checks.
     *
     * @param injector
     */
    private void initHealthChecks(Injector injector) {
        LOGGER.info("Initializing HealthChecks");
        healthCheckRegistry = new HealthCheckRegistry();
        healthCheckRegistry.register(MesosMasterHealthCheck.NAME,
                injector.getInstance(MesosMasterHealthCheck.class));
        healthCheckRegistry.register(ZookeeperHealthCheck.NAME,
                injector.getInstance(ZookeeperHealthCheck.class));
        healthCheckRegistry.register(MesosDriverHealthCheck.NAME,
                injector.getInstance(MesosDriverHealthCheck.class));
    }

    private void initProfiles(Injector injector) {
        LOGGER.info("Initializing Profiles");
        NMProfileManager profileManager = injector.getInstance(NMProfileManager.class);
        Map<String, Map<String, String>> profiles = injector.getInstance(MyriadConfiguration.class).getProfiles();
        if (MapUtils.isNotEmpty(profiles)) {
            for (Map.Entry<String, Map<String, String>> profile : profiles.entrySet()) {
                Map<String, String> profileResourceMap = profile.getValue();
                if (MapUtils.isNotEmpty(profiles)
                        && profileResourceMap.containsKey("cpu")
                        && profileResourceMap.containsKey("mem")) {
                    Long cpu = Long.parseLong(profileResourceMap.get("cpu"));
                    Long mem = Long.parseLong(profileResourceMap.get("mem"));

                    profileManager.add(new NMProfile(profile.getKey(), cpu, mem));
                } else {
                    LOGGER.error("Invalid definition for profile: " + profile.getKey());
                }
            }
        }
    }

    private void validateNMInstances(Injector injector) {
        LOGGER.info("Validating nmInstances..");
        Map<String, Integer> nmInstances = injector.getInstance(MyriadConfiguration.class).getNmInstances();
        NMProfileManager profileManager = injector.getInstance(NMProfileManager.class);
        long maxCpu = Long.MIN_VALUE;
        long maxMem = Long.MIN_VALUE;
        for (Map.Entry<String, Integer> entry : nmInstances.entrySet()) {
          String profile = entry.getKey();
          NMProfile nmProfile = profileManager.get(profile);
          if (nmProfile == null) {
            throw new RuntimeException("Invalid profile name '" + profile + "' specified in 'nmInstances'");
          }
          if (entry.getValue() > 0) {
            if (nmProfile.getCpus() > maxCpu) { // find the profile with largest number of cpus
              maxCpu = nmProfile.getCpus();
              maxMem = nmProfile.getMemory(); // use the memory from the same profile
            }
          }
        }
        if (maxCpu <= 0 || maxMem <= 0) {
          throw new RuntimeException("Please configure 'nmInstances' with at least one instance/profile " 
              + "with non-zero cpu/mem resources.");
        }
    }

    private void startNMInstances(Injector injector) {
      Map<String, Integer> nmInstances = injector.getInstance(MyriadConfiguration.class).getNmInstances();
      MyriadOperations myriadOperations = injector.getInstance(MyriadOperations.class);
      for (Map.Entry<String, Integer> entry : nmInstances.entrySet()) {
        myriadOperations.flexUpCluster(entry.getValue(), entry.getKey());
      }
    }

    private void initTerminatorService(Injector injector) {
        LOGGER.info("Initializing Terminator");
        terminatorService = Executors.newScheduledThreadPool(1);
        final int initialDelay = 100;
        final int period = 2000;
        terminatorService.scheduleAtFixedRate(
                injector.getInstance(TaskTerminator.class), initialDelay, period, TimeUnit.MILLISECONDS);
    }

    private void initRebalancerService(MyriadConfiguration cfg,
                                       Injector injector) {
        if (cfg.isRebalancer()) {
            LOGGER.info("Initializing Rebalancer");
            rebalancerService = Executors.newScheduledThreadPool(1);
            final int initialDelay = 100;
            final int period = 5000;
            rebalancerService.scheduleAtFixedRate(
                    injector.getInstance(Rebalancer.class), initialDelay, period, TimeUnit.MILLISECONDS);
        } else {
            LOGGER.info("Rebalancer is not turned on");
        }
    }

    private void initDisruptors(Injector injector) {
        LOGGER.info("Initializing Disruptors");
        DisruptorManager disruptorManager = injector
                .getInstance(DisruptorManager.class);
        disruptorManager.init(injector);
    }

}