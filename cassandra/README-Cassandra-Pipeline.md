# Cassandra Cluster Management Pipelines

A collection of Jenkins pipelines for comprehensive Cassandra cluster lifecycle management, from infrastructure deployment to monitoring and emergency operations.

## 📁 Pipeline Collection Overview

This folder contains 5 specialized Jenkins pipelines for managing Cassandra clusters:

### 1. **CassandraPipeline.groovy** - Infrastructure Deployment
Terraform-based pipeline for deploying and managing Cassandra cluster infrastructure (cassandra nodes, monitoring, opscenter).

**Key Features:**
- ✅ Multi-module support (cassandra, monitoring, opscenter)
- ✅ Full Terraform lifecycle (init, plan, apply, destroy, validate, show, output)
- ✅ Auto-approve option for automated deployments
- ✅ Automatic cluster health verification
- ✅ Slack notifications for all operations

**Use Cases:** Deploy new clusters, update infrastructure, tear down environments

---

### 2. **CassandraDataLoader.groovy** - Test Data Generation
Python-based data loader for inserting realistic stock market test data into Cassandra clusters.

**Key Features:**
- ✅ Configurable record count (10K - 4M records)
- ✅ Adjustable batch sizes (50 - 5000)
- ✅ Parallel workers (1-8 threads)
- ✅ Schema auto-creation with multiple tables
- ✅ Realistic stock market data using Faker
- ✅ Performance metrics and timing

**Use Cases:** Load testing, demo data generation, performance benchmarking

---

### 3. **CassandraNodeManager.groovy** - Node Lifecycle Management
Pipeline for managing individual or all Cassandra nodes with granular control.

**Key Features:**
- ✅ Start/stop/restart operations
- ✅ Selective node management (specify nodes or all)
- ✅ Complete cluster destruction
- ✅ Real-time status checking
- ✅ Confirmation required for destructive actions

**Use Cases:** Node maintenance, rolling restarts, selective node operations

---

### 4. **EmergencyClusterStop.groovy** - Emergency Operations
Quick response pipeline for stopping Cassandra containers during critical system issues.

**Key Features:**
- ✅ Immediate resource checking (CPU, memory, containers)
- ✅ Stop Cassandra-only or all containers
- ✅ Force remove containers and cleanup
- ✅ Network cleanup
- ✅ No confirmation delay for emergencies

**Use Cases:** System overload, runaway processes, emergency maintenance

---

### 5. **MonitoringSetup.groovy** - Observability Stack
Deploy and manage Prometheus + Grafana + JMX monitoring stack for Cassandra.

**Key Features:**
- ✅ One-click Prometheus deployment
- ✅ Pre-configured Grafana dashboards
- ✅ JMX metrics exporter
- ✅ Auto-configure data sources
- ✅ Port conflict resolution
- ✅ Health checks and access URLs

**Use Cases:** Cluster monitoring, performance tracking, alerting setup

---

## Quick Start Guide

### Initial Deployment Workflow

```
1. CassandraPipeline.groovy (ACTION: apply)
   └─> Deploy 4-node Cassandra cluster

2. MonitoringSetup.groovy (ACTION: deploy)
   └─> Set up monitoring dashboards

3. CassandraDataLoader.groovy
   └─> Load test data

4. Monitor via Grafana (http://localhost:3001)
```

### Maintenance Workflow

```
1. CassandraNodeManager.groovy (ACTION: status)
   └─> Check cluster health

2. CassandraNodeManager.groovy (ACTION: restart-all)
   └─> Rolling restart if needed

3. EmergencyClusterStop.groovy (if issues arise)
   └─> Emergency stop
```

---

## Pipeline Overview (Legacy: CassandraPipeline.groovy)

This section documents the main infrastructure deployment pipeline.

## Features

- ✅ **Terraform Init** - Initialize Terraform working directory
- ✅ **Terraform Validate** - Validate Terraform configuration syntax
- ✅ **Terraform Plan** - Preview infrastructure changes
- ✅ **Terraform Apply** - Deploy Cassandra cluster
- ✅ **Terraform Destroy** - Remove Cassandra cluster
- ✅ **Terraform Show** - Display current state
- ✅ **Terraform Output** - Show cluster connection info
- ✅ **Slack Notifications** - Get notified on success/failure
- ✅ **Auto-Approve Option** - Skip manual confirmation
- ✅ **Cluster Verification** - Automatic health check after deployment

## Setup Instructions

### 1. Create Jenkins Job

1. Go to Jenkins → New Item
2. Enter name: `cassandra-cluster`
3. Select: **Pipeline**
4. Click OK

### 2. Configure Pipeline

In the Pipeline section:
- **Definition**: Pipeline script from SCM
- **SCM**: Git
- **Repository URL**: `https://github.com/MuddyThunder1040/slack-notifier.git`
- **Branch**: `*/master`
- **Script Path**: `cassandra/CassandraPipeline.groovy`

