# EDGARPulse: SEC Filing Analytics System

EDGARPulse is a distributed analytics system that gets SEC filings, computes filing-level risk signals, stores request and performance logs, and exposes operational analytics through a browser-based dashboard.

The system integrates an Android client, a Java Servlet-based backend, the SEC EDGAR API, MongoDB Atlas, and a web dashboard to support end-to-end filing ingestion, analysis, storage, and monitoring.

## Overview

EDGARPulse was built to transform raw SEC filing metadata and filing text into structured, interpretable analytics outputs. The backend retrieves filing data from the SEC EDGAR API, performs text-based risk tone analysis and filing activity analysis, stores request-level logs in MongoDB Atlas, and provides a dashboard for monitoring and aggregate analytics.

## Key Features

- Retrieves company filing metadata and filing text from the SEC EDGAR API
- Normalizes SEC CIK identifiers and selects representative filings for analysis
- Computes a rule-based lexical risk tone score from filing text
- Engineers filing activity indicators such as:
    - recent filing count
    - amendment frequency
    - 8-K frequency
    - filing recency
- Stores request metadata, analysis outputs, and service telemetry in MongoDB Atlas
- Displays formatted logs and aggregate operational analytics through a web dashboard
- Supports Android-based querying and structured JSON response delivery

## Architecture

High-level workflow:

1. The Android client accepts a company CIK from the user.
2. The backend API receives the request and normalizes the identifier.
3. The backend retrieves filing metadata and filing text from the SEC EDGAR API.
4. The backend computes risk tone and filing activity features.
5. Request logs and analytical outputs are stored in MongoDB Atlas.
6. The dashboard displays full logs and aggregated service analytics.

## Tech Stack

- **Backend:** Java, Jakarta Servlets, JSP
- **Mobile Client:** Android, Java
- **Database:** MongoDB Atlas
- **External Data Source:** SEC EDGAR API
- **Data Format:** JSON
- **Deployment / Dev Environment:** GitHub Codespaces
- **Planned Portfolio Deployment Upgrade:** AWS

## Project Structure

    EDGARPulse/
    ├── backend/
    ├── android-client/
    ├── docs/
    ├── screenshots/
    └── README.md

## Running the Project

### Backend configuration

Set the following environment variables before running the backend:

    MONGO_CONNECTION_URL = your_mongodb_connection_string
    SEC_USER_AGENT = your_email_or_app_identifier

### Backend service

The backend exposes two main endpoints:

- `/analyze` — returns structured SEC filing analytics for a given CIK
- `/dashboard` — displays operational analytics and formatted logs

### Android client

The Android app sends requests to the backend endpoint and displays:
- company name
- industry
- analyzed filing type
- risk tone score
- risk tone label
- disclosure activity level
- summary

## Notes

This project was originally developed in a course environment and later refactored into a portfolio-oriented analytics system with a stronger focus on data ingestion, feature generation, persistent logging, and observability.