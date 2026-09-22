package cl.riffpass.eventos;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class EventoDtos {
    private EventoDtos() {}
    public record Crear(
            @NotBlank @Size(max=120) String nombre,
            @NotBlank @Size(max=120) String artista,
            @NotBlank @Size(max=160) String recinto,
            @NotBlank @Size(max=100) String ciudad,
            @NotBlank @Size(max=2000) String descripcion,
            @NotNull @Future Instant fecha) {}
    // Fecha, recinto, artista y propietario son inmutables en EP1:
    // cambiar esas condiciones requiere definir cancelaciones/reprogramaciones.
    public record Editar(@NotBlank @Size(max=120) String nombre,
                         @NotBlank @Size(max=2000) String descripcion) {}
    public record Respuesta(UUID id, String nombre, String artista, String recinto, String ciudad,
                            String descripcion, Instant fecha, String organizadorSub, Instant creadoEn) {}
}
