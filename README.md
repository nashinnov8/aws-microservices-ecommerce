# Scalable AWS Micro-Ecommerce Platform
## Project Overview
This project is a high-performance, distributed ecommerce backend designed to showcase modern cloud-native practices. It bridges the gap between traditional containerized services (Amazon ECS) and modern serverless computing (AWS Lambda).

## Core Microservices
Identity Service (ECS + MySQL): Handles user registration, login, and secure session management using a custom JWT implementation with Refresh Token Rotation.

Product Service (Lambda + DynamoDB): A high-availability service for managing product catalogs with low-latency reads.

Order & Inventory Service: Orchestrates the checkout process and manages stock levels using an asynchronous, event-driven approach.

## Key Features
Hybrid Architecture: Seamless integration between ECS Fargate and AWS Lambda.

Secure Auth: Stateless authentication using JWT (RS256) with secure password hashing.

Event-Driven: Decoupled service communication using Amazon SQS to ensure data consistency.

Infrastructure as Code (IaC): Managed and deployed via AWS SAM/Terraform.

Automated CI/CD: Fully integrated GitHub Actions pipelines for automated testing, Docker building (ECR), and deployment (ECS/Lambda).

## Tech Stack
Cloud: AWS (ECS, Lambda, RDS, SQS, API Gateway, S3)

Backend: Spring Boot Application (Java)

Database: MySQL (RDS), DynamoDB

DevOps: Docker, GitHub Actions, AWS CLI

Security: JWT, IAM Roles & Policies