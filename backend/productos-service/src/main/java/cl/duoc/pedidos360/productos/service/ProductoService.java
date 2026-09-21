package cl.duoc.pedidos360.productos.service;

import cl.duoc.pedidos360.productos.exception.RecursoNoEncontradoException;
import cl.duoc.pedidos360.productos.exception.StockInsuficienteException;
import cl.duoc.pedidos360.productos.model.Producto;
import cl.duoc.pedidos360.productos.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductoService {

    private final ProductoRepository repository;

    public ProductoService(ProductoRepository repository) {
        this.repository = repository;
    }

    public List<Producto> listarActivos() {
        return repository.findByActivoTrue();
    }

    public Producto obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + id + " no existe"));
    }

    @Transactional
    public Producto crear(Producto p) {
        p.setId(null);
        return repository.save(p);
    }

    @Transactional
    public Producto actualizar(Long id, Producto datos) {
        Producto p = obtener(id);
        p.setNombre(datos.getNombre());
        p.setDescripcion(datos.getDescripcion());
        p.setPrecio(datos.getPrecio());
        p.setStock(datos.getStock());
        p.setActivo(datos.isActivo());
        return repository.save(p);
    }

    /** Baja logica: el producto deja de listarse pero se conserva para pedidos historicos. */
    @Transactional
    public void desactivar(Long id) {
        Producto p = obtener(id);
        p.setActivo(false);
        repository.save(p);
    }

    /** Devuelve unidades al stock (cuando se cancela un pedido). */
    @Transactional
    public Producto liberar(Long id, int cantidad) {
        Producto p = obtener(id);
        p.setStock(p.getStock() + cantidad);
        return repository.save(p);
    }

    /** Regla de negocio: no se puede reservar mas unidades que el stock disponible. */
    @Transactional
    public Producto reservar(Long id, int cantidad) {
        Producto p = obtener(id);
        if (!p.isActivo()) {
            throw new StockInsuficienteException("Producto " + id + " no esta disponible");
        }
        if (p.getStock() < cantidad) {
            throw new StockInsuficienteException(
                    "Stock insuficiente para '" + p.getNombre() + "': disponible " + p.getStock());
        }
        p.setStock(p.getStock() - cantidad);
        return repository.save(p);
    }
}
