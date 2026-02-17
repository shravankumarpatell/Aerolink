package com.aerolink.ride.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationRequestDTO {

    @Size(max = 500)
    private String reason;
}
