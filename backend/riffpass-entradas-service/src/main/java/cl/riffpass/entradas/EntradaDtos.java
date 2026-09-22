package cl.riffpass.entradas;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class EntradaDtos {
    private EntradaDtos() {}
    public record AbrirCupos(@Min(1) @Max(100000) int capacidad) {}
    public record Validar(@NotNull UUID codigo) {}
    public record Cupos(UUID eventoId, int capacidad, int emitidas, int disponibles) {}
    public record Respuesta(UUID id, UUID eventoId, UUID codigo, Entrada.Estado estado,
                            Instant emitidaEn, Instant utilizadaEn) {}
}
