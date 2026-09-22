package cl.riffpass.entradas;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EntradaRepository extends JpaRepository<Entrada, UUID> {
    boolean existsByEventoIdAndTitularSub(UUID eventoId, String titularSub);
    List<Entrada> findByTitularSubOrderByEmitidaEnDesc(String titularSub, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Entrada e where e.codigo = :codigo")
    Optional<Entrada> bloquearCodigo(@Param("codigo") UUID codigo);
}
