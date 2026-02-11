# Railway Deployment Guide

## Prerequisites
- Railway account linked to GitHub
- Repository pushed to GitHub

## Step 1: Push Code to GitHub

```bash
git add .
git commit -m "feat: Week 2 complete - MFA, rate limiting, audit logs, session management"
git push origin main
```

## Step 2: Create Railway Project

1. Go to [Railway Dashboard](https://railway.app/dashboard)
2. Click "New Project"
3. Select "Deploy from GitHub repo"
4. Choose `Distributed-Banking-Microservices-Platform`

## Step 3: Add PostgreSQL Database

1. In your Railway project, click "New"
2. Select "Database" → "Add PostgreSQL"
3. Wait for provisioning
4. Copy the `DATABASE_URL` from the Variables tab

## Step 4: Add Redis

1. Click "New" → "Database" → "Add Redis"
2. Wait for provisioning
3. Copy the `REDIS_URL` from the Variables tab

## Step 5: Deploy Auth Service

1. Click "New" → "GitHub Repo"
2. Select your repo
3. Set **Root Directory**: `auth-service`
4. Add environment variables (see below)
5. Deploy

### Auth Service Environment Variables

```
# Database (use Railway's PostgreSQL)
SPRING_DATASOURCE_URL=${DATABASE_URL}
SPRING_DATASOURCE_USERNAME=${PGUSER}
SPRING_DATASOURCE_PASSWORD=${PGPASSWORD}

# Redis (use Railway's Redis)
SPRING_DATA_REDIS_HOST=${REDISHOST}
SPRING_DATA_REDIS_PORT=${REDISPORT}
SPRING_DATA_REDIS_PASSWORD=${REDISPASSWORD}

# JWT (generate RSA keys - see below)
JWT_PRIVATE_KEY=<your-private-key>
JWT_PUBLIC_KEY=<your-public-key>
JWT_ISSUER=banking-auth-service

# Server
PORT=8080
SPRING_PROFILES_ACTIVE=prod

# JVM
JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom
```

## Step 6: Deploy Banking Service

1. Click "New" → "GitHub Repo"
2. Select your repo again
3. Set **Root Directory**: `banking-service`
4. Add environment variables
5. Deploy

### Banking Service Environment Variables

```
# Database
SPRING_DATASOURCE_URL=${DATABASE_URL}
SPRING_DATASOURCE_USERNAME=${PGUSER}
SPRING_DATASOURCE_PASSWORD=${PGPASSWORD}

# Auth Service URL (use Railway's internal networking)
AUTH_SERVICE_URL=http://auth-service.railway.internal:8080

# JWT Public Key (same as auth service)
JWT_PUBLIC_KEY=<your-public-key>

# Server
PORT=8081
SPRING_PROFILES_ACTIVE=prod

# JVM
JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom
```

## Step 7: Generate RSA Keys for JWT

Run this command to generate RSA key pair:

```bash
# Generate private key
openssl genrsa -out private_key.pem 2048

# Extract public key
openssl rsa -in private_key.pem -pubout -out public_key.pem

# Convert to single line for environment variable
cat private_key.pem | tr '\n' '\\n' | sed 's/\\n$//'
cat public_key.pem | tr '\n' '\\n' | sed 's/\\n$//'
```

**Important:** Store these keys securely. The private key should only be in the auth-service.

## Step 8: Configure Networking

1. In Railway, go to Auth Service → Settings → Networking
2. Enable "Public Networking" to get a public URL
3. Note the URL (e.g., `auth-service-xxx.up.railway.app`)

4. Do the same for Banking Service

## Step 9: Update Postman Environment

Update your Postman environment with the Railway URLs:
- `baseUrl`: `https://auth-service-xxx.up.railway.app`
- `bankingServiceUrl`: `https://banking-service-xxx.up.railway.app`

## Verification

1. Check health endpoints:
   - `https://auth-service-xxx.up.railway.app/actuator/health`
   - `https://banking-service-xxx.up.railway.app/actuator/health`

2. Test registration and login via Postman

## Troubleshooting

### Build Fails
- Check Railway build logs
- Ensure Dockerfile is correct
- Verify root directory is set correctly

### Connection Refused
- Check environment variables
- Verify PostgreSQL and Redis are running
- Check internal networking URLs

### JWT Errors
- Ensure RSA keys are properly formatted
- Check that public key is shared between services
- Verify JWT_ISSUER matches

## Cost Estimate (Railway Free Tier)
- 500 hours/month execution time
- 100 GB bandwidth
- Suitable for development/demo purposes
