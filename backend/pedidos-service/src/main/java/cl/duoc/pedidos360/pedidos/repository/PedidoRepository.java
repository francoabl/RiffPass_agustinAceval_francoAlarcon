package cl.duoc.pedidos360.pedidos.repository;

import cl.duoc.pedidos360.pedidos.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByClienteSubOrderByFechaDesc(String clienteSub);
}
