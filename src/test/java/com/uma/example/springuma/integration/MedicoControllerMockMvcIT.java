package com.uma.example.springuma.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uma.example.springuma.integration.base.AbstractIntegration;
import com.uma.example.springuma.model.Medico;

/**
 * Pruebas de integración del controlador de médicos.
 * Se prueban los caminos: crear, obtener (por id y por dni), actualizar y eliminar.
 */
public class MedicoControllerMockMvcIT extends AbstractIntegration {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Medico medico;

    @BeforeEach
    void setUp() {
        medico = new Medico();
        medico.setId(1L);
        medico.setDni("835");
        medico.setNombre("Miguel");
        medico.setEspecialidad("Ginecologia");
    }

    private void crearMedico(Medico medico) throws Exception {
        this.mockMvc.perform(post("/medico")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(medico)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Crear médico y recuperarlo por ID")
    void crearMedico_recuperaPorId() throws Exception {
        crearMedico(medico);

        mockMvc.perform(get("/medico/" + medico.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.id").value(medico.getId()))
                .andExpect(jsonPath("$.dni").value(medico.getDni()))
                .andExpect(jsonPath("$.nombre").value(medico.getNombre()))
                .andExpect(jsonPath("$.especialidad").value(medico.getEspecialidad()));
    }

    @Test
    @DisplayName("Crear médico y recuperarlo por DNI")
    void crearMedico_recuperaPorDni() throws Exception {
        crearMedico(medico);

        mockMvc.perform(get("/medico/dni/" + medico.getDni()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dni").value(medico.getDni()))
                .andExpect(jsonPath("$.nombre").value(medico.getNombre()));
    }

    @Test
    @DisplayName("Buscar médico por un DNI que no existe devuelve 404")
    void buscarMedicoPorDniInexistente_devuelve404() throws Exception {
        mockMvc.perform(get("/medico/dni/NO-EXISTE"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Actualizar médico y comprobar que los cambios se reflejan")
    void actualizarMedico_reflejaCambios() throws Exception {
        crearMedico(medico);

        medico.setNombre("Miguel Angel");
        medico.setEspecialidad("Radiologia");

        mockMvc.perform(put("/medico")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(medico)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/medico/" + medico.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Miguel Angel"))
                .andExpect(jsonPath("$.especialidad").value("Radiologia"));
    }

    @Test
    @DisplayName("Eliminar médico y comprobar que deja de existir")
    void eliminarMedico_dejaDeExistir() throws Exception {
        crearMedico(medico);

        mockMvc.perform(delete("/medico/" + medico.getId()))
                .andExpect(status().isOk());

        // Tras eliminarlo, la búsqueda por DNI ya no debe encontrarlo
        mockMvc.perform(get("/medico/dni/" + medico.getDni()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Crear un médico con un DNI ya existente produce un error")
    void crearMedicoConDniDuplicado_devuelveError() throws Exception {
        crearMedico(medico);

        Medico duplicado = new Medico();
        duplicado.setId(2L);
        duplicado.setDni(medico.getDni()); // mismo DNI (columna unique)
        duplicado.setNombre("Otro Medico");
        duplicado.setEspecialidad("Cardiologia");

        mockMvc.perform(post("/medico")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(duplicado)))
                .andExpect(status().is5xxServerError());
    }
}



