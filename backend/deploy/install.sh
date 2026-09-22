#!/usr/bin/env bash
# Ejecutar en EC2 Amazon Linux, desde la carpeta backend: sudo bash deploy/install.sh
set -euo pipefail
if [[ $EUID -ne 0 ]]; then
  echo "Ejecuta con sudo."
  exit 1
fi
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for service in eventos entradas; do
  test -f "$repo_dir/riffpass-$service-service/target/riffpass-$service-service-1.0.0.jar" || {
    echo "Falta el JAR de $service. Compila o copia los JAR antes de instalar."
    exit 1
  }
done
id riffpass >/dev/null 2>&1 || useradd --system --shell /sbin/nologin riffpass
install -d -m 755 /opt/riffpass /etc/riffpass
for service in eventos entradas; do
  install -m 644 "$repo_dir/riffpass-$service-service/target/riffpass-$service-service-1.0.0.jar" "/opt/riffpass/riffpass-$service-service.jar"
  if [[ ! -e "/etc/riffpass/$service.env" ]]; then
    install -m 600 "$repo_dir/deploy/$service.env.example" "/etc/riffpass/$service.env"
  fi
done
install -m 644 "$repo_dir/deploy/riffpass@.service" /etc/systemd/system/riffpass@.service
systemctl daemon-reload
echo "Instalado. Completa /etc/riffpass/eventos.env y entradas.env e instala el certificado RDS."
echo "Después: sudo systemctl enable --now riffpass@eventos riffpass@entradas"
