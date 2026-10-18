package cl.riffpass.admin.web;

import cl.riffpass.mensajeria.TipoEvento;
import jakarta.validation.constraints.*;
import java.util.*;

public final class AdminDtos {
    private AdminDtos() {}
    private static final String NOMBRE = "^[A-Za-z0-9_-]{1,80}$";

    public record NuevoTopico(@NotBlank @Pattern(regexp = NOMBRE) String nombre) {}
    public record Topico(String nombre, String arn) {}

    public record NuevaCola(@NotBlank @Pattern(regexp = NOMBRE) String nombre,
                            @NotNull Boolean conDlq,
                            @Min(1) @Max(1000) Integer maxRecepciones,
                            @Min(0) @Max(43200) Integer visibilidadSeg,
                            @Min(60) @Max(1209600) Integer retencionSeg,
                            @Min(60) @Max(1209600) Integer retencionDlqSeg) {}
    public record EstadoCola(String nombre, String url, String arn, long mensajesDisponibles, long mensajesEnProceso,
                             long mensajesDemorados, Long antiguedadMensajeMasAntiguoSeg, int visibilidadSeg,
                             int retencionSeg, int esperaLargoSondeoSeg, String dlq, Integer maxRecepciones,
                             Long mensajesEnDlq, Map<String, String> atributos) {}

    public record NuevaSuscripcion(@NotBlank @Pattern(regexp = NOMBRE) String cola, List<TipoEvento> tipos) {}
    public record Filtro(@NotEmpty List<TipoEvento> tipos) {}
    public record Suscripcion(String id, String arn, String protocolo, String destino, Boolean entregaRaw,
                              String filtro) {}

    public record PublicacionPrueba(@NotNull TipoEvento tipo, @NotNull Map<String, String> datos,
                                    @Pattern(regexp = "^(TRANSITORIO|PERMANENTE|NO_RECUPERABLE)(@[A-Z]+)?$")
                                    String simulacion) {}
    public record Publicado(String snsMessageId, UUID eventoId) {}

    public record MensajeDlq(String sqsId, String cuerpo, Map<String, String> atributos, Map<String, String> sistema) {}
    public record Reproceso(String dlq, String destino, int reprocesados, List<String> ids) {}
    public record Purga(String dlq, String resultado) {}

    public record CambioEstado(@NotNull EstadoConsumidor estado) {}
    public enum EstadoConsumidor { ACTIVO, PAUSADO }
    public record Consumidor(String instancia, String url, String id, String estado, String error) {}
}
