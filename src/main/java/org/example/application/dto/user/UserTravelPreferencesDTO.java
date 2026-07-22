package org.example.application.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTravelPreferencesDTO {
    private String currency;
    private String unitSystem;
    private String seatPreference;
    private String accommodationPreference;
    @Builder.Default
    private List<String> dietaryRestrictions = new ArrayList<>();
    private String baseAirport;
    private boolean cloudBackupEnabled;
}
