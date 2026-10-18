package cl.riffpass.procesamiento.tickets;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Comprobante digital generado de forma asíncrona para una entrada emitida. */
@Entity
@Table(name="tickets")
public class Ticket {
    @Id @Column(name="entrada_id") private UUID entradaId;
    @Column(name="evento_id", nullable=false) private UUID eventoId;
    @Column(name="titular_sub", nullable=false, length=128) private String titularSub;
    @Column(nullable=false, unique=true) private UUID codigo;
    @Column(nullable=false, length=1000) private String contenido;
    @Column(name="generado_en", nullable=false) private Instant generadoEn;
    protected Ticket() {}
    public Ticket(UUID entradaId, UUID eventoId, String titularSub, UUID codigo, String contenido) {
        this.entradaId = entradaId; this.eventoId = eventoId; this.titularSub = titularSub;
        this.codigo = codigo; this.contenido = contenido; generadoEn = Instant.now();
    }
    public UUID getEntradaId() { return entradaId; }
    public UUID getCodigo() { return codigo; }
}
