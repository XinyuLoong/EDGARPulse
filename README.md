# EDGARPulse: SEC Filing Analytics System

EDGARPulse is a distributed analytics system that retrieves SEC filing data, computes filing-level risk and disclosure signals, stores request-level logs, and presents operational analytics through a browser-based dashboard.

The project integrates:
- a native Android client for user input and result display,
- a Java Servlet-based backend for data retrieval and analysis,
- the SEC EDGAR API as the external data source,
- MongoDB Atlas for persistent logging and analytics storage,
- and a web dashboard for monitoring and formatted log reporting.

---

## Overview

Public SEC filings contain valuable metadata and text disclosures, but they are not immediately usable for lightweight analytics workflows. EDGARPulse transforms raw filing metadata and filing text into structured, interpretable outputs by combining external API retrieval, text processing, feature generation, persistent storage, and dashboard-based observability.

This repository is organized as a portfolio-oriented version of the project, separating the backend service, Android client, documentation, and screenshots.

---

## Key Features

- Retrieves filing metadata and filing text from the SEC EDGAR API
- Normalizes SEC CIK identifiers for downstream processing
- Selects representative filings for analysis (10-K, 10-Q, then 8-K priority)
- Computes a rule-based lexical **risk tone score** from filing text
- Engineers filing activity indicators, including:
  - recent filing count
  - amendment frequency
  - 8-K frequency
  - filing recency
- Stores request metadata, analysis outputs, and service telemetry in MongoDB Atlas
- Exposes structured JSON responses through backend analysis endpoints
- Displays formatted logs and aggregate analytics in a browser-based dashboard
- Supports Android-based querying and result visualization

---

## High-Level Architecture

Workflow:

1. The Android client accepts a company CIK from the user.
2. The backend API receives the request and normalizes the identifier.
3. The backend retrieves filing metadata and filing text from the SEC EDGAR API.
4. The backend computes risk tone and filing activity features.
5. Request logs and analytical outputs are stored in MongoDB Atlas.
6. The dashboard displays full logs and aggregated operational analytics.

---

## Repository Structure

    EDGARPulse/
    ├── README.md
    ├── backend/
    │   └── edgarpulse-backend/
    ├── android-client/
    │   └── edgarpulse-android/
    ├── docs/
    └── screenshots/

### Folder Details

- `backend/edgarpulse-backend/`
  - Java/Jakarta Servlet backend
  - SEC API retrieval and analysis logic
  - MongoDB-backed logging
  - dashboard endpoints and JSP views

- `android-client/edgarpulse-android/`
  - Native Android client
  - CIK-based querying workflow
  - structured result display

- `docs/`
  - case study and supporting project documentation

- `screenshots/`
  - Android UI screenshots
  - backend API screenshots
  - dashboard screenshots
  - MongoDB screenshots

---

## Tech Stack

- **Backend:** Java, Jakarta Servlets, JSP
- **Mobile Client:** Android, Java
- **Database:** MongoDB Atlas
- **External Data Source:** SEC EDGAR API
- **Data Format:** JSON
- **Development / Demo Environment:** GitHub Codespaces
- **Planned Portfolio Deployment Upgrade:** AWS

---

## Core Analytics Outputs

The backend returns structured analytics such as:

- company name
- industry
- ticker
- analyzed filing type
- filing date
- risk tone score
- risk tone label
- disclosure activity level
- recent filing count
- amendment count
- 8-K count
- days since last filing
- summary

---

## Dashboard Analytics

The dashboard is designed for browser-based monitoring and reporting. It summarizes:
- total API requests
- most queried company
- most common analyzed form type
- average risk tone score
- elevated risk tone request count
- high disclosure activity request count
- successful request count
- average processing time

It also displays formatted full logs rather than raw JSON/XML.

---

## Running the Project

### 1. Backend Configuration

Set the following environment variables before running the backend:

    MONGO_CONNECTION_URL=your_mongodb_connection_string
    SEC_USER_AGENT=your_email_or_app_identifier

A sample template is included in:

    backend/edgarpulse-backend/.env.example

### 2. Backend Endpoints

The backend exposes two main endpoints:

- `/analyze` — returns structured SEC filing analytics for a given CIK
- `/dashboard` — displays operational analytics and formatted logs

### 3. Android Client

The Android app sends requests to the backend endpoint and displays:
- company name
- industry
- analyzed filing type
- risk tone score
- risk tone label
- disclosure activity level
- summary

Note:
The Android client should be configured with the correct public backend URL for the currently deployed backend environment.

---

## Example Use Case

A user enters a SEC CIK in the Android app. The backend retrieves recent filing information from the SEC EDGAR API, selects a representative filing, computes risk tone and disclosure activity indicators, stores the request log in MongoDB Atlas, and returns a structured JSON response. The dashboard then aggregates these logs to provide operational analytics and monitoring visibility.

---

## Screenshots

Suggested screenshots to include in this repository:

- `screenshots/android/android-home.png`
- `screenshots/android/android-result.png`
- `screenshots/backend/api-response.png`
- `screenshots/dashboard/dashboard-analytics.png`
- `screenshots/dashboard/dashboard-logs.png`
- `screenshots/mongodb/mongodb-logs.png`

---

## Security and Configuration Notes

- Real secrets are **not** stored in this repository.
- Environment variables are used for sensitive configuration such as database connection strings.
- `.env.example` is included only as a template.
- Local `.env` files, build outputs, and other generated artifacts should remain excluded from version control.

---

## Portfolio Context

This repository is a portfolio-oriented version of the project. The original course implementation was refactored to emphasize:
- cleaner project structure,
- safer configuration management,
- clearer documentation,
- and stronger presentation of analytics, logging, and observability workflows.

---

## Future Improvements

- Replace rule-based lexical scoring with more advanced NLP-based signal extraction
- Add historical comparisons across filings for the same company
- Deploy the backend to a dedicated cloud environment such as AWS
- Expand dashboard analytics with richer aggregations and visualizations
- Add automated tests for API retrieval, parsing, and analysis modules

---

## Contact

If you are reviewing this project as part of an application, I would be happy to provide:
- a short case study PDF,
- selected screenshots,
- or a guided walkthrough of the system design and implementation.