package com.urleads.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadInternalResponse {
    private Long id;
    private String stage;
    private LocalDate nextFollowUp;
}
