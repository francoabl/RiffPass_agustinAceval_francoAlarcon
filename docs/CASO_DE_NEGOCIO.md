# Caso de negocio - Pedidos360

## 1. Contexto
Una pyme chilena de venta de articulos tecnologicos (perifericos, accesorios) recibe hoy sus pedidos por WhatsApp, correo y planilla compartida. Nadie tiene certeza del stock real, los precios se digitan a mano y no queda registro de quien pidio que ni en que estado va cada pedido.

## 2. Problema
- Pedidos que se venden dos veces por falta de control de stock.
- Errores de precio y de total al calcular manualmente.
- Clientes que preguntan "donde va mi pedido" y nadie sabe responder.
- Cualquier empleado puede modificar la planilla: no hay control de accesos ni trazabilidad.

## 3. Solucion: Pedidos360
Plataforma web con acceso autenticado donde los clientes arman y siguen sus pedidos, y los administradores gestionan catalogo, stock y despacho. Se construye como microservicios en la nube (AWS) para poder escalar y desplegar cada parte por separado.

## 4. Actores
| Actor | Como se identifica | Que puede hacer |
|---|---|---|
| **Cliente** | Usuario Cognito sin grupo | Ver catalogo, crear pedidos, ver y cancelar (en estado CREADO) sus pedidos |
| **Administrador** | Usuario Cognito en el grupo `ADMIN` | Todo lo anterior + gestionar productos, ver todos los pedidos y usuarios, avanzar el estado de los pedidos |

## 5. Reglas de negocio
| Codigo | Regla | Donde se aplica |
|---|---|---|
| RN-01 | Solo usuarios autenticados con un JWT valido de Cognito (firma, vigencia, issuer y audience) acceden a cualquier endpoint. | Los 3 microservicios (401) |
| RN-02 | Solo un ADMIN crea, modifica o desactiva productos, lista usuarios y cambia el estado de pedidos. | `@PreAuthorize` (403) |
| RN-03 | El total de un pedido lo calcula el servidor con el precio vigente; el cliente no envia precios. | pedidos-service |
| RN-04 | No se puede pedir mas unidades que el stock disponible; el stock se descuenta al crear el pedido. | productos-service (409) |
| RN-05 | Un pedido debe tener al menos un producto y cantidades >= 1. | Validacion (400) |
| RN-06 | Estados: CREADO -> CONFIRMADO -> ENVIADO. Un pedido ENVIADO o CANCELADO no cambia mas. | pedidos-service (409) |
| RN-07 | Un cliente solo puede cancelar su pedido mientras esta CREADO; el ADMIN tambien desde CONFIRMADO. | pedidos-service |
| RN-08 | Al cancelar un pedido, las unidades vuelven al stock. | pedidos-service -> productos-service |
| RN-09 | Un cliente solo ve sus propios pedidos; el ADMIN ve todos. | pedidos-service (403) |
| RN-10 | El pedido guarda nombre y precio del producto al momento de la compra (historial inmutable). | `DetallePedido` |
| RN-11 | Los productos no se borran: se desactivan, para conservar el historial de pedidos. | productos-service |

## 6. Alcance de la Evaluacion Parcial 1
Incluido: login/registro con Cognito (Amplify), rutas protegidas, envio del JWT a la API, validacion del JWT en el backend con autorizacion por rol, persistencia en base de datos cloud (RDS MySQL), despliegue en EC2 detras de API Gateway.
Fuera de alcance por ahora: pagos, notificaciones por correo, seguimiento de despacho con courier.

## 7. Modelo de datos
- `usuarios` (id, cognito_sub, email, nombre, rol, creado_en)
- `productos` (id, nombre, descripcion, precio, stock, activo)
- `pedidos` (id, cliente_sub, cliente_email, fecha, estado, total)
- `detalles_pedido` (id, pedido_id, producto_id, nombre_producto, cantidad, precio_unitario)
