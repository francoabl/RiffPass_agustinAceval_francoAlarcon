package cl.duoc.pedidos360.usuarios.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Perfil local de un usuario autenticado en Cognito (se crea en su primer acceso). */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Claim "sub" de Cognito: identificador inmutable del usuario. */
    @Column(nullable = false, unique = true, length = 64)
    private String cognitoSub;

    @Column(length = 160)
    private String email;

    @Column(length = 120)
    private String nombre;

    /** ADMIN o CLIENTE (derivado del grupo de Cognito). */
    @Column(nullable = false, length = 20)
    private String rol;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCognitoSub() { return cognitoSub; }
    public void setCognitoSub(String cognitoSub) { this.cognitoSub = cognitoSub; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
}
