# Cassandra Cluster Terraform Pipeline

Jenkins pipeline for managing a 4-node Cassandra cluster using Terraform and Docker.

## Pipeline Overview

This pipeline automates Terraform operations for deploying and managing a Cassandra cluster on your local infrastructure.

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
- **Script Path**: `CassandraPipeline.groovy`

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
