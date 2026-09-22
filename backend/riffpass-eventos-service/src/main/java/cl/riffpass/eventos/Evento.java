package cl.riffpass.eventos;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eventos")
public class Evento {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String nombre;
    @Column(nullable = false, length = 120) private String artista;
    @Column(nullable = false, length = 160) private String recinto;
    @Column(nullable = false, length = 100) private String ciudad;
    @Column(nullable = false, length = 2000) private String descripcion;
    @Column(nullable = false) private Instant fecha;
    @Column(name = "organizador_sub", nullable = false, length = 128) private String organizadorSub;
    @Column(name = "creado_en", nullable = false) private Instant creadoEn;
    protected Evento() {}
    public Evento(EventoDtos.Crear request, String owner) {
        id = UUID.randomUUID();
        nombre = request.nombre().trim(); artista = request.artista().trim();
        recinto = request.recinto().trim(); ciudad = request.ciudad().trim();
        descripcion = request.descripcion().trim(); fecha = request.fecha();
        organizadorSub = owner; creadoEn = Instant.now();
    }
    public void editar(EventoDtos.Editar request) {
        nombre = request.nombre().trim(); descripcion = request.descripcion().trim();
    }
    public UUID getId() { return id; }
    public String getOrganizadorSub() { return organizadorSub; }
    public Instant getFecha() { return fecha; }
    public EventoDtos.Respuesta respuesta() {
        return new EventoDtos.Respuesta(id, nombre, artista, recinto, ciudad, descripcion, fecha, organizadorSub, creadoEn);
    }
}
