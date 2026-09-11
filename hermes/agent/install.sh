#!/usr/bin/env bash
# Hermes Agent kurulum betiği — Ubuntu/Debian tabanlı VPS (Hostinger, Contabo, Hetzner…)
#
#   curl -fsSL https://github.com/fatsar/fatsar/releases/download/hermes-v1.0.0/install.sh | sudo bash
#
# Yaptıkları: hermes kullanıcısı açar, /opt/hermes'e kurar, systemd servisi tanımlar,
# erişim anahtarını üretir ve telefona yapıştırılacak eşleştirme kodunu yazdırır.
set -euo pipefail

DIR="${HERMES_DIR:-/opt/hermes}"
PORT="${HERMES_PORT:-8713}"
RAW_URL="${HERMES_SOURCE:-https://github.com/fatsar/fatsar/releases/download/hermes-v1.0.0/hermes_agent.py}"
SERVICE=/etc/systemd/system/hermes-agent.service

if [ "$(id -u)" -ne 0 ]; then
  echo "Bu betik root yetkisi ister:  sudo bash install.sh" >&2
  exit 1
fi

command -v python3 >/dev/null || { echo "python3 bulunamadı. Kurun: apt install -y python3" >&2; exit 1; }
echo "==> Python: $(python3 --version)"

id -u hermes >/dev/null 2>&1 || useradd --system --home "$DIR" --shell /usr/sbin/nologin hermes
mkdir -p "$DIR/data"

if [ -f "./hermes_agent.py" ]; then
  cp ./hermes_agent.py "$DIR/hermes_agent.py"
  echo "==> Yerel dosyadan kuruldu."
else
  echo "==> İndiriliyor: $RAW_URL"
  curl -fsSL "$RAW_URL" -o "$DIR/hermes_agent.py"
fi

touch /var/log/hermes-agent.log
chown -R hermes:hermes "$DIR" /var/log/hermes-agent.log
chmod 750 "$DIR"

cat > "$SERVICE" <<UNIT
[Unit]
Description=Hermes Agent (Android bot konsolu sunucusu)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=hermes
Group=hermes
WorkingDirectory=$DIR
ExecStart=/usr/bin/python3 $DIR/hermes_agent.py --host 0.0.0.0 --port $PORT --data-dir $DIR/data
Restart=always
RestartSec=5
StandardOutput=append:/var/log/hermes-agent.log
StandardError=append:/var/log/hermes-agent.log
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true
ReadWritePaths=$DIR /var/log/hermes-agent.log

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now hermes-agent
sleep 2

if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
  ufw allow "$PORT"/tcp >/dev/null 2>&1 || true
  echo "==> Güvenlik duvarında $PORT/tcp açıldı."
fi

PUBLIC_IP="$(curl -fsS --max-time 5 https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')"
echo
echo "==> Servis durumu:"
systemctl --no-pager --lines=0 status hermes-agent | head -4 || true
echo
echo "==> TELEFONA YAPIŞTIRILACAK EŞLEŞTİRME KODU:"
sudo -u hermes python3 "$DIR/hermes_agent.py" --data-dir "$DIR/data" \
  --public-url "http://$PUBLIC_IP:$PORT" --show-pairing
echo
echo "Kabuk (shell) aracını açmak isterseniz:"
echo "  sudo -u hermes python3 $DIR/hermes_agent.py --data-dir $DIR/data --set allow_shell=true"
echo "  sudo systemctl restart hermes-agent"
echo
echo "Model sağlayıcı anahtarı eklemek için örnek:"
echo "  sudo -u hermes python3 $DIR/hermes_agent.py --data-dir $DIR/data --set backends.xai.api_key=xai-XXXX"
echo "  sudo -u hermes python3 $DIR/hermes_agent.py --data-dir $DIR/data --set default_backend=xai"
echo "  sudo systemctl restart hermes-agent"
echo
echo "Günlükler:  tail -f /var/log/hermes-agent.log"
