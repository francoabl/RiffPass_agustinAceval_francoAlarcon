package cl.riffpass.entradas;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InventarioRepository extends JpaRepository<Inventario, UUID> {
    @Modifying
    @Query(value="INSERT INTO inventarios(evento_id, capacidad, emitidas) VALUES (:id, :capacidad, 0)", nativeQuery=true)
    void insertar(@Param("id") UUID id, @Param("capacidad") int capacidad);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventario i where i.eventoId = :id")
    Optional<Inventario> bloquear(@Param("id") UUID id);
}
