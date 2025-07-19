package com.realtime.events.realtimeEvents.controllers;

import com.realtime.events.realtimeEvents.dto.InquiryApiRequest;
import com.realtime.events.realtimeEvents.dto.InquiryApiResponse;
import com.realtime.events.realtimeEvents.services.RealtimeEventsService;
import com.realtime.events.realtimeEvents.services.RealtimeEventsServiceV1;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
@Validated
public class RealtimeEventsController {

    private final Map<String, Sinks.Many<ServerSentEvent<InquiryApiResponse>>> sessions;
    private final ScheduledExecutorService scheduler;
    private final RealtimeEventsServiceV1 realtimeEventsService;

    @GetMapping(value = "/connect/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<InquiryApiResponse>> connect(@PathVariable String sessionId) {
        Sinks.Many<ServerSentEvent<InquiryApiResponse>> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessions.put(sessionId, sink);

        return sink.asFlux()
                .doOnCancel(() -> {
                    System.out.println("SSE disconnected: " + sessionId);
                    sessions.remove(sessionId);
                });
    }

    @PostMapping("/start/{sessionId}")
    public Mono<ResponseEntity<String>> startExternalProcess(
            @PathVariable String sessionId,
            @Valid @RequestBody InquiryApiRequest input) {
        try {
            realtimeEventsService.startExternalProcess(sessionId, input);
            return Mono.just(ResponseEntity.ok("ACK Received"));
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().body(e.getMessage()));
        }
    }

    @DeleteMapping("/disconnect/{sessionId}")
    public Mono<Void> disconnect(@PathVariable String sessionId) {
        Sinks.Many<ServerSentEvent<InquiryApiResponse>> sink = sessions.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
        return Mono.empty();
    }
}
