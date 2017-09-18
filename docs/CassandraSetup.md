This document describes how to set up an Apache Cassandra database cluster, ensure regular backup operations, and restore from backup. Possible installation sources include [DataStax Enterprise](http://www.datastax.com/products/datastax-enterprise) (pay for OpsCenter and support) and the public [Apache Cassandra](http://cassandra.apache.org) (free and self-supported) repositories. Instructions for installing Apache Cassandra are provided here.

**References**:
* [Cassandra Download](http://cassandra.apache.org/download/)
* [Cassandra Documentation](http://cassandra.apache.org/doc/latest/getting_started/index.html)
* [Cassandra Java Client](https://github.com/datastax/java-driver)
* [Cassandra Backup/Restore (DataStax)](http://docs.datastax.com/en/cassandra/3.0/cassandra/operations/opsBackupRestore.html)

**Cluster Design**:
* **Non-production** environments: a single-node Cassandra instance works fine; it can even be installed on the same instance as a full application stack, as long as the instance has at least 4 vCPUs and 16 GB of RAM. For light-usage single-node environments, using the root EBS volume for data storage is acceptable, and no special configuration for Cassandra is required; it just works as-installed.
* **Production** environments, the minimum cluster needs at least three nodes, one in each of three availability zones. As you add capacity, add nodes in threes, one in each availability zone, supporting a replication factor of three. This means that you can lose an entire availability zone and continue operating normally, albeit at lower capacity.
* **Instance selection**: the i3 series on AWS is perfect for Cassandra. It includes sufficient RAM, CPU resources, and local SSD instance storage for data. Starting out, the i3.xlarge instance is "small", but still sufficient. The i3.2xlarge is ideal, providing a maximum operating capacity of about 1 TB of storage per server.
* **Storage**: Theoretically, per-server storage can range from 1-5 TB, but in practice, closer to 1 TB is perfect. This reduces the amount of data that needs to be moved around the cluster during repair and compaction operations -- especially when replacing a node in the cluster. Also, putting Cassandra data on a SAN, RAID, or an EBS volume is an anti-pattern. The storage network traffic competes with client traffic, any additional fail-safe redundancy provided by, say a RAID5 configuration, is unnecessary because of Cassandra's replication factor. Direct-attached storage is best.

## Set up Ubuntu Linux Server

Create a new AWS EC2 instance using the current Ubuntu Linux OS image: 64-bit, instance storage, and i3.xlarge or i3.2xlarge instance type. Use the security group DB. Once the basic machine is up and running, make the following additional changes to set up Cassandra.

## Set Up Ephemeral Disks

Reference: [SSD Instance Store Volumes](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ssd-instance-store.html)

The i3.xlarge and i3.2xlarge instances are ideal for Cassandra. These instance types have a single NVMe SSD ephemeral drive, which by default has the device name `/dev/nvme0n1`. This device will be formatted using the xfs file system with TRIM support, and mounted as /data. Cassandra will be configured to use it for both database storage and logging. DataStax's research has concluded that with AWS instances, instance storage is better than Elastic Block Storage (EBS), and putting the database and transaction logs on separate volumes has no beneficial impact.

```bash
# Format a single ephemeral NVMe SSD
sudo mkdir /data
sudo mkfs.xfs -K /dev/nvme0n1
sudo mount /dev/nvme0n1 /data -o discard
echo "/dev/nvme0n1 /data xfs defaults,nofail,discard 0 2" | sudo tee -a /etc/fstab
sudo mount -a
```

## Install Java

Reference: [Oracle Java 8 Installer](http://www.webupd8.org/2012/09/install-oracle-java-8-in-ubuntu-via-ppa.html)

Install the latest version of Oracle (Sun) Java 8 and make it the default Java on the server.

```bash
# Accept the Oracle license
echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true" | /usr/bin/debconf-set-selections

# Add the repository and press Enter (automatically)
echo "" | add-apt-repository ppa:webupd8team/java

# Install the installer
apt-get update && apt-get install -y oracle-java8-installer oracle-java8-set-default

# Explicitly select Oracle Java as the default on the machine
update-java-alternatives -s java-8-oracle

# Check the version
java -version
```

## Install Apache Cassandra

Apache Cassandra is being released monthly, with Tick-Tock upgrades: odd numbers (e.g. 3.9) are bug-fix only, whereas even numbers (e.g. 3.10) include new features. The objective of this strategy is to (a) reduce the magnitude of a single release, and (b) improve stability, especially for the odd-numbered releases. The version selected is indicated the Debian repository configuration below (e.g. 311x means version 3.11).

Note that the configuration steps below assume that the instance has a network interface `ens3` and that the first three nodes in the cluster (seed nodes, one in each availability zone), have the IP addresses `10.1.21.11`, `10.1.22.11`, and `10.1.23.11`. Also, the `sed` commands below should be confirmed the first time applied to a new version of Cassandra, because the default configuration file can change.

```bash
# Add the repository:
echo "deb http://www.apache.org/dist/cassandra/debian 311x main" | sudo tee -a /etc/apt/sources.list.d/cassandra.list
curl -s https://www.apache.org/dist/cassandra/KEYS | sudo apt-key add -

# Install Cassandra:
sudo apt-get update
sudo apt-get install -y cassandra cassandra-tools

# Clean up the default installation configuration (assumes ephemeral disks):
sudo systemctl stop cassandra.service
sudo rm /var/log/cassandra/*.log
sudo rm -Rf /var/lib/cassandra/*
sudo mv /var/lib/cassandra /data/
sudo ln -s /data/cassandra /var/lib/cassandra

# Configure Cassandra for Production (your IPs may be different):
sudo sed -i 's/cluster_name: 'Test Cluster'/cluster_name: 'Production'/g' /etc/cassandra/cassandra.yaml
sudo sed -i 's/seeds: "127.0.0.1"/seeds: "10.1.31.11,10.1.32.11,10.1.33.11"/g' /etc/cassandra/cassandra.yaml
sudo sed -i 's/listen_address: localhost/# listen_address: localhost/g' /etc/cassandra/cassandra.yaml
sudo sed -i 's/# listen_interface: localhost/listen_interface: ens3/g' /etc/cassandra/cassandra.yaml
sudo sed -i 's/rpc_address: localhost/# rpc_address: localhost/g' /etc/cassandra/cassandra.yaml
sudo sed -i 's/# rpc_interface: eth1/rpc_interface: ens3/g' /etc/cassandra/cassandra.yaml
sudo sed -i 's/endpoint_snitch: SimpleSnitch/endpoint_snitch: Ec2Snitch/g' /etc/cassandra/cassandra.yaml

# Restart Cassandra, and it will initialize its data files
sudo systemctl start cassandra.service

# Optional: view system status, examine the logs
# sudo systemctl status cassandra.service
# sudo less /var/log/cassandra/system.log
# nodetool status
```

## Routine Maintenance

Cassandra needs [Anti-Entropy Repair](http://docs.datastax.com/en/cassandra/3.0/cassandra/operations/opsRepairNodesManualRepair.html) on a regular basis. This process is used to ensure that all nodes have all the most current data that they're supposed to have. It also triggers compactions, where deleted and obsolete data are removed. It should be run at least once/week, at a time when it will have the least impact on regular operations (ie. during a quiet period). It's both computationally and IO intensive, and how long it takes is dependent on how many nodes and how much data there are. If there is an obvious quiet period, then scheduling a `nodetool repair` cron job will work well. If not, then a trickle of sub-range repairs always running in the background is a better strategy, and [DataStax OpsCenter](http://www.datastax.com/products/datastax-opscenter) does a fine job of this. In fact, it's one of the better reasons to choose to pay DataStax for support.

If there are 3 database nodes with replication factor 3, then the repair job only needs to run on one of the nodes. The node running the repair will coordinate activity with its two other peers. If there are more than 3 nodes, or if we simply want to run the the process on each node individually, then we can put a `nodetool repair -pr` cron job on each machine, at staggered time slots (not all at the same time). This will repair only the node's 'primary range' (hence the -pr).

## Database Backup/Restore

In a sense, Cassandra comes with an automatic, built-in backup mechanism: the replication factor. However, this only protects the current data. If you accidentally whacked your database in a bulk operation, and wanted to go back in time to a previous configuration, then you’d need a backup of a snapshot of the database.

There are several ways to make backups:
1. [DataStax OpsCenter](http://www.datastax.com/products/datastax-opscenter) provides a Backup Service. This stores backup snapshot data on the same storage media as regular data, with multiple replicas around the ring. This is part of the reason to purchase DataStax Enterprise; it includes OpsCenter.
1. Create EBS Volumes (up to 1 TB), mount them to each database node, and use daily snapshots and rsync to copy backup data onto the EBS Volumes. This can also be useful for migrating data to other production/non-production environments.
1. Use a tool like [Tablesnap](https://github.com/JeremyGrosser/tablesnap) to make live replicas of each SSTable on Amazon S3 storage as soon as they're written. Need to go back in time? Just pick the appropriate file timestamps.

To restore a node from a backup, you can simply copy the `/var/lib/cassandra/data/*` files into the same folder on the new node and make sure the `/etc/cassandra/cassandra.yaml` configuration is correct. Start Cassandra, and it will detect the new data and work with it. You want to run a `nodetool repair` before you put the node/cluster into service.
