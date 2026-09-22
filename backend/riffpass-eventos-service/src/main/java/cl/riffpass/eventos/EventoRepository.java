package cl.riffpass.eventos;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EventoRepository extends JpaRepository<Evento, UUID> {}
