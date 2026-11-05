package com.omyfish.shop.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.xnio.management.XnioWorkerMXBean;

/**
 * Unlike Tomcat/Jetty, Spring Boot ships no actuator metrics binder for Undertow's XNIO worker
 * pool — this fills that gap by hand. The pool/MXBean only exist once the embedded server has
 * actually started, hence the WebServerInitializedEvent listener rather than a bean constructed
 * at wiring time.
 */
@Component
class UndertowMetricsBinder implements ApplicationListener<WebServerInitializedEvent> {

    private final MeterRegistry registry;

    UndertowMetricsBinder(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (!(event.getWebServer() instanceof UndertowWebServer undertowWebServer)) {
            return;
        }
        XnioWorkerMXBean worker = undertowWebServer.getUndertow().getWorker().getMXBean();

        Gauge.builder("undertow.worker.busy.threads", worker, XnioWorkerMXBean::getBusyWorkerThreadCount)
            .description("Undertow XNIO worker threads currently executing a request")
            .register(registry);
        Gauge.builder("undertow.worker.pool.size", worker, XnioWorkerMXBean::getWorkerPoolSize)
            .description("Undertow XNIO worker thread pool's current size")
            .register(registry);
        Gauge.builder("undertow.worker.queue.size", worker, XnioWorkerMXBean::getWorkerQueueSize)
            .description("Tasks waiting for a free Undertow worker thread")
            .register(registry);
        Gauge.builder("undertow.worker.io.threads", worker, XnioWorkerMXBean::getIoThreadCount)
            .description("Undertow XNIO non-blocking I/O threads")
            .register(registry);
    }
}
