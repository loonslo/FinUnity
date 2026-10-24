# Private server run

This compose profile keeps the single-user prototype private. The published host port is bound to `127.0.0.1:8011`; do not put it behind a public reverse proxy or open the port in a firewall. The app has no login or multi-user isolation.

## Deploy

Build the frontend and confirm the source state before packaging. From the repository root, create a source archive without local data:

```powershell
git archive --format=tar --output=finunity-prototype.tar HEAD -- .dockerignore Dockerfile.prototype backend frontend deploy/compose.prototype.yaml
scp finunity-prototype.tar <ssh-user>@<server-host>:finunity-prototype.tar
ssh <ssh-user>@<server-host> 'mkdir -p "$HOME/finunity-prototype" && tar -xf "$HOME/finunity-prototype.tar" -C "$HOME/finunity-prototype" && rm "$HOME/finunity-prototype.tar"'
```

On the server, prepare the isolated data directory and start the container:

```sh
cd "$HOME/finunity-prototype"
mkdir -p deploy/prototype-data
sudo chown -R 10001:10001 deploy/prototype-data
sudo docker compose -f deploy/compose.prototype.yaml up -d --build
sudo docker compose -f deploy/compose.prototype.yaml ps
curl --fail http://127.0.0.1:8011/api/health
```

The SQLite file lives in `deploy/prototype-data/finunity-prototype.db`, separate from FinUnityServer. The volume persists across image updates and container recreation. Do not remove it when updating the image.

## Connect from the owner workstation

Open an SSH tunnel and keep that terminal open:

```sh
ssh -N -L 18011:127.0.0.1:8011 <ssh-user>@<server-host>
```

Then open `http://127.0.0.1:18011`. The local port can be changed if it is already in use. No public hostname, firewall rule, or DNS change is needed.

## Update or stop

Repeat the archive and copy steps with the new commit, then run `sudo docker compose -f deploy/compose.prototype.yaml up -d --build` from the checkout. To stop the service without deleting its database, run `sudo docker compose -f deploy/compose.prototype.yaml down`.
