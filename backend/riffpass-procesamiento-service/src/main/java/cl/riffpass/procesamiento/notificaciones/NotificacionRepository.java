package cl.riffpass.procesamiento.notificaciones;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {}
