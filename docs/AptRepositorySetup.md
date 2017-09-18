This document describes how to set up a private apt repository `/srv/apt` on Ubuntu Linux, manage it with [reprepro](http://mirrorer.alioth.debian.org/), and publish it with NGINX. Application nodes will be configured to look for this repository. User circle will manage the repository so that it can update it as it completes builds.

## Signing Packages

Generate a key for signing packages:
```bash
sudo apt-get install -y gnupg rng-tools dpkg-sig
sudo vi /etc/default/rng-tools
    HRNGDEVICE=/dev/hwrng
sudo service rng-tools start

sudo su - circle
gpg --gen-key
    ...

pub   2048R/26E3F34E 2015-03-16 [expires: 2018-03-15]
      Key fingerprint = 2F1A A06B 34EE 502F 29A4  D978 774E 8CD1 26E3 F34E
uid                  Tech Support <info@voxtechnica.info>
sub   2048R/405E8C61 2015-03-16 [expires: 2018-03-15]
```

Circle's public key is now 405E8C61 and it will be used for signing packages.

## Apt Repository

Install reprepro and NGINX:
```bash
apt-get install -y reprepro nginx
```

Create a configuration folder:
```bash
sudo mkdir -p /srv/apt/conf
chown -R circle:circle /srv/apt
```

Create file `/srv/apt/conf/distributions` containing:
```
Origin: Vox Technica
Label: voxtechnica.info
Codename: dev
Architectures: noarch i386 amd64 source
Components: main
Description: Vox Technica debian packages
SignWith: 405E8C61

Origin: Vox Technica
Label: voxtechnica.info
Codename: staging
Architectures: noarch i386 amd64 source
Components: main
Description: Vox Technica debian packages
SignWith: 405E8C61

Origin: Vox Technica
Label: voxtechnica.info
Codename: staging
Architectures: noarch i386 amd64 source
Components: main
Description: Vox Technica debian packages
SignWith: 405E8C61
```

Create file `/srv/apt/conf/options` containing:
```
verbose
basedir /srv/apt
```

Create a GPG key for the repository:
```bash
gpg --armor --output /srv/apt/apt.gpg.key --export 405E8C61
```

Sign a package (such as the REST API):
```bash
dpkg-sig -k 405E8C61 --sign builder /tmp/appraisers_1.0_all.deb

Processing /tmp/appraisers_1.0_all.deb...
Signed deb /tmp/appraisers_1.0_all.deb
```

Load a Debian package, such as the REST API, to one of the environments (dev, staging, or production):
```bash
reprepro -Vb /srv/apt -C main includedeb production /tmp/appraisers_1.0_all.deb
```

List the packages in a particular section of the repository:
```bash
reprepro -Vb /srv/apt list production

production|main|amd64: appraisers 1.0
```

Remove a package from the repository:
```bash
reprepro -Vb /srv/apt remove production appraisers
```

## NGINX Configuration

Configure basic auth password for the apt repository (See: [NGINX Basic Auth Setup](https://www.digitalocean.com/community/tutorials/how-to-set-up-password-authentication-with-nginx-on-ubuntu-14-04)).
```bash
sudo sh -c "echo -n 'appraisers:' >> /etc/nginx/.htpasswd"
sudo sh -c "openssl passwd -apr1 >> /etc/nginx/.htpasswd"
```

Create configuration file `/etc/nginx/sites-available/apt-repo`:
```
# Serve apt repository packages to inside network
server {
  listen 80;
  server_name apt.voxtechnica.info;

  access_log /var/log/nginx/apt-access.log;
  error_log /var/log/nginx/apt-error.log;

  location / {
    root /srv/apt;
    index index.html;
    autoindex on;
    auth_basic "Restricted";
    auth_basic_user_file /etc/nginx/.htpasswd; 
  }

 # Provide a health check
  location /status {
    stub_status on;
    access_log off;
    auth_basic off;
    allow 10.0.0.0/8;
    allow 127.0.0.1/32;
    deny all;
  }

  location /conf {
    deny all;
  }

  location /db {
    deny all;
  }
}
```

Enable the site:
```bash
rm /etc/nginx/sites-enabled/default
ln -s /etc/nginx/sites-available/apt-repo /etc/nginx/sites-enabled/apt-repo
```

Test the NGINX configuration and reload if everything is okay:
```bash
sudo nginx -t
sudo service nginx restart
```

Verify that it's working:
```bash
curl http://localhost/status
curl http://appraisers:password@localhost
```

## Set up External Access

Although we could install an SSL/TLS certificate on the apt repository for NGINX to use, it's simpler just to create an ELB and use a free certificate provided by AWS (See: [AWS Certificate Manager](https://aws.amazon.com/certificate-manager/)).

Create/update Security Groups:
* elb-apt: allow incoming 443 from 0.0.0.0/0
* apt-repo: allow incoming 80 from 10.0.0.0/8

Create ELB 'apt-repo':
* Health check: HTTP:80/status
* Listener: forward HTTPS:443 to HTTP:80
* Certificate: use a current wildcard cert for our domain

Create a CNAME for `apt.voxtechnica.info` pointing to new ELB

Test remote access:

```bash
curl https://appraisers:password@apt.voxtechnica.info
```

## Regular System Maintenance

Set up a daily cron job to clean up `/tmp/*.deb` on the apt repository. Since reprepro does not support multiple versions of a package in the same branch at the same time, it's helpful to keep a few old packages around, but they can become excessive, because the deployment scripts do not delete the package in the temp folder.

```bash
crontab -e

# m h dom mon dow command
# Delete Debian packages older than 3 days ago
13 2 * * * find /tmp/*.deb -type f -mtime +3 -delete
```

## Using the Repository

On each machine that will use the repository, create the file `/etc/apt/sources.list.d/appraisers.list` containing something like the following, but substituting the proper environment (dev, staging, production):

```
# Appraiser Service private repository
# wget -O - -q https://appraisers:password@apt.voxtechnica.info/apt.gpg.key | apt-key add -
deb https://appraisers:password@apt.voxtechnica.info production main
```

Import the key for the repository as indicated in the commented wget command, and you can now use it normally with `apt-get update`, `apt-get install appraisers` and `apt-get upgrade`.
