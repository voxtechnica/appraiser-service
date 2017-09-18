This document describes how to set up an [Elasticsearch](https://www.elastic.co/products/elasticsearch) cluster. Ideally, the Elasticsearch cluster would be running on dedicated EC2 instances on AWS (three nodes, one in each availability zone, using perhaps m4 or i3 series instances). However, if the Cassandra cluster has capacity, we can probably get away with running both Cassandra and Elasticsearch on the database nodes. If we grow to beyond 6 Cassandra nodes, then we'll want to move Elasticsearch onto its own cluster, because we currently use only 6 shards. These instructions assume that we're running Elasticsearch on an existing Cassandra cluster.

**References**:
* [Elasticsearch Apt Repository](https://www.elastic.co/guide/en/elasticsearch/reference/current/deb.html)
* [Elasticsearch Configuration](https://www.elastic.co/guide/en/elasticsearch/reference/current/important-settings.html)
* [Elasticsearch Reference](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)
* [Elasticsearch: The Definitive Guide](https://www.elastic.co/guide/en/elasticsearch/guide/current/index.html) (probably out-dated; written for version 2.x)
* [Elasticsearch Java API](https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/index.html)

## Installation and Configuration

For a single-node installation (e.g. an all-in-one apps and database box), no special configuration is needed. Elasticsearch will work fine as it comes. These instructions assume that Elasticsearch is being installed on a cluster with ephemeral drives.

Set up the Elasticsearch Repository:

```bash
# Elasticsearch 5.x Repository
wget -qO - https://artifacts.elastic.co/GPG-KEY-elasticsearch | sudo apt-key add -
echo "deb https://artifacts.elastic.co/packages/5.x/apt stable main" | sudo tee -a /etc/apt/sources.list.d/elasticsearch.list
```

Install Elasticsearch:

```bash
sudo apt-get update
sudo apt-get install elasticsearch
```

Move the data folder to the ephemeral drive:

```bash
# Delete the data if it was started before configuration:
sudo rm -Rf /var/lib/elasticsearch/*
# Move the data folder to the ephemeral drive:
sudo mv /var/lib/elasticsearch /data/
sudo ln -s /data/elasticsearch /var/lib/elasticsearch
```

Configure Elasticsearch:

By default, clients talk to the cluster on port 9200, and cluster nodes talk to each other on port 9300. Make sure they're open in the Security Group!

File: `/etc/elasticsearch/elasticsearch.yml`
* `cluster.name: production`
* `node.name: db1a` (substitute the actual hostname; it must be unique)
* `node.rack: us-west-2a` (substitute with actual, including availability zone)
* `network.host: 10.1.21.11` (substitute the actual IP address)
* `http.port: 9200`
* `discovery.zen.ping.unicast.hosts: ["db1a", "db1b", "db1c"]` (seed nodes)
* `discovery.zen.minimum_master_nodes: 2` (need quorum > 50%; here assuming 3 nodes total)

Enable the systemd service and start it:

```bash
sudo systemctl daemon-reload
sudo systemctl enable elasticsearch.service
sudo systemctl start elasticsearch.service
```

Check to see if it’s running (substitute `localhost` with the machine's IP address):

```bash
curl -s 'http://localhost:9200'
curl -s 'http://localhost:9200/_cluster/health?pretty=true'
```

## Backup/Restore

There's no real need to provide for regular backups, since we can create a simple task in the Dropwizard application to (re)populate the Elasticsearch index from information in the Cassandra database. Effectively, Cassandra is the backup. This might look something like this:

```bash
curl -X POST http://localhost:8081/tasks/index-all
```

The command is idempotent, and can be re-run at any time if we suspect that the index is missing any current data.
