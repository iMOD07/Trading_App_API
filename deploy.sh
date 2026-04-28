#!/bin/bash
# ============================================================
# Trading Bot - Deploy Script
# Build locally with Maven, upload JAR, restart systemd service
# ============================================================

set -e

# ==============================
# Configuration
# ==============================
PROJECT_DIR="/Users/abdullahali/Desktop/myProject/trading"
JAR_NAME="trading-1.0.0.jar"
LOCAL_JAR="$PROJECT_DIR/target/$JAR_NAME"

SSH_KEY="/Users/abdullahali/Desktop/myProject/AWS/ibkr_key.pem"   # <-- update path
SERVER_USER="ubuntu"
SERVER_IP="98.85.235.215"                                       # <-- update IP
REMOTE_DIR="/opt/trading-bot"
SERVICE_NAME="trading-bot.service"
APP_PORT="8080"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

log()  { echo -e "${GREEN}[$(date +%H:%M:%S)]${NC} $1"; }
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
err()  { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

SSH_CMD="ssh -i $SSH_KEY -o StrictHostKeyChecking=no $SERVER_USER@$SERVER_IP"

# ==============================
# 1. Build project
# ==============================
log "🔨 Building project with Maven..."
cd "$PROJECT_DIR"
./mvnw clean package -DskipTests

[ -f "$LOCAL_JAR" ] || err "JAR file not found: $LOCAL_JAR"

JAR_SIZE=$(du -h "$LOCAL_JAR" | cut -f1)
log "✅ Build success — JAR size: $JAR_SIZE"

# ==============================
# 2. Backup current JAR on server
# ==============================
log "📦 Backing up current JAR on server..."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
$SSH_CMD "sudo cp $REMOTE_DIR/$JAR_NAME $REMOTE_DIR/$JAR_NAME.bak_$TIMESTAMP 2>/dev/null || true"
info "Backup: $JAR_NAME.bak_$TIMESTAMP"

# ==============================
# 3. Stop service
# ==============================
log "🛑 Stopping $SERVICE_NAME..."
$SSH_CMD "sudo systemctl stop $SERVICE_NAME || true"

# ==============================
# 4. Upload new JAR
# ==============================
log "⬆️  Uploading new JAR to $REMOTE_DIR ..."
scp -i "$SSH_KEY" -o StrictHostKeyChecking=no "$LOCAL_JAR" "$SERVER_USER@$SERVER_IP:/tmp/$JAR_NAME"
$SSH_CMD "sudo mv /tmp/$JAR_NAME $REMOTE_DIR/$JAR_NAME && sudo chown ubuntu:ubuntu $REMOTE_DIR/$JAR_NAME"

# ==============================
# 5. Start service
# ==============================
log "🚀 Starting $SERVICE_NAME..."
$SSH_CMD "sudo systemctl start $SERVICE_NAME"

# ==============================
# 6. Health checks
# ==============================
log "⏳ Waiting for API to boot..."
sleep 10

log "🔍 Checking service status..."
STATUS=$($SSH_CMD "sudo systemctl is-active $SERVICE_NAME" || echo "failed")
if [ "$STATUS" = "active" ]; then
    log "✅ Service is active"
else
    err "Service failed. Run: $SSH_CMD 'sudo journalctl -u $SERVICE_NAME -n 80'"
fi

log "🔍 Checking port $APP_PORT..."
PORT_UP=false
for i in {1..6}; do
    PORT_CHECK=$($SSH_CMD "sudo ss -tlnp | grep :$APP_PORT || true")
    if [ -n "$PORT_CHECK" ]; then
        PORT_UP=true
        break
    fi
    info "Port not listening yet... retry $i/6"
    sleep 10
done

if [ "$PORT_UP" = true ]; then
    log "✅ Port $APP_PORT is listening"
else
    warn "Port $APP_PORT is not listening after 60s. Check logs."
fi

# ==============================
# 7. Clean up old backups (keep last 5)
# ==============================
log "🧹 Cleaning old backups (keeping last 5)..."
$SSH_CMD "cd $REMOTE_DIR && ls -t $JAR_NAME.bak_* 2>/dev/null | tail -n +6 | xargs -r sudo rm -f"

# ==============================
# Done
# ==============================
echo ""
log "🎉 Deployment complete!"
echo ""
echo "📋 Useful commands:"
echo "  Logs:    $SSH_CMD 'sudo journalctl -u $SERVICE_NAME -f'"
echo "  Status:  $SSH_CMD 'sudo systemctl status $SERVICE_NAME'"
echo "  Restart: $SSH_CMD 'sudo systemctl restart $SERVICE_NAME'"
echo ""