package org.example.application.dto.proposal;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelinePageDTO {
    private List<PipelineTripCardDTO> items;
    private long total;
    private int page;
    private int size;
}
