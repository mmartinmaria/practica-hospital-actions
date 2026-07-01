package com.uma.example.springuma.integration;

import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uma.example.springuma.integration.base.AbstractIntegration;
import com.uma.example.springuma.model.Medico;
import com.uma.example.springuma.model.MedicoService;
import com.uma.example.springuma.model.Paciente;

/**
 * Pruebas de integración del controlador de pacientes.
 * Se prueban los caminos: asociar paciente a médico, obtener, actualizar,
 * reasignar de médico, listar pacientes de un médico y eliminar.
 */
public class PacienteControllerMockMvcIT extends AbstractIntegration {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MedicoService medicoService;

    Paciente paciente;
    Medico medico;

    @BeforeEach
    void setUp() {
        medico = new Medico();
        medico.setNombre("Miguel");
        medico.setId(1L);
        medico.setDni("835");
        medico.setEspecialidad("Ginecologo");

        paciente = new Paciente();
        paciente.setId(1L);
        paciente.setNombre("Maria");
        paciente.setDni("888");
        paciente.setEdad(20);
        paciente.setCita("Ginecologia");
        paciente.setMedico(this.medico);
    }

    private void crearMedico(Medico medico) throws Exception {
        this.mockMvc.perform(post("/medico")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(medico)))
                .andExpect(status().isCreated());
    }

    private void crearPaciente(Paciente paciente) throws Exception {
        mockMvc.perform(post("/paciente")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(paciente)))
                .andExpect(status().isCreated());
    }

    private void getPacienteById(Long id, Paciente expected) throws Exception {
        mockMvc.perform(get("/paciente/" + id))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$").exists())
                .andExpect(jsonPath("$.id").value(expected.getId()))
                .andExpect(jsonPath("$.nombre").value(expected.getNombre()))
                .andExpect(jsonPath("$.dni").value(expected.getDni()))
                .andExpect(jsonPath("$.edad").value(expected.getEdad()))
                .andExpect(jsonPath("$.cita").value(expected.getCita()))
                .andExpect(jsonPath("$.medico.id").value(expected.getMedico().getId()));
    }

    @Test
    @DisplayName("Crear paciente y recuperarlo por ID pasado por parametro")
    void savePaciente_RecuperaPacientePorId() throws Exception {
        crearMedico(medico);
        crearPaciente(paciente);

        //Obtener paciente por ID
        getPacienteById(paciente.getId(), paciente);
    }

    @Test
    @DisplayName("Actualizar un paciente y comprobar que los cambios se reflejan")
    void actualizarPaciente_reflejaCambios() throws Exception {
        crearMedico(medico);
        crearPaciente(paciente);

        paciente.setEdad(25);
        paciente.setCita("Revision");

        mockMvc.perform(put("/paciente")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(paciente)))
                .andExpect(status().isNoContent());

        getPacienteById(paciente.getId(), paciente);
    }

    @Test
    @DisplayName("Listar los pacientes asociados a un médico")
    void listarPacientesDeUnMedico() throws Exception {
        crearMedico(medico);
        crearPaciente(paciente);

        Paciente paciente2 = new Paciente();
        paciente2.setId(2L);
        paciente2.setNombre("Juan");
        paciente2.setDni("999");
        paciente2.setEdad(30);
        paciente2.setCita("Ginecologia");
        paciente2.setMedico(medico);
        crearPaciente(paciente2);

        mockMvc.perform(get("/paciente/medico/" + medico.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Camino largo: reasignar un paciente de un médico a otro")
    void reasignarPacienteAOtroMedico() throws Exception {
        crearMedico(medico);
        crearPaciente(paciente);

        Medico medico2 = new Medico();
        medico2.setId(2L);
        medico2.setDni("111");
        medico2.setNombre("Laura");
        medico2.setEspecialidad("Radiologia");
        crearMedico(medico2);

        // Reasignamos el paciente al segundo médico
        paciente.setMedico(medico2);
        mockMvc.perform(put("/paciente")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(paciente)))
                .andExpect(status().isNoContent());

        // El médico original ya no tiene pacientes
        mockMvc.perform(get("/paciente/medico/" + medico.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // El nuevo médico sí tiene al paciente
        mockMvc.perform(get("/paciente/medico/" + medico2.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].dni").value(paciente.getDni()));
    }

    @Test
    @DisplayName("Eliminar un paciente y comprobar que ya no aparece listado")
    void eliminarPaciente_yaNoAparece() throws Exception {
        crearMedico(medico);
        crearPaciente(paciente);

        mockMvc.perform(delete("/paciente/" + paciente.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/paciente/medico/" + medico.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Crear un paciente con un DNI ya existente produce un error")
    void crearPacienteConDniDuplicado_devuelveError() throws Exception {
        crearMedico(medico);
        crearPaciente(paciente);

        Paciente duplicado = new Paciente();
        duplicado.setId(2L);
        duplicado.setNombre("Otro");
        duplicado.setDni(paciente.getDni()); // mismo DNI (columna unique)
        duplicado.setEdad(40);
        duplicado.setCita("Ginecologia");
        duplicado.setMedico(medico);

        mockMvc.perform(post("/paciente")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(duplicado)))
                .andExpect(status().is5xxServerError());
    }
}


