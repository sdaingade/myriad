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
package com.ebay.myriad.scheduler.event.handlers;

import com.ebay.myriad.scheduler.ReconcileService;
import com.ebay.myriad.scheduler.event.ReRegisteredEvent;
import com.ebay.myriad.state.SchedulerState;
import com.google.inject.Inject;
import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * handles and logs mesos re-register events
 */
public class ReRegisteredEventHandler implements EventHandler<ReRegisteredEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReRegisteredEventHandler.class);

    @Inject
    private SchedulerState state;

    @Inject
    private ReconcileService reconcileService;

    @Override
    public void onEvent(ReRegisteredEvent event, long sequence, boolean endOfBatch) throws Exception {
        LOGGER.info("Framework re-registered: {}", event);
        reconcileService.reconcile(event.getDriver());
    }
}
