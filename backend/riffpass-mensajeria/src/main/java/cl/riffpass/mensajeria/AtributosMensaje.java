package cl.riffpass.mensajeria;

/** Nombres de los atributos SNS/SQS que viajan junto al cuerpo del mensaje. */
public final class AtributosMensaje {
    private AtributosMensaje() {}
    /** Atributo evaluado por las filter policies. */
    public static final String TIPO = "tipo";
    public static final String ORIGEN = "origen";
    /** Atributos que agrega el consumidor cuando envía un mensaje no recuperable a la DLQ. */
    public static final String CAUSA = "causa";
    public static final String INTENTO = "intento";
    public static final String COLA_ORIGEN = "colaOrigen";
    public static final String MENSAJE_ORIGINAL = "mensajeOriginalId";
    /** Veces que el administrador devolvió el mensaje desde la DLQ a su cola. */
    public static final String REPROCESOS = "reprocesos";
}
