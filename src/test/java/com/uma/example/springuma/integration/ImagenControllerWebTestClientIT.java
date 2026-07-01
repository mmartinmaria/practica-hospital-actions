package com.uma.example.springuma.integration;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.uma.example.springuma.model.Imagen;
import com.uma.example.springuma.model.Medico;
import com.uma.example.springuma.model.Paciente;
import com.uma.example.springuma.integration.base.AbstractIntegration;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

import org.springframework.web.reactive.function.BodyInserters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de integración del controlador de imágenes.
 * Requieren servidor completo (WebTestClient) porque implican subida
 * de ficheros multipart y lectura/escritura en base de datos real (H2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ImagenControllerWebTestClientIT extends AbstractIntegration {

    @LocalServerPort
    private Integer port;

    private WebTestClient testClient;

    private Paciente paciente;
    private Medico medico;

    @PostConstruct
    public void init() {
        testClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofMillis(300000)).build();
    }

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
        paciente.setMedico(medico);

        // Crea médico
        testClient.post().uri("/medico")
                .body(Mono.just(medico), Medico.class)
                .exchange()
                .expectStatus().isCreated();

        // Crea paciente
        testClient.post().uri("/paciente")
                .body(Mono.just(paciente), Paciente.class)
                .exchange()
                .expectStatus().isCreated();
    }

    private void subirImagen(String nombreArchivo) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("image", new FileSystemResource(Paths.get("src/test/resources/" + nombreArchivo).toFile()));
            builder.part("paciente", paciente);

            testClient.post().uri("/imagen")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .exchange()
                    .expectStatus().isOk();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<Imagen> obtenerImagenesDelPaciente() {
        return testClient.get().uri("/imagen/paciente/" + paciente.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Imagen.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    @DisplayName("Subir una imagen de un paciente correctamente")
    void subirImagen_correctamente() {
        subirImagen("healthy.png");

        List<Imagen> imagenes = obtenerImagenesDelPaciente();

        assertNotNull(imagenes);
        assertEquals(1, imagenes.size());
        assertEquals("healthy.png", imagenes.get(0).getNombre());
        assertEquals(paciente.getId(), imagenes.get(0).getPaciente().getId());
    }

    @Test
    @DisplayName("Subir varias imágenes del mismo paciente y listarlas todas")
    void subirVariasImagenes_seListanTodas() {
        subirImagen("healthy.png");
        subirImagen("no_healthty.png");

        List<Imagen> imagenes = obtenerImagenesDelPaciente();

        assertNotNull(imagenes);
        assertEquals(2, imagenes.size());
    }

    @Test
    @DisplayName("Obtener la información (metadatos) de una imagen subida")
    void obtenerInfoDeImagen() {
        subirImagen("healthy.png");
        Long idImagen = obtenerImagenesDelPaciente().get(0).getId();

        testClient.get().uri("/imagen/info/" + idImagen)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(idImagen)
                .jsonPath("$.nombre").isEqualTo("healthy.png")
                .jsonPath("$.paciente.id").isEqualTo(paciente.getId());
    }

    @Test
    @DisplayName("Descargar el contenido binario de una imagen subida")
    void descargarImagen() {
        subirImagen("healthy.png");
        Long idImagen = obtenerImagenesDelPaciente().get(0).getId();

        byte[] contenido = testClient.get().uri("/imagen/" + idImagen)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("image/png")
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        assertNotNull(contenido);
        assertTrue(contenido.length > 0);
    }

    @Test
    @DisplayName("Camino largo: subir una imagen y realizar una predicción de IA sobre ella")
    void subirImagenYRealizarPrediccion() {
        subirImagen("healthy.png");
        Long idImagen = obtenerImagenesDelPaciente().get(0).getId();

        // El resultado es aleatorio: sólo comprobamos que se devuelve
        // un resultado bien formado indicando si hay cáncer o no.
        String resultado = testClient.get().uri("/imagen/predict/" + idImagen)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(resultado);
        assertTrue(resultado.contains("status"));
        // Puede devolver "Cancer" o "Not cancer": ambas contienen "cancer" en minúsculas
        assertTrue(resultado.toLowerCase().contains("cancer"));
        assertTrue(resultado.contains("score"));
    }

    @Test
    @DisplayName("Eliminar una imagen y comprobar que deja de estar listada")
    void eliminarImagen_dejaDeEstarListada() {
        subirImagen("healthy.png");
        Long idImagen = obtenerImagenesDelPaciente().get(0).getId();

        testClient.delete().uri("/imagen/" + idImagen)
                .exchange()
                .expectStatus().isNoContent();

        List<Imagen> imagenes = obtenerImagenesDelPaciente();
        assertNotNull(imagenes);
        assertTrue(imagenes.isEmpty());
    }
}