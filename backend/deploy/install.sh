#!/usr/bin/env bash
# Ejecutar en EC2 Amazon Linux, desde la carpeta backend: sudo bash deploy/install.sh
set -euo pipefail
if [[ $EUID -ne 0 ]]; then
  echo "Ejecuta con sudo."
  exit 1
fi
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
services=(eventos entradas procesamiento mensajeria-admin)
for service in "${services[@]}"; do
  test -f "$repo_dir/riffpass-$service-service/target/riffpass-$service-service-1.0.0.jar" || {
    echo "Falta el JAR de $service. Compila o copia los JAR antes de instalar."
    exit 1
  }
done
id riffpass >/dev/null 2>&1 || useradd --system --shell /sbin/nologin riffpass
install -d -m 755 /opt/riffpass /etc/riffpass
for service in "${services[@]}"; do
  install -m 644 "$repo_dir/riffpass-$service-service/target/riffpass-$service-service-1.0.0.jar" "/opt/riffpass/riffpass-$service-service.jar"
done
# El consumidor tiene dos réplicas con su propio archivo de variables.
for env in eventos entradas mensajeria-admin procesamiento-1 procesamiento-2; do
  if [[ ! -e "/etc/riffpass/$env.env" ]]; then
    install -m 600 "$repo_dir/deploy/$env.env.example" "/etc/riffpass/$env.env"
  fi
done
install -m 644 "$repo_dir/deploy/riffpass@.service" /etc/systemd/system/riffpass@.service
install -m 644 "$repo_dir/deploy/riffpass-procesamiento@.service" /etc/systemd/system/riffpass-procesamiento@.service
systemctl daemon-reload
echo "Instalado. Completa los archivos de /etc/riffpass e instala el certificado RDS."
echo "Orden de inicio (el administrador declara la topología antes de que arranquen los consumidores):"
echo "  sudo systemctl enable --now riffpass@mensajeria-admin"
echo "  sudo systemctl enable --now riffpass@eventos riffpass@entradas"
echo "  sudo systemctl enable --now riffpass-procesamiento@1 riffpass-procesamiento@2"
