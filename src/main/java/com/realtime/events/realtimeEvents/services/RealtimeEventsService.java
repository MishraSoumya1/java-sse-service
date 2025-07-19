package com.realtime.events.realtimeEvents.services;

import com.realtime.events.realtimeEvents.dto.InquiryApiRequest;
import com.realtime.events.realtimeEvents.dto.InquiryApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Sinks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeEventsService {

    private final Map<String, Sinks.Many<ServerSentEvent<InquiryApiResponse>>> sessions;
    private final ScheduledExecutorService scheduler;
    private final WebClient webClient;

    @Value("${external.api.start-uri}")
    private String startApiUri;

    @Value("${external.api.status-uri}")
    private String statusApiUri;

    @Value("${polling.intervals}")
    private String pollingIntervalsConfig;

    private volatile List<Integer> pollingIntervals; // Thread-safe reinitialization

    public void startExternalProcess(String sessionId, InquiryApiRequest input) {
        ensurePollingIntervalsLoaded();

        Sinks.Many<ServerSentEvent<InquiryApiResponse>> sink = sessions.get(sessionId);
        if (sink == null) {
            throw new IllegalArgumentException("SSE session not found for sessionId: " + sessionId);
        }

        webClient.post()
                .uri(startApiUri)
                .bodyValue(input)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> {
                    log.info("Received ACK from start API: {}", response);
                    sendToClient(sessionId, new InquiryApiResponse("ACK", "Process started", input.getTrackingId()));
                    startPolling(sessionId, input.getTrackingId(), 0);
                })
                .doOnError(error -> {
                    log.error("Failed to call start API", error);
                    sendToClient(sessionId, new InquiryApiResponse("ERROR", "Start API failed: " + error.getMessage(), input.getTrackingId()));
                })
                .subscribe();
    }

    private void startPolling(String sessionId, String trackingId, int attemptIndex) {
        Runnable pollTask = new Runnable() {
            int attempts = attemptIndex;

            @Override
            public void run() {
                if (attempts >= pollingIntervals.size()) {
                    sendToClient(sessionId, new InquiryApiResponse("TIMEOUT", "Polling limit reached", trackingId));
                    log.warn("Polling limit reached for sessionId={}, trackingId={}", sessionId, trackingId);
                    return;
                }

                webClient.get()
                        .uri(statusApiUri, attempts + 1)
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(status -> {
                            log.info("Status response for trackingId={}: {}", trackingId, status);
                            if (status.contains("COMPLETED")) {
                                return new InquiryApiResponse("COMPLETED", "Process completed", trackingId);
                            } else if (status.contains("FAILED")) {
                                return new InquiryApiResponse("FAILED", "Process failed", trackingId);
                            } else if (status.contains("REJECTED")) {
                                return new InquiryApiResponse("REJECTED", "Process rejected", trackingId);
                            } else {
                                return new InquiryApiResponse("IN_PROGRESS", status, trackingId);
                            }
                        })
                        .subscribe(response -> {
                            sendToClient(sessionId, response);

                            // Stop polling on final states
                            if (List.of("COMPLETED", "FAILED", "REJECTED").contains(response.getStatus())) {
                                log.info("Polling stopped after terminal state: {}", response.getStatus());
                                return;
                            }

                            int nextDelay = getPollingInterval(attempts);
                            attempts++;
                            scheduler.schedule(this, nextDelay, TimeUnit.SECONDS);

                        }, error -> {
                            if (error instanceof WebClientResponseException wcre &&
                                    (wcre.getStatusCode().is4xxClientError() || wcre.getStatusCode().is5xxServerError())) {
                                log.error("Polling failed with response error: {}", wcre.getStatusCode());
                                sendToClient(sessionId, new InquiryApiResponse("ERROR", "Polling failed: " + wcre.getMessage(), trackingId));
                            } else {
                                log.error("Polling exception occurred", error);
                                sendToClient(sessionId, new InquiryApiResponse("ERROR", "Exception: " + error.getMessage(), trackingId));
                            }
                        });
            }
        };

        int initialDelay = getPollingInterval(attemptIndex);
        scheduler.schedule(pollTask, initialDelay, TimeUnit.SECONDS);
    }

    private void ensurePollingIntervalsLoaded() {
        if (pollingIntervals == null) {
            synchronized (this) {
                if (pollingIntervals == null) {
                    pollingIntervals = Arrays.stream(pollingIntervalsConfig.split(","))
                            .map(String::trim)
                            .map(Integer::parseInt)
                            .toList();
                    log.info("Polling intervals loaded: {}", pollingIntervals);
                }
            }
        }
    }

    private int getPollingInterval(int attemptIndex) {
        if (pollingIntervals == null || pollingIntervals.isEmpty()) return 30;
        return (attemptIndex < pollingIntervals.size())
                ? pollingIntervals.get(attemptIndex)
                : pollingIntervals.get(pollingIntervals.size() - 1); // fallback to last
    }

    private void sendToClient(String sessionId, InquiryApiResponse response) {
        Sinks.Many<ServerSentEvent<InquiryApiResponse>> sink = sessions.get(sessionId);
        if (sink != null) {
            sink.tryEmitNext(ServerSentEvent.builder(response).build());
        }
    }
}
