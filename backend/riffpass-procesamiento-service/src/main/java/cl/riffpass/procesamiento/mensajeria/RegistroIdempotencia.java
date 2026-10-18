package cl.riffpass.procesamiento.mensajeria;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RegistroIdempotencia {
    private final JdbcTemplate jdbc;
    public RegistroIdempotencia(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /**
     * Debe ejecutarse dentro de la transacción del procesamiento. Si otra réplica ya registró el evento,
     * PostgreSQL espera su commit y luego no inserta nada: devuelve false y el mensaje se trata como duplicado.
     */
    public boolean registrar(String cola, UUID eventoId, String instancia) {
        return jdbc.update("INSERT INTO mensajes_procesados(cola, evento_id, procesado_en, instancia) "
                + "VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING", cola, eventoId, Timestamp.from(Instant.now()), instancia) == 1;
    }
}
