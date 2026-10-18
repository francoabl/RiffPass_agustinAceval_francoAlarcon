package cl.riffpass.procesamiento.auditoria;

import cl.riffpass.mensajeria.TipoEvento;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="auditoria")
public class RegistroAuditoria {
    @Id @Column(name="evento_mensaje_id") private UUID eventoMensajeId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private TipoEvento tipo;
    @Column(length=80) private String origen;
    @Column(name="concierto_id") private UUID conciertoId;
    @Column(name="ocurrido_en", nullable=false) private Instant ocurridoEn;
    @Column(name="registrado_en", nullable=false) private Instant registradoEn;
    @Column(nullable=false, length=4000) private String datos;
    protected RegistroAuditoria() {}
    public RegistroAuditoria(UUID eventoMensajeId, TipoEvento tipo, String origen, UUID conciertoId,
                             Instant ocurridoEn, String datos) {
        this.eventoMensajeId = eventoMensajeId; this.tipo = tipo; this.origen = origen; this.conciertoId = conciertoId;
        this.ocurridoEn = ocurridoEn; this.datos = datos; registradoEn = Instant.now();
    }
}
