package com.realtime.events.realtimeEvents.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class InquiryApiResponse {
    private String status;
    private String message;
    private String trackingId;
}
