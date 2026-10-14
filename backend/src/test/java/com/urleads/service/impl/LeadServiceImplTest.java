package com.urleads.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class LeadServiceImplTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private LeadServiceImpl service;

    @Test
    void crear_sinTelefonoNiCorreo_lanzaIllegalArgument() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Ana");
        request.setEtapa("NUEVO");

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(leadRepository, usuarioRepository);
    }

    @Test
    void crear_conContactosEnBlanco_lanzaIllegalArgument() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Ana");
        request.setEtapa("NUEVO");
        request.setTelefono("   ");
        request.setCorreo("  ");

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizar_sinTelefonoNiCorreo_lanzaIllegalArgument() {
        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Ana");
        request.setEtapa("NUEVO");

        assertThatThrownBy(() -> service.actualizar(10L, 1L, request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(leadRepository);
    }
}