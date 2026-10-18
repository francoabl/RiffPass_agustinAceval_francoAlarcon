package cl.riffpass.procesamiento.auditoria;

import cl.riffpass.mensajeria.TipoEvento;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, UUID> {
    long countByConciertoIdAndTipo(UUID conciertoId, TipoEvento tipo);
}
