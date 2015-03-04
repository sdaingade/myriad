package com.ebay.myriad.scheduler;

import com.ebay.myriad.configuration.MyriadConfiguration;
import com.ebay.myriad.state.SchedulerState;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

/**
 * {@link ReconcileService} is responsible for reconciling tasks with the mesos master
 */
public class ReconcileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReconcileService.class);

    private SchedulerState state;
    private MyriadConfiguration cfg;
    private Date lastReconcileTime;

    @Inject
    public ReconcileService(SchedulerState state, MyriadConfiguration cfg) {
        this.state = state;
        this.cfg = cfg;
    }

    public void reconcile(SchedulerDriver driver) {
        Collection<Protos.TaskStatus> taskStatuses = state.getTaskStatuses();

        if (taskStatuses.size() == 0) {
            return;
        }
        LOGGER.info("Reconciling {} tasks.", taskStatuses.size());

        driver.reconcileTasks(taskStatuses);

        lastReconcileTime = new Date();

        int attempt = 1;

        Integer maxReconcileAttempts = this.cfg.getMaxReconcileAttempts();
        Integer reconciliationDelayMillis = this.cfg.getReconciliationDelayMillis();
        while (attempt <= maxReconcileAttempts) {
            try {
                // TODO(mohit): Using exponential backoff here, maybe backoff strategy should be configurable.
                Thread.sleep(reconciliationDelayMillis * attempt);
            } catch (InterruptedException e) {
                LOGGER.error("Interrupted", e);
            }
            Collection<Protos.TaskStatus> notYetReconciled = new ArrayList<>();
            for (Protos.TaskStatus status : state.getTaskStatuses()) {
                if (status.getTimestamp() < lastReconcileTime.getTime()) {
                    notYetReconciled.add(status);
                }
            }
            LOGGER.info("Reconcile attempt {} for {} tasks", attempt, notYetReconciled.size());
            driver.reconcileTasks(notYetReconciled);
            lastReconcileTime = new Date();
            attempt++;
        }
    }
}
