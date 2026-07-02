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
import org.springframework.web.reactive.function.BodyInserters;

import com.uma.example.springuma.integration.base.AbstractIntegration;
import com.uma.example.springuma.model.Imagen;
import com.uma.example.springuma.model.Informe;
import com.uma.example.springuma.model.Medico;
import com.uma.example.springuma.model.Paciente;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class InformeControllerWebTestClientIT extends AbstractIntegration {

    @LocalServerPort
    private Integer port;

    private WebTestClient testClient;

    private Medico medico;
    private Paciente paciente;
    private Imagen imagen;
    private Informe informe;

    @PostConstruct
    public void init() {
        testClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofMillis(300000)).build();
    }

    @BeforeEach
    void setUp() {
        // 1. Inicializamos los objetos básicos
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

        // 2. Creamos el médico en el sistema
        testClient.post().uri("/medico")
                .body(Mono.just(medico), Medico.class)
                .exchange()
                .expectStatus().isCreated();

        // 3. Creamos el paciente en el sistema
        testClient.post().uri("/paciente")
                .body(Mono.just(paciente), Paciente.class)
                .exchange()
                .expectStatus().isCreated();

        // 4. Subimos una imagen para ese paciente.
        // El endpoint /imagen no devuelve un JSON con el objeto Imagen (devuelve
        // texto plano), así que sólo comprobamos el status y luego recuperamos
        // la imagen creada consultando el listado de imágenes del paciente.
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("image", new FileSystemResource(Paths.get("src/test/resources/healthy.png").toFile()));
        builder.part("paciente", paciente);

        testClient.post().uri("/imagen")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk();

        List<Imagen> imagenes = testClient.get().uri("/imagen/paciente/" + paciente.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Imagen.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(imagenes);
        assertFalse(imagenes.isEmpty());
        imagen = imagenes.get(0);

        // 5. Preparamos un objeto Informe para las pruebas
        informe = new Informe();
        informe.setPrediccion("Not cancer");
        informe.setContenido("Paciente sana tras revisión de mamografía.");
        informe.setImagen(imagen);
    }

    @Test
    @DisplayName("Crear un informe correctamente")
    void crearInforme_correctamente() {
        testClient.post().uri("/informe")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(informe), Informe.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").exists()
                .jsonPath("$.prediccion").isEqualTo("Not cancer")
                .jsonPath("$.contenido").isEqualTo("Paciente sana tras revisión de mamografía.");
    }

    @Test
    @DisplayName("Obtener un informe por su ID")
    void obtenerInformePorId() {
        // Primero lo creamos
        Informe informeCreado = testClient.post().uri("/informe")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(informe), Informe.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Informe.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(informeCreado);

        // Luego lo recuperamos por su ID
        testClient.get().uri("/informe/" + informeCreado.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(informeCreado.getId())
                .jsonPath("$.prediccion").isEqualTo(informeCreado.getPrediccion());
    }

    @Test
    @DisplayName("Listar todos los informes de una imagen específica")
    void obtenerInformesPorImagen() {
        // Creamos el informe
        testClient.post().uri("/informe")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(informe), Informe.class)
                .exchange()
                .expectStatus().isCreated();

        // Buscamos los informes asociados a esa imagen
        List<Informe> informes = testClient.get().uri("/informe/imagen/" + imagen.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Informe.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(informes);
        assertEquals(1, informes.size());
        assertEquals(imagen.getId(), informes.get(0).getImagen().getId());
    }

    @Test
    @DisplayName("Eliminar un informe y comprobar que desaparece")
    void eliminarInforme_desaparece() {
        // Creamos el informe
        Informe informeCreado = testClient.post().uri("/informe")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(informe), Informe.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Informe.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(informeCreado);

        // Lo eliminamos
        testClient.delete().uri("/informe/" + informeCreado.getId())
                .exchange()
                .expectStatus().isNoContent(); // O isOk() dependiendo de tu controlador

        // Intentamos buscarlo y esperamos un error 404 (Not Found)
        testClient.get().uri("/informe/" + informeCreado.getId())
                .exchange()
                .expectStatus().isNotFound();
    }
}