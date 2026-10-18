package cl.riffpass.procesamiento.notificaciones;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="notificaciones")
public class Notificacion {
    @Id private UUID id;
    @Column(name="evento_mensaje_id", nullable=false) private UUID eventoMensajeId;
    @Column(nullable=false, length=128) private String destinatario;
    @Column(nullable=false, length=200) private String asunto;
    @Column(nullable=false, length=1000) private String cuerpo;
    @Column(name="creada_en", nullable=false) private Instant creadaEn;
    protected Notificacion() {}
    public Notificacion(UUID eventoMensajeId, String destinatario, String asunto, String cuerpo) {
        id = UUID.randomUUID(); this.eventoMensajeId = eventoMensajeId; this.destinatario = destinatario;
        this.asunto = asunto; this.cuerpo = cuerpo; creadaEn = Instant.now();
    }
}
