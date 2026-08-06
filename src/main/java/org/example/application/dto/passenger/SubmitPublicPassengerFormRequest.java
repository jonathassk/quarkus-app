package org.example.application.dto.passenger;

import lombok.Data;

@Data
public class SubmitPublicPassengerFormRequest {
    /** Se true, autoriza salvar/atualizar perfil reutilizável na agência. */
    private Boolean saveToAgencyProfile;
}
