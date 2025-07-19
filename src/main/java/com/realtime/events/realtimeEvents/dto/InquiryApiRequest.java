package com.realtime.events.realtimeEvents.dto;

import lombok.*;
import jakarta.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class InquiryApiRequest  {
    @NotBlank(message = "trackingId is required")
    private String trackingId;

    @NotBlank(message = "userId is required")
    private String userId;
}
