package cl.riffpass.entradas;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="entradas", uniqueConstraints=@UniqueConstraint(columnNames={"evento_id","titular_sub"}))
public class Entrada {
    @Id private UUID id;
    @Column(name="evento_id", nullable=false) private UUID eventoId;
    @Column(name="titular_sub", nullable=false, length=128) private String titularSub;
    @Column(nullable=false, unique=true) private UUID codigo;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private Estado estado;
    @Column(name="emitida_en", nullable=false) private Instant emitidaEn;
    @Column(name="utilizada_en") private Instant utilizadaEn;
    public enum Estado { EMITIDA, UTILIZADA }
    protected Entrada() {}
    public Entrada(UUID eventoId, String titularSub) {
        this.id = UUID.randomUUID(); this.eventoId = eventoId; this.titularSub = titularSub;
        codigo = UUID.randomUUID(); estado = Estado.EMITIDA; emitidaEn = Instant.now();
    }
    public UUID getEventoId() { return eventoId; }
    public Estado getEstado() { return estado; }
    public void utilizar() { estado = Estado.UTILIZADA; utilizadaEn = Instant.now(); }
    public EntradaDtos.Respuesta respuesta() {
        return new EntradaDtos.Respuesta(id, eventoId, codigo, estado, emitidaEn, utilizadaEn);
    }
}