> **Note:** All pipelines are now in the `cassandra/` folder. Update your Script Path accordingly:
> - `cassandra/CassandraPipeline.groovy`
> - `cassandra/CassandraDataLoader.groovy`
> - `cassandra/CassandraNodeManager.groovy`
> - `cassandra/EmergencyClusterStop.groovy`
> - `cassandra/MonitoringSetup.groovy`

### 3. Prerequisites on Jenkins Agent

Ensure the following are installed on your Jenkins agent:

```bash
# Terraform
wget https://releases.hashicorp.com/terraform/1.6.0/terraform_1.6.0_linux_amd64.zip
unzip terraform_1.6.0_linux_amd64.zip
sudo mv terraform /usr/local/bin/

# Docker (if not already installed)
sudo apt update
sudo apt install docker.io
sudo systemctl start docker
sudo usermod -aG docker jenkins
```

## Usage

### Deploy Cassandra Cluster

1. Click **Build with Parameters**
2. Select **TF_ACTION**: `apply`
3. Choose **AUTO_APPROVE**: 
   - ✅ `true` - Skip confirmation (faster)
   - ❌ `false` - Require manual approval (safer)
4. Click **Build**

### Check Cluster Status

1. **TF_ACTION**: `output`
2. Click **Build**

### Preview Changes

1. **TF_ACTION**: `plan`
2. Click **Build**

### Destroy Cluster

1. **TF_ACTION**: `destroy`
2. Choose **AUTO_APPROVE** (true/false)
3. Click **Build**

### Validate Configuration

1. **TF_ACTION**: `validate`
2. Click **Build**

## Terraform Actions

| Action | Description | Auto-Approve |
|--------|-------------|--------------|
| `init` | Initialize Terraform | N/A |
| `validate` | Validate configuration syntax | N/A |
| `plan` | Show planned changes | N/A |
| `apply` | Deploy cluster | Optional |
| `destroy` | Remove cluster | Optional |
| `show` | Display current state | N/A |
| `output` | Show cluster info | N/A |

## Cluster Information

After successful deployment:

**CQL Ports:**
- Node 1: `localhost:9042`
- Node 2: `localhost:9043`
- Node 3: `localhost:9044`
- Node 4: `localhost:9045`

**Connect to Cluster:**
```bash
docker exec -it cassandra-node1 cqlsh
```

**Check Cluster Status:**
```bash
docker exec -it cassandra-node1 nodetool status
```

## Slack Notifications

The pipeline sends notifications to `#the-restack-notifier` for:
- ✅ Successful operations (green)
- ❌ Failed operations (red)
- 🚀 Apply operations
- 🗑️ Destroy operations
- 📋 Plan operations

## Environment Variables

- `ENABLE_SLACK`: Enable/disable Slack notifications (`true`/`false`)
- `SLACK_CHANNEL`: Slack channel for notifications
- `SLACK_CREDENTIAL_ID`: Jenkins credential ID for Slack token
- `GIT_REPO`: Repository URL for aws-topology
- `TF_WORKSPACE`: Terraform workspace directory

## Pipeline Stages

1. **Checkout** - Clone aws-topology repository
2. **Terraform Init** - Initialize Terraform (when needed)
3. **Terraform Validate** - Validate configuration
4. **Terraform Plan** - Create execution plan
5. **Terraform Apply** - Deploy infrastructure
6. **Terraform Destroy** - Remove infrastructure
7. **Terraform Show** - Display state
8. **Terraform Output** - Show outputs
9. **Verify Cluster** - Health check (after apply)

## Security Best Practices

- Use `AUTO_APPROVE: false` for production deployments
- Review plan output before approving
- Monitor Slack notifications for unauthorized changes
- Keep Terraform state files secure

## Troubleshooting

### Pipeline Fails on Docker Commands

Ensure Jenkins user has Docker permissions:
```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

### Terraform Not Found

Install Terraform on Jenkins agent and ensure it's in PATH.

### Cluster Not Starting

Wait 2-3 minutes for full initialization, then check:
```bash
docker logs cassandra-node1
```

### State Lock Issues

Remove stale locks:
```bash
terraform force-unlock <LOCK_ID>
```

## Example Workflow

**Initial Deployment:**
1. Run `validate` to check configuration
2. Run `plan` to preview changes
3. Run `apply` with `AUTO_APPROVE: false`
4. Review plan and approve
5. Run `output` to get connection info

**Teardown:**
1. Run `destroy` with `AUTO_APPROVE: false`
2. Review destroy plan
3. Approve destruction

## Notes

- The pipeline automatically verifies cluster health after `apply`
- Wait 60 seconds after deployment for cluster initialization
- All 4 nodes must show "UN" (Up/Normal) status for full operation
- Data persists in Docker volumes until `destroy` is run
- Each node has isolated persistent storage
