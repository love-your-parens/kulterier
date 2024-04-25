#!/usr/bin/env bash
set -x
set -e

BIFF_PROFILE=${1:-prod}
CLJ_VERSION=1.11.1.1165
TRENCH_VERSION=0.4.0
TRENCH_FILE=trenchman_${TRENCH_VERSION}_linux_amd64.tar.gz

echo waiting for apt to finish
while (ps aux | grep [a]pt); do
  sleep 3
done

# Dependencies
apt-get update
apt-get upgrade
apt-get -y install default-jre rlwrap ufw git snapd
bash < <(curl -s https://download.clojure.org/install/linux-install-$CLJ_VERSION.sh)
bash < <(curl -s https://raw.githubusercontent.com/babashka/babashka/master/install)
wget https://github.com/athos/trenchman/releases/download/v$TRENCH_VERSION/$TRENCH_FILE
mkdir .trench_tmp
tar -xf $TRENCH_FILE --directory .trench_tmp
mv .trench_tmp/trench /usr/local/bin/
rm -rf $TRENCH_FILE .trench_tmp

# Non-root user
useradd -m kulterier-app
mkdir -m 700 -p /home/kulterier-app/.ssh
cp /root/.ssh/authorized_keys /home/kulterier-app/.ssh
chown -R kulterier-app:kulterier-app /home/kulterier-app/.ssh

# Git deploys - only used if you don't have rsync on your machine
set_up_app () {
  cd
  mkdir repo.git
  cd repo.git
  git init --bare
  cat > hooks/post-receive << EOD
#!/usr/bin/env bash
git --work-tree=/home/kulterier-app --git-dir=/home/kulterier-app/repo.git checkout -f
EOD
  chmod +x hooks/post-receive
}
sudo -u kulterier-app bash -c "$(declare -f set_up_app); set_up_app"

# Systemd service
cat > /etc/systemd/system/kulterier-app.service << EOD
[Unit]
Description=kulterier-app
StartLimitIntervalSec=500
StartLimitBurst=5

[Service]
User=kulterier-app
Restart=on-failure
RestartSec=5s
Environment="BIFF_PROFILE=$BIFF_PROFILE"
WorkingDirectory=/home/kulterier-app
ExecStart=/bin/sh -c "mkdir -p target/resources; clj -M:prod"

[Install]
WantedBy=multi-user.target
EOD
systemctl enable kulterier-app
cat > /etc/systemd/journald.conf << EOD
[Journal]
Storage=persistent
EOD
systemctl restart systemd-journald
cat > /etc/sudoers.d/restart-kulterier-app << EOD
kulterier-app ALL= NOPASSWD: /bin/systemctl reset-failed kulterier-app.service
kulterier-app ALL= NOPASSWD: /bin/systemctl restart kulterier-app
kulterier-app ALL= NOPASSWD: /usr/bin/systemctl reset-failed kulterier-app.service
kulterier-app ALL= NOPASSWD: /usr/bin/systemctl restart kulterier-app
EOD
chmod 440 /etc/sudoers.d/restart-kulterier-app

# Firewall
ufw allow OpenSSH
ufw --force enable

# Web dependencies
apt-get -y install nginx
snap install core
snap refresh core
snap install --classic certbot
ln -s /snap/bin/certbot /usr/bin/certbot

# Nginx
rm /etc/nginx/sites-enabled/default
cat > /etc/nginx/sites-available/kulterier-app << EOD
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
    root /home/kulterier-app/target/resources/public;
    location / {
        try_files \$uri \$uri/index.html @resources;
    }
    location @resources {
        root /home/kulterier-app/resources/public;
        try_files \$uri \$uri/index.html @proxy;
    }
    location @proxy {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header X-Real-IP \$remote_addr;
    }
}
EOD
ln -s /etc/nginx/sites-{available,enabled}/kulterier-app

# Firewall
ufw allow "Nginx Full"

# Let's encrypt
certbot --nginx

# App dependencies
# If you need to install additional packages for your app, you can do it here.
# apt-get -y install ...
