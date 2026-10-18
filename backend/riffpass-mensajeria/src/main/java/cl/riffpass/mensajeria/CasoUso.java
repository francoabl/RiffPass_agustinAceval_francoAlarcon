package cl.riffpass.mensajeria;

/** Rutas de mensajería del sistema. Cada una tiene su propia cola, DLQ, suscripción y consumidor. */
public enum CasoUso { TICKETS, NOTIFICACIONES, AUDITORIA }
