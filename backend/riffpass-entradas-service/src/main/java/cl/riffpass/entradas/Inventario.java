package cl.riffpass.entradas;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="inventarios")
public class Inventario {
    @Id @Column(name="evento_id") private UUID eventoId;
    @Column(nullable=false) private int capacidad;
    @Column(nullable=false) private int emitidas;
    protected Inventario() {}
    public Inventario(UUID eventoId, int capacidad) { this.eventoId = eventoId; this.capacidad = capacidad; }
    public int disponibles() { return capacidad - emitidas; }
    public void consumir() { emitidas++; }
    public EntradaDtos.Cupos respuesta() { return new EntradaDtos.Cupos(eventoId, capacidad, emitidas, disponibles()); }
}
