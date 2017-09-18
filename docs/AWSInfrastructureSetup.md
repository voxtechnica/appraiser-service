This document describes, step-by-step, how to build out a complete Virtual Private Cloud (VPC) with all of its associated network infrastructure, virtual machines, and security controls.

Log in to the [Amazon Web Services](https://console.aws.amazon.com) web console.

Create a [Key Pair](https://console.aws.amazon.com/ec2/home?region=us-west-2#s=KeyPairs), if you don't already have one. The private key (e.g. appraisers.pem) file will automatically download. Store this in a secure place. It will be the SSH key for user ubuntu on all virtual machines created with it. Be sure to restrict access to the file to user-only. Example: `chmod 400 appraisers.pem`

Create a [Virtual Private Cloud](https://console.aws.amazon.com/vpc/home?region=us-west-2#s=vpcs) (VPC):
* Subnet 10.1.0.0/16 named Appraisers

Create [Subnets](https://console.aws.amazon.com/vpc/home?region=us-west-2#s=subnets):
* 10.1.1.0/24  in Zone us-west-2a named DMZ-A
* 10.1.2.0/24  in Zone us-west-2b named DMZ-B
* 10.1.3.0/24  in Zone us-west-2c named DMZ-C
* 10.1.11.0/24 in Zone us-west-2a named App-A
* 10.1.12.0/24 in Zone us-west-2b named App-B
* 10.1.13.0/24 in Zone us-west-2c named App-C
* 10.1.21.0/24 in Zone us-west-2a named DB-A
* 10.1.22.0/24 in Zone us-west-2b named DB-B
* 10.1.23.0/24 in Zone us-west-2c named DB-C

Create an [Internet Gateway](https://console.aws.amazon.com/vpc/home?region=us-west-2#s=internet-gateways) (IGW) named Appraisers, and attach it to the VPC.

Create three [NAT Gateways](https://us-west-2.console.aws.amazon.com/vpc/home?region=us-west-2#NatGateways:sort=desc:createTime), one for each availability zone. Outbound traffic originating in each availability zone will be routed through it's respective NAT gateway. Instances will be placed in subnets DMZ-A, DMZ-B, and DMZ-C. [Elastic IPs](https://us-west-2.console.aws.amazon.com/ec2/v2/home?region=us-west-2#Addresses:sort=publicIp) will keep their IP addresses stable, so that if a third party we're connecting to filters traffic by IP address, we can give them those addresses.

Create [Routing Tables](https://us-west-2.console.aws.amazon.com/vpc/home?region=us-west-2#routetables:) for each availability zone. Example for Zone A:
* For DMZ-A, create a Route Table named DMZ-A and associate it with the VPC. Create a route for external traffic (0.0.0.0/0) to the IGW. Associate subnet 10.1.1.0/24 (DMZ-A).
* For other internal subnets in Zone A, create a Route Table named NAT-A and associate it with the VPC. Create a route for external traffic (0.0.0.0/0) to the NAT gateway for Zone A, and associate internal subnets 10.1.11.0/24 (App-A) and 10.1.21.0/24 (DB-A).

Create Security Group 'ELB-WWW' and 'ELB-API', for incoming web traffic and API traffic, respectively. Allow the following ports:
* Inbound HTTP from 0.0.0.0/0 (anywhere)
* Inbound HTTPS from 0.0.0.0/0 (anywhere)

Create Security Group 'Apps' for application nodes. Allow the following ports:
* Inbound SSH Port 22 from 10.1.0.0/16 (inside VPC for admin)
* Inbound HTTP Port 80 from 10.1.0.0/16 (inside VPC for the Web App)
* Inbound TCP Ports 8080-8081 from 10.1.0.0/16 (inside VPC for the REST API)

Create Security Group 'DB' for database nodes. Allow the following ports:
* Inbound SSH Port 22 from 10.1.0.0/16 (inside VPC for admin)
* Inbound TCP Ports 7000-7001 from DB Security Group (Gossip)
* Inbound TCP Port 9042 from Apps Security Group (CQL Client)
* Inbound TCP Port 9160 (optional) from Apps Security Group (Thrift Client)
* Inbound TCP Ports 9200-9400 from 10.1.0.0/16 (Elasticsearch)

Create SSH Bastion Host with a Security Group allowing SSH traffic from anywhere. By default, passwords are disallowed. Authentication requires a public key in the user's ~/.ssh/authorized_keys file. Consider also setting up two-factor authentication using [Duo Security](https://duo.com/docs/duounix) for SSH access.

Create [Apt Repository](AptRepositorySetup.md)

Create Cassandra Instances (See: [Cassandra Setup](CassandraSetup.md)). Also, install Elasticsearch on the same nodes, if you'd like to economize on the cost of the database tier (See: [Elasticsearch Setup](ElasticsearchSetup.md)).

Create [Elastic Load Balancers](https://us-west-2.console.aws.amazon.com/ec2/v2/home?region=us-west-2#LoadBalancers:) (ELB). If you'd like to minimize the cost/effort of managing ELBs, you could put both the web application and the REST API on the same load balancer, but on different ports. Map ports 80 and 443 to the web application, and port 8443 to the REST API. For a health check, use the API's healthcheck on port 8081 (HTTP:8081/healthcheck). For SSL/TLS certificates, create a free wildcard certificate (e.g. *.voxtechnica.info) using the [AWS Certificate Manager](https://aws.amazon.com/certificate-manager/).

Use [Route 53](https://console.aws.amazon.com/route53/home?region=us-west-2#) to update DNS to point appraisers.voxtechnica.info and api.voxtechnica.info to the appropriate load balancer(s).

Create Application Instances. Use [Packer](https://www.packer.io/) to build a virtual machine image ([AMI](https://us-west-2.console.aws.amazon.com/ec2/v2/home?region=us-west-2#Images:sort=name)). Create a corresponding [Launch Configuration](https://us-west-2.console.aws.amazon.com/ec2/autoscaling/home?region=us-west-2#LaunchConfigurations:) and [Auto Scaling Group](https://us-west-2.console.aws.amazon.com/ec2/autoscaling/home?region=us-west-2#AutoScalingGroups:view=details).
