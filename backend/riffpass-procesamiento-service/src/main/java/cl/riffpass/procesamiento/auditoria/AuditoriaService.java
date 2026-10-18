package cl.riffpass.procesamiento.auditoria;

import cl.riffpass.mensajeria.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bitácora de todos los hechos de negocio y estadística simple por concierto. */
@Service
public class AuditoriaService {
    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);
    private final RegistroAuditoriaRepository registros;
    private final ObjectMapper mapper;
    public AuditoriaService(RegistroAuditoriaRepository registros, ObjectMapper mapper) {
        this.registros = registros; this.mapper = mapper;
    }

    @Transactional
    public void registrar(EventoMensaje evento) {
        if (registros.existsById(evento.id())) return;
        UUID conciertoId = UUID.fromString(evento.dato("eventoId"));
        registros.save(new RegistroAuditoria(evento.id(), evento.tipo(), evento.origen(), conciertoId,
                evento.ocurridoEn(), json(evento)));
        long emitidas = registros.countByConciertoIdAndTipo(conciertoId, TipoEvento.ENTRADA_EMITIDA);
        long validadas = registros.countByConciertoIdAndTipo(conciertoId, TipoEvento.ENTRADA_VALIDADA);
        log.info("AUDITORIA tipo={} concierto={} emitidas={} validadas={}", evento.tipo(), conciertoId, emitidas, validadas);
    }

    private String json(EventoMensaje evento) {
        try { return mapper.writeValueAsString(evento.datos()); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Datos no serializables", ex); }
    }
}
